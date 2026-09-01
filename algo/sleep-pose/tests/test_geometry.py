import math
import unittest

from sleep_pose.geometry import try_compute_pitch_deg


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


if __name__ == "__main__":
    unittest.main()
