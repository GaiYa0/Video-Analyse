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
        constexpr int64_t kDefaultSleepHoldMs = 5000;
        constexpr int64_t kMaxSleepHoldMs = 3600000;

        // 布控页会下发 2500ms，盖掉 5 秒口径。空值或过短都抬到默认。
        inline int64_t clampSleepHoldMs(int64_t thresholdMs)
        {
            if (thresholdMs <= 0)
            {
                return kDefaultSleepHoldMs;
            }
            return std::max<int64_t>(kDefaultSleepHoldMs, std::min<int64_t>(kMaxSleepHoldMs, thresholdMs));
        }
        constexpr int64_t kDefaultRecoverHoldMs = 600;
        constexpr float kHeadAboveNeckMinPx = 8.0f;
        constexpr float kPitchAttackAlpha = 0.55f;
        constexpr float kPitchReleaseAlpha = 0.28f;
        constexpr float kExpectedHeadToTorso = 0.42f;
        constexpr float kExpectedHeadToShoulder = 0.50f;
        constexpr float kMinBodyScalePx = 24.0f;

        // Frame quality gates. A frame failing any of these carries no pitch at all,
        // so a bad frame can never push a track towards 睡岗.
        constexpr float kMinMeanKeypointConf = 0.35f;
        constexpr float kMaxPitchJumpDeg = 45.0f;
        // Face-down on folded arms loses all five head points and the desk hides the
        // hips, so a genuine sleeper reports as few as seven. Counting points is a
        // blunt filter anyway; computePitch below is the one that actually decides.
        constexpr int kMinPoseKeypoints = 4;
        constexpr float kMinPersonBoxHeightRatio = 0.18f;

        // Eyes and ears are read at a lower bar than shoulders: a face buried in folded
        // arms is exactly when the nose disappears and only a sliver of ear survives.
        constexpr float kHeadPointConfScale = 0.7f;

        // Geometry hard conditions.
        // Head may sit at most this share of the body scale ABOVE the neck line and
        // still count as head-down. A facing-camera head is far higher than this.
        constexpr float kMaxHeadAboveNeckRatio = 0.50f;
        // A desk hides the hips, so the up-axis falls back to the rotated shoulder
        // line. That ruler is the known laptop-webcam false positive, so entering
        // head-down without hips needs a steeper angle.
        constexpr float kNoHipEnterPenaltyDeg = 6.0f;

        // Window evidence required on top of the hold time.
        constexpr float kSleepMinDownRatio = 0.80f;
        constexpr int64_t kSleepMaxPoseGapMs = 1200;
        constexpr float kSleepMaxGapRatio = 0.50f;
        constexpr int kSleepMinValidFrames = 3;
        constexpr float kSleepPeakPitchDeg = 45.0f;
        constexpr float kSleepMaxHeadDriftRatio = 0.45f;
        constexpr int64_t kMaxFrameDeltaMs = 1000;

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

        /**
         * @brief Why a frame carried no pitch. Diagnostic only, logged so the gates can
         * be tuned against real footage instead of guesswork.
         */
        enum class PitchReject
        {
            None = 0,
            NoHead = 1,
            NoNeck = 2,
            WeakTorso = 3,
            LowConfidence = 4,
            SmallScale = 5,
            NoAxis = 6,
            SmallBox = 7,
            TooFewKeypoints = 8,
        };

        inline const char *pitchRejectName(PitchReject reason)
        {
            switch (reason)
            {
            case PitchReject::None:
                return "ok";
            case PitchReject::NoHead:
                return "no_head";
            case PitchReject::NoNeck:
                return "no_neck";
            case PitchReject::WeakTorso:
                return "weak_torso";
            case PitchReject::LowConfidence:
                return "low_conf";
            case PitchReject::SmallScale:
                return "small_scale";
            case PitchReject::NoAxis:
                return "no_axis";
            case PitchReject::SmallBox:
                return "small_box";
            case PitchReject::TooFewKeypoints:
                return "few_kps";
            }
            return "unknown";
        }

        /**
         * @brief Per-frame geometry output. `valid` false means "this frame carries no
         * usable pitch", which is different from "the person is upright".
         */
        struct PitchResult
        {
            bool valid = false;
            float pitchDeg = 0.0f;
            float scalePx = 0.0f;
            float headX = 0.0f;
            float headY = 0.0f;
            float meanConf = 0.0f;
            bool hasHip = false;
            bool bothShoulders = false;
            bool frontalFace = false;
            float headAboveNeckPx = 0.0f;
            PitchReject reject = PitchReject::None;
        };

        inline PitchResult rejectedPitch(PitchReject reason)
        {
            PitchResult result;
            result.reject = reason;
            return result;
        }

        /**
         * @brief What updateTemporal() consumes each frame.
         */
        struct FrameInput
        {
            bool valid = false;
            float pitchDeg = 0.0f;
            float headX = 0.0f;
            float headY = 0.0f;
            float scalePx = 0.0f;
            bool hasHip = false;
        };

        /**
         * @brief Why the current frame was (or was not) labelled Sleep. Logged and drawn,
         * not part of the alarm JSON contract.
         */
        struct FrameEvidence
        {
            int64_t headDownMs = 0;
            int headDownFrames = 0;
            int validFrames = 0;
            float downRatio = 1.0f;
            float gapRatio = 0.0f;
            float peakPitchDeg = 0.0f;
            float headDriftPx = 0.0f;
            float smoothedPitchDeg = 0.0f;
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
            int64_t lastUpdateMs = 0;
            int64_t lastValidMs = 0;
            int64_t downMs = 0;
            int64_t notDownMs = 0;
            int64_t gapMs = 0;
            int validFrames = 0;
            float peakPitchDeg = 0.0f;
            float headAnchorX = 0.0f;
            float headAnchorY = 0.0f;
            float anchorScalePx = 0.0f;
            float maxHeadDriftPx = 0.0f;
            bool hasAnchor = false;
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

        /**
         * @brief Head anchor. `strong` means nose, both eyes or both ears: enough to
         * trust the head position on its own. A single eye/ear is weaker and needs a
         * torso reference, but it must still be accepted: face-down on folded arms is
         * precisely when the nose vanishes and one ear is all that is left.
         */
        inline bool pickHead(const Keypoint *kps, int count, float minConf,
                             float &x, float &y, float &confSum, int &confCount, bool &strong)
        {
            if (!kps || count <= kNose)
            {
                return false;
            }
            const float headConf = minConf * kHeadPointConfScale;

            if (usable(kps[kNose], minConf))
            {
                x = kps[kNose].x;
                y = kps[kNose].y;
                confSum += kps[kNose].conf;
                confCount += 1;
                strong = true;
                return true;
            }
            if (count > kRightEye && midpoint(kps[kLeftEye], kps[kRightEye], headConf, x, y))
            {
                confSum += kps[kLeftEye].conf + kps[kRightEye].conf;
                confCount += 2;
                strong = true;
                return true;
            }
            if (count > kRightEar && midpoint(kps[kLeftEar], kps[kRightEar], headConf, x, y))
            {
                confSum += kps[kLeftEar].conf + kps[kRightEar].conf;
                confCount += 2;
                strong = true;
                return true;
            }

            const int singles[4] = {kLeftEye, kRightEye, kLeftEar, kRightEar};
            for (int i = 0; i < 4; ++i)
            {
                const int index = singles[i];
                if (count > index && usable(kps[index], headConf))
                {
                    x = kps[index].x;
                    y = kps[index].y;
                    confSum += kps[index].conf;
                    confCount += 1;
                    strong = false;
                    return true;
                }
            }
            return false;
        }

        /**
         * @brief Neck anchor. `both` means the shoulder midpoint was used. A single
         * shoulder puts the "neck" off to one side, which silently defeats the upright
         * gate, so callers only accept it together with a hip.
         */
        inline bool pickNeck(const Keypoint *kps, int count, float minConf,
                             float &x, float &y, float &confSum, int &confCount, bool &both)
        {
            if (!kps || count <= kRightShoulder)
            {
                return false;
            }
            if (midpoint(kps[kLeftShoulder], kps[kRightShoulder], minConf, x, y))
            {
                confSum += kps[kLeftShoulder].conf + kps[kRightShoulder].conf;
                confCount += 2;
                both = true;
                return true;
            }
            both = false;
            if (usable(kps[kLeftShoulder], minConf))
            {
                x = kps[kLeftShoulder].x;
                y = kps[kLeftShoulder].y;
                confSum += kps[kLeftShoulder].conf;
                confCount += 1;
                return true;
            }
            if (usable(kps[kRightShoulder], minConf))
            {
                x = kps[kRightShoulder].x;
                y = kps[kRightShoulder].y;
                confSum += kps[kRightShoulder].conf;
                confCount += 1;
                return true;
            }
            return false;
        }

        inline bool pickHip(const Keypoint *kps, int count, float minConf,
                            float &x, float &y, float &confSum, int &confCount)
        {
            if (!kps || count <= kRightHip)
            {
                return false;
            }
            if (midpoint(kps[kLeftHip], kps[kRightHip], minConf, x, y))
            {
                confSum += kps[kLeftHip].conf + kps[kRightHip].conf;
                confCount += 2;
                return true;
            }
            if (usable(kps[kLeftHip], minConf))
            {
                x = kps[kLeftHip].x;
                y = kps[kLeftHip].y;
                confSum += kps[kLeftHip].conf;
                confCount += 1;
                return true;
            }
            if (usable(kps[kRightHip], minConf))
            {
                x = kps[kRightHip].x;
                y = kps[kRightHip].y;
                confSum += kps[kRightHip].conf;
                confCount += 1;
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
                   eyeDistance(kps, count, minConf) >= kHeadAboveNeckMinPx;
        }

        inline bool isProfileView(float shoulderW, float torsoLen)
        {
            return torsoLen >= kMinBodyScalePx && shoulderW < 0.35f * torsoLen;
        }

        /**
         * @brief Cap the angle whenever the head is still riding above the neck line.
         * Desk sleep drops the head to or below the shoulders; a facing-camera head on a
         * downward laptop webcam does not, however large the shoulder ruler gets.
         */
        inline float applyUprightGate(float pitch, float headAbove, float scale, bool frontalFace)
        {
            // A downward laptop webcam keeps a face-down sleeper above the shoulder
            // line in image Y. Only a facing-camera face, or a head still far above
            // the neck, is safe to treat as "definitely sitting up".
            if (frontalFace && headAbove >= kHeadAboveNeckMinPx)
            {
                return std::min(pitch, kUprightPitchCapDeg);
            }
            if (headAbove > kMaxHeadAboveNeckRatio * scale)
            {
                return std::min(pitch, kUprightPitchCapDeg);
            }
            return pitch;
        }

        inline PitchResult computePitch(const Keypoint *kps, int count, float minConf)
        {
            PitchResult result;

            float confSum = 0.0f;
            int confCount = 0;
            float headX = 0.0f;
            float headY = 0.0f;
            bool headStrong = false;
            float neckX = 0.0f;
            float neckY = 0.0f;
            bool bothShoulders = false;

            if (!pickHead(kps, count, minConf, headX, headY, confSum, confCount, headStrong))
            {
                return rejectedPitch(PitchReject::NoHead);
            }
            if (!pickNeck(kps, count, minConf, neckX, neckY, confSum, confCount, bothShoulders))
            {
                return rejectedPitch(PitchReject::NoNeck);
            }

            float hipX = 0.0f;
            float hipY = 0.0f;
            const bool hasHip = pickHip(kps, count, minConf, hipX, hipY, confSum, confCount);

            // One shoulder alone mislocates the neck, and a lone eye/ear is a shaky head.
            // Either needs a torso reference; what stops a turned-away person from
            // scoring as a slump is the head-above-neck condition further down.
            if (!bothShoulders && !hasHip)
            {
                return rejectedPitch(PitchReject::WeakTorso);
            }
            if (!headStrong && !bothShoulders && !hasHip)
            {
                return rejectedPitch(PitchReject::WeakTorso);
            }

            const float meanConf = confCount > 0 ? confSum / static_cast<float>(confCount) : 0.0f;
            if (meanConf < kMinMeanKeypointConf)
            {
                return rejectedPitch(PitchReject::LowConfidence);
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
            if (hasHip)
            {
                ux = neckX - hipX;
                uy = neckY - hipY;
                torsoLen = std::sqrt(ux * ux + uy * uy);
            }

            if (torsoLen < kMinBodyScalePx)
            {
                if (shoulderW < kMinBodyScalePx || count <= kRightShoulder)
                {
                    return rejectedPitch(PitchReject::SmallScale);
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
                    return rejectedPitch(PitchReject::NoAxis);
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
                return rejectedPitch(PitchReject::SmallScale);
            }

            const float elev = ux * (headX - neckX) + uy * (headY - neckY);
            float pitch = mapElevToPitch(elev, scale);
            if (isProfileView(shoulderW, torsoLen))
            {
                pitch = std::max(pitch, mapElevToPitch(-(headY - neckY), scale));
            }

            const float headAbove = neckY - headY;
            pitch = applyUprightGate(pitch, headAbove, scale, isFrontalFace(kps, count, minConf));

            result.valid = true;
            result.pitchDeg = std::max(0.0f, std::min(135.0f, pitch));
            result.scalePx = scale;
            result.headX = headX;
            result.headY = headY;
            result.meanConf = meanConf;
            result.hasHip = hasHip;
            result.bothShoulders = bothShoulders;
            result.frontalFace = isFrontalFace(kps, count, minConf);
            result.headAboveNeckPx = headAbove;
            return result;
        }

        inline bool tryComputePitchDeg(const Keypoint *kps, int count, float minConf, float &pitchDeg)
        {
            const PitchResult result = computePitch(kps, count, minConf);
            if (!result.valid)
            {
                return false;
            }
            pitchDeg = result.pitchDeg;
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

        inline void resetStreak(TemporalState &state)
        {
            state.headDownFrames = 0;
            state.headDownSinceMs = 0;
            state.recoverSinceMs = 0;
            state.downMs = 0;
            state.notDownMs = 0;
            state.gapMs = 0;
            state.validFrames = 0;
            state.peakPitchDeg = 0.0f;
            state.maxHeadDriftPx = 0.0f;
            state.hasAnchor = false;
            state.anchorScalePx = 0.0f;
        }

        inline void fillEvidence(const TemporalState &state, int64_t headDownMs, FrameEvidence &evidence)
        {
            const int64_t accounted = state.downMs + state.notDownMs;
            evidence.headDownMs = headDownMs;
            evidence.headDownFrames = state.headDownFrames;
            evidence.validFrames = state.validFrames;
            evidence.downRatio = accounted > 0
                                     ? static_cast<float>(state.downMs) / static_cast<float>(accounted)
                                     : 1.0f;
            evidence.gapRatio = headDownMs > 0
                                    ? static_cast<float>(state.gapMs) / static_cast<float>(headDownMs)
                                    : 0.0f;
            evidence.peakPitchDeg = state.peakPitchDeg;
            evidence.headDriftPx = state.maxHeadDriftPx;
            evidence.smoothedPitchDeg = state.smoothedPitchDeg;
        }

        /**
         * @brief Head-down long enough is necessary but not sufficient. Reading, typing
         * and phone use all keep the head low; they differ in duty cycle, in how deep
         * the head actually goes, and in how much it drifts.
         */
        inline bool sleepEvidenceSatisfied(const TemporalState &state, int64_t headDownMs,
                                           int64_t holdMs, const FrameEvidence &evidence)
        {
            if (headDownMs < holdMs)
            {
                return false;
            }
            if (state.validFrames < kSleepMinValidFrames)
            {
                return false;
            }
            if (evidence.downRatio < kSleepMinDownRatio)
            {
                return false;
            }
            if (evidence.gapRatio > kSleepMaxGapRatio)
            {
                return false;
            }
            if (state.peakPitchDeg < kSleepPeakPitchDeg)
            {
                return false;
            }
            if (state.hasAnchor && state.anchorScalePx > 0.0f &&
                state.maxHeadDriftPx > kSleepMaxHeadDriftRatio * state.anchorScalePx)
            {
                return false;
            }
            return true;
        }

        inline const char *sleepEvidenceBlockName(const TemporalState &state, int64_t headDownMs,
                                                  int64_t holdMs, const FrameEvidence &evidence)
        {
            if (headDownMs < holdMs)
            {
                return "hold";
            }
            if (state.validFrames < kSleepMinValidFrames)
            {
                return "frames";
            }
            if (evidence.downRatio < kSleepMinDownRatio)
            {
                return "ratio";
            }
            if (evidence.gapRatio > kSleepMaxGapRatio)
            {
                return "gap";
            }
            if (state.peakPitchDeg < kSleepPeakPitchDeg)
            {
                return "peak";
            }
            if (state.hasAnchor && state.anchorScalePx > 0.0f &&
                state.maxHeadDriftPx > kSleepMaxHeadDriftRatio * state.anchorScalePx)
            {
                return "drift";
            }
            return "ok";
        }

        inline FrameLabel updateTemporal(TemporalState &state,
                                         const FrameInput &frame,
                                         int64_t nowMs,
                                         float downDeg,
                                         float recoverDeg,
                                         int64_t holdMs,
                                         int64_t recoverHoldMs,
                                         FrameEvidence &evidence)
        {
            const int64_t deltaMs = state.lastUpdateMs > 0
                                        ? std::max<int64_t>(0, std::min<int64_t>(kMaxFrameDeltaMs, nowMs - state.lastUpdateMs))
                                        : 0;
            state.lastUpdateMs = nowMs;

            if (!frame.valid)
            {
                if (state.headDownFrames <= 0)
                {
                    fillEvidence(state, 0, evidence);
                    return FrameLabel::Upright;
                }

                // No pitch means no evidence. Letting the clock run here is how a person
                // who already walked away used to reach the hold time.
                const int64_t continuousGapMs = state.lastValidMs > 0 ? nowMs - state.lastValidMs : nowMs - state.headDownSinceMs;
                state.gapMs += deltaMs;
                const int64_t headDownMs = std::max<int64_t>(0, nowMs - state.headDownSinceMs);
                fillEvidence(state, headDownMs, evidence);

                // The ratio only means something once the missing time is itself
                // substantial; on a two-frame streak one dropped frame is already half
                // the window.
                const bool starved = state.gapMs > kSleepMaxPoseGapMs && evidence.gapRatio > kSleepMaxGapRatio;
                if (continuousGapMs > kSleepMaxPoseGapMs || starved)
                {
                    resetStreak(state);
                    fillEvidence(state, 0, evidence);
                    return FrameLabel::Upright;
                }
                return sleepEvidenceSatisfied(state, headDownMs, holdMs, evidence)
                           ? FrameLabel::Sleep
                           : FrameLabel::Bow;
            }

            float rawPitch = frame.pitchDeg;
            if (state.lastPitchValid && std::fabs(rawPitch - state.lastPitchDeg) > kMaxPitchJumpDeg)
            {
                // Keypoints teleported. Treat the frame as missing rather than believing it.
                state.lastPitchValid = false;
                if (state.headDownFrames <= 0)
                {
                    fillEvidence(state, 0, evidence);
                    return FrameLabel::Upright;
                }
                state.gapMs += deltaMs;
                const int64_t headDownMs = std::max<int64_t>(0, nowMs - state.headDownSinceMs);
                fillEvidence(state, headDownMs, evidence);
                return sleepEvidenceSatisfied(state, headDownMs, holdMs, evidence)
                           ? FrameLabel::Sleep
                           : FrameLabel::Bow;
            }

            if (!state.hasSmoothed)
            {
                state.smoothedPitchDeg = rawPitch;
                state.hasSmoothed = true;
            }
            else
            {
                const float alpha = rawPitch >= state.smoothedPitchDeg ? kPitchAttackAlpha : kPitchReleaseAlpha;
                const float clamped = std::max(0.05f, std::min(1.0f, alpha));
                state.smoothedPitchDeg = clamped * rawPitch + (1.0f - clamped) * state.smoothedPitchDeg;
            }
            state.lastPitchDeg = rawPitch;
            state.lastPitchValid = true;
            state.lastValidMs = nowMs;

            const float logicPitch = state.smoothedPitchDeg;
            const float enterDeg = frame.hasHip ? downDeg : downDeg + kNoHipEnterPenaltyDeg;
            const bool down = state.headDownFrames > 0 ? (logicPitch >= recoverDeg) : (logicPitch >= enterDeg);

            if (down)
            {
                if (state.headDownFrames == 0)
                {
                    resetStreak(state);
                    state.headDownSinceMs = nowMs;
                    state.headAnchorX = frame.headX;
                    state.headAnchorY = frame.headY;
                    state.anchorScalePx = frame.scalePx;
                    state.hasAnchor = true;
                }
                else if (rawPitch > state.peakPitchDeg + 3.0f && frame.scalePx > 0.0f)
                {
                    // Still dropping onto the desk: follow the head so the descent
                    // itself is not counted as "fidgeting".
                    state.headAnchorX = frame.headX;
                    state.headAnchorY = frame.headY;
                    state.anchorScalePx = frame.scalePx;
                    state.maxHeadDriftPx = 0.0f;
                }
                state.recoverSinceMs = 0;
                state.headDownFrames += 1;
                state.validFrames += 1;
                state.downMs += deltaMs;
                state.peakPitchDeg = std::max(state.peakPitchDeg, logicPitch);
                if (state.hasAnchor)
                {
                    const float dx = frame.headX - state.headAnchorX;
                    const float dy = frame.headY - state.headAnchorY;
                    state.maxHeadDriftPx = std::max(state.maxHeadDriftPx, std::sqrt(dx * dx + dy * dy));
                }

                const int64_t headDownMs = std::max<int64_t>(0, nowMs - state.headDownSinceMs);
                fillEvidence(state, headDownMs, evidence);
                return sleepEvidenceSatisfied(state, headDownMs, holdMs, evidence)
                           ? FrameLabel::Sleep
                           : FrameLabel::Bow;
            }

            if (state.headDownFrames > 0)
            {
                if (state.recoverSinceMs <= 0)
                {
                    state.recoverSinceMs = nowMs;
                }
                if (recoverHoldMs > 0 && (nowMs - state.recoverSinceMs) < recoverHoldMs)
                {
                    state.validFrames += 1;
                    state.notDownMs += deltaMs;
                    const int64_t headDownMs = std::max<int64_t>(0, nowMs - state.headDownSinceMs);
                    fillEvidence(state, headDownMs, evidence);
                    return sleepEvidenceSatisfied(state, headDownMs, holdMs, evidence)
                               ? FrameLabel::Sleep
                               : FrameLabel::Bow;
                }
            }

            resetStreak(state);
            fillEvidence(state, 0, evidence);
            return FrameLabel::Upright;
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
            FrameInput frame;
            frame.valid = pitchValid;
            frame.pitchDeg = pitchDeg;
            frame.hasHip = true;
            frame.scalePx = 0.0f;
            FrameEvidence evidence;
            const FrameLabel label = updateTemporal(state, frame, nowMs, downDeg, recoverDeg,
                                                    holdMs, recoverHoldMs, evidence);
            headDownMs = evidence.headDownMs;
            return label;
        }
    }
}

#endif
