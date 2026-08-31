#ifndef ANALYZER_SCHEDULER_H
#define ANALYZER_SCHEDULER_H
#include <map>
#include <mutex>
#include <condition_variable>
#include <queue>
#include <vector>
#include <thread>
#include <string>
#include <atomic>
#include <unordered_map>
#include <unordered_set>
#include <opencv2/opencv.hpp>
#include "TrackMetadata.h"
#include "TemporalContext.h"

namespace SVAAnalyzer
{
	class Config;
	class Worker;
	class Algorithm;
	struct Control;
	struct AlarmImage;
	struct Alarm;
	
	/**
	 * @brief Enriched detection object with tracking and behavior analysis metadata.
	 * 
	 * Teaching note: In the SVA worker architecture, each detected object carries:
	 * - Basic detection info (box, class, score)
	 * - Temporal tracking info (trackId, trail, speed, direction)
	 * - Region state (enter/exit/dwell per region)
	 * - Behavior analysis result (ruleId, behaviorType)
	 */
	struct DetectFrameObject
	{
		// Basic detection
		int x1 = 0;
		int y1 = 0;
		int x2 = 0;
		int y2 = 0;
		float score = 0.0f;
		int classId = -1;
		std::string className;
		std::string algorithmCode;
		bool happen = false;
		
		// Temporal tracking fields (populated by TemporalProcessor)
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
		
		// Behavior analysis fields (populated by BehaviorEvaluator)
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

	struct AggregateBehaviorMatch
	{
		std::string ruleId;
		std::string customEventName;
		std::string behaviorType;
		std::string regionId;
		std::string regionName;
		std::string eventClass;
		int trackId = -1;
		int relationTargetTrackId = -1;
		std::string relationTargetClassName;
		double relationDistancePx = -1.0;
		std::string sequenceId;
		int sequenceStageIndex = -1;
		int sequenceStageCount = 0;
		std::string sequenceLogicMode = "all";
		int objectCount = 0;
		int thresholdCount = 0;
		float score = 0.0f;
		std::vector<std::string> algorithmCodes;
	};

	struct DetectFrameEvent
	{
		std::string nodeCode;
		std::string controlCode;
		std::string streamCode;
		std::string streamApp;
		std::string streamName;
		std::string primaryAlgorithmCode;
		std::string imagePath;
		std::string renderMode;
		std::string keyMode = "control";
		std::string ruleMode = "any";
		std::vector<std::string> requiredAlgorithms;
		int ruleMinHits = 1;
		int ruleHitWindowMs = 1000;
		int pendingTimeoutMs = 3000;
		int restartCooldownMs = 0;
		int updateIntervalMs = 1000;
		int endTimeoutMs = 2000;
		int debounceWindowMs = 180;
		int postRetryMax = 2;
		int postFailOpenThreshold = 8;
		int postCooldownMs = 1000;
		int alarmIntervalMs = 180000;
		bool saveImageEnabled = true;
		bool saveVideoEnabled = true;
		int64_t timestampMs = 0;
		int64_t frameSeq = 0;
		int width = 0;
		int height = 0;
		float checkFps = 0.0f;
		float inferFps = 0.0f;
		bool happen = false;
		float happenScore = 0.0f;
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
		std::vector<DetectFrameObject> objects;
		std::vector<AggregateBehaviorMatch> aggregateBehaviors;
	};

	struct DetectLifecycleState
	{
		bool active = false;
		std::string eventId;
		std::string eventClass;
		std::string controlCode;
		std::string streamCode;
		std::string streamApp;
		std::string streamName;
		std::string primaryAlgorithmCode;
		std::string imagePath;
		std::string videoPath;
		std::string nodeCode;
		std::string renderMode;
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
		int64_t startTimestampMs = 0;
		int64_t lastSeenTimestampMs = 0;
		int64_t lastUpdateSentTimestampMs = 0;
		int updateIntervalMs = 1000;
		int endTimeoutMs = 2000;
		int postRetryMax = 2;
		int postFailOpenThreshold = 8;
		int postCooldownMs = 1000;
		int alarmIntervalMs = 180000;
		int64_t lastAlarmNotifyTimestampMs = 0;
		std::string ruleMode = "any";
		std::vector<std::string> requiredAlgorithms;
		int ruleMinHits = 1;
		int ruleHitWindowMs = 1000;
		int pendingTimeoutMs = 3000;
		int restartCooldownMs = 0;
		int pendingHitCount = 0;
		int startHitCount = 0;
		int64_t pendingWindowStartMs = 0;
		float maxScore = 0.0f;
		std::vector<std::string> algorithmCodes;
		std::unordered_set<std::string> algorithmCodeSet;
	};

	struct DetectPostCircuitState
	{
		int64_t consecutiveFailed = 0;
		int64_t cooldownUntilMs = 0;
	};

	struct AlarmMediaBinding
	{
		std::string alarmId;
		std::string eventId;
		std::string behaviorType;
		std::string ruleId;
		std::string videoPath;
		std::string imagePath;
		int64_t boundTimestampMs = 0;
	};

	struct AggregateBehaviorRuntimeState
	{
		bool conditionActive = false;
		int64_t activeSinceTimestampMs = 0;
		int64_t lastUpdatedTimestampMs = 0;
		int objectCount = 0;
		float maxScore = 0.0f;
		std::unordered_set<std::string> algorithmCodeSet;
	};

	struct RegionMotionRuntimeState
	{
		cv::Mat prevGray;
		bool running = false;
		int onCount = 0;
		int offCount = 0;
		double lastScore = 0.0;
		int64_t lastFrameTimestampMs = 0;
		int64_t lastUpdatedTimestampMs = 0;
	};

	struct RelationalBehaviorRuntimeState
	{
		bool conditionActive = false;
		int64_t activeSinceTimestampMs = 0;
		int64_t lastUpdatedTimestampMs = 0;
		double lastDistancePx = -1.0;
		int relationTargetTrackId = -1;
	};

	struct SequenceBehaviorRuntimeState
	{
		int completedStageIndex = -1;
		int currentStageIndex = -1;
		int stageCount = 0;
		int64_t stageStartTimestampMs = 0;
		int64_t lastMatchedTimestampMs = 0;
		int64_t lastCompletedTimestampMs = 0;
		std::unordered_set<std::string> matchedRuleIds;
	};

	class Scheduler
	{
	public:
		friend class Worker;

		Scheduler(Config *config);
		~Scheduler();

	public:
		Config *getConfig();

		bool initAlgorithm();
		std::mutex mAlgorithmMtx;
		/**
		 * Teaching note: In easySVA-server we use pure ONNX Runtime for all models.
		 */
		Algorithm *on_yolo11n_80 = nullptr;
		Algorithm *on_yolo26n_80 = nullptr;
		void loop();

		void setState(bool state);
		bool getState();

		void addAlarm(Alarm *alarm);
		void bindAlarmMedia(const std::string &controlCode,
							const std::string &alarmId,
							const std::string &eventId,
							const std::string &behaviorType,
							const std::string &ruleId,
							const std::string &videoPath = "",
							const std::string &imagePath = "");
		void addDetectFrameEvent(DetectFrameEvent *event);
		
		/**
		 * @brief Update temporal tracking state for a stream.
		 * Called from Worker after inference to enrich detections with track IDs and trails.
		 */
		void updateTemporalTracks(const Control &control,
								  const std::string &streamCode,
								  std::vector<DetectObject *> detects,
								  int64_t timestampMs);
		
		/**
		 * @brief Evaluate aggregate behavior rules (count, occupancy, absence).
		 */
		void evaluateAggregateBehaviorRules(const Control &control,
									 const std::string &streamCode,
									 const std::vector<DetectObject> &detects,
									 int64_t timestampMs,
									 std::vector<AggregateBehaviorMatch> &matches);

		/**
		 * @brief Evaluate relation behavior rules (near, apart, not_contains, fight).
		 */
		void evaluateRelationalBehaviorRules(const Control &control,
									  const std::string &streamCode,
									  const std::vector<DetectObject> &detects,
									  int64_t timestampMs,
									  std::vector<AggregateBehaviorMatch> &matches);

		/**
		 * @brief Apply multi-stage sequence state machine to atomic and relation matches.
		 */
		void applySequenceBehaviorRules(const Control &control,
									 const std::string &streamCode,
									 std::vector<DetectObject> &detects,
									 std::vector<AggregateBehaviorMatch> &matches,
									 int64_t timestampMs);
		
		void getDetectFrameStats(int &queueSize,
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
							 int64_t &postCooldownMaxRemainMs);

		// ApiServer 对应的函数 start
		int apiControls(std::vector<Control *> &controls);
		Control *apiControl(std::string &code);
		void apiControlAdd(Control *control, int &result_code, std::string &result_msg);
		void apiControlCancel(Control *control, int &result_code, std::string &result_msg);
		// ApiServer 对应的函数 end

	private:
		Config *mConfig;
		bool mState;

		std::map<std::string, Worker *> mWorkerMap; // <control.code, Worker*>: API lookup by control.
		std::map<std::string, Worker *> mStreamWorkerMap; // <stream.code/url, Worker*>: share one Worker per stream.
		std::mutex mWorkerMapMtx;
		int getWorkerSize();
		bool isAdd(Control *control);
		bool addWorker(Control *control, Worker *worker);
		bool removeWorker(Control *control); // 加入到待实际删除队列
		Worker *getWorker(Control *control);

		std::queue<Worker *> mTobeDeletedWorkerQ;
		std::mutex mTobeDeletedWorkerQ_mtx;
		std::condition_variable mTobeDeletedWorkerQ_cv;
		void handleDeleteWorker();

		// 报警处理 start
		std::thread *mLoopAlarmThread;
		static void loopAlarmThread(void *arg);
		void handleLoopAlarm();
		std::queue<Alarm *> mAlarmQ;
		std::mutex mAlarmQ_mtx;
		std::condition_variable mAlarmQ_cv;
		std::unordered_map<std::string, AlarmMediaBinding> mAlarmMediaBindingMap;
		void attachAlarmMediaBinding(Alarm *alarm);
		bool getAlarm(Alarm *&alarm, int &alarmQSize);
		void clearAlarmQueue();
		// 报警处理 end

		// 实时检测事件处理 start
		std::thread *mLoopDetectFrameThread;
		static void loopDetectFrameThread(void *arg);
		void handleLoopDetectFrame();
		std::queue<DetectFrameEvent *> mDetectFrameQ;
		std::mutex mDetectFrameQ_mtx;
		std::condition_variable mDetectFrameQ_cv;
		std::atomic<int64_t> mDetectFrameEnqueued{0};
		std::atomic<int64_t> mDetectFrameDropped{0};
		std::atomic<int64_t> mDetectFrameSent{0};
		std::atomic<int64_t> mDetectFramePostFailed{0};
		std::atomic<int64_t> mDetectFrameQueuePeak{0};
		std::atomic<int64_t> mDetectFrameDebounced{0};
		std::atomic<int64_t> mDetectFrameRetried{0};
		std::atomic<int64_t> mDetectEventSent{0};
		std::atomic<int64_t> mDetectEventPostFailed{0};
		std::atomic<int64_t> mDetectEventRetried{0};
		std::atomic<int64_t> mDetectPostSkipped{0};
		std::atomic<int64_t> mDetectLifecycleActive{0};
		std::atomic<int64_t> mDetectLifecyclePending{0};
		std::atomic<int64_t> mDetectLifecyclePendingPeak{0};
		std::atomic<int64_t> mDetectLifecyclePendingEvicted{0};
		std::atomic<int64_t> mDetectLifecycleStarted{0};
		std::atomic<int64_t> mDetectLifecycleEnded{0};
		std::atomic<int64_t> mDetectLifecycleBlockedByRule{0};
		std::atomic<int64_t> mDetectLifecycleBlockedByMinHits{0};
		std::atomic<int64_t> mDetectLifecycleBlockedByRestartCooldown{0};
		std::map<std::string, DetectLifecycleState> mDetectLifecycleStateMap;
		std::unordered_map<std::string, int64_t> mDetectLifecycleLastEndMap;
		std::unordered_map<std::string, std::pair<int64_t, std::string>> mDetectFrameDebounceMap;
		std::unordered_map<std::string, DetectPostCircuitState> mDetectPostCircuitMap;
		
		// Temporal context per stream (teaching: worker architecture owns one context per stream)
		std::mutex mStreamTemporalMtx;
		std::unordered_map<std::string, StreamTemporalContext> mStreamTemporalContextMap;
		
		// Behavior analysis runtime state
		std::mutex mAggregateBehaviorStateMtx;
		std::unordered_map<std::string, AggregateBehaviorRuntimeState> mAggregateBehaviorStateMap;
		std::unordered_map<std::string, RegionMotionRuntimeState> mRegionMotionStateMap;
		std::unordered_map<std::string, RelationalBehaviorRuntimeState> mRelationalBehaviorStateMap;
		std::unordered_map<std::string, SequenceBehaviorRuntimeState> mSequenceBehaviorStateMap;
		
		bool getDetectFrameEvent(DetectFrameEvent *&event, int &qSize);
		bool postDetectPayload(const std::string &payload);
		bool canTryPostPayload(const std::string &streamKey, int64_t nowMs);
		void updatePostState(const std::string &streamKey, bool ok, int failOpenThreshold, int cooldownMs, int64_t nowMs);
		bool postDetectPayloadWithRetry(const std::string &payload,
									   const std::string &streamKey,
									   int maxRetry,
									   int64_t &retryUsed,
									   int failOpenThreshold,
									   int cooldownMs,
									   bool &skippedByCooldown);
		std::string buildDetectFrameDigest(const DetectFrameEvent *event) const;
		bool shouldDebounceDetectFrame(const DetectFrameEvent *event);
		void handleDetectLifecycle(DetectFrameEvent *event);
		void flushDetectLifecycleByTimeout(int64_t nowMs);
		void sendDetectLifecycleEvent(const std::string &controlKey,
								 DetectLifecycleState &state,
								 const DetectFrameEvent &event,
								 const std::string &eventState);
		void clearDetectFrameQueue();
		void clearStreamTemporalContext(const std::string &streamCode);
		// 实时检测事件处理 end
	};
}
#endif // ANALYZER_SCHEDULER_H