#include "AvPushStream.h"
#include "Config.h"
#include "Utils/Log.h"
#include "Utils/Common.h"
#include "Control.h"
#include "Frame.h"
#include "Worker.h"
#include "Analyzer.h"
#include <cstdio>
extern "C"
{
#include "libswscale/swscale.h"
#include <libavutil/imgutils.h>
#include <libswresample/swresample.h>
}
#pragma warning(disable : 4996)

namespace SVAAnalyzer
{
    AvPushStream::AvPushStream(Worker *worker, Control *control) : mWorker(worker), mControl(control)
    {
        LOGI("");
    }

    AvPushStream::~AvPushStream()
    {
        LOGI("");
        closeConnect();
        clearVideoFrameQueue();
    }

    bool AvPushStream::connect()
    {
        mStopped = false;

        Control *control = mControl ? mControl : mWorker->mControl;
        std::string pushStreamUrl = control->pushStreamUrl;
        int videoWidth = control->videoWidth;
        int videoHeight = control->videoHeight;
        int videoFps = control->videoFps;

        if (avformat_alloc_output_context2(&mFmtCtx, NULL, "rtsp", pushStreamUrl.data()) < 0)
        {
            LOGI("avformat_alloc_output_context2 error: pushStreamUrl=%s", pushStreamUrl.data());
            return false;
        }

        // 修改点1：先尝试硬件编码器，再回退到软件
        const AVCodec *videoCodec = avcodec_find_encoder_by_name("h264_nvenc");
        if (!videoCodec)
        {
            LOGI("h264_nvenc not found, falling back to software H.264 encoder");
            videoCodec = avcodec_find_encoder(AV_CODEC_ID_H264);
            if (!videoCodec)
            {
                LOGI("avcodec_find_encoder error: pushStreamUrl=%s", pushStreamUrl.data());
                return false;
            }
        }
        mVideoCodecCtx = avcodec_alloc_context3(videoCodec);
        if (!mVideoCodecCtx)
        {
            LOGI("avcodec_alloc_context3 error: pushStreamUrl=%s", pushStreamUrl.data());
            return false;
        }
        // 根据分辨率和帧率设置更保守的默认码率，避免多路推流时编码器打满
        const int pixels = videoWidth * videoHeight;
        int bit_rate = 4 * 1024 * 1024;
        if (pixels >= 3840 * 2160)
        {
            bit_rate = 12 * 1024 * 1024;
        }
        else if (pixels >= 2560 * 1440)
        {
            bit_rate = 8 * 1024 * 1024;
        }
        else if (pixels >= 1920 * 1080)
        {
            bit_rate = 6 * 1024 * 1024;
        }
        else if (pixels >= 1280 * 720)
        {
            bit_rate = 4 * 1024 * 1024;
        }
        else
        {
            bit_rate = 2 * 1024 * 1024;
        }
        if (videoFps > 30)
        {
            bit_rate = bit_rate * videoFps / 30;
        }
        if (bit_rate < 2 * 1024 * 1024)
        {
            bit_rate = 2 * 1024 * 1024;
        }
        if (bit_rate > 16 * 1024 * 1024)
        {
            bit_rate = 16 * 1024 * 1024;
        }
        // CBR：Constant BitRate - 固定比特率
        //        mVideoCodecCtx->flags |= AV_CODEC_FLAG_QSCALE;
        //        mVideoCodecCtx->bit_rate = bit_rate;
        //        mVideoCodecCtx->rc_min_rate = bit_rate;
        //        mVideoCodecCtx->rc_max_rate = bit_rate;
        //        mVideoCodecCtx->bit_rate_tolerance = bit_rate;

        // VBR
        mVideoCodecCtx->flags |= AV_CODEC_FLAG_QSCALE;
        mVideoCodecCtx->rc_min_rate = bit_rate;
        mVideoCodecCtx->rc_max_rate = bit_rate;
        mVideoCodecCtx->bit_rate = bit_rate;
        mVideoCodecCtx->bit_rate_tolerance = bit_rate / 2;

        // ABR：Average Bitrate - 平均码率
        //        mDstVimVideoCodecCtxdeoCodecCtx->bit_rate = bit_rate;

        mVideoCodecCtx->codec_id = videoCodec->id;
        mVideoCodecCtx->pix_fmt = AV_PIX_FMT_YUV420P;
        mVideoCodecCtx->codec_type = AVMEDIA_TYPE_VIDEO;
        mVideoCodecCtx->width = videoWidth;
        mVideoCodecCtx->height = videoHeight;
        mVideoCodecCtx->time_base = {1, videoFps};
        //        mDstVideoCodecCtx->framerate = { mDstVideoFps, 1 };
        mVideoCodecCtx->gop_size = 25;
        mVideoCodecCtx->max_b_frames = 0;
        mVideoCodecCtx->thread_count = 5;
        mVideoCodecCtx->flags |= AV_CODEC_FLAG_GLOBAL_HEADER; // 添加PPS、SPS
        AVDictionary *video_codec_options = NULL;

        // 修改点2：根据实际使用的编码器设置不同选项
        if (mVideoCodecCtx->codec_id == AV_CODEC_ID_H264)
        {
            if (strcmp(videoCodec->name, "h264_nvenc") == 0)
            {
                // NVENC 硬件编码器选项：低延迟、恒定码率
                char bitrate_k[32] = {0};
                char maxrate_k[32] = {0};
                char bufsize_k[32] = {0};
                std::snprintf(bitrate_k, sizeof(bitrate_k), "%dk", bit_rate / 1024);
                std::snprintf(maxrate_k, sizeof(maxrate_k), "%dk", bit_rate / 1024);
                std::snprintf(bufsize_k, sizeof(bufsize_k), "%dk", (bit_rate / 1024) * 2);
                av_dict_set(&video_codec_options, "preset", "llhp", 0); // 低延迟高性能
                av_dict_set(&video_codec_options, "tune", "ll", 0);     // 低延迟
                av_dict_set(&video_codec_options, "rc", "cbr", 0);      // 恒定码率
                av_dict_set(&video_codec_options, "b", bitrate_k, 0);
                av_dict_set(&video_codec_options, "maxrate", maxrate_k, 0);
                av_dict_set(&video_codec_options, "bufsize", bufsize_k, 0);
            }
            else
            {
                // 软件x264选项
                av_dict_set(&video_codec_options, "preset", "superfast", 0);
                av_dict_set(&video_codec_options, "tune", "zerolatency", 0);
            }
        }
        // H.265 部分（若有需要可类似处理）
        if (mVideoCodecCtx->codec_id == AV_CODEC_ID_H265)
        {
            av_dict_set(&video_codec_options, "preset", "ultrafast", 0);
            av_dict_set(&video_codec_options, "tune", "zero-latency", 0);
        }

        if (avcodec_open2(mVideoCodecCtx, videoCodec, &video_codec_options) < 0)
        {
            LOGI("avcodec_open2 error: pushStreamUrl=%s", pushStreamUrl.data());
            return false;
        }
        mVideoStream = avformat_new_stream(mFmtCtx, videoCodec);
        if (!mVideoStream)
        {
            LOGI("avformat_new_stream error: pushStreamUrl=%s", pushStreamUrl.data());
            return false;
        }
        mVideoStream->id = mFmtCtx->nb_streams - 1;
        // stream的time_base参数非常重要，它表示将现实中的一秒钟分为多少个时间基, 在下面调用avformat_write_header时自动完成
        avcodec_parameters_from_context(mVideoStream->codecpar, mVideoCodecCtx);
        mVideoIndex = mVideoStream->id;
        // init video end

        av_dump_format(mFmtCtx, 0, pushStreamUrl.data(), 1);

        // open output url
        if (!(mFmtCtx->oformat->flags & AVFMT_NOFILE))
        {
            if (avio_open(&mFmtCtx->pb, pushStreamUrl.data(), AVIO_FLAG_WRITE) < 0)
            {
                LOGI("avio_open error: pushStreamUrl=%s", pushStreamUrl.data());
                return false;
            }
        }

        AVDictionary *fmt_options = NULL;
        // av_dict_set(&fmt_options, "bufsize", "1024", 0);
        av_dict_set(&fmt_options, "rw_timeout", "30000000", 0); // 设置rtmp/http-flv连接超时（单位 us）
        av_dict_set(&fmt_options, "stimeout", "30000000", 0);   // 设置rtsp连接超时（单位 us）
        av_dict_set(&fmt_options, "rtsp_transport", "tcp", 0);
        //        av_dict_set(&fmt_options, "fflags", "discardcorrupt", 0);

        // av_dict_set(&fmt_options, "muxdelay", "0.1", 0);
        // av_dict_set(&fmt_options, "tune", "zerolatency", 0);

        mFmtCtx->video_codec_id = mFmtCtx->oformat->video_codec;

        if (avformat_write_header(mFmtCtx, &fmt_options) < 0)
        { // 调用该函数会将所有stream的time_base，自动设置一个值，通常是1/90000或1/1000，这表示一秒钟表示的时间基长度
            LOGI("avformat_write_header error: pushStreamUrl=%s", pushStreamUrl.data());
            return false;
        }

        mConnectCount++;

        return true;
    }
    bool AvPushStream::reConnect()
    {
        if (mConnectCount <= 100)
        {
            closeConnect();

            if (connect())
            {
                return true;
            }
            else
            {
                return false;
            }
        }
        return false;
    }
    void AvPushStream::closeConnect()
    {
        LOGI("");

        clearVideoFrameQueue();
        mVideoFrameQ_cv.notify_all();

        std::this_thread::sleep_for(std::chrono::milliseconds(1));

        if (mFmtCtx)
        {
            // 推流需要释放start
            if (mFmtCtx && !(mFmtCtx->oformat->flags & AVFMT_NOFILE))
            {
                avio_close(mFmtCtx->pb);
            }
            // 推流需要释放end

            avformat_free_context(mFmtCtx);
            mFmtCtx = NULL;
        }

        if (mVideoCodecCtx)
        {
            if (mVideoCodecCtx->extradata)
            {
                av_free(mVideoCodecCtx->extradata);
                mVideoCodecCtx->extradata = NULL;
            }

            avcodec_close(mVideoCodecCtx);
            avcodec_free_context(&mVideoCodecCtx);
            mVideoCodecCtx = NULL;
            mVideoIndex = -1;
        }
    }

    void AvPushStream::addVideoFrame(Frame *frame)
    {
        std::unique_lock<std::mutex> lock(mVideoFrameQ_mtx);
        if (mVideoFrameQ.size() >= mVideoFrameQCapacity)
        {
            Frame *dropped = mVideoFrameQ.front();
            mVideoFrameQ.pop();
            mWorker->mVideoFramePool->giveBack(dropped);
        }
        mVideoFrameQ.push(frame);
        lock.unlock();
        mVideoFrameQ_cv.notify_one();
    }
    int AvPushStream::getVideoFrameQSize()
    {
        int size = 0;
        mVideoFrameQ_mtx.lock();
        size = mVideoFrameQ.size();
        mVideoFrameQ_mtx.unlock();

        return size;
    }

    bool AvPushStream::getVideoFrame(Frame *&frame)
    {

        std::unique_lock<std::mutex> lock(mVideoFrameQ_mtx);
        mVideoFrameQ_cv.wait_for(lock, std::chrono::milliseconds(20), [this]() {
            return !mVideoFrameQ.empty() || !mWorker->getState() || mStopped.load();
        });

        if (!mVideoFrameQ.empty())
        {
            frame = mVideoFrameQ.front();
            mVideoFrameQ.pop();
            return true;
        }
        return false;
    }
    void AvPushStream::clearVideoFrameQueue()
    {

        mVideoFrameQ_mtx.lock();
        while (!mVideoFrameQ.empty())
        {
            Frame *frame = mVideoFrameQ.front();
            mVideoFrameQ.pop();
            mWorker->mVideoFramePool->giveBack(frame);
        }
        mVideoFrameQ_mtx.unlock();
    }
    void AvPushStream::notifyStop()
    {
        mStopped = true;
        mVideoFrameQ_cv.notify_all();
    }
    void AvPushStream::handleEncodeVideo()
    {
        if (!mVideoCodecCtx || !mVideoStream || !mFmtCtx || !mWorker || !mWorker->mVideoFramePool)
        {
            LOGE("encode prerequisites not ready");
            return;
        }

        Control *control = mControl ? mControl : mWorker->mControl;
        int width = control->videoWidth;
        int height = control->videoHeight;

        Frame *videoFrame = NULL; // 未编码的视频帧（bgr格式）

        AVFrame *frame_yuv420p = av_frame_alloc();
        frame_yuv420p->format = mVideoCodecCtx->pix_fmt;
        frame_yuv420p->width = width;
        frame_yuv420p->height = height;

        int frame_yuv420p_buff_size = av_image_get_buffer_size(AV_PIX_FMT_YUV420P, width, height, 1);
        uint8_t *frame_yuv420p_buff = (uint8_t *)av_malloc(frame_yuv420p_buff_size);
        av_image_fill_arrays(frame_yuv420p->data, frame_yuv420p->linesize,
                             frame_yuv420p_buff,
                             AV_PIX_FMT_YUV420P,
                             width, height, 1);
        SwsContext *sws_ctx_bgr2yuv = sws_getContext(width, height,
                                 AV_PIX_FMT_BGR24,
                                 width, height,
                                 AV_PIX_FMT_YUV420P,
                                 SWS_FAST_BILINEAR, nullptr, nullptr, nullptr);

        AVPacket *pkt = av_packet_alloc(); // 编码后的视频帧
        int64_t encodeSuccessCount = 0;
        int64_t frameCount = 0;

        int ret = -1;
        while (mWorker->getState() && !mStopped.load())
        {
            if (getVideoFrame(videoFrame))
            {
                if (!videoFrame || !videoFrame->getBuf())
                {
                    if (videoFrame)
                    {
                        mWorker->mVideoFramePool->giveBack(videoFrame);
                    }
                    continue;
                }

                const uint8_t *src_slice[4] = {videoFrame->getBuf(), nullptr, nullptr, nullptr};
                int src_stride[4] = {width * 3, 0, 0, 0};

                sws_scale(sws_ctx_bgr2yuv,
                          src_slice,
                          src_stride,
                          0,
                          height,
                          frame_yuv420p->data,
                          frame_yuv420p->linesize);
                mWorker->mVideoFramePool->giveBack(videoFrame);

                frame_yuv420p->pts = frame_yuv420p->pkt_dts = av_rescale_q_rnd(frameCount,
                                                                               mVideoCodecCtx->time_base,
                                                                               mVideoStream->time_base,
                                                                               (AVRounding)(AV_ROUND_NEAR_INF | AV_ROUND_PASS_MINMAX));

                frame_yuv420p->pkt_duration = av_rescale_q_rnd(1,
                                                               mVideoCodecCtx->time_base,
                                                               mVideoStream->time_base,
                                                               (AVRounding)(AV_ROUND_NEAR_INF | AV_ROUND_PASS_MINMAX));

                frame_yuv420p->pkt_pos = -1;

                ret = avcodec_send_frame(mVideoCodecCtx, frame_yuv420p);
                if (ret >= 0)
                {
                    while (true)
                    {
                        ret = avcodec_receive_packet(mVideoCodecCtx, pkt);
                        if (ret == AVERROR(EAGAIN) || ret == AVERROR_EOF)
                        {
                            break;
                        }
                        if (ret < 0)
                        {
                            LOGE("avcodec_receive_packet error : ret=%d", ret);
                            break;
                        }

                        encodeSuccessCount++;
                        pkt->stream_index = mVideoIndex;
                        pkt->pos = -1;
                        pkt->duration = frame_yuv420p->pkt_duration;

                        ret = av_interleaved_write_frame(mFmtCtx, pkt);
                        av_packet_unref(pkt);

                        if (ret < 0)
                        {
                            LOGE("av_interleaved_write_frame error : ret=%d", ret);
                            break;
                        }
                    }
                }
                else
                {
                    if (ret != AVERROR(EAGAIN))
                    {
                        LOGE("avcodec_send_frame error : ret=%d", ret);
                    }
                }

                frameCount++;
            }
            else
            {
                continue;
            }
        }

        // av_write_trailer(mFmtCtx);//写文件尾

        av_packet_free(&pkt);
        pkt = NULL;

        av_free(frame_yuv420p_buff);
        frame_yuv420p_buff = NULL;
        sws_freeContext(sws_ctx_bgr2yuv);

        av_frame_free(&frame_yuv420p);
        // av_frame_unref(frame_yuv420p);
        frame_yuv420p = NULL;
    }
    void AvPushStream::encodeVideoThread(void *arg)
    {
        AvPushStream *pushStream = (AvPushStream *)arg;
        pushStream->handleEncodeVideo();
    }

}