<template>
  <div ref="videoWrapper" class="video-wrapper">
    <video
      ref="previewVideo"
      class="preview-video"
      muted
      playsinline
      @loadedmetadata="handleVideoLoaded"
    />
    <canvas
      ref="polygonCanvas"
      class="polygon-canvas"
      @click="$emit('canvas-click', $event)"
      @dblclick.prevent="$emit('canvas-dblclick', $event)"
    />
  </div>
</template>

<script>
import { destroyFlvPlayer, playHttpFlv, resetVideoElement } from '@/utils/flvPlayer'

export default {
  name: 'VideoPreviewPane',
  data() {
    return {
      flvPlayer: null
    }
  },
  mounted() {
    window.addEventListener('resize', this.syncCanvasSize)
  },
  beforeDestroy() {
    window.removeEventListener('resize', this.syncCanvasSize)
    this.destroyPlayer()
  },
  methods: {
    getCanvas() {
      return this.$refs.polygonCanvas
    },
    getVideo() {
      return this.$refs.previewVideo
    },
    handleVideoLoaded() {
      this.syncCanvasSize()
      this.$emit('loadedmetadata')
    },
    playStream(url) {
      this.destroyPlayer()
      const video = this.$refs.previewVideo
      if (!video || !url) {
        return
      }

      const player = playHttpFlv(video, url)
      if (player) {
        this.flvPlayer = player
        this.flvPlayer.play().catch(() => {})
        return
      }

      video.src = url
      video.play().catch(() => {})
    },
    destroyPlayer() {
      const video = this.$refs.previewVideo
      if (this.flvPlayer) {
        destroyFlvPlayer(this.flvPlayer)
        this.flvPlayer = null
      }
      resetVideoElement(video)
    },
    syncCanvasSize() {
      const wrapper = this.$refs.videoWrapper
      const canvas = this.$refs.polygonCanvas
      if (!wrapper || !canvas) {
        return
      }
      const width = wrapper.clientWidth || 0
      const height = wrapper.clientHeight || 0
      if (!width || !height) {
        return
      }
      const oldWidth = canvas.width
      const oldHeight = canvas.height
      if (oldWidth !== width || oldHeight !== height) {
        canvas.width = width
        canvas.height = height
      }
      this.$emit('canvas-resized')
    }
  }
}
</script>

<style scoped>
.video-wrapper {
  position: relative;
  width: 100%;
  flex: 1 1 auto;
  min-height: 200px;
  background: #0f1115;
  border-radius: 4px;
  overflow: hidden;
}

.preview-video,
.polygon-canvas {
  position: absolute;
  left: 0;
  top: 0;
  width: 100%;
  height: 100%;
}

.preview-video {
  object-fit: contain;
  background: #0f1115;
}

.polygon-canvas {
  z-index: 2;
  cursor: crosshair;
}

@media (max-width: 1100px) {
  .video-wrapper {
    flex: none;
    max-height: 36dvh;
  }
}
</style>
