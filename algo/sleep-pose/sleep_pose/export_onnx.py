"""Export ultralytics YOLO-Pose to ONNX. Do not git add the .onnx / .pt files."""

from __future__ import annotations

import argparse
from pathlib import Path


def main() -> int:
    parser = argparse.ArgumentParser(description="Export yolo11n-pose.onnx for Analyzer.")
    parser.add_argument("--weights", default="yolo11n-pose.pt")
    parser.add_argument("--out", default="yolo11n-pose.onnx")
    parser.add_argument("--imgsz", type=int, default=640)
    args = parser.parse_args()

    try:
        from ultralytics import YOLO
    except ImportError:
        print("pip install -r algo/sleep-pose/requirements.txt")
        return 2

    model = YOLO(args.weights)
    exported = Path(model.export(format="onnx", imgsz=args.imgsz, simplify=True, opset=12))
    dest = Path(args.out)
    if exported.resolve() != dest.resolve():
        dest.parent.mkdir(parents=True, exist_ok=True)
        dest.write_bytes(exported.read_bytes())
    print(f"wrote {dest} ({dest.stat().st_size} bytes)")
    print("Copy to /opt/SVA/models/yolo11n-pose.onnx on the demo machine. Do not commit it.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
