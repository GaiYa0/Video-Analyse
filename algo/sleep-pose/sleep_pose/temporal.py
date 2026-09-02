"""Multi-frame bow vs sleep. Same state machine as SleepPose.h."""

from __future__ import annotations

from dataclasses import dataclass
from enum import Enum

from .geometry import PITCH_DOWN_DEG, PITCH_RECOVER_DEG

SLEEP_HOLD_MS = 2500
RECOVER_HOLD_MS = 400
PITCH_ATTACK_ALPHA = 0.55
PITCH_RELEASE_ALPHA = 0.28


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
    recover_hold_ms: int = RECOVER_HOLD_MS,
    attack_alpha: float = PITCH_ATTACK_ALPHA,
    release_alpha: float = PITCH_RELEASE_ALPHA,
) -> FrameDecision:
    """Hysteresis + hold time + brief-recover grace.

    Bow: pitch stays above threshold for less than hold_ms, then recovers.
    Sleep: pitch stays down for >= hold_ms.
    A missing pose this frame does not reset an active head-down streak.
    Side-view jitter that dips below recover for < recover_hold_ms also keeps the streak.
    """
    logic_pitch: float | None = None
    if pitch_deg is not None:
        raw = float(pitch_deg)
        if not state.has_smoothed:
            state.smoothed_pitch_deg = raw
            state.has_smoothed = True
        else:
            alpha = attack_alpha if raw >= state.smoothed_pitch_deg else release_alpha
            alpha = min(1.0, max(0.05, float(alpha)))
            state.smoothed_pitch_deg = alpha * raw + (1.0 - alpha) * state.smoothed_pitch_deg
        state.last_pitch_deg = raw
        state.last_pitch_valid = True
        logic_pitch = state.smoothed_pitch_deg

    if logic_pitch is None:
        duration = 0
        if state.head_down_frames > 0 and state.head_down_since_ms > 0:
            duration = max(0, now_ms - state.head_down_since_ms)
        label = FrameLabel.SLEEP if duration >= hold_ms else (
            FrameLabel.BOW if state.head_down_frames > 0 else FrameLabel.UPRIGHT
        )
        return FrameDecision(label, duration, state.head_down_frames, None)

    down = (
        logic_pitch >= recover_deg
        if state.head_down_frames > 0
        else logic_pitch >= down_deg
    )
    if down:
        state.recover_since_ms = 0
        if state.head_down_frames == 0:
            state.head_down_since_ms = now_ms
        state.head_down_frames += 1
        duration = max(0, now_ms - state.head_down_since_ms)
        label = FrameLabel.SLEEP if duration >= hold_ms else FrameLabel.BOW
        return FrameDecision(label, duration, state.head_down_frames, logic_pitch)

    if state.head_down_frames > 0:
        if state.recover_since_ms <= 0:
            state.recover_since_ms = now_ms
        if recover_hold_ms > 0 and (now_ms - state.recover_since_ms) < recover_hold_ms:
            duration = max(0, now_ms - state.head_down_since_ms)
            label = FrameLabel.SLEEP if duration >= hold_ms else FrameLabel.BOW
            return FrameDecision(label, duration, state.head_down_frames, logic_pitch)

    state.head_down_frames = 0
    state.head_down_since_ms = 0
    state.recover_since_ms = 0
    return FrameDecision(FrameLabel.UPRIGHT, 0, 0, logic_pitch)
