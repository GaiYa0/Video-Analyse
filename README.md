# easySVA 小组仓（Video-Analyse）

课程二次开发 GitHub monorepo。分工与协作规则见 `docs/`。

**一句话：同学 A 守住演示机并主攻国标流媒体；同学 B 主攻睡岗并与 A 完成 C++ 集成；同学 C 主攻前后端与合稿。**

| 文档 | 说明 |
| --- | --- |
| [docs/当前阶段.md](docs/当前阶段.md) | 当前 `phase` 与阶段禁令（AI 以这份为准） |
| [docs/分工.md](docs/分工.md) | 三人分工总览 |
| [docs/PR规范.md](docs/PR规范.md) | 分支、commit、PR 要求 |
| [docs/deploy-notes.md](docs/deploy-notes.md) | **同学 A**：演示机部署与日常启动 |
| [docs/architecture-streaming.md](docs/architecture-streaming.md) | **同学 A**：流媒体与布控数据流 |
| [AGENTS.md](AGENTS.md) | 给协作 AI 的须知 |

```text
backend/        SVA-backend（Java）
web/            SVA-web（Vue）
server/         SVA-server（C++ Analyzer）
mediaServer/    SVA-mediaServer（ZLMediaKit）
docs/           分工、PR 规范、角色手册、验收截图
```

上游地址见 [docs/UPSTREAM.md](docs/UPSTREAM.md)。**验收与编译在同学 A 的 Ubuntu 22.04 x86_64 环境**（本组为 WSL2 Ubuntu 22.04），不要在 Apple 芯片 Mac 上执行官方 `install_source.sh`。

---

## 第一阶段完成情况（验收点 1 · 同学 A）

> 状态：**已在演示机跑通**；仓库 `phase` 仍为 1，待小组确认后由 **同学 C** 更新 [docs/当前阶段.md](docs/当前阶段.md)。

### 已完成

| 项 | 说明 |
| --- | --- |
| 演示环境 | Windows 宿主机 + **WSL2 Ubuntu 22.04**，数据目录在 **D 盘**；GPU 版 easySVA 安装完成 |
| 服务可用 | MariaDB、Nginx、backend(9114)、ZLMediaKit、Analyzer 可启动；见 [deploy-notes.md](docs/deploy-notes.md) |
| 设备接入 | 直连设备 + 本机 RTMP 测试流（`rtmp://127.0.0.1:9995/live/test1`） |
| 视频预览 | 设备启动监控后可预览 |
| 原 YOLO 布控 | `yolo11n_80` + 目标 `cup` + 数量阈值规则 + **直接告警** |
| 告警全链路 | 报警列表有记录，告警推送与 **截图** 正常 |
| 文档 | `deploy-notes.md`、`architecture-streaming.md` |

### 验收截图

见 [docs/photo/](docs/photo/)：

| 文件 | 内容 |
| --- | --- |
| [初步搭建.png](docs/photo/初步搭建.png) | 环境安装与登录 |
| [尝试布控管理.png](docs/photo/尝试布控管理.png) | 布控配置与检测画框 |
| [报警检测.png](docs/photo/报警检测.png) | 报警列表 |
| [警告界面.png](docs/photo/警告界面.png) | 告警推送与截图 |

### 给其他成员的演示机信息

| 项目 | 值 |
| --- | --- |
| 网页 | `http://<A 的机器 IP>/`（A 本机可用 `http://localhost/`） |
| 登录 | `admin` / `admin123` |
| 重启后启动 | 见 [deploy-notes.md §5](docs/deploy-notes.md#5-日常启动重启电脑--重启-wsl-后) |

### 已知问题（不影响 P1 验收）

- 布控详情页可能报 `live-output` 404：前端调用了后端尚未实现的接口，**以报警列表为准**。
- 宿主机 Windows MySQL 占用 3306，演示机 MariaDB 使用 **3307**（已在部署笔记中说明）。

### 本阶段未做（符合 phase 1 禁令）

- GB28181 / SIP 5060
- 睡岗检测（YOLO-Pose）
- 修改 `web/` 页面、设备表结构、`server/` 睡岗推理逻辑

---

## 后续阶段规划

以 [docs/当前阶段.md](docs/当前阶段.md) 为准；过验收后由 C 将 `phase` 递增。

| phase | 验收点 | 同学 A | 同学 B | 同学 C |
| --- | --- | --- | --- | --- |
| **1**（当前） | 原系统 + 文档 | 演示机、部署笔记、流媒体架构、P1 截图 | 结对看 Analyzer 日志 | 合稿分工与总图目录 |
| **2** | 睡岗 | 虚拟机编译 `server/`、集成 B 的 ONNX | YOLO-Pose 原型、ONNX、睡岗告警链路 | 告警类型、布控睡岗选项、前端展示 |
| **3** | GB28181 | **主责**：ZLM SIP 5060、国标注册/预览、ZLM 同步接口 | 国标流进推理管线联调 | 设备表 `device_type`、国标列表页 |
| **4** | 联调交付 | 环境复现、断线重连、**演示日操作机器** | 睡岗 + 回归 | 手册、PPT、宣讲统稿 |

### A 的近期待办（phase 1 收尾 → phase 2 准备）

1. 与 B 结对：B 改 `server/` 后，A 在演示机 `git pull` 并编译 Analyzer。
2. 保持演示机可访问，把 IP 与 `start_easysva.sh` 路径同步给 B/C。
3. phase 2 开始后：继续守机器，**不**提前改国标相关配置。

---

## 分支说明（本 PR）

- 分支：`docs/phase1-deploy-and-streaming`
- 内容：P1 部署笔记、流媒体架构说明、验收截图、本 README 更新
- 合入方式：按 [PR规范.md](docs/PR规范.md) 开 PR，至少 1 人 Review 后 Squash merge
