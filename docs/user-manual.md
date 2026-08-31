# 验收点 1 操作步骤（给老师照做）

> 同学 C 整理。环境与端口以同学 A 的 [deploy-notes.md](./deploy-notes.md) 为准。本组演示机是 **WSL2 Ubuntu 22.04**，账号 `admin` / `admin123`。无真实摄像头时用 ffmpeg 推 RTMP，见 A 的流媒体章第 5 节。

合入 [PR #1](https://github.com/GaiYa0/Video-Analyse/pull/1) 后，截图在 `docs/photo/`。

## 0. 启动服务

在演示机（同学 A）：

```bash
# 详见 docs/deploy-notes.md；若已拷贝启动脚本：
bash scripts/start_easysva.sh
```

确认：MariaDB、ZLMediaKit、backend.jar、Analyzer、Nginx 均在。浏览器打开 `http://localhost/`（局域网则用虚拟机 IP）。

## 1. 登录

1. 打开后台首页
2. 用户名 `admin`，密码 `admin123`
3. 进入系统后能看到菜单（设备、布控、报警等）

对应截图：`docs/photo/初步搭建.png`

## 2. 添加直连设备并预览

1. 打开 **设备管理**（`web/src/views/device/index.vue`）
2. 新增设备，源流选 **直连**，**视频流地址**填 ZLM 能拉到的地址  
   本组无摄像头示例：`rtmp://127.0.0.1:9995/live/test1`（需先按 A 的笔记用 ffmpeg 推流）
3. 保存后 **启动监控**
4. 打开实时预览，应能出画面（当前版本不支持 H.265 预览；失败先查编码和 ZLM 是否拉到流，不要先改 Java）

## 3. 布控（原 YOLO）

1. 打开 **布控管理**（`web/src/views/deployment/`）
2. 选择刚加的设备
3. 算法选原有 YOLO（本组实测 `yolo11n_80`）
4. 检测目标按画面内容选（本组实测 `cup`）
5. 画识别区域：`geometryConfig` 主区域至少 3 个点
6. 规则用数量阈值 + 直接告警
7. 在布控列表 **启动**

对应截图：`docs/photo/尝试布控管理.png`

## 4. 查看告警截图

1. 保证画面里持续出现检测目标
2. 打开 **报警列表**（`web/src/views/warning/index.vue`）
3. 应出现告警记录，并能打开截图

对应截图：`docs/photo/报警检测.png`、`docs/photo/警告界面.png`

## 5. 验收对照

- [ ] 浏览器能打开 easySVA 后台
- [ ] 直连/RTSP 预览有画面
- [ ] 原 YOLO 布控能出告警截图
- [ ] 架构文档：[architecture.md](./architecture.md)
- [ ] 三人已给 Gitee 上游五仓点 Star（清单在架构文档第 7 节）
- [ ] 本阶段 **没有** 改 GB28181、没有接睡岗模型

## 6. 本步不包含

睡岗与国标按 [当前阶段.md](./当前阶段.md) 的 `phase` 开放后再测。Analyzer 内部模型路径等由同学 B 写进 `architecture-analyzer.md`。
