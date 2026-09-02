# easySVA 小组仓（Video-Analyse）

课程二次开发 GitHub monorepo。分工与协作规则见 `docs/`。

**一句话：同学 A 守住演示机并主攻国标流媒体；同学 B 主攻睡岗并与 A 完成 C++ 集成；同学 C 主攻前后端与合稿。**

| 文档 | 说明 |
| --- | --- |
| [docs/当前阶段.md](docs/当前阶段.md) | 当前 `phase` 与阶段禁令（AI 以这份为准） |
| [docs/architecture.md](docs/architecture.md) | 架构总图 + 直连流媒体实测（C 合稿，A 并入） |
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
scripts/        一键启动、局域网、告警视频补救脚本
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

---

## 近期已合入 / 待合入能力

### 局域网实时预览（PR #14，已合入）

- Nginx `location /live/` 代理 ZLM HTTP-FLV / WS-FLV，同学经 `:8080` 看监控墙
- **不改** `zlm_server.host`（须保持 `127.0.0.1`）；用 `rewrite_play_url_for_lan.sh` 改写库内 `play_url`
- 脚本：`scripts/nginx-live-proxy.conf`、`apply_nginx_live_proxy.sh`、`rewrite_play_url_for_lan.sh`

### 告警视频证据（PR 进行中）

**问题**：监控墙能看直播，但告警页点「视频证据」报「视频不存在」——实时预览（WS-FLV）与告警 MP4 是两条链路。

**根因与修复**：

| 层面 | 说明 |
| --- | --- |
| 后端 | `addFromSvaMediaCallback` 收到 Analyzer 的 `video_path` 时写回 `video_url`；`AlarmMediaUrls` 支持 `/alarm/` 与 `/zlm/` 浏览器相对路径 |
| 前端 | 共用 `web/src/utils/alarmVideo.js`；无视频时按 `sva_media_status` 提示「录像中 / 录像失败 / 尚未回写」 |
| 录像引擎 | 默认 **M-SERVER** 靠 ZLM 录像；**A-SERVER** 由 Analyzer 生成 `alarm/.../main.mp4`（详见 deploy-notes §7.1） |
| 历史数据 | `scripts/backfill_alarm_video_url.sh` 回写磁盘已有但库内为空的 `video_url` |

**演示机验证**：回写 38 条历史告警；`/alarm/.../main.mp4` 经 Nginx 返回 200。

### 前端生产构建（PR #10，已合入）

- `web/vue.config.js` 转译 `@opentiny`、OpenSSL 兼容；补 `web/.env.production`

---

## 演示机日常脚本（同学 A）

| 脚本 | 用途 |
| --- | --- |
| `scripts/start_easysva.ps1` | Windows 一键启动全套服务（推荐） |
| `scripts/start_easysva.sh` | WSL 内启动；支持 `--with-stream` 推 cup.mp4 测试流 |
| `scripts/setup_windows_lan_access.ps1` | 管理员：防火墙 + `8080→80` 端口转发 |
| `scripts/open_lan_firewall.bat` | 管理员：仅放行 80/8080 防火墙 |
| `scripts/rewrite_play_url_for_lan.sh` | 把监控墙 `play_url` 改为 `ws://<IP>:8080/live/...` |
| `scripts/backfill_alarm_video_url.sh` | 历史告警 `video_url` 与磁盘 `main.mp4` 对齐 |
| `scripts/switch_cup_to_a_server.sh` | 水杯布控改 A-SERVER 并等待新告警验证 |

**同学连不上时**：确认双方同一 WiFi/热点、地址带 `:8080`、A 已关 VPN；仍不行则 A 以管理员运行 `setup_windows_lan_access.ps1`。

**合入告警视频 PR 后 A 执行**：

```bash
cd /mnt/e/video-analysis/Video-Analyse-main   # 或 /opt/SVA-dev
git pull
cd backend && mvn -DskipTests package && cp ruoyi-admin/target/ruoyi-admin.jar /opt/SVA/backend/backend.jar
cd ../web && npm run build:prod && cp -r dist/* /var/www/SVA-web/dist/
bash scripts/backfill_alarm_video_url.sh      # 可选：补历史 video_url
# Windows: .\scripts\start_easysva.ps1
```

---

## 协作说明

- 分支与 PR 标题见 [PR规范.md](docs/PR规范.md)，例：`[P2] A 一句话`
- `main` 只接受 Review 后的 Squash merge；跨 `web/` 改动需 **C 会签**
