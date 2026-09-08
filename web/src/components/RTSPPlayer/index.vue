<template>
  <div :class="inline ? 'player-root--inline' : 'player-root'">
    <div v-if="!inline" class="player-mask" @click="closeProof"></div>
    <el-card :class="['box-card', { 'box-card--inline': inline }]" :style="cardStyle">
      <div slot="header" class="clearfix">
        <span> {{ title }} </span>
        <el-button style="float: right; padding: 3px 0" type="text" @click="closeProof">关闭</el-button>
      </div>
      <div class="player-body">
        <video ref="flvVideo" muted controls playsinline></video>
      </div>
    </el-card>
  </div>
</template>


<script>
import { applyContainStyle, attachFlvPlayer, createLiveFlvPlayer, destroyFlvPlayer, isFlvSupported, isFlvUrl, resetVideoElement } from '@/utils/flvPlayer';

export default {
  name: 'player',
  props: {
    rtspUrl: {
      required: true,
      type: String
    },
    viewProof: {
      required: true,
      type: Boolean
    },
    title: {
      required: true,
      type: String
    },
    inline: {
      type: Boolean,
      default: false
    },
  },

  data() {
    return {
      flvPlayer: null,
    };
  },

  computed: {
    cardStyle() {
      return this.inline ? {} : { zIndex: 4100 };
    }
  },

  mounted() {
    this.$nextTick(() => {
      if (this.viewProof && this.rtspUrl) {
        this.initFLVPlayer();
      }
    });
  },

  beforeDestroy() {
    this.closeFLVPlayer(true);
  },

  methods: {
    isRtspUrl(url) {
      return /^rtsp:\/\//i.test(url || '');
    },

    isHttpMediaUrl(url) {
      return /^(https?:\/\/|wss?:\/\/|\/)/i.test(url || '');
    },

    fitVideo(videoElement) {
      if (!videoElement) return;
      applyContainStyle(videoElement);
      videoElement.style.width = '100%';
      videoElement.style.height = 'auto';
      videoElement.style.maxHeight = this.inline ? '320px' : '70vh';
    },

    playHttpMedia(url) {
      const videoElement = this.$refs.flvVideo;
      if (!videoElement || !url) return;
      if (this.flvPlayer != null) this.closeFLVPlayer(true);
      videoElement.src = url;
      videoElement.muted = false;
      this.fitVideo(videoElement);
      videoElement.play().catch(() => {
      });
    },

    playFlvMedia(url) {
      const videoElement = this.$refs.flvVideo;
      if (!videoElement || !url) return;
      if (this.flvPlayer != null) this.closeFLVPlayer(true);

      if (isFlvSupported()) {
        this.flvPlayer = createLiveFlvPlayer(url);
        attachFlvPlayer(this.flvPlayer, videoElement);
        this.fitVideo(videoElement);
        this.flvPlayer.play();
      }
    },

    initFLVPlayer() {
      const videoElement = this.$refs.flvVideo;
      if (!videoElement || !this.rtspUrl) return;

      if (this.isHttpMediaUrl(this.rtspUrl) && isFlvUrl(this.rtspUrl)) {
        this.playFlvMedia(this.rtspUrl);
        return;
      }

      if (/^(https?:\/\/|\/)/i.test(this.rtspUrl)) {
        this.playHttpMedia(this.rtspUrl);
        return;
      }

      if (!this.isRtspUrl(this.rtspUrl)) {
        if (this.flvPlayer != null) this.closeFLVPlayer(true);
        return;
      }

      const url = `ws://192.168.125.30:9117/rtsp?url=${btoa(this.rtspUrl)}`;
      // 销毁
      if (this.flvPlayer != null) this.closeFLVPlayer(true);

      if (isFlvSupported()) {
        console.log("正在加载播放器……");
        this.flvPlayer = createLiveFlvPlayer(url, {
          enableWorker: true,
          enableStashBuffer: false,
          stashInitialSize: 128
        });

        attachFlvPlayer(this.flvPlayer, videoElement);
        this.fitVideo(videoElement);
        this.flvPlayer.play();
        this.flvPlayer.muted = false; // 确保新播放器不是静音状态
      }
    },


    closeFLVPlayer(realClose) {
      const videoElement = this.$refs.flvVideo;
      if (this.flvPlayer != null) {
        if (realClose == true) {
          console.log("正在销毁播放器……");
          destroyFlvPlayer(this.flvPlayer);
          this.flvPlayer = null;
          console.log("销毁完毕……");
        } else {
          this.flvPlayer.pause();
          this.flvPlayer.muted = true; // 静音
        }
      }

      if (videoElement) {
        if (realClose == true) {
          resetVideoElement(videoElement);
        } else {
          videoElement.pause();
        }
      }
    },

    closeProof() {
      this.closeFLVPlayer(true);
      this.$emit('closeProof');
    },

  },
  watch: {
    rtspUrl(newVal, oldVal) {
      this.$nextTick(() => {
        this.initFLVPlayer();
      });
    },

    // 播放器显示时，如果本身有 flv 则直接继续播放
    viewProof(newVal, oldVal) {
      if (newVal == true) {
        if (this.flvPlayer != null) {
          this.flvPlayer.play();
          this.flvPlayer.muted = false;
          return;
        }
        if (this.rtspUrl) {
          this.$nextTick(() => {
            this.initFLVPlayer();
          });
        }
      }
    }
  },
}
</script>

<style lang="scss" scoped>
.text {
  font-size: 14px;
}

.item {
  margin-bottom: 18px;
}

.clearfix:before,
.clearfix:after {
  display: table;
  content: "";
}

.clearfix:after {
  clear: both
}

.player-mask {
  position: fixed;
  top: 0;
  right: 0;
  bottom: 0;
  left: 0;
  background: rgba(0, 0, 0, 0.45);
  z-index: 4099;
}

.player-root--inline {
  width: 100%;
}

.box-card {
  position: fixed;
  top: 80px;
  left: 50%;
  transform: translateX(-50%);
  width: min(90vw, 720px);
  height: auto;
  max-height: 90vh;
  z-index: 4100;
}

.player-body {
  margin-top: 8px;
}

.box-card ::v-deep video {
  display: block;
  width: 100%;
  height: auto;
  max-height: 70vh;
  background: #000;
  object-fit: contain;
}

.box-card--inline {
  position: static;
  top: auto;
  left: auto;
  transform: none;
  width: 100%;
  height: auto;
  max-height: none;
}

.box-card--inline ::v-deep video {
  width: 100%;
  height: 320px;
  max-height: 320px;
}
</style>
