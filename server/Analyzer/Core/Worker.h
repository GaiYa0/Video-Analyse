#ifndef ANALYZER_WORKER_H
#define ANALYZER_WORKER_H

#include <condition_variable>
#include <cstdint>
#include <map>
#include <mutex>
#include <queue>
#include <string>
#include <thread>
#include <vector>
#include <opencv2/opencv.hpp>

namespace SVAAnalyzer
{
	class Scheduler;
	class AvPullStream;
	class AvPushStream;
	class Analyzer;
	struct Control;
	struct Frame;
	class FramePool;
	struct DetectObject;
	struct AggregateBehaviorMatch;
	struct WorkerControlRuntime;

	/**
	 * @brief Worker —— 视频分析执行单元
	 *
	 * ## 架构概念
	 * SVA-server 采用 Worker 模型，但为了避免同一路视频被重复拉流和解码，
	 * 当前采用 **1 个视频流(streamCode/streamUrl) = 1 个 Worker**。
	 * 同一流下的多个布控(Control)挂载到同一个 Worker 内，每个布控仍保留独立的
	 * Analyzer、行为规则、告警队列和可选推流器。
	 *
	 *
	 * ## 线程模型
	 * 每个 Worker 固定启动拉流线程和解码线程；每个 Control runtime 额外维护自己的
	 * 告警线程，并在开启推流时维护自己的编码推流线程。
	 * 
	 * ```
	 * Worker 生命周期
	 * ┌─────────────────────────────────────────────────────┐
	 * │  init() —— 初始化共享拉流、解码资源和首个 Control    │
	 * │    │                                                 │
	 * │    ├── 线程1: AvPullStream::readThread()            │
	 * │    │   └── 从 RTSP/RTMP 拉取 AVPacket 放入队列      │
	 * │    │                                                 │
	 * │    ├── 线程2: Worker::decodeVideoThread()  ★核心★   │
	 * │    │   └── 解码一次 → BGR转换 → 遍历本流下 Controls │
	 * │    │       → 各自目标检测/行为分析/事件/推流/告警   │
	 * │    │                                                 │
	 * │    ├── 每个 Control: generateAlarmThread()          │
	 * │    │   └── 独立缓存 prefix 帧，发生报警时生成视频    │
	 * │    │                                                 │
	 * │    └── 每个开启推流的 Control: encodeVideoThread()  │
	 * │        └── 将该布控画框后的帧编码推流出去            │
	 * │                                                     │
	 * │  remove() —— 通知所有线程停止                        │
	 * │  ~Worker() —— 等待线程 join，释放所有资源           │
	 * └─────────────────────────────────────────────────────┘
	 * ```
	 * 
	 * ## 数据流（线程2 内部）
	 * ```
	 * AVPacket → avcodec_send/receive → YUV420P 
	 *   → sws_scale → BGR cv::Mat
	 *   → 对同一 stream 下的每个 Control 复制一份 BGR 图像
	 *     → Analyzer::handleVideoFrame()
	 *       → Algorithm::objectDetect()     // ONNX Runtime 推理
	 *       → applyRegionAndObjectMatch()   // 区域 IoU 匹配
	 *     → Scheduler::updateTemporalTracks()  // IoU 贪心追踪
	 *     → BehaviorEvaluator::evaluateAtomicBehavior() // 行为分析
	 *     → Scheduler::evaluateAggregateBehaviorRules() // 聚合行为
	 *     → 构建 DetectFrameEvent
	 *       → Scheduler::addDetectFrameEvent()  // 事件上报
	 *   → Server Overlay (画框/线/区域)
	 *   → Frame → PushStream (推流) / AlarmQueue (报警缓存)
	 * ```
	 * 
	 */
	class Worker
	{
	public:
		explicit Worker(Scheduler *scheduler, Control *control);
		~Worker();

	public:
		static void decodeVideoThread(void *arg);
		void handleDecodeVideo();
		static void generateAlarmThread(void *arg);
		void handleGenerateAlarm();

	public:
		bool start(std::string &msg);
		bool addControl(Control *control, std::string &msg);
		bool removeControl(const std::string &code);
		Control *getControl(const std::string &code);
		int getControlCount();
		std::vector<Control *> snapshotControls();
		bool getState();
		void setState(bool state);
		void remove();

	public:
		Control *mControl;
		Scheduler *mScheduler;
		AvPullStream *mPullStream;
		AvPushStream *mPushStream;
		Analyzer *mAnalyzer;
		FramePool *mVideoFramePool;

	private:
		bool init(std::string &msg);
		bool addControlLocked(Control *control, std::string &msg);
		void deleteControlRuntime(WorkerControlRuntime *runtime);
		void handleGenerateAlarm(WorkerControlRuntime *runtime);
		bool pushVideoFrame(WorkerControlRuntime *runtime, Frame *frame);
		bool getVideoFrame(WorkerControlRuntime *runtime, Frame *&videoFrame);
		void clearVideoFrameQueue(WorkerControlRuntime *runtime);
		void clearAllControlRuntimes();
		void processControlFrame(WorkerControlRuntime *runtime, int64_t frameCount, const cv::Mat &sourceImage, bool packetReadyForInfer);

	private:
		bool mState = false;
		std::vector<std::thread *> mThreads;
		std::map<std::string, WorkerControlRuntime *> mControlRuntimes;
		std::mutex mControlRuntimesMtx;
		size_t mVideoFrameQueueCapacity = 64;
	};
}

#endif // ANALYZER_WORKER_H