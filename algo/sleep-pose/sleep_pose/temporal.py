"""Multi-frame bow vs sleep. Same state machine as SleepPose.h."""

from __future__ import annotations

import math
from dataclasses import dataclass
from enum import Enum

from .geometry import (
    MAX_PITCH_JUMP_DEG,
    NO_HIP_ENTER_PENALTY_DEG,
    PITCH_DOWN_DEG,
    PITCH_RECOVER_DEG,
    PitchResult,
)

SLEEP_HOLD_MS = 10000
RECOVER_HOLD_MS = 600
PITCH_ATTACK_ALPHA = 0.55
PITCH_RELEASE_ALPHA = 0.28

# Window evidence required on top of the hold time.
SLEEP_MIN_DOWN_RATIO = 0.80
SLEEP_MAX_POSE_GAP_MS = 1200
SLEEP_MAX_GAP_RATIO = 0.50
SLEEP_MIN_VALID_FRAMES = 12
SLEEP_PEAK_PITCH_DEG = 45.0
SLEEP_MAX_HEAD_DRIFT_RATIO = 0.45
MAX_FRAME_DELTA_MS = 1000


class FrameLabel(str, Enum):
    UPRIGHT = "upright"
    BOW = "bow"
    SLEEP = "sleep"


@dataclass
class FrameInput:
    """What update_temporal consumes each frame."""

    valid: bool = False
    pitch_deg: float = 0.0
    head_x: float = 0.0
    head_y: float = 0.0
    scale_px: float = 0.0
    has_hip: bool = False

    @classmethod
    def from_pitch_result(cls, result: PitchResult | None) -> "FrameInput":
        if result is None or not result.valid:
            return cls(valid=False)
        return cls(
            valid=True,
            pitch_deg=result.pitch_deg,
            head_x=result.head_x,
            head_y=result.head_y,
            scale_px=result.scale_px,
            has_hip=result.has_hip,
        )


@dataclass
class TemporalState:
    head_down_since_ms: int = 0
    head_down_frames: int = 0
    last_pitch_deg: float = 0.0
    last_pitch_valid: bool = False
    smoothed_pitch_deg: float = 0.0
    has_smoothed: bool = False
    recover_since_ms: int = 0
    last_update_ms: int = 0
    last_valid_ms: int = 0
    down_ms: int = 0
    not_down_ms: int = 0
    gap_ms: int = 0
    valid_frames: int = 0
    peak_pitch_deg: float = 0.0
    head_anchor_x: float = 0.0
    head_anchor_y: float = 0.0
    anchor_scale_px: float = 0.0
    max_head_drift_px: float = 0.0
    has_anchor: bool = False


@dataclass
class FrameDecision:
    label: FrameLabel
    head_down_ms: int
    head_down_frames: int
    pitch_deg: float | None
    valid_frames: int = 0
    down_ratio: float = 1.0
    gap_ratio: float = 0.0
    peak_pitch_deg: float = 0.0
    head_drift_px: float = 0.0


def _reset_streak(state: TemporalState) -> None:
    state.head_down_frames = 0
    state.head_down_since_ms = 0
    state.recover_since_ms = 0
    state.down_ms = 0
    state.not_down_ms = 0
    state.gap_ms = 0
    state.valid_frames = 0
    state.peak_pitch_deg = 0.0
    state.max_head_drift_px = 0.0
    state.has_anchor = False
    state.anchor_scale_px = 0.0


def _down_ratio(state: TemporalState) -> float:
    accounted = state.down_ms + state.not_down_ms
    return state.down_ms / accounted if accounted > 0 else 1.0


def _gap_ratio(state: TemporalState, head_down_ms: int) -> float:
    return state.gap_ms / head_down_ms if head_down_ms > 0 else 0.0


def _decision(
    state: TemporalState,
    label: FrameLabel,
    head_down_ms: int,
    pitch: float | None,
) -> FrameDecision:
    return FrameDecision(
        label=label,
        head_down_ms=head_down_ms,
        head_down_frames=state.head_down_frames,
        pitch_deg=pitch,
        valid_frames=state.valid_frames,
        down_ratio=_down_ratio(state),
        gap_ratio=_gap_ratio(state, head_down_ms),
        peak_pitch_deg=state.peak_pitch_deg,
        head_drift_px=state.max_head_drift_px,
    )


def _sleep_evidence_satisfied(state: TemporalState, head_down_ms: int, hold_ms: int) -> bool:
    """Head-down long enough is necessary but not sufficient.

    Reading, typing and phone use all keep the head low; they differ in duty cycle,
    in how deep the head actually goes, and in how much it drifts.
    """
    if head_down_ms < hold_ms:
        return False
    if state.valid_frames < SLEEP_MIN_VALID_FRAMES:
        return False
    if _down_ratio(state) < SLEEP_MIN_DOWN_RATIO:
        return False
    if _gap_ratio(state, head_down_ms) > SLEEP_MAX_GAP_RATIO:
        return False
    if state.peak_pitch_deg < SLEEP_PEAK_PITCH_DEG:
        return False
    if (
        state.has_anchor
        and state.anchor_scale_px > 0.0
        and state.max_head_drift_px > SLEEP_MAX_HEAD_DRIFT_RATIO * state.anchor_scale_px
    ):
        return False
    return True


def _coerce_frame(frame: FrameInput | PitchResult | float | int | None) -> FrameInput:
    if frame is None:
        return FrameInput(valid=False)
    if isinstance(frame, FrameInput):
        return frame
    if isinstance(frame, PitchResult):
        return FrameInput.from_pitch_result(frame)
    # Bare angle: no head position to judge stillness by, so assume a parked head
    # and a visible torso. Used by threshold-only tests.
    return FrameInput(valid=True, pitch_deg=float(frame), scale_px=100.0, has_hip=True)


def update_temporal(
    state: TemporalState,
    frame: FrameInput | PitchResult | float | int | None,
    now_ms: int,
    *,
    down_deg: float = PITCH_DOWN_DEG,
    recover_deg: float = PITCH_RECOVER_DEG,
    hold_ms: int = SLEEP_HOLD_MS,
    recover_hold_ms: int = RECOVER_HOLD_MS,
    attack_alpha: float = PITCH_ATTACK_ALPHA,
    release_alpha: float = PITCH_RELEASE_ALPHA,
) -> FrameDecision:
    """Hysteresis + hold time + window evidence.

    Bow: the head is down but the window does not yet prove 睡岗.
    Sleep: down for >= hold_ms with enough duty cycle, depth, stillness and real frames.
    Losing the pose no longer lets the clock run on unopposed.
    """
    data = _coerce_frame(frame)

    delta_ms = 0
    if state.last_update_ms > 0:
        delta_ms = max(0, min(MAX_FRAME_DELTA_MS, now_ms - state.last_update_ms))
    state.last_update_ms = now_ms

    if not data.valid:
        if state.head_down_frames <= 0:
            return _decision(state, FrameLabel.UPRIGHT, 0, None)

        # No pitch means no evidence. Letting the clock run here is how a person who
        # already walked away used to reach the hold time.
        continuous_gap_ms = (
            now_ms - state.last_valid_ms
            if state.last_valid_ms > 0
            else now_ms - state.head_down_since_ms
        )
        state.gap_ms += delta_ms
        head_down_ms = max(0, now_ms - state.head_down_since_ms)

        # The ratio only means something once the missing time is itself substantial;
        # on a two-frame streak a single dropped frame is already half the window.
        starved = state.gap_ms > SLEEP_MAX_POSE_GAP_MS and _gap_ratio(state, head_down_ms) > SLEEP_MAX_GAP_RATIO
        if continuous_gap_ms > SLEEP_MAX_POSE_GAP_MS or starved:
            _reset_streak(state)
            return _decision(state, FrameLabel.UPRIGHT, 0, None)

        label = (
            FrameLabel.SLEEP
            if _sleep_evidence_satisfied(state, head_down_ms, hold_ms)
            else FrameLabel.BOW
        )
        return _decision(state, label, head_down_ms, None)

    raw = float(data.pitch_deg)
    if state.last_pitch_valid and abs(raw - state.last_pitch_deg) > MAX_PITCH_JUMP_DEG:
        # Keypoints teleported. Treat the frame as missing rather than believing it.
        state.last_pitch_valid = False
        if state.head_down_frames <= 0:
            return _decision(state, FrameLabel.UPRIGHT, 0, None)
        state.gap_ms += delta_ms
        head_down_ms = max(0, now_ms - state.head_down_since_ms)
        label = (
            FrameLabel.SLEEP
            if _sleep_evidence_satisfied(state, head_down_ms, hold_ms)
            else FrameLabel.BOW
        )
        return _decision(state, label, head_down_ms, None)

    if not state.has_smoothed:
        state.smoothed_pitch_deg = raw
        state.has_smoothed = True
    else:
        alpha = attack_alpha if raw >= state.smoothed_pitch_deg else release_alpha
        alpha = min(1.0, max(0.05, float(alpha)))
        state.smoothed_pitch_deg = alpha * raw + (1.0 - alpha) * state.smoothed_pitch_deg
    state.last_pitch_deg = raw
    state.last_pitch_valid = True
    state.last_valid_ms = now_ms

    logic_pitch = state.smoothed_pitch_deg
    enter_deg = down_deg if data.has_hip else down_deg + NO_HIP_ENTER_PENALTY_DEG
    down = logic_pitch >= recover_deg if state.head_down_frames > 0 else logic_pitch >= enter_deg

    if down:
        if state.head_down_frames == 0:
            _reset_streak(state)
            state.head_down_since_ms = now_ms
            state.head_anchor_x = data.head_x
            state.head_anchor_y = data.head_y
            state.anchor_scale_px = data.scale_px
            state.has_anchor = True
        state.recover_since_ms = 0
        state.head_down_frames += 1
        state.valid_frames += 1
        state.down_ms += delta_ms
        state.peak_pitch_deg = max(state.peak_pitch_deg, logic_pitch)
        if state.has_anchor:
            drift = math.hypot(data.head_x - state.head_anchor_x, data.head_y - state.head_anchor_y)
            state.max_head_drift_px = max(state.max_head_drift_px, drift)

        head_down_ms = max(0, now_ms - state.head_down_since_ms)
        label = (
            FrameLabel.SLEEP
            if _sleep_evidence_satisfied(state, head_down_ms, hold_ms)
            else FrameLabel.BOW
        )
        return _decision(state, label, head_down_ms, logic_pitch)

    if state.head_down_frames > 0:
        if state.recover_since_ms <= 0:
            state.recover_since_ms = now_ms
        if recover_hold_ms > 0 and (now_ms - state.recover_since_ms) < recover_hold_ms:
            state.valid_frames += 1
            state.not_down_ms += delta_ms
            head_down_ms = max(0, now_ms - state.head_down_since_ms)
            label = (
                FrameLabel.SLEEP
                if _sleep_evidence_satisfied(state, head_down_ms, hold_ms)
                else FrameLabel.BOW
            )
            return _decision(state, label, head_down_ms, logic_pitch)

    _reset_streak(state)
    return _decision(state, FrameLabel.UPRIGHT, 0, logic_pitch)
