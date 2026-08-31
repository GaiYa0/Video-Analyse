# 角色手册：同学 C（前后端与合稿）

给协作 AI：你正在协助**同学 C（Mac）**。先读 [../当前阶段.md](../当前阶段.md)、[../分工.md](../分工.md) 和 [../PR规范.md](../PR规范.md)。不要在 macOS 上跑 easySVA 官方安装脚本，不要代写 A/B 的章节正文，不要改 `server/` 推理或 `mediaServer` 配置。

## 一句话

在 Mac 上主攻前后端业务、GitHub 仓库和文档统稿；不当万能文档工，演示当天不操作机器（由 A 操作）。

## 机器

- Apple 芯片 Mac：只写 `backend/`、`web/`、文档
- 用 IDEA 打开 `backend/`，在 `web/` 里 `npm run dev`
- 浏览器访问同学 A 虚拟机 `http://<虚拟机IP>/` 做验收
- 看日志用 SSH 进虚拟机，**禁止**把官方 `install_source.sh` + `easySVA-lib.zip` 当成本机部署方案（依赖是 Linux x86_64，Apple 芯片对不上）
- 默认演示账号：`admin` / `admin123`

## 可以改

- `backend/` 业务：设备表、布控规则、告警类型与入库、查询接口（**不含** A 负责的 ZLM REST/Hook 客户端）
- `web/`：设备列表、布控、告警、预览相关页面
- `docs/分工.md`、`docs/当前阶段.md`、`docs/architecture.md`（小组只维护这一份架构文）
- `.github/`（PR 模板等协作文件）

## 不要改

- `mediaServer/conf` 与 SIP 配置
- `server/` 模型、推理、取流
- A 的 `deploy-notes.md` / 流媒体章正文、B 的分析器章 / 算法说明正文（你可以改目录和错字，不改技术结论）
- 在未与 B 签字前「发明」一套睡岗告警字段

## 和谁对接

- 和 A：你起草设备字段 `device_type` / `gb_device_id` / `gb_platform_id`；A 补 ZLM 同步字段并实现同步接口；你做列表页和「同步国标设备」**按钮与展示**。对接口径：同一 PR 或两个互相链接的 PR，A 必须 Approve 同步相关后端
- 和 B：B 起草睡岗告警 JSON，你确认后改库和告警/布控页。未确认前不要建表
- 协作职责（收窄）：PR 合并、每日打卡提醒、截稿。不包办演示操作

## 四个验收点

### 验收点 1

1. 建 GitHub monorepo，导入 `backend/` `web/` `server/` `mediaServer/`，加 A、B 为 Collaborator，保护 `main`
2. 确认三人读过 [../PR规范.md](../PR规范.md)
3. 审查同学 A 的部署 PR；用浏览器（或 A 的演示机 IP）走通登录 → 设备 → 预览 → 布控 → 告警，把步骤写进 `docs/architecture.md` 末节，**不要另开操作手册**
4. 只维护 `docs/architecture.md`：拓扑、数据流、三张表索引；流媒体细节链 A，分析器链 B
5. 提醒三人给 Gitee 上游五仓点 Star；合并文档 PR，不要直推 `main` 堆新 md

### 验收点 2

按与 B 确认的 JSON，增加睡岗告警类型、布控可选睡岗、告警列表展示。在 A 的虚拟机上验证记录和截图入库。原有 YOLO 告警页不能坏。

### 验收点 3

设备表扩展、列表区分 RTSP/GB28181、在线状态。同步按钮的前端归你，拉数接口归 A。

### 验收点 4

使用手册、演示视频、PPT 并进答辩材料或 `architecture.md`，不另开一堆 md。A 提供部署片段，B 提供算法片段。

## 架构文档最低目录（你负责搭架子）

1. 一句话分工与三人手册链接
2. 仓库与进程拓扑
3. 端到端数据流图
4. 设备 / 布控 / 告警表（链到细节或自己列字段）
5. 链到 A《流媒体》、B《分析器》
6. 后续切入点（只写将改哪里）

## 开 PR 时

标题带验收点和 `C`。改了接口就 @ A 或 B。合入后写明 A 是否要重启 `backend.jar` / 刷前端。完整规则见 [../PR规范.md](../PR规范.md)。
