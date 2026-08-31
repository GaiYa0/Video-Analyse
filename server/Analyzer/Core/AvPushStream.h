#ifndef ANALYZER_AVPUSHSTREAM_H
#define ANALYZER_AVPUSHSTREAM_H
#include <atomic>
#include <condition_variable>
#include <queue>
#include <mutex>
extern "C"
{
#include "libavcodec/avcodec.h"
#include "libavformat/avformat.h"
}
namespace SVAAnalyzer
{
	class Worker;
	struct Control;
	struct Frame;

	class AvPushStream
	{
	public:
		AvPushStream(Worker *worker, Control *control = nullptr);
		~AvPushStream();

	public:
		bool connect();		 // 连接流媒体服务
		bool reConnect();	 // 重连流媒体服务
		void closeConnect(); // 关闭流媒体服务的连接
		int mConnectCount = 0;

		AVFormatContext *mFmtCtx = nullptr;

		// 视频帧
		AVCodecContext *mVideoCodecCtx = NULL;
		AVStream *mVideoStream = NULL;
		int mVideoIndex = -1;
		void addVideoFrame(Frame *frame);
		int getVideoFrameQSize();
		void clearVideoFrameQueue();
		void notifyStop();

	public:
		static void encodeVideoThread(void *arg); // 编码视频帧并推流
		void handleEncodeVideo();

	private:
		Worker *mWorker;
		Control *mControl;
		std::atomic<bool> mStopped{false};

		// 视频帧
		std::queue<Frame *> mVideoFrameQ;
		std::mutex mVideoFrameQ_mtx;
		std::condition_variable mVideoFrameQ_cv;
		size_t mVideoFrameQCapacity = 32;
		bool getVideoFrame(Frame *&frame);
	};

}
#endif // ANALYZER_AVPUSHSTREAM_H
