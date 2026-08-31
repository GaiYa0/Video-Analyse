#ifndef ANALYZER_FRAME_H
#define ANALYZER_FRAME_H
#include <vector>
#include <queue>
#include <mutex>

namespace SVAAnalyzer
{
	struct Frame
	{
	public:
		Frame() = delete;
		Frame(int bufInitSize);
		~Frame();

	public:
		void setBuf(unsigned char *buf, int size);
		void setData(unsigned char *buf, int width, int height, int channels);
		unsigned char *getBuf();
		int getSize();
		int getWidth();
		int getHeight();
		int getChannels();

		bool happen = false;   // 是否发生事件
		float happenScore = 0; // 发生事件的分数

	private:
		int mBufInitSize = 0; // buf初始化时的长度
		int mBufSize = 0;
		int mWidth = 0;
		int mHeight = 0;
		int mChannels = 0;
		unsigned char *mBuf;
	};
	class FramePool
	{
	public:
		FramePool() = delete;
		FramePool(int size);
		~FramePool();

	public:
		Frame *gain();				 // 获取一个实例
		Frame *take();
		void giveBack(Frame *frame); // 归还一个实例
	private:
		int mSize;
		std::queue<Frame *> mFrameQ;
		std::mutex mFrameQ_mtx;
		void clearFrameQ();
	};

}

#endif // ANALYZER_FRAME_H