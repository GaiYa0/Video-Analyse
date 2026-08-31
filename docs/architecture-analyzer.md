# 分析器架构（同学 B）

验收点 1 产出。本文根据仓库源码整理，描述 **SVA-server Analyzer** 如何取流、加载模型、上报告警。睡岗（YOLO-Pose）属于验收点 2，这里不改推理管线。

维护：同学 B。总图由同学 C 写在 [architecture.md](./architecture.md)（若该文件尚未合入，以本文 + [分工.md](./分工.md) 为准）。

版本线索：`server/Analyzer/Core/Version.h` 中 `PROJECT_VERSION` 为 **v1.2.7**。

---

## 1. 它在整条链路里的位置

```text
摄像头 / 测试文件 RTSP
        │
        ▼
ZLMediaKit  addStreamProxy          ← 同学 A，backend 调 REST
        │
        │  rtsp://{zlmHost}:{rtspPort}/{app}/{apeId}
        ▼
Analyzer 拉流 → 解码 → YOLO → 行为规则 → 告警图/视频
        │
        ├─ HTTP POST  告警媒体  → backend :9114 /waring/waring/addFromSvaSimple
        └─ WebSocket  检测帧    → backend :9114 /websocket/sva/noop
```

Analyzer **不调用** ZLM REST。播放 URL 由 backend 按设备绑定的 `zlm_server` / `sva_server` 拼好，再 `POST /api/control/add` 下发。拼装逻辑见：

- `backend/ruoyi-admin/src/main/java/com/ruoyi/web/service/deployment/DeploymentAnalyzerClient.java`

默认形态（`DEFAULT_ZLM_APP=live`，`DEFAULT_SVA_APP=analyzer`）：

| 用途 | URL |
|------|-----|
| 分析输入 | `rtsp://{zlmHost}:{media_rtsp_port}/live/{apeId}` |
| 画框回推（可选） | `rtsp://{zlmHost}:{media_rtsp_port}/analyzer/{deploymentId}` |
| 前端看算法流 | `ws://{zlmHost}:{media_http_port}/analyzer/{deploymentId}.live.flv` |

官方样例端口见 `server/config.json`：HTTP/API 9992、Analyzer 9993、RTSP 9994。样例里的 `host`、`mediaSecret` 是上游开发机残留，**部署时必须改成虚拟机真实 IP**；Analyzer C++ **不读取** `mediaSecret`。

---

## 2. 进程怎么起来

入口：`server/Analyzer/main.cpp`

1. `Config(config.json)` — `server/Analyzer/Core/Config.cpp`
2. `Scheduler::initAlgorithm()` — 加载两个 ONNX
3. `Server::start()` — 独立线程，libevent 监听 `0.0.0.0:{analyzerPort}`
4. `Scheduler::loop()` — 主线程管理 Worker 增删；另起告警线程、检测帧上报线程

启动命令（官方脚本部署后的路径，以实际为准）：

```bash
cd /opt/SVA/server
./Analyzer -f /opt/SVA/config.json
```

GitHub monorepo 在虚拟机上的源码目录约定为 `/opt/SVA-dev`（见 [PR规范.md](./PR规范.md)）。用本仓库源码编译时：

```bash
cd /opt/SVA-dev/server
mkdir -p build && cd build
# 无 NVIDIA 或驱动 < 580：关 GPU EP（与 A 手册一致）
cmake .. -DCMAKE_BUILD_TYPE=Release -DSVA_ONNXRUNTIME_GPU=OFF
cmake --build . -j"$(nproc)"
./Analyzer -f /opt/SVA/config.json
```

CMake 说明：`server/CMakeLists.txt`。默认 `SVA_ONNXRUNTIME_GPU=ON`，依赖 `/usr/local/onnxruntime`、`/usr/local/ffmpeg`、OpenCV、jsoncpp、libevent、libcurl。这些由官方 `install_source.sh` + `easySVA-lib`（本机散装目录 `Analyzer-lib/`，**不要 git add**）在 Ubuntu 上安装，不在 Windows 上编整套 Analyzer。

---

## 3. 模型在哪、启动时加载什么

配置字段 `modelDir`，样例为 `/opt/SVA/models`（`server/config.json`）。

`Scheduler::initAlgorithm()`（`server/Analyzer/Core/Scheduler.cpp`）启动时加载 **2 个 COCO 80 类** 模型：

| 算法代号 | 文件 | 用途 |
|----------|------|------|
| `on_yolo11n_80` | `{modelDir}/yolo11n.onnx` | YOLO11n |
| `on_yolo26n_80`（别名 `ov_yolo26n_80`） | `{modelDir}/yolo26s.onnx` | 文件名是 yolo26**s** |

解析代号：`server/Analyzer/Core/Analyzer.cpp` 的 `resolveAlgorithm()`。推理实现：`server/Analyzer/Core/AlgorithmOnYolo.cpp`（GPU 构建时 TensorRT → CUDA → CPU 回退）。

本机 `Analyzer-lib/models/` 里有上述两个 ONNX，部署时应拷到虚拟机 `/opt/SVA/models/`。同目录的 `yolo26s_miner.onnx` 是矿用 8 类定制模型，**当前 Analyzer 不会自动加载**，验收点 1 不要接入。

权重文件已被根目录 `.gitignore` 的 `*.onnx` 排除，不进 GitHub。

---

## 4. 取流与 Worker

布控下发后，`AvPullStream::connect()`（`server/Analyzer/Core/AvPullStream.cpp`）用 FFmpeg `avformat_open_input` 打开 `Control.streamUrl`：

- RTSP 使用 **TCP**（`rtsp_transport=tcp`）
- 连接超时约 10s；可尝试 CUDA 硬解，失败回退 CPU

同一路流可以挂多个布控：`Scheduler.cpp` 里 `getWorkerStreamKey()` 优先用 `streamCode`，否则用 `streamUrl`。backend 下发时 `streamCode` = 设备 `apeId`。

Worker 内线程（`server/Analyzer/main.cpp` 注释 + `Worker.cpp`）：

1. `AvPullStream::readThread` — 读包
2. `Worker::decodeVideoThread` — 解码 BGR → YOLO → 区域匹配 → 时序追踪 → 行为规则
3. 告警线程 — 缓存帧、写出 MP4/JPG
4. 可选 `AvPushStream` — 画框后推回 ZLM

行为规则在 `server/Analyzer/Core/BehaviorEvaluator.cpp`。其中已有名为 `sleep` 的启发式（宽高比 + 低速 + 停留），**不是**验收点 2 的 YOLO-Pose 睡岗。验收点 1 用原 YOLO 的 `person` 等 COCO 类 + 区域/越线即可出告警。

---

## 5. Analyzer 对外 HTTP

监听：`server/Analyzer/Core/Server.cpp`，`0.0.0.0:{analyzerPort}`（样例 9993）。

| 路径 | 作用 |
|------|------|
| `GET /api/health` | 健康检查 |
| `POST /api/controls` | 列出布控（backend 监控页会调） |
| `POST /api/control/add` | 启动布控 |
| `POST /api/control/cancel` | 停止布控 |
| `POST /api/alarm/bind-media` | backend 先入库后再绑定 alarmId / 媒体路径 |

backend 监控：`AlgorithmServerMonitorController` → `/monitor/algorithm`。

---

## 6. 告警如何打到 backend

配置（`server/config.json` + `Config.cpp`）：

- `saveAlarmUrl`：告警图/视频回调  
- `detectEventUrl`：实时检测（支持 `ws://`；当前实现不支持 `wss://`）  
- `uploadDir`：落盘根目录，样例 `/var/www/SVA-web/upload`

### 6.1 实时 overlay / 事件（WebSocket）

`Scheduler` 把 `detect.frame` / `detect.event` 发到 `detectEventUrl`（`Utils/Request.cpp`）。

backend：

- 入口 `backend/ruoyi-framework/src/main/java/com/ruoyi/framework/websocket/SvaNoopWebSocketEndpoint.java` — 路径 `/websocket/sva/noop`
- 转发 `SvaOverlayRelay.java`：`detect.frame` 转给前端 `/websocket/message`；`detect.event` 交给 `HWaringController.consumeSvaDetectEvent`

Nginx 需把 `/websocket/` 反代到 9114（官方安装脚本如此配置）。

### 6.2 告警媒体（HTTP POST）

`GenerateAlarmVideo.cpp` 写出：

```text
{uploadDir}/alarm/{controlCode}/{timestamp}/main.mp4
{uploadDir}/alarm/{controlCode}/{timestamp}/main.jpg
```

然后 POST JSON（字段名以源码为准）：

```json
{
  "control_code": "<deploymentId>",
  "behavior_type": "",
  "rule_id": "",
  "desc": "",
  "video_path": "alarm/.../main.mp4",
  "image_path": "alarm/.../main.jpg"
}
```

若已 `bind-media`，会带 `alarm_id` / `event_id`，并改走 `addFromSvaMediaCallback`。

backend 入库：

- `POST /waring/waring/addFromSvaSimple` — `HWaringController.java`
- `POST /waring/waring/addFromSvaMediaCallback`

这两条在 `SecurityConfig.java` 中匿名放行（Analyzer 无登录 JWT）。`control_code` 按 **布控任务 id** 去查 `deployment_task`，再映射到设备。

前端告警页读库表 `h_waring`；截图经 Nginx `/alarm/` 映射到 upload 目录。

---

## 7. 配置字段（Analyzer 实际读取的）

`Config.cpp` 读取：`code`、`host`、`adminPort`、`saveAlarmUrl`、`detectEventUrl`、`analyzerPort`、`mediaHttpPort`、`mediaRtspPort`、`uploadDir`、`modelDir`。

`adminHost` 由 `http://{host}:{adminPort}` 拼出。`name` / `describe` / `mediaSecret` / `saveAlarmType` 在样例 JSON 里有，**C++ 未解析**。

部署后至少改：

1. `host` 为虚拟机 IP（不要用样例 `10.129.52.114`）
2. `saveAlarmUrl` / `detectEventUrl` 指向 backend 的 9114（本机可用 127.0.0.1）
3. 数据库 `zlm_server`、`sva_server` 的 IP 从 127.0.0.1 改为实际地址（官方 README 要求）

---

## 8. 验收点 1：同学 B 要做的验证

无摄像头时用 [fixtures/README.md](./fixtures/README.md) 的 H.264 + 伪造 RTSP。

建议在 A 的 Ubuntu 虚拟机上核对：

1. 进程在：`ps aux | grep Analyzer`，日志无「open model」失败
2. `curl -s http://127.0.0.1:9993/api/health` 有响应
3. `/opt/SVA/models/yolo11n.onnx` 与 `yolo26s.onnx` 存在
4. 设备管理添加 **H.264** RTSP → 启动监控 → 预览有画面（H.265 预览官方尚未支持）
5. 布控选原 YOLO（如 `on_yolo11n_80`）、目标含 `person`、画区域后启动
6. 告警列表出现记录，磁盘上有 `upload/alarm/.../main.jpg`

本机 Windows **不要**当验收环境编译 Analyzer。phase 1 允许安装 Python + ultralytics，仅做后续睡岗原型环境，**不要**接到 `server/`。

```text
python -m pip install ultralytics
```

---

## 9. 验收点 1 尚未在虚拟机闭环的部分

以下必须等同学 A 的 Ubuntu 22.04 演示机就绪后，B 上去点一遍，不能用本文代替实测：

- 官方脚本把 Analyzer 编过、四服务起来
- 原 YOLO 布控真正打出告警截图

编过之后把 cmake 选项、二进制路径、一段日志摘要贴到对应 PR 评论（见角色手册）。
