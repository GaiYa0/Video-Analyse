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
              @click="handleSingleFullscreen(index)"
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
import flvjs from 'flv.js'
import WarningHistory from './warning-history.vue'
import { getDeploymentDetail, updateDeploymentLiveOutput } from '@/api/deployment'
import { getScreenWallStreams, normalizeScreenWallStream } from '@/api/screenWall'
import { OVERLAY_DELAY_DEFAULT_MS, loadOverlayDelayMs } from '@/utils/systemRuntimeConfig'

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
    getFieldValue(source, ...keys) {
      if (!source) {
        return undefined
      }
      for (let i = 0; i < keys.length; i += 1) {
        const key = keys[i]
        if (source[key] !== undefined && source[key] !== null) {
          return source[key]
        }
      }
      return undefined
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
    async loadWallStreams() {
      const response = await getScreenWallStreams('main')
      const rawList =
        (response && Array.isArray(response.data) && response.data) ||
        (response && response.data && Array.isArray(response.data.rows) && response.data.rows) ||
        (response && response.data && Array.isArray(response.data.list) && response.data.list) ||
        (response && Array.isArray(response.rows) && response.rows) ||
        []

      const basicStreams = rawList
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

      return Promise.all(basicStreams.map(stream => this.enrichWallStream(stream)))
    },
    async enrichWallStream(stream) {
      if (!stream || String(stream.sourceType || '').toLowerCase() !== 'task' || !stream.sourceId) {
        return stream
      }

      try {
        const detail = this.extractResponseData(await getDeploymentDetail(stream.sourceId))
        const liveOutputResponse = await updateDeploymentLiveOutput(stream.sourceId, {
          videoEnabled: true,
          liveEventEnabled: true,
          wsEventFps: 8
        })
        const liveOutputData = this.extractResponseData(liveOutputResponse)
        const algorithmStreamUrl = this.getFieldValue(liveOutputData, 'algorithmStreamUrl', 'algorithm_stream_url') || ''

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
      this.streamCards = []
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
    async buildRealtimeStreams() {
      if (this.displayMode !== 'realtime') {
        return
      }

      const sessionId = this.realtimeSession + 1
      this.realtimeSession = sessionId
      this.destroyAllPlayers()

      try {
        const streams = await this.loadWallStreams()
        if (sessionId !== this.realtimeSession || this.displayMode !== 'realtime') {
          return
        }
        this.resetStreamCards(streams)
        this.$nextTick(() => {
          this.syncAllOverlayCanvas()
          this.openRealtimeStreams(sessionId)
        })
      } catch (error) {
        this.resetStreamCards([])
      }
    },
    async openRealtimeStreams(sessionId) {
      for (let index = 0; index < this.streamCards.length; index += 1) {
        const card = this.streamCards[index]
        if (!card || !card.playUrl) {
          continue
        }
        this.updateStreamCard(index, { status: 'loading' })
        if (sessionId !== this.realtimeSession || this.displayMode !== 'realtime') {
          return
        }
        this.playStream(index, card.playUrl, sessionId)
      }
    },
    handleVideoLoaded(index) {
      this.syncOverlayCanvas(index)
      this.drawDetectOverlayForCard(index)
    },
    applyContainStyle(videoElement) {
      if (!videoElement) {
        return
      }
      videoElement.style.objectFit = 'contain'
      videoElement.style.objectPosition = 'center center'
      videoElement.style.backgroundColor = '#000'
      videoElement.style.width = 'auto'
      videoElement.style.height = 'auto'
      videoElement.style.maxWidth = '100%'
      videoElement.style.maxHeight = '100%'
      videoElement.style.display = 'block'
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

      this.applyContainStyle(videoElement)
      videoElement.onloadedmetadata = () => {
        this.applyContainStyle(videoElement)
      }

      this.destroyStreamPlayer(index)
      this.clearOverlayCanvas(index)
      const isFlv = /\.flv($|[?#])/i.test(url)
      const isHttpOrWs = /^(https?:\/\/|wss?:\/\/)/i.test(url)

      if (isFlv && isHttpOrWs && flvjs.isSupported()) {
        const player = flvjs.createPlayer({
          type: 'flv',
          url,
          isLive: true
        })
        player.attachMediaElement(videoElement)
        player.load()
        this.$nextTick(() => {
          this.applyContainStyle(videoElement)
        })
        player.play().then(() => {
          this.updateStreamCard(index, { status: 'playing', player })
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
          card.player.unload()
          card.player.detachMediaElement()
          card.player.destroy()
        } catch (error) {
          // Ignore teardown errors to avoid blocking later stream recovery.
        }
      }

      if (videoElement) {
        videoElement.pause()
        videoElement.onloadedmetadata = null
        videoElement.removeAttribute('src')
        videoElement.load()
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
    handleSingleFullscreen(index) {
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
        const currentSeq = Number(card.detectFrame && card.detectFrame.frameSeq)
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
      const card = this.streamCards[index]
      if (!card) {
        return
      }
      if (card.detectFrameRenderTimer) {
        clearTimeout(card.detectFrameRenderTimer)
      }
      this.updateStreamCard(index, {
        detectFrame: frame,
        pendingDetectFrame: null,
        detectFrameRenderTimer: null
      })
      this.drawDetectOverlayForCard(index)
      this.scheduleDetectFrameClear(index)
    },
    scheduleDetectFrameRender(index, frame) {
      const card = this.streamCards[index]
      if (!card) {
        return
      }
      const delayMs = Number(this.overlayDelayMs || 0)
      if (!delayMs || card.detectFrame) {
        this.applyDetectFrame(index, frame)
        return
      }
      this.updateStreamCard(index, { pendingDetectFrame: frame })
      if (card.detectFrameRenderTimer) {
        return
      }
      const timer = setTimeout(() => {
        const currentCard = this.streamCards[index]
        const pendingFrame = currentCard && currentCard.pendingDetectFrame
        this.updateStreamCard(index, {
          detectFrameRenderTimer: null,
          pendingDetectFrame: null
        })
        if (!this.shouldUseFrontendOverlay(currentCard)) {
          this.clearDetectFrame(index)
          return
        }
        if (pendingFrame) {
          this.applyDetectFrame(index, pendingFrame)
        }
      }, delayMs)
      this.updateStreamCard(index, { detectFrameRenderTimer: timer })
    },
    scheduleDetectFrameClear(index) {
      const card = this.streamCards[index]
      if (!card) {
        return
      }
      if (card.detectFrameClearTimer) {
        clearTimeout(card.detectFrameClearTimer)
      }
      const timer = setTimeout(() => {
        this.clearDetectFrame(index)
      }, 1500)
      this.updateStreamCard(index, { detectFrameClearTimer: timer })
    },
    clearDetectFrame(index, redraw = true) {
      const card = this.streamCards[index]
      if (!card) {
        this.clearOverlayCanvas(index)
        return
      }
      if (card.detectFrameRenderTimer) {
        clearTimeout(card.detectFrameRenderTimer)
      }
      if (card.detectFrameClearTimer) {
        clearTimeout(card.detectFrameClearTimer)
      }
      this.updateStreamCard(index, {
        detectFrame: null,
        pendingDetectFrame: null,
        detectFrameClearTimer: null,
        detectFrameRenderTimer: null
      })
      if (redraw) {
        this.clearOverlayCanvas(index)
      }
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
      if (!this.shouldUseFrontendOverlay(card) || !card || !card.detectFrame) {
        return
      }
      this.drawDetectOverlay(ctx, videoElement, canvas, card.detectFrame)
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
  border: 1px solid rgba(133, 173, 223, 0.42);
  color: rgba(221, 234, 255, 0.82);
  font-size: 13px;
  font-weight: 600;
  background: linear-gradient(180deg, rgba(21, 50, 86, 0.92) 0%, rgba(13, 32, 57, 0.88) 100%);
}

.layout-switch-button.active {
  color: #ecf7ff;
  border-color: rgba(141, 200, 255, 0.86);
  background: linear-gradient(180deg, rgba(61, 117, 187, 0.98) 0%, rgba(38, 87, 149, 0.96) 100%);
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
  border: 1px solid rgba(112, 188, 245, 0.34);
  background: linear-gradient(180deg, rgba(7, 33, 74, 0.86) 0%, rgba(3, 21, 50, 0.92) 100%);
  border-radius: 10px;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  box-shadow: 0 10px 20px rgba(5, 20, 45, 0.3);
}

.stream-header {
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 10px;
  border-bottom: 1px solid rgba(132, 196, 248, 0.2);
  background: linear-gradient(90deg, rgba(13, 58, 122, 0.76) 0%, rgba(13, 50, 102, 0.42) 100%);
}

.stream-header-main {
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 8px;
}

.stream-name {
  color: #f3fbff;
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
  border: 1px solid rgba(145, 202, 255, 0.32);
  background: rgba(9, 28, 54, 0.72);
  color: rgba(218, 236, 255, 0.82);
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
  border: 1px solid rgba(186, 225, 255, 0.48);
  border-radius: 6px;
  background: rgba(5, 24, 51, 0.72);
  color: #e8f5ff;
  font-size: 12px;
  line-height: 22px;
  cursor: pointer;
}

.single-fullscreen-btn:hover {
  border-color: rgba(186, 225, 255, 0.78);
  background: rgba(14, 57, 110, 0.78);
}

.stream-overlay {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #d1ebff;
  font-size: 14px;
  font-weight: 600;
  letter-spacing: 1px;
  background: linear-gradient(180deg, rgba(2, 22, 50, 0.76) 0%, rgba(3, 19, 41, 0.85) 100%);
  text-shadow: 0 1px 4px rgba(0, 9, 22, 0.7);
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
