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
        # 25 fps, head down for 800 ms then recover past the grace window.
        for i in range(20):
            last = update_temporal(state, 50.0, i * 40)
            self.assertNotEqual(last.label, FrameLabel.SLEEP)
        last = None
        for j in range(30):
            last = update_temporal(state, 10.0, 20 * 40 + j * 40)
        self.assertEqual(last.label, FrameLabel.UPRIGHT)
        self.assertEqual(last.head_down_frames, 0)

    def test_sustained_down_is_sleep(self):
        state = TemporalState()
        last = None
        frames = SLEEP_HOLD_MS // 40 + 8
        for i in range(frames):
            last = update_temporal(state, 50.0, i * 40)
        self.assertIsNotNone(last)
        self.assertEqual(last.label, FrameLabel.SLEEP)
        self.assertGreaterEqual(last.head_down_ms, SLEEP_HOLD_MS)

    def test_hysteresis_keeps_streak(self):
        state = TemporalState()
        update_temporal(state, 50.0, 0)
        mid = update_temporal(state, 28.0, 40)  # between recover(22) and down(32)
        self.assertEqual(mid.label, FrameLabel.BOW)
        self.assertEqual(state.head_down_frames, 2)

    def test_missing_pose_does_not_reset(self):
        state = TemporalState()
        update_temporal(state, 50.0, 0)
        update_temporal(state, 50.0, 40)
        gap = update_temporal(state, None, 80)
        self.assertEqual(gap.label, FrameLabel.BOW)
        self.assertEqual(state.head_down_frames, 2)

    def test_keyboard_bow_under_hold_does_not_sleep(self):
        state = TemporalState()
        last = None
        # 1.2s of mid pitch (look-down / type) then recover — must stay bow.
        for i in range(30):
            last = update_temporal(state, 40.0, i * 40)
        self.assertEqual(last.label, FrameLabel.BOW)
        self.assertLess(last.head_down_ms, SLEEP_HOLD_MS)
        for j in range(20):
            last = update_temporal(state, 8.0, 30 * 40 + j * 40)
        self.assertEqual(last.label, FrameLabel.UPRIGHT)

    def test_brief_recover_does_not_reset(self):
        state = TemporalState()
        update_temporal(state, 55.0, 0)
        update_temporal(state, 55.0, 40)
        flicker = update_temporal(state, 8.0, 80)
        self.assertEqual(flicker.label, FrameLabel.BOW)
        self.assertGreater(state.head_down_frames, 0)


if __name__ == "__main__":
    unittest.main()
