/**
 * @mainpage easySVA-server —— Worker 架构教学项目
 * 
 * ============================================================
 *                     项目定位
 * ============================================================
 * easySVA-server 是一个面向教学的智能视频分析（SVA）服务端程序。
 * 它基于 ONNX Runtime 进行目标检测。
 * 
 * ============================================================
 *                   架构概览（Worker 模型）
 * ============================================================
 * 
 *   main()
 *     │
 *     ├── Config(config.json)           // 读取配置
 *     ├── Scheduler::initAlgorithm()    // 加载 ONNX 模型
 *     ├── Server::start(&scheduler)      // 启动 HTTP API 服务
 *     └── Scheduler::loop()             // 主循环（管理 Worker）
 *           │
 *           ├── 接收 API 请求
 *           │   POST /api/control/add   → 创建 Worker
 *           │   POST /api/control/cancel → 删除 Worker
 *           │
 *           └── Worker Map 管理
 *                │
 *                └── Worker (每个布控 = 1 个 Worker)
 *                      │
 *                      ├── 线程1: AvPullStream::readThread    // 拉流
 *                      ├── 线程2: Worker::decodeVideoThread   // 解码+推理+行为分析 ★
 *                      ├── 线程3: Worker::generateAlarmThread // 报警视频生成
 *                      └── 线程4: AvPushStream::encodeVideoThread (可选推流)
 * 
 * ============================================================
 *              Worker 内的数据处理管线（线程2）
 * ============================================================
 * 
 *   FFmpeg 解码 → BGR 转换 → 目标检测 → 区域匹配
 *                                      ↓
 *                              时态追踪（TemporalProcessor）
 *                                      ↓
 *                              行为分析（BehaviorEvaluator）
 *                                      ↓
 *                              构建事件 → Scheduler → HTTP POST 上报
 * 
 * ============================================================
 */

#include "Core/Config.h"
#include "Core/Scheduler.h"
#include "Core/Server.h"
#include "Core/Version.h"
#include "Core/Algorithm.h"
using namespace SVAAnalyzer;

int main(int argc, char **argv)
{
#ifdef WIN32
	srand(time(NULL));
#endif

	const char *file = NULL;

	for (int i = 1; i < argc; i += 2)
	{
		if (argv[i][0] != '-')
		{
			printf("parameter error:%s\n", argv[i]);
			return -1;
		}
		switch (argv[i][1])
		{
		case 'h':
		{
			printf("-h 打印参数配置信息并退出\n");
			printf("-f 配置文件    如：-f config.json \n");
			system("pause\n");
			exit(0);
			return -1;
		}
		case 'f':
		{
			file = argv[i + 1];
			break;
		}
		default:
		{
			printf("set parameter error:%s\n", argv[i]);
			return -1;
		}
		}
	}

	if (file == NULL)
	{
		printf("failed to read config file\n");
		return -1;
	}

	/*
	 * 步骤1: 读取配置
	 * config.json 包含：
	 *   - code: 节点编号
	 *   - analyzerPort: HTTP API 端口
	 *   - modelDir: ONNX 模型目录
	 *   - saveAlarmUrl / detectEventUrl: 报警上报地址
	 */
	Config config(file);
	if (!config.mState)
	{
		printf("failed to read config file: %s\n", file);
		return -1;
	}
	printf("easySVA-server (teaching edition) %s \n", PROJECT_VERSION);

	/*
	 * 步骤2: 初始化算法模型
	 * 使用 ONNX Runtime 加载模型，自动选择 GPU/CPU
	 * 见 Scheduler::initAlgorithm() 和 AlgorithmOnYolo.cpp
	 */
	Scheduler scheduler(&config);
	if (!scheduler.initAlgorithm())
	{
		return -1;
	}

	/*
	 * 步骤3: 启动 HTTP 服务 + 主循环
	 * Server::start() 在独立线程启动 libevent HTTP 服务
	 * Scheduler::loop() 在主线程中处理 Worker 的新增/删除
	 */
	Server server;
	server.start(&scheduler);
	scheduler.loop();

	return 0;
}