#include "Analyzer.h"
#include "Algorithm.h"
#include <json/json.h>
#include "Scheduler.h"
#include "Config.h"
#include "Control.h"
#include "Utils/Log.h"
#include "Utils/Request.h"
#include "Utils/Base64.h"
#include "Utils/CalcuIOU.h"
#include <array>
#include <algorithm>
#include <utility>

namespace SVAAnalyzer
{

    Analyzer::Analyzer(Scheduler *scheduler, Control *control) : mScheduler(scheduler),
                                                                 mControl(control)
    {
    }

    Analyzer::~Analyzer()
    {
    }

    /**
     * @brief Resolve algorithm instance by code.
     * Teaching note: All models use ONNX Runtime. Multiple algorithmCodes can map to the same instance.
     * Compatibility names (ov_* prefix) map to the matching ONNX engine for drop-in replacement.
     */
    Algorithm *Analyzer::resolveAlgorithm(const std::string &algorithmCode)
    {
        if (algorithmCode == "on_yolo11n_80")
        {
            return mScheduler->on_yolo11n_80;
        }
        if (algorithmCode == "on_yolo26n_80" || algorithmCode == "ov_yolo26n_80")
        {
            return mScheduler->on_yolo26n_80;
        }
        return nullptr;
    }

    void Analyzer::applyRegionAndObjectMatch(const AlgorithmTask &task,
                                             std::vector<DetectObject> &detects,
                                             bool &happen,
                                             float &happenScore)
    {
        /**
         * Teaching note: When behavior-only mode is active, skip classic region-IoU matching.
         * Behavior analysis is handled by the Worker's temporal tracking + BehaviorEvaluator
         * pipeline instead (cross_line, enter_region, dwell, etc.).
         */
        if (controlUsesBehaviorOnlyMode(*mControl))
        {
            // All objects in region are "happen" candidates; actual decision deferred to BehaviorEvaluator
            const std::vector<double> &recognitionRegion = mControl->recognitionRegion_d;
            DetectObject *detectsData = detects.data();
            const size_t detectCount = detects.size();
            int matchCount = 0;
            constexpr double kIouThreshold = 0.5;
            std::array<double, 8> object_d;
            
            for (size_t i = 0; i < detectCount; ++i)
            {
                DetectObject &detect = detectsData[i];
                object_d[0] = detect.x1;
                object_d[1] = detect.y1;
                object_d[2] = detect.x2;
                object_d[3] = detect.y1;
                object_d[4] = detect.x2;
                object_d[5] = detect.y2;
                object_d[6] = detect.x1;
                object_d[7] = detect.y2;
                
                const double iou = CalcuPolygonIOU(recognitionRegion, object_d);
                if (iou >= kIouThreshold)
                {
                    detect.happen = true;
                    ++matchCount;
                    happenScore = std::max(happenScore, detect.class_score);
                }
            }
            
            if (matchCount > 0)
            {
                happen = true;
                if (happenScore <= 0.0f)
                {
                    happenScore = 1.0f;
                }
            }
            return;
        }

        if (detects.empty())
        {
            return;
        }

        constexpr double kIouThreshold = 0.5;
        int matchCount = 0;
        const std::vector<double> &recognitionRegion = mControl->recognitionRegion_d;
        const std::string &objectCode = task.objectCode;
        const auto &objects = task.objects_v1;
        const std::string *objectsData = objects.data();
        const int objectsLen = task.objects_v1_len;
        std::array<double, 8> object_d;
        DetectObject *detectsData = detects.data();
        const size_t detectCount = detects.size();
        for (size_t i = 0; i < detectCount; ++i)
        {
            DetectObject &detect = detectsData[i];
            object_d[0] = detect.x1;
            object_d[1] = detect.y1;
            object_d[2] = detect.x2;
            object_d[3] = detect.y1;
            object_d[4] = detect.x2;
            object_d[5] = detect.y2;
            object_d[6] = detect.x1;
            object_d[7] = detect.y2;

            const double iou = CalcuPolygonIOU(recognitionRegion, object_d);
            if (iou < kIouThreshold)
            {
                continue;
            }

            const int class_id = detect.class_id;
            bool isTargetObject = false;
            if (class_id >= 0 && class_id < objectsLen)
            {
                const std::string &resolvedClassName = objectsData[class_id];
                if (detect.class_name != resolvedClassName)
                {
                    detect.class_name = resolvedClassName;
                }
                isTargetObject = (resolvedClassName == objectCode);
            }
            else
            {
                isTargetObject = (detect.class_name == objectCode);
            }

            if (isTargetObject)
            {
                detect.happen = true;
                ++matchCount;
                happenScore = std::max(happenScore, detect.class_score);
            }
        }

        if (matchCount > 0)
        {
            happen = true;
            if (happenScore <= 0.0f)
            {
                happenScore = 1.0f;
            }
        }
    }

    bool Analyzer::runAlgorithmTask(int64_t frameCount,
                                    AlgorithmTask &task,
                                    cv::Mat &image,
                                    std::vector<DetectObject> &taskDetects,
                                    bool &taskHappen,
                                    float &taskHappenScore,
                                    bool isKeyframe)
    {
        taskDetects.clear();
        taskHappen = false;
        taskHappenScore = 0.0f;
        const std::string &algorithmCode = task.algorithmCode;
        const float detectFps = task.detectFps;

        if (detectFps <= -1.5f)
        {
            return true;
        }
        if (detectFps <= -0.5f)
        {
            if (!isKeyframe)
            {
                return true;
            }
        }
        else if (detectFps > 0.0f)
        {
            const double detectFpsD = static_cast<double>(detectFps);
            const int64_t nowMs = getCurTime();
            const int64_t lastInferTimestampMs = task.lastInferTimestampMs;
            if (lastInferTimestampMs > 0)
            {
                const double elapsedMs = static_cast<double>(nowMs - lastInferTimestampMs);
                if (elapsedMs * detectFpsD < 1000.0)
                {
                    return true;
                }
            }
            task.lastInferTimestampMs = nowMs;
        }

        if (algorithmCode == "wensou")
        {
            const int videoFps = mControl->videoFps;
            const int len = std::max(1, videoFps * 2);
            if (frameCount % len == 0)
            {
                return postImage2Server(frameCount, image, taskDetects, taskHappen, taskHappenScore);
            }
            return true;
        }

        else if (algorithmCode == "api")
        {
            return postImage2Server(frameCount, image, taskDetects, taskHappen, taskHappenScore);
        }

        Algorithm *algorithm = task.resolvedAlgorithm;
        if (task.resolvedAlgorithmCode != algorithmCode || algorithm == nullptr)
        {
            algorithm = resolveAlgorithm(algorithmCode);
            task.resolvedAlgorithm = algorithm;
            task.resolvedAlgorithmCode = algorithmCode;
        }
        if (!algorithm)
        {
            LOGE("不支持的算法：%s", algorithmCode.data());
            return true;
        }

        {
            std::lock_guard<std::mutex> lock(mScheduler->mAlgorithmMtx);
            algorithm->objectDetect(image, taskDetects);
        }

        if (taskDetects.empty())
        {
            return true;
        }

        DetectObject *taskDetectsData = taskDetects.data();
        const size_t detectCount = taskDetects.size();
        for (size_t i = 0; i < detectCount; ++i)
        {
            DetectObject &detect = taskDetectsData[i];
            if (detect.source_algorithm != algorithmCode)
            {
                detect.source_algorithm = algorithmCode;
            }
        }

        applyRegionAndObjectMatch(task, taskDetects, taskHappen, taskHappenScore);
        return true;
    }

    bool Analyzer::handleVideoFrame(int64_t frameCount, cv::Mat &image, std::vector<DetectObject> &happenDetects, bool &happen, float &happenScore, bool isKeyframe)
    {
        Control *control = mControl;
        happenDetects.clear();
        happen = false;
        happenScore = 0.0f;

        if (control->algorithmTasks.empty())
        {
            AlgorithmTask &task = control->algorithmTasks.emplace_back();
            task.algorithmCode = control->algorithmCode;
            task.object_str = control->object_str;
            task.objects_v1 = control->objects_v1;
            task.objects_v1_len = control->objects_v1_len;
            task.objectCode = control->objectCode;
            task.detectFps = control->detectFps;
        }

        std::vector<DetectObject> taskDetects;
        bool taskHappen = false;
        float taskHappenScore = 0.0f;
        auto &algorithmTasks = control->algorithmTasks;
        const size_t taskCount = algorithmTasks.size();
        for (size_t i = 0; i < taskCount; ++i)
        {
            AlgorithmTask &task = algorithmTasks[i];
            runAlgorithmTask(frameCount, task, image, taskDetects, taskHappen, taskHappenScore, isKeyframe);
            const size_t taskDetectCount = taskDetects.size();
            if (taskDetectCount > 0)
            {
                const size_t baseSize = happenDetects.size();
                const size_t mergedSize = baseSize + taskDetectCount;
                if (happenDetects.capacity() < mergedSize)
                {
                    happenDetects.reserve(mergedSize);
                }
                happenDetects.resize(mergedSize);
                DetectObject *dst = happenDetects.data() + static_cast<std::ptrdiff_t>(baseSize);
                DetectObject *src = taskDetects.data();
                std::move(src,
                          src + static_cast<std::ptrdiff_t>(taskDetectCount),
                          dst);
            }

            if (taskHappen)
            {
                happen = true;
                happenScore = std::max(happenScore, taskHappenScore);
            }
        }

        if (happen && happenScore <= 0.0f)
        {
            happenScore = 1.0f;
        }

        return true;
    }
    bool Analyzer::postImage2Server(int64_t frameCount, cv::Mat &image, std::vector<DetectObject> &happenDetects, bool &happen, float &happenScore)
    {
        // C++示例：指定编码参数
        std::vector<uchar> jpg;
        static const std::vector<int> kJpegParams = {cv::IMWRITE_JPEG_QUALITY, 90};
        cv::imencode(".jpg", image, jpg, kJpegParams);

        // std::vector<int> JPEG_QUALITY = { 75 };
        // std::vector<uchar> jpg;
        // cv::imencode(".jpg", image, jpg, JPEG_QUALITY);

        if (jpg.empty())
        {
            return true;
        }

        const Control *control = mControl;
        const Config *config = mScheduler->getConfig();
        Base64 base64;
        std::string imageBase64;
        base64.encode(jpg.data(), static_cast<int>(jpg.size()), imageBase64);

        std::string response;
        Json::Value param;
        param["image_base64"] = imageBase64; // 当前帧
        param["nodeCode"] = config->code;
        param["controlCode"] = control->code;            // 布控编号
        param["streamCode"] = control->streamCode;       // 视频流编号
        param["streamApp"] = control->streamApp;         // 视频app
        param["streamName"] = control->streamName;       // 视频name
        param["flowCode"] = control->algorithmCode;      // 算法编号
        param["modelClassNames"] = control->object_str;  // 算法模型支持的所有目标
        param["detectClassNames"] = control->objectCode; // 当前布控选中的算法目标
        param["polygonType"] = 3;                         // 3表示绘制的算法识别区域是多边形
        param["polygon"] = control->recognitionRegion;    // 绘制的多边形识别区域

        static const Json::StreamWriterBuilder kCompactWriterBuilder = [] {
            Json::StreamWriterBuilder builder;
            builder["indentation"] = "";
            return builder;
        }();
        std::string data = Json::writeString(kCompactWriterBuilder, param);
        const char *apiUrl = control->api_url.c_str();
        // int64_t t1 = Common::getCurTimestamp();
        Request request;
        if (request.post(apiUrl, data.c_str(), response))
        {
            static const Json::CharReaderBuilder kCharReaderBuilder;
            const std::unique_ptr<Json::CharReader> reader(kCharReaderBuilder.newCharReader());
            const char *responseData = response.data();
            const char *responseEnd = responseData + response.size();

            Json::Value root;
            JSONCPP_STRING errs;

            /*---------------------解析JSON响应数据----------------------*/
            if (reader->parse(responseData, responseEnd,
                              &root, &errs) &&
                errs.empty())
            {
                const Json::Value &codeNode = root["code"];
                const Json::Value &msgNode = root["msg"];
                if (codeNode.isInt() && msgNode.isString())
                {                                              // 验证响应数据的格式正确性
                    const int code = codeNode.asInt();          // 获取服务器返回的状态码
                    const char *msg = msgNode.asCString();      // 获取服务器返回的状态信息

                    if (1000 == code)
                    {
                        happenDetects.clear();
                        happen = false;
                        happenScore = 0.0f;

                        const Json::Value &result = root["result"];
                        if (result["happen"].isBool() && result["happenScore"].isDouble())
                        {
                            const Json::Value &result_detects = result["detects"];
                            const Json::ArrayIndex detectCount = result_detects.size();
                            if (detectCount == 0)
                            {
                                return true;
                            }

                            const float nms_threshold = 0.5f;    // NMS重叠阈值
                            const float score_threshold = 0.25f; // 置信度阈值

                            struct FilteredClassMeta
                            {
                                int class_id;
                                const char *class_name;

                                FilteredClassMeta(int id, const char *name)
                                    : class_id(id), class_name(name)
                                {
                                }
                            };

                            // 收集并按置信度过滤，减少中间容器与一次额外遍历
                            std::vector<cv::Rect> filtered_boxes;
                            std::vector<float> filtered_scores;
                            std::vector<FilteredClassMeta> filtered_class_meta;
                            const size_t detectCountSize = static_cast<size_t>(detectCount);
                            filtered_boxes.reserve(detectCountSize);
                            filtered_scores.reserve(detectCountSize);
                            filtered_class_meta.reserve(detectCountSize);

                            // 第一步：收集所有检测结果
                            for (Json::ArrayIndex detectJsonIndex = 0; detectJsonIndex < detectCount; ++detectJsonIndex)
                            {
                                const Json::Value &detectJson = result_detects[detectJsonIndex];
                                const float class_score = detectJson["class_score"].asFloat();
                                if (class_score < score_threshold)
                                {
                                    continue;
                                }
                                const int x1 = detectJson["x1"].asInt();
                                const int y1 = detectJson["y1"].asInt();
                                const int x2 = detectJson["x2"].asInt();
                                const int y2 = detectJson["y2"].asInt();
                                const int width = x2 - x1;
                                const int height = y2 - y1;

                                filtered_boxes.emplace_back(x1, y1, width, height);
                                filtered_scores.push_back(class_score);
                                filtered_class_meta.emplace_back(detectJson["class_id"].asInt(),
                                                                 detectJson["class_name"].asCString());
                            }

                            // 第二步：执行非极大值抑制
                            if (filtered_boxes.empty())
                            {
                                return true;
                            }
                            std::vector<int> indices;
                            indices.reserve(filtered_boxes.size());
                            cv::dnn::NMSBoxes(filtered_boxes, filtered_scores, score_threshold, nms_threshold, indices);
                            const size_t appendedCount = indices.size();
                            if (appendedCount == 0)
                            {
                                return true;
                            }
                            happenDetects.resize(appendedCount);
                            DetectObject *outputDetects = happenDetects.data();
                            const int *indexData = indices.data();
                            const cv::Rect *filteredBoxesData = filtered_boxes.data();
                            const float *filteredScoresData = filtered_scores.data();
                            const FilteredClassMeta *filteredClassMetaData = filtered_class_meta.data();

                            // 第三步：保存NMS处理后的结果
                            for (size_t i = 0; i < appendedCount; ++i)
                            {
                                const int index = indexData[i];
                                const cv::Rect &box = filteredBoxesData[index];
                                const float score = filteredScoresData[index];

                                DetectObject &detect = outputDetects[i];
                                detect.x1 = box.x;
                                detect.y1 = box.y;
                                detect.x2 = box.x + box.width;
                                detect.y2 = box.y + box.height;
                                detect.class_score = score;
                                const FilteredClassMeta &meta = filteredClassMetaData[index];
                                detect.class_id = meta.class_id;
                                const char *className = meta.class_name;
                                if (detect.class_name != className)
                                {
                                    detect.class_name = className;
                                }
                            }

                            // 第四步：区域过滤和事件判断
                            constexpr double kIouThreshold = 0.5;
                            int matchCount = 0;
                            const std::vector<double> &recognitionRegion = control->recognitionRegion_d;
                            std::array<double, 8> object_d;
                            DetectObject *filteredDetects = outputDetects;

                            for (size_t i = 0; i < appendedCount; ++i)
                            {
                                DetectObject &detect = filteredDetects[i];
                                object_d[0] = detect.x1;
                                object_d[1] = detect.y1;
                                object_d[2] = detect.x2;
                                object_d[3] = detect.y1;
                                object_d[4] = detect.x2;
                                object_d[5] = detect.y2;
                                object_d[6] = detect.x1;
                                object_d[7] = detect.y2;

                                const double iou = CalcuPolygonIOU(recognitionRegion, object_d);
                                if (iou >= kIouThreshold)
                                {
                                    detect.happen = true;
                                    ++matchCount;
                                }
                            }

                            if (matchCount > 0)
                            {
                                happen = true;
                                happenScore = 1.0f;
                            }
                        }
                    }
                    else
                    {
                        LOGE("code=%d,msg=%s", code, msg);
                    }
                }
                else
                {
                    LOGE("incorrect return parameter format");
                }
            }
        }
        else
        {
            happenDetects.clear();
            happen = false;
            happenScore = 0.0f;
        }
        // int64_t t2 = Common::getCurTimestamp();

        return true;
    }

}