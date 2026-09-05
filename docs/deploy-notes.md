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

**按组件拆开的启动/关闭对照表**见 [启动手册.md](./启动手册.md)（哪条命令起哪一部分写清楚了）。

在 **Windows PowerShell** 进入仓库根目录，一键启动：

```powershell
cd E:\video-analysis\Video-Analyse-main
.\scripts\start_easysva.ps1
```

需要同时推测试流（cup.mp4 → RTMP）：

```powershell
.\scripts\start_easysva.ps1 -WithStream
```

也可直接调 WSL 脚本（路径随 clone 位置变化）：

```powershell
wsl -d Ubuntu-22.04 -u root -- bash /mnt/e/video-analysis/Video-Analyse-main/scripts/start_easysva.sh
wsl -d Ubuntu-22.04 -u root -- bash /mnt/e/video-analysis/Video-Analyse-main/scripts/start_easysva.sh --with-stream
```

脚本会：MariaDB(3307) → Redis → Nginx → backend(9114) → MediaServer → Analyzer，并做健康检查。若在 Windows 上编辑过 `.sh` 导致 CRLF，脚本会自动修复换行符。

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

## 7.1 告警视频证据与录像引擎

实时预览（监控墙 WS-FLV）与告警「视频证据」（录好的 MP4）是两条链路。告警页点「视频证据」时，前端只读库里的 `video_url` / `video_absolute_url`；两者都空会提示「视频不存在」或按 `sva_media_status` 显示「录像中 / 录像失败」。

### 录像引擎（布控「录像引擎」字段）

| 引擎 | 默认？ | Analyzer 是否生成 MP4 | 视频来源 |
| --- | --- | --- | --- |
| **M-SERVER（媒体服务器）** | 是 | **否**（`saveVideoEnabled=false`） | 后端调 ZLM `startRecordTask`，成功后写入 `zlm/live/{deviceId}/{alarmId}.mp4` |
| **A-SERVER（算法服务器）** | 否 | **是** | Analyzer 编码到 `alarm/{control_code}/{alarm_id}/main.mp4`，经 `addFromSvaMediaCallback` 回写库 |

**演示机建议**：本机 RTMP 水杯流等稳定源，布控选 **A-SERVER**，便于快速得到 `alarm/.../main.mp4`。公网 HLS/RTMP 若用默认 **M-SERVER**，ZLM 录像可能因断流或 `MediaSource` 不存在而失败（`sva_media_status=record_failed`），可暂时只展示截图证据。

### 静态资源与浏览器 URL

| 类型 | 磁盘路径 | Nginx |
| --- | --- | --- |
| 告警截图 / A-SERVER 视频 | `/var/www/SVA-web/upload/alarm/` | `location /alarm/` |
| M-SERVER ZLM 录像 | `/var/www/SVA-web/upload/storage/`（`zlm/live/...`） | `location /zlm/` |

后端 `AlarmMediaUrls` 会把 `alarm/`、`zlm/` 路径改写成浏览器可访问的相对 URL（如 `/alarm/...`、`/zlm/...`），**不要**把 `zlm_server.host` 改成局域网 IP。

### 历史告警 video_url 为空但磁盘已有 MP4

若素材回调漏写库，可执行回写脚本（只改 `video_url` 为空的记录）：

```bash
wsl -d Ubuntu-22.04 -u root -- bash /mnt/e/video-analysis/Video-Analyse-main/scripts/backfill_alarm_video_url.sh
```

### 排查「有截图、无视频」

```bash
mysql -h127.0.0.1 -P3307 -uroot -peasySVA.EZ easySVA -e \
"SELECT id, device_id, control_code, video_url, sva_media_status, sva_media_error FROM h_waring ORDER BY alarm_time DESC LIMIT 5\G"

grep -E 'ZLM录像|startRecordTask|SVA素材回写' /opt/SVA/backend/log.out | tail -30

find /var/www/SVA-web/upload -name 'main.mp4' | tail -10
```

### PR #30 合入后必跑：睡岗俯仰角列

前端 #30 会读写 `h_waring.sva_pitch_degree`。演示机若未执行 `ALTER`，告警列表与 WebSocket 弹窗会报 `Unknown column 'sva_pitch_degree'`，页面表现为 **列表空白、右下角不弹告警**。

```bash
wsl -d Ubuntu-22.04 -u root -- bash -lc \
  "mysql -h127.0.0.1 -P3307 -uroot -peasySVA.EZ easySVA < /mnt/e/video-analysis/Video-Analyse-main/scripts/add_sva_pitch_degree.sql"
# 然后重启 backend：.\scripts\start_easysva.ps1
```

列已存在时 MariaDB 会报错，可忽略。合入后需 **重启 `backend.jar`**。

**报警列表「今天没数据」**：待处理页默认日期为 **当天**。历史告警在昨天时，把日期改到昨天～今天或清空日期再搜索。

### 本机摄像头测睡岗（P2）

无工位录像时，用笔记本摄像头推 RTMP，设备 `cam918429`（工位摄像头）。

| 脚本 | 用途 |
| --- | --- |
| `scripts\push_webcam.bat` | Windows：摄像头 → `rtmp://127.0.0.1:9995/live/webcam`（窗口需保持打开） |
| `scripts\setup_webcam_device.sql` | 库内插入/更新工位摄像头设备 |
| `scripts\insert_on_sleep_pose.sql` | `av_algorithm` 插入睡岗算法（缺则布控下拉无睡岗） |
| `scripts\start_webcam_monitor.sh` | WSL：启用该设备监控 |
| `scripts\start_sleep_deployment.sh` | WSL：启动睡岗布控 `controljDNAEPaKnlcupU`（需先在网页保存过该布控） |

顺序：先 `push_webcam.bat` → `setup_webcam_device.sql`（首次）→ `start_webcam_monitor.sh` → 网页布控选睡岗并保存 → `start_sleep_deployment.sh` 或布控列表点启动。趴桌约 2.5s 应出睡岗告警。

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



## 9. 给成员的访问信息

| 项目 | 值 |
| --- | --- |
| 本机浏览器 | `http://localhost/` 或 `http://localhost:8080/` |
| 同学访问（局域网） | `http://<你的IP>:8080/`（**不要用 `:80`**） |
| 账号 | `admin` / `admin123` |
| 重启后 | 先 `.\scripts\start_easysva.ps1`，同学连不上再运行 `.\scripts\setup_windows_lan_access.ps1`（管理员） |

**为什么不用 `http://10.x.x.x/`（80 端口）？**

本机 `.wslconfig` 使用 `networkingMode=mirrored`，WSL 与 Windows 共用 WLAN IP。在本机浏览器访问自己的局域网 IP + 80 端口常会失败；若还开了 Clash（`127.0.0.1:7890`），请求会被代理成 502/超时。

**一次性配置局域网（管理员 PowerShell）：**

```powershell
cd E:\video-analysis\Video-Analyse-main
.\scripts\setup_windows_lan_access.ps1
```

会放行防火墙 80/8080，并设置 `8080 → WSL 80` 转发。配置好后同学用 `http://<IP>:8080/`。

WSL IP 查看：`wsl -d Ubuntu-22.04 -- hostname -I`

### 同学看不到实时画面（监控墙黑屏）

页面能开、列表有设备，但画面黑：数据库里的播放地址是 `ws://127.0.0.1:9992/live/<流>.live.flv`。同学浏览器里的 `127.0.0.1` 是他们自己的电脑，且 **9992 未对局域网开放**。

正确做法（已在演示机落地，一键启动会补 Nginx）：

1. Nginx `location /live/` 与 `location /rtp/` 把 ZLM 的 HTTP-FLV / WS-FLV 代理到 80，复用 `8080 → 80`。片段见 `scripts/nginx-live-proxy.conf`。
2. **不要改** `zlm_server.host`。从 WSL 访问 `局域网IP:9992` 不通，改了会弄断 Analyzer 拉流和后端调 ZLM API。
3. 把库里的播放地址改成 `ws://<局域网IP>:8080/live/<流>.live.flv`：

```bash
wsl -d Ubuntu-22.04 -u root -- bash /mnt/e/video-analysis/Video-Analyse-main/scripts/rewrite_play_url_for_lan.sh <你的WLAN_IPv4>
```

同学强制刷新 `http://<IP>:8080/`，看 **监控墙**。设备列表里的「实时预览」弹窗仍由后端按 `zlm_server` 现算 `127.0.0.1`，只有本机能放。

WiFi IP 变了，或「启动监控」把 `play_url` 写回 `127.0.0.1:9992` 后，再跑一次上面的改写脚本。

## 10. 已知问题


| 现象                                     | 原因                                                            | 处理                                |
| -------------------------------------- | ------------------------------------------------------------- | --------------------------------- |
| 添加设备 / 拉流 **连接超时**                     | 公网 RTSP 不可达，或误用 554 端口                                        | 用本机 RTMP 9995 推流                  |
| 保存布控报「geometryConfig 至少需要一个 3 点以上的主区域」 | 未画检测区域                                                        | 左侧点「区域对齐」或手动画区并设主区域               |
| 顶部 `live-output` 404                   | 前端调用 `POST /deployments/{id}/live-output`，当前安装的后端 **未实现** 该接口 | 可忽略；以报警列表为准，勿依赖布控详情页算法预览流         |
| 本机 / 同学打不开 `http://<IP>/` 或 `:8080` | WSL mirrored；**Clash/VPN 劫持局域网**；防火墙未放行 | **先关 VPN/Clash**；同学用 `http://<IP>:8080/`；管理员运行 `setup_windows_lan_access.ps1` 或 `open_lan_firewall.bat` |
| 同学打开网页但监控墙/国标黑屏 | `play_url` 是 `ws://127.0.0.1:9992/...`；9992 未对局域网开放 | Nginx `/live/`、`/rtp/` 代理 + `rewrite_play_url_for_lan.sh <IP>`；关 Clash |
| 告警有截图但点「视频证据」提示不存在 | 默认 M-SERVER 时 ZLM 录像失败，或 A-SERVER 回调未写 `video_url` | 布控改 A-SERVER 复测；查 `sva_media_status` / 后端日志；跑 `backfill_alarm_video_url.sh`；见 §7.1 |
| 重启后网页 502 | 后端未起或连错数据库 | 执行 `start_easysva.ps1` |
| MariaDB 启动失败                           | Windows MySQL 占 3306                                          | 使用 MariaDB **3307** + 后端 JDBC 改端口 |





## 10.1 P3 国标（GB28181：WVP SIP + ZLM 媒体）

**怎么用**见 [国标功能使用说明书.md](./国标功能使用说明书.md)。下面是部署与接口。

C 已合入设备三字段与「同步国标设备」按钮（PR #35）。演示机执行：

```bash
mysql -h127.0.0.1 -P3307 -uroot -peasySVA.EZ easySVA < scripts/add_h_device_gb28181.sql
# 重编 backend.jar + web dist 后 start_easysva.ps1
```

### 本机 ZLM 能力说明

安装的 MediaServer 含 GB28181Process 与 REST：`openRtpServer` / `listRtpServer` / `getMediaList`。

**没有内置 SIP UAS（5060）**。完整国标注册/保活/离线由外挂 **WVP-GB28181-pro** 提供；ZLM 只收 PS/RTP 并转 WS-FLV。

### WVP 部署（演示机）

| 项 | 值 |
| --- | --- |
| 源码/安装 | `/opt/wvp-GB28181-pro`（需 **JDK 21** 编译） |
| 配置 profile | `easysva` → `application-easysva.yml` |
| Web / API | `http://127.0.0.1:18080`；演示机账号 **`admin` / `SvaDemo@2026`**（API 登录密码为该明文的 MD5） |
| SIP | **UDP/TCP 5060**，本机只监听 **局域网 IP**（`sip.ip`，如 `10.21.235.102`），**不是** `0.0.0.0`/`127.0.0.1`；平台 ID `34020000002000000001`；域 `3402000000`；设备密码 `12345678` |
| 库 | MariaDB `3307` / 库名 `wvp`（与 easySVA 同实例） |
| Redis | `127.0.0.1:6379` DB **7**（无密码） |
| 对接 ZLM | API `127.0.0.1:9992`；`media.id` = `config.ini` 的 `mediaServerId`；`secret` 与 `[api] secret` 一致 |
| 演示配置 | `interface-authentication: false`（默认 admin 密码未改时否则 API 仅允许改密）；配置放 `/opt/SVA/wvp/config/` 外挂（jar 内可能不含 yml） |
| 编译 | 需 **JDK 21**：`bash scripts/build_wvp.sh` |

一键脚本：

```bash
bash scripts/setup_wvp.sh          # 建库 + 写 easysva 配置
# JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 mvn -DskipTests package  # 在 /opt/wvp-GB28181-pro
bash scripts/start_wvp.sh          # 起 WVP + 写 ZLM hook 指向 :18080
```

Windows 防火墙 / WSL 端口转发需放行 **5060**（及可选 18080）。

### 无真机：SIP 模拟器（真 REGISTER + 点播出画面）

```powershell
wsl -d Ubuntu-22.04 -u root -- bash /mnt/e/video-analysis/Video-Analyse-main/scripts/start_gb_sim.sh
```

- 设备/通道国标编码默认 `34020000001320000001`，Contact `LAN_IP:15060`
- 推 `cup.mp4`：H.264 → MPEG-PS → RTP 到 WVP INVITE 给出的端口
- 成功时 ZLM `getMediaList` 出现 `app=rtp`，预览  
  `http://127.0.0.1:9992/rtp/34020000001320000001_34020000001320000001.live.flv`
- **`seed_wvp_device.sh` 不是真注册**：只写库，WVP 点播无 PS，黑屏是预期
- 模拟器进程需保持；`stop_all.sh` 会一并停掉

字段映射（冻接口，不改名）：

| 业务字段 | 来源 |
| --- | --- |
| `device_type` | 固定 `gb28181` |
| `gb_device_id` | WVP 通道/设备国标编码 |
| `gb_platform_id` | WVP `sip.id`（配置 `gb28181.wvp.platform-id`） |
| `play_url` | `ws://<zlm>/rtp/<stream>.live.flv` |
| `stream_source_type` | `PLATFORM` |

### 同步接口

`POST /waring/device/syncGb28181`：

1. 必须 ZLM 可达（`listRtpServer` / `getMediaList`），否则 `ready=false`
2. `gb28181.wvp.enabled=true` 时再拉 WVP 设备目录；WVP 不可达则回退 RTP 通道
3. 写入继续走 `HDeviceService.upsertGb28181Device`

### 验收走查

1. 模拟器/IPC：SIP 服务器 = 演示机局域网 IP:**5060**，平台/设备 ID 与 WVP 一致  
2. WVP 设备列表可见 **注册成功**；过一段时间仍 **在线（保活）**  
3. WVP 点播后 ZLM `getMediaList` 有 `app=rtp`；网页预览有画面  
4. 断设备后 WVP/同步结果体现 **离线**（模拟器 SIGTERM 会注销；同步跟 SIP 在线，不跟 rtp 是否还在）
5. 网页「同步国标设备」出现国标行 + `gb_device_id`
6. 国标布控：ZLM 无 rtp 时走 `on_stream_not_found` 自动 INVITE

### 媒体半程（无 SIP 时自证）

```bash
bash scripts/verify_gb_rtp.sh gbcam001
# 向返回的 port 推 GB28181 PS 后，预览 ws://127.0.0.1:9992/rtp/gbcam001.live.flv
# 无推流时黑屏是预期
```

## 11. 变更记录


| 日期         | 说明                                             |
| ---------- | ---------------------------------------------- |
| 2026-08-31 | 初稿：WSL2 + D 盘、GPU 安装、3307/9994/9995、验收点 1 跑通记录 |
| 2026-08-31 | 一键启动 `start_easysva.ps1`；局域网 `:8080`；关 Clash/VPN 才能互访 |
| 2026-09-02 | Nginx `/live/` 代理 ZLM；同学经 `:8080` 看监控墙，不改 `zlm_server.host` |
| 2026-09-02 | §7.1 告警视频证据：录像引擎 A/M-SERVER、`/zlm/` URL、`backfill_alarm_video_url.sh` |
| 2026-09-03 | P3：#35 加列；同步拉数接 listRtpServer；说明本机无内置 SIP 5060 |
| 2026-09-04 | P3：`start_gb_sim.sh` 真 SIP REGISTER + WVP 点播；ZLM `app=rtp` 有 cup 画面 |
