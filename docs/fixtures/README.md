# 测试视频（同学 B / 验收点 1）

当前平台**预览不支持 H.265**。无摄像头时，用一段 **H.264** 工位或人物视频伪造 RTSP，供加设备 → 预览 → 原 YOLO 布控。

打开网页：

- 同学 A 演示机：`http://localhost/` 或 `http://<局域网IP>:8080/`（见 [deploy-notes.md](../deploy-notes.md)）
- 同学 B 本机 WSL24 联调：`http://127.0.0.1:8088/`（Chrome / Edge，**不要**用 Cursor Browser，也**不要**用 8081）。重启后先看 [architecture-analyzer.md 第 10 节](../architecture-analyzer.md)
- 账号均为 `admin` / `admin123`

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

验收点 2 再准备两段（或同一段里两段动作）：**趴桌睡岗**应报，**短低头看键盘**不应报。Windows 原型命令见 [algorithm-sleep.md](../algorithm-sleep.md)。

工位摄像头请从 ZLM 的 RTMP 再录，不要和第二路 ffmpeg 抢同一只 dshow 设备。成品例如 `docs/fixtures/desk-sleep.mp4`，**不要 git add**（根目录已忽略 `docs/fixtures/*.mp4`）。

## 国标睡岗演示片（同学 A）

`scripts/start_gb_sim.sh` / `start_demo.ps1 -WithGbSim` 优先使用本目录 **`monisleep.mp4`**（本地放置，勿提交）。没有该文件时脚本回退系统 cup。覆盖片源：

```bash
VIDEO=/path/to/other.mp4 bash scripts/start_gb_sim.sh
```

## 本机模型（不要提交）

`Analyzer-lib/models/yolo11n.onnx`、`yolo26s.onnx` 拷到虚拟机 `/opt/SVA/models/`。不要 `git add Analyzer-lib`。

## 验收点 3：国标源原 YOLO 布控验证（同学 B）

不改睡岗公式。目标：国标设备注册成功后，Analyzer 拉 `rtsp://…/rtp/<设备_通道>`，原 YOLO 能出框或告警。国标 / 直连睡岗实测见 [algorithm-sleep.md](../algorithm-sleep.md) §7，不要改开机脚本。

取流公式与责任切分见 [architecture-analyzer.md](../architecture-analyzer.md) 第 11 节。操作手册见 [国标功能使用说明书.md](../国标功能使用说明书.md)。

### 演示机步骤（验收以 A 的 Ubuntu 22.04 为准）

1. A 按 [启动手册.md](../启动手册.md) 拉起 WVP + 模拟器或真机，业务库国标行在线（常见 `demo-ipc` / `camgbf0b09a04`）。
2. 设备预览有画面（Nginx `/rtp/…live.flv`）。`getMediaList` 有 `app=rtp`。
3. 在 WSL 跑：

```bash
bash scripts/probe_analyzer_pull.sh --live camgbf0b09a04 --rtp 34020000001320000001_34020000001320000001
```

`rtp` 一行应为 `OPEN`。若只有 live 打开、rtp 失败，先找 A（没点播 / 没推 PS）。
4. 布控选该国标设备 + `on_yolo11n_80`（或 `on_yolo26n_80`），目标用画面里真实有的类（水杯演示用 `cup`，工位用 `person`），闭合主区域，录像引擎 **算法服务器**。
5. 启动后看 Analyzer 日志：`streamUrl` 必须是 `rtsp://127.0.0.1:9994/rtp/…`，不能是 `live/camgb…`。
6. 布控预览出框，或告警列表有原 YOLO 记录。然后 **停止布控**，再对一条直连 RTSP 原 YOLO 走一遍，确认没拆掉。

### 「有注册无画面」

| 现象 | 归谁 |
|------|------|
| 列表在线，`getMediaList` 无该 `rtp` | A（SIP / INVITE / 推流） |
| ZLM 与网页有画，Analyzer `pull stream connect error` 或 URL 仍是 `live/` | B（下发 URL / FFmpeg 错误串） |
| 已连上但无框 | 先看区域、目标类、算法代号；不要和睡岗混查 |

### 本机 WSL24 记录

本机 **没有** WVP / 国标模拟器，不能当验收环境。国标 YOLO / 睡岗以 A 演示机为准；**2026-09-08 已与 A 联调通过**。睡岗双源实测表见 [algorithm-sleep.md](../algorithm-sleep.md) §7。

**2026-09-05**

```text
RESULT analyzer: UP
RESULT live  cam918429: NO_MEDIA_OR_TIMEOUT   （未启用监控、未推 webcam）
RESULT rtp   3402…0001: NO_MEDIA_OR_TIMEOUT   （无 WVP，预期）
```

**2026-09-08**（六服务 `active` 后再跑 `scripts/probe_analyzer_pull.sh --live cam918429 --rtp 34020000001320000001_34020000001320000001`）

```text
RESULT analyzer: UP
RESULT live  cam918429: NO_MEDIA_OR_TIMEOUT   （未推 webcam，预期）
RESULT rtp   34020000001320000001_34020000001320000001: NO_MEDIA_OR_TIMEOUT   （无 WVP，预期）
```

演示机记录（A 的 Ubuntu 22.04）：

| 日期 | streamUrl | 目标类 | 出框/告警 | 直连 YOLO 回归 |
| --- | --- | --- | --- | --- |
| 2026-09-08 | `rtsp://127.0.0.1:9994/rtp/34020000001320000001_34020000001320000001` | person | 过（出框；同日睡岗亦过，见 algorithm-sleep.md §7） | 过 |

### 不要做的

- 不要在本机装一套 WVP 当验收
- 不要把睡岗阈值改动塞进这次验证
- 不要改 `zlm_server.host`
