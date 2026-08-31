# 部署笔记（张柏烁 · 演示机）

> 记录本小组 **验收点 1** 实际跑通的环境。演示机由张柏烁维护；邹子轶与吴佳锴通过浏览器访问，不在本机执行 `install_source.sh`。

## 1. 机器与角色


| 项目          | 本组实际配置                                                   |
| ----------- | -------------------------------------------------------- |
| 宿主机         | Windows 10/11 x64                                        |
| 演示环境        | **WSL2 + Ubuntu 22.04**（等价于手册中的 Ubuntu 22.04 x86_64 演示机） |
| WSL 数据目录    | `D:\WSL\Ubuntu-22.04\`（已从 C 盘迁出）                         |
| 显卡          | NVIDIA GeForce RTX 4060 Laptop，驱动 ≥ 580，安装时选 **G（GPU）**  |
| 小组 monorepo | `Video-Analyse-main`（GitHub）；本机工作副本见下文路径                 |


**说明**：课程文档也允许 VMware 虚拟机；本组用 WSL2，功能等价，网络用 `localhost` 或 WSL IP 访问。

## 2. 本机路径（Windows）


| 路径                                      | 内容                                                       |
| --------------------------------------- | -------------------------------------------------------- |
| `D:\WSL\Ubuntu-22.04\`                  | WSL 虚拟磁盘与发行版根                                            |
| `D:\video-analysis\Video-Analyse-main\` | 小组 Git 仓库（文档、`mediaServer/` 等）                           |
| `D:\video-analysis\start_easysva.sh`    | 一键启动脚本（WSL 内路径：`/mnt/d/video-analysis/start_easysva.sh`） |
| `D:\video-analysis\easySVA-lib.zip`     | 安装依赖包备份（安装脚本用 `/opt/easySVA-lib.zip`）                    |


WSL 内访问 Windows D 盘：`/mnt/d/...`

## 3. 安装概要（已完成）

1. 启用 WSL2，安装 **Ubuntu-22.04**，默认用户 `xdyy`。
2. 将 `install_source.sh`、`data_20250520.sql`、`easySVA-lib.zip` 放到 WSL 的 `/opt/`。
3. 以 **root** 执行 `install_source.sh`，选择 **G（GPU）**。
4. 安装完成后主要产物在 WSL 内（非 monorepo 目录）：


| 组件         | 路径                                                                      |
| ---------- | ----------------------------------------------------------------------- |
| 后端 JAR     | `/opt/SVA/backend/backend.jar`                                          |
| ZLMediaKit | `/opt/SVA/mediaServer/MediaServer`，配置 `config.ini`                      |
| C++ 分析器    | `/opt/SVA/server/Analyzer`，配置 `/opt/SVA/config.json`                    |
| 前端静态页      | `/var/www/SVA-web/dist`                                                 |
| 告警截图目录     | `/var/www/SVA-web/upload/alarm/`                                        |
| 安装源码（参考）   | `/opt/SVA/SVA-backend`、`/opt/SVA/SVA-server`、`/opt/SVA/SVA-mediaServer` |
| 模型         | `/opt/SVA/models/`（如 `yolo11n.onnx`、`yolo26s.onnx`）                     |


官方一键包与脚本：[https://gitee.com/andersonwu/easySVA](https://gitee.com/andersonwu/easySVA)，`easySVA-lib.zip` 见 README 中的网盘链接。

## 4. 账号与端口



### 4.1 网页与数据库


| 用途       | 值                               |
| -------- | ------------------------------- |
| 网页地址（本机） | `http://localhost/`             |
| 网页登录     | `admin` / `admin123`            |
| MariaDB  | `127.0.0.1`:**3307**（见下文「端口冲突」） |
| 数据库用户/密码 | `root` / `easySVA.EZ`           |
| 数据库名     | `easySVA`                       |




### 4.2 服务端口


| 服务             | 端口       | 说明                               |
| -------------- | -------- | -------------------------------- |
| Nginx（前端）      | **80**   | 浏览器入口                            |
| Spring Boot 后端 | **9114** | API、WebSocket                    |
| MariaDB        | **3307** | 本组改端口，非默认 3306                   |
| ZLM HTTP / API | **9992** | REST 如 `/index/api/getMediaList` |
| ZLM RTSP       | **9994** | 非国标默认 554                        |
| ZLM RTMP       | **9995** | 推流/拉流测试常用                        |


数据库 `zlm_server` 表默认记录（id=1）：

- `host`: `127.0.0.1`
- `api_port` / `media_http_port`: `9992`
- `media_rtsp_port`: `9994`
- `app`: `live`
- `secret`: 与 `config.ini` 中 `[api] secret` 一致



### 4.3 Windows MySQL 与 MariaDB 端口冲突

宿主机安装了 **MySQL95**，占用 **3306**。WSL 内 MariaDB 已改为 **3307**：

```ini
# /etc/mysql/mariadb.conf.d/50-server.cnf  [mysqld] 下
port = 3307
```

后端启动必须带 JDBC 3307（已写入 `start_easysva.sh` 与 `/etc/rc.local`）：

```text
jdbc:mysql://127.0.0.1:3307/easySVA?...
```



## 5. 日常启动（重启电脑 / 重启 WSL 后）

在 **Windows PowerShell** 执行（路径以本机 clone 位置为准）：

```powershell
wsl -d Ubuntu-22.04 -u root -- bash /mnt/e/video-analysis/Video-Analyse-main/scripts/start_easysva.sh
```

或 D 盘：

```powershell
wsl -d Ubuntu-22.04 -u root -- bash /mnt/d/video-analysis/Video-Analyse-main/scripts/start_easysva.sh
```

脚本会：启动 MariaDB(3307) → Nginx → backend(9114) → MediaServer → Analyzer，并做简单健康检查。

**手动检查：**

```powershell
wsl -d Ubuntu-22.04 -u root -- bash -lc "ps aux | grep -E 'backend.jar|MediaServer|Analyzer' | grep -v grep; ss -tlnp | grep -E ':80|:9114|:9992|:3307'"
```

浏览器打开 `http://localhost/`，能登录即前端与 Nginx 正常。

## 6. 测试流（无真实摄像头）

本组用 **本机 RTMP 推流** 到 ZLM，设备「视频流地址」填：

```text
rtmp://127.0.0.1:9995/live/test1
```

推流命令（WSL root，需保持 ffmpeg 进程）：

```bash
nohup ffmpeg -re -stream_loop -1 \
  -i /opt/easySVA-lib/opencv/doc/js_tutorials/js_assets/cup.mp4 \
  -c:v libx264 -preset veryfast -tune zerolatency -an \
  -f flv rtmp://127.0.0.1:9995/live/test1 \
  > /tmp/ffmpeg_push.log 2>&1 &
```

PowerShell 一行版：

```powershell
wsl -d Ubuntu-22.04 -u root -- bash -lc "nohup ffmpeg -re -stream_loop -1 -i /opt/easySVA-lib/opencv/doc/js_tutorials/js_assets/cup.mp4 -c:v libx264 -preset veryfast -tune zerolatency -an -f flv rtmp://127.0.0.1:9995/live/test1 > /tmp/ffmpeg_push.log 2>&1 &"
```

**注意**：不要用 `rtsp://...:554/...`；本组 ZLM RTSP 监听在 **9994**。

公网 RTSP 测流地址在国内易 **连接超时**，验收建议用本机推流。

## 7. 验收点 1 操作清单（已跑通）

1. **设备管理**：新增直连设备，视频流地址填 `rtmp://127.0.0.1:9995/live/test1`。
2. **启动监控**：设备列表或实时监控中启动。
3. **视频预览**：确认有画面（H.265 源可能无法预览，请用 H.264）。
4. **布控管理**：选设备、`yolo11n_80`、检测目标 `cup`（与测试视频一致）。
5. **行为规则**：数量阈值 + **直接告警**，主区域至少 3 点并闭合（可用「区域对齐」）。
6. **布控列表**：点 **启动**，状态 **RUNNING**。
7. **报警管理 → 报警列表**：出现告警记录与截图；右下角可有告警推送弹窗。

布控建议：「是否推流」先选 **否**，「前端画框」选 **是**；AI 复核先 **否**，便于快速验证。

## 8. 日志位置


| 组件       | 日志                          |
| -------- | --------------------------- |
| 后端       | `/opt/SVA/backend/log.out`  |
| Analyzer | `/opt/SVA/server/log.out`   |
| ZLM      | `/opt/SVA/mediaServer/log/` |
| 安装过程     | `/opt/install.log`          |
| 测试推流     | `/tmp/ffmpeg_push.log`      |


查看 Analyzer 最近日志：

```bash
tail -50 /opt/SVA/server/log.out
```



## 9. 给 成员 的访问信息


| 项目     | 值                                               |
| ------ | ----------------------------------------------- |
| 演示 URL | `http://<演示机IP>/`（本机可用 `http://localhost/`）     |
| 账号     | `admin` / `admin123`                            |
| 后端 API | `http://<IP>:9114/`（页面通过 Nginx `/prod-api/` 代理） |
| 重启后    | 由 A 执行 `start_easysva.sh` 后再测                   |


WSL IP 查看：`wsl -d Ubuntu-22.04 -- hostname -I`

## 10. 已知问题


| 现象                                     | 原因                                                            | 处理                                |
| -------------------------------------- | ------------------------------------------------------------- | --------------------------------- |
| 添加设备 / 拉流 **连接超时**                     | 公网 RTSP 不可达，或误用 554 端口                                        | 用本机 RTMP 9995 推流                  |
| 保存布控报「geometryConfig 至少需要一个 3 点以上的主区域」 | 未画检测区域                                                        | 左侧点「区域对齐」或手动画区并设主区域               |
| 顶部 `live-output` 404                   | 前端调用 `POST /deployments/{id}/live-output`，当前安装的后端 **未实现** 该接口 | 可忽略；以报警列表为准，勿依赖布控详情页算法预览流         |
| 重启后网页 502                              | 后端未起或连错数据库                                                    | 执行 `start_easysva.sh`             |
| MariaDB 启动失败                           | Windows MySQL 占 3306                                          | 使用 MariaDB **3307** + 后端 JDBC 改端口 |




## 11. 变更记录


| 日期         | 说明                                             |
| ---------- | ---------------------------------------------- |
| 2026-08-31 | 初稿：WSL2 + D 盘、GPU 安装、3307/9994/9995、验收点 1 跑通记录 |


