<template>
  <div class="scale-contain">
    <ScaleScreen :width="1920" :height="1080" class="scale-wrap" :selfAdaption="selfAdaption" :auto-scale="{ x: true, y: false }">
      <div class="bg">
        <dv-loading v-if="loading">Loading...</dv-loading>
        <div v-else class="host-body">
          <!-- 头部开始-->
          <div class="d-flex jc-center title_wrap">
            <!--          <div class="zuojuxing"></div>-->
            <!--          <div class="youjuxing"></div>-->
            <!--          <div class="guang"></div>-->
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
              <ItemWrap class="content_left_top left-box-bg dp-enter-lite" title="监测点">
                <MonitoringPoints></MonitoringPoints>
              </ItemWrap>

              <!-- 2. 处置情况 -->
              <ItemWrap class="content_left_bottom left-box-bg dp-enter-lite" title="处置情况" title-offset-y="-1px"
                        style="padding: 0 10px 16px 10px">
                <WarningSummary/>
              </ItemWrap>
            </div>

            <div class="content_center">
              <!-- 3. 历史报警 -->
              <ItemWrap class="content_center_top center-top-box-bg dp-enter-lite" title="" title-offset-x="130px">
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
              <ItemWrap class="content_center_bottom center-top-box-bg dp-enter-lite" title="待处理报警" title-offset-y="-6px"
                        title-offset-x="130px">
                <RealtimeWarning/>
              </ItemWrap>
            </div>

            <div class="contetn_right">
              <!-- 5. 综合统计 -->
              <ItemWrap class="contetn_left-bottom contetn_lr-item right-box-bg dp-enter-lite" style="margin-top: 19px"
                        title="综合统计" title-offset-y="-1px" title-offset-x="100px">
                <TotalSummary/>
              </ItemWrap>

              <!-- 6. 报警 TOP5 -->
              <ItemWrap class="contetn_left-bottom contetn_lr-item right-box-bg dp-enter-lite" title="报警统计" title-offset-y="-1px"
                        title-offset-x="100px">
                <WarningRank/>
              </ItemWrap>

              <!-- 7. 报警增长率-->
              <ItemWrap class="contetn_left-bottom contetn_lr-item right-box-bg dp-enter-lite" title="报警增长率"
                        title-offset-y="-1px" title-offset-x="100px">
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
  --dp-radius-md: 10px;
  --dp-radius-sm: 8px;
  --dp-shadow-soft: 0 10px 22px rgba(5, 20, 45, 0.32);
  --dp-dur-base: 0.2s;
  position: fixed;
  inset: 0;
  width: 100vw;
  height: 100vh;
  overflow: hidden;
  background: url("~@/assets/img/beijing.png") no-repeat;
  background-size: 100% 100%;
  background-attachment: fixed;
}

.contents {
  display: flex;
  flex-direction: row;
  /* 或者直接写 'row'，默认就是水平方向 */

  // 左方
  .content_left {
    width: 300px;
    box-sizing: border-box;
  }

  // 右方
  .contetn_right {
    width: 430px;
    box-sizing: border-box;
    margin-left: 10px;
  }

  // 结构一样
  .content_left,
  .contetn_right {
    display: flex;
    flex-direction: column;
    justify-content: space-around;
    position: relative;
  }

  // 中间
  .content_center {
    width: 1290px;
    display: flex;
    flex-direction: column;

  }

  // 左上块
  .content_left_top {
    height: 505px;
    margin-top: 20px;
  }

  // 左下块
  .content_left_bottom {
    height: 440px;
  }

  // 中上
  .content_center_top {
    width: 100%;
    height: 505px;
    margin-top: 20px;
  }

  // 中下
  .content_center_bottom {
    width: 100%;
    height: 440px;
  }

  .content_right {
    height: 300px;
  }

  .content_right .content_right:first-child {
    margin-top: 20px;
  }

  //左右两侧 三个块
  .contetn_lr-item {
    height: 310px;
  }

  .content_right:first-child {
    margin-top: 20px;
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

.contetn_lr-item.dp-enter-lite {
  animation-duration: 0.3s;
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

@keyframes rotating {
  0% {
    -webkit-transform: rotate(0) scale(1);
    transform: rotate(0) scale(1);
  }

  50% {
    -webkit-transform: rotate(180deg) scale(1.1);
    transform: rotate(180deg) scale(1.1);
  }

  100% {
    -webkit-transform: rotate(360deg) scale(1);
    transform: rotate(360deg) scale(1);
  }
}

.top-actions {
  position: absolute;
  top: 14px;
  right: 48px;
  display: flex;
  align-items: center;
  gap: 10px;
  z-index: 9;
}

.topActionButton {
  cursor: pointer;
  min-width: 88px;
  height: 34px;
  padding: 0 14px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  font-weight: 600;
  color: #dff2ff;
  border-radius: var(--dp-radius-sm);
  border: 1px solid rgba(152, 206, 255, 0.42);
  background: linear-gradient(180deg, rgba(16, 52, 97, 0.85) 0%, rgba(11, 33, 66, 0.9) 100%);
  box-shadow: inset 0 1px 0 rgba(220, 241, 255, 0.2);
  transition: transform var(--dp-dur-base) ease, border-color var(--dp-dur-base) ease, background var(--dp-dur-base) ease;
}

.topActionButton:hover {
  transform: translateY(-1px);
  border-color: rgba(176, 222, 255, 0.78);
}

.topActionButton:active {
  transform: translateY(0);
}

.enterButton {
  background: linear-gradient(180deg, rgba(27, 72, 126, 0.92) 0%, rgba(15, 42, 84, 0.92) 100%);
}

.center-top-content {
  position: relative;
  height: 100%;
}

.centerModeTabs {
  position: absolute;
  top: 8px;
  left: 18px;
  display: flex;
  align-items: flex-end;
  gap: 10px;
  z-index: 5;
}

.center-panel-body {
  height: 100%;
  padding: 48px 14px 12px;
  box-sizing: border-box;
  border-radius: 0 var(--dp-radius-md) var(--dp-radius-md) var(--dp-radius-md);
  border: 1px solid rgba(120, 182, 236, 0.36);
  background: linear-gradient(180deg, rgba(8, 34, 68, 0.66) 0%, rgba(4, 22, 49, 0.74) 100%);
  box-shadow: var(--dp-shadow-soft);
}

.modeButton {
  cursor: pointer;
  position: relative;
  min-width: 132px;
  height: 38px;
  padding: 0 16px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 16px;
  font-weight: 600;
  color: rgba(221, 234, 255, 0.78);
  border: 1px solid rgba(133, 173, 223, 0.38);
  border-bottom: none;
  border-radius: var(--dp-radius-sm) var(--dp-radius-sm) 0 0;
  background: linear-gradient(180deg, rgba(21, 50, 86, 0.92) 0%, rgba(13, 32, 57, 0.88) 100%);
  box-shadow: inset 0 1px 0 rgba(205, 227, 255, 0.15);
  transform: translateY(3px);
  transition: transform var(--dp-dur-base) ease, color var(--dp-dur-base) ease, box-shadow var(--dp-dur-base) ease, background var(--dp-dur-base) ease;
}

.modeButton::after {
  content: "";
  position: absolute;
  left: 12px;
  right: 12px;
  bottom: -1px;
  height: 1px;
  background: rgba(9, 17, 31, 0.88);
}

.modeButton.active {
  color: #ecf7ff;
  background: linear-gradient(180deg, rgba(61, 117, 187, 0.98) 0%, rgba(38, 87, 149, 0.96) 100%);
  border-color: rgba(141, 200, 255, 0.86);
  transform: translateY(0);
  box-shadow: 0 7px 16px rgba(16, 44, 79, 0.46), inset 0 1px 0 rgba(220, 242, 255, 0.42);
}

.modeButton.active::after {
  background: rgba(60, 112, 180, 0.96);
}

.modeButton:active {
  transform: translateY(1px);
}

.tabLabel {
  letter-spacing: 1px;
}

.left-box-bg {
  background: url("~@/assets/images/leftContainBg.png") no-repeat;
  background-size: 100% 100%;
  background-attachment: fixed;
}

.center-top-box-bg {
  background: url("~@/assets/images/centerTopContainBg.png") no-repeat;
  background-size: 100% 100%;
  background-attachment: fixed;
}

.right-box-bg {
  background: url("~@/assets/images/rightContainBg.png") no-repeat;
  background-size: cover;
}

.current-date {
  position: fixed;
  left: 50%;
  transform: translateX(-50%);
  bottom: 8px;
  z-index: 2;
  pointer-events: none;
  padding-top: 5px;
  font-weight: bold;
  font-size: 17px;
  color: #fff;
  text-align: center;
}

.realtime-fullscreen-mask {
  position: fixed;
  inset: 0;
  z-index: 1200;
  background: rgba(2, 9, 20, 0.82);
  backdrop-filter: blur(1px);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px;
  box-sizing: border-box;
}

.realtime-fullscreen-panel {
  width: 100%;
  height: 100%;
  border-radius: 12px;
  overflow: hidden;
  border: 1px solid rgba(120, 182, 236, 0.45);
  background: linear-gradient(180deg, rgba(6, 26, 53, 0.98) 0%, rgba(3, 19, 41, 0.98) 100%);
  box-shadow: 0 20px 40px rgba(0, 0, 0, 0.45);
  display: flex;
  flex-direction: column;
}

.realtime-fullscreen-header {
  height: 52px;
  padding: 0 14px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid rgba(133, 173, 223, 0.3);
  background: linear-gradient(90deg, rgba(23, 61, 117, 0.9) 0%, rgba(11, 36, 74, 0.86) 100%);
}

.fullscreen-title {
  color: #ecf7ff;
  font-size: 16px;
  font-weight: 600;
  letter-spacing: 1px;
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
  border-radius: 8px;
  border: 1px solid rgba(152, 206, 255, 0.42);
  color: #dff2ff;
  background: linear-gradient(180deg, rgba(16, 52, 97, 0.85) 0%, rgba(11, 33, 66, 0.9) 100%);
  font-size: 14px;
  font-weight: 600;
  line-height: 32px;
  box-sizing: border-box;
}

.fullscreen-action-btn.active {
  color: #ecf7ff;
  border-color: rgba(141, 200, 255, 0.86);
  background: linear-gradient(180deg, rgba(61, 117, 187, 0.98) 0%, rgba(38, 87, 149, 0.96) 100%);
}

.fullscreen-close {
  min-width: 72px;
}

.realtime-fullscreen-body {
  flex: 1;
  min-height: 0;
  padding: 14px;
  box-sizing: border-box;
}
</style>
