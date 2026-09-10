# 答辩 PPT 要点：优化点与创新点（给同学 C）

给 **PPT / 结题视频旁白** 用。每节建议 **1 页**，口令可直接念。算法数字以 [algorithm-sleep.md](./algorithm-sleep.md) 为准；国标链路以 [国标功能使用说明书.md](./国标功能使用说明书.md) 为准。  
**不要**另编阈值，**不要**写成「我们实现了 SIP 协议栈 / ZLM 把视频转码成 FLV」。

作者：A 整理现网已落地能力（P1–P4）。合稿、版式、截图裁切归 C。

---

## 0. 封面可用的一句话

> 在原 easySVA 上增量：用开源 WVP 做国标信令、ZLM 做媒体转封装，业务系统只同步已注册通道；睡岗用 YOLO-Pose 俯仰角 + 2s/5s/15s 三档时序，国标与直连共用同一套检测，只换拉流地址。

三人贡献（一页三人照片旁）：

| 角色 | 一句话 |
| --- | --- |
| A | 演示机、国标 SIP/媒体、一键启动、局域网预览 |
| B | 睡岗算法、Analyzer 接入、质量分与三档上报 |
| C | 设备/布控/告警页、国标同步按钮、HTTP-FLV 播放、合稿 |

---

## 1. 总体定位（先讲边界，再讲增量）

**原系统已经有的（紫色，不要说是我们从零做的）：** RTSP/直连预览、ZLM 转封装、Analyzer 原 YOLO、告警入库、Vue 后台。

**本组增量（蓝色）：**

1. **睡岗检测**（P2）：Pose + 多帧，不替换原 YOLO  
2. **国标接入**（P3）：真 SIP 注册 + 业务同步 + 预览/布控  
3. **联调交付**（P4）：双源睡岗、告警质量分/三连图、AI 复核、严重档邮件、手册与演示

课件要求「推理复用」：检测仍在 Analyzer；国标只换 `live/<apeId>` → `rtp/<设备_通道>`。

---

## 2. 创新点（建议 4 页，这是 PPT 主菜）

### 2.1 国标：信令 / 媒体 / 业务 三拆（不要自研 SIP）

口令：

> 我们没有在 easySVA 里自己实现 GB28181。摄像机向 WVP 的 5060 做 Digest 注册；ZLM 只收 PS/RTP 并转封装；Java 后台用 HTTP 问 WVP 目录，把通道写入设备表。

| 层 | 谁 | 协议 | PPT 上写 |
| --- | --- | --- | --- |
| 信令 | 开源 WVP | SIP :5060 | 注册、保活、目录、INVITE |
| 媒体 | 原 ZLM | RTP → FLV/RTSP | 解 PS、转封装，不重编码 |
| 业务 | easySVA | HTTP JSON | 「同步国标设备」才进 `h_device` |

三句必须分开（防老师追问）：

1. **注册成功 ≠ 业务页有设备**（还没同步）  
2. **业务页有设备 ≠ 有画面**（还没点播）  
3. **WVP 能播 ≠ 业务页能播**（Jessibuca 直连 :9992；业务页 flv.js 走 :8080 HTTP-FLV）

代码入口：`Gb28181DeviceSyncServiceImpl.syncFromWvp`、`POST /waring/device/syncGb28181`。

### 2.2 无真机时仍是「真国标」：最小 IPC 模拟器

口令：

> 现场没有真机 IPC 时，不用在数据库里假写在线。模拟器按国标发 REGISTER / Catalog / INVITE，ffmpeg 把测试片打成 MPEG-2 PS 再推 RTP。Wireshark 能抓到完整信令。

对比（可做左右栏）：

| 错误做法 | 本组做法 |
| --- | --- |
| SQL 改 `wvp_device` 假在线 | `scripts/gb28181_sim.py` 真 REGISTER |
| 推裸 H.264 RTP | `-f vob` + RTP PT=96（`a=rtpmap:96 PS/90000`） |
| 只给 WVP 网页看 | 同步进业务页 `demo-ipc`，Analyzer 拉同一条 `rtp/` 流 |

抓包文件（演示用）：`E:\video-analysis\gb28181-sip.pcap`，Wireshark 过滤 `sip`。

### 2.3 睡岗：几何俯仰角 + 三档时序，而不是「框变扁」

口令：

> 不是用检测框宽高比猜睡觉。用 YOLO11n-Pose 的头/颈/髋算俯仰角，连续低头分三档：2 秒疑似、5 秒确认、15 秒严重。正脸看镜头封顶 18°，避免看屏幕误报。

| 点 | 数字（不要改） |
| --- | --- |
| 进入低头 | ≥ **32°**（无髋 38°） |
| 三档 | **2s / 5s / 15s**，只升不降 |
| 质量分 | **0–100**，列表排序用，**不参与**是否报警 |
| 反例 | 打字、看手机、看镜头、空座位、背景人 |

公式与反例细节：[algorithm-sleep.md](./algorithm-sleep.md)「交给 C 进 PPT」五条。

### 2.4 推理复用：两种源、同一套阈值

口令：

> 国标不改 32° 和三档时间。Analyzer 打开的地址不同：直连是 `rtsp://…/live/<id>`，国标是 `rtsp://…/rtp/设备_通道`。原 YOLO 仍然保留，布控可以选杯子出框。

| 源 | Analyzer `streamUrl` |
| --- | --- |
| 工位 / 直连 | `rtsp://127.0.0.1:9994/live/<apeId>` |
| 国标 demo-ipc | `rtsp://127.0.0.1:9994/rtp/34020000001320000001_34020000001320000001` |

代码：`DeploymentAnalyzerClient.resolveGbRtpPull` / `buildStreamUrl`。

---

## 3. 优化点（建议 3 页，偏工程，有对比更好）

### 3.1 预览可看、可局域网：HTTP-FLV + Hook

| 问题 | 优化 |
| --- | --- |
| Windows `8080→80` 升不了 WebSocket，画面转圈 | 业务页统一 **HTTP-FLV**，Nginx `/live/` `/rtp/` 反代 ZLM |
| 国标流名先是 8 位 SSRC hex | ZLM `on_publish` 指回 WVP，`stream_replace=设备_通道` |
| 工位 RTMP 被播放鉴权 401 | 清空 `on_play`，`push-authority=false`（**不清空** `on_publish`） |
| 预览冻帧 | SIP Via/`rport`、100 Trying 不加 To-tag、RTP 用单调时钟、flv.js stall 自愈 |
| 点了「启动监控」没有画面 | 国标启动监控 **只改状态**；看画面必须点 **预览视频**（`warmRtp` 才 INVITE） |

代码：`web/src/utils/flvPlayer.js`、`scripts/start_wvp.sh` 的 `HOOK_WVP`、`HDeviceServiceImpl.previewMonitor`。

### 3.2 告警可讲、可复核：三连图 + 质量分 + AI + 邮件

| 能力 | 一句话 | 注意 |
| --- | --- | --- |
| 关键帧三连图 | 起始 / 峰值（头最低）/ 结束，详情「查看三连图」 | 封面须是 `main.jpg` 才拼 `keyframes.jpg` |
| 质量分 | 列表一列，详情 `xx / 100` | 证据硬度，不是误报分数 |
| AI 复核 | 读 Nginx `/alarm/` 封面，结论进详情 | 去掉「AI 误报分数」展示 |
| 邮件 | 仅 **首次升到严重档** 发一封，可内嵌关键帧 | 授权码不进 Git |

对应合入：#80 三连图、#79 质量分/邮件/AI、#81 前端展示与读图。

### 3.3 演示可复现：一键栈 + 自检

口令：

> 答辩机一条命令拉齐业务栈、WVP 和国标模拟器，结束时跑自检：进程、Hook、命名 rtp 流。

```powershell
.\scripts\start_demo.ps1 -WithGbSim
```

关栈：`scripts/stop_all.sh`（不停 MariaDB / Redis / Nginx）。  
自检：`check_demo_stack.sh --dual-sleep`。

---

## 4. 老师追问「解码转码」时（半页即可）

先分三个词：

| 词 | 谁做 |
| --- | --- |
| 编码 | 模拟器 ffmpeg 把测试片压成 H.264 再打进 PS（预览前唯一一次） |
| 解复用 / 转封装 | ZLM `GB28181Process` 拆 PS，同一份 H.264 装进 FLV 或 RTSP |
| 解码成画面 | Analyzer（检测）和浏览器 flv.js（预览） |

**不要写：**「ZLM 转码成 FLV」。`getMediaList` 视频编码仍是 H.264。  
ZLM 源码里的 decoder 是 **PS 解复用器**，不是解成 RGB。

---

## 5. 建议 PPT 页序（约 12 页）

1. 题目 / 三人分工  
2. 需求：睡岗 + 国标，原 YOLO 必须还在  
3. 总架构：设备 → ZLM → Analyzer → 后台 → Vue（标紫/蓝）  
4. **创新** 国标三拆  
5. **创新** 真 SIP 模拟器 + 抓包  
6. **创新** 睡岗俯仰角与三档  
7. **创新** 双源同一公式  
8. **优化** 预览 HTTP-FLV 与 Hook  
9. **优化** 告警三连图 / 质量分 / 邮件  
10. 演示截图：注册 → 同步 → 预览 → 布控告警  
11. 测试与反例  
12. 总结 + 未做（真机 IPC、不自研 SIP 栈）

截图目录：`docs/photo/`（P2 睡岗、P3 国标在线/同步/预览）。

---

## 6. 演示操作页（C 可做成「现场 2 分钟」）

1. 业务 `http://localhost:8080/`　`admin` / `admin123`  
2. 设备管理 **demo-ipc** → **预览视频**（不要只点启动监控）  
3. 布控 `on_sleep_pose`，录像引擎选算法服务器  
4. 告警列表看类型、质量分、三连图  
5. （可选）WVP `http://127.0.0.1:18080/` 对照「有设备」；强调两条播放器不是一条路  

局域网同学：`http://<A的IP>:8080/`，先关 Clash。

---

## 7. 不要写进 PPT 的话

- 「我们实现了 GB28181 / 写了 SIP 协议栈」→ 改为「接入开源 WVP，业务用 HTTP 消费目录」  
- 「ZLM 自己听 5060」→ 演示机 ZLM **无内置 SIP**  
- 「启动监控就是接入成功」→ 国标看画面要点预览  
- 「质量分决定是否报警」→ 质量分只排序  
- 「AI 误报分数」→ 已从详情拿掉  
- 把 BehaviorEvaluator 里启发式 `sleep` 当成睡岗  
- SMTP 授权码、DashScope Key、WVP/业务密码明文大字贴在幻灯片上  

---

## 8. 老师要看代码时（一页索引）

按「注册 → Hook → 同步 → 预览 → 播放器 → 布控」打开，**搜函数名**：

| 讲什么 | 文件 | 搜 |
| --- | --- | --- |
| SIP 注册 / INVITE | `scripts/gb28181_sim.py` | `serve` / `handle_invite` |
| ZLM Hook | `scripts/start_wvp.sh` | `HOOK_WVP` |
| 进业务库 | `Gb28181DeviceSyncServiceImpl.java` | `syncFromWvp` |
| 预览催流 | `HDeviceServiceImpl.java` | `previewMonitor` |
| 业务播放器 | `web/src/utils/flvPlayer.js` | `createLiveFlvPlayer` |
| Analyzer 拉流 | `DeploymentAnalyzerClient.java` | `resolveGbRtpPull` |
| 睡岗阈值 | `server/Analyzer/Core/SleepPose.h` | 与 algorithm-sleep.md 对照 |

---

## 9. 材料从哪拷

| 用途 | 文档 |
| --- | --- |
| 本文（PPT 骨架） | 本文件 |
| 睡岗五句 + 反例 | [algorithm-sleep.md](./algorithm-sleep.md) §7 |
| 国标口述 | [P3验收现场讲解稿.md](./P3验收现场讲解稿.md) |
| 开机 | [启动手册.md](./启动手册.md) §1 |
| 架构总图 | [architecture.md](./architecture.md) |
