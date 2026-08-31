# easySVA 小组仓

课程二次开发的 GitHub monorepo。分工与协作规则在 `docs/`。

**一句话：同学 A 守住 Ubuntu 演示机并主攻国标流媒体，同学 B 主攻睡岗并和 A 完成 C++ 集成，同学 C 主攻前后端与合稿。**

- 总览：[docs/分工.md](docs/分工.md)
- PR：[docs/PR规范.md](docs/PR规范.md)
- 给 AI：[AGENTS.md](AGENTS.md)

```text
backend/        SVA-backend（Java）
web/            SVA-web（Vue）
server/         SVA-server（C++ Analyzer）
mediaServer/    SVA-mediaServer（ZLMediaKit）
docs/           分工、PR 规范、角色手册
```

上游地址见 [docs/UPSTREAM.md](docs/UPSTREAM.md)。验收跑在同学 A 的 Ubuntu 22.04 x86_64 虚拟机，不要在 Apple 芯片 Mac 上执行官方安装脚本。
