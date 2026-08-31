#include "Worker.h"
#include "Algorithm.h"
#include "Analyzer.h"
#include "AvPullStream.h"
#include "AvPushStream.h"
#include "Config.h"
#include "Control.h"
#include "Frame.h"
#include "BehaviorEvaluator.h"
#include "GenerateAlarmVideo.h"
#include "Scheduler.h"
#include "Utils/Common.h"
#include "Utils/Log.h"
#include <chrono>
#include <algorithm>
#include <cstdio>
#include <filesystem>
extern "C"
{
#include <libavutil/imgutils.h>
#include <libavutil/hwcontext.h>
#include <libswscale/swscale.h>
}

namespace SVAAnalyzer
{
    namespace
    {
        bool saveDetectEventSnapshot(Config *config,
                                     const std::string &controlCode,
                                     const cv::Mat &image,
                                     int64_t timestampMs,
                                     std::string &imagePath)
        {
            imagePath.clear();
            if (!config || config->uploadDir.empty() || controlCode.empty() || image.empty())
            {
                return false;
            }

            imagePath = "alarm/" + controlCode + "/detect_event_" + std::to_string(timestampMs) + "_" + std::to_string(getRandomInt()) + ".jpg";
            const std::string absPath = config->uploadDir + "/" + imagePath;
            try
            {
                std::filesystem::create_directories(std::filesystem::path(absPath).parent_path());
            }
            catch (const std::filesystem::filesystem_error &e)
            {
                LOGE("create detect snapshot dir failed: control=%s err=%s", controlCode.c_str(), e.what());
                imagePath.clear();
                return false;
            }

            if (!cv::imwrite(absPath, image))
            {
                LOGE("save detect snapshot failed: control=%s path=%s", controlCode.c_str(), absPath.c_str());
                imagePath.clear();
                return false;
            }
            return true;
        }

        Frame *cloneFrameForAlarm(Frame *src)
        {
            if (!src || !src->getBuf() || src->getWidth() <= 0 || src->getHeight() <= 0 || src->getChannels() <= 0)
            {
                return nullptr;
            }

            const int frameSize = src->getWidth() * src->getHeight() * src->getChannels();
            Frame *dst = new Frame(frameSize);
            dst->setData(src->getBuf(), src->getWidth(), src->getHeight(), src->getChannels());
            dst->happen = src->happen;
            dst->happenScore = src->happenScore;
            return dst;
        }
    }

    // Per-Control runtime owned by one stream Worker. The stream Worker shares
    // pull/decode resources; this runtime keeps each Control's analyzer, alarm
    // cache and optional push stream isolated for teaching clarity.
    struct WorkerControlRuntime
    {
        Control *control = nullptr;
        Analyzer *analyzer = nullptr;
        AvPushStream *pushStream = nullptr;
        std::thread *alarmThread = nullptr;
        std::thread *encodeThread = nullptr;
        std::queue<Frame *> videoFrameQueue;
        std::mutex videoFrameQueueMtx;
        std::condition_variable videoFrameQueueCv;
        bool stopping = false;
        int64_t lastInferTimestampMs = 0;
        int64_t lastSnapshotTimestampMs = 0;
        int continuityCheckCount = 0;
        int64_t continuityCheckStartMs = 0;
    };

    Worker::Worker(Scheduler *scheduler, Control *control) : mControl(control),
                                                              mScheduler(scheduler),
                                                              mPullStream(nullptr),
                                                              mPushStream(nullptr),
                                                              mAnalyzer(nullptr),
                                                              mVideoFramePool(nullptr)
    {
        mControl->startTimestamp = getCurTimestamp();
    }

    Worker::~Worker()
    {
        LOGI("%s()", __FUNCTION__);

        setState(false);
            remove();

        for (auto *th : mThreads)
        {
            if (th->joinable())
            {
                th->join();
            }
            delete th;
        }
        mThreads.clear();

        clearAllControlRuntimes();

        if (mPullStream)
        {
            delete mPullStream;
            mPullStream = nullptr;
        }
        if (mVideoFramePool)
        {
            delete mVideoFramePool;
            mVideoFramePool = nullptr;
        }
    }

    bool Worker::init(std::string &msg)
    {
        LOGI("%s()", __FUNCTION__);

        mPullStream = new AvPullStream(this);
        if (!mPullStream->connect())
        {
            msg = "pull stream connect error";
            return false;
        }

        int videoBgrSize = mControl->videoHeight * mControl->videoWidth * mControl->videoChannel;
        mVideoFramePool = new FramePool(videoBgrSize);
        mState = true;

        {
            std::lock_guard<std::mutex> lock(mControlRuntimesMtx);
            if (!addControlLocked(mControl, msg))
            {
                return false;
            }
        }

        mThreads.reserve(2);

        mThreads.push_back(new std::thread(AvPullStream::readThread, mPullStream));
        mThreads.push_back(new std::thread(Worker::decodeVideoThread, this));

        return true;
    }

    bool Worker::start(std::string &msg)
    {
        msg.clear();
        return init(msg);
    }

    void Worker::remove()
    {
        setState(false);
        std::vector<WorkerControlRuntime *> runtimes;
        {
            std::lock_guard<std::mutex> lock(mControlRuntimesMtx);
            for (auto &entry : mControlRuntimes)
            {
                runtimes.push_back(entry.second);
            }
        }
        for (WorkerControlRuntime *runtime : runtimes)
        {
            if (!runtime)
            {
                continue;
            }
            runtime->stopping = true;
            runtime->videoFrameQueueCv.notify_all();
            if (runtime->pushStream)
            {
                runtime->pushStream->notifyStop();
            }
        }
    }

    bool Worker::addControl(Control *control, std::string &msg)
    {
        if (!control)
        {
            msg = "control is null";
            return false;
        }
        std::lock_guard<std::mutex> lock(mControlRuntimesMtx);
        return addControlLocked(control, msg);
    }

    bool Worker::addControlLocked(Control *control, std::string &msg)
    {
        if (!control)
        {
            msg = "control is null";
            return false;
        }
        if (mControlRuntimes.find(control->code) != mControlRuntimes.end())
        {
            msg = "the control is running";
            return false;
        }
        if (mControl && control != mControl)
        {
            control->videoWidth = mControl->videoWidth;
            control->videoHeight = mControl->videoHeight;
            control->videoFps = mControl->videoFps;
            control->videoChannel = mControl->videoChannel;
            control->videoIndex = mControl->videoIndex;
            if (!control->parseRecognitionRegion())
            {
                msg = "parseRecognitionRegion error";
                return false;
            }
        }

        WorkerControlRuntime *runtime = new WorkerControlRuntime();
        runtime->control = control;
        runtime->control->startTimestamp = getCurTimestamp();
        runtime->continuityCheckStartMs = getCurTime();
        runtime->analyzer = new Analyzer(mScheduler, control);

        if (control->pushStream)
        {
            runtime->pushStream = new AvPushStream(this, control);
            if (!runtime->pushStream->connect())
            {
                deleteControlRuntime(runtime);
                msg = "push stream connect error";
                return false;
            }
            runtime->alarmThread = new std::thread([this, runtime]() { this->handleGenerateAlarm(runtime); });
            if (control->videoIndex > -1)
            {
                runtime->encodeThread = new std::thread(AvPushStream::encodeVideoThread, runtime->pushStream);
            }
        }
        else
        {
            runtime->alarmThread = new std::thread([this, runtime]() { this->handleGenerateAlarm(runtime); });
        }

        mControlRuntimes.insert(std::make_pair(control->code, runtime));
        msg = "add success";
        return true;
    }

    bool Worker::removeControl(const std::string &code)
    {
        WorkerControlRuntime *runtime = nullptr;
        {
            std::lock_guard<std::mutex> lock(mControlRuntimesMtx);
            auto it = mControlRuntimes.find(code);
            if (it == mControlRuntimes.end())
            {
                return false;
            }
            runtime = it->second;
            mControlRuntimes.erase(it);
            if (mControl && mControl->code == code)
            {
                mControl = mControlRuntimes.empty() ? nullptr : mControlRuntimes.begin()->second->control;
            }
        }
        deleteControlRuntime(runtime);
        return true;
    }

    Control *Worker::getControl(const std::string &code)
    {
        std::lock_guard<std::mutex> lock(mControlRuntimesMtx);
        auto it = mControlRuntimes.find(code);
        return it == mControlRuntimes.end() ? nullptr : it->second->control;
    }

    int Worker::getControlCount()
    {
        std::lock_guard<std::mutex> lock(mControlRuntimesMtx);
        return static_cast<int>(mControlRuntimes.size());
    }

    std::vector<Control *> Worker::snapshotControls()
    {
        std::vector<Control *> controls;
        std::lock_guard<std::mutex> lock(mControlRuntimesMtx);
        controls.reserve(mControlRuntimes.size());
        for (const auto &entry : mControlRuntimes)
        {
            controls.push_back(entry.second->control);
        }
        return controls;
    }

    void Worker::generateAlarmThread(void *arg)
    {
        (void)arg;
    }

    void Worker::handleGenerateAlarm(WorkerControlRuntime *runtime)
    {
        if (!runtime || !runtime->control)
        {
            return;
        }
        std::queue<Frame *> cacheV;
        std::queue<Frame *> happenV;
        FramePool *const framePool = mVideoFramePool;
        Control *control = runtime->control;

        int64_t last_alarm_timestamp = 0;
        bool happening = false;
        const int prefix_size = 30;
        const int max_alarm_seconds = 12;
        const int max_happen_frames = std::max(prefix_size,
                                               std::max(1, control->videoFps) * max_alarm_seconds);

        auto flushHappenFrames = [&]() {
            if (happenV.empty())
            {
                return;
            }

            Alarm *alarm = new Alarm();
            alarm->controlCode = control->code;
            alarm->width = control->videoWidth;
            alarm->height = control->videoHeight;
            alarm->fps = control->videoFps;
            alarm->happenImageIndex = 0;
            alarm->frames.reserve(happenV.size());

            int firstHappenIndex = -1;
            int frameIndex = 0;

            while (!happenV.empty())
            {
                Frame *p = happenV.front();
                happenV.pop();
                if (firstHappenIndex < 0 && p->happen)
                {
                    firstHappenIndex = frameIndex;
                }
                Frame *alarmFrame = cloneFrameForAlarm(p);
                if (alarmFrame)
                {
                    alarm->frames.push_back(alarmFrame);
                }
                if (framePool)
                {
                    framePool->giveBack(p);
                }
                else
                {
                    delete p;
                }
                ++frameIndex;
            }

            if (firstHappenIndex < 0)
            {
                delete alarm;
                return;
            }

            alarm->happenImageIndex = firstHappenIndex;

            const int64_t nowTs = getCurTimestamp();
            alarm->happenTimestamp = nowTs;
            last_alarm_timestamp = nowTs;
            mScheduler->addAlarm(alarm);
        };

        while (getState() && !runtime->stopping)
        {
            Frame *videoFrame = nullptr;
            if (!getVideoFrame(runtime, videoFrame))
            {
                if (runtime->stopping)
                {
                    break;
                }
                continue;
            }

            if (happening)
            {
                if (videoFrame->happen)
                {
                    happenV.push(videoFrame);

                    // Bound memory for long-running events by segmenting alarm frames.
                    if (static_cast<int>(happenV.size()) >= max_happen_frames)
                    {
                        flushHappenFrames();
                    }
                }
                else
                {
                    flushHappenFrames();
                    happening = false;
                    if (framePool)
                    {
                        framePool->giveBack(videoFrame);
                    }
                }
                continue;
            }

            if (!cacheV.empty() && static_cast<int>(cacheV.size()) >= prefix_size)
            {
                Frame *head = cacheV.front();
                cacheV.pop();
                if (framePool)
                {
                    framePool->giveBack(head);
                }
            }
            cacheV.push(videoFrame);

            if (videoFrame->happen &&
                static_cast<int>(cacheV.size()) >= prefix_size &&
                    (getCurTimestamp() - last_alarm_timestamp) > control->minInterval)
            {
                happening = true;
                while (!cacheV.empty())
                {
                    Frame *p = cacheV.front();
                    cacheV.pop();
                    happenV.push(p);
                }
            }
        }

        while (!happenV.empty())
        {
            Frame *p = happenV.front();
            happenV.pop();
            if (framePool)
            {
                framePool->giveBack(p);
            }
        }
        while (!cacheV.empty())
        {
            Frame *p = cacheV.front();
            cacheV.pop();
            if (framePool)
            {
                framePool->giveBack(p);
            }
        }
    }

    void Worker::decodeVideoThread(void *arg)
    {
        Worker *worker = (Worker *)arg;
        worker->handleDecodeVideo();
    }

    void Worker::handleDecodeVideo()
    {
        int width = mPullStream->mVideoCodecCtx->width;
        int height = mPullStream->mVideoCodecCtx->height;
        const std::string &nodeCode = mScheduler->getConfig()->code;

        AVPacket pkt;
        int pktQSize = 0;

        AVFrame *frame_yuv420p = av_frame_alloc();
        AVFrame *frame_bgr = av_frame_alloc();
        AVFrame *frame_sw = av_frame_alloc();

        int frame_bgr_buff_size = av_image_get_buffer_size(AV_PIX_FMT_BGR24, width, height, 1);
        uint8_t *frame_bgr_buff = (uint8_t *)av_malloc(frame_bgr_buff_size);
        av_image_fill_arrays(frame_bgr->data, frame_bgr->linesize, frame_bgr_buff, AV_PIX_FMT_BGR24, width, height, 1);

        SwsContext *sws_ctx_yuv420p2bgr = sws_getContext(width, height,
                                                         mPullStream->mVideoCodecCtx->pix_fmt,
                                                         mPullStream->mVideoCodecCtx->width,
                                                         mPullStream->mVideoCodecCtx->height,
                                                         AV_PIX_FMT_BGR24,
                                                         SWS_BICUBIC, nullptr, nullptr, nullptr);
        int sws_src_w = width;
        int sws_src_h = height;
        AVPixelFormat sws_src_fmt = mPullStream->mVideoCodecCtx->pix_fmt;

        bool cur_is_check = false;
        int continuity_check_max_time = 6000;

        int ret = -1;
        int64_t frameCount = 0;
        bool happen = false;
        float happenScore = 0.0;
        std::vector<DetectObject> happenDetects;
        const cv::Scalar overlayColor(0, 255, 255);
        const cv::Scalar textColor(0, 0, 0);

        while (getState())
        {
            if (!mPullStream->getVideoPkt(pkt, pktQSize))
            {
                continue;
            }

            if (mControl->videoIndex > -1)
            {
                ret = avcodec_send_packet(mPullStream->mVideoCodecCtx, &pkt);
                if (ret == 0)
                {
                    ret = avcodec_receive_frame(mPullStream->mVideoCodecCtx, frame_yuv420p);
                    if (ret == 0)
                    {
                        frameCount++;

                        AVFrame *src_frame = frame_yuv420p;
                        if (frame_yuv420p->hw_frames_ctx)
                        {
                            av_frame_unref(frame_sw);
                            if (av_hwframe_transfer_data(frame_sw, frame_yuv420p, 0) < 0)
                            {
                                LOGE("Failed to transfer hw frame to system memory");
                                av_packet_unref(&pkt);
                                av_frame_unref(frame_yuv420p);
                                continue;
                            }
                            src_frame = frame_sw;
                        }

                        if (!src_frame || !src_frame->data[0])
                        {
                            LOGE("Invalid frame data pointers");
                            av_packet_unref(&pkt);
                            av_frame_unref(frame_yuv420p);
                            av_frame_unref(frame_sw);
                            continue;
                        }

                        AVPixelFormat src_fmt = static_cast<AVPixelFormat>(src_frame->format);
                        if (!sws_ctx_yuv420p2bgr || sws_src_w != src_frame->width || sws_src_h != src_frame->height || sws_src_fmt != src_fmt)
                        {
                            if (sws_ctx_yuv420p2bgr)
                            {
                                sws_freeContext(sws_ctx_yuv420p2bgr);
                                sws_ctx_yuv420p2bgr = nullptr;
                            }
                            sws_ctx_yuv420p2bgr = sws_getContext(src_frame->width,
                                                                 src_frame->height,
                                                                 src_fmt,
                                                                 mPullStream->mVideoCodecCtx->width,
                                                                 mPullStream->mVideoCodecCtx->height,
                                                                 AV_PIX_FMT_BGR24,
                                                                 SWS_BICUBIC,
                                                                 nullptr,
                                                                 nullptr,
                                                                 nullptr);
                            sws_src_w = src_frame->width;
                            sws_src_h = src_frame->height;
                            sws_src_fmt = src_fmt;
                        }

                        if (!sws_ctx_yuv420p2bgr)
                        {
                            LOGE("Failed to create sws context");
                            av_packet_unref(&pkt);
                            av_frame_unref(frame_yuv420p);
                            av_frame_unref(frame_sw);
                            continue;
                        }

                        int scale_ret = sws_scale(sws_ctx_yuv420p2bgr,
                                                  src_frame->data,
                                                  src_frame->linesize,
                                                  0,
                                                  src_frame->height,
                                                  frame_bgr->data,
                                                  frame_bgr->linesize);
                        if (scale_ret <= 0)
                        {
                            LOGE("Failed to scale frame: %d", scale_ret);
                            av_packet_unref(&pkt);
                            av_frame_unref(frame_yuv420p);
                            av_frame_unref(frame_sw);
                            continue;
                        }

                        cv::Mat sourceImage(mControl->videoHeight, mControl->videoWidth, CV_8UC3, frame_bgr->data[0]);

                        const bool isKeyframe = (pkt.flags & AV_PKT_FLAG_KEY) != 0;
                        std::lock_guard<std::mutex> runtimeLock(mControlRuntimesMtx);
                        for (auto &runtimeEntry : mControlRuntimes)
                        {
                            WorkerControlRuntime *runtime = runtimeEntry.second;
                            if (!runtime || runtime->stopping || !runtime->control || !runtime->analyzer)
                            {
                                continue;
                            }
                            Control &control = *runtime->control;
                            cv::Mat image = sourceImage.clone();

                        happen = false;
                        happenScore = 0.0;

                        bool shouldInfer = true;
                        if (control.checkFps > 0.0f)
                        {
                            const int64_t nowMs = getCurTimestamp();
                            const double intervalMs = 1000.0 / static_cast<double>(control.checkFps);
                            if (runtime->lastInferTimestampMs > 0 &&
                                static_cast<double>(nowMs - runtime->lastInferTimestampMs) < intervalMs)
                            {
                                shouldInfer = false;
                            }
                            else
                            {
                                runtime->lastInferTimestampMs = nowMs;
                            }
                        }

                        std::vector<AggregateBehaviorMatch> aggMatches;
                        if (shouldInfer)
                        {
                            cur_is_check = runtime->analyzer->handleVideoFrame(frameCount, image, happenDetects, happen, happenScore, isKeyframe);

                            if (cur_is_check)
                            {
                                runtime->continuityCheckCount += 1;
                            }

                            /*
                             * ==========================================================
                             *  行为分析处理流程
                             * ==========================================================
                             *
                             * 这是 easySVA-server 最核心的教学内容。在 YOLO 检测出目标边界框后，
                             * 我们通过三个步骤将"裸检测框"转化为"行为事件"：
                             *
                             * 【步骤1】时态追踪 (Temporal Tracking)
                             *   ─────────────────────────────────────
             *   问题：YOLO 每帧独立检测，不知道"这一帧的人和上一帧的人是不是同一个"。
             *   解法：IoU（交并比）贪心匹配。
             *   算法：
             *     for 每个新检测框:
             *       找与已有 Track 的 IoU 最高的 Track
             *       如果 IoU > 30% 且类别相同 → 匹配，更新 Track 参数
             *       否则 → 创建新 Track (分配新 trackId)
             *     没有匹配的 Track → 标记为 Lost/Removed
             *   产出：
             *     - trackId: 目标唯一标识
             *     - trail[]: 中心点轨迹历史 (最近32个点)
             *     - speedPxPerSec: 当前速度 (像素/秒)
             *     - directionAngleDeg: 运动方向角度
             *     - dwellMs: 目标存在时长
             *     - regionStates: 各区域的进出/停留状态
             *
             * 【步骤2】原子行为评估 (Atomic Behavior Evaluation)
             *   ──────────────────────────────────────────
             *   问题：单个目标是否触发了某个行为规则？
             *   解法：遍历 control.behaviorRules[]，对每个 DetectObject 逐一判断。
             *   支持的行为类型：
             *     - cross_line:      目标轨迹是否穿越了某条线？
             *     - enter_region:    目标是否刚进入某个区域？
             *     - exit_region:     目标是否刚离开某个区域？
             *     - dwell:           目标在区域内停留超过阈值？
             *     - low_speed:       目标在区域内移动速度低于阈值？
             *     - loitering:       目标在区域内小范围徘徊？
             *     - sleep:           目标静止 + 宽高比异常（躺卧）？
             *     - direction_move:  目标运动方向匹配指定角度？
             *   产出：
             *      BehaviorDecision { ruleId, behaviorType, regionId, lineId, ... }
             *
             * 【步骤3】聚合行为评估 (Aggregate Behavior Evaluation)
             *   ──────────────────────────────────────────────
             *   问题：不是单个目标，而是看一帧中所有目标的整体情况？
             *   解法：遍历所有目标，按规则条件计数。
             *   支持的行为类型：
             *     - count_threshold: 区域内目标数 >= N → 触发
             *     - absence:         区域内无目标持续 >= T 毫秒 → 触发
             *     - occupancy:       区域内有目标持续 >= T 毫秒 → 触发
             *   产出：
             *      AggregateBehaviorMatch { ruleId, behaviorType, objectCount, ... }
             *
             * 【数据流图】
             *   ┌─────────────────┐
             *   │ YOLO 检测结果    │  class_name, x1, y1, x2, y2, class_score
             *   └────────┬────────┘
             *            │
             *   ┌────────▼────────┐
             *   │ TemporalProcessor│  每个 DetectObject 附加 trackId, trail, speed, 
             *   │ (IoU贪心匹配)    │  direction, regionStates 等追踪元数据
             *   └────────┬────────┘
             *            │
             *   ┌────────▼────────┐
             *   │ BehaviorEvaluator│  每个 DetectObject 附加 ruleId, behaviorType,
             *   │ (逐一规则判断)    │  regionId, lineId 等行为分析结果
             *   └────────┬────────┘
             *            │
             *   ┌────────▼────────┐
             *   │ AggregateEval   │  帧级统计，产出 aggregateBehaviors 列表
             *   │ (帧级计数判断)   │
             *   └────────┬────────┘
             *            │
             *   ┌────────▼────────┐
             *   │ 构建事件上报     │  DetectFrameEvent → HTTP POST → 后端
             *   └─────────────────┘
             */
                            {
                                // 步骤1: 收集指针并做时态追踪
                                std::vector<DetectObject *> detectPtrs;
                                detectPtrs.reserve(happenDetects.size());
                                for (size_t di = 0; di < happenDetects.size(); ++di)
                                {
                                    detectPtrs.push_back(&happenDetects[di]);
                                }
                                
                                // 时态追踪：给每个检测框分配 trackId，计算速度/轨迹/区域状态
                                mScheduler->updateTemporalTracks(control, control.streamCode, detectPtrs, getCurTime());
                                
                                // 步骤2: 对每个追踪到的目标做原子行为规则评估
                                for (size_t di = 0; di < happenDetects.size(); ++di)
                                {
                                    DetectObject &detect = happenDetects[di];
                                    detect.happen = false;
                                    detect.ruleId.clear();
                                    detect.customEventName.clear();
                                    detect.behaviorType.clear();
                                    detect.regionId.clear();
                                    detect.regionName.clear();
                                    detect.lineId.clear();
                                    detect.lineName.clear();
                                    detect.crossingDirection.clear();
                                    detect.sequenceId.clear();
                                    detect.sequenceStageIndex = -1;
                                    detect.sequenceStageCount = 0;
                                    detect.sequenceLogicMode = "all";
                                    detect.relationTargetTrackId = -1;
                                    detect.relationTargetClassName.clear();
                                    detect.relationDistancePx = -1.0f;
                                    BehaviorDecision decision = evaluateAtomicBehavior(control, detect);
                                    if (decision.matched)
                                    {
                                        detect.happen = true;  // 标记为"已触发行为"
                                        detect.ruleId = decision.ruleId;
                                        detect.customEventName = decision.customEventName;
                                        detect.behaviorType = decision.behaviorType;
                                        detect.regionId = decision.regionId;
                                        detect.regionName = decision.regionName;
                                        detect.lineId = decision.lineId;
                                        detect.lineName = decision.lineName;
                                        detect.crossingDirection = decision.crossingDirection;
                                        if (decision.directionAngleDeg > 0.0)
                                        {
                                            detect.directionAngleDeg = static_cast<float>(decision.directionAngleDeg);
                                        }
                                        happen = true;
                                        happenScore = std::max(happenScore, detect.class_score);
                                    }
                                }
                                
                                // 步骤3: 帧级聚合行为规则评估
                                mScheduler->evaluateAggregateBehaviorRules(control, control.streamCode, happenDetects, getCurTime(), aggMatches);
                                mScheduler->evaluateRelationalBehaviorRules(control, control.streamCode, happenDetects, getCurTime(), aggMatches);
                                mScheduler->applySequenceBehaviorRules(control, control.streamCode, happenDetects, aggMatches, getCurTime());
                                happen = false;
                                happenScore = 0.0f;
                                for (size_t di = 0; di < happenDetects.size(); ++di)
                                {
                                    if (happenDetects[di].happen)
                                    {
                                        happen = true;
                                        happenScore = std::max(happenScore, happenDetects[di].class_score);
                                    }
                                }
                                for (size_t ai = 0; ai < aggMatches.size(); ++ai)
                                {
                                    happen = true;
                                    happenScore = std::max(happenScore, aggMatches[ai].score);
                                }
                            }
                        }
                        else
                        {
                            cur_is_check = false;
                        }

                        int64_t continuity_check_end = getCurTime();
                        if (continuity_check_end - runtime->continuityCheckStartMs > continuity_check_max_time)
                        {
                            control.checkFps = float(runtime->continuityCheckCount) / (float(continuity_check_end - runtime->continuityCheckStartMs) / 1000.0f);
                            runtime->continuityCheckCount = 0;
                            runtime->continuityCheckStartMs = continuity_check_end;
                        }
                        const float currentCheckFps = control.checkFps;

                        bool shouldPublishWsEvent = shouldInfer;
                        if (shouldPublishWsEvent)
                        {
                            const float wsEventFps = control.wsEventFps;
                            if (wsEventFps <= 0.0f)
                            {
                                shouldPublishWsEvent = false;
                            }
                            else
                            {
                                int64_t nowMs = continuity_check_end;
                                double intervalMs = 1000.0 / static_cast<double>(wsEventFps);
                                if (control.lastWsEventTimestampMs > 0 && static_cast<double>(nowMs - control.lastWsEventTimestampMs) < intervalMs)
                                {
                                    shouldPublishWsEvent = false;
                                }
                                else
                                {
                                    control.lastWsEventTimestampMs = nowMs;
                                }
                            }
                        }

                        if (shouldPublishWsEvent)
                        {
                            std::string detectEventImagePath;
                            if (happen && control.saveImageEnabled)
                            {
                                const int64_t snapshotNowMs = getCurTimestamp();
                                if (runtime->lastSnapshotTimestampMs <= 0 || snapshotNowMs - runtime->lastSnapshotTimestampMs >= 1000)
                                {
                                    runtime->lastSnapshotTimestampMs = snapshotNowMs;
                                    saveDetectEventSnapshot(mScheduler->getConfig(), control.code, image, snapshotNowMs, detectEventImagePath);
                                }
                            }

                            DetectFrameEvent *event = new DetectFrameEvent();
                            event->nodeCode = nodeCode;
                            event->controlCode = control.code;
                            event->streamCode = control.streamCode;
                            event->streamApp = control.streamApp;
                            event->streamName = control.streamName;
                            event->imagePath = detectEventImagePath;
                            event->renderMode = control.renderMode;
                            event->keyMode = control.wsEventKeyMode;
                            event->ruleMode = control.wsEventRuleMode;
                            event->requiredAlgorithms = control.wsEventRequiredAlgorithms;
                            event->ruleMinHits = control.wsEventMinHits;
                            event->ruleHitWindowMs = control.wsEventHitWindowMs;
                            event->pendingTimeoutMs = control.wsEventPendingTimeoutMs;
                            event->restartCooldownMs = control.wsEventRestartCooldownMs;
                            event->updateIntervalMs = control.wsEventUpdateIntervalMs;
                            event->endTimeoutMs = control.wsEventEndTimeoutMs;
                            event->debounceWindowMs = control.wsFrameDebounceMs;
                            event->postRetryMax = control.wsPostRetryMax;
                            event->postFailOpenThreshold = control.wsPostFailOpenThreshold;
                            event->postCooldownMs = control.wsPostCooldownMs;
                            event->saveImageEnabled = control.saveImageEnabled;
                            event->saveVideoEnabled = control.saveVideoEnabled;
                            event->timestampMs = getCurTimestamp();
                            event->frameSeq = frameCount;
                            event->width = image.cols;
                            event->height = image.rows;
                            event->checkFps = currentCheckFps;
                            event->happen = happen;
                            event->happenScore = happenScore;
                            event->aggregateBehaviors = aggMatches;
                            if (!aggMatches.empty())
                            {
                                const AggregateBehaviorMatch &firstMatch = aggMatches.front();
                                event->ruleId = firstMatch.ruleId;
                                event->customEventName = firstMatch.customEventName;
                                event->behaviorType = firstMatch.behaviorType;
                                event->regionId = firstMatch.regionId;
                                event->regionName = firstMatch.regionName;
                                event->trackId = firstMatch.trackId;
                                event->relationTargetTrackId = firstMatch.relationTargetTrackId;
                                event->relationTargetClassName = firstMatch.relationTargetClassName;
                                event->relationDistancePx = firstMatch.relationDistancePx;
                                event->aggregateCount = firstMatch.objectCount;
                                event->aggregateThresholdCount = firstMatch.thresholdCount;
                            }

                            event->objects.reserve(happenDetects.size());
                            for (const DetectObject &src : happenDetects)
                            {
                                event->objects.emplace_back();
                                DetectFrameObject &obj = event->objects.back();
                                obj.x1 = src.x1;
                                obj.y1 = src.y1;
                                obj.x2 = src.x2;
                                obj.y2 = src.y2;
                                obj.score = src.class_score;
                                obj.classId = src.class_id;
                                obj.className = src.class_name;
                                obj.algorithmCode = src.source_algorithm;
                                obj.happen = src.happen;
                                obj.trackId = src.trackId;
                                obj.firstSeenTimestampMs = src.firstSeenTimestampMs;
                                obj.lastSeenTimestampMs = src.lastSeenTimestampMs;
                                obj.dwellMs = src.dwellMs;
                                obj.trackAgeFrames = src.trackAgeFrames;
                                obj.trackMissedFrames = src.trackMissedFrames;
                                obj.speedPxPerSec = src.speedPxPerSec;
                                obj.velocityXPxPerSec = src.velocityXPxPerSec;
                                obj.velocityYPxPerSec = src.velocityYPxPerSec;
                                obj.directionAngleDeg = src.directionAngleDeg;
                                obj.motionState = src.motionState;
                                obj.trackNew = src.trackNew;
                                obj.trail = src.trail;
                                obj.ruleId = src.ruleId;
                                obj.customEventName = src.customEventName;
                                obj.behaviorType = src.behaviorType;
                                obj.regionId = src.regionId;
                                obj.regionName = src.regionName;
                                obj.lineId = src.lineId;
                                obj.lineName = src.lineName;
                                obj.crossingDirection = src.crossingDirection;
                                obj.sequenceId = src.sequenceId;
                                obj.sequenceStageIndex = src.sequenceStageIndex;
                                obj.sequenceStageCount = src.sequenceStageCount;
                                obj.sequenceLogicMode = src.sequenceLogicMode;
                                obj.relationTargetTrackId = src.relationTargetTrackId;
                                obj.relationTargetClassName = src.relationTargetClassName;
                                obj.relationDistancePx = src.relationDistancePx;
                                if (event->behaviorType.empty() && obj.happen && !obj.behaviorType.empty())
                                {
                                    event->ruleId = obj.ruleId;
                                    event->customEventName = obj.customEventName;
                                    event->behaviorType = obj.behaviorType;
                                    event->regionId = obj.regionId;
                                    event->regionName = obj.regionName;
                                    event->lineId = obj.lineId;
                                    event->lineName = obj.lineName;
                                    event->crossingDirection = obj.crossingDirection;
                                    event->sequenceId = obj.sequenceId;
                                    event->sequenceStageIndex = obj.sequenceStageIndex;
                                    event->sequenceStageCount = obj.sequenceStageCount;
                                    event->sequenceLogicMode = obj.sequenceLogicMode;
                                    event->directionAngleDeg = obj.directionAngleDeg;
                                    event->trackId = obj.trackId;
                                    event->relationTargetTrackId = obj.relationTargetTrackId;
                                    event->relationTargetClassName = obj.relationTargetClassName;
                                    event->relationDistancePx = obj.relationDistancePx;
                                }
                            }

                            mScheduler->addDetectFrameEvent(event);
                        }

                        if (control.serverOverlayEnabled)
                        {
                            cv::polylines(image, control.recognitionRegion_points, control.recognitionRegion_points.size(), overlayColor, 3, cv::LINE_AA);

                            const int border_thickness = 3;
                            const double font_scale = 0.8;
                            const int font_thickness = 2;
                            const int text_padding = 5;

                            if (!happenDetects.empty())
                            {
                                for (const DetectObject &det : happenDetects)
                                {
                                    const std::string detClassName = Control::normalizeObjectClassValue(det.class_name);
                                    bool isConfiguredObject = false;
                                    if (!control.objectCodes.empty())
                                    {
                                        isConfiguredObject = std::find(control.objectCodes.begin(), control.objectCodes.end(), detClassName) != control.objectCodes.end();
                                    }
                                    else
                                    {
                                        isConfiguredObject = detClassName == Control::normalizeObjectClassValue(control.objectCode);
                                    }
                                    if (!isConfiguredObject)
                                    {
                                        continue;
                                    }

                                    int x1 = det.x1;
                                    int y1 = det.y1;
                                    int x2 = det.x2;
                                    int y2 = det.y2;
                                    const cv::Scalar boxColor = det.happen ? overlayColor : cv::Scalar(0, 180, 0);
                                    const int boxThickness = det.happen ? border_thickness : 2;

                                    char classScoreBuf[16];
                                    std::snprintf(classScoreBuf, sizeof(classScoreBuf), "%.2f", det.class_score);
                                    std::string title = det.class_name + " " + classScoreBuf;

                                    cv::rectangle(image, cv::Rect(x1, y1, x2 - x1, y2 - y1), boxColor, boxThickness, cv::LINE_AA);

                                    cv::Size text_size = cv::getTextSize(title, cv::FONT_HERSHEY_SIMPLEX, font_scale, font_thickness, nullptr);
                                    int text_bg_height = text_size.height + text_padding * 2;
                                    cv::Rect text_bg_rect(x1, y1 - text_bg_height, text_size.width + text_padding * 2, text_bg_height);
                                    cv::rectangle(image, text_bg_rect, boxColor, -1);
                                    cv::putText(image, title, cv::Point(x1 + text_padding, y1 - text_padding),
                                                cv::FONT_HERSHEY_SIMPLEX, font_scale, textColor, font_thickness, cv::LINE_AA);

                                    if (det.happen)
                                    {
                                        int icon_size = 20;
                                        cv::circle(image, cv::Point(x2 - icon_size / 2, y1 + icon_size / 2), icon_size / 2, overlayColor, -1);
                                        cv::putText(image, "!", cv::Point(x2 - icon_size / 2 - 5, y1 + icon_size / 2 + 5),
                                                    cv::FONT_HERSHEY_SIMPLEX, 0.6, textColor, 2, cv::LINE_AA);
                                    }
                                }
                            }

                            cv::putText(image, control.algorithmCode, cv::Point(20, 60),
                                        cv::FONT_HERSHEY_COMPLEX, 1.2, overlayColor, 3, cv::LINE_AA);

                            char fpsBuf[32];
                            std::snprintf(fpsBuf, sizeof(fpsBuf), "%.2f", control.checkFps);
                            std::string fps_title = std::string("Check FPS: ") + fpsBuf;
                            cv::Size fps_text_size = cv::getTextSize(fps_title, cv::FONT_HERSHEY_COMPLEX, 1.0, 2, nullptr);
                            cv::Rect fps_bg_rect(20, 80, fps_text_size.width + 20, fps_text_size.height + 15);
                            cv::rectangle(image, fps_bg_rect, overlayColor, -1);
                            cv::putText(image, fps_title, cv::Point(30, 120),
                                        cv::FONT_HERSHEY_COMPLEX, 1.0, textColor, 2, cv::LINE_AA);
                        }

                        auto fillFrameFromImage = [&](Frame *dst) {
                            dst->setData(image.data, image.cols, image.rows, image.channels());
                            dst->happen = happen;
                            dst->happenScore = happenScore;
                        };

                        if (control.pushStream && runtime->pushStream)
                        {
                            Frame *pushFrame = mVideoFramePool->take();
                            if (pushFrame)
                            {
                                fillFrameFromImage(pushFrame);
                                runtime->pushStream->addVideoFrame(pushFrame);
                            }
                        }

                        // Alarm generation needs the full frame sequence for prefix cache and flush.
                        {
                            Frame *alarmFrame = mVideoFramePool->take();
                            if (alarmFrame)
                            {
                                fillFrameFromImage(alarmFrame);
                                pushVideoFrame(runtime, alarmFrame);
                            }
                        }

                        }

                        av_frame_unref(frame_yuv420p);
                        av_frame_unref(frame_sw);
                    }
                }
            }

            av_packet_unref(&pkt);
        }

        av_frame_free(&frame_yuv420p);
        av_frame_free(&frame_sw);
        av_frame_free(&frame_bgr);
        av_free(frame_bgr_buff);
        sws_freeContext(sws_ctx_yuv420p2bgr);
    }

    bool Worker::pushVideoFrame(WorkerControlRuntime *runtime, Frame *frame)
    {
        FramePool *const framePool = mVideoFramePool;
        if (!runtime)
        {
            if (framePool && frame)
            {
                framePool->giveBack(frame);
            }
            return false;
        }
        std::unique_lock<std::mutex> lock(runtime->videoFrameQueueMtx);
        if (runtime->videoFrameQueue.size() >= mVideoFrameQueueCapacity)
        {
            Frame *dropped = runtime->videoFrameQueue.front();
            runtime->videoFrameQueue.pop();
            if (framePool)
            {
                framePool->giveBack(dropped);
            }
        }
        runtime->videoFrameQueue.push(frame);
        lock.unlock();
        runtime->videoFrameQueueCv.notify_one();
        return true;
    }

    bool Worker::getVideoFrame(WorkerControlRuntime *runtime, Frame *&videoFrame)
    {
        if (!runtime)
        {
            return false;
        }
        std::unique_lock<std::mutex> lock(runtime->videoFrameQueueMtx);
        runtime->videoFrameQueueCv.wait_for(lock, std::chrono::milliseconds(20), [this, runtime]() {
            return !runtime->videoFrameQueue.empty() || !mState || runtime->stopping;
        });

        if (runtime->videoFrameQueue.empty())
        {
            return false;
        }

        videoFrame = runtime->videoFrameQueue.front();
        runtime->videoFrameQueue.pop();
        return true;
    }

    void Worker::clearVideoFrameQueue(WorkerControlRuntime *runtime)
    {
        if (!runtime)
        {
            return;
        }
        FramePool *const framePool = mVideoFramePool;
        std::lock_guard<std::mutex> lock(runtime->videoFrameQueueMtx);
        while (!runtime->videoFrameQueue.empty())
        {
            Frame *frame = runtime->videoFrameQueue.front();
            runtime->videoFrameQueue.pop();
            if (framePool)
            {
                framePool->giveBack(frame);
            }
        }
    }

    void Worker::deleteControlRuntime(WorkerControlRuntime *runtime)
    {
        if (!runtime)
        {
            return;
        }
        runtime->stopping = true;
        runtime->videoFrameQueueCv.notify_all();
        if (runtime->pushStream)
        {
            runtime->pushStream->notifyStop();
        }
        if (runtime->alarmThread)
        {
            if (runtime->alarmThread->joinable())
            {
                runtime->alarmThread->join();
            }
            delete runtime->alarmThread;
            runtime->alarmThread = nullptr;
        }
        if (runtime->encodeThread)
        {
            if (runtime->encodeThread->joinable())
            {
                runtime->encodeThread->join();
            }
            delete runtime->encodeThread;
            runtime->encodeThread = nullptr;
        }
        clearVideoFrameQueue(runtime);
        if (runtime->pushStream)
        {
            delete runtime->pushStream;
            runtime->pushStream = nullptr;
        }
        if (runtime->analyzer)
        {
            delete runtime->analyzer;
            runtime->analyzer = nullptr;
        }
        if (runtime->control)
        {
            delete runtime->control;
            runtime->control = nullptr;
        }
        delete runtime;
    }

    void Worker::clearAllControlRuntimes()
    {
        std::vector<WorkerControlRuntime *> runtimes;
        {
            std::lock_guard<std::mutex> lock(mControlRuntimesMtx);
            for (auto &entry : mControlRuntimes)
            {
                runtimes.push_back(entry.second);
            }
            mControlRuntimes.clear();
            mControl = nullptr;
        }
        for (WorkerControlRuntime *runtime : runtimes)
        {
            deleteControlRuntime(runtime);
        }
    }

    bool Worker::getState()
    {
        return mState;
    }

    void Worker::setState(bool state)
    {
        mState = state;
    }

}