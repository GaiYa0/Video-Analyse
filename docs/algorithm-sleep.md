# 睡岗算法（同学 B）

验收点 2。实现与阈值以本文 + `server/Analyzer/Core/SleepPose.h` + `algo/sleep-pose/` 为准。总图仍看 [architecture.md](./architecture.md)。

**不要**把 BehaviorEvaluator 里已有的启发式 `sleep`（框宽高比 + 低速）当成睡岗。P2 行为类型是 `sleep_on_duty`。

---

## 1. 链路

```text
H.264 流
  → 原管线解码（不改）
  → on_sleep_pose（YOLO11n-Pose ONNX，person）
  → 头点 + 颈点 + 髋/肩，正侧拍共用俯仰角
  → 平滑 + 滞回：短低头=bow，连续低头≥5s=sleep；正脸朝镜头且头仍在脖子上方不报
  → 原告警 HTTP：behavior_type=sleep_on_duty
```

原算法 `on_yolo11n_80` / `on_yolo26n_80` 仍加载、仍走原来的框检测解码，**不替换、不改输出维**。

| 项 | 值 |
| --- | --- |
| 算法代号 | `on_sleep_pose`（C 已写进架构文与 `av_algorithm` 插入语句） |
| 模型文件 | `{modelDir}/yolo11n-pose.onnx`（`*.onnx` 不进 git） |
| 目标类 | `person` |
| 行为类型 | `sleep_on_duty` |
| 告警类型 | `SLEEP_ON_DUTY` / 展示名「睡岗」 |

模型不存在时 Analyzer **照常启动**原 YOLO；选睡岗会打「不支持的算法」，这是预期。

---

## 2. 帧质量门（先决定这一帧算不算数）

一帧过不了下面任何一条，就当**这一帧没有俯仰角**——注意这不等于「此人是直立的」，坏帧永远不会把人往睡岗方向推。

1. **人体够大**：框高 ≥ 0.18 × 画面高，且置信度 ≥ 0.25 的关键点 ≥ 8 个。不够只画绿框，不进睡岗判定。
2. **头点**：鼻，或双眼中点，或双耳中点。只有单只眼/耳时，必须同时看得到髋。
3. **颈点**：双肩中点；只有单肩时，必须同时看得到髋。单肩会把颈点拉到一侧，直立门控就废了。
4. **平均置信度** ≥ 0.35（参与计算的那些点）。
5. **尺子** ≥ 24px。
6. **不跳变**：原始角相对上一帧跳过 45° 的帧按丢帧处理。

## 3. 俯仰角（正拍 / 侧拍同一套）

只靠鼻+双肩、用 **0.5×肩宽** 当尺子时：正面肩线稳定，侧脸肩宽会被透视压扁，角度会乱跳或偏小，看起来就像「正拍灵、侧拍钝」。

现在用完整 COCO-17（与 `SleepPose.h` / `geometry.py` 相同）：

1. **躯干上方向**：有髋则 **髋→肩**（侧拍仍看得到躯干长）；没有髋才退回「肩线转 90°」。  
2. **尺子** = `max(0.42 × 躯干长, 0.5 × 肩宽)`，肩宽塌掉时不会把角度放大炸掉。  
3. **俯仰角** = `(1 - clamp(elev / 尺子, -0.6, 1.15)) × 90`，再限制在 0–135°。侧拍再叠图像 Y 下落；正拍不要用下落（笔记本俯视时肩宽尺子会把「看镜头」打到 60°+）。  
4. **直立门控**：正脸（鼻+双眼）且头仍在脖子上方 8px 以上 → 角度封顶 18°。头高出颈线超过 **0.50 × 尺子** 也会封顶。笔记本俯视摄像头上，脸埋进手臂时往往不再是正脸，门控应放开；只是低头看屏幕、正脸对着镜头，永远不会报。
5. **缺髋加价**：没有髋时，进入低头的阈值 **+6°**。桌子挡住髋是常态，而这时的尺子来自旋转肩线，本身就不可靠。

沿中线低头时头向量方向几乎不变，不能用两向量夹角。坐直约 0°，头落到颈点附近约 90°。这是 2D 近似，不是 IMU 俯仰。

默认：

| 符号 | 默认 | 含义 |
| --- | --- | --- |
| `PITCH_DOWN_DEG` | 32 | 进入低头（无髋时 38） |
| `PITCH_RECOVER_DEG` | 22 | 滞回：已低头后低于此值才开始「恢复计时」 |
| `SLEEP_HOLD_MS` | **5000** | 连续低头达到此时长才可能报睡岗 |
| `RECOVER_HOLD_MS` | 600 | 抬起必须稳住这么久才清零 |
| `PITCH_SMOOTH` | 上升 0.55 / 下降 0.28 | 低头跟上快，抬起滤波慢 |
| `MIN_KEYPOINT_CONF` | 0.25 | 单点最低置信度 |
| `MIN_BODY_SCALE_PX` | 24 | 尺子下限 |
| `MAX_HEAD_ABOVE_NECK_RATIO` | 0.50 | 非正脸时，头最多高出颈线这么多还能算低头 |

布控规则若带 `sleep_on_duty`：`thresholdMs` = 持续时间；`distanceThresholdPx` 复用为低头角度；`directionToleranceDeg` 复用为滞回差（默认 10）。选了 `on_sleep_pose` 但没下发规则时，Analyzer 会补一条默认规则（5 秒）。

**注意**：布控页现在会自动写一条 `sleep_on_duty`，持续时间**写死 2500ms**，会盖掉 Analyzer 的 5 秒默认值。本机这条布控若 `behaviorRules` 为空，用的就是 Analyzer 的 5 秒。页面默认值在 `web/`，归同学 C。滞回差在页面上没有输入框，恒等于 `低头角 − 10`。

---

## 4. 多帧：低头 vs 睡岗

每个 `trackId` 一份状态（与 Python `TemporalState` 相同）。判定用的是 **平滑后的角**，不是原始单帧。

进入与退出：

- 平滑角 ≥ 32°（无髋 38°）→ 开始计时（`bow`），记下头点锚和尺子  
- 计时期间角 ≥ 22° → 连续  
- 角 < 22°：先进入 600ms 恢复宽限；宽限内仍算低头  
- 宽限结束仍抬起 → 清零（点头结束，不报）  
- 连续丢 pose 超过 **1200ms** → **清零**。以前这里让计时继续走，人走开了也能凑满时长  

到时长只是必要条件。要报 `sleep`，窗口内还必须同时满足：

| 门 | 条件 | 挡住什么 |
| --- | --- | --- |
| 占空比 | 低头时间 / (低头 + 短暂抬头) ≥ **0.80** | 打字、看资料时角度反复出入阈值带 |
| 峰值角 | 平滑角至少到过 **45°** | 浅低头看键盘（33° 左右）永远不够 |
| 头点静止 | 相对锚点最大漂移 ≤ **0.45 × 尺子** | 看手机、写字这类「头低但一直在动」 |
| 有效帧 | ≥ **3** 帧真实姿态 | 靠单帧凑出来的时长。低帧率下 12 帧等于十几秒，会把已经趴下的人挡住 |
| 丢帧占比 | 累计缺失 / 窗口 ≤ **0.35** | 中途被挡、人走开 |

告警前的第二道门（`isSleepOnDutyHit`）**不再**看运动状态、躯干速度、轨迹年龄或 `dwellMs`。现网只认：`sleepOnDuty` 已成立、轨迹有效、人在闭合区域内、且 `headDownMs` ≥ 规则时长（空规则时 5000ms）。运动门已从 `BehaviorEvaluator.cpp` 拿掉，避免低帧率或轻微晃动把已经趴下的人挡掉。

睡岗布控的告警视频前缀从 30 帧收到 **10** 帧，减少「已经趴下却还在等缓存」的延迟。原 YOLO 布控仍是 30 帧。

验收：本地视频里睡岗要出事件，点头/看键盘/看手机/正面看镜头/背景里的人都不能出。正拍、侧拍趴桌都应能过 32° 并在约 5 秒后变黄框。绿框 `UP <角度>` = 还在坐直；橙框 `BOW <角度> <秒数>` = 已经在计时；黄框 `SLEEP <角度> <秒数>` = 睡岗成立。成立那一刻 Analyzer 日志会打一行 `sleep_on_duty track=… peak=… downRatio=… drift=…`。

告警封面用 **事件 start** 的那张图，不要用结束帧（坐直后会把趴桌盖掉）。视频路径在 start 时就带上 `alarm/.../evt-.../main.mp4`；Analyzer 不再丢掉排队中的告警片段。旧告警若 `video_url` 为空，点「播放视频证据」仍会提示不存在，那是当时没写成片，不是播放器坏了。

---

## 5. 告警 JSON（与 C 已确认的现网叠加）

入口不变：`POST /waring/waring/addFromSvaSimple`。仍带原告警媒体字段，再叠加睡岗识别。设备靠 `control_code` 反查，不靠 `deviceId` 入库。

```json
{
  "control_code": "<deploymentId>",
  "behavior_type": "sleep_on_duty",
  "alarmType": "SLEEP_ON_DUTY",
  "customEventName": "睡岗",
  "rule_id": "",
  "desc": "",
  "video_path": "alarm/.../main.mp4",
  "image_path": "alarm/.../main.jpg",
  "confidence": 0.0,
  "pitchDegree": 0.0,
  "durationFrames": 0,
  "duration_ms": 0
}
```

Analyzer 在 `addFromSvaSimple` 和 `detect.event` 都会带上 `confidence`（YOLO 分数）、`pitchDegree`、`durationFrames`，并优先带墙钟 `duration_ms`（`headDownMs`）。backend（#6）命中任一即入库为睡岗：`alarmType=SLEEP_ON_DUTY`，或 `behavior_type=sleep_on_duty`，或 `customEventName=睡岗`。有 `duration_ms` 直接写入；否则用 `durationFrames × 40ms`。`pitchDegree` 现网尚未入库（归 C）。不要新建表。

启发式 `behavior_type=sleep` **不要**当睡岗。

---

## 6. 怎么测

### Windows 原型

```powershell
cd algo\sleep-pose
python -m pip install -r requirements.txt
python -m unittest discover -s tests -v
python -m sleep_pose.detect --video <H.264工位视频> --device cpu
python -m sleep_pose.export_onnx --weights yolo11n-pose.pt --out yolo11n-pose.onnx
```

`.pt` / `.onnx` / 工位 mp4 **不要提交**。首次导出若 GitHub 下不动权重，可用镜像拉 `yolo11n-pose.pt`。

### Analyzer（A 的 Ubuntu 演示机为准；本机 WSL 只作联调）

```bash
# 拷模型
# /opt/SVA/models/yolo11n-pose.onnx
# MariaDB 插入 on_sleep_pose（语句见 architecture.md；A 演示机常见 3307，本机 WSL24 为 3306 / easySVA）
cd /opt/SVA-dev/server
mkdir -p build && cd build
cmake .. -DCMAKE_BUILD_TYPE=Release -DSVA_ONNXRUNTIME_GPU=OFF
cmake --build . -j"$(nproc)"
cp Analyzer /opt/SVA/server/Analyzer
# 再重启 Analyzer 服务
```

1. 布控原 YOLO（`on_yolo11n_80` / `on_yolo26n_80`）仍能出框/告警  
2. 布控选 **睡岗检测**（`on_sleep_pose` + `person` + 闭合区域）；空规则时 Analyzer 默认 **5 秒**（页面自动写的是 2500，会盖掉默认值）  
3. **不要**选启发式「睡觉」  
4. 录像引擎用 **算法服务器**；保存后到布控列表 **停止再启动**  
5. 推流画面：绿框=检出人，黄框写 `SLEEP <角度> <秒数>`（常带 `!`）=睡岗成立  
6. 告警看 **告警管理**，类型 **睡岗** / `SLEEP_ON_DUTY`；布控页「最近事件」在推流模式下经常是空的  
7. 反例要一条条走：打字看资料 5 秒、低头看手机、正脸看镜头、走开留空座位、背景里站个人——都不该出告警  

网页：A 演示机 80/8080；本机 WSL24 联调用 `http://127.0.0.1:8088/`（Chrome/Edge）。**电脑重启后**先按 [architecture-analyzer.md 第 10 节](./architecture-analyzer.md) 拉起 WSL 服务、工位推流和布控，再测睡岗。

### 本机 WSL24 已测（2026-09-01，非正式演示机）

- 单元测试：`python -m unittest discover -s tests -v` 通过  
- 原型：工位 H.264 `desk-sleep.mp4` 能打出 `SLEEP`（正例；短低头另录反例）  
- Analyzer：`sleep_pose=1` 与原 YOLO 同时加载；`on_sleep_pose` 布控趴桌后黄框，修 `addFromSvaSimple` 回落后告警管理可见睡岗  
- 合入后仍须同学 A 在 Ubuntu 22.04 演示机编一遍并评论编译结果

### 降误报改版（2026-09-03）

持续时间现为 **5 秒**，并加了帧质量门、几何硬条件和窗口证据（占空比 / 峰值 / 静止 / 丢帧预算）。趴下过程中的头部位移不再花掉静止预算。

演示时要在画好的区域里：**脸颊或额头落到桌面/键盘方向**（不要只是低头看屏幕），正脸离开镜头，框上出现 `BOW` 后再稳住 5 秒。只对镜头坐着时框上是 `UP 18`，计时不会走。

### 验收点 2 还差什么（B 侧已收口后）

B 能改的已经在本仓库：公式、时序、ONNX 接入、告警 JSON 字段、封面/视频路径、本机 WSL 联调。不要改 `docs/当前阶段.md` 的 phase，等演示机过完由 C 关账。

| 谁 | 还要做 |
| --- | --- |
| 同学 B | 开 `[P2] B` PR（不要推 `main`）；PR 里写 A 的 cmake 命令 |
| 同学 A | 演示机 pull、拷 `yolo11n-pose.onnx`、插 `on_sleep_pose`、`-DSVA_ONNXRUNTIME_GPU=OFF` 编过并评论 |
| 同学 C | 告警页如需展示俯仰角再扩字段；phase 升 3 等 P2 演示过了再改 |

启发式「睡觉」不要当睡岗。国标不要塞进这个 PR。
