#ifndef ANALYZER_TEMPORALCONTEXT_H
#define ANALYZER_TEMPORALCONTEXT_H

#include "TrackMetadata.h"

#include <array>
#include <cstdint>
#include <deque>
#include <string>
#include <unordered_map>
#include <vector>

namespace SVAAnalyzer
{
    struct Control;
    struct DetectObject;

    enum class TemporalTrackLifecycleState
    {
        New = 0,
        Tracked = 1,
        Lost = 2,
        Removed = 3,
    };

    /**
     * @brief Per-track temporal state maintained by TemporalProcessor.
     * 
     * In a teaching-oriented worker architecture, each stream gets one StreamTemporalContext.
     * The TemporalProcessor implements a simple IoU-based greedy tracker (no Kalman math needed
     * to understand the concept), which is sufficient for demo/learning.
     */
    struct TemporalTrackState
    {
        int trackId = -1;
        int x1 = 0;
        int y1 = 0;
        int x2 = 0;
        int y2 = 0;
        float score = 0.0f;
        int classId = -1;
        std::string className;
        std::string algorithmCode;
        int64_t firstSeenTimestampMs = 0;
        int64_t lastSeenTimestampMs = 0;
        int64_t lastMovedTimestampMs = 0;
        int ageFrames = 0;
        int missedFrames = 0;
        int consecutiveVisibleFrames = 0;
        float speedPxPerSec = 0.0f;
        float velocityXPxPerSec = 0.0f;
        float velocityYPxPerSec = 0.0f;
        float directionAngleDeg = 0.0f;
        std::string motionState = "unknown";
        std::unordered_map<std::string, RegionTemporalState> regionStates;
        TemporalTrackLifecycleState lifeState = TemporalTrackLifecycleState::New;
        std::deque<TrackTrailPoint> trail;
    };

    /**
     * @brief Per-stream temporal context holding all active tracks.
     * 
     * In easySVA-server the Scheduler owns one StreamTemporalContext per streamCode.
     */
    struct StreamTemporalContext
    {
        std::string streamCode;
        int nextTrackId = 1;
        int64_t lastFrameTimestampMs = 0;
        std::unordered_map<int, TemporalTrackState> activeTracks;
    };

    /**
     * @brief Simple IoU-based temporal processor.
     * 
     * Public interface: updateStream() assigns/updates track IDs and trail data 
     * for a vector of DetectObject* from one frame.
     */
    class TemporalProcessor
    {
    public:
        static void updateStream(StreamTemporalContext &context,
                                 const Control &control,
                                 std::vector<DetectObject *> &detects,
                                 int64_t timestampMs);
    };
}

#endif // ANALYZER_TEMPORALCONTEXT_H