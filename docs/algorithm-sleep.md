# 睡岗算法（同学 B）

验收点 2。实现与阈值以本文 + `server/Analyzer/Core/SleepPose.h` + `algo/sleep-pose/` 为准。总图仍看 [architecture.md](./architecture.md)。

**不要**把 BehaviorEvaluator 里已有的启发式 `sleep`（框宽高比 + 低速）当成睡岗。P2 行为类型是 `sleep_on_duty`。

---

## 1. 链路

```text
H.264 流
  → 原管线解码（不改）
  → on_sleep_pose（YOLO11n-Pose ONNX，person）
  → 鼻尖 + 双肩算俯仰角
  → 多帧滞回：短低头=bow，连续低头≥3s=sleep
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

## 2. 俯仰角

COCO-17：鼻尖 `0`，左肩 `5`，右肩 `6`。三者置信度都 ≥ `0.25` 才算有效。

1. 颈点 = 双肩中点  
2. 肩向量旋转 90° 得到躯干「上」方向；若该向量在图像里朝下（Y 增大）则取反  
3. 头向量 = 鼻尖 − 颈点  
4. `elev` = 头向量在躯干上方向上的投影长度  
5. **俯仰角** = `(1 - clamp(elev / (0.5 × 肩宽), -0.5, 1)) × 90`

沿中线低头时头向量方向几乎不变，不能用两向量夹角。坐直约 0°，鼻尖落到颈点附近约 90°。这是 2D 近似，不是 IMU 俯仰。

默认：

| 符号 | 默认 | 含义 |
| --- | --- | --- |
| `PITCH_DOWN_DEG` | 35 | 进入低头 |
| `PITCH_RECOVER_DEG` | 25 | 滞回：已低头后低于此值才恢复 |
| `SLEEP_HOLD_MS` | 3000 | 连续低头达到此时长才报睡岗 |
| `MIN_KEYPOINT_CONF` | 0.25 | 关键点最低置信度 |

布控规则若带 `sleep_on_duty`：`thresholdMs` = 持续时间；`distanceThresholdPx` 复用为低头角度；`directionToleranceDeg` 复用为滞回差（默认 10）。选了 `on_sleep_pose` 但没下发规则时，Analyzer 会补一条默认规则。

---

## 3. 多帧：低头 vs 睡岗

每个 `trackId` 一份状态（与 Python `TemporalState` 相同）：

- 俯仰角 ≥ 35° → 开始计时（`bow`）  
- 计时期间角 ≥ 25° → 连续  
- 连续时长 ≥ 3s → **`sleep`**，上报  
- 角 < 25° → 清零（普通低头结束，不报）  
- 某一帧关键点丢了 → **不清零**，避免眨眼式漏检把睡岗拆断  

验收：本地视频里睡岗要出事件，点头/看键盘那种短低头不能出。

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
  "durationFrames": 0
}
```

backend（#6）命中任一即入库为睡岗：`alarmType=SLEEP_ON_DUTY`，或 `behavior_type=sleep_on_duty`，或 `customEventName=睡岗`。`durationFrames` 写入已有 `duration_ms`（按 40ms/帧）。不要新建表。

启发式 `behavior_type=sleep` **不要**当睡岗。

---

## 5. 怎么测

### Windows 原型

```powershell
cd algo\sleep-pose
python -m pip install -r requirements.txt
python -m unittest discover -s tests -v
python -m sleep_pose.detect --video <H.264工位视频> --device cpu
```

### Analyzer（A 的 Ubuntu 或本机 WSL 编译，Windows 不当验收机）

```bash
# 模型放到 /opt/SVA/models/yolo11n-pose.onnx
# MariaDB 插入 on_sleep_pose（语句见 architecture.md）
cd /opt/SVA-dev/server
mkdir -p build && cd build
cmake .. -DCMAKE_BUILD_TYPE=Release -DSVA_ONNXRUNTIME_GPU=OFF
cmake --build . -j"$(nproc)"
```

1. 布控原 YOLO（`on_yolo11n_80`）仍能出框/告警  
2. 布控选睡岗（`on_sleep_pose` + `person` + 区域）  
3. 睡岗视频出 `SLEEP_ON_DUTY`；短低头不出  

网页：A 演示机 80/8080；本机 WSL24 联调用 `http://127.0.0.1:8088/`（Chrome/Edge）。
