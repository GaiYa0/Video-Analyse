#ifndef ANALYZER_CONFIG_H
#define ANALYZER_CONFIG_H

#include <string>
#include <vector>

namespace SVAAnalyzer
{
	/**
	 * @brief 配置管理 —— 从 config.json 读取运行时参数
	 * 
	 * ## config.json 关键字段说明
	 * 
	 * @param code           节点唯一编号，用于后端区分不同 SVA 节点
	 * @param host           本机 IP 地址
	 * @param analyzerPort   HTTP API 服务端口（libevent 监听）
	 * @param modelDir       ONNX 模型文件目录（如 /opt/SVA/models）
	 * @param saveAlarmUrl   报警视频保存后的回调 URL（HTTP POST）
	 * @param detectEventUrl 实时检测事件的 WebSocket/HTTP 上报地址
	 * 
	 * ## 教学注意
	 * - modelDir 必须指向包含 yolo11n.onnx、yolo26s.onnx 等 ONNX 模型文件的目录
	 * - ONNX Runtime 在加载模型时自动检测 GPU（TensorRT > CUDA > CPU）
	 * - 无需 OpenVINO，所有推理统一使用 ONNX Runtime
	 */
	class Config
	{
	public:
		Config(const char *file);
		~Config();

	public:
		bool mState = false;
		void show();

	public:
		const char *file = NULL;

		std::string code{};
		std::string host{};
		std::string adminHost{};
		std::string saveAlarmUrl{};
		std::string detectEventUrl{};
		int adminPort;
		int analyzerPort;
		int mediaHttpPort;
		int mediaRtspPort;

		std::string uploadDir{};
		std::string modelDir{};
	};
}
#endif // ANALYZER_CONFIG_H
