# 流媒体架构说明（验收点 1 · RTSP/直连设备）

> 张柏烁维护。描述 **原版 easySVA** 在直连（DIRECT）设备场景下，视频如何从源进入 ZLM、如何被网页预览与 Analyzer 消费。国标 GB28181 在 **phase 3** 再补充。

## 1. 组件总览

```text
┌─────────────┐     ┌──────────────────┐     ┌─────────────────┐
│ 视频源       │     │ ZLMediaKit (ZLM) │     │ 业务后端         │
│ RTSP/RTMP   │────▶│ MediaServer      │◀───▶│ backend.jar     │
│ 摄像头/推流  │     │ :9992/9994/9995  │     │ :9114           │
└─────────────┘     └────────┬─────────┘     └────────┬────────┘
                             │                        │
                             │ FLV/WebSocket          │ 布控/设备 API
                             ▼                        ▼
                    ┌────────────────┐        ┌─────────────────┐
                    │ Nginx + Vue    │        │ Analyzer (C++)  │
                    │ :80 预览/告警页 │        │ YOLO 推理/告警   │
                    └────────────────┘        └─────────────────┘
```


| 组件         | 本组部署路径                             | 作用                                   |
| ---------- | ---------------------------------- | ------------------------------------ |
| ZLMediaKit | `/opt/SVA/mediaServer/MediaServer` | 拉流、转协议、录像、对外 HTTP-FLV/RTSP           |
| 后端         | `/opt/SVA/backend/backend.jar`     | 设备管理、布控任务、调 ZLM API、存告警              |
| Analyzer   | `/opt/SVA/server/Analyzer`         | 按布控配置拉流、YOLO 检测、行为规则、上报告警            |
| Nginx      | 系统包 `nginx-full`                   | 托管前端 `dist`，反向代理 `/prod-api/` → 9114 |
| MariaDB    | 端口 **3307**                        | 设备、布控、`zlm_server` 等配置               |




## 2. 直连（DIRECT）设备数据流



### 2.1 添加设备

1. 用户在 **设备管理** 填写 **视频流地址**（`direct_source_url`），类型为 **直连**。
2. 后端根据设备绑定的 `zlm_server_id` 读取 `zlm_server` 表（host、api_port、secret、app 等）。
3. 后端调用 ZLM **addStreamProxy**，让 ZLM **主动拉取** 用户填写的 URL，在 ZLM 上注册为 `live/<stream>`（stream 名由设备编码规范化生成）。

```text
用户填写: rtmp://127.0.0.1:9995/live/test1
     │
     ▼
backend → GET http://127.0.0.1:9992/index/api/addStreamProxy
          ?app=live&stream=<设备流名>&url=<direct_source_url>&...
     │
     ▼
ZLM 内部持有代理流，可供播放与再拉取
```



### 2.2 启动监控与视频预览

1. **启动监控**：后端确保设备对应 ZLM 代理流存在/在线，并更新设备 `monitor_status` 等字段。
2. **视频预览**：前端请求设备预览接口，后端返回 **WebSocket-FLV** 播放地址（形如 `ws://127.0.0.1:9992/live/<stream>.live.flv`），由页面 `flv.js` 播放。

预览走的是 **ZLM 对外媒体端口（9992）**，不是用户原始的 RTSP URL。

### 2.3 布控与 Analyzer

1. 用户在 **布控管理** 选择设备、算法（如 `yolo11n_80`）、检测目标（如 `cup`）、行为规则（如数量阈值 + 直接告警）及 **geometryConfig**（主区域多边形）。
2. **布控列表 → 启动**：后端调用 **Analyzer** 的布控接口，传入：
  - 设备流地址（ZLM 侧可拉的 URL）
  - 识别区域坐标
  - 算法与规则 JSON
3. Analyzer 拉流 → ONNX/YOLO 推理 → 行为规则判定 → 命中后 **HTTP 回调后端** 写入告警，并保存截图到 `upload/alarm/`。

```text
ZLM (live/test1)
     │ RTSP/RTMP 拉流
     ▼
Analyzer (GPU 推理)
     │ 规则命中
     ▼
POST http://127.0.0.1:9114/waring/waring/addFromSvaSimple
     │
     ▼
MariaDB 告警表 + 截图文件
     │
     ▼
前端「报警列表」/ 告警推送 WebSocket
```

Analyzer 全局配置：`/opt/SVA/config.json`（本组已将 `host` 设为 `127.0.0.1`，`mediaRtspPort` 等为 9994）。

## 3. ZLM 端口与播放地址（本组实测）

安装脚本自带的 `config.ini` **未使用** 国标默认 554，实际为：


| 协议                  | 端口       | 示例                                    |
| ------------------- | -------- | ------------------------------------- |
| HTTP API / HTTP-FLV | **9992** | `http://127.0.0.1:9992/index/api/...` |
| RTSP                | **9994** | `rtsp://127.0.0.1:9994/live/test1`    |
| RTMP                | **9995** | `rtmp://127.0.0.1:9995/live/test1`    |


网页预览常用：**WS-FLV**（由后端拼好 URL 给前端）。

查询当前流列表：

```bash
curl -s "http://127.0.0.1:9992/index/api/getMediaList?secret=<ZLM_SECRET>"
```

`secret` 与 `zlm_server.secret`、`config.ini` 中 `[api] secret` 一致。

## 4. 设备在线状态从哪来


| 层级                     | 来源                                                    |
| ---------------------- | ----------------------------------------------------- |
| 设备表 `is_online` / 监控状态 | 后端结合 ZLM 代理流是否存活、监控启停接口更新                             |
| ZLM 流是否存活              | `getMediaList` / 流无人观看超时（`streamNoneReaderDelayMS` 等） |
| 页面「实时监控」               | 展示 `monitor_status`（RUNNING / STOPPED 等）              |


直连设备 **不经过国标 SIP**；在线与否取决于 **ZLM 能否拉到** `direct_source_url` 以及监控任务是否启动。

## 5. 本组测试拓扑（无摄像头）

```text
ffmpeg (cup.mp4 循环)
    │ RTMP publish
    ▼
ZLM :9995  app=live  stream=test1
    │
    ├─▶ 设备 direct_source_url = rtmp://127.0.0.1:9995/live/test1
    │       → addStreamProxy（或同源复用）
    ├─▶ 视频预览（WS-FLV）
    └─▶ Analyzer 布控拉流 → YOLO 检 cup → 数量阈值告警
```



## 6. 告警截图路径


| 类型           | 路径                                                     |
| ------------ | ------------------------------------------------------ |
| 磁盘目录         | `/var/www/SVA-web/upload/alarm/`                       |
| Nginx URL 前缀 | `/alarm/`（见 `sites-enabled/default`）                   |
| 后端配置         | `config.json` → `uploadDir`: `/var/www/SVA-web/upload` |




## 7. 与后续阶段的边界


| 阶段          | 本文档范围                                                                                 |
| ----------- | ------------------------------------------------------------------------------------- |
| phase 1（当前） | DIRECT + RTSP/RTMP 进 ZLM，预览，原 YOLO 布控告警                                               |
| phase 3     | GB28181：SIP **5060**、国标注册、设备同步、国标播放 URL（由 A 在 `mediaServer/config.ini` 与后端 ZLM 客户端扩展） |


睡岗算法在 **phase 2**，走 Analyzer 推理链，不改变本章「流如何进 ZLM」的主路径。

## 8. 参考配置片段

**zlm_server 表（节选）**

```text
host=127.0.0.1, api_port=9992, media_http_port=9992, media_rtsp_port=9994, app=live
```

**Nginx 反向代理（节选）**

```text
location /prod-api/ { proxy_pass http://127.0.0.1:9114/; }
location /alarm/   { alias /var/www/SVA-web/upload/alarm/; }
root /var/www/SVA-web/dist/;
```



## 9. 变更记录


| 日期         | 说明                                         |
| ---------- | ------------------------------------------ |
| 2026-08-31 | 初稿：DIRECT 设备、ZLM 9992/9994/9995、预览与布控告警数据流 |


