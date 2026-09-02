# 睡岗原型（同学 B / 验收点 2）

Windows 上用 Python + ultralytics 验证：**YOLO-Pose 关键点 → 头部俯仰角 → 多帧区分低头与睡岗**。公式和阈值与 Analyzer 的 `server/Analyzer/Core/SleepPose.h` 一致。说明见 [docs/algorithm-sleep.md](../../docs/algorithm-sleep.md)。

不要把 `.pt` / `.onnx` / 大视频推进 Git。

## 依赖

```powershell
cd algo\sleep-pose
python -m pip install -r requirements.txt
```

首次跑检测会下载 `yolo11n-pose.pt`。

## 阈值自测（不需要 GPU / 视频）

```powershell
cd algo\sleep-pose
python -m unittest discover -s tests -v
```

短低头、正面看镜头不应报；持续低头 ≥ 2.5s 应报。正拍/侧拍用同一套俯仰角；头仍在脖子上方会封顶。

## 本地视频

准备一段 **H.264** 工位视频（睡岗要报，普通低头不报）。大文件放 `docs/fixtures/` 但不要提交。

```powershell
python -m sleep_pose.detect --video ..\..\docs\fixtures\workplace-h264.mp4 --device cpu
```

有 NVIDIA 时把 `--device` 改成 `0`。

## 导出 ONNX（给 A 的演示机）

```powershell
python -m sleep_pose.export_onnx --weights yolo11n-pose.pt --out yolo11n-pose.onnx
```

拷到虚拟机 `/opt/SVA/models/yolo11n-pose.onnx`。Analyzer 新算法代号 **`on_sleep_pose`**，缺模型时原 YOLO 仍可启动。

A 编译（合入后）：

```bash
cd /opt/SVA-dev/server
mkdir -p build && cd build
cmake .. -DCMAKE_BUILD_TYPE=Release -DSVA_ONNXRUNTIME_GPU=OFF
cmake --build . -j"$(nproc)"
```
