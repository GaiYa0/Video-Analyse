import unittest

from sleep_pose.temporal import (
    SLEEP_HOLD_MS,
    FrameLabel,
    TemporalState,
    update_temporal,
)


class TemporalTests(unittest.TestCase):
    def test_short_bow_does_not_alarm(self):
        state = TemporalState()
        last = None
        for i in range(20):
            last = update_temporal(state, 55.0, i * 40)
            self.assertNotEqual(last.label, FrameLabel.SLEEP)
        for j in range(30):
            last = update_temporal(state, 10.0, 20 * 40 + j * 40)
        self.assertEqual(last.label, FrameLabel.UPRIGHT)
        self.assertEqual(last.head_down_frames, 0)

    def test_sustained_down_is_sleep(self):
        state = TemporalState()
        last = None
        frames = SLEEP_HOLD_MS // 40 + 20
        for i in range(frames):
            last = update_temporal(state, 55.0, i * 40)
        self.assertEqual(last.label, FrameLabel.SLEEP)
        self.assertGreaterEqual(last.head_down_ms, SLEEP_HOLD_MS)

    def test_hysteresis_keeps_streak(self):
        state = TemporalState()
        for i in range(4):
            update_temporal(state, 55.0, i * 40)
        mid = update_temporal(state, 30.0, 4 * 40)  # between recover(26) and down(38)
        self.assertEqual(mid.label, FrameLabel.BOW)
        self.assertGreaterEqual(state.head_down_frames, 2)

    def test_missing_pose_continues_after_confirm(self):
        state = TemporalState()
        for i in range(4):
            update_temporal(state, 55.0, i * 40)
        # Face buried: missing keypoints should keep counting toward sleep.
        last = None
        for j in range(90):
            last = update_temporal(state, None, 4 * 40 + j * 40)
        self.assertEqual(last.label, FrameLabel.SLEEP)

    def test_clothing_spike_needs_confirm_frames(self):
        state = TemporalState()
        for i in range(2):
            last = update_temporal(state, 70.0, i * 40)
            self.assertEqual(last.label, FrameLabel.UPRIGHT)
            self.assertEqual(last.head_down_ms, 0)

    def test_keyboard_bow_under_hold_does_not_sleep(self):
        state = TemporalState()
        last = None
        for i in range(30):
            last = update_temporal(state, 50.0, i * 40)
        self.assertEqual(last.label, FrameLabel.BOW)
        self.assertLess(last.head_down_ms, SLEEP_HOLD_MS)
        for j in range(20):
            last = update_temporal(state, 8.0, 30 * 40 + j * 40)
        self.assertEqual(last.label, FrameLabel.UPRIGHT)

    def test_brief_recover_does_not_reset(self):
        state = TemporalState()
        for i in range(4):
            update_temporal(state, 55.0, i * 40)
        flicker = update_temporal(state, 8.0, 4 * 40)
        self.assertEqual(flicker.label, FrameLabel.BOW)
        self.assertGreater(state.head_down_frames, 0)


if __name__ == "__main__":
    unittest.main()
