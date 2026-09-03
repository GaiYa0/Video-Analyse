import math
import unittest

from sleep_pose.geometry import (
    LEFT_EAR,
    LEFT_EYE,
    LEFT_HIP,
    LEFT_SHOULDER,
    NOSE,
    RIGHT_EYE,
    RIGHT_HIP,
    RIGHT_SHOULDER,
    compute_pitch,
    pitch_from_coco17,
    try_compute_pitch_deg,
)


def _blank_coco17():
    return [[0.0, 0.0, 0.0] for _ in range(17)]


def _desk_sleeper():
    """Frontal desk slump: nose has dropped to the shoulder line."""
    kps = _blank_coco17()
    kps[NOSE] = [320, 268, 0.9]
    kps[LEFT_SHOULDER] = [200, 270, 0.9]
    kps[RIGHT_SHOULDER] = [440, 272, 0.9]
    kps[LEFT_HIP] = [220, 460, 0.8]
    kps[RIGHT_HIP] = [420, 462, 0.8]
    return kps


class PitchTests(unittest.TestCase):
    def test_upright_is_near_zero(self):
        # Shoulders horizontal, nose well above neck (image Y down).
        pitch = try_compute_pitch_deg((100, 40, 1), (70, 100, 1), (130, 100, 1))
        self.assertIsNotNone(pitch)
        self.assertLess(pitch, 15.0)

    def test_head_on_desk_is_large(self):
        # Nose almost at the neck line.
        pitch = try_compute_pitch_deg((100, 98, 1), (70, 100, 1), (130, 100, 1))
        self.assertIsNotNone(pitch)
        self.assertGreater(pitch, 60.0)

    def test_weak_keypoints_rejected(self):
        self.assertIsNone(try_compute_pitch_deg((100, 40, 0.1), (70, 100, 1), (130, 100, 1)))

    def test_down_greater_than_upright(self):
        up = try_compute_pitch_deg((100, 40, 1), (70, 100, 1), (130, 100, 1))
        down = try_compute_pitch_deg((100, 80, 1), (70, 100, 1), (130, 100, 1))
        self.assertIsNotNone(up)
        self.assertIsNotNone(down)
        self.assertGreater(down, up)
        self.assertFalse(math.isnan(down))

    def test_profile_upright_stays_low(self):
        kps = _blank_coco17()
        kps[NOSE] = [240, 90, 1]
        kps[LEFT_SHOULDER] = [200, 120, 1]
        kps[RIGHT_SHOULDER] = [210, 125, 0.9]
        kps[LEFT_HIP] = [195, 220, 1]
        kps[RIGHT_HIP] = [205, 225, 0.9]
        pitch = pitch_from_coco17(kps)
        self.assertIsNotNone(pitch)
        self.assertLess(pitch, 32.0)

    def test_profile_sleep_is_high(self):
        kps = _blank_coco17()
        kps[NOSE] = [250, 145, 1]
        kps[LEFT_SHOULDER] = [200, 120, 1]
        kps[RIGHT_SHOULDER] = [210, 125, 0.9]
        kps[LEFT_HIP] = [195, 220, 1]
        kps[RIGHT_HIP] = [205, 225, 0.9]
        pitch = pitch_from_coco17(kps)
        self.assertIsNotNone(pitch)
        self.assertGreater(pitch, 50.0)

    def test_profile_sleep_higher_than_upright(self):
        kps_up = _blank_coco17()
        kps_up[NOSE] = [240, 90, 1]
        kps_up[LEFT_SHOULDER] = [200, 120, 1]
        kps_up[RIGHT_SHOULDER] = [210, 125, 0.9]
        kps_up[LEFT_HIP] = [195, 220, 1]
        kps_up[RIGHT_HIP] = [205, 225, 0.9]
        kps_down = [list(p) for p in kps_up]
        kps_down[NOSE] = [250, 145, 1]
        self.assertGreater(pitch_from_coco17(kps_down), pitch_from_coco17(kps_up))

    def test_collapsed_shoulders_without_hips_rejected(self):
        # Old ruler exploded when shoulder width ≈ 0. Require a real body scale.
        self.assertIsNone(try_compute_pitch_deg((100, 40, 1), (100, 100, 1), (101, 100, 1)))

    def test_webcam_closeup_looking_at_camera_stays_low(self):
        # Downward laptop cam: huge shoulders, head only a bit above the neck.
        kps = _blank_coco17()
        kps[NOSE] = [320, 200, 0.95]
        kps[LEFT_EYE] = [290, 185, 0.9]
        kps[RIGHT_EYE] = [350, 185, 0.9]
        kps[LEFT_SHOULDER] = [160, 270, 0.85]
        kps[RIGHT_SHOULDER] = [480, 275, 0.85]
        pitch = pitch_from_coco17(kps)
        self.assertIsNotNone(pitch)
        self.assertLess(pitch, 22.0)

    def test_head_on_desk_with_face_still_high(self):
        kps = _blank_coco17()
        kps[NOSE] = [320, 268, 0.85]
        kps[LEFT_EYE] = [300, 260, 0.55]
        kps[RIGHT_EYE] = [340, 262, 0.55]
        kps[LEFT_SHOULDER] = [200, 270, 0.8]
        kps[RIGHT_SHOULDER] = [440, 272, 0.8]
        pitch = pitch_from_coco17(kps)
        self.assertIsNotNone(pitch)
        self.assertGreater(pitch, 50.0)


class QualityGateTests(unittest.TestCase):
    """Every rejected case must return no pitch at all, so a bad frame cannot
    push a track towards 睡岗."""

    def test_far_background_person_is_rejected(self):
        # Same slump geometry, but the whole body is only a few dozen pixels.
        kps = _blank_coco17()
        kps[NOSE] = [300, 104, 0.8]
        kps[LEFT_SHOULDER] = [292, 105, 0.8]
        kps[RIGHT_SHOULDER] = [308, 106, 0.8]
        self.assertIsNone(pitch_from_coco17(kps))

    def test_single_shoulder_without_hip_is_rejected(self):
        # Turned away: one shoulder puts the "neck" off to the side and the old
        # code happily read that as a desk slump.
        kps = _blank_coco17()
        kps[NOSE] = [320, 268, 0.8]
        kps[LEFT_SHOULDER] = [260, 270, 0.8]
        self.assertIsNone(pitch_from_coco17(kps))

    def test_single_shoulder_with_hip_is_accepted(self):
        kps = _blank_coco17()
        kps[NOSE] = [320, 268, 0.8]
        kps[LEFT_SHOULDER] = [260, 270, 0.8]
        kps[LEFT_HIP] = [255, 460, 0.8]
        result = compute_pitch(kps)
        self.assertTrue(result.valid)
        self.assertFalse(result.both_shoulders)
        self.assertTrue(result.has_hip)

    def test_lone_ear_without_hip_is_rejected(self):
        kps = _blank_coco17()
        kps[LEFT_EAR] = [320, 268, 0.6]
        kps[LEFT_SHOULDER] = [200, 270, 0.8]
        kps[RIGHT_SHOULDER] = [440, 272, 0.8]
        self.assertIsNone(pitch_from_coco17(kps))

    def test_low_mean_confidence_is_rejected(self):
        kps = _blank_coco17()
        kps[NOSE] = [320, 268, 0.26]
        kps[LEFT_SHOULDER] = [200, 270, 0.26]
        kps[RIGHT_SHOULDER] = [440, 272, 0.26]
        self.assertIsNone(pitch_from_coco17(kps))

    def test_desk_sleeper_reports_evidence(self):
        result = compute_pitch(_desk_sleeper())
        self.assertTrue(result.valid)
        self.assertGreater(result.pitch_deg, 60.0)
        self.assertTrue(result.has_hip)
        self.assertTrue(result.both_shoulders)
        self.assertGreater(result.scale_px, 24.0)
        # Head must be at or below the neck line for a real slump.
        self.assertLessEqual(result.head_above_neck_px, 0.10 * result.scale_px)


if __name__ == "__main__":
    unittest.main()
