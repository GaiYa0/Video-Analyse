#ifndef ANALYZER_ANALYZER_H
#define ANALYZER_ANALYZER_H

#include <string>
#include <vector>
#include <opencv2/opencv.hpp>
#include <iostream>
#include <filesystem>
#include "BehaviorEvaluator.h"
namespace SVAAnalyzer
{
	struct Control;
	struct AlgorithmTask;
	class Config;
	class Scheduler;
	class Algorithm;
	struct DetectObject;

	class Analyzer
	{
	public:
		explicit Analyzer(Scheduler *scheduler, Control *control);
		~Analyzer();

	public:
		bool handleVideoFrame(int64_t frameCount, cv::Mat &image, std::vector<DetectObject> &happenDetects, bool &happen, float &happenScore, bool isKeyframe = false);

	private:
		bool runAlgorithmTask(int64_t frameCount,
						 AlgorithmTask &task,
						 cv::Mat &image,
						 std::vector<DetectObject> &taskDetects,
						 bool &taskHappen,
						 float &taskHappenScore,
						 bool isKeyframe);
		Algorithm *resolveAlgorithm(const std::string &algorithmCode);
		void applyRegionAndObjectMatch(const AlgorithmTask &task,
								 std::vector<DetectObject> &detects,
								 bool &happen,
								 float &happenScore);

	private:
		bool postImage2Server(int64_t frameCount, cv::Mat &image, std::vector<DetectObject> &happenDetects, bool &happen, float &happenScore);

	private:
		Scheduler *mScheduler;
		Control *mControl;

	private:
	};
}
#endif // ANALYZER_ANALYZER_H
