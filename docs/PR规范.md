# PR 规范

协作 AI 在创建分支、改代码、写 commit、开 PR 时必须遵守本文。角色手册里的「可以改的路径」同时生效，两者冲突时以更严的为准。

## 分支

- 禁止直接推 `main`。`main` 只接受已审查的 PR。
- 从最新 `main` 开分支，命名：

```text
feat/phase1-<简述>      验收点 1：文档、部署笔记、走通记录
feat/sleep-<简述>       验收点 2：睡岗
feat/gb28181-<简述>     验收点 3：国标
feat/phase4-<简述>      验收点 4：联调与交付物
fix/<简述>              修 bug
docs/<简述>             只改文档
chore/<简述>            仓库杂务（gitignore、脚本）
```

- 一个分支只做一件事。不要把睡岗和国标塞进同一个 PR。
- 演示前一天冻结 `main`，只收 `fix/` 热修。

## 每个角色允许改的目录

| 角色 | 可以改 | 未经商量禁止改 |
| --- | --- | --- |
| A | `mediaServer/`（尤其配置）、`backend/` 里调 ZLM REST/Hook 的客户端与接口、`docs/deploy-notes.md`、`docs/architecture.md` 流媒体实测节 | `web/` 页面、设备表结构、睡岗算法与 `server/` 推理逻辑 |
| B | `server/`、`docs/fixtures/`、`server/models/` 或约定模型目录、睡岗 Python 原型目录、`docs/architecture-analyzer.md`、`docs/algorithm-sleep.md` | `web/`、设备表与布控页、ZLM `config.ini` 国标段、`docs` 里 A/C 的章节 |
| C | `backend/` 业务（设备表、布控、告警）、`web/`、`docs/分工.md`、`docs/当前阶段.md`、`docs/architecture.md`、`.github/` | `mediaServer/conf`、`server/` 推理与模型、在 Mac 上提交「我改过官方安装脚本」之类部署改动 |

跨角色改动：先在 PR 描述里写「需 A/B/C 会签」，并指定对方为 reviewer。例如 C 要动设备表字段、A 要动同步接口，必须同一 PR 或两个 PR 互相链接，不能各改各的各合。

## Commit

- 用约定式中文标题，能看懂「为什么」：

```text
feat(backend): 设备表增加 device_type 字段
fix(server): 国标设备改走 ZLM 播放 URL
docs(phase1): 补充 ZLM 播放地址说明
```

- 禁止：`update`、`fix`、`改了一下` 这种空标题。
- 一次 commit 只含一类改动。不要把格式化和功能混在一起。
- 禁止提交：密码、`.env`、`easySVA-lib.zip`、虚拟机磁盘、大于仓库限额的权重/视频（大文件放网盘，文档里写链接；小测试视频可放 `docs/fixtures/` 并说明来源）。

## PR 必须包含

标题：`[<验收点>] <角色> <一句话>`

```text
[P1] A 补充虚拟机部署笔记与 ZLM 端口
[P2] B 接入睡岗 ONNX 到 Analyzer
[P3] C 设备列表区分 RTSP/GB28181
```

正文按 `.github/PULL_REQUEST_TEMPLATE.md` 填写，缺一不可：

1. 对应验收点（1/2/3/4）
2. 改了哪些路径
3. **没改**哪些（防止误伤原 YOLO / 原 RTSP）
4. 怎么测的：在谁的机器、命令或页面路径、结果
5. 是否需要对方机器 pull 后再编（C++ / ZLM 必填）
6. 接口是否变动（告警 JSON、设备字段、ZLM API）——有变动必须 @ 对接人

## 审查与合并

- 至少 **1 个非作者** Approve 才能合。涉及接口契约（告警 JSON、设备字段、ZLM 同步）必须对接人 Approve。
- 审查看：有没有改到禁止目录、有没有破坏原 RTSP/YOLO、有没有密钥、描述是否写了复现步骤。
- 用 Squash merge，保持 `main` 线性。
- 合入后：**同学 A 在 Ubuntu 虚拟机 `git pull` 再编译/重启**。作者负责在 PR 里写「A 需要执行的命令」。
- 冲突由作者变基：`git fetch origin && git rebase origin/main`，禁止无说明的 force push；已开 PR 的分支需要 rebase 时用 `git push --force-with-lease`。

## 虚拟机与 PR 的关系

- 代码只以 GitHub 为准。A 在虚拟机里改完必须提交并推到自己的分支，禁止只存在于虚拟机磁盘。
- A 的 clone 目录固定为 `/opt/SVA-dev`（若实际路径不同，写进 `docs/deploy-notes.md`）。
- Java / Vue 由 C 在 Mac 开发并开 PR；合入后 A pull 部署，或 C 说明如何用 `npm run dev` 对虚拟机 IP。
- C++ / ZLM 只在 Ubuntu 编译。B 推源码和 ONNX，A 在 PR 勾选「已在虚拟机编译通过」或评论编译日志摘要。

## AI 代开 PR 时的禁令

- 不要用 `--no-verify` 跳过 hook。
- 不要改 git config。
- 不要把上游 Gitee 的无关历史重写进 `main`。
- 业务范围以 [当前阶段.md](./当前阶段.md) 的 `phase` 为准，不要在别的文档里另写一套阶段禁令。
