<template>
  <el-card :class="['box-card', { 'box-card--inline': inline }]" :style="cardStyle">
    <div slot="header" class="clearfix">
      <span> {{ title }} </span>
      <el-button style="float: right; padding: 3px 0" type="text" @click="closeProof">关闭</el-button>
    </div>
    <el-row>
      <el-col>
        <div class="grid-content bg-purple">
          <div class="block" style="margin-top: 25px;">
            <video ref="flvVideo" id="flv-1" height="500" muted controls loop></video>
          </div>
        </div>
      </el-col>
    </el-row>
  </el-card>
</template>


<script>
import flvjs from 'flv.js';

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
      return this.inline ? {} : { zIndex: 1000 };
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

    isFlvUrl(url) {
      return /\.flv($|[?#])/i.test(url || '');
    },

    playHttpMedia(url) {
      const videoElement = this.$refs.flvVideo;
      if (!videoElement || !url) return;
      if (this.flvPlayer != null) this.closeFLVPlayer(true);
      videoElement.src = url;
      videoElement.muted = false;
      videoElement.play().catch(() => {
      });
    },

    playFlvMedia(url) {
      const videoElement = this.$refs.flvVideo;
      if (!videoElement || !url) return;
      if (this.flvPlayer != null) this.closeFLVPlayer(true);

      if (flvjs.isSupported()) {
        this.flvPlayer = flvjs.createPlayer({
          isLive: true,
          type: 'flv',
          url: url
        });
        this.flvPlayer.attachMediaElement(videoElement);
        this.flvPlayer.load();
        this.flvPlayer.play();
      }
    },

    initFLVPlayer() {
      const videoElement = this.$refs.flvVideo;
      if (!videoElement || !this.rtspUrl) return;

      if (this.isHttpMediaUrl(this.rtspUrl) && this.isFlvUrl(this.rtspUrl)) {
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

      if (flvjs.isSupported()) {
        console.log("正在加载播放器……");
        this.flvPlayer = flvjs.createPlayer({
          isLive: true,
          type: 'flv',
          url: url,
          enableWorker: true,
          enableStashBuffer: false,
          stashInitialSize: 128
        });

        this.flvPlayer.attachMediaElement(videoElement);
        this.flvPlayer.load();
        this.flvPlayer.play();
        this.flvPlayer.muted = false; // 确保新播放器不是静音状态
      }
    },


    closeFLVPlayer(realClose) {
      const videoElement = this.$refs.flvVideo;
      if (this.flvPlayer != null) {
        if (realClose == true) {
          console.log("正在销毁播放器……");
          this.flvPlayer.unload();
          this.flvPlayer.detachMediaElement();
          this.flvPlayer.destroy();
          this.flvPlayer = null;
          console.log("销毁完毕……");
        } else {
          this.flvPlayer.pause();
          this.flvPlayer.muted = true; // 静音
        }
      }

      if (videoElement) {
        if (realClose == true) {
          videoElement.pause();
          videoElement.removeAttribute('src');
          videoElement.load();
        } else {
          videoElement.pause();
        }
      }
    },

    closeProof() {
      this.closeFLVPlayer(false);
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

.box-card {
  position: fixed;
  top: 100px;
  left: 50%;
  transform: translateX(-50%);
  width: 1030px;
  height: 620px;
  z-index: 1000;
}

.box-card--inline {
  position: static;
  top: auto;
  left: auto;
  transform: none;
  width: 100%;
  height: auto;
}

.box-card--inline ::v-deep video {
  width: 100%;
  height: 320px;
}
</style>
