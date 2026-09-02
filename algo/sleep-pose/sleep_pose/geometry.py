"""Head-drop score from COCO-17 pose. Same formulas as server/Analyzer/Core/SleepPose.h."""

from __future__ import annotations

import math
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
MIN_BODY_SCALE_PX = 8.0
HEAD_ABOVE_NECK_MIN_PX = 8.0
HEAD_ABOVE_NECK_RATIO = 0.20


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


def _first_usable(points: Sequence[Sequence[float] | None], min_conf: float) -> tuple[float, float] | None:
    for point in points:
        if _usable(point, min_conf):
            x, y, _ = _as_xyz(point)
            return x, y
    return None


def _pick_head(kps: Sequence[Sequence[float]], min_conf: float) -> tuple[float, float] | None:
    head_conf = min_conf * 0.7
    if len(kps) <= NOSE:
        return None
    if _usable(kps[NOSE], min_conf):
        x, y, _ = _as_xyz(kps[NOSE])
        return x, y
    eyes = _midpoint(
        kps[LEFT_EYE] if len(kps) > LEFT_EYE else None,
        kps[RIGHT_EYE] if len(kps) > RIGHT_EYE else None,
        head_conf,
    )
    if eyes:
        return eyes
    found = _first_usable(
        [
            kps[LEFT_EYE] if len(kps) > LEFT_EYE else None,
            kps[RIGHT_EYE] if len(kps) > RIGHT_EYE else None,
        ],
        head_conf,
    )
    if found:
        return found
    ears = _midpoint(
        kps[LEFT_EAR] if len(kps) > LEFT_EAR else None,
        kps[RIGHT_EAR] if len(kps) > RIGHT_EAR else None,
        head_conf,
    )
    if ears:
        return ears
    return _first_usable(
        [
            kps[LEFT_EAR] if len(kps) > LEFT_EAR else None,
            kps[RIGHT_EAR] if len(kps) > RIGHT_EAR else None,
        ],
        head_conf,
    )


def _pick_neck(kps: Sequence[Sequence[float]], min_conf: float) -> tuple[float, float] | None:
    if len(kps) <= RIGHT_SHOULDER:
        return None
    both = _midpoint(kps[LEFT_SHOULDER], kps[RIGHT_SHOULDER], min_conf)
    if both:
        return both
    return _first_usable([kps[LEFT_SHOULDER], kps[RIGHT_SHOULDER]], min_conf)


def _pick_hip(kps: Sequence[Sequence[float]], min_conf: float) -> tuple[float, float] | None:
    if len(kps) <= RIGHT_HIP:
        return None
    both = _midpoint(kps[LEFT_HIP], kps[RIGHT_HIP], min_conf)
    if both:
        return both
    return _first_usable([kps[LEFT_HIP], kps[RIGHT_HIP]], min_conf)


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
        and _eye_distance(kps, min_conf) >= MIN_BODY_SCALE_PX
    )


def _is_profile_view(shoulder_w: float, torso_len: float) -> bool:
    return torso_len >= MIN_BODY_SCALE_PX and shoulder_w < 0.35 * torso_len


def _apply_upright_gate(
    pitch: float,
    head_y: float,
    neck_y: float,
    scale: float,
    frontal_face: bool,
) -> float:
    """Laptop webcams look down: shoulder width explodes and a facing-camera
    head still scores like a desk slump. If the head is clearly above the neck,
    or both eyes+nose are visible, this is not 睡岗.
    """
    head_above = neck_y - head_y
    if frontal_face and head_above >= HEAD_ABOVE_NECK_MIN_PX:
        return min(pitch, UPRIGHT_PITCH_CAP_DEG)
    if head_above >= max(HEAD_ABOVE_NECK_MIN_PX, HEAD_ABOVE_NECK_RATIO * scale):
        return min(pitch, UPRIGHT_PITCH_CAP_DEG)
    return pitch


def pitch_from_coco17(
    keypoints: Sequence[Sequence[float]],
    min_conf: float = MIN_KEYPOINT_CONF,
) -> float | None:
    """View-adaptive head-drop score in degrees (0 ≈ upright, ~90 ≈ head at the neck).

    Frontal sitting used to work because shoulder width is a stable ruler. In profile
    that width collapses, so the old 0.5×shoulder-width pitch jumped around.
    Close-up downward webcams do the opposite: shoulders explode and a facing-camera
    head looks like a slump. Now:
      * torso-up from hip→shoulder when hips are visible (stable in profile)
      * scale = max(0.42×torso, 0.5×shoulder) so a thin shoulder line cannot explode
      * image-Y drop only in profile
      * upright gate if the head is still above the neck, or both eyes+nose are visible
      * head may fall back to eyes/ears when the nose is occluded on the desk
    """
    head = _pick_head(keypoints, min_conf)
    neck = _pick_neck(keypoints, min_conf)
    if head is None or neck is None:
        return None

    hx, hy = head
    nx, ny = neck
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
    hip = _pick_hip(keypoints, min_conf)
    if hip is not None:
        ux = nx - hip[0]
        uy = ny - hip[1]
        torso_len = math.hypot(ux, uy)

    if torso_len < MIN_BODY_SCALE_PX:
        # No usable hip axis: rotate the shoulder line 90° (old frontal fallback).
        if shoulder_w < MIN_BODY_SCALE_PX:
            return None
        lsx, lsy, _ = _as_xyz(keypoints[LEFT_SHOULDER])
        rsx, rsy, _ = _as_xyz(keypoints[RIGHT_SHOULDER])
        ux = -(rsy - lsy)
        uy = rsx - lsx
        if uy > 0.0:
            ux = -ux
            uy = -uy
        u_norm = math.hypot(ux, uy)
        if u_norm < 1e-3:
            return None
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
        return None

    elev = ux * (hx - nx) + uy * (hy - ny)
    pitch_along_up = _map_elev_to_pitch(elev, scale)
    pitch = pitch_along_up
    # Image-Y drop helps profile. On a close frontal webcam it uses the huge
    # shoulder ruler and turns "looking at the camera" into 60°+.
    if _is_profile_view(shoulder_w, torso_len):
        pitch = max(pitch, _map_elev_to_pitch(-(hy - ny), scale))
    pitch = _apply_upright_gate(
        pitch,
        hy,
        ny,
        scale,
        _is_frontal_face(keypoints, min_conf),
    )
    return max(0.0, min(135.0, pitch))


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
