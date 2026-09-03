"""Multi-frame bow vs sleep. Same state machine as SleepPose.h."""

from __future__ import annotations

from dataclasses import dataclass
from enum import Enum

from .geometry import PITCH_DOWN_DEG, PITCH_RECOVER_DEG

SLEEP_HOLD_MS = 3500
RECOVER_HOLD_MS = 450
MAX_MISSING_POSE_MS = 10000
MIN_CONFIRM_DOWN_FRAMES = 3
MAX_PITCH_RISE_DEG = 18.0
PITCH_ATTACK_ALPHA = 0.40
PITCH_RELEASE_ALPHA = 0.38


class FrameLabel(str, Enum):
    UPRIGHT = "upright"
    BOW = "bow"
    SLEEP = "sleep"


@dataclass
class TemporalState:
    head_down_since_ms: int = 0
    head_down_frames: int = 0
    last_pitch_deg: float = 0.0
    last_pitch_valid: bool = False
    smoothed_pitch_deg: float = 0.0
    has_smoothed: bool = False
    recover_since_ms: int = 0
    missing_since_ms: int = 0
    frozen_head_down_ms: int = 0
    confirm_down_frames: int = 0


@dataclass
class FrameDecision:
    label: FrameLabel
    head_down_ms: int
    head_down_frames: int
    pitch_deg: float | None


def _clear(state: TemporalState) -> None:
    state.head_down_frames = 0
    state.head_down_since_ms = 0
    state.recover_since_ms = 0
    state.missing_since_ms = 0
    state.frozen_head_down_ms = 0
    state.confirm_down_frames = 0


def update_temporal(
    state: TemporalState,
    pitch_deg: float | None,
    now_ms: int,
    *,
    down_deg: float = PITCH_DOWN_DEG,
    recover_deg: float = PITCH_RECOVER_DEG,
    hold_ms: int = SLEEP_HOLD_MS,
    recover_hold_ms: int = RECOVER_HOLD_MS,
    attack_alpha: float = PITCH_ATTACK_ALPHA,
    release_alpha: float = PITCH_RELEASE_ALPHA,
) -> FrameDecision:
    """Hysteresis + hold + recover grace + clothing-spike guards.

    After head-down is confirmed, missing pose continues the wall clock
    (face-on-desk often loses the nose). Long missing clears the streak.
    """
    logic_pitch: float | None = None
    if pitch_deg is not None:
        raw = float(pitch_deg)
        if state.has_smoothed and raw > state.smoothed_pitch_deg + MAX_PITCH_RISE_DEG:
            raw = state.smoothed_pitch_deg + MAX_PITCH_RISE_DEG
        if not state.has_smoothed:
            state.smoothed_pitch_deg = raw
            state.has_smoothed = True
        else:
            alpha = attack_alpha if raw >= state.smoothed_pitch_deg else release_alpha
            alpha = min(1.0, max(0.05, float(alpha)))
            state.smoothed_pitch_deg = alpha * raw + (1.0 - alpha) * state.smoothed_pitch_deg
        state.last_pitch_deg = float(pitch_deg)
        state.last_pitch_valid = True
        state.missing_since_ms = 0
        logic_pitch = state.smoothed_pitch_deg

    if logic_pitch is None:
        if state.head_down_frames <= 0:
            _clear(state)
            return FrameDecision(FrameLabel.UPRIGHT, 0, 0, None)
        if state.missing_since_ms <= 0:
            state.missing_since_ms = now_ms
        if (now_ms - state.missing_since_ms) >= MAX_MISSING_POSE_MS:
            _clear(state)
            return FrameDecision(FrameLabel.UPRIGHT, 0, 0, None)
        duration = max(0, now_ms - state.head_down_since_ms)
        state.frozen_head_down_ms = duration
        label = FrameLabel.SLEEP if duration >= hold_ms else FrameLabel.BOW
        return FrameDecision(label, duration, state.head_down_frames, None)

    down = (
        logic_pitch >= recover_deg
        if state.head_down_frames > 0
        else logic_pitch >= down_deg
    )
    if down:
        state.recover_since_ms = 0
        if state.head_down_frames == 0:
            state.confirm_down_frames += 1
            if state.confirm_down_frames < MIN_CONFIRM_DOWN_FRAMES:
                return FrameDecision(FrameLabel.UPRIGHT, 0, 0, logic_pitch)
            state.head_down_since_ms = now_ms
        state.head_down_frames += 1
        duration = max(0, now_ms - state.head_down_since_ms)
        state.frozen_head_down_ms = duration
        label = FrameLabel.SLEEP if duration >= hold_ms else FrameLabel.BOW
        return FrameDecision(label, duration, state.head_down_frames, logic_pitch)

    state.confirm_down_frames = 0
    if state.head_down_frames > 0:
        if state.recover_since_ms <= 0:
            state.recover_since_ms = now_ms
        if recover_hold_ms > 0 and (now_ms - state.recover_since_ms) < recover_hold_ms:
            duration = max(0, now_ms - state.head_down_since_ms)
            label = FrameLabel.SLEEP if duration >= hold_ms else FrameLabel.BOW
            return FrameDecision(label, duration, state.head_down_frames, logic_pitch)

    _clear(state)
    return FrameDecision(FrameLabel.UPRIGHT, 0, 0, logic_pitch)
