# 系统架构分析

小组一份架构文：总图与表由同学 C 整理，直连流媒体实测由张柏烁并入。启动步骤只看 [deploy-notes.md](./deploy-notes.md)。阶段禁令看 [当前阶段.md](./当前阶段.md)。

五层与课件一致：**监控设备 → ZLMediaKit → SVA-server（C++）→ SVA-backend → Vue**。紫色为原系统（P1 已跑通），蓝色为学生增量（按 phase 点亮）。

课件算法是 **睡岗**（YOLO-Pose + 多帧时序），不是跌倒。国标设备进业务库是 **backend 调 ZLM REST/Hook**，不是 Analyzer 去同步。

## 总图（终态，按 phase 点亮）

```mermaid
flowchart TB
  subgraph devices [监控设备]
    RTSP[RTSP摄像头_原有_平台拉流]
    GB[GB28181国标IPC_P3_设备主动SIP注册]
  end

  subgraph zlm [ZLMediaKit]
    Pull[RTSP拉流模块_原有]
    SIP[GB28181_SIP_5060_P3]
    Fwd[转分发]
    Pull --> Fwd
    SIP --> Fwd
  end

  subgraph ai [SVA-server_C++]
    Dec[统一解码队列_原有]
    Yolo[YOLO通用检测_原有]
    Pose[YOLO-Pose睡岗_P2]
    Eng[告警判定_多帧时序_P2改造]
    Dec --> Yolo --> Eng
    Dec --> Pose --> Eng
  end

  subgraph be [SVA-backend]
    DevMgr[设备管理_P3兼国标]
    Task[布控任务_P2加睡岗类型]
    Alarm[告警入库_原有]
    Sync[从ZLM同步国标设备_P3]
    DevMgr --> Alarm
    Task --> Alarm
    Sync --> DevMgr
  end

  subgraph fe [Vue加Nginx]
    Pages[设备_布控_告警页]
    Prev[视频预览_WS-FLV]
  end

  RTSP --> Pull
  GB --> SIP
  Fwd -->|"Analyzer拉RTSP或RTMP"| Dec
  Fwd -->|"WS-FLV"| Prev
  Eng -->|HTTP告警| Alarm
  Pages --> be
  Sync -.->|REST_Hook| zlm
  Alarm --> DB[(MariaDB_3307)]
```

| 阶段 | 图上要亮的部分 |
| --- | --- |
| P1 已完成 | RTSP/直连 → ZLM 拉流 → WS-FLV 预览；Analyzer 原 YOLO；backend 告警入库；Vue 原页面 |
| P2 当前 | Pose 睡岗 + 时序防误报；布控增加睡岗类型；告警页展示睡岗 |
| P3 | SIP 5060、国标 IPC；backend 从 ZLM 同步设备；列表区分 RTSP/国标；Analyzer 向 ZLM 取播放 URL |

P1 已过，正在做睡岗。国标仍禁止。已知布控详情可能 `live-output` 404，**以报警列表为准**；C 在改告警/布控页时可顺手修，不要顺手做国标。

## 和课件图的差别

- Analyzer **向 ZLM 拉流**，不是 ZLM 专线吐帧。
- 预览用 **WS-FLV**（本组 ZLM HTTP 9992，RTSP 9994，RTMP 9995），不是只写 FLV。
- 国标同步：A 写 ZLM 客户端，C 做设备表和「同步」按钮；Analyzer 不负责设备目录。
- 睡岗判定：关键点 → 头部俯仰角 → 连续多帧，再走原告警 HTTP；原 YOLO 保留。
- 库是 **MariaDB**（演示机 3307）。

## 仓库与进程（本组路径）

```text
backend/     Java :9114          web/          Vue + Nginx :80
server/      C++ Analyzer     mediaServer/  ZLMediaKit
```

| 组件 | 路径 | 作用 |
| --- | --- | --- |
| ZLMediaKit | `/opt/SVA/mediaServer/MediaServer` | 拉流、转协议、HTTP-FLV/RTSP |
| 后端 | `/opt/SVA/backend/backend.jar` | 设备、布控、调 ZLM、存告警 |
| Analyzer | `/opt/SVA/server/Analyzer` | 拉流、YOLO、上报告警 |
| Nginx | 系统包 | `dist` + `/prod-api/` → 9114 |
| MariaDB | 端口 3307 | 设备、布控、`zlm_server` |

登录 `admin` / `admin123`。

## 直连设备数据流（张柏烁实测）

### 添加设备

1. 设备管理填写 **视频流地址** `direct_source_url`，类型为直连。
2. 后端按 `zlm_server_id` 读 `zlm_server`（host、api_port、secret、app）。
3. 调用 ZLM **addStreamProxy**，把用户 URL 拉成 `live/<stream>`（stream 名由设备编码生成）。

```text
用户填写 rtmp://127.0.0.1:9995/live/test1
  → GET http://127.0.0.1:9992/index/api/addStreamProxy?app=live&stream=<流名>&url=...
  → ZLM 持有代理流，供预览与 Analyzer 再拉
```

### 启动监控与预览

启动监控后后端更新 `monitor_status`。预览接口返回 **WS-FLV**（如 `ws://127.0.0.1:9992/live/<stream>.live.flv`），页面 `flv.js` 播放。走的是 ZLM **9992**，不是原始 RTSP。

### 布控与 Analyzer

布控选设备、算法（如 `yolo11n_80`）、目标（如 `cup`）、数量阈值 + 直接告警、`geometryConfig`（主区域 ≥3 点）。启动后 backend 把 ZLM 侧可拉 URL、区域、规则发给 Analyzer。Analyzer 拉流 → ONNX/YOLO → 规则命中 → `POST http://127.0.0.1:9114/waring/waring/addFromSvaSimple` → `h_waring` + 截图。配置：`/opt/SVA/config.json`（`host=127.0.0.1`，`mediaRtspPort=9994`）。

### ZLM 端口（安装脚本未用 554）

| 协议 | 端口 | 示例 |
| --- | --- | --- |
| HTTP API / HTTP-FLV | 9992 | `http://127.0.0.1:9992/index/api/...` |
| RTSP | 9994 | `rtsp://127.0.0.1:9994/live/test1` |
| RTMP | 9995 | `rtmp://127.0.0.1:9995/live/test1` |

查流：`curl -s "http://127.0.0.1:9992/index/api/getMediaList?secret=<与 zlm_server.secret 及 config.ini [api] secret 一致>"`

### 在线状态

`h_device.is_online` / `monitor_status` 由后端结合 ZLM 代理流是否存活、监控启停更新。ZLM 用 `getMediaList`。直连设备不经过 SIP。

### 无摄像头测试

```text
ffmpeg 循环 cup.mp4 → RTMP 推到 ZLM :9995 live/test1
  → 设备 direct_source_url = rtmp://127.0.0.1:9995/live/test1
  → 预览 WS-FLV；Analyzer 拉流 YOLO 检 cup → 告警
```

### 截图与 Nginx

磁盘 `/var/www/SVA-web/upload/alarm/`，URL 前缀 `/alarm/`。`config.json` 的 `uploadDir` 为 `/var/www/SVA-web/upload`。

```text
zlm_server: host=127.0.0.1, api_port=9992, media_http_port=9992, media_rtsp_port=9994, app=live
nginx: location /prod-api/ → 9114；location /alarm/ → upload/alarm/；root dist/
```

睡岗不改变「流如何进 ZLM」这条路径，只改 Analyzer 判定和告警类型。国标在 P3 开 SIP 5060。

## 三张表

P2 只动布控选项和告警类型，**不要改国标设备字段**。

**设备 `h_device`**（`HDevice.java`，`web/src/views/device/`）：`ape_id`、`name`、`stream_source_type`、`direct_source_url`、`play_url`、`zlm_proxy_key`、`zlm_server_id`、`sva_server_id`、`is_online`、`monitor_status`。P3 再加类型/国标 ID。

**布控 `deployment_task`**（`DeploymentTask.java`，`web/src/views/deployment/`）：`deployment_id`、`device_id`、`algorithm_code`、`target_code`、`geometry_config`、`stream_url`、`status`。P2 再加睡岗选项。

**告警 `h_waring`**（`HWaring.java`，`web/src/views/warning/index.vue`）：`alarm_type`、`device_id`、`alarm_time`、`picture_url`、`is_handle`、`sva_behavior_type`。P2 再加睡岗类型。

## 验收走查

截图在 [docs/photo/](./photo/)。

1. 演示机启动服务，打开后台。
2. 新增直连设备，启动监控，预览有画面。
3. 布控原 YOLO（本组 `yolo11n_80` + 如 `cup`），画区域后启动。
4. 报警列表有记录和截图。
5. 三人已给 Gitee 上游五仓点 Star。

## 后面改哪里

- 睡岗：B 改 `server/`（Pose + 时序）；C 改告警类型、布控选项、告警页。
- 国标：A 改 SIP 与 ZLM 同步接口；C 改设备表与列表；B 让 Analyzer 取国标播放 URL。
