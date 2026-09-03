"""Head-drop score from COCO-17 pose. Same formulas as server/Analyzer/Core/SleepPose.h."""

from __future__ import annotations

import math
from dataclasses import dataclass
from typing import Sequence

NOSE = 0
LEFT_EYE = 1
RIGHT_EYE = 2
LEFT_EAR = 3
RIGHT_EAR = 4
LEFT_SHOULDER = 5
RIGHT_SHOULDER = 6
LEFT_HIP = 11
RIGHT_HIP = 12

MIN_KEYPOINT_CONF = 0.25
PITCH_DOWN_DEG = 32.0
PITCH_RECOVER_DEG = 22.0
# Keep gated upright frames below recover so the temporal machine stays UPRIGHT.
UPRIGHT_PITCH_CAP_DEG = 18.0
# Upright head-to-neck length vs torso (shoulder–hip) and vs shoulder width.
EXPECTED_HEAD_TO_TORSO = 0.42
EXPECTED_HEAD_TO_SHOULDER = 0.50
MIN_BODY_SCALE_PX = 24.0
HEAD_ABOVE_NECK_MIN_PX = 8.0

# Frame quality gates.
MIN_MEAN_KEYPOINT_CONF = 0.35
MAX_PITCH_JUMP_DEG = 45.0
MIN_POSE_KEYPOINTS = 8
MIN_PERSON_BOX_HEIGHT_RATIO = 0.18

# Geometry hard conditions.
MAX_HEAD_ABOVE_NECK_RATIO = 0.10
NO_HIP_ENTER_PENALTY_DEG = 6.0


@dataclass
class PitchResult:
    """Per-frame geometry output. `valid` False means the frame carries no usable
    pitch, which is not the same as "the person is upright"."""

    valid: bool = False
    pitch_deg: float = 0.0
    scale_px: float = 0.0
    head_x: float = 0.0
    head_y: float = 0.0
    mean_conf: float = 0.0
    has_hip: bool = False
    both_shoulders: bool = False
    frontal_face: bool = False
    head_above_neck_px: float = 0.0


def _as_xyz(point: Sequence[float]) -> tuple[float, float, float]:
    if len(point) < 2:
        raise ValueError("keypoint needs x,y")
    conf = float(point[2]) if len(point) > 2 else 1.0
    return float(point[0]), float(point[1]), conf


def _usable(point: Sequence[float] | None, min_conf: float) -> bool:
    if point is None or len(point) < 2:
        return False
    conf = float(point[2]) if len(point) > 2 else 1.0
    return conf >= min_conf


def _at(kps: Sequence[Sequence[float]], index: int) -> Sequence[float] | None:
    return kps[index] if len(kps) > index else None


def _midpoint(
    a: Sequence[float] | None,
    b: Sequence[float] | None,
    min_conf: float,
) -> tuple[float, float] | None:
    if not _usable(a, min_conf) or not _usable(b, min_conf):
        return None
    ax, ay, _ = _as_xyz(a)
    bx, by, _ = _as_xyz(b)
    return 0.5 * (ax + bx), 0.5 * (ay + by)


class _ConfAccumulator:
    def __init__(self) -> None:
        self.total = 0.0
        self.count = 0

    def add(self, *points: Sequence[float]) -> None:
        for point in points:
            self.total += _as_xyz(point)[2]
            self.count += 1

    def mean(self) -> float:
        return self.total / self.count if self.count else 0.0


def _pick_head(
    kps: Sequence[Sequence[float]],
    min_conf: float,
    conf: _ConfAccumulator,
) -> tuple[float, float, bool] | None:
    """Returns (x, y, strong). `strong` means nose, both eyes or both ears.

    A single eye/ear is only accepted with a real torso axis: a lone low-confidence
    ear used to be the cheapest way to fake a desk slump on a turned-away person.
    """
    if len(kps) <= NOSE:
        return None

    if _usable(kps[NOSE], min_conf):
        x, y, _ = _as_xyz(kps[NOSE])
        conf.add(kps[NOSE])
        return x, y, True

    eyes = _midpoint(_at(kps, LEFT_EYE), _at(kps, RIGHT_EYE), min_conf)
    if eyes:
        conf.add(kps[LEFT_EYE], kps[RIGHT_EYE])
        return eyes[0], eyes[1], True

    ears = _midpoint(_at(kps, LEFT_EAR), _at(kps, RIGHT_EAR), min_conf)
    if ears:
        conf.add(kps[LEFT_EAR], kps[RIGHT_EAR])
        return ears[0], ears[1], True

    for index in (LEFT_EYE, RIGHT_EYE, LEFT_EAR, RIGHT_EAR):
        point = _at(kps, index)
        if _usable(point, min_conf):
            x, y, _ = _as_xyz(point)
            conf.add(point)
            return x, y, False
    return None


def _pick_neck(
    kps: Sequence[Sequence[float]],
    min_conf: float,
    conf: _ConfAccumulator,
) -> tuple[float, float, bool] | None:
    """Returns (x, y, both_shoulders). One shoulder puts the neck off to one side,
    which silently defeats the upright gate, so callers pair it with a hip."""
    if len(kps) <= RIGHT_SHOULDER:
        return None

    both = _midpoint(kps[LEFT_SHOULDER], kps[RIGHT_SHOULDER], min_conf)
    if both:
        conf.add(kps[LEFT_SHOULDER], kps[RIGHT_SHOULDER])
        return both[0], both[1], True

    for index in (LEFT_SHOULDER, RIGHT_SHOULDER):
        point = _at(kps, index)
        if _usable(point, min_conf):
            x, y, _ = _as_xyz(point)
            conf.add(point)
            return x, y, False
    return None


def _pick_hip(
    kps: Sequence[Sequence[float]],
    min_conf: float,
    conf: _ConfAccumulator,
) -> tuple[float, float] | None:
    if len(kps) <= RIGHT_HIP:
        return None

    both = _midpoint(kps[LEFT_HIP], kps[RIGHT_HIP], min_conf)
    if both:
        conf.add(kps[LEFT_HIP], kps[RIGHT_HIP])
        return both

    for index in (LEFT_HIP, RIGHT_HIP):
        point = _at(kps, index)
        if _usable(point, min_conf):
            x, y, _ = _as_xyz(point)
            conf.add(point)
            return x, y
    return None


def _map_elev_to_pitch(elev: float, scale: float) -> float:
    ratio = elev / max(scale, 1e-3)
    ratio = max(-0.6, min(1.15, ratio))
    return (1.0 - ratio) * 90.0


def _eye_distance(kps: Sequence[Sequence[float]], min_conf: float) -> float:
    if len(kps) <= RIGHT_EYE:
        return 0.0
    if not _usable(kps[LEFT_EYE], min_conf) or not _usable(kps[RIGHT_EYE], min_conf):
        return 0.0
    lx, ly, _ = _as_xyz(kps[LEFT_EYE])
    rx, ry, _ = _as_xyz(kps[RIGHT_EYE])
    return math.hypot(rx - lx, ry - ly)


def _is_frontal_face(kps: Sequence[Sequence[float]], min_conf: float) -> bool:
    if len(kps) <= RIGHT_EYE:
        return False
    return (
        _usable(kps[NOSE], min_conf)
        and _usable(kps[LEFT_EYE], min_conf)
        and _usable(kps[RIGHT_EYE], min_conf)
        and _eye_distance(kps, min_conf) >= HEAD_ABOVE_NECK_MIN_PX
    )


def _is_profile_view(shoulder_w: float, torso_len: float) -> bool:
    return torso_len >= MIN_BODY_SCALE_PX and shoulder_w < 0.35 * torso_len


def _apply_upright_gate(
    pitch: float,
    head_above: float,
    scale: float,
    frontal_face: bool,
) -> float:
    """Cap the angle whenever the head still rides above the neck line.

    Desk sleep drops the head to or below the shoulders. A facing-camera head on a
    downward laptop webcam does not, however large the shoulder ruler gets.
    """
    if head_above > MAX_HEAD_ABOVE_NECK_RATIO * scale:
        return min(pitch, UPRIGHT_PITCH_CAP_DEG)
    if frontal_face and head_above >= HEAD_ABOVE_NECK_MIN_PX:
        return min(pitch, UPRIGHT_PITCH_CAP_DEG)
    return pitch


def compute_pitch(
    keypoints: Sequence[Sequence[float]],
    min_conf: float = MIN_KEYPOINT_CONF,
) -> PitchResult:
    """View-adaptive head-drop score in degrees (0 ≈ upright, ~90 ≈ head at the neck).

    Sitting frontally used to work because shoulder width is a stable ruler. In profile
    that width collapses; close-up downward webcams do the opposite and make a
    facing-camera head look like a slump. So:
      * torso-up from hip→shoulder when hips are visible (stable in profile)
      * scale = max(0.42×torso, 0.5×shoulder) so a thin shoulder line cannot explode
      * image-Y drop only in profile
      * the head must not still be riding above the neck line
      * one shoulder or a lone eye/ear is only trusted alongside a hip
    """
    conf = _ConfAccumulator()

    head = _pick_head(keypoints, min_conf, conf)
    neck = _pick_neck(keypoints, min_conf, conf)
    if head is None or neck is None:
        return PitchResult()

    hx, hy, head_strong = head
    nx, ny, both_shoulders = neck
    hip = _pick_hip(keypoints, min_conf, conf)
    has_hip = hip is not None

    if not both_shoulders and not has_hip:
        return PitchResult()
    if not head_strong and not has_hip:
        return PitchResult()

    mean_conf = conf.mean()
    if mean_conf < MIN_MEAN_KEYPOINT_CONF:
        return PitchResult()

    shoulder_w = 0.0
    if (
        len(keypoints) > RIGHT_SHOULDER
        and _usable(keypoints[LEFT_SHOULDER], min_conf)
        and _usable(keypoints[RIGHT_SHOULDER], min_conf)
    ):
        lsx, lsy, _ = _as_xyz(keypoints[LEFT_SHOULDER])
        rsx, rsy, _ = _as_xyz(keypoints[RIGHT_SHOULDER])
        shoulder_w = math.hypot(rsx - lsx, rsy - lsy)

    torso_len = 0.0
    ux = 0.0
    uy = 0.0
    if hip is not None:
        ux = nx - hip[0]
        uy = ny - hip[1]
        torso_len = math.hypot(ux, uy)

    if torso_len < MIN_BODY_SCALE_PX:
        # No usable hip axis: rotate the shoulder line 90° (old frontal fallback).
        if shoulder_w < MIN_BODY_SCALE_PX:
            return PitchResult()
        lsx, lsy, _ = _as_xyz(keypoints[LEFT_SHOULDER])
        rsx, rsy, _ = _as_xyz(keypoints[RIGHT_SHOULDER])
        ux = -(rsy - lsy)
        uy = rsx - lsx
        if uy > 0.0:
            ux = -ux
            uy = -uy
        u_norm = math.hypot(ux, uy)
        if u_norm < 1e-3:
            return PitchResult()
        ux /= u_norm
        uy /= u_norm
    else:
        ux /= torso_len
        uy /= torso_len

    scale = 0.0
    if torso_len >= MIN_BODY_SCALE_PX:
        scale = max(scale, EXPECTED_HEAD_TO_TORSO * torso_len)
    if shoulder_w >= MIN_BODY_SCALE_PX:
        scale = max(scale, EXPECTED_HEAD_TO_SHOULDER * shoulder_w)
    if scale < MIN_BODY_SCALE_PX:
        return PitchResult()

    elev = ux * (hx - nx) + uy * (hy - ny)
    pitch = _map_elev_to_pitch(elev, scale)
    # Image-Y drop helps profile. On a close frontal webcam it uses the huge
    # shoulder ruler and turns "looking at the camera" into 60°+.
    if _is_profile_view(shoulder_w, torso_len):
        pitch = max(pitch, _map_elev_to_pitch(-(hy - ny), scale))

    head_above = ny - hy
    frontal_face = _is_frontal_face(keypoints, min_conf)
    pitch = _apply_upright_gate(pitch, head_above, scale, frontal_face)

    return PitchResult(
        valid=True,
        pitch_deg=max(0.0, min(135.0, pitch)),
        scale_px=scale,
        head_x=hx,
        head_y=hy,
        mean_conf=mean_conf,
        has_hip=has_hip,
        both_shoulders=both_shoulders,
        frontal_face=frontal_face,
        head_above_neck_px=head_above,
    )


def pitch_from_coco17(
    keypoints: Sequence[Sequence[float]],
    min_conf: float = MIN_KEYPOINT_CONF,
) -> float | None:
    result = compute_pitch(keypoints, min_conf)
    return result.pitch_deg if result.valid else None


def try_compute_pitch_deg(
    nose: Sequence[float],
    left_shoulder: Sequence[float],
    right_shoulder: Sequence[float],
    min_conf: float = MIN_KEYPOINT_CONF,
    left_hip: Sequence[float] | None = None,
    right_hip: Sequence[float] | None = None,
) -> float | None:
    """Convenience wrapper used by unit tests (frontal 3-point and optional hips)."""
    kps: list[list[float]] = [[0.0, 0.0, 0.0] for _ in range(RIGHT_HIP + 1)]
    kps[NOSE] = list(nose)
    kps[LEFT_SHOULDER] = list(left_shoulder)
    kps[RIGHT_SHOULDER] = list(right_shoulder)
    if left_hip is not None:
        kps[LEFT_HIP] = list(left_hip)
    if right_hip is not None:
        kps[RIGHT_HIP] = list(right_hip)
    return pitch_from_coco17(kps, min_conf)
