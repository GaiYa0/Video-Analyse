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
        constexpr int kLeftEye = 1;
        constexpr int kRightEye = 2;
        constexpr int kLeftEar = 3;
        constexpr int kRightEar = 4;
        constexpr int kLeftShoulder = 5;
        constexpr int kRightShoulder = 6;
        constexpr int kLeftHip = 11;
        constexpr int kRightHip = 12;
        constexpr int kKeypointCount = 17;
        constexpr float kMinKeypointConf = 0.25f;
        constexpr float kDefaultPitchDownDeg = 32.0f;
        constexpr float kDefaultPitchRecoverDeg = 22.0f;
        constexpr float kUprightPitchCapDeg = 18.0f;
        constexpr int64_t kDefaultSleepHoldMs = 2500;
        constexpr int64_t kDefaultRecoverHoldMs = 400;
        constexpr float kHeadAboveNeckMinPx = 8.0f;
        constexpr float kHeadAboveNeckRatio = 0.20f;
        constexpr float kPitchAttackAlpha = 0.55f;
        constexpr float kPitchReleaseAlpha = 0.28f;
        constexpr float kExpectedHeadToTorso = 0.42f;
        constexpr float kExpectedHeadToShoulder = 0.50f;
        constexpr float kMinBodyScalePx = 8.0f;

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
            float smoothedPitchDeg = 0.0f;
            bool hasSmoothed = false;
            int64_t recoverSinceMs = 0;
        };

        inline bool usable(const Keypoint &p, float minConf)
        {
            return p.conf >= minConf;
        }

        inline bool midpoint(const Keypoint &a, const Keypoint &b, float minConf, float &x, float &y)
        {
            if (!usable(a, minConf) || !usable(b, minConf))
            {
                return false;
            }
            x = 0.5f * (a.x + b.x);
            y = 0.5f * (a.y + b.y);
            return true;
        }

        inline bool pickHead(const Keypoint *kps, int count, float minConf, float &x, float &y)
        {
            if (!kps || count <= kNose)
            {
                return false;
            }
            const float headConf = minConf * 0.7f;
            if (usable(kps[kNose], minConf))
            {
                x = kps[kNose].x;
                y = kps[kNose].y;
                return true;
            }
            if (count > kRightEye && midpoint(kps[kLeftEye], kps[kRightEye], headConf, x, y))
            {
                return true;
            }
            if (count > kLeftEye && usable(kps[kLeftEye], headConf))
            {
                x = kps[kLeftEye].x;
                y = kps[kLeftEye].y;
                return true;
            }
            if (count > kRightEye && usable(kps[kRightEye], headConf))
            {
                x = kps[kRightEye].x;
                y = kps[kRightEye].y;
                return true;
            }
            if (count > kRightEar && midpoint(kps[kLeftEar], kps[kRightEar], headConf, x, y))
            {
                return true;
            }
            if (count > kLeftEar && usable(kps[kLeftEar], headConf))
            {
                x = kps[kLeftEar].x;
                y = kps[kLeftEar].y;
                return true;
            }
            if (count > kRightEar && usable(kps[kRightEar], headConf))
            {
                x = kps[kRightEar].x;
                y = kps[kRightEar].y;
                return true;
            }
            return false;
        }

        inline bool pickNeck(const Keypoint *kps, int count, float minConf, float &x, float &y)
        {
            if (!kps || count <= kRightShoulder)
            {
                return false;
            }
            if (midpoint(kps[kLeftShoulder], kps[kRightShoulder], minConf, x, y))
            {
                return true;
            }
            if (usable(kps[kLeftShoulder], minConf))
            {
                x = kps[kLeftShoulder].x;
                y = kps[kLeftShoulder].y;
                return true;
            }
            if (usable(kps[kRightShoulder], minConf))
            {
                x = kps[kRightShoulder].x;
                y = kps[kRightShoulder].y;
                return true;
            }
            return false;
        }

        inline bool pickHip(const Keypoint *kps, int count, float minConf, float &x, float &y)
        {
            if (!kps || count <= kRightHip)
            {
                return false;
            }
            if (midpoint(kps[kLeftHip], kps[kRightHip], minConf, x, y))
            {
                return true;
            }
            if (usable(kps[kLeftHip], minConf))
            {
                x = kps[kLeftHip].x;
                y = kps[kLeftHip].y;
                return true;
            }
            if (usable(kps[kRightHip], minConf))
            {
                x = kps[kRightHip].x;
                y = kps[kRightHip].y;
                return true;
            }
            return false;
        }

        inline float mapElevToPitch(float elev, float scale)
        {
            float ratio = elev / std::max(scale, 1e-3f);
            ratio = std::max(-0.6f, std::min(1.15f, ratio));
            return (1.0f - ratio) * 90.0f;
        }

        inline float eyeDistance(const Keypoint *kps, int count, float minConf)
        {
            if (!kps || count <= kRightEye || !usable(kps[kLeftEye], minConf) || !usable(kps[kRightEye], minConf))
            {
                return 0.0f;
            }
            const float dx = kps[kRightEye].x - kps[kLeftEye].x;
            const float dy = kps[kRightEye].y - kps[kLeftEye].y;
            return std::sqrt(dx * dx + dy * dy);
        }

        inline bool isFrontalFace(const Keypoint *kps, int count, float minConf)
        {
            return kps && count > kRightEye && usable(kps[kNose], minConf) &&
                   usable(kps[kLeftEye], minConf) && usable(kps[kRightEye], minConf) &&
                   eyeDistance(kps, count, minConf) >= kMinBodyScalePx;
        }

        inline bool isProfileView(float shoulderW, float torsoLen)
        {
            return torsoLen >= kMinBodyScalePx && shoulderW < 0.35f * torsoLen;
        }

        inline float applyUprightGate(float pitch, float headY, float neckY, float scale, bool frontalFace)
        {
            const float headAbove = neckY - headY;
            if (frontalFace && headAbove >= kHeadAboveNeckMinPx)
            {
                return std::min(pitch, kUprightPitchCapDeg);
            }
            if (headAbove >= std::max(kHeadAboveNeckMinPx, kHeadAboveNeckRatio * scale))
            {
                return std::min(pitch, kUprightPitchCapDeg);
            }
            return pitch;
        }

        inline bool tryComputePitchDeg(const Keypoint *kps,
                                       int count,
                                       float minConf,
                                       float &pitchDeg)
        {
            float headX = 0.0f;
            float headY = 0.0f;
            float neckX = 0.0f;
            float neckY = 0.0f;
            if (!pickHead(kps, count, minConf, headX, headY) || !pickNeck(kps, count, minConf, neckX, neckY))
            {
                return false;
            }

            float shoulderW = 0.0f;
            if (count > kRightShoulder && usable(kps[kLeftShoulder], minConf) && usable(kps[kRightShoulder], minConf))
            {
                const float sx = kps[kRightShoulder].x - kps[kLeftShoulder].x;
                const float sy = kps[kRightShoulder].y - kps[kLeftShoulder].y;
                shoulderW = std::sqrt(sx * sx + sy * sy);
            }

            float torsoLen = 0.0f;
            float ux = 0.0f;
            float uy = 0.0f;
            float hipX = 0.0f;
            float hipY = 0.0f;
            if (pickHip(kps, count, minConf, hipX, hipY))
            {
                ux = neckX - hipX;
                uy = neckY - hipY;
                torsoLen = std::sqrt(ux * ux + uy * uy);
            }

            if (torsoLen < kMinBodyScalePx)
            {
                if (shoulderW < kMinBodyScalePx || count <= kRightShoulder)
                {
                    return false;
                }
                const float sx = kps[kRightShoulder].x - kps[kLeftShoulder].x;
                const float sy = kps[kRightShoulder].y - kps[kLeftShoulder].y;
                ux = -sy;
                uy = sx;
                if (uy > 0.0f)
                {
                    ux = -ux;
                    uy = -uy;
                }
                const float uNorm = std::sqrt(ux * ux + uy * uy);
                if (uNorm < 1e-3f)
                {
                    return false;
                }
                ux /= uNorm;
                uy /= uNorm;
            }
            else
            {
                ux /= torsoLen;
                uy /= torsoLen;
            }

            float scale = 0.0f;
            if (torsoLen >= kMinBodyScalePx)
            {
                scale = std::max(scale, kExpectedHeadToTorso * torsoLen);
            }
            if (shoulderW >= kMinBodyScalePx)
            {
                scale = std::max(scale, kExpectedHeadToShoulder * shoulderW);
            }
            if (scale < kMinBodyScalePx)
            {
                return false;
            }

            const float elev = ux * (headX - neckX) + uy * (headY - neckY);
            float pitch = mapElevToPitch(elev, scale);
            if (isProfileView(shoulderW, torsoLen))
            {
                pitch = std::max(pitch, mapElevToPitch(-(headY - neckY), scale));
            }
            pitch = applyUprightGate(pitch, headY, neckY, scale, isFrontalFace(kps, count, minConf));
            pitchDeg = std::max(0.0f, std::min(135.0f, pitch));
            return true;
        }

        inline bool tryComputePitchDeg(const Keypoint &nose,
                                       const Keypoint &leftShoulder,
                                       const Keypoint &rightShoulder,
                                       float minConf,
                                       float &pitchDeg)
        {
            Keypoint kps[kKeypointCount] = {};
            kps[kNose] = nose;
            kps[kLeftShoulder] = leftShoulder;
            kps[kRightShoulder] = rightShoulder;
            return tryComputePitchDeg(kps, kKeypointCount, minConf, pitchDeg);
        }

        inline FrameLabel updateTemporal(TemporalState &state,
                                         bool pitchValid,
                                         float pitchDeg,
                                         int64_t nowMs,
                                         float downDeg,
                                         float recoverDeg,
                                         int64_t holdMs,
                                         int64_t recoverHoldMs,
                                         int64_t &headDownMs)
        {
            bool hasLogicPitch = false;
            float logicPitch = 0.0f;
            if (pitchValid)
            {
                if (!state.hasSmoothed)
                {
                    state.smoothedPitchDeg = pitchDeg;
                    state.hasSmoothed = true;
                }
                else
                {
                    const float alpha = pitchDeg >= state.smoothedPitchDeg ? kPitchAttackAlpha : kPitchReleaseAlpha;
                    const float clamped = std::max(0.05f, std::min(1.0f, alpha));
                    state.smoothedPitchDeg = clamped * pitchDeg + (1.0f - clamped) * state.smoothedPitchDeg;
                }
                state.lastPitchDeg = pitchDeg;
                state.lastPitchValid = true;
                logicPitch = state.smoothedPitchDeg;
                hasLogicPitch = true;
            }

            if (!hasLogicPitch)
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

            const bool down = state.headDownFrames > 0 ? (logicPitch >= recoverDeg) : (logicPitch >= downDeg);
            if (down)
            {
                state.recoverSinceMs = 0;
                if (state.headDownFrames == 0)
                {
                    state.headDownSinceMs = nowMs;
                }
                state.headDownFrames += 1;
                headDownMs = std::max<int64_t>(0, nowMs - state.headDownSinceMs);
                return headDownMs >= holdMs ? FrameLabel::Sleep : FrameLabel::Bow;
            }

            if (state.headDownFrames > 0)
            {
                if (state.recoverSinceMs <= 0)
                {
                    state.recoverSinceMs = nowMs;
                }
                if (recoverHoldMs > 0 && (nowMs - state.recoverSinceMs) < recoverHoldMs)
                {
                    headDownMs = std::max<int64_t>(0, nowMs - state.headDownSinceMs);
                    return headDownMs >= holdMs ? FrameLabel::Sleep : FrameLabel::Bow;
                }
            }

            state.headDownFrames = 0;
            state.headDownSinceMs = 0;
            state.recoverSinceMs = 0;
            headDownMs = 0;
            return FrameLabel::Upright;
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
            return updateTemporal(state, pitchValid, pitchDeg, nowMs, downDeg, recoverDeg, holdMs,
                                  kDefaultRecoverHoldMs, headDownMs);
        }
    }
}

#endif
