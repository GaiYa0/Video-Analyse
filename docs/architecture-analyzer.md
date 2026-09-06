# 分析器架构（同学 B）

验收点 1 产出，并随验收点 2/3 补取流与模型。本文根据仓库源码整理，描述 **SVA-server Analyzer** 如何取流、加载模型、上报告警。睡岗公式见 [algorithm-sleep.md](./algorithm-sleep.md)；国标取流见第 11 节。

维护：同学 B。总图由同学 C 写在 [architecture.md](./architecture.md)（若该文件尚未合入，以本文 + [分工.md](./分工.md) 为准）。

版本线索：`server/Analyzer/Core/Version.h` 中 `PROJECT_VERSION` 为 **v1.2.7**。

---

## 1. 它在整条链路里的位置

```text
直连 RTSP / 国标 SIP（WVP）
        │
        ▼
ZLMediaKit  live 代理 或 rtp 点播     ← 同学 A
        │
        │  直连: rtsp://{zlm}:{rtspPort}/live/{apeId}
        │  国标: rtsp://{zlm}:{rtspPort}/rtp/{设备_通道}
        ▼
Analyzer 拉流 → 解码 → YOLO / 睡岗 → 行为规则 → 告警图/视频
        │
        ├─ HTTP POST  告警媒体  → backend :9114 /waring/waring/addFromSvaSimple
        └─ WebSocket  检测帧    → backend :9114 /websocket/sva/noop
```

Analyzer **不调用** ZLM REST，也不做 SIP。播放 URL 由 backend 按 `device_type` 拼好，再 `POST /api/control/add` 下发。拼装逻辑见：

- `backend/ruoyi-admin/src/main/java/com/ruoyi/web/service/deployment/DeploymentAnalyzerClient.java`（`resolveGbRtpPull` / `buildStreamUrl`）

默认形态（直连 `app=live`，国标 `app=rtp`，回推 `DEFAULT_SVA_APP=analyzer`）：

| 用途 | URL |
|------|-----|
| 分析输入（直连 RTSP） | `rtsp://{zlmHost}:{media_rtsp_port}/live/{apeId}` |
| 分析输入（国标 GB28181） | `rtsp://{zlmHost}:{media_rtsp_port}/rtp/{设备_通道}` |
| 画框回推（可选） | `rtsp://{zlmHost}:{media_rtsp_port}/analyzer/{deploymentId}` |
| 前端看算法流 | `ws://{zlmHost}:{media_http_port}/analyzer/{deploymentId}.live.flv` |
| 前端看国标源（Nginx） | `ws://{host}:8080/rtp/{设备_通道}.live.flv`（演示机；本机 WSL24 常见 8088） |

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
| `on_sleep_pose` | `{modelDir}/yolo11n-pose.onnx` | 睡岗 Pose；文件缺失则跳过，原 YOLO 不受影响 |

解析代号：`server/Analyzer/Core/Analyzer.cpp` 的 `resolveAlgorithm()`。推理实现：`server/Analyzer/Core/AlgorithmOnYolo.cpp`（GPU 构建时 TensorRT → CUDA → CPU 回退）。睡岗公式与多帧见 [algorithm-sleep.md](./algorithm-sleep.md)。

本机 `Analyzer-lib/models/` 里有上述 YOLO ONNX，部署时应拷到虚拟机 `/opt/SVA/models/`。睡岗另拷 `yolo11n-pose.onnx`（由 `algo/sleep-pose` 导出，不进 git）。同目录的 `yolo26s_miner.onnx` 是矿用 8 类定制模型，**当前 Analyzer 不会自动加载**，不要当睡岗模型。

权重文件已被根目录 `.gitignore` 的 `*.onnx` 排除，不进 GitHub。

---

## 4. 取流与 Worker

布控下发后，`AvPullStream::connect()`（`server/Analyzer/Core/AvPullStream.cpp`）用 FFmpeg `avformat_open_input` 打开 `Control.streamUrl`：

- RTSP 使用 **TCP**（`rtsp_transport=tcp`）
- 连接超时约 **25s**（`stimeout=25000000`），给国标 INVITE / `on_stream_not_found` 留窗口；可尝试 CUDA 硬解，失败回退 CPU

同一路流可以挂多个布控：`Scheduler.cpp` 里 `getWorkerStreamKey()` 优先用 `streamCode`，否则用 `streamUrl`。backend 下发时 `streamCode` = 设备 `apeId`。

拉流默认 `nobuffer` / `low_delay`，包队列只留 **4** 帧作解码突发缓冲。解码线程若发现队列里还有更新的包，会继续喂给 H.264 解码器，但跳过 BGR / YOLO / 回推，只对最新一帧做推理和画框。回推编码队列只留 **1** 帧，时间戳按墙钟、时长按相邻帧间隔，GOP 约 5 帧、不编 B 帧。宁可丢帧，也不把旧画面堆给布控预览。前端直播 FLV 必须把 `enableStashBuffer: false` 传给 flv.js 的 **Config（第二个参数）**，否则浏览器缓冲会一直涨。

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

然后按有没有 `alarm_id` 分两条路（`GenerateAlarmVideo.cpp` 的 `resolveAlarmCallbackUrl`）：

- **没有 `alarm_id`**：POST `saveAlarmUrl`，即 `addFromSvaSimple`，**新建**告警行。现网 `detectEventUrl` 常是 `/websocket/sva/noop`，不会先入库，必须走这条，否则只落盘、告警页空白。  
- **已 `bind-media`**：带 `alarm_id` / `event_id`，改走 `addFromSvaMediaCallback`，只回写媒体路径。

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

现网 `detect.event` 会先插入告警行（封面用 start 截图，并带预设 `video_path`）。Analyzer 再把 mp4 写到同一路径。睡岗时叠加 `behavior_type=sleep_on_duty`、`alarmType=SLEEP_ON_DUTY`、`customEventName=睡岗`，以及 `confidence` / `pitchDegree` / `durationFrames` / `duration_ms`（见 [algorithm-sleep.md](./algorithm-sleep.md)）。编码失败会打 `genAlarmVideo failed`，不再静默丢掉。

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

**小组验收环境**以同学 A 的演示机为准（见 [deploy-notes.md](./deploy-notes.md)）：浏览器 `http://localhost/` 或局域网 `http://<IP>:8080/`。不要用 Cursor 内置 Browser，也不要打开随机端口（例如 8081）。

同学 B 本机另有一套 **WSL2 Ubuntu 24.04、CPU** 联调栈（与 A 的 22.04 / GPU / :80 不是同一套）。**开机后按第 10 节拉起**，否则 Windows 上 `127.0.0.1:8088` 会拒绝连接。

| 项 | 本机 WSL24 |
| --- | --- |
| 网页 | **http://127.0.0.1:8088/**（Chrome / Edge，Ctrl+F5） |
| 账号 | `admin` / `admin123` |
| Analyzer | `/opt/SVA/server/Analyzer -f /opt/SVA/config.json` |
| cmake | `-DCMAKE_BUILD_TYPE=Release -DSVA_ONNXRUNTIME_GPU=OFF` |
| 模型 | `/opt/SVA/models/yolo11n.onnx`、`yolo26s.onnx`；睡岗另需 `yolo11n-pose.onnx` |
| 源码检出 | `/opt/SVA-dev`（从本仓库 `.git` 拷到 ext4） |
| 开机清单 | **第 10 节** |

在本机 WSL 已核对：四服务可拉起；`curl http://127.0.0.1:9993/api/health` 返回 `code: 1000`；登录须走 `/prod-api`（打包时要有 `VUE_APP_BASE_API=/prod-api`，否则会出现 405）。

建议在演示机或本机 WSL 再核：

1. 进程在：`ps aux | grep Analyzer`，日志无「open model」失败
2. `curl -s http://127.0.0.1:9993/api/health` 有响应
3. `/opt/SVA/models/yolo11n.onnx` 与 `yolo26s.onnx` 存在
4. 设备管理添加 **H.264** RTSP → 启动监控 → 预览有画面（H.265 预览官方尚未支持）
5. 布控选原 YOLO（如 `on_yolo11n_80`）、目标含 `person`、画区域后启动
6. 告警列表出现记录，磁盘上有 `upload/alarm/.../main.jpg`

Windows **不要**当验收环境编译整套 Analyzer。phase 2 原型在 `algo/sleep-pose/`，公式见 [algorithm-sleep.md](./algorithm-sleep.md)。Analyzer 已认 `on_sleep_pose`（`yolo11n-pose.onnx`），缺模型时原 YOLO 仍启动。

```text
cd algo\sleep-pose
python -m pip install -r requirements.txt
python -m unittest discover -s tests -v
```

---

## 9. 验收点 1 闭环情况

- 文档已合入 `main`（PR #3）
- 本机 WSL24：Analyzer **CPU 已编过**，四服务可启动，网页 **8088**
- 同学 A 演示机：按 [deploy-notes.md](./deploy-notes.md) 的 80 / 8080 为准
- **原 YOLO 布控打出告警截图**仍要在有人入画的 H.264 流上点一遍，不能用本文代替最后一次实测

编过之后把 cmake 选项、二进制路径、一段日志摘要贴到对应 PR 评论（见角色手册）。

---

## 10. 本机重启后要拉起什么（同学 B / WSL24）

这是 **Windows + WSL2 Ubuntu-24.04 联调机**，不是 A 的 Ubuntu 22.04 演示机。A 的开机步骤仍看 [deploy-notes.md](./deploy-notes.md)。

镜像网络（`%USERPROFILE%\.wslconfig` 里 `networkingMode=mirrored`）下：发行版一变成 `Stopped`，Windows 访问 `127.0.0.1:8088` / `9992` 就会 **Connection refused**。网页能开、预览转圈、告警不来，先看这一节，不要先改算法。

### 10.1 开机必做（每次重启）

在 **PowerShell**（不要用 Cursor 内置终端代替系统浏览器）：

```powershell
# 1) 确认发行版能起来
wsl -l -v
# 应看到 Ubuntu-24.04  Running。若是 Stopped，下一步会把它拉起来。

# 2) 启动六个服务（多数已 enabled，保险起见全 start 一遍）
wsl -d Ubuntu-24.04 -u root -- bash -lc "systemctl start mariadb easysva-redis nginx easysva-media easysva-backend easysva-analyzer; systemctl is-active mariadb easysva-redis nginx easysva-media easysva-backend easysva-analyzer"

# 3) 钉住发行版，防止空闲被停（这个窗口不要关）
wsl -d Ubuntu-24.04 -u root -- sleep infinity
```

`sleep infinity` 要一直开着。关掉后过一会儿 WSL 可能再次 `Stopped`，本地端口又全拒。

| 服务 | systemd | 端口 | 干什么 |
| --- | --- | --- | --- |
| MariaDB | `mariadb` | 3306 | 库 `easySVA` |
| Redis | `easysva-redis` | 6380 | 登录会话 |
| Nginx | `nginx` | **8088** | 网页 + `/prod-api` + `/alarm/` |
| 后端 | `easysva-backend` | 9114 | 登录、设备、布控、告警 |
| ZLM | `easysva-media` | 9992 HTTP / 9994 RTSP / **9995 RTMP** | 预览与推流 |
| Analyzer | `easysva-analyzer` | 9993 | YOLO / 睡岗 |

自检（PowerShell）：

```powershell
curl.exe -sS -o NUL -w "8088:%{http_code}`n" http://127.0.0.1:8088/
curl.exe -sS -o NUL -w "api:%{http_code}`n" http://127.0.0.1:8088/prod-api/captchaImage
curl.exe -sS -o NUL -w "zlm:%{http_code}`n" http://127.0.0.1:9992/
curl.exe -sS -o NUL -w "analyzer:%{http_code}`n" http://127.0.0.1:9993/
```

都应是 `200`（Analyzer health 也可能是 JSON `code: 1000`）。

### 10.2 打开网页

- 只用 **Chrome / Edge**：http://127.0.0.1:8088/  
- 账号：`admin` / `admin123`  
- **不要**用 Cursor 内置 Browser（会 Connection Failed）  
- 测本地时关掉「狗急加速」或保证 `127.*` 走直连，否则登录页红条「后端接口连接异常」  
- 登录页红条若出现在服务刚 start 的几秒内：等后端打出「若依启动成功」再 Ctrl+F5  

### 10.3 开机不会自动恢复、必须手做的

systemd **不会**帮你：

1. **工位摄像头推流**（DirectShow 独占，必须在 Windows 另开窗口）  
2. **ZLM 拉流代理**（库里「运行中」只是旧状态；ZLM 重启后 `getMediaList` 经常是空的）  
3. **Analyzer 上的布控**（Analyzer 一重启，任务掉了，要在布控列表 **停止再启动**）

#### 工位摄像头（`cam918429`）

设备源：`rtmp://127.0.0.1:9995/live/webcam`  
预览：`ws://127.0.0.1:9992/live/cam918429.live.flv`

```powershell
# 设备名以本机为准：ffmpeg -list_devices true -f dshow -i dummy
# 100M 缓冲和 GOP=30 会堆出约 1 秒延迟。布控预览还要再经 Analyzer，更不能用大缓冲。
ffmpeg -hide_banner -fflags nobuffer -flags low_delay -use_wallclock_as_timestamps 1 -f dshow -rtbufsize 2M -framerate 15 -i video="ASUS FHD webcam" -c:v libx264 -pix_fmt yuv420p -preset ultrafast -tune zerolatency -profile:v baseline -bf 0 -g 15 -keyint_min 15 -an -flush_packets 1 -f flv rtmp://127.0.0.1:9995/live/webcam
```

这个窗口保持打开。不要再用别的软件打开同一颗摄像头。推上之后到 **设备管理 → 实时监控** 对「工位摄像头」点 **启用监控**（或再点一次预览）。没有推流时预览会黑屏转圈。

**延迟说明**：设备管理「实时监控」看的是 ZLM 源；布控管理看的是 Analyzer 画框后再编码回推的流。本机 CPU 睡岗大约 6–10 帧/秒，布控画面会比真人慢一点，这是推理链路，不是摄像头坏了。改过 ffmpeg 命令后要关掉旧窗口再开一条，布控再 **停止 → 启动**。

#### CCTV5（`cam555180`）

源是北邮台 HLS（`tv.byr.cn`）。到实时监控点 **启用监控**。拉不到时先关 VPN/加速再试；校园网以外可能 RST。

#### 睡岗 / 原 YOLO 布控

Analyzer 重启后：布控列表对该任务 **停止 → 启动**。录像引擎用 **算法服务器**。睡岗选 `on_sleep_pose`，**不要**选启发式「睡觉」。公式见 [algorithm-sleep.md](./algorithm-sleep.md)。

### 10.4 改过 Analyzer 源码之后（不是每次开机）

只在本机联调改了 `server/Analyzer` 时才需要：

```bash
# 在 WSL 里
cp /mnt/d/work/genshin\ impact/server/Analyzer/Core/*.h /opt/SVA-dev/server/Analyzer/Core/   # 按实际改过的文件拷
# 更稳：只拷改过的 cpp/h 到 /opt/SVA-dev
cd /opt/SVA-dev/server/build
cmake --build . -j"$(nproc)"
systemctl stop easysva-analyzer
cp /opt/SVA-dev/server/build/Analyzer /opt/SVA/server/Analyzer
systemctl start easysva-analyzer
```

然后回到 10.3：推流、启用监控、布控停止再启动。

### 10.5 不要做的

- 不要在本机跑官方 `install_source.sh` / CUDA `.run`  
- 不要把 `Analyzer-lib/`、`*.onnx`、`*.pt`、工位大视频推进 git  
- 不要把本机 8088 当成 A 演示机的 80/8080 写进验收记录  
- 不要 `git push` 到 `main`

---

## 11. 验收点 3：国标播放 URL 与「有注册无画面」

课件要求推理链路复用：国标不另起一套检测，只换 Analyzer 打开的 `streamUrl`。URL **由 backend 拼**，Analyzer 只拉。

国标行：`device_type=gb28181`。`DeploymentAnalyzerClient.resolveGbRtpPull` 从 `play_url` 解析 `/rtp/<stream>`；解析不到则用 `gb_device_id`（无下划线时拼成 `{id}_{id}`）。发给 Analyzer 的是：

```text
rtsp://{zlmHost}:{media_rtsp_port}/rtp/{设备_通道}
```

演示机业务库常见：`demo-ipc`（`ape_id=camgbf0b09a04`），stream 形如 `34020000001320000001_34020000001320000001`。网页预览走 Nginx `/rtp/` 的 WS-FLV，**不要**把 WS 地址塞给 Analyzer。

布控启动时 backend 对国标 `warmRtp`（后台 INVITE，不堵 HTTP）。ZLM 还没有该 rtp 时，拉流会触发 WVP `on_stream_not_found` 再 INVITE。Analyzer `stimeout` 25s 就是在等这场点播。

### 11.1 验证步骤（注册成功 → 分析器有画面 → 能布控）

以同学 A 演示机为准（本机 WSL24 通常没有 WVP，不要当验收环境装一套）：

1. 国标设备在线：业务列表 `device_type=gb28181`、`is_online=1`（跟 WVP SIP，不跟「此刻有没有画面」）。
2. 设备预览有画面（Nginx `/rtp/…live.flv`）。`getMediaList` 里应有 `app=rtp` 对应 stream。
3. 布控选该国标设备 + **原 YOLO**（`on_yolo11n_80` / `on_yolo26n_80`），画闭合区域，录像引擎用算法服务器。启动后 Analyzer 日志里 `streamUrl` 必须是 `rtsp://127.0.0.1:9994/rtp/…`，不能是 `live/camgb…`。
4. 布控预览出框，或告警列表能出原 YOLO 记录。睡岗放到单独联调，不要和本条验证绑死。

操作细节见 [国标功能使用说明书.md](./国标功能使用说明书.md)、[启动手册.md](./启动手册.md) §2.5–2.7。媒体半程可用 A 的 `scripts/verify_gb_rtp.sh`；Analyzer 能否打开那条 RTSP 用 `scripts/probe_analyzer_pull.sh`（另 PR）。

### 11.2 「有注册无画面」谁排

| 现象 | 归谁 | 怎么看 |
|------|------|--------|
| WVP / 列表已注册，`getMediaList` 没有 `rtp/{stream}` | 同学 A | SIP 未点播、模拟器没推 PS、INVITE 失败 |
| ZLM 有 rtp、网页预览有画，Analyzer 仍 `pull stream connect error` 或打开的是 `live/{apeId}` | 同学 B | 看下发的 `streamUrl`、FFmpeg 错误串、`stimeout` |
| 预览有画、Analyzer 已连上但 YOLO/睡岗不出 | 先确认区域与算法代号；公式问题归 B，前端选错归 C | 框是否画出、`on_sleep_pose` 是否加载 |

不要改 `zlm_server.host`（须 `127.0.0.1`）。不要把国标和睡岗大改塞进同一个 PR。
