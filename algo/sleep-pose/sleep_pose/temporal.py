"""Multi-frame bow vs sleep. Same state machine as SleepPose.h."""

from __future__ import annotations

from dataclasses import dataclass
from enum import Enum

from .geometry import PITCH_DOWN_DEG, PITCH_RECOVER_DEG

SLEEP_HOLD_MS = 3000


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


@dataclass
class FrameDecision:
    label: FrameLabel
    head_down_ms: int
    head_down_frames: int
    pitch_deg: float | None


def update_temporal(
    state: TemporalState,
    pitch_deg: float | None,
    now_ms: int,
    *,
    down_deg: float = PITCH_DOWN_DEG,
    recover_deg: float = PITCH_RECOVER_DEG,
    hold_ms: int = SLEEP_HOLD_MS,
) -> FrameDecision:
    """Hysteresis + hold time.

    Bow: pitch stays above threshold for less than hold_ms, then recovers.
    Sleep: pitch stays down for >= hold_ms.
    A missing pose this frame does not reset an active head-down streak.
    """
    if pitch_deg is None:
        duration = 0
        if state.head_down_frames > 0 and state.head_down_since_ms > 0:
            duration = max(0, now_ms - state.head_down_since_ms)
        label = FrameLabel.SLEEP if duration >= hold_ms else (
            FrameLabel.BOW if state.head_down_frames > 0 else FrameLabel.UPRIGHT
        )
        return FrameDecision(label, duration, state.head_down_frames, None)

    state.last_pitch_deg = float(pitch_deg)
    state.last_pitch_valid = True
    down = (
        pitch_deg >= recover_deg
        if state.head_down_frames > 0
        else pitch_deg >= down_deg
    )
    if down:
        if state.head_down_frames == 0:
            state.head_down_since_ms = now_ms
        state.head_down_frames += 1
        duration = max(0, now_ms - state.head_down_since_ms)
        label = FrameLabel.SLEEP if duration >= hold_ms else FrameLabel.BOW
        return FrameDecision(label, duration, state.head_down_frames, pitch_deg)

    state.head_down_frames = 0
    state.head_down_since_ms = 0
    return FrameDecision(FrameLabel.UPRIGHT, 0, 0, pitch_deg)
