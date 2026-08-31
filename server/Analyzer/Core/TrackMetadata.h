#ifndef ANALYZER_TRACKMETADATA_H
#define ANALYZER_TRACKMETADATA_H

#include <cstdint>
#include <string>
#include <unordered_map>

namespace SVAAnalyzer
{
    struct TrackTrailPoint
    {
        int x = 0;
        int y = 0;
        float speedPxPerSec = 0.0f;
        int64_t timestampMs = 0;
    };

    struct RegionTemporalState
    {
        bool inRegion = false;
        bool enteredRegion = false;
        bool exitedRegion = false;
        int64_t inRegionDurationMs = 0;
        int64_t lastEnterTimestampMs = 0;
        int64_t lastLeaveTimestampMs = 0;
    };
}

#endif // ANALYZER_TRACKMETADATA_H