"""Head pitch from COCO-17 pose. Same formulas as server/Analyzer/Core/SleepPose.h."""

from __future__ import annotations

import math
from typing import Sequence

NOSE = 0
LEFT_SHOULDER = 5
RIGHT_SHOULDER = 6

MIN_KEYPOINT_CONF = 0.25
PITCH_DOWN_DEG = 35.0
PITCH_RECOVER_DEG = 25.0
# Typical nose-to-neck length as a fraction of shoulder width (upright facing camera).
EXPECTED_HEAD_TO_SHOULDER = 0.50


def _as_xyz(point: Sequence[float]) -> tuple[float, float, float]:
    if len(point) < 2:
        raise ValueError("keypoint needs x,y")
    conf = float(point[2]) if len(point) > 2 else 1.0
    return float(point[0]), float(point[1]), conf


def try_compute_pitch_deg(
    nose: Sequence[float],
    left_shoulder: Sequence[float],
    right_shoulder: Sequence[float],
    min_conf: float = MIN_KEYPOINT_CONF,
) -> float | None:
    """Head-drop score in degrees (0 ≈ upright, ~90 ≈ nose at the neck).

    Midline bow does not change the *direction* of neck→nose, so we do not use
    the raw angle between vectors. Instead: project neck→nose onto body-up and
    compare that length to 0.5 × shoulder width. Image Y grows downward.
    Returns None when keypoints are too weak.
    """
    nx, ny, nc = _as_xyz(nose)
    lx, ly, lc = _as_xyz(left_shoulder)
    rx, ry, rc = _as_xyz(right_shoulder)
    if nc < min_conf or lc < min_conf or rc < min_conf:
        return None

    neck_x = 0.5 * (lx + rx)
    neck_y = 0.5 * (ly + ry)
    sx = rx - lx
    sy = ry - ly
    # Rotate shoulder 90° CCW to get a candidate "up" axis; flip if it points down.
    ux = -sy
    uy = sx
    if uy > 0.0:
        ux = -ux
        uy = -uy

    hx = nx - neck_x
    hy = ny - neck_y
    u_norm = math.hypot(ux, uy)
    if u_norm < 1e-3:
        return None

    elev = (ux * hx + uy * hy) / u_norm
    expected = EXPECTED_HEAD_TO_SHOULDER * u_norm
    ratio = elev / max(expected, 1e-3)
    ratio = max(-0.5, min(1.0, ratio))
    return (1.0 - ratio) * 90.0


def pitch_from_coco17(
    keypoints: Sequence[Sequence[float]],
    min_conf: float = MIN_KEYPOINT_CONF,
) -> float | None:
    if len(keypoints) <= RIGHT_SHOULDER:
        return None
    return try_compute_pitch_deg(
        keypoints[NOSE],
        keypoints[LEFT_SHOULDER],
        keypoints[RIGHT_SHOULDER],
        min_conf,
    )
