#include "Scheduler.h"
#include "Config.h"
#include "Control.h"
#include "Worker.h"
#include "Algorithm.h"
#include "AlgorithmOnYolo.h"
#include "GenerateAlarmVideo.h"
#include "Utils/Common.h"
#include "Utils/Log.h"
#include "Utils/Request.h"
#include <json/json.h>
#include <fstream>
#include <string>
#include <cctype>
#include <algorithm>
#include <chrono>
#include <thread>
#include <sstream>
#include <unordered_set>
#include <cmath>
#include <limits>

namespace SVAAnalyzer
{
    namespace
    {
        // Worker sharing key for the teaching Worker model: prefer stable streamCode,
        // and fall back to streamUrl for direct/manual control requests.
        static std::string getWorkerStreamKey(const Control *control)
        {
            if (!control)
            {
                return "";
            }
            return control->streamCode.empty() ? control->streamUrl : control->streamCode;
        }

        bool isRelationalBehaviorType(const std::string &behaviorType)
        {
            return behaviorType == "relation_near" ||
                   behaviorType == "relation_apart" ||
                   behaviorType == "relation_not_contains" ||
                   behaviorType == "fight";
        }

        bool isRelationalBehaviorRule(const BehaviorRuleConfig &rule)
        {
            return rule.enabled && isRelationalBehaviorType(rule.behaviorType);
        }

        bool isAggregateBehaviorType(const std::string &behaviorType)
        {
            return behaviorType == "absence" ||
                   behaviorType == "count_threshold" ||
                   behaviorType == "occupancy" ||
                   behaviorType == "region_motion";
        }

        bool isSequenceBehaviorRule(const BehaviorRuleConfig &rule)
        {
            return rule.enabled && !rule.sequenceId.empty() && !isAggregateBehaviorType(rule.behaviorType);
        }

        std::string safeMediaPathSegment(const std::string &value)
        {
            std::string result;
            result.reserve(value.size());
            for (char ch : value)
            {
                unsigned char uch = static_cast<unsigned char>(ch);
                if (std::isalnum(uch) || ch == '_' || ch == '-' || ch == '.')
                {
                    result.push_back(ch);
                }
                else
                {
                    result.push_back('_');
                }
            }
            return result.empty() ? "unknown" : result;
        }

        std::string buildPresetAlarmVideoPath(const std::string &controlCode, const std::string &eventId)
        {
            return "alarm/" + safeMediaPathSegment(controlCode) + "/" + safeMediaPathSegment(eventId) + "/main.mp4";
        }

        struct SequenceStageConfig
        {
            int stageIndex = 0;
            std::string logicMode = "all";
            int64_t stageTimeoutMs = 0;
            int64_t stageHoldMs = 0;
            std::vector<const BehaviorRuleConfig *> rules;
        };

        double detectCenterX(const DetectObject &detect)
        {
            return (static_cast<double>(detect.x1) + static_cast<double>(detect.x2)) * 0.5;
        }

        double detectCenterY(const DetectObject &detect)
        {
            return (static_cast<double>(detect.y1) + static_cast<double>(detect.y2)) * 0.5;
        }

        double detectCenterDistancePx(const DetectObject &lhs, const DetectObject &rhs)
        {
            const double dx = detectCenterX(lhs) - detectCenterX(rhs);
            const double dy = detectCenterY(lhs) - detectCenterY(rhs);
            return std::sqrt(dx * dx + dy * dy);
        }

        bool isDetectCenterInsideSubjectBox(const DetectObject &subject, const DetectObject &target)
        {
            const double centerX = detectCenterX(target);
            const double centerY = detectCenterY(target);
            return centerX >= static_cast<double>(subject.x1) && centerX <= static_cast<double>(subject.x2) &&
                   centerY >= static_cast<double>(subject.y1) && centerY <= static_cast<double>(subject.y2);
        }
    }

    Scheduler::Scheduler(Config *config) : mConfig(config), mState(false),
                                           mLoopAlarmThread(nullptr),
                                           mLoopDetectFrameThread(nullptr)
    {
        LOGI("");
    }

    Scheduler::~Scheduler()
    {
        LOGI("");
        setState(false);

        // 释放算法对象 (pure ONNX Runtime, teaching project)
        if (on_yolo11n_80) {
            delete on_yolo11n_80;
            on_yolo11n_80 = nullptr;
        }
        if (on_yolo26n_80) {
            delete on_yolo26n_80;
            on_yolo26n_80 = nullptr;
        }

        clearAlarmQueue();
        clearDetectFrameQueue();
        if (mLoopAlarmThread)
        {
            mAlarmQ_cv.notify_all();
            mLoopAlarmThread->join();
            delete mLoopAlarmThread;
            mLoopAlarmThread = nullptr;
        }
        if (mLoopDetectFrameThread)
        {
            mDetectFrameQ_cv.notify_all();
            mLoopDetectFrameThread->join();
            delete mLoopDetectFrameThread;
            mLoopDetectFrameThread = nullptr;
        }
    }

    Config *Scheduler::getConfig()
    {
        return mConfig;
    }
    bool Scheduler::initAlgorithm()
    {
        LOGI("initAlgorithm() start - pure ONNX Runtime (GPU优先，CPU回退)");

        // 80-class COCO class names shared by YOLO11 and YOLO26.
        std::string modelPath = mConfig->modelDir + "/yolo11n.onnx";
        std::vector<std::string> classNames = {
            "person", "bicycle", "car", "motorcycle", "airplane", "bus", "train", "truck", "boat", "traffic light",
            "fire hydrant", "stop sign", "parking meter", "bench", "bird", "cat", "dog", "horse", "sheep", "cow",
            "elephant", "bear", "zebra", "giraffe", "backpack", "umbrella", "handbag", "tie", "suitcase", "frisbee",
            "skis", "snowboard", "sports ball", "kite", "baseball bat", "baseball glove", "skateboard", "surfboard",
            "tennis racket", "bottle", "wine glass", "cup", "fork", "knife", "spoon", "bowl", "banana", "apple",
            "sandwich", "orange", "broccoli", "carrot", "hot dog", "pizza", "donut", "cake", "chair", "couch",
            "potted plant", "bed", "dining table", "toilet", "tv", "laptop", "mouse", "remote", "keyboard", "cell phone",
            "microwave", "oven", "toaster", "sink", "refrigerator", "book", "clock", "vase", "scissors", "teddy bear",
            "hair drier", "toothbrush"};
        LOGI("初始化 on_yolo11n_80 (yolo11n.onnx)");
        on_yolo11n_80 = new AlgorithmOnYolo(mConfig, modelPath, classNames, "on_yolo11n_80");

        LOGI("初始化 on_yolo26n_80 (yolo26s.onnx)");
        modelPath = mConfig->modelDir + "/yolo26s.onnx";
        on_yolo26n_80 = new AlgorithmOnYolo(mConfig, modelPath, classNames, "on_yolo26n_80");

        LOGI("initAlgorithm() end - total ONNX models loaded: 2");
        return true;
    }
    void Scheduler::loop()
    {

        mLoopAlarmThread = new std::thread(Scheduler::loopAlarmThread, this);
    mLoopDetectFrameThread = new std::thread(Scheduler::loopDetectFrameThread, this);
        mLoopAlarmThread->native_handle();
    mLoopDetectFrameThread->native_handle();
        LOGI("Start Success");
        int64_t l = 0;
        while (mState)
        {
            ++l;
            handleDeleteWorker();
        }
    }

    int Scheduler::apiControls(std::vector<Control *> &controls)
    {
        int len = 0;

        mWorkerMapMtx.lock();
        for (auto f = mWorkerMap.begin(); f != mWorkerMap.end(); ++f)
        {
            Control *control = f->second->getControl(f->first);
            if (control)
            {
                ++len;
                controls.push_back(control);
            }
        }
        mWorkerMapMtx.unlock();

        return len;
    }
    Control *Scheduler::apiControl(std::string &code)
    {
        Control *control = nullptr;
        mWorkerMapMtx.lock();
        for (auto f = mWorkerMap.begin(); f != mWorkerMap.end(); ++f)
        {
            if (f->first == code)
            {
                control = f->second->getControl(code);
            }
        }
        mWorkerMapMtx.unlock();

        return control;
    }

    void Scheduler::apiControlAdd(Control *control, int &result_code, std::string &result_msg)
    {

        if (isAdd(control))
        {
            result_msg = "the control is running";
            result_code = 1000;
            return;
        }

        else
        {
            Control *controlCopy = new Control(*control);
            Worker *worker = nullptr;
            bool reusedWorker = false;
            std::string streamKey = getWorkerStreamKey(controlCopy);
            {
                std::lock_guard<std::mutex> lock(mWorkerMapMtx);
                auto streamIt = mStreamWorkerMap.find(streamKey);
                if (streamIt != mStreamWorkerMap.end())
                {
                    worker = streamIt->second;
                    reusedWorker = true;
                }
            }

            if (reusedWorker)
            {
                if (worker->addControl(controlCopy, result_msg))
                {
                    if (addWorker(controlCopy, worker))
                    {
                        result_msg = "add success";
                        result_code = 1000;
                    }
                    else
                    {
                        worker->removeControl(controlCopy->code);
                        result_msg = "add error";
                        result_code = 0;
                    }
                }
                else
                {
                    delete controlCopy;
                    result_code = 0;
                }
                return;
            }

            worker = new Worker(this, controlCopy);

            if (worker->start(result_msg))
            {
                if (addWorker(controlCopy, worker))
                {
                    result_msg = "add success";
                    result_code = 1000;
                }
                else
                {
                    delete worker;
                    worker = nullptr;
                    result_msg = "add error";
                    result_code = 0;
                }
            }
            else
            {
                delete worker;
                worker = nullptr;
                result_code = 0;
            }
        }
    }
    void Scheduler::apiControlCancel(Control *control, int &result_code, std::string &result_msg)
    {

        Worker *worker = getWorker(control);

        if (worker)
        {
            if (worker->getState())
            {
                result_msg = "control is running, ";
            }
            else
            {
                result_msg = "control is not running, ";
            }

            removeWorker(control);

            result_msg += "cancel success";
            result_code = 1000;
            return;
        }
        else
        {
            result_msg = "there is no such control";
            result_code = 0;
            return;
        }
    }
    void Scheduler::setState(bool state)
    {
        mState = state;
    }
    bool Scheduler::getState()
    {
        return mState;
    }

    int Scheduler::getWorkerSize()
    {
        mWorkerMapMtx.lock();
        int size = mWorkerMap.size();
        mWorkerMapMtx.unlock();

        return size;
    }
    bool Scheduler::isAdd(Control *control)
    {

        mWorkerMapMtx.lock();
        bool isAdd = mWorkerMap.end() != mWorkerMap.find(control->code);
        mWorkerMapMtx.unlock();

        return isAdd;
    }
    bool Scheduler::addWorker(Control *control, Worker *worker)
    {
        bool add = false;

        mWorkerMapMtx.lock();
        bool isAdd = mWorkerMap.end() != mWorkerMap.find(control->code);
        if (!isAdd)
        {
            mWorkerMap.insert(std::pair<std::string, Worker *>(control->code, worker));
            std::string streamKey = getWorkerStreamKey(control);
            if (!streamKey.empty() && mStreamWorkerMap.find(streamKey) == mStreamWorkerMap.end())
            {
                mStreamWorkerMap.insert(std::make_pair(streamKey, worker));
            }
            add = true;
        }
        mWorkerMapMtx.unlock();
        return add;
    }
    bool Scheduler::removeWorker(Control *control)
    {
        if (!control)
        {
            return false;
        }

        Worker *worker = nullptr;
        std::string streamKey;
        {
            std::lock_guard<std::mutex> lock(mWorkerMapMtx);
            auto f = mWorkerMap.find(control->code);
            if (mWorkerMap.end() == f)
            {
                return false;
            }
            worker = f->second;
            Control *runningControl = worker->getControl(control->code);
            streamKey = getWorkerStreamKey(runningControl ? runningControl : control);
            mWorkerMap.erase(f);
        }

        const bool removeWholeWorker = worker->getControlCount() <= 1;
        if (removeWholeWorker)
        {
            {
                std::lock_guard<std::mutex> lock(mWorkerMapMtx);
                if (!streamKey.empty())
                {
                    auto streamIt = mStreamWorkerMap.find(streamKey);
                    if (streamIt != mStreamWorkerMap.end() && streamIt->second == worker)
                    {
                        mStreamWorkerMap.erase(streamIt);
                    }
                }
            }
            worker->remove();

            // 添加到待删除队列start
            std::unique_lock<std::mutex> lck(mTobeDeletedWorkerQ_mtx);
            mTobeDeletedWorkerQ.push(worker);
            // mTobeDeletedWorkerQ_cv.notify_all();
            mTobeDeletedWorkerQ_cv.notify_one();
            // 添加到待删除队列end
            return true;
        }

        worker->removeControl(control->code);
        return true;
    }
    Worker *Scheduler::getWorker(Control *control)
    {
        Worker *worker = nullptr;

        mWorkerMapMtx.lock();
        auto f = mWorkerMap.find(control->code);
        if (mWorkerMap.end() != f)
        {
            worker = f->second;
        }
        mWorkerMapMtx.unlock();
        return worker;
    }

    void Scheduler::handleDeleteWorker()
    {

        std::unique_lock<std::mutex> lck(mTobeDeletedWorkerQ_mtx);
        mTobeDeletedWorkerQ_cv.wait(lck);

        while (!mTobeDeletedWorkerQ.empty())
        {
            Worker *worker = mTobeDeletedWorkerQ.front();
            mTobeDeletedWorkerQ.pop();

            if (worker->mControl)
            {
                LOGI("code=%s,streamUrl=%s", worker->mControl->code.data(), worker->mControl->streamUrl.data());
            }

            delete worker;
            worker = nullptr;
        }
    }
    void Scheduler::handleLoopAlarm()
    {

        int alarmQSize;

        while (getState())
        {

            Alarm *alarm = nullptr;
            // 使用条件变量等待，类似 handleDeleteWorker
            {
                std::unique_lock<std::mutex> lck(mAlarmQ_mtx);
                mAlarmQ_cv.wait(lck, [this]() { return !mAlarmQ.empty() || !getState(); });
            }
            if (!getState())
            {
                break;
            }
            if (getAlarm(alarm, alarmQSize))
            {
                for (int retry = 0;
                     alarm && alarm->alarmId.empty() && retry < 100 && getState();
                     ++retry)
                {
                    attachAlarmMediaBinding(alarm);
                    if (!alarm->alarmId.empty())
                    {
                        break;
                    }
                    std::this_thread::sleep_for(std::chrono::milliseconds(50));
                }

                GenerateAlarmVideo gen(mConfig, alarm);
                gen.genAlarmVideo();

                delete alarm;
                alarm = nullptr;
            }
        }
    }
    void Scheduler::loopAlarmThread(void *arg)
    {
        Scheduler *scheduler = (Scheduler *)arg;
        scheduler->handleLoopAlarm();
    }
    void Scheduler::addAlarm(Alarm *alarm)
    {
        attachAlarmMediaBinding(alarm);

        mAlarmQ_mtx.lock();
        if (mAlarmQ.size() > 0)
        {
            // 扔掉
            delete alarm;
            alarm = nullptr;
        }
        else
        {
            mAlarmQ.push(alarm);
            mAlarmQ_cv.notify_one(); // 唤醒等待的报警处理线程
        }

        mAlarmQ_mtx.unlock();
    }

    void Scheduler::bindAlarmMedia(const std::string &controlCode,
                                   const std::string &alarmId,
                                   const std::string &eventId,
                                   const std::string &behaviorType,
                                   const std::string &ruleId,
                                   const std::string &videoPath,
                                   const std::string &imagePath)
    {
        if (controlCode.empty())
        {
            return;
        }

        std::lock_guard<std::mutex> lock(mAlarmQ_mtx);
        AlarmMediaBinding &binding = mAlarmMediaBindingMap[controlCode];
        if (!alarmId.empty())
        {
            binding.alarmId = alarmId;
        }
        if (!eventId.empty())
        {
            binding.eventId = eventId;
        }
        if (!behaviorType.empty())
        {
            binding.behaviorType = behaviorType;
        }
        if (!ruleId.empty())
        {
            binding.ruleId = ruleId;
        }
        if (!videoPath.empty())
        {
            binding.videoPath = videoPath;
        }
        if (!imagePath.empty())
        {
            binding.imagePath = imagePath;
        }
        binding.boundTimestampMs = getCurTimestamp();
    }

    void Scheduler::attachAlarmMediaBinding(Alarm *alarm)
    {
        if (!alarm || alarm->controlCode.empty())
        {
            return;
        }

        std::lock_guard<std::mutex> lock(mAlarmQ_mtx);
        auto it = mAlarmMediaBindingMap.find(alarm->controlCode);
        if (it == mAlarmMediaBindingMap.end())
        {
            return;
        }

        const int64_t nowMs = getCurTimestamp();
        if (it->second.boundTimestampMs > 0 && nowMs - it->second.boundTimestampMs > 60000)
        {
            mAlarmMediaBindingMap.erase(it);
            return;
        }

        if (!it->second.alarmId.empty())
        {
            alarm->alarmId = it->second.alarmId;
        }
        if (!it->second.eventId.empty())
        {
            alarm->eventId = it->second.eventId;
        }
        if (!it->second.behaviorType.empty())
        {
            alarm->behaviorType = it->second.behaviorType;
        }
        if (!it->second.ruleId.empty())
        {
            alarm->ruleId = it->second.ruleId;
        }
        if (!it->second.videoPath.empty())
        {
            alarm->videoPath = it->second.videoPath;
        }
        if (!it->second.imagePath.empty())
        {
            alarm->imagePath = it->second.imagePath;
        }
        if (!it->second.alarmId.empty())
        {
            mAlarmMediaBindingMap.erase(it);
        }
    }

    void Scheduler::loopDetectFrameThread(void *arg)
    {
        Scheduler *scheduler = (Scheduler *)arg;
        scheduler->handleLoopDetectFrame();
    }

    void Scheduler::handleLoopDetectFrame()
    {
        while (getState())
        {
            bool hasEvent = false;
            {
                std::unique_lock<std::mutex> lck(mDetectFrameQ_mtx);
                hasEvent = mDetectFrameQ_cv.wait_for(lck,
                                                     std::chrono::milliseconds(200),
                                                     [this]() { return !mDetectFrameQ.empty() || !getState(); });
            }

            if (!getState())
            {
                break;
            }

            flushDetectLifecycleByTimeout(getCurTimestamp());

            if (!hasEvent)
            {
                continue;
            }

            DetectFrameEvent *event = nullptr;
            int qSize = 0;
            if (!getDetectFrameEvent(event, qSize) || !event)
            {
                continue;
            }

            Json::Value root;
            root["type"] = "detect.frame";
            root["version"] = "v1";
            root["nodeCode"] = event->nodeCode;
            root["controlCode"] = event->controlCode;
            root["streamCode"] = event->streamCode;
            root["streamApp"] = event->streamApp;
            root["streamName"] = event->streamName;
            root["renderMode"] = event->renderMode;
            root["timestampMs"] = static_cast<Json::Int64>(event->timestampMs);
            root["frameSeq"] = static_cast<Json::Int64>(event->frameSeq);
            root["width"] = event->width;
            root["height"] = event->height;
            root["checkFps"] = event->checkFps;
            root["happen"] = event->happen;
            root["happenScore"] = event->happenScore;
            root["ruleId"] = event->ruleId;
            root["customEventName"] = event->customEventName;
            root["behaviorType"] = event->behaviorType;
            root["regionId"] = event->regionId;
            root["regionName"] = event->regionName;
            root["lineId"] = event->lineId;
            root["lineName"] = event->lineName;
            root["crossingDirection"] = event->crossingDirection;
            root["sequenceId"] = event->sequenceId;
            root["sequenceStageIndex"] = event->sequenceStageIndex;
            root["sequenceStageCount"] = event->sequenceStageCount;
            root["sequenceLogicMode"] = event->sequenceLogicMode;
            root["directionAngleDeg"] = event->directionAngleDeg;
            root["trackId"] = event->trackId;
            root["relationTargetTrackId"] = event->relationTargetTrackId;
            root["relationTargetClassName"] = event->relationTargetClassName;
            root["relationDistancePx"] = event->relationDistancePx;
            root["aggregateCount"] = event->aggregateCount;
            root["aggregateThresholdCount"] = event->aggregateThresholdCount;

            Json::Value objects(Json::arrayValue);
            for (size_t i = 0; i < event->objects.size(); ++i)
            {
                const DetectFrameObject &obj = event->objects[i];
                Json::Value item;
                item["x1"] = obj.x1;
                item["y1"] = obj.y1;
                item["x2"] = obj.x2;
                item["y2"] = obj.y2;
                item["score"] = obj.score;
                item["classId"] = obj.classId;
                item["className"] = obj.className;
                item["algorithmCode"] = obj.algorithmCode;
                item["happen"] = obj.happen;
                item["trackId"] = obj.trackId;
                item["ruleId"] = obj.ruleId;
                item["customEventName"] = obj.customEventName;
                item["behaviorType"] = obj.behaviorType;
                item["regionId"] = obj.regionId;
                item["regionName"] = obj.regionName;
                item["lineId"] = obj.lineId;
                item["lineName"] = obj.lineName;
                item["crossingDirection"] = obj.crossingDirection;
                item["directionAngleDeg"] = obj.directionAngleDeg;
                item["sequenceId"] = obj.sequenceId;
                item["sequenceStageIndex"] = obj.sequenceStageIndex;
                item["sequenceStageCount"] = obj.sequenceStageCount;
                item["sequenceLogicMode"] = obj.sequenceLogicMode;
                item["relationTargetTrackId"] = obj.relationTargetTrackId;
                item["relationTargetClassName"] = obj.relationTargetClassName;
                item["relationDistancePx"] = obj.relationDistancePx;
                objects.append(item);
            }
            root["objects"] = objects;

            Json::Value aggregateBehaviors(Json::arrayValue);
            for (size_t i = 0; i < event->aggregateBehaviors.size(); ++i)
            {
                const AggregateBehaviorMatch &match = event->aggregateBehaviors[i];
                Json::Value item;
                item["ruleId"] = match.ruleId;
                item["customEventName"] = match.customEventName;
                item["behaviorType"] = match.behaviorType;
                item["regionId"] = match.regionId;
                item["regionName"] = match.regionName;
                item["eventClass"] = match.eventClass;
                item["trackId"] = match.trackId;
                item["relationTargetTrackId"] = match.relationTargetTrackId;
                item["relationTargetClassName"] = match.relationTargetClassName;
                item["relationDistancePx"] = match.relationDistancePx;
                item["objectCount"] = match.objectCount;
                item["thresholdCount"] = match.thresholdCount;
                item["score"] = match.score;
                Json::Value algorithmCodes(Json::arrayValue);
                for (size_t j = 0; j < match.algorithmCodes.size(); ++j)
                {
                    algorithmCodes.append(match.algorithmCodes[j]);
                }
                item["algorithmCodes"] = algorithmCodes;
                aggregateBehaviors.append(item);
            }
            root["aggregateBehaviors"] = aggregateBehaviors;

            std::string data = root.toStyledString();
            std::string streamPostKey = event->controlCode + "|" + event->streamCode;
            int64_t retryUsed = 0;
            bool skippedByCooldown = false;
            bool postOk = postDetectPayloadWithRetry(data, streamPostKey, std::max(0, event->postRetryMax), retryUsed,
                                                     std::max(0, event->postFailOpenThreshold),
                                                     std::max(0, event->postCooldownMs),
                                                     skippedByCooldown);
            if (postOk)
            {
                mDetectFrameSent.fetch_add(1);
            }
            else if (!skippedByCooldown)
            {
                mDetectFramePostFailed.fetch_add(1);
            }
            if (retryUsed > 0)
            {
                mDetectFrameRetried.fetch_add(retryUsed);
            }

            handleDetectLifecycle(event);
            flushDetectLifecycleByTimeout(event->timestampMs);

            delete event;
            event = nullptr;
        }

        flushDetectLifecycleByTimeout(getCurTimestamp() + 30000);
    }

    bool Scheduler::postDetectPayload(const std::string &payload)
    {
        std::string url = mConfig->adminHost + "/alarm/detectFrame";
        if (!mConfig->detectEventUrl.empty())
        {
            url = mConfig->detectEventUrl;
        }

        Request request;
        std::string response;
        if (url.rfind("ws://", 0) == 0 || url.rfind("wss://", 0) == 0)
        {
            return request.sendText(url.data(), payload.data(), response);
        }
        return request.post(url.data(), payload.data(), response);
    }

    bool Scheduler::canTryPostPayload(const std::string &streamKey, int64_t nowMs)
    {
        std::lock_guard<std::mutex> lock(mDetectFrameQ_mtx);
        auto it = mDetectPostCircuitMap.find(streamKey);
        if (it == mDetectPostCircuitMap.end())
        {
            return true;
        }
        return nowMs >= it->second.cooldownUntilMs;
    }

    void Scheduler::updatePostState(const std::string &streamKey, bool ok, int failOpenThreshold, int cooldownMs, int64_t nowMs)
    {
        std::lock_guard<std::mutex> lock(mDetectFrameQ_mtx);

        if (mDetectPostCircuitMap.size() > 1024)
        {
            for (auto it = mDetectPostCircuitMap.begin(); it != mDetectPostCircuitMap.end();)
            {
                bool cooldownExpired = nowMs >= it->second.cooldownUntilMs;
                bool noPendingFailure = it->second.consecutiveFailed == 0;
                if (cooldownExpired && noPendingFailure)
                {
                    it = mDetectPostCircuitMap.erase(it);
                }
                else
                {
                    ++it;
                }
            }
        }

        DetectPostCircuitState &state = mDetectPostCircuitMap[streamKey];
        if (ok)
        {
            state.consecutiveFailed = 0;
            state.cooldownUntilMs = 0;
            return;
        }

        int64_t failed = ++state.consecutiveFailed;
        if (failOpenThreshold > 0 && failed >= failOpenThreshold && cooldownMs > 0)
        {
            state.cooldownUntilMs = nowMs + cooldownMs;
            state.consecutiveFailed = 0;
        }
    }

    bool Scheduler::postDetectPayloadWithRetry(const std::string &payload,
                                               const std::string &streamKey,
                                               int maxRetry,
                                               int64_t &retryUsed,
                                               int failOpenThreshold,
                                               int cooldownMs,
                                               bool &skippedByCooldown)
    {
        retryUsed = 0;
        skippedByCooldown = false;
        if (maxRetry < 0)
        {
            maxRetry = 0;
        }

        int64_t nowMs = getCurTimestamp();
        if (!canTryPostPayload(streamKey, nowMs))
        {
            mDetectPostSkipped.fetch_add(1);
            skippedByCooldown = true;
            return false;
        }

        bool ok = postDetectPayload(payload);
        if (ok)
        {
            updatePostState(streamKey, true, failOpenThreshold, cooldownMs, nowMs);
            return true;
        }

        for (int i = 1; i <= maxRetry; ++i)
        {
            retryUsed += 1;
            int delayMs = 20 * i;
            std::this_thread::sleep_for(std::chrono::milliseconds(delayMs));
            if (postDetectPayload(payload))
            {
                updatePostState(streamKey, true, failOpenThreshold, cooldownMs, nowMs);
                return true;
            }
        }
        updatePostState(streamKey, false, failOpenThreshold, cooldownMs, nowMs);
        return false;
    }

    std::string Scheduler::buildDetectFrameDigest(const DetectFrameEvent *event) const
    {
        if (!event)
        {
            return "";
        }

        std::ostringstream ss;
        ss << (event->happen ? '1' : '0') << '|';
        ss << event->objects.size() << '|';
        int happenedCount = 0;
        int sample = 0;
        for (size_t i = 0; i < event->objects.size(); ++i)
        {
            const DetectFrameObject &obj = event->objects[i];
            if (obj.happen)
            {
                ++happenedCount;
            }
            if (sample < 4)
            {
                int scoreBucket = static_cast<int>(obj.score * 100.0f);
                ss << obj.className << ':' << obj.algorithmCode << ':' << scoreBucket << ':' << (obj.happen ? 1 : 0) << ';';
                ++sample;
            }
        }
        ss << '|' << happenedCount;
        return ss.str();
    }

    bool Scheduler::shouldDebounceDetectFrame(const DetectFrameEvent *event)
    {
        if (!event)
        {
            return false;
        }

        const std::string streamKey = event->controlCode + "|" + event->streamCode;
        const std::string digest = buildDetectFrameDigest(event);

        if (mDetectFrameDebounceMap.size() > 512)
        {
            for (auto itClean = mDetectFrameDebounceMap.begin(); itClean != mDetectFrameDebounceMap.end();)
            {
                if (event->timestampMs - itClean->second.first > 10000)
                {
                    itClean = mDetectFrameDebounceMap.erase(itClean);
                }
                else
                {
                    ++itClean;
                }
            }
        }

        auto it = mDetectFrameDebounceMap.find(streamKey);
        if (it != mDetectFrameDebounceMap.end())
        {
            int64_t lastTs = it->second.first;
            const std::string &lastDigest = it->second.second;
            const int debounceMs = std::max(0, event->debounceWindowMs);
            if (digest == lastDigest && event->timestampMs >= lastTs && (event->timestampMs - lastTs) <= debounceMs)
            {
                it->second.first = event->timestampMs;
                return true;
            }
            it->second.first = event->timestampMs;
            it->second.second = digest;
        }
        else
        {
            mDetectFrameDebounceMap.insert(std::make_pair(streamKey, std::make_pair(event->timestampMs, digest)));
        }
        return false;
    }

    void Scheduler::sendDetectLifecycleEvent(const std::string &controlKey,
                                             DetectLifecycleState &state,
                                             const DetectFrameEvent &event,
                                             const std::string &eventState)
    {
        Json::Value root;
        root["type"] = "detect.event";
        root["version"] = "v1";
        root["eventId"] = state.eventId;
        root["eventKey"] = controlKey;
        root["eventState"] = eventState;
        root["nodeCode"] = event.nodeCode;
        root["controlCode"] = event.controlCode;
        root["streamCode"] = event.streamCode;
        root["streamApp"] = event.streamApp;
        root["streamName"] = event.streamName;
        root["image_path"] = state.imagePath;
        root["timestampMs"] = static_cast<Json::Int64>(event.timestampMs);
        root["startTimestampMs"] = static_cast<Json::Int64>(state.startTimestampMs);
        root["eventClass"] = state.eventClass;
        root["score"] = state.maxScore;
        root["renderMode"] = event.renderMode;
        root["ruleMode"] = state.ruleMode;
        root["ruleMinHits"] = state.ruleMinHits;
        root["ruleHitWindowMs"] = state.ruleHitWindowMs;
        root["pendingTimeoutMs"] = state.pendingTimeoutMs;
        root["startHitCount"] = state.startHitCount;
        root["ruleId"] = state.ruleId;
        root["customEventName"] = state.customEventName;
        root["behaviorType"] = state.behaviorType;
        root["regionId"] = state.regionId;
        root["regionName"] = state.regionName;
        root["lineId"] = state.lineId;
        root["lineName"] = state.lineName;
        root["crossingDirection"] = state.crossingDirection;
        root["sequenceId"] = state.sequenceId;
        root["sequenceStageIndex"] = state.sequenceStageIndex;
        root["sequenceStageCount"] = state.sequenceStageCount;
        root["sequenceLogicMode"] = state.sequenceLogicMode;
        root["directionAngleDeg"] = state.directionAngleDeg;
        root["trackId"] = state.trackId;
        root["relationTargetTrackId"] = state.relationTargetTrackId;
        root["relationTargetClassName"] = state.relationTargetClassName;
        root["relationDistancePx"] = state.relationDistancePx;
        root["aggregateCount"] = state.aggregateCount;
        root["aggregateThresholdCount"] = state.aggregateThresholdCount;

        Json::Value algorithmCodes(Json::arrayValue);
        for (size_t i = 0; i < state.algorithmCodes.size(); ++i)
        {
            algorithmCodes.append(state.algorithmCodes[i]);
        }
        root["algorithmCodes"] = algorithmCodes;

        Json::Value requiredAlgorithms(Json::arrayValue);
        for (size_t i = 0; i < state.requiredAlgorithms.size(); ++i)
        {
            requiredAlgorithms.append(state.requiredAlgorithms[i]);
        }
        root["requiredAlgorithms"] = requiredAlgorithms;

        std::string streamPostKey = event.controlCode + "|" + event.streamCode;
        int64_t retryUsed = 0;
        bool skippedByCooldown = false;
        if (postDetectPayloadWithRetry(root.toStyledString(), streamPostKey, std::max(0, state.postRetryMax), retryUsed,
                                       std::max(0, state.postFailOpenThreshold),
                                       std::max(0, state.postCooldownMs),
                                       skippedByCooldown))
        {
            mDetectFrameSent.fetch_add(1);
            mDetectEventSent.fetch_add(1);
        }
        else if (!skippedByCooldown)
        {
            mDetectFramePostFailed.fetch_add(1);
            mDetectEventPostFailed.fetch_add(1);
        }
        if (retryUsed > 0)
        {
            mDetectFrameRetried.fetch_add(retryUsed);
            mDetectEventRetried.fetch_add(retryUsed);
        }

        (void)eventState;
    }

    void Scheduler::handleDetectLifecycle(DetectFrameEvent *event)
    {
        if (!event)
        {
            return;
        }

        struct LifecycleCandidate
        {
            std::string key;
            std::string eventClass;
            std::vector<std::string> algorithmCodes;
            std::unordered_set<std::string> algorithmCodeSet;
            float score = 0.0f;
            std::string ruleId;
            std::string customEventName;
            std::string behaviorType;
            std::string regionId;
            std::string regionName;
            std::string lineId;
            std::string lineName;
            std::string crossingDirection;
            std::string sequenceId;
            int sequenceStageIndex = -1;
            int sequenceStageCount = 0;
            std::string sequenceLogicMode = "all";
            double directionAngleDeg = 0.0;
            int trackId = -1;
            int relationTargetTrackId = -1;
            std::string relationTargetClassName;
            double relationDistancePx = -1.0;
            int aggregateCount = 0;
            int aggregateThresholdCount = 0;
        };

        std::vector<LifecycleCandidate> candidates;
        std::unordered_map<std::string, size_t> candidateIndexMap;
        std::unordered_map<std::string, size_t> classModeCandidateIndex;
        std::unordered_map<std::string, std::unordered_set<std::string>> classAlgorithms;
        std::unordered_map<std::string, bool> classRulePassCache;
        std::unordered_set<std::string> frameAlgorithms;
        std::unordered_set<std::string> blockedByRuleClasses;
        int64_t blockedByMinHitsCount = 0;
        int64_t blockedByRestartCooldownCount = 0;
        const std::vector<DetectFrameObject> &objects = event->objects;
        const std::vector<std::string> &requiredAlgorithms = event->requiredAlgorithms;
        const std::string &ruleMode = event->ruleMode;
        const bool hasRequiredAlgorithms = !requiredAlgorithms.empty();
        const bool ruleModeAny = (ruleMode == "any");
        const bool keyModeControl = (event->keyMode == "control");
        const bool keyModeClass = (event->keyMode == "class");
        const bool keyModeClassAlgorithm = (event->keyMode == "class_algorithm");

        const bool needClassAlgorithms =
            (ruleMode == "all_algorithms_per_class" && hasRequiredAlgorithms);
        const bool needFrameAlgorithms =
            (ruleMode == "all_algorithms_any_class" && hasRequiredAlgorithms);
        const bool needCandidateIndex = keyModeClassAlgorithm;
        const size_t objectCount = objects.size();
        const size_t candidateReserve = keyModeControl ? 1 : objectCount;

        candidates.reserve(candidateReserve);
        if (needCandidateIndex)
        {
            candidateIndexMap.reserve(objectCount);
        }
        if (needClassAlgorithms)
        {
            classAlgorithms.reserve(objectCount);
            classRulePassCache.reserve(objectCount);
        }
        if (keyModeClass)
        {
            classModeCandidateIndex.reserve(objectCount);
        }
        if (needFrameAlgorithms)
        {
            frameAlgorithms.reserve(objectCount);
        }
        if (needClassAlgorithms || needFrameAlgorithms)
        {
            blockedByRuleClasses.reserve(objectCount);
        }

        if (needClassAlgorithms || needFrameAlgorithms)
        {
            for (size_t i = 0; i < objectCount; ++i)
            {
                const DetectFrameObject &obj = objects[i];
                if (!obj.happen)
                {
                    continue;
                }
                if (needClassAlgorithms && !obj.algorithmCode.empty())
                {
                    std::unordered_set<std::string> &algorithms = classAlgorithms[obj.className];
                    algorithms.insert(obj.algorithmCode);
                }
                if (needFrameAlgorithms && !obj.algorithmCode.empty())
                {
                    frameAlgorithms.insert(obj.algorithmCode);
                }
            }
        }

        auto classHasRequiredAlgorithms = [&](const std::string &className) -> bool {
            if (!needClassAlgorithms)
            {
                return true;
            }

            auto cached = classRulePassCache.find(className);
            if (cached != classRulePassCache.end())
            {
                return cached->second;
            }
            auto it = classAlgorithms.find(className);
            if (it == classAlgorithms.end())
            {
                classRulePassCache[className] = false;
                return false;
            }
            for (const std::string &requiredAlgorithm : requiredAlgorithms)
            {
                if (it->second.find(requiredAlgorithm) == it->second.end())
                {
                    classRulePassCache[className] = false;
                    return false;
                }
            }
            classRulePassCache[className] = true;
            return true;
        };

        bool frameRulePassed = true;
        if (needFrameAlgorithms)
        {
            for (const std::string &requiredAlgorithm : requiredAlgorithms)
            {
                if (frameAlgorithms.find(requiredAlgorithm) == frameAlgorithms.end())
                {
                    frameRulePassed = false;
                    break;
                }
            }
        }
        if (!frameRulePassed)
        {
            // Frame-level algorithm rule already failed; skip further candidate scans.
            mDetectLifecycleBlockedByRule.fetch_add(1);
            return;
        }

        const std::string controlStreamPrefix = event->controlCode + "|" + event->streamCode + "|";
        const std::string classKeyPrefix = keyModeClass ? (controlStreamPrefix + "class|") : "";
        const std::string classAlgorithmKeyPrefix =
            keyModeClassAlgorithm ? (controlStreamPrefix + "class_algorithm|") : "";

        bool frameHappen = false;
        if (keyModeControl)
        {
            LifecycleCandidate c;
            c.key = controlStreamPrefix + "control";
            c.score = event->happenScore;

            for (size_t i = 0; i < objectCount; ++i)
            {
                const DetectFrameObject &obj = objects[i];
                if (!obj.happen)
                {
                    continue;
                }
                if (needClassAlgorithms && !classHasRequiredAlgorithms(obj.className))
                {
                    blockedByRuleClasses.insert(obj.className);
                    continue;
                }
                frameHappen = true;
                c.score = std::max(c.score, obj.score);
                if (c.eventClass.empty())
                {
                    c.eventClass = obj.className;
                }
                if (c.ruleId.empty() && !obj.ruleId.empty())
                {
                    c.ruleId = obj.ruleId;
                    c.customEventName = obj.customEventName;
                    c.behaviorType = obj.behaviorType;
                    c.regionId = obj.regionId;
                    c.regionName = obj.regionName;
                    c.lineId = obj.lineId;
                    c.lineName = obj.lineName;
                    c.crossingDirection = obj.crossingDirection;
                    c.sequenceId = obj.sequenceId;
                    c.sequenceStageIndex = obj.sequenceStageIndex;
                    c.sequenceStageCount = obj.sequenceStageCount;
                    c.sequenceLogicMode = obj.sequenceLogicMode;
                    c.directionAngleDeg = obj.directionAngleDeg;
                    c.trackId = obj.trackId;
                    c.relationTargetTrackId = obj.relationTargetTrackId;
                    c.relationTargetClassName = obj.relationTargetClassName;
                    c.relationDistancePx = obj.relationDistancePx;
                }
                if (!obj.algorithmCode.empty() &&
                    c.algorithmCodeSet.insert(obj.algorithmCode).second)
                {
                    c.algorithmCodes.push_back(obj.algorithmCode);
                }
            }

            if (ruleModeAny || !hasRequiredAlgorithms)
            {
                frameHappen = event->happen;
            }
            if (frameHappen)
            {
                candidates.push_back(c);
            }
        }

        if (keyModeClass || keyModeClassAlgorithm)
        {
            for (size_t i = 0; i < objectCount; ++i)
            {
                const DetectFrameObject &obj = objects[i];
                if (!obj.happen)
                {
                    continue;
                }
                if (needClassAlgorithms && !classHasRequiredAlgorithms(obj.className))
                {
                    blockedByRuleClasses.insert(obj.className);
                    continue;
                }
                if (keyModeClass)
                {
                    auto classInsert = classModeCandidateIndex.try_emplace(obj.className, candidates.size());
                    if (classInsert.second)
                    {
                        frameHappen = true;
                        LifecycleCandidate c;
                        c.key = classKeyPrefix + obj.className;
                        c.eventClass = obj.className;
                        c.score = obj.score;
                        c.ruleId = obj.ruleId;
                        c.customEventName = obj.customEventName;
                        c.behaviorType = obj.behaviorType;
                        c.regionId = obj.regionId;
                        c.regionName = obj.regionName;
                        c.lineId = obj.lineId;
                        c.lineName = obj.lineName;
                        c.crossingDirection = obj.crossingDirection;
                        c.sequenceId = obj.sequenceId;
                        c.sequenceStageIndex = obj.sequenceStageIndex;
                        c.sequenceStageCount = obj.sequenceStageCount;
                        c.sequenceLogicMode = obj.sequenceLogicMode;
                        c.directionAngleDeg = obj.directionAngleDeg;
                        c.trackId = obj.trackId;
                        c.relationTargetTrackId = obj.relationTargetTrackId;
                        c.relationTargetClassName = obj.relationTargetClassName;
                        c.relationDistancePx = obj.relationDistancePx;
                        if (!obj.algorithmCode.empty())
                        {
                            c.algorithmCodeSet.insert(obj.algorithmCode);
                            c.algorithmCodes.push_back(obj.algorithmCode);
                        }

                        candidates.push_back(c);
                    }
                    else
                    {
                        frameHappen = true;
                        LifecycleCandidate &existing = candidates[classInsert.first->second];
                        existing.score = std::max(existing.score, obj.score);
                        if (existing.ruleId.empty() && !obj.ruleId.empty())
                        {
                            existing.ruleId = obj.ruleId;
                            existing.customEventName = obj.customEventName;
                            existing.behaviorType = obj.behaviorType;
                            existing.regionId = obj.regionId;
                            existing.regionName = obj.regionName;
                            existing.lineId = obj.lineId;
                            existing.lineName = obj.lineName;
                            existing.crossingDirection = obj.crossingDirection;
                            existing.sequenceId = obj.sequenceId;
                            existing.sequenceStageIndex = obj.sequenceStageIndex;
                            existing.sequenceStageCount = obj.sequenceStageCount;
                            existing.sequenceLogicMode = obj.sequenceLogicMode;
                            existing.directionAngleDeg = obj.directionAngleDeg;
                            existing.trackId = obj.trackId;
                            existing.relationTargetTrackId = obj.relationTargetTrackId;
                            existing.relationTargetClassName = obj.relationTargetClassName;
                            existing.relationDistancePx = obj.relationDistancePx;
                        }
                        if (!obj.algorithmCode.empty() &&
                            existing.algorithmCodeSet.insert(obj.algorithmCode).second)
                        {
                            existing.algorithmCodes.push_back(obj.algorithmCode);
                        }
                    }
                    continue;
                }
                else
                {
                    if (obj.algorithmCode.empty())
                    {
                        continue;
                    }
                }
                std::string key;
                key.reserve(classAlgorithmKeyPrefix.size() + obj.className.size() + 1 + obj.algorithmCode.size());
                key.append(classAlgorithmKeyPrefix);
                key.append(obj.className);
                key.push_back('|');
                key.append(obj.algorithmCode);
                frameHappen = true;

                auto candidateInsert = candidateIndexMap.try_emplace(key, candidates.size());
                if (candidateInsert.second)
                {
                    LifecycleCandidate c;
                    c.key = key;
                    c.eventClass = obj.className;
                    c.score = obj.score;
                    c.ruleId = obj.ruleId;
                    c.customEventName = obj.customEventName;
                    c.behaviorType = obj.behaviorType;
                    c.regionId = obj.regionId;
                    c.regionName = obj.regionName;
                    c.lineId = obj.lineId;
                    c.lineName = obj.lineName;
                    c.crossingDirection = obj.crossingDirection;
                    c.sequenceId = obj.sequenceId;
                    c.sequenceStageIndex = obj.sequenceStageIndex;
                    c.sequenceStageCount = obj.sequenceStageCount;
                    c.sequenceLogicMode = obj.sequenceLogicMode;
                    c.directionAngleDeg = obj.directionAngleDeg;
                    c.trackId = obj.trackId;
                    c.relationTargetTrackId = obj.relationTargetTrackId;
                    c.relationTargetClassName = obj.relationTargetClassName;
                    c.relationDistancePx = obj.relationDistancePx;
                    c.algorithmCodeSet.insert(obj.algorithmCode);
                    c.algorithmCodes.push_back(obj.algorithmCode);
                    candidates.push_back(c);
                }
                else
                {
                    LifecycleCandidate &existing = candidates[candidateInsert.first->second];
                    existing.score = std::max(existing.score, obj.score);
                    if (existing.ruleId.empty() && !obj.ruleId.empty())
                    {
                        existing.ruleId = obj.ruleId;
                        existing.customEventName = obj.customEventName;
                        existing.behaviorType = obj.behaviorType;
                        existing.regionId = obj.regionId;
                        existing.regionName = obj.regionName;
                        existing.lineId = obj.lineId;
                        existing.lineName = obj.lineName;
                        existing.crossingDirection = obj.crossingDirection;
                        existing.sequenceId = obj.sequenceId;
                        existing.sequenceStageIndex = obj.sequenceStageIndex;
                        existing.sequenceStageCount = obj.sequenceStageCount;
                        existing.sequenceLogicMode = obj.sequenceLogicMode;
                        existing.directionAngleDeg = obj.directionAngleDeg;
                        existing.trackId = obj.trackId;
                        existing.relationTargetTrackId = obj.relationTargetTrackId;
                        existing.relationTargetClassName = obj.relationTargetClassName;
                        existing.relationDistancePx = obj.relationDistancePx;
                    }
                    if (existing.algorithmCodeSet.insert(obj.algorithmCode).second)
                    {
                        existing.algorithmCodes.push_back(obj.algorithmCode);
                    }
                }
            }
        }

        for (size_t i = 0; i < event->aggregateBehaviors.size(); ++i)
        {
            const AggregateBehaviorMatch &match = event->aggregateBehaviors[i];
            LifecycleCandidate c;
            c.key = controlStreamPrefix + "aggregate|" + match.ruleId + "|" + match.behaviorType;
            c.eventClass = match.eventClass;
            c.algorithmCodes = match.algorithmCodes;
            c.algorithmCodeSet.insert(match.algorithmCodes.begin(), match.algorithmCodes.end());
            c.score = match.score;
            c.ruleId = match.ruleId;
            c.customEventName = match.customEventName;
            c.behaviorType = match.behaviorType;
            c.regionId = match.regionId;
            c.regionName = match.regionName;
            c.trackId = match.trackId;
            c.relationTargetTrackId = match.relationTargetTrackId;
            c.relationTargetClassName = match.relationTargetClassName;
            c.relationDistancePx = match.relationDistancePx;
            c.aggregateCount = match.objectCount;
            c.aggregateThresholdCount = match.thresholdCount;
            candidates.push_back(c);
            frameHappen = true;
        }

        if (!frameHappen)
        {
            if (!blockedByRuleClasses.empty())
            {
                mDetectLifecycleBlockedByRule.fetch_add(static_cast<int64_t>(blockedByRuleClasses.size()));
            }
            if (blockedByMinHitsCount > 0)
            {
                mDetectLifecycleBlockedByMinHits.fetch_add(blockedByMinHitsCount);
            }
            if (blockedByRestartCooldownCount > 0)
            {
                mDetectLifecycleBlockedByRestartCooldown.fetch_add(blockedByRestartCooldownCount);
            }
            return;
        }

        const int clampedRuleMinHits = std::max(1, event->ruleMinHits);
        const int clampedRuleHitWindowMs = std::max(200, event->ruleHitWindowMs);
        const int clampedPendingTimeoutMs = std::max(500, event->pendingTimeoutMs);
        const int clampedRestartCooldownMs = std::max(0, event->restartCooldownMs);
        const int clampedPostRetryMax = std::max(0, event->postRetryMax);
        const int clampedPostFailOpenThreshold = std::max(0, event->postFailOpenThreshold);
        const int clampedPostCooldownMs = std::max(0, event->postCooldownMs);
        const int clampedUpdateIntervalMs = std::max(200, event->updateIntervalMs);
        const int clampedEndTimeoutMs = std::max(500, event->endTimeoutMs);
        const int64_t eventTimestampMs = event->timestampMs;
        const float eventHappenScore = event->happenScore;

        for (size_t i = 0; i < candidates.size(); ++i)
        {
            LifecycleCandidate &candidate = candidates[i];
            if (candidate.key.empty())
            {
                continue;
            }
            const float mergedScore = std::max(eventHappenScore, candidate.score);

            auto stateInsert = mDetectLifecycleStateMap.try_emplace(candidate.key);
            DetectLifecycleState &state = stateInsert.first->second;
            if (!state.active)
            {
                state.ruleMode = event->ruleMode;
                state.requiredAlgorithms = event->requiredAlgorithms;
                state.ruleMinHits = clampedRuleMinHits;
                state.ruleHitWindowMs = clampedRuleHitWindowMs;
                state.pendingTimeoutMs = clampedPendingTimeoutMs;
                state.restartCooldownMs = clampedRestartCooldownMs;
                state.postRetryMax = clampedPostRetryMax;
                state.postFailOpenThreshold = clampedPostFailOpenThreshold;
                state.postCooldownMs = clampedPostCooldownMs;

                auto lastEndIt = mDetectLifecycleLastEndMap.find(candidate.key);
                if (lastEndIt != mDetectLifecycleLastEndMap.end() &&
                    state.restartCooldownMs > 0 &&
                    eventTimestampMs - lastEndIt->second < state.restartCooldownMs)
                {
                    ++blockedByRestartCooldownCount;
                    continue;
                }

                if (state.pendingWindowStartMs <= 0 ||
                    eventTimestampMs - state.pendingWindowStartMs > state.ruleHitWindowMs)
                {
                    state.pendingWindowStartMs = eventTimestampMs;
                    state.pendingHitCount = 1;
                    state.eventClass = candidate.eventClass;
                    state.algorithmCodes.clear();
                    state.algorithmCodeSet.clear();
                    state.ruleId = candidate.ruleId;
                    state.customEventName = candidate.customEventName;
                    state.behaviorType = candidate.behaviorType;
                    state.regionId = candidate.regionId;
                    state.regionName = candidate.regionName;
                    state.lineId = candidate.lineId;
                    state.lineName = candidate.lineName;
                    state.crossingDirection = candidate.crossingDirection;
                    state.sequenceId = candidate.sequenceId;
                    state.sequenceStageIndex = candidate.sequenceStageIndex;
                    state.sequenceStageCount = candidate.sequenceStageCount;
                    state.sequenceLogicMode = candidate.sequenceLogicMode;
                    state.directionAngleDeg = candidate.directionAngleDeg;
                    state.trackId = candidate.trackId;
                    state.relationTargetTrackId = candidate.relationTargetTrackId;
                    state.relationTargetClassName = candidate.relationTargetClassName;
                    state.relationDistancePx = candidate.relationDistancePx;
                    state.aggregateCount = candidate.aggregateCount;
                    state.aggregateThresholdCount = candidate.aggregateThresholdCount;
                }
                else
                {
                    state.pendingHitCount += 1;
                }

                state.lastSeenTimestampMs = eventTimestampMs;
                state.maxScore = std::max(state.maxScore, mergedScore);
                if (!candidate.eventClass.empty())
                {
                    state.eventClass = candidate.eventClass;
                }
                if (!candidate.algorithmCodes.empty())
                {
                    state.algorithmCodes.reserve(state.algorithmCodes.size() + candidate.algorithmCodes.size());
                }
                for (const std::string &algorithmCode : candidate.algorithmCodes)
                {
                    if (state.algorithmCodeSet.insert(algorithmCode).second)
                    {
                        state.algorithmCodes.push_back(algorithmCode);
                    }
                }

                if (state.pendingHitCount < state.ruleMinHits)
                {
                    ++blockedByMinHitsCount;
                    continue;
                }

                state.active = true;
                state.eventId = "evt-" + event->controlCode + "-" + std::to_string(eventTimestampMs) + "-" + std::to_string(i);
                const bool saveAlarmMedia = event->saveImageEnabled || event->saveVideoEnabled;
                state.videoPath = saveAlarmMedia ? buildPresetAlarmVideoPath(event->controlCode, state.eventId) : "";
                state.controlCode = event->controlCode;
                state.streamCode = event->streamCode;
                state.streamApp = event->streamApp;
                state.streamName = event->streamName;
                state.imagePath = event->imagePath;
                state.nodeCode = event->nodeCode;
                state.renderMode = event->renderMode;
                state.startTimestampMs = eventTimestampMs;
                state.lastSeenTimestampMs = eventTimestampMs;
                state.lastUpdateSentTimestampMs = eventTimestampMs;
                state.maxScore = mergedScore;
                state.eventClass = candidate.eventClass;
                state.algorithmCodes = candidate.algorithmCodes;
                state.algorithmCodeSet = candidate.algorithmCodeSet;
                state.ruleId = candidate.ruleId;
                state.customEventName = candidate.customEventName;
                state.behaviorType = candidate.behaviorType;
                state.regionId = candidate.regionId;
                state.regionName = candidate.regionName;
                state.lineId = candidate.lineId;
                state.lineName = candidate.lineName;
                state.crossingDirection = candidate.crossingDirection;
                state.sequenceId = candidate.sequenceId;
                state.sequenceStageIndex = candidate.sequenceStageIndex;
                state.sequenceStageCount = candidate.sequenceStageCount;
                state.sequenceLogicMode = candidate.sequenceLogicMode;
                state.directionAngleDeg = candidate.directionAngleDeg;
                state.trackId = candidate.trackId;
                state.relationTargetTrackId = candidate.relationTargetTrackId;
                state.relationTargetClassName = candidate.relationTargetClassName;
                state.relationDistancePx = candidate.relationDistancePx;
                state.aggregateCount = candidate.aggregateCount;
                state.aggregateThresholdCount = candidate.aggregateThresholdCount;
                state.updateIntervalMs = clampedUpdateIntervalMs;
                state.endTimeoutMs = clampedEndTimeoutMs;
                state.startHitCount = state.pendingHitCount;
                state.pendingHitCount = 0;
                state.pendingWindowStartMs = 0;
                if (saveAlarmMedia)
                {
                    bindAlarmMedia(state.controlCode, "", state.eventId, state.behaviorType, state.ruleId, state.videoPath, state.imagePath);
                }
                sendDetectLifecycleEvent(candidate.key, state, *event, "start");
                mDetectLifecycleStarted.fetch_add(1);
                continue;
            }

            state.lastSeenTimestampMs = eventTimestampMs;
            state.maxScore = std::max(state.maxScore, mergedScore);
            if (!event->imagePath.empty())
            {
                state.imagePath = event->imagePath;
            }
            if (state.eventClass.empty() && !candidate.eventClass.empty())
            {
                state.eventClass = candidate.eventClass;
            }
            if (!candidate.ruleId.empty())
            {
                state.ruleId = candidate.ruleId;
                state.customEventName = candidate.customEventName;
                state.behaviorType = candidate.behaviorType;
                state.regionId = candidate.regionId;
                state.regionName = candidate.regionName;
                state.lineId = candidate.lineId;
                state.lineName = candidate.lineName;
                state.crossingDirection = candidate.crossingDirection;
                state.sequenceId = candidate.sequenceId;
                state.sequenceStageIndex = candidate.sequenceStageIndex;
                state.sequenceStageCount = candidate.sequenceStageCount;
                state.sequenceLogicMode = candidate.sequenceLogicMode;
                state.directionAngleDeg = candidate.directionAngleDeg;
                state.trackId = candidate.trackId;
                state.relationTargetTrackId = candidate.relationTargetTrackId;
                state.relationTargetClassName = candidate.relationTargetClassName;
                state.relationDistancePx = candidate.relationDistancePx;
                state.aggregateCount = candidate.aggregateCount;
                state.aggregateThresholdCount = candidate.aggregateThresholdCount;
            }
            if (!candidate.algorithmCodes.empty())
            {
                state.algorithmCodes.reserve(state.algorithmCodes.size() + candidate.algorithmCodes.size());
            }
            for (const std::string &algorithmCode : candidate.algorithmCodes)
            {
                if (state.algorithmCodeSet.insert(algorithmCode).second)
                {
                    state.algorithmCodes.push_back(algorithmCode);
                }
            }

            if (eventTimestampMs - state.lastUpdateSentTimestampMs >= state.updateIntervalMs)
            {
                state.lastUpdateSentTimestampMs = eventTimestampMs;
                sendDetectLifecycleEvent(candidate.key, state, *event, "update");
            }
        }

        int64_t activeCount = 0;
        int64_t pendingCount = 0;
        for (const auto &entry : mDetectLifecycleStateMap)
        {
            if (entry.second.active)
            {
                ++activeCount;
            }
            else
            {
                ++pendingCount;
            }
        }
        mDetectLifecycleActive.store(activeCount);
        mDetectLifecyclePending.store(pendingCount);
        int64_t peak = mDetectLifecyclePendingPeak.load();
        while (pendingCount > peak && !mDetectLifecyclePendingPeak.compare_exchange_weak(peak, pendingCount))
        {
        }
        if (!blockedByRuleClasses.empty())
        {
            mDetectLifecycleBlockedByRule.fetch_add(static_cast<int64_t>(blockedByRuleClasses.size()));
        }
        if (blockedByMinHitsCount > 0)
        {
            mDetectLifecycleBlockedByMinHits.fetch_add(blockedByMinHitsCount);
        }
        if (blockedByRestartCooldownCount > 0)
        {
            mDetectLifecycleBlockedByRestartCooldown.fetch_add(blockedByRestartCooldownCount);
        }
    }

    void Scheduler::flushDetectLifecycleByTimeout(int64_t nowMs)
    {
        std::vector<std::string> endedKeys;
        endedKeys.reserve(mDetectLifecycleStateMap.size());
        int64_t pendingEvicted = 0;
        for (auto it = mDetectLifecycleStateMap.begin(); it != mDetectLifecycleStateMap.end(); ++it)
        {
            DetectLifecycleState &state = it->second;
            if (!state.active)
            {
                if (nowMs - state.lastSeenTimestampMs > state.pendingTimeoutMs)
                {
                    endedKeys.push_back(it->first);
                    ++pendingEvicted;
                }
                continue;
            }
            if (nowMs - state.lastSeenTimestampMs < state.endTimeoutMs)
            {
                continue;
            }

            DetectFrameEvent event;
            event.controlCode = state.controlCode;
            event.streamCode = state.streamCode;
            event.streamApp = state.streamApp;
            event.streamName = state.streamName;
            event.nodeCode = state.nodeCode.empty() ? mConfig->code : state.nodeCode;
            event.renderMode = state.renderMode;
            event.timestampMs = nowMs;
            event.happen = false;
            event.happenScore = state.maxScore;
            sendDetectLifecycleEvent(it->first, state, event, "end");
            mDetectLifecycleEnded.fetch_add(1);
            mDetectLifecycleLastEndMap[it->first] = nowMs;
            endedKeys.push_back(it->first);
        }

        for (size_t i = 0; i < endedKeys.size(); ++i)
        {
            mDetectLifecycleStateMap.erase(endedKeys[i]);
        }
        int64_t activeCount = 0;
        int64_t pendingCount = 0;
        for (auto it = mDetectLifecycleStateMap.begin(); it != mDetectLifecycleStateMap.end(); ++it)
        {
            if (it->second.active)
            {
                ++activeCount;
            }
            else
            {
                ++pendingCount;
            }
        }
        mDetectLifecycleActive.store(activeCount);
        mDetectLifecyclePending.store(pendingCount);
        int64_t peak = mDetectLifecyclePendingPeak.load();
        while (pendingCount > peak && !mDetectLifecyclePendingPeak.compare_exchange_weak(peak, pendingCount))
        {
        }
        if (pendingEvicted > 0)
        {
            mDetectLifecyclePendingEvicted.fetch_add(pendingEvicted);
        }

        if (mDetectLifecycleLastEndMap.size() > 4096)
        {
            for (auto it = mDetectLifecycleLastEndMap.begin(); it != mDetectLifecycleLastEndMap.end();)
            {
                if (nowMs - it->second > 600000)
                {
                    it = mDetectLifecycleLastEndMap.erase(it);
                }
                else
                {
                    ++it;
                }
            }
        }
    }

    void Scheduler::addDetectFrameEvent(DetectFrameEvent *event)
    {
        if (!event)
        {
            return;
        }

        std::lock_guard<std::mutex> lock(mDetectFrameQ_mtx);

        mDetectFrameEnqueued.fetch_add(1);

        if (shouldDebounceDetectFrame(event))
        {
            delete event;
            mDetectFrameDebounced.fetch_add(1);
            return;
        }

        // Coalesce adjacent events of same stream to reduce pressure under burst.
        if (!mDetectFrameQ.empty())
        {
            DetectFrameEvent *tail = mDetectFrameQ.back();
            if (tail && tail->controlCode == event->controlCode && tail->streamCode == event->streamCode)
            {
                int64_t delta = event->timestampMs - tail->timestampMs;
                if (delta >= 0 && delta <= 200)
                {
                    delete tail;
                    mDetectFrameQ.back() = event;
                    mDetectFrameQ_cv.notify_one();
                    return;
                }
            }
        }

        while (mDetectFrameQ.size() >= 64)
        {
            DetectFrameEvent *dropped = mDetectFrameQ.front();
            mDetectFrameQ.pop();
            delete dropped;
            mDetectFrameDropped.fetch_add(1);
        }
        mDetectFrameQ.push(event);
        int64_t qsz = static_cast<int64_t>(mDetectFrameQ.size());
        int64_t peak = mDetectFrameQueuePeak.load();
        while (qsz > peak && !mDetectFrameQueuePeak.compare_exchange_weak(peak, qsz))
        {
        }
        mDetectFrameQ_cv.notify_one();
    }

    bool Scheduler::getDetectFrameEvent(DetectFrameEvent *&event, int &qSize)
    {
        std::lock_guard<std::mutex> lock(mDetectFrameQ_mtx);
        if (mDetectFrameQ.empty())
        {
            qSize = 0;
            return false;
        }

        event = mDetectFrameQ.front();
        mDetectFrameQ.pop();
        qSize = static_cast<int>(mDetectFrameQ.size());
        return true;
    }

    void Scheduler::clearDetectFrameQueue()
    {
        std::lock_guard<std::mutex> lock(mDetectFrameQ_mtx);
        while (!mDetectFrameQ.empty())
        {
            DetectFrameEvent *event = mDetectFrameQ.front();
            mDetectFrameQ.pop();
            delete event;
        }
        mDetectFrameDebounceMap.clear();
        mDetectPostCircuitMap.clear();
    }

    void Scheduler::getDetectFrameStats(int &queueSize,
                                        int64_t &enqueued,
                                        int64_t &dropped,
                                        int64_t &sent,
                                        int64_t &postFailed,
                                        int64_t &queuePeak,
                                        int64_t &activeEvents,
                                        int64_t &pendingEvents,
                                        int64_t &pendingPeak,
                                        int64_t &pendingEvicted,
                                        int64_t &lifecycleStarted,
                                        int64_t &lifecycleEnded,
                                        int64_t &blockedByRule,
                                        int64_t &blockedByMinHits,
                                        int64_t &blockedByRestartCooldown,
                                        int64_t &debounced,
                                        int64_t &retried,
                                        int64_t &eventSent,
                                        int64_t &eventPostFailed,
                                        int64_t &eventRetried,
                                        int64_t &postSkipped,
                                        int64_t &postCooldownActive,
                                        int64_t &postCircuitStreams,
                                        int64_t &postConsecutiveFailedTotal,
                                        int64_t &postConsecutiveFailedMax,
                                        int64_t &postCooldownMaxRemainMs)
    {
        std::lock_guard<std::mutex> lock(mDetectFrameQ_mtx);
        queueSize = static_cast<int>(mDetectFrameQ.size());
        enqueued = mDetectFrameEnqueued.load();
        dropped = mDetectFrameDropped.load();
        sent = mDetectFrameSent.load();
        postFailed = mDetectFramePostFailed.load();
        queuePeak = mDetectFrameQueuePeak.load();
        activeEvents = mDetectLifecycleActive.load();
        pendingEvents = mDetectLifecyclePending.load();
        pendingPeak = mDetectLifecyclePendingPeak.load();
        pendingEvicted = mDetectLifecyclePendingEvicted.load();
        lifecycleStarted = mDetectLifecycleStarted.load();
        lifecycleEnded = mDetectLifecycleEnded.load();
        blockedByRule = mDetectLifecycleBlockedByRule.load();
        blockedByMinHits = mDetectLifecycleBlockedByMinHits.load();
        blockedByRestartCooldown = mDetectLifecycleBlockedByRestartCooldown.load();
        debounced = mDetectFrameDebounced.load();
        retried = mDetectFrameRetried.load();
        eventSent = mDetectEventSent.load();
        eventPostFailed = mDetectEventPostFailed.load();
        eventRetried = mDetectEventRetried.load();
        postSkipped = mDetectPostSkipped.load();
        int64_t nowMs = getCurTimestamp();
        postCooldownActive = 0;
        postCircuitStreams = static_cast<int64_t>(mDetectPostCircuitMap.size());
        postConsecutiveFailedTotal = 0;
        postConsecutiveFailedMax = 0;
        postCooldownMaxRemainMs = 0;
        for (auto it = mDetectPostCircuitMap.begin(); it != mDetectPostCircuitMap.end(); ++it)
        {
            postConsecutiveFailedTotal += it->second.consecutiveFailed;
            if (it->second.consecutiveFailed > postConsecutiveFailedMax)
            {
                postConsecutiveFailedMax = it->second.consecutiveFailed;
            }
            if (nowMs < it->second.cooldownUntilMs)
            {
                ++postCooldownActive;
                int64_t remainMs = it->second.cooldownUntilMs - nowMs;
                if (remainMs > postCooldownMaxRemainMs)
                {
                    postCooldownMaxRemainMs = remainMs;
                }
            }
        }
    }

    bool Scheduler::getAlarm(Alarm *&alarm, int &alarmQSize)
    {
        mAlarmQ_mtx.lock();

        if (!mAlarmQ.empty())
        {
            alarm = mAlarmQ.front();
            mAlarmQ.pop();
            alarmQSize = mAlarmQ.size();
            mAlarmQ_mtx.unlock();
            return true;
        }
        else
        {
            alarmQSize = 0;
            mAlarmQ_mtx.unlock();
            return false;
        }
    }
    void Scheduler::clearAlarmQueue()
    {
        std::lock_guard<std::mutex> lock(mAlarmQ_mtx);
        while (!mAlarmQ.empty())
        {
            Alarm *alarm = mAlarmQ.front();
            mAlarmQ.pop();
            delete alarm;
            alarm = nullptr;
        }
    }

    void Scheduler::clearStreamTemporalContext(const std::string &streamCode)
    {
        std::lock_guard<std::mutex> lock(mStreamTemporalMtx);
        mStreamTemporalContextMap.erase(streamCode);
    }

    /**
     * @brief Update temporal tracking state for a stream after inference.
     * 
     * Teaching note: This is the bridge between raw detections and behavior analysis.
     * The TemporalProcessor enriches each DetectObject with:
     * - trackId (greedy IoU matching across frames)
     * - trail (center-point history for trajectory analysis)
     * - speed, direction, motion state
     * - region enter/exit/dwell states
     */
    void Scheduler::updateTemporalTracks(const Control &control,
                                          const std::string &streamCode,
                                          std::vector<DetectObject *> detects,
                                          int64_t timestampMs)
    {
        std::lock_guard<std::mutex> lock(mStreamTemporalMtx);
        
        // Get or create temporal context for this stream
        StreamTemporalContext &context = mStreamTemporalContextMap[streamCode];
        context.streamCode = streamCode;
        
        // Run the tracker
        TemporalProcessor::updateStream(context, control, detects, timestampMs);
    }

    /**
     * @brief Evaluate aggregate behavior rules (count_threshold, absence, occupancy).
     * 
     * Teaching note: Unlike atomic behaviors evaluated per-detection, aggregate behaviors
     * look at all detections in a frame together (e.g., "at least N people in region").
     */
    void Scheduler::evaluateAggregateBehaviorRules(const Control &control,
                                     const std::string &streamCode,
                                     const std::vector<DetectObject> &detects,
                                     int64_t timestampMs,
                                     std::vector<AggregateBehaviorMatch> &matches)
    {
        std::lock_guard<std::mutex> lock(mAggregateBehaviorStateMtx);
        
        for (size_t i = 0; i < control.behaviorRules.size(); ++i)
        {
            const BehaviorRuleConfig &rule = control.behaviorRules[i];
            if (!rule.enabled)
            {
                continue;
            }

            if (rule.behaviorType != "count_threshold" && 
                rule.behaviorType != "absence" && 
                rule.behaviorType != "occupancy")
            {
                continue;
            }

            // Count matching objects in the target region/line/full-frame
            int objectCount = 0;
            float maxScore = 0.0f;
            std::string sampleAlgorithmCode;
            
            for (size_t di = 0; di < detects.size(); ++di)
            {
                const DetectObject &detect = detects[di];
                
                // Object class filter
                if (!rule.ruleObjectCode.empty() && detect.class_name != rule.ruleObjectCode)
                {
                    continue;
                }
                
                // Region filter (if geometryId is specified)
                if (!rule.geometryId.empty())
                {
                    auto it = detect.regionStates.find(rule.geometryId);
                    if (it == detect.regionStates.end() || !it->second.inRegion)
                    {
                        continue;
                    }
                }
                
                ++objectCount;
                if (detect.class_score > maxScore)
                {
                    maxScore = detect.class_score;
                    sampleAlgorithmCode = detect.source_algorithm;
                }
            }

            // Generate aggregate key for state tracking
            std::string aggKey = streamCode + "|" + rule.id + "|" + rule.behaviorType;

            if (rule.behaviorType == "count_threshold")
            {
                if (objectCount >= rule.thresholdCount)
                {
                    AggregateBehaviorMatch match;
                    match.ruleId = rule.id;
                    match.customEventName = rule.customEventName;
                    match.behaviorType = "count_threshold";
                    match.regionId = rule.geometryId;
                    match.objectCount = objectCount;
                    match.thresholdCount = rule.thresholdCount;
                    match.score = maxScore;
                    match.eventClass = rule.ruleObjectCode;
                    if (!sampleAlgorithmCode.empty())
                    {
                        match.algorithmCodes.push_back(sampleAlgorithmCode);
                    }
                    matches.push_back(match);
                }
            }
            else if (rule.behaviorType == "absence")
            {
                // absence = no matching objects for thresholdMs
                auto &aggState = mAggregateBehaviorStateMap[aggKey];
                
                if (objectCount == 0)
                {
                    if (!aggState.conditionActive)
                    {
                        aggState.conditionActive = true;
                        aggState.activeSinceTimestampMs = timestampMs;
                    }
                    else if (timestampMs - aggState.activeSinceTimestampMs >= rule.thresholdMs)
                    {
                        aggState.conditionActive = false; // Prevent re-trigger
                        
                        AggregateBehaviorMatch match;
                        match.ruleId = rule.id;
                        match.customEventName = rule.customEventName;
                        match.behaviorType = "absence";
                        match.regionId = rule.geometryId;
                        matches.push_back(match);
                    }
                }
                else
                {
                    aggState.conditionActive = false;
                    aggState.activeSinceTimestampMs = 0;
                }
                aggState.lastUpdatedTimestampMs = timestampMs;
            }
            else if (rule.behaviorType == "occupancy")
            {
                // occupancy = at least 1 matching object for thresholdMs
                auto &aggState = mAggregateBehaviorStateMap[aggKey];
                
                if (objectCount > 0)
                {
                    if (!aggState.conditionActive)
                    {
                        aggState.conditionActive = true;
                        aggState.activeSinceTimestampMs = timestampMs;
                    }
                    else if (timestampMs - aggState.activeSinceTimestampMs >= rule.thresholdMs)
                    {
                        aggState.conditionActive = false; // Prevent re-trigger
                        
                        AggregateBehaviorMatch match;
                        match.ruleId = rule.id;
                        match.customEventName = rule.customEventName;
                        match.behaviorType = "occupancy";
                        match.regionId = rule.geometryId;
                        match.objectCount = objectCount;
                        match.score = maxScore;
                        match.eventClass = rule.ruleObjectCode;
                        matches.push_back(match);
                    }
                }
                else
                {
                    aggState.conditionActive = false;
                    aggState.activeSinceTimestampMs = 0;
                }
                aggState.lastUpdatedTimestampMs = timestampMs;
            }
        }
    }

    void Scheduler::evaluateRelationalBehaviorRules(const Control &control,
                                     const std::string &streamCode,
                                     const std::vector<DetectObject> &detects,
                                     int64_t timestampMs,
                                     std::vector<AggregateBehaviorMatch> &matches)
    {
        if (detects.empty() || control.behaviorRules.empty())
        {
            return;
        }

        std::lock_guard<std::mutex> lock(mAggregateBehaviorStateMtx);

        const std::string streamMarker = control.code + "|stream|" + streamCode + "|rule|";
        for (auto it = mRelationalBehaviorStateMap.begin(); it != mRelationalBehaviorStateMap.end();)
        {
            if (it->first.find(streamMarker) != 0)
            {
                ++it;
                continue;
            }
            if (timestampMs - it->second.lastUpdatedTimestampMs > 60000)
            {
                it = mRelationalBehaviorStateMap.erase(it);
            }
            else
            {
                ++it;
            }
        }

        for (size_t i = 0; i < control.behaviorRules.size(); ++i)
        {
            const BehaviorRuleConfig &rule = control.behaviorRules[i];
            if (!isRelationalBehaviorRule(rule))
            {
                continue;
            }

            const RegionConfig *region = nullptr;
            if (!rule.geometryId.empty())
            {
                region = control.findRegionById(rule.geometryId);
                if (!region)
                {
                    continue;
                }
            }

            for (size_t subjectIndex = 0; subjectIndex < detects.size(); ++subjectIndex)
            {
                const DetectObject &subject = detects[subjectIndex];
                if (subject.trackId < 0)
                {
                    continue;
                }
                if (!rule.subjectObject.empty() && subject.class_name != rule.subjectObject)
                {
                    continue;
                }
                if (region)
                {
                    auto regionIt = subject.regionStates.find(region->id);
                    if (regionIt == subject.regionStates.end() || !regionIt->second.inRegion)
                    {
                        continue;
                    }
                }

                double nearestDistancePx = std::numeric_limits<double>::max();
                int nearestTargetTrackId = -1;
                std::string nearestTargetClassName;
                float nearestTargetScore = 0.0f;
                float nearestTargetSpeedPxPerSec = 0.0f;
                bool hasContainedTarget = false;
                std::unordered_set<std::string> algorithmCodeSet;
                if (!subject.source_algorithm.empty())
                {
                    algorithmCodeSet.insert(subject.source_algorithm);
                }

                for (size_t targetIndex = 0; targetIndex < detects.size(); ++targetIndex)
                {
                    if (targetIndex == subjectIndex)
                    {
                        continue;
                    }
                    const DetectObject &target = detects[targetIndex];
                    if (target.trackId < 0 || target.trackId == subject.trackId)
                    {
                        continue;
                    }
                    if (!rule.targetObject.empty() && target.class_name != rule.targetObject)
                    {
                        continue;
                    }
                    if (region)
                    {
                        auto regionIt = target.regionStates.find(region->id);
                        if (regionIt == target.regionStates.end() || !regionIt->second.inRegion)
                        {
                            continue;
                        }
                    }

                    const double distancePx = detectCenterDistancePx(subject, target);
                    if (isDetectCenterInsideSubjectBox(subject, target))
                    {
                        hasContainedTarget = true;
                    }
                    if (distancePx >= nearestDistancePx)
                    {
                        continue;
                    }
                    nearestDistancePx = distancePx;
                    nearestTargetTrackId = target.trackId;
                    nearestTargetClassName = target.class_name;
                    nearestTargetScore = target.class_score;
                    nearestTargetSpeedPxPerSec = target.speedPxPerSec;
                    if (!target.source_algorithm.empty())
                    {
                        algorithmCodeSet.insert(target.source_algorithm);
                    }
                }

                if ((rule.behaviorType == "relation_near" || rule.behaviorType == "relation_apart" || rule.behaviorType == "fight") &&
                    (nearestTargetTrackId < 0 || !std::isfinite(nearestDistancePx)))
                {
                    continue;
                }

                bool conditionActive = false;
                if (rule.behaviorType == "relation_near")
                {
                    conditionActive = nearestDistancePx <= rule.distanceThresholdPx;
                }
                else if (rule.behaviorType == "relation_apart")
                {
                    conditionActive = nearestDistancePx >= rule.distanceThresholdPx;
                }
                else if (rule.behaviorType == "relation_not_contains")
                {
                    conditionActive = !hasContainedTarget;
                }
                else if (rule.behaviorType == "fight")
                {
                    const double minMotionSpeed = rule.maxSpeedPxPerSec > 0.0 ? rule.maxSpeedPxPerSec : 20.0;
                    const bool distanceHit = nearestDistancePx <= rule.distanceThresholdPx;
                    const bool motionHit = (subject.speedPxPerSec >= minMotionSpeed) || (nearestTargetSpeedPxPerSec >= minMotionSpeed);
                    conditionActive = distanceHit && motionHit;
                }

                const std::string stateKey = streamMarker + rule.id + "|subject|" + std::to_string(subject.trackId);
                if (!conditionActive)
                {
                    auto stateIt = mRelationalBehaviorStateMap.find(stateKey);
                    if (stateIt != mRelationalBehaviorStateMap.end())
                    {
                        stateIt->second.conditionActive = false;
                        stateIt->second.activeSinceTimestampMs = 0;
                        stateIt->second.lastUpdatedTimestampMs = timestampMs;
                        stateIt->second.lastDistancePx = nearestDistancePx;
                        stateIt->second.relationTargetTrackId = nearestTargetTrackId;
                    }
                    continue;
                }

                RelationalBehaviorRuntimeState &state = mRelationalBehaviorStateMap[stateKey];
                state.lastUpdatedTimestampMs = timestampMs;
                state.lastDistancePx = nearestDistancePx;
                state.relationTargetTrackId = nearestTargetTrackId;
                if (!state.conditionActive || state.activeSinceTimestampMs <= 0)
                {
                    state.conditionActive = true;
                    state.activeSinceTimestampMs = timestampMs;
                }

                const int64_t activeDurationMs = std::max<int64_t>(0, timestampMs - state.activeSinceTimestampMs);
                if (rule.thresholdMs > 0 && activeDurationMs < rule.thresholdMs)
                {
                    continue;
                }

                AggregateBehaviorMatch match;
                match.ruleId = rule.id;
                match.customEventName = rule.customEventName;
                match.behaviorType = rule.behaviorType;
                match.regionId = region ? region->id : "";
                match.regionName = region ? (region->name.empty() ? region->id : region->name) : "";
                match.eventClass = subject.class_name.empty() ? (!rule.subjectObject.empty() ? rule.subjectObject : "object") : subject.class_name;
                match.trackId = subject.trackId;
                if (rule.behaviorType == "relation_not_contains")
                {
                    match.relationTargetTrackId = -1;
                    match.relationTargetClassName = rule.targetObject;
                    match.relationDistancePx = -1.0;
                }
                else
                {
                    match.relationTargetTrackId = nearestTargetTrackId;
                    match.relationTargetClassName = nearestTargetClassName;
                    match.relationDistancePx = nearestDistancePx;
                }
                match.objectCount = (rule.behaviorType == "relation_not_contains") ? 1 : 2;
                match.thresholdCount = 0;
                match.score = std::max(subject.class_score, nearestTargetScore);
                match.algorithmCodes.assign(algorithmCodeSet.begin(), algorithmCodeSet.end());
                match.sequenceId = rule.sequenceId;
                match.sequenceStageIndex = rule.stageIndex;
                match.sequenceLogicMode = rule.logicMode.empty() ? "all" : rule.logicMode;
                matches.push_back(std::move(match));
            }
        }
    }

    void Scheduler::applySequenceBehaviorRules(const Control &control,
                                     const std::string &streamCode,
                                     std::vector<DetectObject> &detects,
                                     std::vector<AggregateBehaviorMatch> &matches,
                                     int64_t timestampMs)
    {
        std::unordered_map<std::string, const BehaviorRuleConfig *> behaviorRuleById;
        std::unordered_map<std::string, std::vector<SequenceStageConfig>> sequenceStagesById;
        std::unordered_map<std::string, size_t> sequenceStagePositionByRuleId;
        behaviorRuleById.reserve(control.behaviorRules.size());
        sequenceStagesById.reserve(control.behaviorRules.size());
        sequenceStagePositionByRuleId.reserve(control.behaviorRules.size());

        bool hasSequenceRules = false;
        for (size_t ruleIndex = 0; ruleIndex < control.behaviorRules.size(); ++ruleIndex)
        {
            const BehaviorRuleConfig &rule = control.behaviorRules[ruleIndex];
            if (!rule.id.empty())
            {
                behaviorRuleById[rule.id] = &rule;
            }
            if (!isSequenceBehaviorRule(rule))
            {
                continue;
            }

            hasSequenceRules = true;
            std::vector<SequenceStageConfig> &stages = sequenceStagesById[rule.sequenceId];
            if (stages.empty() || stages.back().stageIndex != rule.stageIndex)
            {
                SequenceStageConfig stage;
                stage.stageIndex = rule.stageIndex;
                stage.logicMode = rule.logicMode.empty() ? "all" : rule.logicMode;
                stage.stageTimeoutMs = std::max<int64_t>(0, rule.stageTimeoutMs);
                stage.stageHoldMs = std::max<int64_t>(0, rule.stageHoldMs);
                stages.push_back(stage);
            }
            stages.back().rules.push_back(&rule);
            if (!rule.id.empty())
            {
                sequenceStagePositionByRuleId[rule.id] = stages.size() - 1;
            }
        }

        if (!hasSequenceRules)
        {
            return;
        }

        for (auto &entry : sequenceStagesById)
        {
            std::vector<SequenceStageConfig> &stages = entry.second;
            for (size_t stageIndex = 0; stageIndex < stages.size(); ++stageIndex)
            {
                SequenceStageConfig &stage = stages[stageIndex];
                std::sort(stage.rules.begin(), stage.rules.end(), [](const BehaviorRuleConfig *lhs, const BehaviorRuleConfig *rhs) {
                    return lhs->id < rhs->id;
                });
                for (size_t ruleIndex = 0; ruleIndex < stage.rules.size(); ++ruleIndex)
                {
                    if (!stage.rules[ruleIndex]->id.empty())
                    {
                        sequenceStagePositionByRuleId[stage.rules[ruleIndex]->id] = stageIndex;
                    }
                }
            }
        }

        std::lock_guard<std::mutex> lock(mAggregateBehaviorStateMtx);
        const std::string sequenceStreamMarker = control.code + "|stream|" + streamCode + "|sequence|";
        for (auto it = mSequenceBehaviorStateMap.begin(); it != mSequenceBehaviorStateMap.end();)
        {
            if (it->first.find(sequenceStreamMarker) != 0)
            {
                ++it;
                continue;
            }
            const int64_t lastActiveMs = std::max(it->second.lastMatchedTimestampMs, it->second.lastCompletedTimestampMs);
            if (lastActiveMs > 0 && timestampMs - lastActiveMs > 60000)
            {
                it = mSequenceBehaviorStateMap.erase(it);
            }
            else
            {
                ++it;
            }
        }

        auto resetSequenceState = [](SequenceBehaviorRuntimeState &state, int stageCount) {
            state.completedStageIndex = -1;
            state.currentStageIndex = -1;
            state.stageCount = stageCount;
            state.stageStartTimestampMs = 0;
            state.lastMatchedTimestampMs = 0;
            state.lastCompletedTimestampMs = 0;
            state.matchedRuleIds.clear();
        };

        auto processRuleHit = [&](const std::string &ruleId,
                                  int trackId,
                                  std::string &sequenceId,
                                  int &sequenceStageIndex,
                                  int &sequenceStageCount,
                                  std::string &sequenceLogicMode) -> bool {
            if (trackId < 0 || ruleId.empty())
            {
                return true;
            }
            auto ruleIt = behaviorRuleById.find(ruleId);
            if (ruleIt == behaviorRuleById.end() || !ruleIt->second || !isSequenceBehaviorRule(*ruleIt->second))
            {
                return true;
            }

            const BehaviorRuleConfig &matchedRule = *ruleIt->second;
            auto sequenceIt = sequenceStagesById.find(matchedRule.sequenceId);
            if (sequenceIt == sequenceStagesById.end() || sequenceIt->second.empty())
            {
                return false;
            }
            auto positionIt = sequenceStagePositionByRuleId.find(matchedRule.id);
            if (positionIt == sequenceStagePositionByRuleId.end())
            {
                return false;
            }

            const int matchedStageIndex = static_cast<int>(positionIt->second);
            const std::vector<SequenceStageConfig> &stages = sequenceIt->second;
            const int stageCount = static_cast<int>(stages.size());
            if (matchedStageIndex < 0 || matchedStageIndex >= stageCount)
            {
                return false;
            }

            const SequenceStageConfig &stage = stages[matchedStageIndex];
            const std::string stateKey = sequenceStreamMarker + matchedRule.sequenceId + "|track|" + std::to_string(trackId);
            SequenceBehaviorRuntimeState &state = mSequenceBehaviorStateMap[stateKey];
            if (state.stageCount != stageCount)
            {
                resetSequenceState(state, stageCount);
            }

            if (matchedStageIndex > 0 && state.lastCompletedTimestampMs > 0)
            {
                const int64_t stageTimeoutMs = std::max<int64_t>(0, stage.stageTimeoutMs);
                if (stageTimeoutMs > 0 && timestampMs - state.lastCompletedTimestampMs > stageTimeoutMs)
                {
                    resetSequenceState(state, stageCount);
                }
            }

            if (matchedStageIndex == 0 && state.completedStageIndex >= 0)
            {
                resetSequenceState(state, stageCount);
            }

            const int expectedStageIndex = state.completedStageIndex + 1;
            if (matchedStageIndex != expectedStageIndex)
            {
                if (matchedStageIndex == 0)
                {
                    resetSequenceState(state, stageCount);
                }
                else
                {
                    return false;
                }
            }

            if (state.currentStageIndex != matchedStageIndex)
            {
                state.currentStageIndex = matchedStageIndex;
                state.stageStartTimestampMs = timestampMs;
                state.matchedRuleIds.clear();
            }
            state.lastMatchedTimestampMs = timestampMs;

            if (stage.logicMode == "all" && !matchedRule.id.empty())
            {
                state.matchedRuleIds.insert(matchedRule.id);
            }

            const bool stageRulesSatisfied = (stage.logicMode == "any")
                                                 ? true
                                                 : (state.matchedRuleIds.size() >= stage.rules.size());
            if (!stageRulesSatisfied)
            {
                return false;
            }

            const int64_t stageHoldMs = std::max<int64_t>(0, stage.stageHoldMs);
            if (stageHoldMs > 0 && timestampMs - state.stageStartTimestampMs < stageHoldMs)
            {
                return false;
            }

            state.completedStageIndex = matchedStageIndex;
            state.currentStageIndex = -1;
            state.lastCompletedTimestampMs = timestampMs;
            state.matchedRuleIds.clear();
            if (matchedStageIndex + 1 < stageCount)
            {
                return false;
            }

            sequenceId = matchedRule.sequenceId;
            sequenceStageIndex = matchedStageIndex;
            sequenceStageCount = stageCount;
            sequenceLogicMode = stage.logicMode.empty() ? "all" : stage.logicMode;
            mSequenceBehaviorStateMap.erase(stateKey);
            return true;
        };

        for (size_t i = 0; i < detects.size(); ++i)
        {
            DetectObject &detect = detects[i];
            if (!detect.happen || detect.ruleId.empty())
            {
                continue;
            }
            if (!processRuleHit(detect.ruleId,
                                detect.trackId,
                                detect.sequenceId,
                                detect.sequenceStageIndex,
                                detect.sequenceStageCount,
                                detect.sequenceLogicMode))
            {
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
            }
        }

        std::vector<AggregateBehaviorMatch> completedMatches;
        completedMatches.reserve(matches.size());
        for (size_t i = 0; i < matches.size(); ++i)
        {
            AggregateBehaviorMatch match = matches[i];
            if (processRuleHit(match.ruleId,
                               match.trackId,
                               match.sequenceId,
                               match.sequenceStageIndex,
                               match.sequenceStageCount,
                               match.sequenceLogicMode))
            {
                completedMatches.push_back(std::move(match));
            }
        }
        matches.swap(completedMatches);
    }

}
