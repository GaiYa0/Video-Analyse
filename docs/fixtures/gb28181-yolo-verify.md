# 国标源原 YOLO 布控验证（同学 B / 验收点 3）

不改睡岗公式。目标：国标设备注册成功后，Analyzer 拉 `rtsp://…/rtp/<设备_通道>`，原 YOLO 能出框或告警。睡岗联调另开 PR。

取流公式与责任切分见 [architecture-analyzer.md](../architecture-analyzer.md) 第 11 节（合入后）。操作手册见 [国标功能使用说明书.md](../国标功能使用说明书.md)。

## 演示机步骤（验收以 A 的 Ubuntu 22.04 为准）

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

## 「有注册无画面」

| 现象 | 归谁 |
|------|------|
| 列表在线，`getMediaList` 无该 `rtp` | A（SIP / INVITE / 推流） |
| ZLM 与网页有画，Analyzer `pull stream connect error` 或 URL 仍是 `live/` | B（下发 URL / FFmpeg 错误串） |
| 已连上但无框 | 先看区域、目标类、算法代号；不要和睡岗混查 |

## 本机 WSL24 记录（2026-09-05）

本机 **没有** WVP / 国标模拟器，不能当验收环境。当天已拉起六服务后跑探测：

```text
RESULT analyzer: UP
RESULT live  cam918429: NO_MEDIA_OR_TIMEOUT   （未启用监控、未推 webcam）
RESULT rtp   3402…0001: NO_MEDIA_OR_TIMEOUT   （无 WVP，预期）
```

国标 YOLO 出框仍须在 A 演示机对 `demo-ipc` 点一遍。本机只保证 Analyzer 进程活着、脚本能区分 live / rtp。

## 不要做的

- 不要在本机装一套 WVP 当验收
- 不要把睡岗阈值改动塞进这次验证
- 不要改 `zlm_server.host`
