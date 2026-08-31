<template>
  <div class="right-monitor-panel">
    <el-tabs v-model="activeTab" class="monitor-tabs" stretch>
      <el-tab-pane label="历史报警" name="history">
        <div class="history-grid">
          <div
            v-for="(item, index) in historyAlarms"
            :key="`history-${index}`"
            class="history-card"
          >
            <el-image
              class="history-image"
              :src="item.picture"
              fit="cover"
              :preview-src-list="item.picture ? [item.picture] : []"
            >
              <div slot="error" class="image-placeholder">暂无图片</div>
            </el-image>
            <div class="history-type">{{ item.alarmType }}</div>
            <div class="history-name">{{ item.deviceName }}</div>
          </div>
        </div>
      </el-tab-pane>

      <el-tab-pane label="实时监控" name="realtime">
        <div class="realtime-grid">
          <div
            v-for="(card, index) in streamCards"
            :key="`stream-${card.apeId || 'empty'}-${index}`"
            class="stream-card"
          >
            <div class="stream-header">
              <span class="stream-name">{{ card.name }}</span>
              <span class="stream-status" :class="`status-${card.status}`">{{ statusText(card.status) }}</span>
            </div>
            <div class="stream-body">
              <video
                :ref="`liveVideo${index}`"
                class="stream-video"
                muted
                autoplay
                playsinline
                preload="auto"
                controls
              ></video>
              <div v-if="card.status === 'empty'" class="stream-overlay">暂无设备</div>
              <div v-else-if="card.status === 'failed'" class="stream-overlay">播放失败</div>
              <div v-else-if="card.status === 'loading'" class="stream-overlay">加载中</div>
            </div>
          </div>
        </div>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script>
import flvjs from 'flv.js'
import { getDeviceList, previewDeviceMonitor } from '@/api/device'
import { getAlarmPhoto } from '@/api/system/kanban'

export default {
  name: 'RightMonitorPanel',
  data() {
    return {
      activeTab: 'history',
      historyAlarms: [],
      streamCards: [],
      historyTimer: null,
      realtimeTimer: null,
      realtimeSession: 0
    }
  },
  mounted() {
    this.initHistoryCards()
    this.fetchHistoryAlarms()
    this.startHistoryRefresh()
  },
  beforeDestroy() {
    this.stopHistoryRefresh()
    this.stopRealtimeRefresh()
    this.destroyAllPlayers()
  },
  watch: {
    activeTab(newTab) {
      if (newTab === 'realtime') {
        this.enterRealtimeMode()
        return
      }
      this.leaveRealtimeMode()
    }
  },
  methods: {
    initHistoryCards() {
      this.historyAlarms = Array.from({ length: 4 }).map(() => ({
        picture: '',
        deviceName: '--',
        alarmType: '--'
      }))
    },

    statusText(status) {
      if (status === 'playing') return '播放中'
      if (status === 'loading') return '加载中'
      if (status === 'failed') return '失败'
      return '空闲'
    },

    extractPreviewUrl(response) {
      if (!response) {
        return ''
      }
      const data = response.data || response
      return data.playUrl || data.previewUrl || data.url || data.streamUrl || data.rtspUrl || data.flvUrl || data.directSourceUrl || data.direct_source_url || data.liveUrl || data.live_url || ''
    },

    normalizeDevice(item) {
      const apeId = item.apeId || item.ape_id || item.deviceId || item.device_id || ''
      const name = item.name || item.deviceName || item.device_name || apeId || '未命名设备'
      return {
        apeId,
        name
      }
    },

    normalizeAlarm(item) {
      return {
        picture: item.picture_absolute_url || item.pictureAbsoluteUrl || item.picture_url || item.pictureUrl || '',
        deviceName: item.device_name || item.deviceName || item.name || '--',
        alarmType: item.alarm_type_name || item.alarmTypeName || item.alarm_type || '--'
      }
    },

    async fetchHistoryAlarms() {
      try {
        const response = await getAlarmPhoto()
        const list = (response && response.data && Array.isArray(response.data)) ? response.data : []
        const normalized = list.slice(0, 4).map(this.normalizeAlarm)
        while (normalized.length < 4) {
          normalized.push({ picture: '', deviceName: '--', alarmType: '--' })
        }
        this.historyAlarms = normalized
      } catch (error) {
        this.initHistoryCards()
      }
    },

    startHistoryRefresh() {
      if (this.historyTimer) {
        return
      }
      this.historyTimer = setInterval(() => {
        this.fetchHistoryAlarms()
      }, 20000)
    },

    stopHistoryRefresh() {
      if (!this.historyTimer) {
        return
      }
      clearInterval(this.historyTimer)
      this.historyTimer = null
    },

    async enterRealtimeMode() {
      await this.buildRealtimeStreams()
      this.startRealtimeRefresh()
    },

    leaveRealtimeMode() {
      this.realtimeSession += 1
      this.stopRealtimeRefresh()
      this.destroyAllPlayers()
    },

    startRealtimeRefresh() {
      if (this.realtimeTimer) {
        return
      }
      this.realtimeTimer = setInterval(() => {
        this.buildRealtimeStreams()
      }, 60000)
    },

    stopRealtimeRefresh() {
      if (!this.realtimeTimer) {
        return
      }
      clearInterval(this.realtimeTimer)
      this.realtimeTimer = null
    },

    async loadTopDevices() {
      const response = await getDeviceList({ pageNum: 1, pageSize: 4 })
      let rows = []
      if (response && Array.isArray(response.rows)) {
        rows = response.rows
      } else if (response && response.data && Array.isArray(response.data.rows)) {
        rows = response.data.rows
      } else if (response && response.data && Array.isArray(response.data.list)) {
        rows = response.data.list
      } else if (response && Array.isArray(response.data)) {
        rows = response.data
      }
      return rows.map(this.normalizeDevice).filter(item => item.apeId).slice(0, 4)
    },

    resetStreamCards(devices) {
      const cards = devices.map(device => ({
        apeId: device.apeId,
        name: device.name,
        status: 'loading',
        previewUrl: '',
        player: null
      }))
      while (cards.length < 4) {
        cards.push({
          apeId: '',
          name: '暂无设备',
          status: 'empty',
          previewUrl: '',
          player: null
        })
      }
      this.streamCards = cards
    },

    updateStreamCard(index, patch) {
      const current = this.streamCards[index] || {}
      this.$set(this.streamCards, index, {
        ...current,
        ...patch
      })
    },

    async buildRealtimeStreams() {
      if (this.activeTab !== 'realtime') {
        return
      }

      const sessionId = this.realtimeSession + 1
      this.realtimeSession = sessionId
      this.destroyAllPlayers()

      try {
        const devices = await this.loadTopDevices()
        if (sessionId !== this.realtimeSession || this.activeTab !== 'realtime') {
          return
        }

        this.resetStreamCards(devices)
        this.$nextTick(() => {
          this.openRealtimeStreams(sessionId)
        })
      } catch (error) {
        this.resetStreamCards([])
      }
    },

    async openRealtimeStreams(sessionId) {
      for (let index = 0; index < this.streamCards.length; index += 1) {
        const card = this.streamCards[index]
        if (!card || !card.apeId) {
          continue
        }
        this.updateStreamCard(index, { status: 'loading' })
        try {
          const response = await previewDeviceMonitor(card.apeId)
          if (sessionId !== this.realtimeSession || this.activeTab !== 'realtime') {
            return
          }
          const previewUrl = this.extractPreviewUrl(response)
          if (!previewUrl) {
            this.updateStreamCard(index, { status: 'failed', previewUrl: '' })
            continue
          }
          this.playStream(index, previewUrl, sessionId)
        } catch (error) {
          this.updateStreamCard(index, { status: 'failed', previewUrl: '' })
        }
      }
    },

    playStream(index, url, sessionId) {
      if (sessionId !== this.realtimeSession || this.activeTab !== 'realtime') {
        return
      }
      const video = this.$refs[`liveVideo${index}`]
      if (!video) {
        this.updateStreamCard(index, { status: 'failed', previewUrl: '' })
        return
      }

      this.destroyStreamPlayer(index)
      const isFlv = /\.flv($|[?#])/i.test(url)
      const isHttpOrWs = /^(https?:\/\/|wss?:\/\/)/i.test(url)

      if (isFlv && isHttpOrWs && flvjs.isSupported()) {
        const player = flvjs.createPlayer({
          type: 'flv',
          url,
          isLive: true
        })
        player.attachMediaElement(video)
        player.load()
        player.play().then(() => {
          this.updateStreamCard(index, { status: 'playing', previewUrl: url, player })
        }).catch(() => {
          this.updateStreamCard(index, { status: 'failed', previewUrl: url, player: null })
          this.destroyStreamPlayer(index)
        })
        this.updateStreamCard(index, { player, previewUrl: url })
        return
      }

      video.src = url
      video.play().then(() => {
        this.updateStreamCard(index, { status: 'playing', previewUrl: url })
      }).catch(() => {
        this.updateStreamCard(index, { status: 'failed', previewUrl: url })
      })
    },

    destroyStreamPlayer(index) {
      const card = this.streamCards[index]
      const video = this.$refs[`liveVideo${index}`]
      if (card && card.player) {
        try {
          card.player.unload()
          card.player.detachMediaElement()
          card.player.destroy()
        } catch (error) {
          // Ignore teardown errors to avoid blocking later stream recovery.
        }
      }
      if (video) {
        video.pause()
        video.removeAttribute('src')
        video.load()
      }
      if (card) {
        this.updateStreamCard(index, { player: null })
      }
    },

    destroyAllPlayers() {
      for (let index = 0; index < this.streamCards.length; index += 1) {
        this.destroyStreamPlayer(index)
      }
    }
  }
}
</script>

<style lang="scss" scoped>
.right-monitor-panel {
  height: 100%;
  padding: 8px 12px 4px;
  box-sizing: border-box;
}

.monitor-tabs {
  height: 100%;

  ::v-deep .el-tabs__header {
    margin: 0 0 8px;
  }

  ::v-deep .el-tabs__item {
    color: #9fd9ff;
    font-size: 13px;
  }

  ::v-deep .el-tabs__item.is-active {
    color: #30fbe5;
  }

  ::v-deep .el-tabs__active-bar {
    background-color: #30fbe5;
  }

  ::v-deep .el-tabs__content {
    height: 252px;
  }

  ::v-deep .el-tab-pane {
    height: 100%;
  }
}

.history-grid,
.realtime-grid {
  height: 100%;
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  grid-template-rows: repeat(2, minmax(0, 1fr));
  gap: 8px;
}

.history-card,
.stream-card {
  position: relative;
  border: 1px solid rgba(48, 251, 229, 0.35);
  background: rgba(5, 29, 66, 0.78);
  border-radius: 4px;
  overflow: hidden;
}

.history-image {
  width: 100%;
  height: calc(100% - 24px);
}

.image-placeholder {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #7eb8d6;
  font-size: 12px;
}

.history-type {
  position: absolute;
  top: 0;
  right: 0;
  font-size: 11px;
  line-height: 18px;
  padding: 0 8px;
  color: #00fdfa;
  background: rgba(9, 107, 167, 0.82);
}

.history-name {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  height: 24px;
  line-height: 24px;
  padding: 0 8px;
  font-size: 12px;
  color: #ffffff;
  background: linear-gradient(90deg, rgba(9, 107, 167, 0.95), rgba(9, 107, 167, 0.25));
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.stream-card {
  display: flex;
  flex-direction: column;
}

.stream-header {
  height: 22px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 8px;
  border-bottom: 1px solid rgba(48, 251, 229, 0.2);
  background: rgba(8, 45, 102, 0.7);
}

.stream-name {
  color: #ffffff;
  font-size: 12px;
  max-width: 72%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.stream-status {
  font-size: 11px;
}

.status-loading {
  color: #e6c25f;
}

.status-playing {
  color: #30fbe5;
}

.status-failed {
  color: #ff8f8f;
}

.status-empty {
  color: #7eb8d6;
}

.stream-body {
  position: relative;
  flex: 1;
  background: rgba(1, 16, 41, 0.85);
}

.stream-video {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.stream-overlay {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #9dd7f6;
  font-size: 12px;
  background: rgba(2, 22, 50, 0.72);
}
</style>
