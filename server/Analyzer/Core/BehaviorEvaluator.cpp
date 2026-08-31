#include "BehaviorEvaluator.h"

#include "Algorithm.h"
#include "Control.h"
#include "Utils/GeometryUtils.h"

#include <algorithm>
#include <cmath>
#include <limits>
#include <vector>

namespace SVAAnalyzer
{
    namespace
    {
        bool hasLineCrossingBehavior(const Control &control)
        {
            for (size_t i = 0; i < control.lines.size(); ++i)
            {
                if (control.lines[i].points.size() >= 2)
                {
                    return true;
                }
            }
            return false;
        }

        bool hasEnabledBehaviorRules(const Control &control)
        {
            for (size_t i = 0; i < control.behaviorRules.size(); ++i)
            {
                if (control.behaviorRules[i].enabled)
                {
                    return true;
                }
            }
            return false;
        }

        /**
         * @brief Normalize an angle to [0, 360).
         */
        double normalizeAngleDeg(double angleDeg)
        {
            while (angleDeg < 0.0)
            {
                angleDeg += 360.0;
            }
            while (angleDeg >= 360.0)
            {
                angleDeg -= 360.0;
            }
            return angleDeg;
        }

        /**
         * @brief Compute the minimum angular distance between two angles.
         */
        double computeAngleDeltaDeg(double lhs, double rhs)
        {
            const double delta = std::fabs(normalizeAngleDeg(lhs) - normalizeAngleDeg(rhs));
            return std::min(delta, 360.0 - delta);
        }

        /**
         * @brief Try to resolve the movement direction angle from the detect object.
         * Teaching note: Uses trail points (center-of-box history) to compute movement vector.
         */
        bool tryResolveMovementAngleDeg(const DetectObject &detect, double &angleDeg)
        {
            angleDeg = 0.0;
            if (detect.trail.size() >= 2)
            {
                const TrackTrailPoint &prev = detect.trail[detect.trail.size() - 2];
                const TrackTrailPoint &curr = detect.trail[detect.trail.size() - 1];
                const double dx = static_cast<double>(curr.x - prev.x);
                const double dy = static_cast<double>(curr.y - prev.y);
                if (std::fabs(dx) > 1e-3 || std::fabs(dy) > 1e-3)
                {
                    angleDeg = normalizeAngleDeg(std::atan2(dy, dx) * 180.0 / M_PI);
                    return true;
                }
            }

            if (detect.speedPxPerSec > 0.1f)
            {
                angleDeg = normalizeAngleDeg(static_cast<double>(detect.directionAngleDeg));
                return true;
            }
            return false;
        }

        /**
         * @brief Resolve region info (id, name) for a behavior rule.
         * Falls back to the primary region if no specific geometryId is given.
         */
        bool resolveBehaviorRegionInfo(const Control &control,
                                       const std::string &requestedRegionId,
                                       std::string &regionId,
                                       std::string &regionName)
        {
            const RegionConfig *region = requestedRegionId.empty()
                                            ? control.findPrimaryRegion()
                                            : control.findRegionById(requestedRegionId);
            if (!region)
            {
                return false;
            }
            regionId = region->id;
            regionName = region->name.empty() ? region->id : region->name;
            return true;
        }

        /**
         * @brief Find the temporal region state for a specific region from the detect's state map.
         */
        const RegionTemporalState *findRegionState(const DetectObject &detect, const std::string &regionId)
        {
            if (regionId.empty())
            {
                return nullptr;
            }
            auto it = detect.regionStates.find(regionId);
            if (it == detect.regionStates.end())
            {
                return nullptr;
            }
            return &it->second;
        }

        /**
         * @brief Check dwell hit: tracked object has been in region >= thresholdMs.
         */
        bool isDwellHit(int64_t thresholdMs, const DetectObject &detect, const RegionTemporalState *regionState)
        {
            if (detect.trackId < 0 || !regionState)
            {
                return false;
            }
            return regionState->inRegionDurationMs >= std::max<int64_t>(1, thresholdMs);
        }

        /**
         * @brief Check low_speed hit: tracked object moving slower than maxSpeedPxPerSec for thresholdMs.
         */
        bool isLowSpeedHit(double maxSpeedPxPerSec,
                           int64_t thresholdMs,
                           const DetectObject &detect,
                           const RegionTemporalState *regionState)
        {
            if (detect.trackId < 0 || !regionState || !regionState->inRegion)
            {
                return false;
            }
            if (maxSpeedPxPerSec <= 0.0 || thresholdMs <= 0)
            {
                return false;
            }
            if (regionState->inRegionDurationMs < thresholdMs)
            {
                return false;
            }
            if (detect.trail.size() < 2 || detect.lastSeenTimestampMs <= 0)
            {
                return false;
            }

            const int64_t windowStartMs = detect.lastSeenTimestampMs - thresholdMs;
            size_t firstIndex = detect.trail.size();
            for (size_t i = 0; i < detect.trail.size(); ++i)
            {
                if (detect.trail[i].timestampMs >= windowStartMs)
                {
                    firstIndex = i;
                    break;
                }
            }
            if (firstIndex == detect.trail.size())
            {
                return false;
            }
            if (firstIndex > 0)
            {
                --firstIndex;
            }

            double travelledDistance = 0.0;
            const int64_t beginTs = std::max<int64_t>(windowStartMs, detect.trail[firstIndex].timestampMs);
            int64_t endTs = beginTs;
            for (size_t i = firstIndex + 1; i < detect.trail.size(); ++i)
            {
                const TrackTrailPoint &prev = detect.trail[i - 1];
                const TrackTrailPoint &curr = detect.trail[i];
                if (curr.timestampMs < windowStartMs)
                {
                    continue;
                }

                const double dx = static_cast<double>(curr.x - prev.x);
                const double dy = static_cast<double>(curr.y - prev.y);
                travelledDistance += std::sqrt(dx * dx + dy * dy);
                endTs = curr.timestampMs;
            }

            const int64_t elapsedMs = endTs - beginTs;
            if (elapsedMs < std::max<int64_t>(200, thresholdMs / 2))
            {
                return false;
            }

            const double averageSpeed = travelledDistance * 1000.0 / static_cast<double>(elapsedMs);
            return averageSpeed <= maxSpeedPxPerSec;
        }

        /**
         * @brief Check loitering hit: tracked object stays within maxDisplacementPx for thresholdMs.
         */
        bool isLoiteringHit(double maxDisplacementPx,
                            int64_t thresholdMs,
                            const DetectObject &detect,
                            const RegionTemporalState *regionState)
        {
            if (detect.trackId < 0 || !regionState || !regionState->inRegion)
            {
                return false;
            }
            if (maxDisplacementPx <= 0.0 || thresholdMs <= 0)
            {
                return false;
            }
            if (regionState->inRegionDurationMs < thresholdMs)
            {
                return false;
            }
            if (detect.trail.size() < 2 || detect.lastSeenTimestampMs <= 0)
            {
                return false;
            }

            const int64_t windowStartMs = detect.lastSeenTimestampMs - thresholdMs;
            size_t firstIndex = detect.trail.size();
            for (size_t i = 0; i < detect.trail.size(); ++i)
            {
                if (detect.trail[i].timestampMs >= windowStartMs)
                {
                    firstIndex = i;
                    break;
                }
            }
            if (firstIndex == detect.trail.size())
            {
                return false;
            }

            const TrackTrailPoint &anchor = detect.trail[firstIndex];
            double maxDistance = 0.0;
            int pointCount = 0;
            for (size_t i = firstIndex; i < detect.trail.size(); ++i)
            {
                const TrackTrailPoint &point = detect.trail[i];
                if (point.timestampMs < windowStartMs)
                {
                    continue;
                }
                const double dx = static_cast<double>(point.x - anchor.x);
                const double dy = static_cast<double>(point.y - anchor.y);
                maxDistance = std::max(maxDistance, std::sqrt(dx * dx + dy * dy));
                ++pointCount;
            }

            if (pointCount < 2)
            {
                return false;
            }
            return maxDistance <= maxDisplacementPx;
        }

        /**
         * @brief Check sleep hit: object stationary + wide aspect ratio (person lying down).
         */
        bool isSleepHit(const BehaviorRuleConfig &rule,
                        const DetectObject &detect,
                        const RegionTemporalState *regionState)
        {
            if (detect.trackId < 0)
            {
                return false;
            }
            if (regionState && !regionState->inRegion)
            {
                return false;
            }

            const int64_t thresholdMs = std::max<int64_t>(1000, rule.thresholdMs > 0 ? rule.thresholdMs : 15000);
            const int64_t activeDurationMs = regionState ? regionState->inRegionDurationMs : detect.dwellMs;
            if (activeDurationMs < thresholdMs)
            {
                return false;
            }

            const double maxSpeedPxPerSec = rule.maxSpeedPxPerSec > 0.0 ? rule.maxSpeedPxPerSec : 6.0;
            if (detect.speedPxPerSec > maxSpeedPxPerSec)
            {
                return false;
            }
            if (detect.motionState == "moving")
            {
                return false;
            }

            // Wide aspect ratio check: a lying person is wider than tall
            const double width = std::max(1, detect.x2 - detect.x1);
            const double height = std::max(1, detect.y2 - detect.y1);
            const double minAspectRatio = rule.distanceThresholdPx > 0.0 ? rule.distanceThresholdPx : 1.2;
            if ((width / height) < minAspectRatio)
            {
                return false;
            }

            if (rule.maxDisplacementPx > 0.0 && detect.trail.size() >= 2)
            {
                const int64_t windowStartMs = detect.lastSeenTimestampMs - thresholdMs;
                size_t firstIndex = detect.trail.size();
                for (size_t i = 0; i < detect.trail.size(); ++i)
                {
                    if (detect.trail[i].timestampMs >= windowStartMs)
                    {
                        firstIndex = i;
                        break;
                    }
                }
                if (firstIndex == detect.trail.size())
                {
                    return false;
                }

                const TrackTrailPoint &anchor = detect.trail[firstIndex];
                double maxDistance = 0.0;
                for (size_t i = firstIndex; i < detect.trail.size(); ++i)
                {
                    const TrackTrailPoint &point = detect.trail[i];
                    if (point.timestampMs < windowStartMs)
                    {
                        continue;
                    }
                    const double dx = static_cast<double>(point.x - anchor.x);
                    const double dy = static_cast<double>(point.y - anchor.y);
                    maxDistance = std::max(maxDistance, std::sqrt(dx * dx + dy * dy));
                }
                if (maxDistance > rule.maxDisplacementPx)
                {
                    return false;
                }
            }

            return true;
        }

        /**
         * @brief Check direction_move / direction_reverse hit.
         */
        bool isDirectionRuleHit(const BehaviorRuleConfig &rule,
                                const DetectObject &detect,
                                const RegionTemporalState *regionState,
                                double &matchedAngleDeg)
        {
            matchedAngleDeg = 0.0;
            if (detect.trackId < 0)
            {
                return false;
            }
            if (!rule.geometryId.empty())
            {
                if (!regionState || !regionState->inRegion)
                {
                    return false;
                }
                if (rule.thresholdMs > 0 && regionState->inRegionDurationMs < rule.thresholdMs)
                {
                    return false;
                }
            }
            else if (rule.thresholdMs > 0 && detect.dwellMs < rule.thresholdMs)
            {
                return false;
            }

            if (!tryResolveMovementAngleDeg(detect, matchedAngleDeg))
            {
                return false;
            }

            double expectedAngleDeg = normalizeAngleDeg(rule.directionAngleDeg);
            if (rule.behaviorType == "direction_reverse")
            {
                expectedAngleDeg = normalizeAngleDeg(expectedAngleDeg + 180.0);
            }
            return computeAngleDeltaDeg(matchedAngleDeg, expectedAngleDeg) <= std::max(1.0, rule.directionToleranceDeg);
        }

        /**
         * @brief Check cross_line hit using trail crossing logic.
         */
        bool tryResolveCrossLineBehavior(const Control &control,
                         const DetectObject &detect,
                         const std::string &requestedLineId,
                         const std::string &requestedDirection,
                         const std::string &ruleId,
                         const std::string &customEventName,
                         BehaviorDecision &decision)
        {
            if (detect.trackId < 0 || detect.trail.size() < 2)
            {
                return false;
            }

            // Use the most recent two trail points
            std::vector<TrackTrailPoint> latestTrail;
            latestTrail.reserve(2);
            latestTrail.push_back(detect.trail[detect.trail.size() - 2]);
            latestTrail.push_back(detect.trail[detect.trail.size() - 1]);

            auto computeLineSide = [](const GeometryLineSegment &line,
                                      const TrackTrailPoint &point) -> double
            {
                return (line.end.x - line.start.x) * (static_cast<double>(point.y) - line.start.y) -
                       (line.end.y - line.start.y) * (static_cast<double>(point.x) - line.start.x);
            };

            for (size_t i = 0; i < control.lines.size(); ++i)
            {
                const LineConfig &line = control.lines[i];
                if (line.points.size() < 2)
                {
                    continue;
                }
                if (!requestedLineId.empty() && line.id != requestedLineId)
                {
                    continue;
                }

                GeometryLineSegment geometryLine;
                geometryLine.start.x = static_cast<double>(line.points[0].x);
                geometryLine.start.y = static_cast<double>(line.points[0].y);
                geometryLine.end.x = static_cast<double>(line.points[1].x);
                geometryLine.end.y = static_cast<double>(line.points[1].y);
                if (!didTrailCrossLine(latestTrail, geometryLine))
                {
                    continue;
                }

                decision.matched = true;
                decision.ruleId = ruleId;
                decision.customEventName = customEventName;
                decision.behaviorType = "cross_line";
                decision.lineId = line.id;
                decision.lineName = line.name.empty() ? line.id : line.name;

                const double previousSide = computeLineSide(geometryLine, latestTrail[0]);
                const double currentSide = computeLineSide(geometryLine, latestTrail[1]);
                if (previousSide > 0.0 && currentSide < 0.0)
                {
                    decision.crossingDirection = "left_to_right";
                }
                else if (previousSide < 0.0 && currentSide > 0.0)
                {
                    decision.crossingDirection = "right_to_left";
                }
                else
                {
                    decision.crossingDirection = "unknown";
                }
                const std::string effectiveDirection = requestedDirection.empty() ? line.direction : requestedDirection;
                if (effectiveDirection == "left_to_right" || effectiveDirection == "right_to_left")
                {
                    if (decision.crossingDirection != effectiveDirection)
                    {
                        decision = BehaviorDecision{};
                        continue;
                    }
                }
                return true;
            }
            return false;
        }
    }

    bool controlUsesBehaviorOnlyMode(const Control &control)
    {
        return hasEnabledBehaviorRules(control) || control.dwellEnabled || hasLineCrossingBehavior(control);
    }

    /**
     * @brief Main behavior evaluation entry point.
     *
     * Teaching note: For each detect object from the current frame, this function iterates
     * through all enabled behavior rules in the control and returns the first match.
     *
     * The Scheduler (or Analyzer) calls this after temporal tracking has enriched the
     * DetectObject with trail, region states, speed, direction, etc.
     */
    BehaviorDecision evaluateAtomicBehavior(const Control &control, const DetectObject &detect)
    {
        BehaviorDecision decision;

        // Phase 1: Evaluate explicit behavior rules (from behaviorRules array)
        if (hasEnabledBehaviorRules(control))
        {
            for (size_t i = 0; i < control.behaviorRules.size(); ++i)
            {
                const BehaviorRuleConfig &rule = control.behaviorRules[i];
                if (!rule.enabled)
                {
                    continue;
                }

                // Filter by object class if rule specifies one
                if (!rule.ruleObjectCode.empty() && detect.class_name != rule.ruleObjectCode &&
                    rule.behaviorType != "region_motion")
                {
                    continue;
                }

                // --- cross_line ---
                if (rule.behaviorType == "cross_line")
                {
                    if (tryResolveCrossLineBehavior(control, detect, rule.geometryId, rule.direction, rule.id, rule.customEventName, decision))
                    {
                        return decision;
                    }
                    continue;
                }

                // Resolve region info for region-based rules
                std::string regionId;
                std::string regionName;
                if (!resolveBehaviorRegionInfo(control, rule.geometryId, regionId, regionName))
                {
                    continue;
                }
                const RegionTemporalState *regionState = findRegionState(detect, regionId);

                // --- enter_region ---
                if (rule.behaviorType == "enter_region" && regionState && regionState->enteredRegion)
                {
                    decision.matched = true;
                    decision.ruleId = rule.id;
                    decision.customEventName = rule.customEventName;
                    decision.behaviorType = "enter_region";
                    decision.regionId = regionId;
                    decision.regionName = regionName;
                    return decision;
                }

                // --- exit_region ---
                if (rule.behaviorType == "exit_region" && regionState && regionState->exitedRegion)
                {
                    decision.matched = true;
                    decision.ruleId = rule.id;
                    decision.customEventName = rule.customEventName;
                    decision.behaviorType = "exit_region";
                    decision.regionId = regionId;
                    decision.regionName = regionName;
                    return decision;
                }

                // --- dwell ---
                if (rule.behaviorType == "dwell" && isDwellHit(rule.thresholdMs, detect, regionState))
                {
                    decision.matched = true;
                    decision.ruleId = rule.id;
                    decision.customEventName = rule.customEventName;
                    decision.behaviorType = "dwell";
                    decision.regionId = regionId;
                    decision.regionName = regionName;
                    return decision;
                }

                // --- low_speed ---
                if (rule.behaviorType == "low_speed" && isLowSpeedHit(rule.maxSpeedPxPerSec, rule.thresholdMs, detect, regionState))
                {
                    decision.matched = true;
                    decision.ruleId = rule.id;
                    decision.customEventName = rule.customEventName;
                    decision.behaviorType = "low_speed";
                    decision.regionId = regionId;
                    decision.regionName = regionName;
                    return decision;
                }

                // --- loitering ---
                if (rule.behaviorType == "loitering" && isLoiteringHit(rule.maxDisplacementPx, rule.thresholdMs, detect, regionState))
                {
                    decision.matched = true;
                    decision.ruleId = rule.id;
                    decision.customEventName = rule.customEventName;
                    decision.behaviorType = "loitering";
                    decision.regionId = regionId;
                    decision.regionName = regionName;
                    return decision;
                }

                // --- sleep ---
                if (rule.behaviorType == "sleep" && isSleepHit(rule, detect, regionState))
                {
                    decision.matched = true;
                    decision.ruleId = rule.id;
                    decision.customEventName = rule.customEventName;
                    decision.behaviorType = "sleep";
                    decision.regionId = regionId;
                    decision.regionName = regionName;
                    return decision;
                }

                // --- direction_move / direction_reverse ---
                if ((rule.behaviorType == "direction_move" || rule.behaviorType == "direction_reverse"))
                {
                    double matchedAngleDeg = 0.0;
                    if (isDirectionRuleHit(rule, detect, regionState, matchedAngleDeg))
                    {
                        decision.matched = true;
                        decision.ruleId = rule.id;
                        decision.customEventName = rule.customEventName;
                        decision.behaviorType = rule.behaviorType;
                        decision.regionId = regionId;
                        decision.regionName = regionName;
                        decision.directionAngleDeg = matchedAngleDeg;
                        return decision;
                    }
                }
            }

            return decision;
        }

        // Phase 2: Legacy line crossing (no explicit behavior rules, but lines exist)
        if (hasLineCrossingBehavior(control) && tryResolveCrossLineBehavior(control, detect, "", "", "", "", decision))
        {
            return decision;
        }

        return decision;
    }
}