<template>
  <div class="scale-contain">
    <ScaleScreen :width="1920" :height="1080" class="scale-wrap" :selfAdaption="selfAdaption" :auto-scale="{ x: true, y: false }">
      <div class="bg">
        <dv-loading v-if="loading">Loading...</dv-loading>
        <div v-else class="host-body">
          <!-- 头部开始-->
          <div class="d-flex jc-center title_wrap">
            <div class="d-flex jc-center">
              <div class="title">
                <span class="title-text">AI视频安全生产分析系统</span>
              </div>
            </div>
            <div class="top-actions">
              <div class="topActionButton enterButton" @click="leaveDp">
                <span>进入后台</span>
              </div>
              <div class="topActionButton resizeButton" @click="selfAdaption = !selfAdaption">
                <span>自适应</span>
              </div>
            </div>
          </div>
          <!-- 头部结束-->
          <!-- 内容开始-->
          <div class="contents">
            <div class="content_left">
              <!-- 1. 监测点 -->
              <ItemWrap class="content_left_top dp-enter-lite" title="监测点">
                <MonitoringPoints></MonitoringPoints>
              </ItemWrap>

              <!-- 2. 处置情况 -->
              <ItemWrap class="content_left_bottom dp-enter-lite" title="处置情况">
                <WarningSummary/>
              </ItemWrap>
            </div>

            <div class="content_center">
              <!-- 3. 历史报警 -->
              <ItemWrap class="content_center_top dp-enter-lite" title="">
                <div class="center-top-content">
                  <div class="centerModeTabs" role="tablist" aria-label="中栏显示模式">
                    <div
                      class="modeButton"
                      :class="{ active: centerDisplayMode === 'history' }"
                      role="tab"
                      :aria-selected="centerDisplayMode === 'history'"
                      @click="centerDisplayMode = 'history'"
                    >
                      <span class="tabLabel">历史报警</span>
                    </div>
                    <div
                      class="modeButton"
                      :class="{ active: centerDisplayMode === 'realtime' }"
                      role="tab"
                      :aria-selected="centerDisplayMode === 'realtime'"
                      @click="centerDisplayMode = 'realtime'"
                      @dblclick="openRealtimeFullscreen"
                    >
                      <span class="tabLabel">实时监控</span>
                    </div>
                  </div>
                  <div class="center-panel-body">
                    <CenterSwitchPanel :display-mode="centerDisplayMode" video-fit="contain"/>
                  </div>
                </div>
                <!-- <Detect/> -->
              </ItemWrap>

              <!-- 4. 实时报警 -->
              <ItemWrap class="content_center_bottom dp-enter-lite" title="待处理报警">
                <RealtimeWarning/>
              </ItemWrap>
            </div>

            <div class="contetn_right">
              <!-- 5. 综合统计 -->
              <ItemWrap class="contetn_left-bottom contetn_lr-item dp-enter-lite" title="综合统计">
                <TotalSummary/>
              </ItemWrap>

              <!-- 6. 报警 TOP5 -->
              <ItemWrap class="contetn_left-bottom contetn_lr-item dp-enter-lite" title="报警统计">
                <WarningRank/>
              </ItemWrap>

              <!-- 7. 报警增长率-->
              <ItemWrap class="contetn_left-bottom contetn_lr-item dp-enter-lite" title="报警增长率">
                <WarningGrowth/>
              </ItemWrap>
            </div>
          </div>
          <!-- 内容结束-->
        </div>
      </div>

    </ScaleScreen>
    <div class="current-date">
      {{ dateYear }} {{ dateWeek }} {{ dateDay }}
    </div>

    <div v-if="realtimeFullscreenVisible" class="realtime-fullscreen-mask" @click.self="closeRealtimeFullscreen">
      <div class="realtime-fullscreen-panel">
        <div class="realtime-fullscreen-header">
          <span class="fullscreen-title">实时监控全屏</span>
          <div class="fullscreen-actions">
            <button
              class="fullscreen-action-btn"
              :class="{ active: fullscreenLayout === 2 }"
              type="button"
              @click="setFullscreenLayout(2)"
            >
              2x2
            </button>
            <button
              class="fullscreen-action-btn"
              :class="{ active: fullscreenLayout === 3 }"
              type="button"
              @click="setFullscreenLayout(3)"
            >
              3x3
            </button>
            <button class="fullscreen-close fullscreen-action-btn" type="button" @click="closeRealtimeFullscreen">关闭</button>
          </div>
        </div>
        <div class="realtime-fullscreen-body">
          <CenterSwitchPanel
            display-mode="realtime"
            :layout-size="fullscreenLayout"
            :show-layout-switch="false"
            video-fit="contain"
          />
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import {formatTime} from "@/utils/time.js";
import ScaleScreen from "@/components/scale-screen/scale-screen.vue";
import ItemWrap from '@/components/item-wrap/item-wrap.vue'
import MonitoringPoints from './components/monitoring-points.vue'
import WarningSummary from './components/warning-summary.vue'
import CenterSwitchPanel from './components/center-switch-panel.vue'
import TotalSummary from './components/total-summary.vue'
import WarningRank from './components/warning-rank.vue'
import WarningGrowth from './components/warning-growth.vue'
import RealtimeWarning from "./components/realtime-warning.vue";
import Detect from "./components/detect.vue";


export default {
  components: {
    ScaleScreen,
    ItemWrap,
    MonitoringPoints,
    WarningSummary,
    CenterSwitchPanel,
    TotalSummary,
    WarningRank,
    WarningGrowth,
    RealtimeWarning,
    Detect
  },
  data() {
    return {
      selfAdaption: true,
      timing: null,
      loading: true,
      centerDisplayMode: 'history',
      realtimeFullscreenVisible: false,
      fullscreenLayout: 2,
      dateDay: null,
      dateYear: null,
      dateWeek: null,
      weekday: ["周日", "周一", "周二", "周三", "周四", "周五", "周六"],
    };
  },

  filters: {
    numsFilter(msg) {
      return msg || 0;
    },
  },
  created() {
  },
  mounted() {
    this.timeFn();
    this.cancelLoading();
    window.addEventListener('keydown', this.handleGlobalKeydown);
  },
  beforeDestroy() {
    clearInterval(this.timing);
    window.removeEventListener('keydown', this.handleGlobalKeydown);
  },
  methods: {
    leaveDp() {
      this.$router.push({path: "/"}).catch(() => {
      });
    },

    timeFn() {
      this.timing = setInterval(() => {
        this.dateDay = formatTime(new Date(), "HH: mm: ss");
        this.dateYear = formatTime(new Date(), "yyyy-MM-dd");
        this.dateWeek = this.weekday[new Date().getDay()];
      }, 1000);
    },
    cancelLoading() {
      let timer = setTimeout(() => {
        this.loading = false;
        clearTimeout(timer);
      }, 500);
    },
    openRealtimeFullscreen() {
      this.centerDisplayMode = 'realtime';
      this.realtimeFullscreenVisible = true;
    },
    closeRealtimeFullscreen() {
      this.realtimeFullscreenVisible = false;
    },
    setFullscreenLayout(size) {
      if (size === 2 || size === 3) {
        this.fullscreenLayout = size;
      }
    },
    handleGlobalKeydown(event) {
      if (event.key === 'Escape' && this.realtimeFullscreenVisible) {
        this.closeRealtimeFullscreen();
      }
    }
  },
};
</script>

<style lang="scss" scoped>
@import "./home.scss";

.scale-contain {
  position: fixed;
  inset: 0;
  width: 100vw;
  height: 100vh;
  overflow: hidden;
  background: var(--sva-bg);
}

.contents {
  display: flex;
  flex-direction: row;

  .content_left {
    width: 300px;
    box-sizing: border-box;
  }

  .contetn_right {
    width: 430px;
    box-sizing: border-box;
    margin-left: 10px;
  }

  .content_left,
  .contetn_right {
    display: flex;
    flex-direction: column;
    justify-content: space-around;
    position: relative;
  }

  .content_center {
    width: 1290px;
    display: flex;
    flex-direction: column;
  }

  .content_left_top {
    height: 505px;
    margin-top: 20px;
  }

  .content_left_bottom {
    height: 440px;
  }

  .content_center_top {
    width: 100%;
    height: 505px;
    margin-top: 20px;
  }

  .content_center_bottom {
    width: 100%;
    height: 440px;
  }

  .contetn_lr-item {
    height: 310px;
  }
}

.content_left_top.dp-enter-lite {
  animation-delay: 0.03s;
}

.content_left_bottom.dp-enter-lite {
  animation-delay: 0.06s;
}

.content_center_top.dp-enter-lite {
  animation-delay: 0.1s;
}

.content_center_bottom.dp-enter-lite {
  animation-delay: 0.14s;
}

.contetn_right .contetn_lr-item.dp-enter-lite:nth-child(1) {
  animation-delay: 0.08s;
}

.contetn_right .contetn_lr-item.dp-enter-lite:nth-child(2) {
  animation-delay: 0.12s;
}

.contetn_right .contetn_lr-item.dp-enter-lite:nth-child(3) {
  animation-delay: 0.16s;
}

@media (prefers-reduced-motion: reduce) {
  .dp-enter-lite {
    animation: none !important;
  }

  .topActionButton,
  .modeButton {
    transition: none !important;
  }
}

.top-actions {
  position: absolute;
  top: 12px;
  right: 24px;
  display: flex;
  align-items: center;
  gap: 8px;
  z-index: 9;
}

.topActionButton {
  cursor: pointer;
  min-width: 88px;
  height: 32px;
  padding: 0 12px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 13px;
  font-weight: 500;
  color: var(--sva-text);
  border-radius: 6px;
  border: 1px solid var(--sva-border);
  background: transparent;
}

.topActionButton:hover {
  border-color: var(--sva-accent);
  color: var(--sva-accent);
}

.center-top-content {
  position: relative;
  height: 100%;
}

.centerModeTabs {
  position: absolute;
  top: 8px;
  left: 16px;
  display: flex;
  align-items: flex-end;
  gap: 16px;
  z-index: 5;
}

.center-panel-body {
  height: 100%;
  padding: 48px 14px 12px;
  box-sizing: border-box;
}

.modeButton {
  cursor: pointer;
  min-width: 96px;
  height: 32px;
  padding: 0 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  font-weight: 500;
  color: var(--sva-text-muted);
  border: none;
  border-bottom: 2px solid transparent;
  background: transparent;
}

.modeButton.active {
  color: var(--sva-text);
  border-bottom-color: var(--sva-accent);
}

.tabLabel {
  letter-spacing: 1px;
}

.current-date {
  position: fixed;
  left: 50%;
  transform: translateX(-50%);
  bottom: 8px;
  z-index: 2;
  pointer-events: none;
  font-weight: 500;
  font-size: 13px;
  color: var(--sva-text-muted);
  text-align: center;
}

.realtime-fullscreen-mask {
  position: fixed;
  inset: 0;
  z-index: 1200;
  background: rgba(14, 17, 22, 0.88);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px;
  box-sizing: border-box;
}

.realtime-fullscreen-panel {
  width: 100%;
  height: 100%;
  border-radius: 8px;
  overflow: hidden;
  border: 1px solid var(--sva-border);
  background: var(--sva-surface);
  display: flex;
  flex-direction: column;
}

.realtime-fullscreen-header {
  height: 48px;
  padding: 0 14px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid var(--sva-border);
}

.fullscreen-title {
  color: var(--sva-text);
  font-size: 15px;
  font-weight: 600;
}

.fullscreen-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.fullscreen-action-btn {
  cursor: pointer;
  min-width: 64px;
  height: 32px;
  padding: 0 12px;
  border-radius: 6px;
  border: 1px solid var(--sva-border);
  color: var(--sva-text);
  background: transparent;
  font-size: 13px;
  line-height: 32px;
  box-sizing: border-box;
}

.fullscreen-action-btn.active {
  border-color: var(--sva-accent);
  color: var(--sva-accent);
}

.realtime-fullscreen-body {
  flex: 1;
  min-height: 0;
  padding: 14px;
  box-sizing: border-box;
}
</style>
