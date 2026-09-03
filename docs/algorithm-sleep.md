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
  → 平滑 + 滞回：短低头=bow，连续低头≥3.5s=sleep；头仍在脖子上方或正脸朝镜头不报
  → 抗抖动：双肩必需、限制俯仰角尖峰；已低头后丢关键点继续计时（趴桌遮鼻），过久才清零
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

## 2. 俯仰角（正拍 / 侧拍同一套）

只靠鼻+双肩、用 **0.5×肩宽** 当尺子时：正面肩线稳定，侧脸肩宽会被透视压扁，角度会乱跳或偏小，看起来就像「正拍灵、侧拍钝」。

现在用完整 COCO-17（与 `SleepPose.h` / `geometry.py` 相同）：

1. **头点**：鼻尖 ≥ 0.25；否则眼 / 耳（置信度 ≥ 0.175）。趴桌挡住鼻子时还能算。  
2. **颈点**：双肩中点；只露出一侧肩就用那一侧。  
3. **躯干上方向**：有髋则 **髋→肩**（侧拍仍看得到躯干长）；没有髋才退回「肩线转 90°」。  
4. **尺子** = `max(0.42 × 躯干长, 0.5 × 肩宽)`，肩宽塌掉时不会把角度放大炸掉。  
5. **俯仰角** = `(1 - clamp(elev / 尺子, -0.6, 1.15)) × 90`，再限制在 0–135°。侧拍再叠图像 Y 下落；正拍不要用下落（笔记本俯视时肩宽尺子会把「看镜头」打到 60°+）。  
6. **直立门控**：头在脖子上方超过 `max(8px, 0.20×尺子)`，或鼻+双眼都在且头仍在脖子上方 → 角度封顶 18°（低于恢复阈值，不进低头）。趴桌时头到颈线附近，门控不生效。

沿中线低头时头向量方向几乎不变，不能用两向量夹角。坐直约 0°，头落到颈点附近约 90°。这是 2D 近似，不是 IMU 俯仰。笔记本摄像头从上往下拍、人脸贴近镜头时，旧公式会把正面朝镜头当成睡岗。

默认：

| 符号 | 默认 | 含义 |
| --- | --- | --- |
| `PITCH_DOWN_DEG` | 38 | 进入低头（原 32 对衣物起伏过松） |
| `PITCH_RECOVER_DEG` | 26 | 滞回：已低头后低于此值才开始「恢复计时」 |
| `SLEEP_HOLD_MS` | 3500 | 连续低头达到此时长才报睡岗 |
| `RECOVER_HOLD_MS` | 450 | 抬起必须稳住这么久才清零 |
| `MAX_MISSING_POSE_MS` | 10000 | 已低头后丢关键点超过此时长才清零；期间**继续**计时（趴桌遮鼻） |
| `MIN_CONFIRM_DOWN_FRAMES` | 3 | 连续确认低头才开始计时 |
| `PITCH_SMOOTH` | 上升 0.40 / 下降 0.38 | 限制尖峰，兼顾趴桌跟进 |
| `MIN_KEYPOINT_CONF` | 0.28 | 肩/髋/鼻最低置信度；颈点必须双肩都可用 |

布控规则若带 `sleep_on_duty`：`thresholdMs` = 持续时间；`distanceThresholdPx` 复用为低头角度；`directionToleranceDeg` 复用为滞回差（默认 12）。选了 `on_sleep_pose` 但没下发规则时，Analyzer / 前端都会补默认规则。**页面上可以不手填规则**；Analyzer 读到旧规则若低于上述默认，会抬到默认下限，避免 32°/2.5s 继续误报。

**不要**再额外加启发式类型「睡觉」（宽高比+低速），那不是 Pose 睡岗，衣物静止更容易误报。

---

## 3. 多帧：低头 vs 睡岗

每个 `trackId` 一份状态（与 Python `TemporalState` 相同）。判定用的是 **平滑后的角**，不是原始单帧。

- 平滑角 ≥ 38° 且连续确认 ≥ 3 帧 → 开始计时（`bow`）  
- 计时期间角 ≥ 26° → 连续  
- 连续时长 ≥ **3.5s** → **`sleep`**，上报  
- 角 < 26°：先进入 450ms 恢复宽限；宽限内仍算低头  
- 宽限结束仍抬起 → 清零（点头 / 衣物起伏结束，不报）  
- 某一帧关键点丢了：若**已在低头** → **继续计时**（趴桌遮住鼻子是常态）；超过 10s 仍丢点 → 清零  

睡岗布控的告警视频前缀从 30 帧收到 **10** 帧，减少「已经趴下却还在等缓存」的延迟。原 YOLO 布控仍是 30 帧。

验收：本地视频里睡岗要出事件，点头/看键盘/正面看镜头/短时衣服起伏不能出。正拍、侧拍趴桌应能过 38° 并在约 3.5 秒内变黄框。

告警封面用 **事件 start** 的那张图，不要用结束帧（坐直后会把趴桌盖掉）。视频路径在 start 时就带上 `alarm/.../evt-.../main.mp4`；Analyzer 不再丢掉排队中的告警片段。旧告警若 `video_url` 为空，点「播放视频证据」仍会提示不存在，那是当时没写成片，不是播放器坏了。

---

## 4. 告警 JSON（与 C 已确认的现网叠加）

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

## 5. 怎么测

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
2. 布控选 **睡岗检测**（`on_sleep_pose` + `person` + 闭合区域）；**行为规则可不手填**（选算法后前端/Analyzer 会自动补 `sleep_on_duty`）。若要调灵敏度，改该规则的持续时间（默认 3500ms）与角度（默认 38°）即可  
3. **不要**选启发式「睡觉」  
4. 录像引擎用 **算法服务器**；保存后到布控列表 **停止再启动**  
5. 推流画面：绿框=检出人，黄框写 `SLEEP`（带俯仰角，常带 `!`）=睡岗成立；真趴桌约 3.5 秒后应变黄  
6. 告警看 **告警管理**，类型 **睡岗** / `SLEEP_ON_DUTY`；布控页「最近事件」在推流模式下经常是空的  

网页：A 演示机 80/8080；本机 WSL24 联调用 `http://127.0.0.1:8088/`（Chrome/Edge）。**电脑重启后**先按 [architecture-analyzer.md 第 10 节](./architecture-analyzer.md) 拉起 WSL 服务、工位推流和布控，再测睡岗。

### 本机 WSL24 已测（2026-09-01，非正式演示机）

- 单元测试：`python -m unittest discover -s tests -v` 通过  
- 原型：工位 H.264 `desk-sleep.mp4` 能打出 `SLEEP`（正例；短低头另录反例）  
- Analyzer：`sleep_pose=1` 与原 YOLO 同时加载；`on_sleep_pose` 布控趴桌后黄框，修 `addFromSvaSimple` 回落后告警管理可见睡岗  
- 合入后仍须同学 A 在 Ubuntu 22.04 演示机编一遍并评论编译结果

### 验收点 2 还差什么（B 侧已收口后）

B 能改的已经在本仓库：公式、时序、ONNX 接入、告警 JSON 字段、封面/视频路径、本机 WSL 联调。不要改 `docs/当前阶段.md` 的 phase，等演示机过完由 C 关账。

| 谁 | 还要做 |
| --- | --- |
| 同学 B | 开 `[P2] B` PR（不要推 `main`）；PR 里写 A 的 cmake 命令 |
| 同学 A | 演示机 pull、拷 `yolo11n-pose.onnx`、插 `on_sleep_pose`、`-DSVA_ONNXRUNTIME_GPU=OFF` 编过并评论 |
| 同学 C | 告警页如需展示俯仰角再扩字段；phase 升 3 等 P2 演示过了再改 |

启发式「睡觉」不要当睡岗。国标不要塞进这个 PR。
