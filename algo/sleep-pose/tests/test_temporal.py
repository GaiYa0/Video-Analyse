import unittest

from sleep_pose.temporal import (
    SLEEP_HOLD_MS,
    FrameInput,
    FrameLabel,
    TemporalState,
    update_temporal,
)

FRAME_MS = 40
SCALE_PX = 100.0


def _frame(pitch, *, head_x=0.0, head_y=0.0, has_hip=True, scale_px=SCALE_PX):
    return FrameInput(
        valid=True,
        pitch_deg=pitch,
        head_x=head_x,
        head_y=head_y,
        scale_px=scale_px,
        has_hip=has_hip,
    )


def _run(state, frames, *, start_ms=0, **kwargs):
    """frames is a sequence of FrameInput|float|None, one per 40ms tick."""
    last = None
    for i, frame in enumerate(frames):
        last = update_temporal(state, frame, start_ms + i * FRAME_MS, **kwargs)
    return last


class TemporalTests(unittest.TestCase):
    def test_short_bow_does_not_alarm(self):
        state = TemporalState()
        last = _run(state, [50.0] * 20)
        self.assertNotEqual(last.label, FrameLabel.SLEEP)
        last = _run(state, [10.0] * 30, start_ms=20 * FRAME_MS)
        self.assertEqual(last.label, FrameLabel.UPRIGHT)
        self.assertEqual(last.head_down_frames, 0)

    def test_sustained_down_is_sleep(self):
        state = TemporalState()
        frames = SLEEP_HOLD_MS // FRAME_MS + 8
        last = _run(state, [_frame(70.0)] * frames)
        self.assertEqual(last.label, FrameLabel.SLEEP)
        self.assertGreaterEqual(last.head_down_ms, SLEEP_HOLD_MS)

    def test_hysteresis_keeps_streak(self):
        state = TemporalState()
        update_temporal(state, 50.0, 0)
        mid = update_temporal(state, 28.0, FRAME_MS)  # between recover(22) and down(32)
        self.assertEqual(mid.label, FrameLabel.BOW)
        self.assertEqual(state.head_down_frames, 2)

    def test_missing_pose_does_not_reset(self):
        state = TemporalState()
        update_temporal(state, 50.0, 0)
        update_temporal(state, 50.0, FRAME_MS)
        gap = update_temporal(state, None, 2 * FRAME_MS)
        self.assertEqual(gap.label, FrameLabel.BOW)
        self.assertEqual(state.head_down_frames, 2)

    def test_keyboard_bow_under_hold_does_not_sleep(self):
        state = TemporalState()
        last = _run(state, [40.0] * 30)
        self.assertEqual(last.label, FrameLabel.BOW)
        self.assertLess(last.head_down_ms, SLEEP_HOLD_MS)
        last = _run(state, [8.0] * 20, start_ms=30 * FRAME_MS)
        self.assertEqual(last.label, FrameLabel.UPRIGHT)

    def test_brief_recover_does_not_reset(self):
        state = TemporalState()
        update_temporal(state, 55.0, 0)
        update_temporal(state, 55.0, FRAME_MS)
        flicker = update_temporal(state, 8.0, 2 * FRAME_MS)
        self.assertEqual(flicker.label, FrameLabel.BOW)
        self.assertGreater(state.head_down_frames, 0)


class FalsePositiveTests(unittest.TestCase):
    """One case per false-positive class seen on the workstation camera."""

    def test_typing_oscillation_never_sleeps(self):
        # 20s of reading: angle dips in and out of the band and the head keeps moving.
        state = TemporalState()
        frames = []
        for i in range(500):
            pitch = 38.0 if (i % 10) < 7 else 18.0
            frames.append(_frame(pitch, head_x=(i % 10) * 4.0, head_y=(i % 6) * 3.0))
        last = _run(state, frames)
        self.assertNotEqual(last.label, FrameLabel.SLEEP)

    def test_phone_deep_but_moving_never_sleeps(self):
        # Deep enough angle, but the head wanders far beyond the drift budget.
        state = TemporalState()
        frames = [_frame(70.0, head_x=(i % 40) * 3.0) for i in range(400)]
        last = _run(state, frames)
        self.assertGreaterEqual(last.head_down_ms, SLEEP_HOLD_MS)
        self.assertGreater(last.head_drift_px, 0.45 * SCALE_PX)
        self.assertEqual(last.label, FrameLabel.BOW)

    def test_shallow_bow_never_reaches_peak(self):
        # Sustained 36°: over the entry threshold, never deep enough to be a slump.
        state = TemporalState()
        last = _run(state, [_frame(36.0)] * 400)
        self.assertGreaterEqual(last.head_down_ms, SLEEP_HOLD_MS)
        self.assertLess(last.peak_pitch_deg, 45.0)
        self.assertEqual(last.label, FrameLabel.BOW)

    def test_person_leaves_mid_streak_resets(self):
        # 1s head down, then the pose is gone for 9s. The old clock kept running.
        state = TemporalState()
        _run(state, [_frame(70.0)] * 25)
        last = _run(state, [None] * 225, start_ms=25 * FRAME_MS)
        self.assertEqual(last.label, FrameLabel.UPRIGHT)
        self.assertEqual(last.head_down_ms, 0)

    def test_no_hip_needs_steeper_entry(self):
        # Desk hides the hips, so the rotated-shoulder ruler must clear a higher bar.
        state = TemporalState()
        last = _run(state, [_frame(34.0, has_hip=False)] * 30)
        self.assertEqual(last.label, FrameLabel.UPRIGHT)

        state = TemporalState()
        last = _run(state, [_frame(34.0, has_hip=True)] * 30)
        self.assertEqual(last.label, FrameLabel.BOW)

    def test_keypoint_teleport_is_not_believed(self):
        state = TemporalState()
        update_temporal(state, _frame(5.0), 0)
        jump = update_temporal(state, _frame(95.0), FRAME_MS)
        self.assertEqual(jump.label, FrameLabel.UPRIGHT)
        self.assertEqual(state.head_down_frames, 0)


class TruePositiveTests(unittest.TestCase):
    def test_still_desk_sleep_fires_at_hold(self):
        state = TemporalState()
        frames = SLEEP_HOLD_MS // FRAME_MS + 5
        # Head parked with a couple of px of pose jitter.
        seq = [_frame(85.0, head_x=(i % 3), head_y=(i % 2)) for i in range(frames)]
        last = _run(state, seq)
        self.assertEqual(last.label, FrameLabel.SLEEP)
        self.assertGreaterEqual(last.head_down_ms, SLEEP_HOLD_MS)
        self.assertLess(last.head_down_ms, SLEEP_HOLD_MS + 500)
        self.assertGreaterEqual(last.down_ratio, 0.80)
        self.assertGreaterEqual(last.peak_pitch_deg, 45.0)

    def test_short_nap_under_hold_does_not_fire(self):
        state = TemporalState()
        frames = 8000 // FRAME_MS
        last = _run(state, [_frame(85.0)] * frames)
        self.assertEqual(last.label, FrameLabel.BOW)
        self.assertLess(last.head_down_ms, SLEEP_HOLD_MS)

    def test_brief_pose_dropout_survives(self):
        # Losing a frame or two mid-nap must not restart the 10 second clock.
        state = TemporalState()
        seq = []
        for i in range(SLEEP_HOLD_MS // FRAME_MS + 20):
            seq.append(None if i % 25 == 0 and i > 0 else _frame(85.0))
        last = _run(state, seq)
        self.assertEqual(last.label, FrameLabel.SLEEP)


if __name__ == "__main__":
    unittest.main()
