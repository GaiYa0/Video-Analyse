#ifndef ANALYZER_SLEEPPOSE_H
#define ANALYZER_SLEEPPOSE_H

#include <algorithm>
#include <cmath>
#include <cstdint>

namespace SVAAnalyzer
{
    namespace SleepPose
    {
        constexpr int kNose = 0;
        constexpr int kLeftShoulder = 5;
        constexpr int kRightShoulder = 6;
        constexpr int kKeypointCount = 17;
        constexpr float kMinKeypointConf = 0.25f;
        constexpr float kDefaultPitchDownDeg = 35.0f;
        constexpr float kDefaultPitchRecoverDeg = 25.0f;
        constexpr int64_t kDefaultSleepHoldMs = 3000;
        constexpr float kExpectedHeadToShoulder = 0.50f;

        struct Keypoint
        {
            float x = 0.0f;
            float y = 0.0f;
            float conf = 0.0f;
        };

        enum class FrameLabel
        {
            Upright = 0,
            Bow = 1,
            Sleep = 2,
        };

        struct TemporalState
        {
            int64_t headDownSinceMs = 0;
            int headDownFrames = 0;
            float lastPitchDeg = 0.0f;
            bool lastPitchValid = false;
        };

        inline bool tryComputePitchDeg(const Keypoint &nose,
                                       const Keypoint &leftShoulder,
                                       const Keypoint &rightShoulder,
                                       float minConf,
                                       float &pitchDeg)
        {
            if (nose.conf < minConf || leftShoulder.conf < minConf || rightShoulder.conf < minConf)
            {
                return false;
            }

            const float neckX = 0.5f * (leftShoulder.x + rightShoulder.x);
            const float neckY = 0.5f * (leftShoulder.y + rightShoulder.y);
            const float sx = rightShoulder.x - leftShoulder.x;
            const float sy = rightShoulder.y - leftShoulder.y;
            float ux = -sy;
            float uy = sx;
            if (uy > 0.0f)
            {
                ux = -ux;
                uy = -uy;
            }

            const float hx = nose.x - neckX;
            const float hy = nose.y - neckY;
            const float uNorm = std::sqrt(ux * ux + uy * uy);
            if (uNorm < 1e-3f)
            {
                return false;
            }

            const float elev = (ux * hx + uy * hy) / uNorm;
            const float expected = kExpectedHeadToShoulder * uNorm;
            float ratio = elev / std::max(expected, 1e-3f);
            ratio = std::max(-0.5f, std::min(1.0f, ratio));
            pitchDeg = (1.0f - ratio) * 90.0f;
            return true;
        }

        inline FrameLabel updateTemporal(TemporalState &state,
                                         bool pitchValid,
                                         float pitchDeg,
                                         int64_t nowMs,
                                         float downDeg,
                                         float recoverDeg,
                                         int64_t holdMs,
                                         int64_t &headDownMs)
        {
            if (!pitchValid)
            {
                headDownMs = 0;
                if (state.headDownFrames > 0 && state.headDownSinceMs > 0)
                {
                    headDownMs = std::max<int64_t>(0, nowMs - state.headDownSinceMs);
                }
                if (headDownMs >= holdMs)
                {
                    return FrameLabel::Sleep;
                }
                return state.headDownFrames > 0 ? FrameLabel::Bow : FrameLabel::Upright;
            }

            state.lastPitchDeg = pitchDeg;
            state.lastPitchValid = true;
            const bool down = state.headDownFrames > 0 ? (pitchDeg >= recoverDeg) : (pitchDeg >= downDeg);
            if (down)
            {
                if (state.headDownFrames == 0)
                {
                    state.headDownSinceMs = nowMs;
                }
                state.headDownFrames += 1;
                headDownMs = std::max<int64_t>(0, nowMs - state.headDownSinceMs);
                return headDownMs >= holdMs ? FrameLabel::Sleep : FrameLabel::Bow;
            }

            state.headDownFrames = 0;
            state.headDownSinceMs = 0;
            headDownMs = 0;
            return FrameLabel::Upright;
        }
    }
}

#endif
