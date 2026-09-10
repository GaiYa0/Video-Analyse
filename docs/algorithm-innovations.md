# 同学 B：睡岗相关创新点

本文整理同学 B 在 easySVA 二次开发中落地的**创新点**（不是 PPT 提纲）。实现与阈值以 [algorithm-sleep.md](./algorithm-sleep.md) 和 `server/Analyzer/Core/SleepPose.h` 为准。总图仍看 [architecture.md](./architecture.md)。

「库」一律指演示机 MariaDB 业务库 `easySVA`（常见端口 3307）。不是每条数据都进表：有的在磁盘、有的在 `sys_config`、有的请求时现算。

启发式行为类型 `sleep`（框宽高比 + 低速）**不是**睡岗，不要写进创新。P2 行为类型是 `sleep_on_duty`。

---

## 0. 这些创新挂在哪条链上

创新都叠在同一条睡岗链上，不另起检测进程，也不换原 YOLO。

```text
直连 live / 国标 rtp（H.264）
  → Analyzer 解码（FFmpeg）
  → on_sleep_pose（YOLO11n-Pose ONNX，COCO-17）
  → SleepPose：帧质量门 → 俯仰角 → 多帧时钟 → 证据门
  → 档位 sleepLevel + 质量分 sleepScore
  → 回流画框（可选推回 ZLM app=analyzer）
  → WebSocket detect.event → backend :9114 → 表 h_waring
  → 磁盘 main.jpg / main.mp4 / keyframes.jpg → Nginx /alarm/
```

- Analyzer 监听 `:9993`（布控下发、健康检查）。拉流走 ZLM RTSP `:9994`。
- 告警事件走 `ws://127.0.0.1:9114/websocket/sva/noop`；媒体回调走 `POST /waring/waring/addFromSvaSimple`（或已 bind 后的 media callback）。
- 设备靠 `control_code`（布控任务 id）反查，不靠摄像头编号入库。
- 国标与直连**公式相同**，只换 URL：`live/<ape_id>` ↔ `rtp/<设备_通道>`。Analyzer 不做 SIP。

技术栈：C++ Analyzer（OpenCV + ONNX Runtime + FFmpeg + libevent）；Java Spring Boot / 若依 + MyBatis；Vue 告警页。Python `algo/sleep-pose/` 是孪生原型，`tests/test_cpp_parity.py` 对齐常量，线上不跑 Python 推理。

---

## 1. 三档分级（2 秒疑似 / 5 秒确认 / 15 秒严重）

### 要解决什么

原先只有「连续低头满 5 秒则报、否则不报」。刚趴下和已经趴死在列表里看不出差别，也无法按严重程度通知。

### 怎么判定（先过门，再分档）

每个 `trackId` 一份时序状态。平滑俯仰角进入低头约 **32°**（看不见髋再加 **6°**），滞回恢复 **22°**，抬起需稳住 **600ms** 才清零。正脸朝镜头且头仍在脖子上方时，角度封顶 **18°**，计时不会走。

连续低头时长只是必要条件。要标成睡岗，窗口内还必须同时满足证据门（三档共用，**不因计到 15 秒而放松**）：

| 门 | 阈值 | 挡住什么 |
| --- | --- | --- |
| 占空比 | ≥ 0.80 | 打字、翻资料，角度反复出入阈值带 |
| 峰值角 | ≥ 45° | 浅低头看键盘（三十来度） |
| 头点静止 | 漂移 ≤ 0.45×尺子 | 看手机、写字（头还在动） |
| 有效帧 | ≥ 3 | 靠一两帧凑时长 |
| 丢帧占比 | ≤ 0.50 | 人走开、中途被挡 |

另：丢 pose 超过 **1200ms** 清零。峰值已经 ≥ **90°**（趴实）时不再用漂移挡——循环片源头点会跳，否则国标正例不报。

门不过：回流保持橙框 `BOW`，`sleepLevel = -1`，即使时钟走到 15 秒也不给档位。门过了才按时长分段：

| 档位 | `sleepLevel` | 时长 | 回流 | 类型名 | `alarm_level` |
| --- | --- | --- | --- | --- | --- |
| 疑似 | 0 | ≥ 2s | 琥珀 `SLEEP?` | 疑似睡岗 | 3 / 提示 |
| 确认 | 1 | ≥ 5s | 黄 `SLEEP` | 确认睡岗 | 4 / 警告 |
| 严重 | 2 | ≥ 15s | 红 `SLEEP!` | 严重睡岗 | 5 / 严重 |

进入计时门槛写死 2000ms（`sleepEnterHoldMs()`）。布控页下发的 2500ms **被忽略**，避免页面数字把三档打乱。

同一 Analyzer 事件、同一条 `h_waring` 上，档位只升不降：疑似 → 确认 → 严重，**不新插三行**。

### 数据在哪

- Analyzer 内存：`DetectObject` / `Frame` / `EventState` / `Alarm` 上的 `int sleepLevel`。
- 事件 JSON：`detect.event` 的 `sleepLevel`（取 max）。
- 数据库：**没有** `sleep_level` 列。映射进现成的 `h_waring.alarm_level`、`alarm_level_name`、`alarm_type_name`。时长在 `duration_ms`。行为类型仍是 `sleep_on_duty`，`alarm_type` 仍是 `SLEEP_ON_DUTY`。
- 旧 Analyzer 若不带 `sleepLevel`，backend 用同一套 2/5/15 秒从 `duration_ms` 补档。

### 实现与上下游

`SleepPose::sleepLevelFor()` 只看时长；`labelForEvidence()` 在证据门失败时把 `sleepLevel` 打回 -1。`Scheduler::sendDetectLifecycleEvent` 写入 `detect.event`。backend `HWaringController.upsertRuleDetectEvent`：`start` 插入一行，`update` 仅当新等级更高才改三列。

回流颜色在 `Worker.cpp`。常量与 Python `temporal.py` 由 parity 对齐。

相关 PR：#76（Analyzer）、#77（backend 落等级）。

---

## 2. 质量分 0–100

### 要解决什么

档位只回答「多严重」。值班员还需要「这条有多像真睡」，用来排序，而不是再改一套触发阈值。

### 怎么算

只在证据门已经通过时计算；没过则为 0，且不带档位。四项先归一到 0–1，再乘权重（权重本身是百分数，加起来就是 0–100）：

| 项 | 权重 | 归一 | 含义 |
| --- | --- | --- | --- |
| 峰值角 | 30 | `min(1, 峰值角 / 45°)` | 刚过峰值门就是该项满分；再深不加分 |
| 占空比 | 25 | `min(1, 占空比 / 0.80)` | 刚过占空比门就是该项满分 |
| 时长 | 25 | `min(1, headDownMs / 15000)` | 15 秒封顶，再趴不加分 |
| 头点静止 | 20 | `max(0, 1 − 漂移比 / 0.45)` | 没锚点当满分；头越晃越接近 0 |

实现：`SleepPose.h` 的 `sleepQualityScore()`。刚好过门时四项都是 1.0，能报的告警分不会是负数；趴实、够久、头不动接近 100。

**不参与报不报。** 分低照样报警，只是可以排后面。中间量（峰值、占空比、漂移像素）只打 Analyzer 日志（`sleep_on_duty track=… peak=… downRatio=…`），**不进库**。

同一事件取更大的分，与档位一样只升不降。`detect.event` 的 **start 插入**走 `buildRuleWaring`，当前不写分数；第一次 **update** 才写入 `sva_sleep_score`。刚触达 2 秒就打开详情，质量分可能暂时为空。

### 数据在哪

- 内存 / JSON：`sleepScore`。
- 数据库：`h_waring.sva_sleep_score`（`DOUBLE`，脚本 `scripts/add_sleep_score.sql`）。非睡岗为 `NULL`。
- 前端：告警列表、误报页、详情「xx / 100」（#81 接线）。

### 实现与上下游

C++ 算分 → `detect.event` → `HWaringController` 单调写入 → MyBatis → Vue `WarningDetailDialog.vue`。Python 侧 `sleep_quality_score()` 对齐。

相关 PR：#76 上报、#79 落库、#81 页面。

---

## 3. 关键帧三连图 `keyframes.jpg`

### 要解决什么

告警封面只用事件 **start** 那一帧（结束帧常是坐直，会把趴桌盖掉）。值班员和邮件仍需要一眼看到「怎么趴下去的」，不必先播完整 MP4。

### 怎么做

告警线程把解码 BGR 帧堆进 `Alarm.frames`，每帧带着 `pitchDegree`。写完 `main.jpg` / `main.mp4` 后，`GenerateAlarmVideo::writeKeyframeStrip()` 抽三帧：

- `start`：封面下标（事件开始）
- `peak`：缓冲里俯仰角最大的一帧；没有姿态则退回封面
- `end`：缓冲最后一帧

OpenCV `hconcat` 横拼，左上角标注 `start/peak/end` 与帧号，`imwrite` 到封面同目录的 `keyframes.jpg`。

若视频路径已由 `detect.event` 预定（常见含 `/evt-`）且尚未 bind `alarm_id`，不再 POST `addFromSvaSimple`，避免插第二行。文件已经在磁盘上，库里的 `picture_url` 仍指向 `main.jpg`。

前端 `WarningDetailDialog.resolveKeyframeUrl()`、邮件 `AlarmEmailNotifyService.resolveKeyframeStrip()` 都是把路径里的 `main.jpg` 换成 `keyframes.jpg`。图不存在就隐藏或发纯文字，不失败整条告警。

演示机 Analyzer **必须编过带该函数的版本**，否则磁盘上没有这张图。

### 数据在哪

**不在数据库，没有任何新列。**

```text
/var/www/SVA-web/upload/alarm/<controlCode>/<事件目录>/
    main.jpg
    main.mp4
    keyframes.jpg
```

浏览器经 Nginx：`http://<主机>:8080/alarm/.../keyframes.jpg`，不经过 backend `:9114`。

相关 PR：#80 写文件、#81 页面展示、#79 邮件内嵌。

---

## 4. 严重档站外通知（企业微信 + HTML 邮件）

两条通道共用同一个触发点：`detect.event` 的 `update` 里，档位**第一次**升到严重（`alarm_level` 变为 `"5"`）。疑似、确认不发；已经是严重后再 update 也不再发。异步线程池，失败只打日志，不回滚 `h_waring`。

### 4.1 企业微信群机器人

`SvaSleepNotifyService`：HTTP POST JSON，`msgtype=text`，正文含设备、时间、持续秒、俯仰角、事件 id。超时 5 秒。

- 开关 / URL：`sys_config` 的 `sva.sleep.webhook.enabled`、`sva.sleep.webhook.url`，**默认关**。
- 发出去的内容**不落库**。没配 URL 只打「未配置 webhook」。
- 开着 Clash 时对 localhost 绕过代理，避免本机测试地址被劫持。

相关 PR：#77。

### 4.2 HTML 邮件（可内嵌三连图）

`AlarmEmailNotifyService`：只发 `alarm_level=5`。`JavaMailSender` 发 `MimeMessage`（HTML 表格 + CID `keyframes` 内嵌同目录 `keyframes.jpg`）。找不到图就只发文字。

- 业务开关：`sys_config` 的 `sva.sleep.email.enabled`（默认 false）、`sva.sleep.email.maxRecipients`、`sva.sleep.email.fallback`。
- SMTP **不进业务表**：`application.yml` 的 `spring.mail.*`，演示启动脚本可用 `/opt/SVA/backend/mail.password` 覆盖。常见 SMTPS **465**。
- 收件人：告警 `org_index` 下启用用户的 `sys_user.email`，否则 fallback。
- 发出去的信存在对方邮箱服务器，本组不建发送记录表。

相关 PR：#79、#81（启动脚本接通 SMTP）。

---

## 5. 误报率闭环 + 质量分分桶

### 要解决什么

验收常问「算法准不准」。需要把值班员的人工结论和质量分对上账，而不是只口头说「我们测过了」。

### 怎么做

人在告警详情选「确认」或「误报」，写入 `h_handle.h_title`。统计用**最新一条**处理记录，且**只统计已处理**告警（未处理不进分母，避免新告警把误报率稀释成 0）。

- `GET /index/index/getFalsePositiveStats`：按 `alarm_type_name` 聚合已处理数、误报数、误报率。
- `GET /index/index/getSleepScoreBuckets`：只看 `sva_behavior_type='sleep_on_duty'` 且分数非空，`floor(分数/20)*20` 分桶（0/20/40/60/80），再算各桶误报率。

时间窗参数 `type`：1 本周、2 或不传为本月、3 本季、4 本年（与首页其它统计同一套）。

浏览器走 Nginx `/prod-api` → backend `:9114`。前端**还没有图表页**；不要把大屏画成已经有这张图。

### 数据在哪

- 原料在库：`h_waring`、`h_handle`。不新建表。
- 统计结果**不落库**，每次 GET 现算。

相关 PR：#79。实现：`HWaringMapper.xml`、`HWaringServiceImpl`、`IndexController`。

---

## 6. 设备健康度

### 要解决什么

把「取流到底稳不稳」从排障经验变成可看的数：分析器在不在、这台设备的布控有没有真在拉流。

### 怎么做

`GET /waring/health/list`（权限 `waring:device:list`）。backend 当 HTTP 客户端现探，**不改 C++、不新开上报通道、不落健康度表**。

1. 列出当前用户可见的 `h_device`。
2. 读该设备 `deployment_task.status = 'RUNNING'` 的任务编号（必须是字面量 `RUNNING`，不是 `"1"`）。
3. `GET {analyzer}/api/health`：丢帧、帧/事件上报失败、熔断路数。
4. `POST {analyzer}/api/controls`，body 必须是 JSON `{}`（GET 或空 body 会走 Analyzer 参数错误分支）。
5. 用布控任务 id 匹配返回项的 `code`。**不能**用 `apeId` 对 `streamCode`：接口不返回 `streamCode`，国标 URL 也是 `/rtp/设备_通道`。

打分（满分 100）：Analyzer 不可达 → 0；没有运行中布控，或已下发但对不上 → 扣 30（「未监控」≠ 设备坏了）；`checkFps < 1` 扣 20；帧上报失败扣 10；事件上报失败扣 15；熔断扣 20。≥90 良好，≥70 注意，其余异常。

早期错误版本用 GET、并用 `streamCode==apeId` 匹配，国标会被打成 70 分。现网已改。

前端**没有独立健康度页面**。

### 数据在哪

只读旧表 `deployment_task` / 设备绑定的 `sva_server`。健康分只在接口 JSON 里。Analyzer HTTP 在 `:9993`。

相关 PR：#79。实现：`DeviceHealthController`、`DeploymentAnalyzerClient.probeHealth`、Analyzer `Server.cpp`。

---

## 7. AI 复核吃算法证据

### 要解决什么

原 `buildReviewPrompt` 只给「告警类型 + 设备 + 时间」。多模态模型看不到算法已经算出的量化证据，会凭图编造场景（实测 `qwen-vl-max` 把工位图说成网球场）。

### 怎么做

告警 `insertWaring` 成功后建 `h_alarm_review_task`（PENDING）。`AiReviewTaskScheduler` 启动 30 秒后每 15 秒扫一批，线程池里读封面图转 base64，HTTPS 调阿里云（默认 `qwen-vl-max` / DashScope；也兼容 OpenAI 格式端点）。

提示词现带：俯仰角、持续低头秒数、质量分、档位，并写明看手机/键盘/写字算误报；算法与画面矛盾时以画面为准。系统提示词强制只输出 JSON，字段含 `decision`、`confidence`、`observed`、`pose_match`、`summary`、`reason`。`observed` / `pose_match` **不新建列**，拼进 `h_alarm_review_result.reason`。

图优先读演示机 `/var/www/SVA-web/upload`（#81 修过「按远程 URL 抓图、本地反而读不到」）。失败最多重试 3 次，不影响告警落库。

总开关 `sys_config` 的 `ai.review.enabled`；单条布控还有 `deployment_task.ai_review_enabled` / `ai_review_prompt`。账号在表 `ai_review_server`。

说明：主路径 `detect.event` 的 insert/update **目前没有把 JSON 里的 `pitchDegree` 写入** `h_waring.sva_pitch_degree`（该列主要由 HTTP `addFromSvaSimple` 写入）。因此提示词里「俯仰角」有时会缺；时长、档位、质量分（update 之后）一般还在。

### 数据在哪

- 任务：`h_alarm_review_task`
- 结论：`h_alarm_review_result`（含完整 `raw_response_json`）
- 列表上的状态：`selectWaringVo` 子查询最新一条，不是 `h_waring` 的实列
- 图：仍是磁盘 `main.jpg`

相关 PR：#79 提示词、#81 读图与详情展示。

---

## 8. 和「创新」绑在一起的底座与优化（不是新功能名，但答辩要能讲）

没有这些，上面的档位和分数没有意义。

- **YOLO-Pose 睡岗接入**：`on_sleep_pose` + `yolo11n-pose.onnx`，原 `on_yolo11n_80` / `on_yolo26n_80` 仍加载。正拍/侧拍同一套俯仰角。
- **帧质量门 + 几何硬条件**：坏帧不往睡岗方向推；正脸封顶 18°。
- **证据门与趴实漂移豁免**：见第 1 节表格；#75 趴实后不再用头点漂移挡住告警。
- **告警证据完整 MP4**：按解码帧写，避免只剩一帧；封面用 start。
- **国标 / 直连同一套阈值**：只换 `streamUrl`。Analyzer 拉流失败记下 FFmpeg 错误串；另有探测脚本与双源实测表。
- **C++ / Python parity**：改一边忘一边，`test_cpp_parity.py` 会红。

---

## 9. 数据落点对照

| 创新 | 主要落地 | 算不算「存在数据库」 |
| --- | --- | --- |
| 三档 | `h_waring` 的等级/类型名三列 | 是（映射进旧列） |
| 质量分 | `h_waring.sva_sleep_score` | 是（新列） |
| 三连图 | 磁盘 `keyframes.jpg` | 否 |
| 企业微信 | `sys_config` + 出网 HTTP | 开关在库，消息不在 |
| 邮件 | `sys_config` + SMTP | 开关在库，信不在 |
| 误报率 / 分桶 | `h_waring` + `h_handle` 现算 | 原料在库，结果不存 |
| 健康度 | 探 Analyzer，现算 | 否（只读了布控表） |
| AI 复核 | `h_alarm_review_*` | 是（任务和结论在库，图在磁盘） |

---

## 10. 代码索引

| 主题 | 路径 |
| --- | --- |
| 档位 / 证据门 / 质量分公式 | [server/Analyzer/Core/SleepPose.h](../server/Analyzer/Core/SleepPose.h) |
| 每轨时序 | [server/Analyzer/Core/TemporalContext.cpp](../server/Analyzer/Core/TemporalContext.cpp) |
| 事件 JSON | [server/Analyzer/Core/Scheduler.cpp](../server/Analyzer/Core/Scheduler.cpp) |
| 回流画框 | [server/Analyzer/Core/Worker.cpp](../server/Analyzer/Core/Worker.cpp) |
| 三连图 | [server/Analyzer/Core/GenerateAlarmVideo.cpp](../server/Analyzer/Core/GenerateAlarmVideo.cpp) |
| 落库 / 升级 / 触发通知 | [HWaringController.java](../backend/ruoyi-admin/src/main/java/com/ruoyi/waring/controller/HWaringController.java) |
| 企业微信 | [SvaSleepNotifyService.java](../backend/ruoyi-admin/src/main/java/com/ruoyi/waring/service/SvaSleepNotifyService.java) |
| 邮件 | [AlarmEmailNotifyService.java](../backend/ruoyi-admin/src/main/java/com/ruoyi/waring/service/AlarmEmailNotifyService.java) |
| 误报率 SQL | [HWaringMapper.xml](../backend/ruoyi-admin/src/main/resources/mapper/waring/HWaringMapper.xml) |
| 健康度 | [DeviceHealthController.java](../backend/ruoyi-admin/src/main/java/com/ruoyi/waring/controller/DeviceHealthController.java) |
| AI 提示词 | [AiReviewServiceImpl.java](../backend/ruoyi-admin/src/main/java/com/ruoyi/waring/service/impl/AiReviewServiceImpl.java) |
| Python 对照 | [algo/sleep-pose/sleep_pose/temporal.py](../algo/sleep-pose/sleep_pose/temporal.py) |
| 算法口径 | [algorithm-sleep.md](./algorithm-sleep.md) |

阈值与公式若和本文冲突，以 `SleepPose.h` 与 `algorithm-sleep.md` 为准。
