# 测试视频（同学 B / 验收点 1）

当前平台**预览不支持 H.265**。无摄像头时，用一段 **H.264** 工位或人物视频伪造 RTSP，供加设备 → 预览 → 原 YOLO 布控。

## 文件放哪

把转好的文件放到本目录，例如：

```text
docs/fixtures/workplace-h264.mp4
```

- 编码：H.264（`avc1` / `libx264`），建议 720p 或 1080p、有人入画
- 容器：`.mp4` 或 `.mkv`
- **不要把大文件推进 GitHub**（见 [PR规范.md](../PR规范.md)）。小片段可以提交；超过仓库限额的放网盘，在本 README 补链接
- 不要提交 `Analyzer-lib/` 里的 zip / CUDA / onnx

本仓库暂不附带样例成片（版权与体积）。请自行录一段工位画面，或用下面命令把已有视频转成 H.264。

## 转成 H.264

Windows（已安装 ffmpeg）在仓库根目录：

```powershell
ffmpeg -y -i ".\原始视频.mp4" -c:v libx264 -pix_fmt yuv420p -preset veryfast -crf 23 -an ".\docs\fixtures\workplace-h264.mp4"
```

查看编码：

```powershell
ffprobe -hide_banner ".\docs\fixtures\workplace-h264.mp4"
```

应看到 `Video: h264`，不要是 `hevc`。

## 伪造 RTSP（给设备管理填）

虚拟机或能被 ZLM 拉到的机器上，循环推流（把路径换成实际文件）：

```bash
ffmpeg -re -stream_loop -1 -i /path/to/workplace-h264.mp4 \
  -c:v copy -an -f rtsp -rtsp_transport tcp \
  rtsp://127.0.0.1:8554/workplace
```

若本机没有 RTSP 服务，可先用 [MediaMTX](https://github.com/bluenviron/mediamtx) 或 ZLM 自己的 RTSP 端口收推流，再把 **ZLM 上的播放地址**填进设备「直连 URL」。

更省事的验收路径（与正式流程一致）：

1. 设备类型选直连，`direct_source_url` 填摄像头或上述伪造 RTSP
2. 启动监控，让 backend 调 ZLM `addStreamProxy`
3. 预览走 ZLM 转出的 FLV；Analyzer 拉 `rtsp://{zlm}/live/{apeId}`

YOLO 要出「人」的框，画面里需要能看清人体；纯色条或空办公室很难稳定告警。

## 本机模型（不要提交）

`Analyzer-lib/models/yolo11n.onnx`、`yolo26s.onnx` 拷到虚拟机 `/opt/SVA/models/`。不要 `git add Analyzer-lib`。
