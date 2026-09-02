"""Run YOLO-Pose on a local video and print bow / sleep decisions."""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

from .geometry import PITCH_DOWN_DEG, PITCH_RECOVER_DEG, pitch_from_coco17
from .temporal import SLEEP_HOLD_MS, FrameLabel, TemporalState, update_temporal


def _parse_args(argv: list[str] | None = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Sleep-on-duty prototype (YOLO-Pose + multi-frame).")
    parser.add_argument("--video", required=True, help="Local H.264/mp4 path")
    parser.add_argument("--weights", default="yolo11n-pose.pt", help="Ultralytics pose weights or ONNX")
    parser.add_argument("--device", default="cpu")
    parser.add_argument("--down-deg", type=float, default=PITCH_DOWN_DEG)
    parser.add_argument("--recover-deg", type=float, default=PITCH_RECOVER_DEG)
    parser.add_argument("--hold-ms", type=int, default=SLEEP_HOLD_MS)
    parser.add_argument("--save-json", default="", help="Optional event list path")
    parser.add_argument("--save-overlay", default="", help="Optional annotated mp4")
    return parser.parse_args(argv)


def main(argv: list[str] | None = None) -> int:
    args = _parse_args(argv)
    video = Path(args.video)
    if not video.is_file():
        print(f"video not found: {video}", file=sys.stderr)
        return 2

    try:
        from ultralytics import YOLO
    except ImportError:
        print("pip install -r algo/sleep-pose/requirements.txt", file=sys.stderr)
        return 2

    model = YOLO(args.weights)
    trackers: dict[int, TemporalState] = {}
    events: list[dict] = []
    sleeping_ids: set[int] = set()
    frame_index = 0

    results = model.track(
        source=str(video),
        stream=True,
        persist=True,
        verbose=False,
        device=args.device,
        save=bool(args.save_overlay),
        project=str(Path(args.save_overlay).parent) if args.save_overlay else "runs",
        name=Path(args.save_overlay).stem if args.save_overlay else "sleep-pose",
        exist_ok=True,
    )

    for result in results:
        fps = result.speed.get("fps") if isinstance(getattr(result, "speed", None), dict) else None
        timestamp_ms = int(frame_index * (1000.0 / fps)) if fps else frame_index * 40
        boxes = result.boxes
        keypoints = result.keypoints
        if boxes is None or keypoints is None or boxes.id is None:
            frame_index += 1
            continue

        ids = boxes.id.int().tolist()
        kpts = keypoints.data.cpu().numpy()
        confs = boxes.conf.cpu().numpy()
        for i, track_id in enumerate(ids):
            state = trackers.setdefault(int(track_id), TemporalState())
            pitch = pitch_from_coco17(kpts[i])
            decision = update_temporal(
                state,
                pitch,
                timestamp_ms,
                down_deg=args.down_deg,
                recover_deg=args.recover_deg,
                hold_ms=args.hold_ms,
            )
            if decision.label == FrameLabel.SLEEP and track_id not in sleeping_ids:
                sleeping_ids.add(int(track_id))
                events.append(
                    {
                        "alarmType": "SLEEP_ON_DUTY",
                        "behavior_type": "sleep_on_duty",
                        "customEventName": "睡岗",
                        "trackId": int(track_id),
                        "confidence": float(confs[i]),
                        "pitchDegree": decision.pitch_deg,
                        "durationFrames": decision.head_down_frames,
                        "duration_ms": decision.head_down_ms,
                        "frameIndex": frame_index,
                    }
                )
                print(
                    f"SLEEP track={track_id} frame={frame_index} "
                    f"pitch={decision.pitch_deg} hold_ms={decision.head_down_ms}"
                )
            if decision.label == FrameLabel.UPRIGHT:
                sleeping_ids.discard(int(track_id))
        frame_index += 1

    print(f"frames={frame_index} sleep_events={len(events)}")
    if args.save_json:
        Path(args.save_json).write_text(json.dumps(events, ensure_ascii=False, indent=2), encoding="utf-8")
    return 0 if events else 1


if __name__ == "__main__":
    raise SystemExit(main())
