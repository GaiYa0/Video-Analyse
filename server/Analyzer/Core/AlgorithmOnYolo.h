#ifndef ANALYZER_ALGORITHMONYOLO_H
#define ANALYZER_ALGORITHMONYOLO_H

#include <string>
#include <vector>
#include <mutex>
#include <queue>
#include "Algorithm.h"
#include <onnxruntime_cxx_api.h>

namespace SVAAnalyzer
{
	class Config;
	enum class YoloOutputDecoder
	{
		DenseWithNms,
		DirectDetections,
	};

	/**
	 * @brief ONNX Runtime inference engine.
	 * 
	 * Teaching note: This is the inference backend for object detection.
	 * GPU build priority: TensorRT > CUDA > CPU (auto-fallback).
	 * CPU-only build: compile with -DSVA_ONNXRUNTIME_GPU=OFF and link CPU-only ONNX Runtime.
	 * 
	 * - TensorRT: NVIDIA's inference optimizer, requires .engine cache build on first run, fastest.
	 * - CUDA: Direct CUDA execution, no engine build needed, faster than CPU.
	 * - CPU: Fallback when no GPU is available, uses optimized CPU kernels.
	 * 
	 * Configuration via config.json: pipelineGpuStrict=true skips GPU if not available.
	 */
	class OnnxRuntimeEngine
	{
	public:
		explicit OnnxRuntimeEngine(Config *config, std::string &modelPath, std::vector<std::string> &classNames, const std::string &algorithmCode);
		~OnnxRuntimeEngine();

	public:
		bool runInference(cv::Mat &image, std::vector<DetectObject> &detects);
		bool isGpuEnabled() const { return mGpuEnabled; }
		std::string getActiveProvider() const { return mActiveProvider; }

	private:
		Config *mConfig;
		std::vector<std::string> mClassNames;
		std::string mModelPath;
		std::string mAlgorithmCode;
		YoloOutputDecoder mDecoder = YoloOutputDecoder::DenseWithNms;
		std::string mInputNodeName;
		std::string mOutputNodeName;
		int mInputWidth = 0;
		int mInputHeight = 0;
		int mOutputDim = 0;
		int mOutputRow = 0;
		std::vector<int64_t> mOutputDims;
		bool mGpuEnabled = false;
		std::string mActiveProvider = "CPU";
		Ort::Env mEnv{nullptr};
		Ort::SessionOptions mSessionOptions{nullptr};
		Ort::Session mSession{nullptr};

		void initPostprocessProfile(const std::string &algorithmCode);
		bool decodeDenseOutputWithNms(const float *pdata, int imageWidth, int imageHeight, int paddedImageSize, std::vector<DetectObject> &detects);
		bool decodeDirectDetections(const float *pdata, int imageWidth, int imageHeight, std::vector<DetectObject> &detects);
	};
	class AlgorithmOnYolo : public Algorithm
	{
	public:
		AlgorithmOnYolo(Config *config, std::string &modelPath, std::vector<std::string> &classNames, const std::string &algorithmCode);
		virtual ~AlgorithmOnYolo();

	public:
		virtual bool objectDetect(cv::Mat &image, std::vector<DetectObject> &detects);

	private:
		std::vector<std::string> mClassNames;
		OnnxRuntimeEngine *mEngine;
	};
}
#endif // ANALYZER_ALGORITHMONYOLO_H
