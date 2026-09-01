# easySVA 小组仓（Video-Analyse）

课程二次开发 GitHub monorepo。分工与协作规则见 `docs/`。

**一句话：同学 A 守住演示机并主攻国标流媒体；同学 B 主攻睡岗并与 A 完成 C++ 集成；同学 C 主攻前后端与合稿。**

| 文档 | 说明 |
| --- | --- |
| [docs/当前阶段.md](docs/当前阶段.md) | 当前 `phase` 与阶段禁令（AI 以这份为准） |
| [docs/architecture.md](docs/architecture.md) | 架构总图 + 直连流媒体实测（C 合稿，A并入） |
| [docs/分工.md](docs/分工.md) | 三人分工总览 |
| [docs/PR规范.md](docs/PR规范.md) | 分支、commit、PR 要求 |
| [docs/deploy-notes.md](docs/deploy-notes.md) | **同学 A**：演示机部署与日常启动 |
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
| 服务 | MariaDB **3307**、Redis、Nginx、backend 9114、ZLM、Analyzer |
| 闭环 | 直连/RTMP 测试流预览 + `yolo11n_80` 布控 + 报警列表截图 |
| 截图 | [docs/photo/](docs/photo/) |
| 本机网页 | `http://localhost/` 或 `http://localhost:8080/`，`admin` / `admin123` |
| 同学访问 | `http://<A 的局域网 IP>:8080/`（**带 8080**；演示前请 **关闭 Clash/VPN**） |
| 一键启动 | Windows：`.\scripts\start_easysva.ps1`（可选 `-WithStream`）；详见 [deploy-notes.md](docs/deploy-notes.md) |

已知：布控详情可能 `live-output` 404，以报警列表为准。

### 演示机日常脚本（同学 A）

| 脚本 | 用途 |
| --- | --- |
| `scripts/start_easysva.ps1` | Windows 一键启动全套服务（推荐） |
| `scripts/start_easysva.sh` | WSL 内启动；支持 `--with-stream` 推 cup.mp4 测试流 |
| `scripts/setup_windows_lan_access.ps1` | 管理员：防火墙 + `8080→80` 端口转发 |
| `scripts/open_lan_firewall.bat` | 管理员：仅放行 80/8080 防火墙 |

**同学连不上时**：确认双方同一 WiFi/热点、地址带 `:8080`、A 已关 VPN；仍不行则 A 以管理员运行 `setup_windows_lan_access.ps1`。

---

## 分支说明（本 PR）

- 分支：`docs/phase1-deploy-and-streaming`
- 内容：一键启动脚本、局域网 `:8080` 访问说明、README / deploy-notes 更新
- 合入：按 [PR规范.md](docs/PR规范.md)，至少 1 人 Review 后 Squash merge
