#ifndef ANALYZER_ALGORITHM_H
#define ANALYZER_ALGORITHM_H

#include <string>
#include <vector>
#include <deque>
#include <unordered_map>
#include <cstdint>
#include <opencv2/opencv.hpp> //opencv header file
#include "TrackMetadata.h"

namespace SVAAnalyzer
{
    class Config;

    static std::vector<cv::Scalar> colors = {
        cv::Scalar(0, 0, 255),
        cv::Scalar(0, 255, 0),
        cv::Scalar(255, 0, 0),
        cv::Scalar(255, 100, 50),
        cv::Scalar(50, 100, 255),
        cv::Scalar(255, 50, 100)};

    cv::Mat static letterbox(const cv::Mat &source)
    {
        int col = source.cols;
        int row = source.rows;
        int _max = MAX(col, row);
        cv::Mat result = cv::Mat::zeros(_max, _max, CV_8UC3);
        source.copyTo(result(cv::Rect(0, 0, col, row)));
        return result;
    };

    /**
     * @brief Unified detection object with temporal tracking and behavior fields.
     * 
     * Teaching note: This struct is the data carrier through the worker pipeline:
     * 1. Algorithm output   → x1..class_name, class_score
     * 2. TemporalProcessor  → trackId, trail, speed, direction, regionStates
     * 3. BehaviorEvaluator  → ruleId, behaviorType, regionId, lineId
     * 4. Worker             → builds DetectFrameEvent with all fields
     */
    struct DetectObject
    {
        // Basic detection (from Algorithm)
        int x1 = 0;
        int y1 = 0;
        int x2 = 0;
        int y2 = 0;
        float class_score = 0.0f;
        int class_id = -1;
        std::string class_name;
        std::string source_algorithm;
        bool happen = false;

        // Temporal tracking (from TemporalProcessor)
        int trackId = -1;
        int64_t firstSeenTimestampMs = 0;
        int64_t lastSeenTimestampMs = 0;
        int64_t dwellMs = 0;
        int trackAgeFrames = 0;
        int trackMissedFrames = 0;
        float speedPxPerSec = 0.0f;
        float velocityXPxPerSec = 0.0f;
        float velocityYPxPerSec = 0.0f;
        float directionAngleDeg = 0.0f;
        std::string motionState;
        bool trackNew = false;
        std::vector<TrackTrailPoint> trail;
        std::unordered_map<std::string, RegionTemporalState> regionStates;

        // Behavior analysis (from BehaviorEvaluator)
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
        int relationTargetTrackId = -1;
        std::string relationTargetClassName;
        double relationDistancePx = -1.0;
    };

    class Algorithm
    {
    public:
        Algorithm() = delete;
        Algorithm(Config *config);
        virtual ~Algorithm();

    public:
        virtual bool objectDetect(cv::Mat &image, std::vector<DetectObject> &detects) = 0;
        bool createState();

    protected:
        Config *mConfig;
        bool mCreateState = false; // 创建状态，默认false
    };

}
#endif // ANALYZER_ALGORITHM_H
