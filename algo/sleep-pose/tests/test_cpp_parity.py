"""The prototype and the Analyzer must agree constant by constant.

Changing one side and forgetting the other is the failure mode this guards:
`algo/sleep-pose` is what the report and the demo describe, while
`server/Analyzer/Core/SleepPose.h` is what actually raises the alarm.
"""

from __future__ import annotations

import re
import unittest
from pathlib import Path

from sleep_pose import geometry, temporal

HEADER = Path(__file__).resolve().parents[3] / "server" / "Analyzer" / "Core" / "SleepPose.h"

# C++ constant name -> Python value
EXPECTED = {
    "kMinKeypointConf": geometry.MIN_KEYPOINT_CONF,
    "kDefaultPitchDownDeg": geometry.PITCH_DOWN_DEG,
    "kDefaultPitchRecoverDeg": geometry.PITCH_RECOVER_DEG,
    "kUprightPitchCapDeg": geometry.UPRIGHT_PITCH_CAP_DEG,
    "kExpectedHeadToTorso": geometry.EXPECTED_HEAD_TO_TORSO,
    "kExpectedHeadToShoulder": geometry.EXPECTED_HEAD_TO_SHOULDER,
    "kMinBodyScalePx": geometry.MIN_BODY_SCALE_PX,
    "kHeadAboveNeckMinPx": geometry.HEAD_ABOVE_NECK_MIN_PX,
    "kMinMeanKeypointConf": geometry.MIN_MEAN_KEYPOINT_CONF,
    "kHeadPointConfScale": geometry.HEAD_POINT_CONF_SCALE,
    "kMaxPitchJumpDeg": geometry.MAX_PITCH_JUMP_DEG,
    "kMinPoseKeypoints": geometry.MIN_POSE_KEYPOINTS,
    "kMinPersonBoxHeightRatio": geometry.MIN_PERSON_BOX_HEIGHT_RATIO,
    "kMaxHeadAboveNeckRatio": geometry.MAX_HEAD_ABOVE_NECK_RATIO,
    "kNoHipEnterPenaltyDeg": geometry.NO_HIP_ENTER_PENALTY_DEG,
    "kDefaultSleepHoldMs": temporal.SLEEP_HOLD_MS,
    "kDefaultRecoverHoldMs": temporal.RECOVER_HOLD_MS,
    "kPitchAttackAlpha": temporal.PITCH_ATTACK_ALPHA,
    "kPitchReleaseAlpha": temporal.PITCH_RELEASE_ALPHA,
    "kSleepMinDownRatio": temporal.SLEEP_MIN_DOWN_RATIO,
    "kSleepMaxPoseGapMs": temporal.SLEEP_MAX_POSE_GAP_MS,
    "kSleepMaxGapRatio": temporal.SLEEP_MAX_GAP_RATIO,
    "kSleepMinValidFrames": temporal.SLEEP_MIN_VALID_FRAMES,
    "kSleepPeakPitchDeg": temporal.SLEEP_PEAK_PITCH_DEG,
    "kSleepMaxHeadDriftRatio": temporal.SLEEP_MAX_HEAD_DRIFT_RATIO,
    "kMaxFrameDeltaMs": temporal.MAX_FRAME_DELTA_MS,
}

PATTERN = re.compile(
    r"constexpr\s+(?:float|int|int64_t)\s+(k\w+)\s*=\s*(-?[0-9.]+)f?\s*;"
)


def _parse_header() -> dict[str, float]:
    text = HEADER.read_text(encoding="utf-8")
    return {name: float(value) for name, value in PATTERN.findall(text)}


class ParityTests(unittest.TestCase):
    def setUp(self):
        self.assertTrue(HEADER.is_file(), f"missing {HEADER}")
        self.cpp = _parse_header()

    def test_every_shared_constant_matches(self):
        for name, python_value in EXPECTED.items():
            with self.subTest(constant=name):
                self.assertIn(name, self.cpp, f"{name} not found in SleepPose.h")
                self.assertAlmostEqual(self.cpp[name], float(python_value), places=6)

    def test_hold_time_is_ten_seconds(self):
        self.assertEqual(temporal.SLEEP_HOLD_MS, 10000)
        self.assertEqual(self.cpp["kDefaultSleepHoldMs"], 10000)


if __name__ == "__main__":
    unittest.main()
