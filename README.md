# easySVA 小组仓（Video-Analyse）

课程二次开发 GitHub monorepo。分工与协作规则见 `docs/`。

**一句话：同学 A 守住演示机并主攻国标流媒体；同学 B 主攻睡岗并与 A 完成 C++ 集成；同学 C 主攻前后端与合稿。**

| 文档 | 说明 |
| --- | --- |
| [docs/当前阶段.md](docs/当前阶段.md) | 当前 `phase` 与阶段禁令（AI 以这份为准） |
| [docs/architecture.md](docs/architecture.md) | **同学 C**：架构总图、三张表、P1 走查 |
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
docs/           分工、架构、角色手册、验收截图
```

上游见 [docs/UPSTREAM.md](docs/UPSTREAM.md)。验收与编译在同学 A 的 **WSL2 Ubuntu 22.04 x86_64**，不要在 Apple 芯片 Mac 上执行官方 `install_source.sh`。阶段规划只看 [docs/当前阶段.md](docs/当前阶段.md)（现为 **phase 2**，做睡岗、不做国标）。

---

## 验收点 1（已在演示机跑通）

| 项 | 说明 |
| --- | --- |
| 环境 | Windows + WSL2 Ubuntu 22.04，GPU；安装产物在 `/opt/SVA/` |
| 服务 | MariaDB **3307**、Nginx、backend 9114、ZLM、Analyzer |
| 闭环 | 直连/RTMP 测试流预览 + `yolo11n_80` 布控 + 报警列表截图 |
| 截图 | [docs/photo/](docs/photo/) |
| 网页 | `http://<A 的机器 IP>/` 或本机 `http://localhost/`，`admin` / `admin123` |
| 启动 | [deploy-notes.md](docs/deploy-notes.md) |

已知：布控详情可能 `live-output` 404，以报警列表为准。
