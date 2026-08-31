# 角色手册：同学 A（流媒体与演示机）

给协作 AI：你正在协助**同学 A**。先读 [../分工.md](../分工.md) 和 [../PR规范.md](../PR规范.md)。未经用户明确要求，不要改 `web/`、设备表结构、睡岗算法或 `server/` 推理逻辑。

## 一句话

守住唯一的 Ubuntu 演示机，主攻国标流媒体；功能产出是「流能进、国标能注册、另外两人能打开这台机器做验收」。

## 机器

- 宿主机：Windows
- 演示机：VMware / VirtualBox 上的 **Ubuntu 22.04 x86_64**（不要 ARM）
- 虚拟机：内存 8GB+，磁盘 40GB+
- 网络：桥接或端口转发，保证同学 B、C 浏览器能打开 `http://<虚拟机IP>/`
- 默认账号（部署成功后）：`admin` / `admin123`
- 数据库（脚本默认）：`root` / `easySVA.EZ`
- clone 目录：`/opt/SVA-dev`（若不同，必须写进 `docs/deploy-notes.md`）

不要让用户在 Mac 上跑 [install_source.sh](https://gitee.com/andersonwu/easySVA)。没有 NVIDIA 驱动 580+ 就选脚本里的 **C（CPU）**。

## 可以改

- `mediaServer/`，尤其 `config.ini`（第三阶段开 SIP 5060）
- `backend/` 里**仅**调用 ZLM REST / Hook 的客户端、同步设备接口
- `docs/deploy-notes.md`、`docs/architecture-streaming.md`、`docs/deploy-manual.md`

## 不要改

- `web/` 页面（同步按钮的前端归 C）
- 设备表字段设计（归 C 起草，你只补 ZLM 侧字段）
- `server/` 里 YOLO / 睡岗推理（集成编译你可以做，算法逻辑归 B）
- C 的架构总图正文、B 的算法章节

## 和谁对接

- 和 B：验收点 1、2 结对编译 Analyzer；合入含 `server/` 的 PR 后你在虚拟机 pull 并编译
- 和 C：你提供 ZLM 同步接口字段与 URL；C 做设备表和列表页。接口变动必须 C Approve
- 冻接口：国标同步字段由你补全，C 起草的 `device_type` / `gb_device_id` / `gb_platform_id` 你不能擅自改名

## 四个验收点

### 验收点 1（现在，禁止做新功能）

1. 装好 Ubuntu 22.04 x86_64 虚拟机，下载 `easySVA-lib.zip` 与 `install_source.sh` 到 `/opt`
2. root 执行脚本，选 CPU；编译时让 B 盯 Analyzer 日志，不要一个人熬
3. 重启后四服务起来：backend、Analyzer、ZLMediaKit、MariaDB
4. 把 80 / 9114 / ZLM 相关端口给 B、C 能访问
5. 亲手走通：加 RTSP → 预览 → 原 YOLO 布控 → 告警截图（预览失败先查 H.265 和 IP）
6. 写 `docs/deploy-notes.md` 和 `docs/architecture-streaming.md`（RTSP 如何进 ZLM、播放地址、在线状态从哪来）

官方脚本部署后常见路径（以实际为准，写进笔记）：

- `/opt/SVA/backend/backend.jar`
- `/opt/SVA/mediaServer/MediaServer`
- `/opt/SVA/server/Analyzer`，配置 `/opt/SVA/config.json`
- `/var/www/SVA-web/dist`，告警图 `/var/www/SVA-web/upload/alarm/`

### 验收点 2

在虚拟机编译 `server/`、放置 B 的 ONNX、看 Analyzer 日志。这是集成工作，不是打杂。PR 评论里贴编译是否通过。

### 验收点 3（你主责）

ZLM 开 SIP 5060；国标设备或模拟器注册、保活、离线、预览。写后端「调 ZLM REST / Hook」那一层。设备列表页和表结构交给 C。

### 验收点 4

环境复现、断线重连、部署手册完稿。**演示当天由你操作机器**。

## 文档

只写流媒体与部署相关章节，不要代写 B 的分析器章或 C 的总图。

## 开 PR 时

标题带 `[P1]`/`[P2]`/`[P3]`/`[P4]` 和 `A`。正文写明虚拟机上如何验证、B/C 是否需要刷新页面或 pull。完整规则见 [../PR规范.md](../PR规范.md)。
