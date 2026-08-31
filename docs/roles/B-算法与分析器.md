# 角色手册：同学 B（算法与分析器）

给协作 AI：你正在协助**同学 B**。先读 [../分工.md](../分工.md) 和 [../PR规范.md](../PR规范.md)。未经用户明确要求，不要改 `web/`、设备表、布控页、ZLM 国标配置。

## 一句话

主攻睡岗算法，并和同学 A 在 Ubuntu 虚拟机上把 C++ 集成做完；第一、三阶段也要有分析器侧的可验证产出，不能只交一段视频或改一行 URL。

## 机器

- 自己的电脑：Windows，做 Python 原型（建议 Python + ultralytics）
- **不要**在自己电脑上当验收环境编译整套 easySVA
- C++ / Analyzer 只在同学 A 的 Ubuntu 22.04 x86_64 虚拟机编译
- 源码和 ONNX 必须推进 GitHub，再让 A 在 `/opt/SVA-dev` pull
- 测试视频：H.264（当前平台预览不支持 H.265），放 `docs/fixtures/` 或网盘（大文件不要塞进 git）

## 可以改

- `server/`（Analyzer、取流、推理管线、国标播放 URL）
- 睡岗 Python 原型目录（例如 `algo/sleep-pose/`，自建时在 README 写清依赖）
- `server/models/` 或项目约定的模型目录（ONNX）
- `docs/fixtures/`
- `docs/architecture-analyzer.md`、`docs/algorithm-sleep.md`

## 不要改

- `web/`
- `backend/` 的库表、布控、告警入库（字段你起草，实现归 C）
- `mediaServer/conf` 国标 SIP 段（归 A）
- A 的部署笔记、C 的架构总图正文

## 和谁对接

- 和 A：验收点 1 结对盯 Analyzer 编译；验收点 2 你出 ONNX 和接入代码，A 在虚拟机编过才算完成；验收点 3 你排「国标已注册但分析器没画面」
- 和 C：你起草睡岗告警 JSON（类型名、置信度、关键点/角度、持续帧数、截图路径），C 确认后才改库和页面。你不能直接改表
- 冻接口：告警 JSON 由你起草、C 确认；确认前不要让 C 的 AI 猜字段

## 四个验收点

### 验收点 1（现在，禁止做睡岗业务代码）

1. 准备一段 H.264 工位/人物视频，无摄像头时供伪造 RTSP
2. 和 A 一起把官方脚本里的 Analyzer 编起来，记录模型路径与启动命令
3. 自己在虚拟机上验证：原 YOLO 布控能出告警截图
4. 写 `docs/architecture-analyzer.md`：Analyzer 如何取流、模型在哪、告警如何 HTTP/WebSocket 打到 backend（尽量指到仓库文件路径）

可以先在 Windows 安装 Python + ultralytics，但不要把睡岗判定合进主流程。

### 验收点 2（你主责）

YOLO-Pose 识别关键点，算头部俯仰角，多帧时序区分正常低头与睡岗。本地视频：睡岗要报，普通低头不报。导出 ONNX，接入 `server/` 原有管线，上报告警且**原 YOLO 不受影响**。

### 验收点 3

Analyzer 按设备类型向 ZLM 取播放 URL（课件：推理链路复用）。写出验证步骤：国标注册成功 → 分析器有画面 → 能布控。出现「有注册无画面」由你排。

### 验收点 4

RTSP 与国标两种源都能睡岗告警；回归原 YOLO。算法说明由你完稿，交给 C 串进 PPT/视频，不要让 C 代写公式和阈值。

## 建议的告警 JSON 草案（验收点 2 再定稿）

C 改库前必须和你对过一版。示例字段，实现时以你们签字的终稿为准：

```json
{
  "alarmType": "SLEEP_ON_DUTY",
  "deviceId": "",
  "confidence": 0.0,
  "pitchDegree": 0.0,
  "durationFrames": 0,
  "snapshotPath": ""
}
```

## 开 PR 时

标题带验收点和 `B`。含 C++ 的 PR 必须写「A 在虚拟机上的编译命令」，并等 A 评论编译结果或自己在虚拟机编过再标 Ready。完整规则见 [../PR规范.md](../PR规范.md)。
