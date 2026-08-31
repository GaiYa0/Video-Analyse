#include "AlgorithmOnYolo.h"
#include "Config.h"
#include "Utils/Log.h"
#include "Utils/Common.h"
#include <algorithm>
#include <array>
#include <cmath>
#include <opencv2/dnn.hpp>

namespace SVAAnalyzer
{
    OnnxRuntimeEngine::OnnxRuntimeEngine(Config *config, std::string &modelPath, std::vector<std::string> &classNames, const std::string &algorithmCode) : mConfig(config), mClassNames(classNames), mAlgorithmCode(algorithmCode)
    {
        LOGI("modelPath=%s", modelPath.data());
        initPostprocessProfile(algorithmCode);

        mEnv = Ort::Env(OrtLoggingLevel::ORT_LOGGING_LEVEL_WARNING, "YOLO");
        mSessionOptions = Ort::SessionOptions();
        mSessionOptions.SetGraphOptimizationLevel(ORT_ENABLE_ALL);

        // std::cout << "onnxruntime inference try to use GPU Device" << std::endl;
        // OrtSessionOptionsAppendExecutionProvider_CUDA(session_options, 0);

        // log available providers for diagnostics
        std::vector<std::string> providers = Ort::GetAvailableProviders();

        LOGI("supported onnxruntime providers");
        for (size_t i = 0; i < providers.size(); i++)
        {
            LOGI("%zu,%s", i, providers[i].data());
        }

        bool gpuAssigned = false;
    #if SVA_ONNXRUNTIME_GPU
        /**
         * GPU provider selection strategy (teaching note):
         * 1. TensorRT: fastest, requires engine build/cache.
         * 2. CUDA: direct GPU execution.
         * 3. CPU: fallback when GPU providers are unavailable or fail to create a session.
         *
         * Build with -DSVA_ONNXRUNTIME_GPU=OFF when using CPU-only ONNX Runtime headers/libs.
         */
        auto trt_itr = std::find(providers.begin(), providers.end(), "TensorrtExecutionProvider");
        if (trt_itr != providers.end())
        {
            try
            {
                // 关键：正确初始化TensorRT配置结构体（避免空指针崩溃）
                OrtTensorRTProviderOptions trt_options = OrtTensorRTProviderOptions();

                // 基础GPU配置
                trt_options.device_id = 0;                    // 指定GPU 0
                trt_options.trt_max_workspace_size = 1 << 30; // 1GB工作空间
                trt_options.trt_fp16_enable = 1;              // 启用FP16加速

                // 修复警告：补充缺失的必填参数
                trt_options.trt_max_partition_iterations = 1000; // 日志提示的默认值
                trt_options.trt_min_subgraph_size = 1;           // 日志提示的默认值

                // 引擎缓存配置（可选，加速后续推理）
                trt_options.trt_engine_cache_path = "/opt/SVA/tmp/trt_cache";
                trt_options.trt_engine_cache_enable = 1;

                // 添加TensorRT执行提供者
                mSessionOptions.AppendExecutionProvider_TensorRT(trt_options);
                LOGI("appended TensorrtExecutionProvider (device %d)", trt_options.device_id);
                gpuAssigned = true;
                mGpuEnabled = true;
                mActiveProvider = "TensorRT";
            }
            catch (const Ort::Exception &e)
            {
                LOGI("failed to append TensorrtExecutionProvider: %s", e.what());
                gpuAssigned = false;
            }
        }

        auto itr = std::find(providers.begin(), providers.end(), "CUDAExecutionProvider");
        if (itr != providers.end())
        {
            try
            {
                OrtCUDAProviderOptions cuda_opts;
                cuda_opts.device_id = 0; // GPU 0
                mSessionOptions.AppendExecutionProvider_CUDA(cuda_opts);
                LOGI("appended CUDAExecutionProvider (device %d)", cuda_opts.device_id);
                gpuAssigned = true;
                if (mActiveProvider == "TensorRT")
                {
                    mActiveProvider = "TensorRT/CUDA";
                }
                else
                {
                    mGpuEnabled = true;
                    mActiveProvider = "CUDA";
                }
            }
            catch (const Ort::Exception &e)
            {
                LOGI("failed to append CUDAExecutionProvider: %s", e.what());
            }
        }
#else
        LOGI("SVA_ONNXRUNTIME_GPU=OFF, use CPUExecutionProvider only");
#endif

        if (!gpuAssigned)
        {
            LOGI("no GPU provider appended, fall back to CPU");
        }
        auto createSession = [this, &modelPath]() {
#ifdef WIN32
            std::wstring modelPath_ws = std::wstring(modelPath.begin(), modelPath.end());
            return Ort::Session(mEnv, modelPath_ws.c_str(), mSessionOptions);
#else
            return Ort::Session(mEnv, modelPath.c_str(), mSessionOptions);
#endif
        };

        try
        {
            mSession = createSession();
        }
        catch (const Ort::Exception &e)
        {
            if (!gpuAssigned)
            {
                throw;
            }
            LOGI("failed to create GPU ONNX Runtime session: %s, retry with CPUExecutionProvider", e.what());
            mSessionOptions.release();
            mSessionOptions = Ort::SessionOptions();
            mSessionOptions.SetGraphOptimizationLevel(ORT_ENABLE_ALL);
            mGpuEnabled = false;
            mActiveProvider = "CPU";
            mSession = createSession();
        }

    Ort::AllocatorWithDefaultOptions allocator;
    auto input_name = mSession.GetInputNameAllocated(0, allocator);
    mInputNodeName = input_name.get();
    auto input_type_info = mSession.GetInputTypeInfo(0);
    auto input_tensor_info = input_type_info.GetTensorTypeAndShapeInfo();
    auto input_dims = input_tensor_info.GetShape();
    mInputWidth = static_cast<int>(input_dims[3]);
    mInputHeight = static_cast<int>(input_dims[2]);

    auto output_name = mSession.GetOutputNameAllocated(0, allocator);
    mOutputNodeName = output_name.get();
    auto output_type_info = mSession.GetOutputTypeInfo(0);
    auto output_tensor_info = output_type_info.GetTensorTypeAndShapeInfo();
    auto output_dims = output_tensor_info.GetShape();
    if (output_dims.size() >= 3)
    {
        mOutputDims = output_dims;
        mOutputDim = static_cast<int>(output_dims[1]);
        mOutputRow = static_cast<int>(output_dims[2]);
        if (mOutputDims.back() == 6)
        {
            mDecoder = YoloOutputDecoder::DirectDetections;
            LOGI("AlgorithmOnYolo output dims=[%lld,%lld,%lld] decoder=direct_detections provider=%s",
                 static_cast<long long>(mOutputDims[0]),
                 static_cast<long long>(mOutputDims[1]),
                 static_cast<long long>(mOutputDims[2]),
                 mActiveProvider.c_str());
        }
        else
        {
            mDecoder = YoloOutputDecoder::DenseWithNms;
            LOGI("AlgorithmOnYolo output dims=[%lld,%lld,%lld] decoder=dense_with_nms provider=%s",
                 static_cast<long long>(mOutputDims[0]),
                 static_cast<long long>(mOutputDims[1]),
                 static_cast<long long>(mOutputDims[2]),
                 mActiveProvider.c_str());
        }
    }
    else
    {
        mOutputDims.clear();
        mOutputDim = 0;
        mOutputRow = 0;
        LOGE("AlgorithmOnYolo invalid output tensor dims size=%zu", output_dims.size());
    }
    }

    OnnxRuntimeEngine::~OnnxRuntimeEngine()
    {

        mSessionOptions.release();
        mSession.release();
        mEnv.release();
    }

    void OnnxRuntimeEngine::initPostprocessProfile(const std::string &algorithmCode)
    {
        mDecoder = YoloOutputDecoder::DenseWithNms;
        if (algorithmCode == "on_yolo26n_80" || algorithmCode == "ov_yolo26n_80")
        {
            mDecoder = YoloOutputDecoder::DirectDetections;
            LOGI("AlgorithmOnYolo profile=%s decoder=direct_detections preprocess=direct_resize_bgr score=0.25", algorithmCode.data());
            return;
        }
        LOGI("AlgorithmOnYolo profile=%s decoder=dense_with_nms preprocess=square_rgb score=0.50 nms=0.50", algorithmCode.data());
    }

    bool OnnxRuntimeEngine::decodeDenseOutputWithNms(const float *pdata, int imageWidth, int imageHeight, int paddedImageSize, std::vector<DetectObject> &detects)
    {
        float score_threshold = 0.5;
        float nms_threshold = 0.5;
        float x_factor = static_cast<float>(paddedImageSize) / static_cast<float>(mInputWidth);
        float y_factor = static_cast<float>(paddedImageSize) / static_cast<float>(mInputHeight);
        cv::Mat dout(mOutputDim, mOutputRow, CV_32F, (float *)pdata);
        cv::Mat det_output = dout.t();
        if (det_output.cols <= 4)
        {
            LOGE("AlgorithmOnYolo invalid dense det_output cols=%d", det_output.cols);
            return false;
        }

        int class_end = det_output.cols;
        if (!mClassNames.empty() && 4 + static_cast<int>(mClassNames.size()) < class_end)
        {
            class_end = 4 + static_cast<int>(mClassNames.size());
        }

        // post-process
        std::vector<cv::Rect> boxes;
        std::vector<int> classIds;
        std::vector<float> confidences;

        for (int i = 0; i < det_output.rows; i++)
        {
            cv::Mat classes_scores = det_output.row(i).colRange(4, class_end);
            cv::Point classIdPoint;
            double score;
            minMaxLoc(classes_scores, 0, &score, 0, &classIdPoint);

            if (score > score_threshold)
            {
                float cx = det_output.at<float>(i, 0);
                float cy = det_output.at<float>(i, 1);
                float ow = det_output.at<float>(i, 2);
                float oh = det_output.at<float>(i, 3);
                if (!std::isfinite(cx) || !std::isfinite(cy) || !std::isfinite(ow) || !std::isfinite(oh) || ow <= 0.0f || oh <= 0.0f)
                {
                    continue;
                }
                int left = static_cast<int>((cx - 0.5f * ow) * x_factor);
                int top = static_cast<int>((cy - 0.5f * oh) * y_factor);
                int right = static_cast<int>((cx + 0.5f * ow) * x_factor);
                int bottom = static_cast<int>((cy + 0.5f * oh) * y_factor);

                left = std::max(0, std::min(left, imageWidth - 1));
                top = std::max(0, std::min(top, imageHeight - 1));
                right = std::max(0, std::min(right, imageWidth));
                bottom = std::max(0, std::min(bottom, imageHeight));
                if (right <= left || bottom <= top)
                {
                    continue;
                }

                cv::Rect box;
                box.x = left;
                box.y = top;
                box.width = right - left;
                box.height = bottom - top;

                boxes.push_back(box);
                classIds.push_back(classIdPoint.x);
                confidences.push_back(score);
            }
        }

        // NMS
        std::vector<int> indexes;
        cv::dnn::NMSBoxes(boxes, confidences, score_threshold, nms_threshold, indexes);
        for (size_t i = 0; i < indexes.size(); i++)
        {

            int index = indexes[i];
            int class_id = classIds[index];
            if (class_id < 0 || class_id >= static_cast<int>(mClassNames.size()))
            {
                continue;
            }
            float class_score = confidences[index];
            cv::Rect box = boxes[index];

            DetectObject detect;
            detect.x1 = box.x;
            detect.y1 = box.y;
            detect.x2 = box.x + box.width;
            detect.y2 = box.y + box.height;
            detect.class_id = class_id;
            detect.class_name = mClassNames[class_id];
            detect.class_score = class_score;

            detects.push_back(detect);
        }

        return true;
    }

    bool OnnxRuntimeEngine::decodeDirectDetections(const float *pdata, int imageWidth, int imageHeight, std::vector<DetectObject> &detects)
    {
        if (mOutputDims.size() < 3)
        {
            LOGE("AlgorithmOnYolo invalid direct output dims size=%zu", mOutputDims.size());
            return false;
        }
        if (mOutputDims.back() != 6)
        {
            LOGE("AlgorithmOnYolo invalid direct output last dim=%lld, expect 6", static_cast<long long>(mOutputDims.back()));
            return false;
        }

        const int64_t detectionCount64 = mOutputDims[mOutputDims.size() - 2];
        if (detectionCount64 <= 0)
        {
            return true;
        }

        const int numDetections = static_cast<int>(detectionCount64);
        const float scaleX = static_cast<float>(imageWidth) / static_cast<float>(mInputWidth);
        const float scaleY = static_cast<float>(imageHeight) / static_cast<float>(mInputHeight);
        const float scoreThreshold = 0.25f;

        for (int i = 0; i < numDetections; ++i)
        {
            const int base = i * 6;
            const float x1 = pdata[base + 0];
            const float y1 = pdata[base + 1];
            const float x2 = pdata[base + 2];
            const float y2 = pdata[base + 3];
            const float score = pdata[base + 4];
            const int class_id = static_cast<int>(pdata[base + 5]);

            if (!std::isfinite(x1) || !std::isfinite(y1) || !std::isfinite(x2) || !std::isfinite(y2) || !std::isfinite(score))
            {
                continue;
            }
            if (score < scoreThreshold || class_id < 0 || class_id >= static_cast<int>(mClassNames.size()))
            {
                continue;
            }

            int left = static_cast<int>(x1 * scaleX);
            int top = static_cast<int>(y1 * scaleY);
            int right = static_cast<int>(x2 * scaleX);
            int bottom = static_cast<int>(y2 * scaleY);

            left = std::max(0, std::min(left, imageWidth - 1));
            top = std::max(0, std::min(top, imageHeight - 1));
            right = std::max(0, std::min(right, imageWidth));
            bottom = std::max(0, std::min(bottom, imageHeight));
            if (right <= left || bottom <= top)
            {
                continue;
            }

            DetectObject detect;
            detect.x1 = left;
            detect.y1 = top;
            detect.x2 = right;
            detect.y2 = bottom;
            detect.class_id = class_id;
            detect.class_name = mClassNames[class_id];
            detect.class_score = score;
            detects.push_back(detect);
        }

        return true;
    }

    bool OnnxRuntimeEngine::runInference(cv::Mat &image, std::vector<DetectObject> &detects)
    {
        detects.clear();
        int image_w = image.cols;
        int image_h = image.rows;
        if (image_w <= 0 || image_h <= 0 || mInputWidth <= 0 || mInputHeight <= 0 || mOutputDim <= 0 || mOutputRow <= 0)
        {
            LOGE("AlgorithmOnYolo invalid inference dims image=%dx%d input=%dx%d output=%dx%d", image_w, image_h, mInputWidth, mInputHeight, mOutputDim, mOutputRow);
            return false;
        }

        cv::Mat inputImage;
        int paddedImageSize = std::max(image_h, image_w);
        if (mDecoder == YoloOutputDecoder::DirectDetections)
        {
            inputImage = image;
        }
        else
        {
            inputImage = cv::Mat::zeros(cv::Size(paddedImageSize, paddedImageSize), CV_8UC3);
            cv::Rect roi(0, 0, image_w, image_h);
            image.copyTo(inputImage(roi));
        }

        const bool swapRB = (mDecoder == YoloOutputDecoder::DenseWithNms);
        cv::Mat blob = cv::dnn::blobFromImage(inputImage, 1 / 255.0, cv::Size(mInputWidth, mInputHeight), cv::Scalar(0, 0, 0), swapRB, false);
        size_t tpixels = static_cast<size_t>(mInputHeight * mInputWidth * 3);
        std::array<int64_t, 4> input_shape_info{1, 3, mInputHeight, mInputWidth};

        auto allocator_info = Ort::MemoryInfo::CreateCpu(OrtDeviceAllocator, OrtMemTypeCPU);
        Ort::Value input_tensor_ = Ort::Value::CreateTensor<float>(allocator_info, blob.ptr<float>(), tpixels, input_shape_info.data(), input_shape_info.size());
        const std::array<const char *, 1> inputNames = {mInputNodeName.c_str()};
        const std::array<const char *, 1> outNames = {mOutputNodeName.c_str()};

        std::vector<Ort::Value> ort_outputs = mSession.Run(Ort::RunOptions{nullptr}, inputNames.data(), &input_tensor_, 1, outNames.data(), outNames.size());
        if (ort_outputs.empty())
        {
            LOGE("AlgorithmOnYolo empty ort outputs");
            return false;
        }

        const float *pdata = ort_outputs[0].GetTensorMutableData<float>();
        if (!pdata)
        {
            LOGE("AlgorithmOnYolo null output tensor data");
            return false;
        }

        if (mDecoder == YoloOutputDecoder::DirectDetections)
        {
            return decodeDirectDetections(pdata, image_w, image_h, detects);
        }
        return decodeDenseOutputWithNms(pdata, image_w, image_h, paddedImageSize, detects);
    }

    AlgorithmOnYolo::AlgorithmOnYolo(Config *config, std::string &modelPath, std::vector<std::string> &classNames, const std::string &algorithmCode) : Algorithm(config),
                                                                                                                     mClassNames(classNames)
    {
        mEngine = new OnnxRuntimeEngine(config, modelPath, classNames, algorithmCode);
    }

    AlgorithmOnYolo::~AlgorithmOnYolo()
    {
        LOGI("");
        delete mEngine;
        mEngine = nullptr;
    }

    bool AlgorithmOnYolo::objectDetect(cv::Mat &image, std::vector<DetectObject> &detects)
    {
        return mEngine->runInference(image, detects);
    }

}