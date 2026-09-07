<template>
  <div class="center-switch-panel">
    <div
      v-if="displayMode === 'realtime' && showLayoutSwitch"
      class="layout-switch-bar"
    >
      <button
        type="button"
        class="layout-switch-button"
        :class="{ active: currentLayout === 2 }"
        @click="changeLayout(2)"
      >
        2x2
      </button>
      <button
        type="button"
        class="layout-switch-button"
        :class="{ active: currentLayout === 3 }"
        @click="changeLayout(3)"
      >
        3x3
      </button>
    </div>
    <div class="tab-body">
      <WarningHistory v-show="displayMode === 'history'" />

      <div
        v-show="displayMode === 'realtime'"
        class="realtime-grid-wrap"
        :class="`grid-${currentLayout}`"
      >
        <div
          v-for="(card, index) in streamCards"
          :key="`stream-${card.sourceId || card.id || 'empty'}-${index}`"
          class="stream-card"
          :class="`card-${card.status}`"
          @click="promoteDecode(index)"
        >
          <div class="stream-header">
            <div class="stream-header-main">
              <span class="stream-name">{{ card.name }}</span>
              <span
                v-if="card.sourceType === 'task'"
                class="stream-source-badge"
                :class="{ 'stream-source-badge--overlay': shouldUseFrontendOverlay(card) }"
              >{{ sourceBadgeText(card) }}</span>
            </div>
            <span class="stream-status" :class="`status-${card.status}`">{{ statusText(card.status) }}</span>
          </div>
          <div class="stream-body">
            <div :ref="`streamFrame${index}`" class="stream-frame">
              <video
                :ref="`liveVideo${index}`"
                class="stream-video"
                :style="videoStyle"
                muted
                autoplay
                playsinline
                preload="auto"
                @loadedmetadata="handleVideoLoaded(index)"
                @dblclick="handleSingleFullscreen(index)"
              />
              <canvas
                v-if="card.sourceType === 'task'"
                :ref="`overlayCanvas${index}`"
                class="stream-overlay-canvas"
              />
            </div>
            <button
              v-if="card.status !== 'empty' && card.playUrl"
              type="button"
              class="single-fullscreen-btn"
              @click.stop="handleSingleFullscreen(index)"
            >
              全屏
            </button>
            <div v-if="card.status === 'empty'" class="stream-overlay">暂无设备</div>
            <div v-else-if="card.status === 'failed'" class="stream-overlay">播放失败</div>
            <div v-else-if="card.status === 'loading'" class="stream-overlay">加载中</div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import WarningHistory from './warning-history.vue'
import { getDeploymentDetail, updateDeploymentLiveOutput } from '@/api/deployment'
import { getScreenWallStreams, normalizeScreenWallStream } from '@/api/screenWall'
import { OVERLAY_DELAY_DEFAULT_MS, loadOverlayDelayMs } from '@/utils/systemRuntimeConfig'
import { getFieldValue } from '@/utils/fieldMap'
import { applyContainStyle, destroyFlvPlayer, pauseFlvPlayer, playHttpFlv, resetVideoElement, resumeFlvPlayer } from '@/utils/flvPlayer'

export default {
  name: 'CenterSwitchPanel',
  components: {
    WarningHistory
  },
  props: {
    displayMode: {
      type: String,
      default: 'history'
    },
    layoutSize: {
      type: Number,
      default: 2,
      validator(value) {
        return value === 2 || value === 3
      }
    },
    showLayoutSwitch: {
      type: Boolean,
      default: false
    },
    videoFit: {
      type: String,
      default: 'contain',
      validator(value) {
        return ['cover', 'contain'].includes(value)
      }
    }
  },
  data() {
    return {
      streamCards: [],
      realtimeTimer: null,
      wallSyncTimer: null,
      wallMembershipKey: '',
      wallPlaybackKey: '',
      realtimeSession: 0,
      currentLayout: this.layoutSize === 3 ? 3 : 2,
      resizeHandler: null,
      overlayDelayMs: OVERLAY_DELAY_DEFAULT_MS
    }
  },
  computed: {
    maxStreams() {
      return this.currentLayout * this.currentLayout
    },
    videoStyle() {
      return {
        objectFit: this.videoFit
      }
    },
    maxLiveDecode() {
      return this.currentLayout === 3 ? 4 : this.maxStreams
    }
  },
  watch: {
    displayMode: {
      immediate: true,
      handler(mode) {
        if (mode === 'realtime') {
          this.enterRealtimeMode()
          return
        }
        this.leaveRealtimeMode()
      }
    },
    layoutSize(value) {
      const normalized = value === 3 ? 3 : 2
      if (this.currentLayout === normalized) {
        return
      }
      this.currentLayout = normalized
      if (this.displayMode === 'realtime') {
        this.buildRealtimeStreams()
      }
    }
  },
  created() {
    this._overlayFrames = Object.create(null)
    this._overlayPending = Object.create(null)
    this._overlayClearTimer = Object.create(null)
    this._overlayDelayTimer = Object.create(null)
    this._overlayRaf = 0
    this._overlayDirty = Object.create(null)
    this.decodeIndexes = []
  },
  mounted() {
    this.resizeHandler = () => {
      this.syncAllOverlayCanvas()
    }
    this.loadOverlayDelayConfig()
    window.addEventListener('resize', this.resizeHandler)
    window.addEventListener('sva:detect-frame', this.handleDetectFramePush)
  },
  beforeDestroy() {
    this.leaveRealtimeMode()
    window.removeEventListener('resize', this.resizeHandler)
    window.removeEventListener('sva:detect-frame', this.handleDetectFramePush)
  },
  methods: {
    getFieldValue,
    statusText(status) {
      if (status === 'playing') return '播放中'
      if (status === 'loading') return '加载中'
      if (status === 'failed') return '失败'
      return '空闲'
    },
    sourceBadgeText(card) {
      if (!card || card.sourceType !== 'task') {
        return ''
      }
      if (card.taskPushEnabled) {
        return '算法流'
      }
      if (card.frontendOverlayEnabled) {
        return '前端画框'
      }
      return '原始流'
    },
    toBoolean(value, defaultValue = false) {
      if (value === undefined || value === null || value === '') {
        return defaultValue
      }
      if (typeof value === 'boolean') {
        return value
      }
      if (typeof value === 'number') {
        return value !== 0
      }
      if (typeof value === 'string') {
        const normalized = value.trim().toLowerCase()
        if (['true', '1', 'yes', 'y'].includes(normalized)) {
          return true
        }
        if (['false', '0', 'no', 'n'].includes(normalized)) {
          return false
        }
      }
      return Boolean(value)
    },
    extractResponseData(response) {
      if (!response) {
        return {}
      }
      const data = response.data !== undefined ? response.data : response
      return data && typeof data === 'object' ? data : {}
    },
    buildStreamCard(stream) {
      return {
        id: stream.id || '',
        sourceId: stream.sourceId || '',
        sourceType: stream.sourceType || '',
        deviceId: stream.deviceId || '',
        name: stream.name || '未命名流',
        status: 'loading',
        playUrl: stream.playUrl || '',
        player: null,
        taskPushEnabled: this.toBoolean(stream.taskPushEnabled, false),
        frontendOverlayEnabled: this.toBoolean(stream.frontendOverlayEnabled, false),
        detectFrame: null,
        pendingDetectFrame: null,
        detectFrameClearTimer: null,
        detectFrameRenderTimer: null
      }
    },
    buildEmptyCard() {
      return {
        id: '',
        sourceId: '',
        sourceType: '',
        deviceId: '',
        name: '暂无设备',
        status: 'empty',
        playUrl: '',
        player: null,
        taskPushEnabled: false,
        frontendOverlayEnabled: false,
        detectFrame: null,
        pendingDetectFrame: null,
        detectFrameClearTimer: null,
        detectFrameRenderTimer: null
      }
    },
    async loadOverlayDelayConfig() {
      this.overlayDelayMs = await loadOverlayDelayMs(this.overlayDelayMs)
    },
    async listWallStreamBasics() {
      const rawList = this.extractWallStreamList(await getScreenWallStreams('main'))
      return rawList
        .map(item => {
          const normalized = normalizeScreenWallStream(item)
          return {
            ...normalized,
            id: item.id || item.streamId || item.stream_id || '',
            sourceId: normalized.sourceId || item.sourceId || item.source_id || '',
            sourceType: normalized.sourceType || item.sourceType || item.source_type || '',
            deviceId: normalized.deviceId || item.deviceId || item.device_id || '',
            name: normalized.title || item.title || item.name || '未命名流',
            playUrl: normalized.playUrl || item.playUrl || item.play_url || ''
          }
        })
        .filter(item => item.enabled !== false && item.playUrl)
        .sort((a, b) => {
          const aIndex = Number.isFinite(Number(a.slotIndex)) ? Number(a.slotIndex) : Number.MAX_SAFE_INTEGER
          const bIndex = Number.isFinite(Number(b.slotIndex)) ? Number(b.slotIndex) : Number.MAX_SAFE_INTEGER
          return aIndex - bIndex
        })
        .slice(0, this.maxStreams)
    },
    async loadWallStreams() {
      const basicStreams = await this.listWallStreamBasics()
      return Promise.all(basicStreams.map(stream => this.enrichWallStream(stream)))
    },
    async enrichWallStream(stream) {
      if (!stream || String(stream.sourceType || '').toLowerCase() !== 'task' || !stream.sourceId) {
        return stream
      }

      try {
        const detail = this.extractResponseData(await getDeploymentDetail(stream.sourceId))
        let algorithmStreamUrl = ''
        try {
          const liveOutputResponse = await updateDeploymentLiveOutput(stream.sourceId, {
            videoEnabled: true,
            liveEventEnabled: true,
            wsEventFps: 8
          })
          const liveOutputData = this.extractResponseData(liveOutputResponse)
          algorithmStreamUrl = this.getFieldValue(liveOutputData, 'algorithmStreamUrl', 'algorithm_stream_url') || ''
        } catch (error) {
          algorithmStreamUrl = ''
        }

        return {
          ...stream,
          deviceId: this.getFieldValue(detail, 'deviceId', 'device_id', 'apeId', 'ape_id') || stream.deviceId || '',
          name: this.getFieldValue(detail, 'taskName', 'task_name', 'title', 'name') || stream.name,
          playUrl: algorithmStreamUrl || stream.playUrl,
          taskPushEnabled: Boolean(algorithmStreamUrl),
          frontendOverlayEnabled: false
        }
      } catch (error) {
        return {
          ...stream,
          taskPushEnabled: false,
          frontendOverlayEnabled: false
        }
      }
    },
    resetStreamCards(streams) {
      const cards = streams.map(stream => this.buildStreamCard(stream))
      while (cards.length < this.maxStreams) {
        cards.push(this.buildEmptyCard())
      }
      this.streamCards = cards
    },
    changeLayout(size) {
      if (size !== 2 && size !== 3) {
        return
      }
      if (this.currentLayout === size) {
        return
      }
      this.currentLayout = size
      if (this.displayMode === 'realtime') {
        this.buildRealtimeStreams()
      }
    },
    updateStreamCard(index, patch) {
      const current = this.streamCards[index] || {}
      this.$set(this.streamCards, index, {
        ...current,
        ...patch
      })
    },
    async enterRealtimeMode() {
      await this.buildRealtimeStreams()
      this.startRealtimeRefresh()
    },
    leaveRealtimeMode() {
      this.realtimeSession += 1
      this.stopRealtimeRefresh()
      this.destroyAllPlayers()
      this.clearAllOverlayState()
      this.streamCards = []
      this.wallMembershipKey = ''
      this.wallPlaybackKey = ''
      this.decodeIndexes = []
    },
    startRealtimeRefresh() {
      if (!this.realtimeTimer) {
        this.realtimeTimer = setInterval(() => {
          this.buildRealtimeStreams()
        }, 60000)
      }
      if (!this.wallSyncTimer) {
        this.wallSyncTimer = setInterval(() => {
          this.syncWallMembership()
        }, 8000)
      }
    },
    stopRealtimeRefresh() {
      if (this.realtimeTimer) {
        clearInterval(this.realtimeTimer)
        this.realtimeTimer = null
      }
      if (this.wallSyncTimer) {
        clearInterval(this.wallSyncTimer)
        this.wallSyncTimer = null
      }
    },
    wallStreamSignature(item = {}) {
      const id = item.id || item.streamId || item.stream_id || ''
      const sourceId = item.sourceId || item.source_id || ''
      return `${id}|${sourceId}`
    },
    wallPlaybackSignature(item = {}) {
      const playUrl = item.playUrl || item.play_url || ''
      return `${this.wallStreamSignature(item)}|${playUrl}`
    },
    extractWallStreamList(response) {
      return (response && Array.isArray(response.data) && response.data) ||
        (response && response.data && Array.isArray(response.data.rows) && response.data.rows) ||
        (response && response.data && Array.isArray(response.data.list) && response.data.list) ||
        (response && Array.isArray(response.rows) && response.rows) ||
        []
    },
    async syncWallMembership() {
      if (this.displayMode !== 'realtime') {
        return
      }
      try {
        const basicStreams = await this.listWallStreamBasics()
        const key = `${this.currentLayout}:${this.maxStreams}|${basicStreams.map(item => this.wallStreamSignature(item)).sort().join('||')}`
        if (key === this.wallMembershipKey) {
          return
        }
        await this.buildRealtimeStreams()
      } catch (error) {
        // 轮询失败不打断当前画面
      }
    },
    async buildRealtimeStreams() {
      if (this.displayMode !== 'realtime') {
        return
      }

      try {
        const streams = await this.loadWallStreams()
        if (this.displayMode !== 'realtime') {
          return
        }
        const memberKey = `${this.currentLayout}:${this.maxStreams}|${streams.map(item => this.wallStreamSignature(item)).sort().join('||')}`
        const playbackKey = streams.map(item => this.wallPlaybackSignature(item)).sort().join('||')
        if (playbackKey === this.wallPlaybackKey && this.streamCards.length) {
          this.wallMembershipKey = memberKey
          return
        }

        const sessionId = this.realtimeSession + 1
        this.realtimeSession = sessionId
        this.destroyAllPlayers()
        this.clearAllOverlayState()

        this.wallMembershipKey = memberKey
        this.wallPlaybackKey = playbackKey
        this.resetStreamCards(streams)
        this.$nextTick(() => {
          if (sessionId !== this.realtimeSession || this.displayMode !== 'realtime') {
            return
          }
          this.syncAllOverlayCanvas()
          this.openRealtimeStreams(sessionId)
        })
      } catch (error) {
        this.resetStreamCards([])
      }
    },
    async openRealtimeStreams(sessionId) {
      const liveIndexes = []
      for (let index = 0; index < this.streamCards.length; index += 1) {
        const card = this.streamCards[index]
        if (card && card.playUrl) {
          liveIndexes.push(index)
        }
      }
      this.decodeIndexes = liveIndexes.slice(0, this.maxLiveDecode)
      for (let index = 0; index < this.streamCards.length; index += 1) {
        const card = this.streamCards[index]
        if (!card || !card.playUrl) {
          continue
        }
        if (sessionId !== this.realtimeSession || this.displayMode !== 'realtime') {
          return
        }
        if (this.decodeIndexes.indexOf(index) === -1) {
          this.updateStreamCard(index, { status: 'playing' })
          continue
        }
        this.updateStreamCard(index, { status: 'loading' })
        this.playStream(index, card.playUrl, sessionId)
      }
    },
    handleVideoLoaded(index) {
      this.syncOverlayCanvas(index)
      this.drawDetectOverlayForCard(index)
    },
    playStream(index, url, sessionId) {
      if (sessionId !== this.realtimeSession || this.displayMode !== 'realtime') {
        return
      }

      const video = this.$refs[`liveVideo${index}`]
      const videoElement = Array.isArray(video) ? video[0] : video
      if (!videoElement) {
        this.updateStreamCard(index, { status: 'failed' })
        return
      }

      applyContainStyle(videoElement)
      videoElement.onloadedmetadata = () => {
        applyContainStyle(videoElement)
      }

      this.destroyStreamPlayer(index)
      this.clearOverlayCanvas(index)

      const player = playHttpFlv(videoElement, url)
      if (player) {
        this.$nextTick(() => {
          applyContainStyle(videoElement)
        })
        player.play().then(() => {
          this.updateStreamCard(index, { status: 'playing', player })
          if (this.decodeIndexes.indexOf(index) === -1) {
            this.pauseStream(index)
          }
        }).catch(() => {
          this.updateStreamCard(index, { status: 'failed', player: null })
          this.destroyStreamPlayer(index)
        })
        this.updateStreamCard(index, { player })
        return
      }

      videoElement.src = url
      videoElement.play().then(() => {
        this.updateStreamCard(index, { status: 'playing' })
      }).catch(() => {
        this.updateStreamCard(index, { status: 'failed' })
      })
    },
    destroyStreamPlayer(index) {
      const card = this.streamCards[index]
      const video = this.$refs[`liveVideo${index}`]
      const videoElement = Array.isArray(video) ? video[0] : video

      this.clearDetectFrame(index, false)

      if (card && card.player) {
        try {
          destroyFlvPlayer(card.player)
        } catch (error) {
          // Ignore teardown errors to avoid blocking later stream recovery.
        }
      }

      if (videoElement) {
        videoElement.onloadedmetadata = null
        resetVideoElement(videoElement)
      }

      this.clearOverlayCanvas(index)

      if (card) {
        this.updateStreamCard(index, { player: null })
      }
    },
    destroyAllPlayers() {
      for (let index = 0; index < this.streamCards.length; index += 1) {
        this.destroyStreamPlayer(index)
      }
    },
    applyDecodeBudget() {
      const liveIndexes = []
      for (let index = 0; index < this.streamCards.length; index += 1) {
        const card = this.streamCards[index]
        if (card && card.playUrl) {
          liveIndexes.push(index)
        }
      }
      const kept = this.decodeIndexes.filter(index => liveIndexes.indexOf(index) !== -1)
      liveIndexes.forEach(index => {
        if (kept.indexOf(index) === -1 && kept.length < this.maxLiveDecode) {
          kept.push(index)
        }
      })
      this.decodeIndexes = kept.slice(0, this.maxLiveDecode)
      liveIndexes.forEach(index => {
        if (this.decodeIndexes.indexOf(index) === -1) {
          this.pauseStream(index)
        }
      })
    },
    pauseStream(index) {
      const card = this.streamCards[index]
      const videoElement = this.getVideoElement(index)
      pauseFlvPlayer(card && card.player, videoElement)
    },
    resumeStream(index) {
      const card = this.streamCards[index]
      const videoElement = this.getVideoElement(index)
      if (!card || !card.playUrl) {
        return
      }
      if (card.player && videoElement) {
        resumeFlvPlayer(card.player, videoElement)
        return
      }
      this.playStream(index, card.playUrl, this.realtimeSession)
    },
    promoteDecode(index) {
      if (this.displayMode !== 'realtime' || this.currentLayout !== 3) {
        return
      }
      const card = this.streamCards[index]
      if (!card || !card.playUrl) {
        return
      }
      if (this.decodeIndexes.indexOf(index) !== -1) {
        this.resumeStream(index)
        return
      }
      const evicted = this.decodeIndexes.length >= this.maxLiveDecode ? this.decodeIndexes[0] : null
      const next = this.decodeIndexes.filter(item => item !== evicted)
      next.push(index)
      this.decodeIndexes = next.slice(-this.maxLiveDecode)
      if (evicted !== null && evicted !== index) {
        this.pauseStream(evicted)
      }
      this.resumeStream(index)
    },
    handleSingleFullscreen(index) {
      this.promoteDecode(index)
      const card = this.streamCards[index]
      if (!card || card.status === 'empty' || !card.playUrl) {
        return
      }

      const video = this.$refs[`liveVideo${index}`]
      const videoElement = Array.isArray(video) ? video[0] : video
      if (!videoElement) {
        return
      }

      const requestFullscreen =
        videoElement.requestFullscreen ||
        videoElement.webkitRequestFullscreen ||
        videoElement.msRequestFullscreen

      if (typeof requestFullscreen !== 'function') {
        return
      }

      try {
        const result = requestFullscreen.call(videoElement)
        if (result && typeof result.catch === 'function') {
          result.catch(() => {
            // Ignore fullscreen rejections (e.g. browser policy) to keep playback stable.
          })
        }
      } catch (error) {
        // Ignore fullscreen exceptions to avoid interrupting the preview workflow.
      }
    },
    shouldUseFrontendOverlay(card) {
      return Boolean(card && card.sourceType === 'task' && !card.taskPushEnabled && card.frontendOverlayEnabled)
    },
    handleDetectFramePush(event) {
      if (this.displayMode !== 'realtime') {
        return
      }

      const detail = (event && event.detail) || {}
      const frame = detail.frame || null
      if (!frame || frame.type !== 'detect.frame') {
        return
      }

      for (let index = 0; index < this.streamCards.length; index += 1) {
        const card = this.streamCards[index]
        if (!this.isDetectFrameMatched(card, frame)) {
          continue
        }
        if (!this.shouldUseFrontendOverlay(card)) {
          this.clearDetectFrame(index)
          continue
        }

        const renderMode = String(frame.renderMode || '').trim().toLowerCase()
        if (renderMode !== 'ws_overlay') {
          this.clearDetectFrame(index)
          continue
        }

        const nextSeq = Number(frame.frameSeq || 0)
        const current = this._overlayFrames[index]
        const currentSeq = Number(current && current.frameSeq)
        if (Number.isFinite(currentSeq) && Number.isFinite(nextSeq) && nextSeq > 0 && currentSeq > nextSeq) {
          continue
        }

        this.scheduleDetectFrameRender(index, frame)
      }
    },
    isDetectFrameMatched(card, frame) {
      if (!card || card.sourceType !== 'task' || !frame) {
        return false
      }
      const sourceId = String(card.sourceId || '').trim()
      const deviceId = String(card.deviceId || '').trim()
      const controlCode = String(frame.controlCode || frame.control_code || '').trim()
      const streamCode = String(frame.streamCode || '').trim()
      if (sourceId && controlCode && sourceId === controlCode) {
        return true
      }
      if (deviceId && streamCode && deviceId === streamCode) {
        return true
      }
      return false
    },
    applyDetectFrame(index, frame) {
      if (this._overlayDelayTimer[index]) {
        clearTimeout(this._overlayDelayTimer[index])
        this._overlayDelayTimer[index] = null
      }
      this._overlayPending[index] = null
      this._overlayFrames[index] = frame
      this.queueOverlayDraw(index)
      this.scheduleDetectFrameClear(index)
    },
    scheduleDetectFrameRender(index, frame) {
      const delayMs = Number(this.overlayDelayMs || 0)
      if (!delayMs || this._overlayFrames[index]) {
        this.applyDetectFrame(index, frame)
        return
      }
      this._overlayPending[index] = frame
      if (this._overlayDelayTimer[index]) {
        return
      }
      this._overlayDelayTimer[index] = setTimeout(() => {
        this._overlayDelayTimer[index] = null
        const card = this.streamCards[index]
        const pendingFrame = this._overlayPending[index]
        this._overlayPending[index] = null
        if (!this.shouldUseFrontendOverlay(card)) {
          this.clearDetectFrame(index)
          return
        }
        if (pendingFrame) {
          this.applyDetectFrame(index, pendingFrame)
        }
      }, delayMs)
    },
    scheduleDetectFrameClear(index) {
      if (this._overlayClearTimer[index]) {
        clearTimeout(this._overlayClearTimer[index])
      }
      this._overlayClearTimer[index] = setTimeout(() => {
        this._overlayClearTimer[index] = null
        this.clearDetectFrame(index)
      }, 1500)
    },
    clearDetectFrame(index, redraw = true) {
      if (this._overlayDelayTimer[index]) {
        clearTimeout(this._overlayDelayTimer[index])
        this._overlayDelayTimer[index] = null
      }
      if (this._overlayClearTimer[index]) {
        clearTimeout(this._overlayClearTimer[index])
        this._overlayClearTimer[index] = null
      }
      this._overlayPending[index] = null
      this._overlayFrames[index] = null
      if (redraw) {
        this.clearOverlayCanvas(index)
      }
    },
    clearAllOverlayState() {
      if (this._overlayRaf) {
        cancelAnimationFrame(this._overlayRaf)
        this._overlayRaf = 0
      }
      Object.keys(this._overlayDelayTimer || {}).forEach(key => {
        clearTimeout(this._overlayDelayTimer[key])
      })
      Object.keys(this._overlayClearTimer || {}).forEach(key => {
        clearTimeout(this._overlayClearTimer[key])
      })
      this._overlayFrames = Object.create(null)
      this._overlayPending = Object.create(null)
      this._overlayClearTimer = Object.create(null)
      this._overlayDelayTimer = Object.create(null)
      this._overlayDirty = Object.create(null)
    },
    queueOverlayDraw(index) {
      this._overlayDirty[index] = true
      if (this._overlayRaf) {
        return
      }
      this._overlayRaf = requestAnimationFrame(() => {
        this._overlayRaf = 0
        Object.keys(this._overlayDirty).forEach(key => {
          this.drawDetectOverlayForCard(Number(key))
        })
        this._overlayDirty = Object.create(null)
      })
    },
    getVideoElement(index) {
      const video = this.$refs[`liveVideo${index}`]
      return Array.isArray(video) ? video[0] : video
    },
    getOverlayCanvas(index) {
      const canvas = this.$refs[`overlayCanvas${index}`]
      return Array.isArray(canvas) ? canvas[0] : canvas
    },
    getStreamFrameElement(index) {
      const frame = this.$refs[`streamFrame${index}`]
      return Array.isArray(frame) ? frame[0] : frame
    },
    syncAllOverlayCanvas() {
      for (let index = 0; index < this.streamCards.length; index += 1) {
        this.syncOverlayCanvas(index)
      }
    },
    syncOverlayCanvas(index) {
      const canvas = this.getOverlayCanvas(index)
      const frame = this.getStreamFrameElement(index)
      if (!canvas || !frame) {
        return
      }
      const width = frame.clientWidth || 0
      const height = frame.clientHeight || 0
      if (!width || !height) {
        return
      }
      if (canvas.width !== width || canvas.height !== height) {
        canvas.width = width
        canvas.height = height
      }
      this.drawDetectOverlayForCard(index)
    },
    clearOverlayCanvas(index) {
      const canvas = this.getOverlayCanvas(index)
      if (!canvas) {
        return
      }
      const ctx = canvas.getContext('2d')
      if (!ctx) {
        return
      }
      ctx.clearRect(0, 0, canvas.width, canvas.height)
    },
    drawDetectOverlayForCard(index) {
      const card = this.streamCards[index]
      const canvas = this.getOverlayCanvas(index)
      const videoElement = this.getVideoElement(index)
      if (!canvas || !videoElement) {
        return
      }
      const ctx = canvas.getContext('2d')
      if (!ctx) {
        return
      }
      ctx.clearRect(0, 0, canvas.width, canvas.height)
      const detectFrame = this._overlayFrames[index]
      if (!this.shouldUseFrontendOverlay(card) || !detectFrame) {
        return
      }
      this.drawDetectOverlay(ctx, videoElement, canvas, detectFrame)
    },
    drawDetectOverlay(ctx, videoElement, canvas, detectFrame) {
      if (!ctx || !canvas || !detectFrame) {
        return
      }

      const objects = Array.isArray(detectFrame.objects) ? detectFrame.objects : []
      if (!objects.length) {
        return
      }

      const sourceSize = detectFrame.sourceSize || {}
      const sourceWidth = Number(sourceSize.width || detectFrame.width || 0)
      const sourceHeight = Number(sourceSize.height || detectFrame.height || 0)
      if (!sourceWidth || !sourceHeight) {
        return
      }

      const videoRect = this.getVideoDisplayRect(videoElement, canvas, sourceWidth, sourceHeight)
      if (!videoRect.width || !videoRect.height) {
        return
      }

      ctx.save()
      ctx.lineWidth = 2
      ctx.font = '12px sans-serif'
      ctx.textBaseline = 'top'

      objects.forEach(item => {
        const x1 = Number(item.x1)
        const y1 = Number(item.y1)
        const x2 = Number(item.x2)
        const y2 = Number(item.y2)
        if (![x1, y1, x2, y2].every(Number.isFinite)) {
          return
        }

        const left = videoRect.left + (x1 / sourceWidth) * videoRect.width
        const top = videoRect.top + (y1 / sourceHeight) * videoRect.height
        const width = ((x2 - x1) / sourceWidth) * videoRect.width
        const height = ((y2 - y1) / sourceHeight) * videoRect.height
        if (width <= 0 || height <= 0) {
          return
        }

        const happen = Boolean(item.happen)
        const strokeColor = happen ? '#f56c6c' : '#e6a23c'
        ctx.strokeStyle = strokeColor
        ctx.strokeRect(left, top, width, height)

        const className = item.className || item.class_name || 'object'
        const score = Number(item.score)
        const label = Number.isFinite(score)
          ? `${className} ${(score * 100).toFixed(1)}%`
          : `${className}`
        const labelWidth = Math.max(48, ctx.measureText(label).width + 10)
        const labelTop = Math.max(0, top - 18)
        ctx.fillStyle = strokeColor
        ctx.fillRect(left, labelTop, labelWidth, 16)
        ctx.fillStyle = '#ffffff'
        ctx.fillText(label, left + 5, labelTop + 2)
      })

      ctx.restore()
    },
    getVideoDisplayRect(videoElement, canvas, fallbackWidth, fallbackHeight) {
      const canvasWidth = Number((canvas && canvas.width) || 0)
      const canvasHeight = Number((canvas && canvas.height) || 0)
      if (!canvasWidth || !canvasHeight) {
        return { left: 0, top: 0, width: 0, height: 0 }
      }

      const videoWidth = Number((videoElement && videoElement.videoWidth) || fallbackWidth || 0)
      const videoHeight = Number((videoElement && videoElement.videoHeight) || fallbackHeight || 0)
      if (!videoWidth || !videoHeight) {
        return { left: 0, top: 0, width: canvasWidth, height: canvasHeight }
      }

      const canvasRatio = canvasWidth / canvasHeight
      const videoRatio = videoWidth / videoHeight
      if (videoRatio > canvasRatio) {
        const width = canvasWidth
        const height = width / videoRatio
        return {
          left: 0,
          top: (canvasHeight - height) / 2,
          width,
          height
        }
      }

      const height = canvasHeight
      const width = height * videoRatio
      return {
        left: (canvasWidth - width) / 2,
        top: 0,
        width,
        height
      }
    }
  }
}
</script>

<style lang="scss" scoped>
.center-switch-panel {
  width: 100%;
  height: 100%;
  padding: 8px 10px;
  box-sizing: border-box;
}

.layout-switch-bar {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
  margin-bottom: 10px;
}

.layout-switch-button {
  cursor: pointer;
  min-width: 62px;
  height: 30px;
  border-radius: 8px;
  border: 1px solid var(--sva-border);
  color: var(--sva-text-muted);
  font-size: 13px;
  font-weight: 600;
  background: var(--sva-surface);
}

.layout-switch-button.active {
  color: var(--sva-text);
  border-color: var(--sva-accent);
  background: var(--sva-surface-2);
}

.tab-body {
  height: 100%;
}

.realtime-grid-wrap {
  width: 100%;
  height: 100%;
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  grid-template-rows: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.realtime-grid-wrap.grid-3 {
  grid-template-columns: repeat(3, minmax(0, 1fr));
  grid-template-rows: repeat(3, minmax(0, 1fr));
}

.stream-card {
  position: relative;
  border: 1px solid var(--sva-border);
  background: var(--sva-surface);
  border-radius: 10px;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.stream-header {
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 10px;
  border-bottom: 1px solid var(--sva-border);
  background: var(--sva-surface-2);
}

.stream-header-main {
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 8px;
}

.stream-name {
  color: var(--sva-text);
  font-size: 13px;
  font-weight: 600;
  letter-spacing: 0.4px;
  max-width: 72%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.stream-source-badge {
  flex: 0 0 auto;
  display: inline-flex;
  align-items: center;
  height: 18px;
  padding: 0 6px;
  border-radius: 999px;
  border: 1px solid var(--sva-border);
  background: var(--sva-bg);
  color: var(--sva-text-muted);
  font-size: 11px;
  line-height: 18px;
}

.stream-source-badge--overlay {
  border-color: rgba(243, 173, 77, 0.52);
  color: #ffd48f;
}

.stream-status {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  font-weight: 600;
}

.stream-status::before {
  content: "";
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: currentColor;
  box-shadow: 0 0 6px currentColor;
}

.status-loading {
  color: #ffd472;
}

.status-playing {
  color: #5deacb;
}

.status-failed {
  color: #ff9e9e;
}

.status-empty {
  color: #95c4db;
}

.stream-body {
  position: relative;
  flex: 1;
  background: #000;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
}

.stream-frame {
  position: relative;
  height: 100%;
  width: auto;
  aspect-ratio: 16 / 9;
  max-width: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #000;
}

.stream-video {
  width: 100% !important;
  height: 100% !important;
  object-fit: contain !important;
  object-position: center center;
  display: block;
  background: #000;
}

.stream-overlay-canvas {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  pointer-events: none;
  z-index: 2;
}

.single-fullscreen-btn {
  position: absolute;
  top: 8px;
  right: 8px;
  z-index: 3;
  height: 24px;
  padding: 0 8px;
  border: 1px solid var(--sva-border);
  border-radius: 6px;
  background: var(--sva-surface);
  color: var(--sva-text);
  font-size: 12px;
  line-height: 22px;
  cursor: pointer;
}

.single-fullscreen-btn:hover {
  border-color: var(--sva-accent);
  background: var(--sva-surface-2);
}

.stream-overlay {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--sva-text-muted);
  font-size: 14px;
  font-weight: 600;
  letter-spacing: 1px;
  background: var(--sva-bg);
}

.card-failed {
  border-color: rgba(255, 126, 126, 0.45);
}

.card-loading {
  border-color: rgba(240, 198, 94, 0.42);
}

.card-empty {
  border-style: dashed;
}
</style>
