<template>
  <div class="alarm-popup-container" v-if="messageVisible">
    <el-card class="box-card tech-alarm-card">
      <div slot="header" class="clearfix popup-header">
        <span class="popup-title">报警推送</span>
        <el-button class="popup-close" type="text" @click="messageVisible = false">关闭</el-button>
      </div>
      <el-row :gutter="12" class="popup-body-row">
        <!-- 左部分:图片展示 -->
        <el-col :span="11" class="popup-left-col">
          <div class="popup-image-panel">
            <el-image v-if="alarmImageUrl" class="popup-image" fit="cover" :src="alarmImageUrl">
              <div slot="error" class="image-fallback">暂无抓拍</div>
            </el-image>

            <div v-else class="image-fallback">暂无抓拍</div>
          </div>
        </el-col>
        <!-- 右部分:信息展示 -->
        <el-col :span="13" class="popup-right-col">
          <div class="popup-info-card">
            <div class="popup-info-title">报警信息</div>
            <div class="popup-info-list">
              <div class="popup-info-item">
                <span class="popup-info-label">报警类型</span>
                <span class="popup-info-value">{{ showMessage.type || '--' }}</span>
              </div>
              <div class="popup-info-item">
                <span class="popup-info-label">报警时间</span>
                <span class="popup-info-value time-value">{{ showMessage.time || '--' }}</span>
              </div>
              <div class="popup-info-item">
                <span class="popup-info-label">设备</span>
                <span class="popup-info-value">
                  <el-tag class="popup-device-tag" size="mini">{{ showMessage.device || '--' }}</el-tag>
                </span>
              </div>
            </div>
          </div>
        </el-col>
      </el-row>
    </el-card>
  </div>
</template>

<script>
export default {
  name: "WebsocketComponent",
  data() {
    return {
      messageVisible: false,
      showMessage: {},
      websocket: null, // WebSocket对象
      reconnectInterval: 3000, // 重连间隔时间（毫秒）
      heartbeatInterval: null, // 心跳定时器
    };
  },

  computed: {
    alarmImageUrl() {
      const message = this.showMessage || {}
      return message.url || message.picture_absolute_url || message.pictureAbsoluteUrl || message.picture_url || message.pictureUrl || ''
    }
  },

  created() {
    this.setupWebSocket();
  },
  methods: {
    getWebSocketUrl() {
      const wsProtocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
      const wsHost = window.location.host;
      const wsPath = '/websocket/message';
      return `${wsProtocol}//${wsHost}${wsPath}`;
    },

    setupWebSocket() {
      this.websocket = new WebSocket(this.getWebSocketUrl());
      this.websocket.onopen = this.onWebSocketOpen;
      this.websocket.onmessage = this.onWebSocketMessage;
      this.websocket.onclose = this.onWebSocketClose;
    },
    closeWebSocket() {
      if (this.websocket) {
        this.websocket.close(); // 关闭WebSocket连接
      }
    },

    onWebSocketOpen() {
      console.log("WebSocket 连接成功！");
      this.startHeartbeat();
    },

    onWebSocketMessage(event) {
      const data = event.data;

      if (typeof data === 'string' && (data.startsWith('{') || data.startsWith('['))) {
        try {
          const message = JSON.parse(data);
          if (message.newWarning !== undefined) {
            this.messageVisible = true;
            this.showMessage = message.newWarning;
            window.dispatchEvent(new CustomEvent('sva:alarm-push', {
              detail: {
                ts: Date.now(),
                warning: message.newWarning
              }
            }));
            return
          }
          if (message.type === 'detect.frame') {
            window.dispatchEvent(new CustomEvent('sva:detect-frame', {
              detail: {
                ts: Date.now(),
                frame: message
              }
            }))
            return
          }
          if (message.type === 'detect.event') {
            window.dispatchEvent(new CustomEvent('sva:detect-event', {
              detail: {
                ts: Date.now(),
                event: message
              }
            }))
          }
        } catch (error) {
          console.error("Failed to parse the received message as JSON:", error);
        }
      }
    },

    onWebSocketClose() {
      console.log("WebSocket 连接关闭！");
      this.stopHeartbeat();
      setTimeout(this.setupWebSocket, this.reconnectInterval);
    },

    sendMessage(message) {
      if (this.websocket && this.websocket.readyState === WebSocket.OPEN) {
        this.websocket.send(message); // 发送消息到 WebSocket 服务器
      }
    },

    startHeartbeat() {
      this.heartbeatInterval = setInterval(() => {
        if (this.websocket && this.websocket.readyState === WebSocket.OPEN) {
          this.websocket.send("ping");
        }
      }, 10000); // 每 10 秒发送一次心跳
    },

    stopHeartbeat() {
      if (this.heartbeatInterval) {
        clearInterval(this.heartbeatInterval); // 停止心跳检测定时器
      }
    },
  },

  beforeDestroy() {
    this.closeWebSocket();
  },
};
</script>
<style lang="scss" scoped>
.alarm-popup-container {
  position: fixed;
  top: auto;
  left: auto;
  bottom: 24px;
  right: 24px;
  margin: 0;
  z-index: 10000;
  pointer-events: none;
}

.clearfix:before,
.clearfix:after {
  display: table;
  content: "";
}

.clearfix:after {
  clear: both
}

.box-card {
  width: 480px;
  min-height: 268px;
}

.tech-alarm-card {
  position: relative;
  border-radius: 10px;
  border: 1px solid rgba(124, 193, 244, 0.38);
  background: linear-gradient(160deg, rgba(8, 35, 68, 0.9) 0%, rgba(4, 23, 49, 0.92) 68%, rgba(3, 18, 40, 0.94) 100%);
  box-shadow: 0 8px 18px rgba(5, 20, 45, 0.34), inset 0 1px 0 rgba(205, 227, 255, 0.16);
  overflow: hidden;
  pointer-events: auto;
}

.tech-alarm-card::before {
  content: "";
  position: absolute;
  inset: 0;
  border-radius: inherit;
  pointer-events: none;
  box-shadow: inset 0 0 0 1px rgba(115, 193, 240, 0.1);
}

.tech-alarm-card::after {
  content: "";
  position: absolute;
  inset: 0;
  border-radius: inherit;
  pointer-events: none;
  background:
    linear-gradient(135deg, rgba(75, 212, 255, 0.42), rgba(75, 212, 255, 0) 64%) top left / 62px 62px no-repeat,
    linear-gradient(-45deg, rgba(75, 212, 255, 0.3), rgba(75, 212, 255, 0) 66%) top right / 46px 46px no-repeat;
  opacity: 0.62;
}

.popup-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  line-height: 1;
}

.popup-title {
  position: relative;
  padding-left: 11px;
  font-size: 16px;
  font-weight: 700;
  letter-spacing: 1px;
  color: #e6f5ff;
  text-shadow: 0 0 8px rgba(61, 188, 241, 0.26);
}

.popup-title::before {
  content: "";
  position: absolute;
  left: 0;
  top: 50%;
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: #30fbe5;
  transform: translateY(-50%);
  box-shadow: 0 0 8px rgba(48, 251, 229, 0.7);
}

.popup-close {
  padding: 4px 0 !important;
  color: #9fd9ff !important;
  font-size: 13px;
}

.popup-close:hover,
.popup-close:focus {
  color: #dff3ff !important;
}

.popup-body-row {
  margin: 0 !important;
}

.popup-image-panel {
  margin-top: 4px;
  height: 192px;
  border-radius: 8px;
  border: 1px solid rgba(112, 188, 245, 0.24);
  background: linear-gradient(180deg, rgba(7, 33, 74, 0.7) 0%, rgba(3, 21, 50, 0.78) 100%);
  box-shadow: inset 0 0 0 1px rgba(118, 198, 248, 0.07);
  overflow: hidden;
}

.popup-image {
  width: 100%;
  height: 100%;
}

.image-fallback {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #8bb7da;
  font-size: 13px;
  letter-spacing: 0.5px;
}

.popup-info-card {
  margin-top: 4px;
  min-height: 192px;
  padding: 8px 8px 6px;
  border-radius: 8px;
  border: 1px solid rgba(120, 182, 236, 0.2);
  background: linear-gradient(180deg, rgba(8, 34, 68, 0.34) 0%, rgba(4, 22, 49, 0.44) 100%);
  box-shadow: inset 0 0 0 1px rgba(123, 203, 252, 0.06);
  position: relative;
}

.popup-info-title {
  font-size: 14px;
  font-weight: 700;
  color: #d9efff;
  line-height: 20px;
  margin-bottom: 6px;
  padding-left: 8px;
  border-left: 2px solid rgba(68, 212, 250, 0.58);
}

.popup-info-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.popup-info-item {
  display: grid;
  grid-template-columns: 74px 1fr;
  align-items: start;
  column-gap: 8px;
  padding: 7px 8px;
  border-radius: 5px;
  background: rgba(6, 29, 56, 0.34);
  border: 1px solid rgba(116, 182, 235, 0.12);
}

.popup-info-label {
  color: #9ec8e8;
  font-size: 13px;
  line-height: 20px;
}

.popup-info-value {
  color: #eef8ff;
  font-size: 13px;
  font-weight: 600;
  line-height: 20px;
  min-width: 0;
  word-break: break-word;
}

.time-value {
  display: inline-block;
  max-width: 100%;
  white-space: nowrap;
  word-break: keep-all;
  line-break: auto;
  overflow-wrap: normal;
  letter-spacing: 0;
}

.popup-device-tag {
  border-color: rgba(123, 211, 255, 0.4);
  color: #e6f9ff;
  background: rgba(11, 69, 106, 0.48);
}

::v-deep .el-card__header {
  padding: 12px 16px 11px;
  border-bottom: 1px solid rgba(115, 193, 240, 0.24);
  background: linear-gradient(180deg, rgba(14, 58, 100, 0.34) 0%, rgba(8, 32, 62, 0.12) 100%);
}

::v-deep .el-card__body {
  padding: 11px 12px 12px;
  color: #e9f9ff;
}
</style>
