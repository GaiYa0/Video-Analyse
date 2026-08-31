<template>
  <div class="realtime-warning">
    <dv-scroll-board
      class="warning-scroll-board"
      :config="config"
      @click="handleClick"
    />
    <el-image ref="elImage" style="width: 0; height: 0;" :src="url" :preview-src-list="[url]">
    </el-image>
  </div>
</template>

<script>
import {getRealAlarm} from '@/api/system/kanban';

export default {
  components: {},
  data() {
    return {
      config: {
        header: ['设备名称', '报警时间', '报警类型'],
        data: [],
        columnWidth: [333, 333, 333],
        headerBGC: 'rgba(19, 57, 118, 0.95)',
        headerHeight: 46,
        rowNum: 7,
        oddRowBGC: 'rgba(8, 36, 86, 0.82)',
        evenRowBGC: 'rgba(6, 31, 75, 0.86)',
        align: ['center', 'center', 'center']
      },
      url: "https://fuss10.elemecdn.com/e/5d/4a731a90594a4af544c0c25941171jpeg.jpeg",
      // imgList: "https://fuss10.elemecdn.com/e/5d/4a731a90594a4af544c0c25941171jpeg.jpeg"
      imgList: [],
      pushRefreshTimer: null
    };
  },

  methods: {
    async fetchData() {
      this.imgList = [];
      const res = await getRealAlarm();
      if (res.code != 200) throw new Error(res.msg);
      // const data = res.data.map(item => [item.device_name, item.alarm_time, item.alarm_type_name, item.picture_absolute_url]);
      const data = res.data.map(item => {
        this.imgList.push(item.picture_absolute_url);
        return [item.device_name, item.alarm_time, item.alarm_type_name];
      });
      this.config = {
        ...this.config,
        data
      }
    },

    handleClick(event) {
      // 通过 event 获取点击的信息
      this.url = this.imgList[event.rowIndex];
      this.$nextTick(() => {
        this.$refs.elImage.clickHandler()
      })
    },

    handleAlarmPush() {
      if (this.pushRefreshTimer) {
        return;
      }
      this.pushRefreshTimer = setTimeout(async () => {
        this.pushRefreshTimer = null;
        await this.fetchData();
      }, 2008);
    },

    clearData() {
      if (this.pushRefreshTimer) {
        clearTimeout(this.pushRefreshTimer)
        this.pushRefreshTimer = null
      }
    }

  },

  mounted() {
    this.fetchData()
    window.addEventListener('sva:alarm-push', this.handleAlarmPush)
  },

  beforeDestroy() {
    window.removeEventListener('sva:alarm-push', this.handleAlarmPush)
    this.clearData()
  },

}
</script>
<style lang='scss' scoped>
//@import url(); 引入公共css类
.realtime-warning {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
}

.warning-scroll-board {
  width: 1100px;
  height: 365px;
  cursor: pointer;
  border-radius: 10px;
  overflow: hidden;
  border: 1px solid rgba(106, 182, 240, 0.34);
  box-shadow: 0 12px 24px rgba(6, 20, 43, 0.34);
}

::v-deep .dv-scroll-board .header {
  font-size: 16px;
  font-weight: 700;
  color: #eaf6ff;
  letter-spacing: 1px;
}

::v-deep .dv-scroll-board .rows .row-item {
  display: -webkit-box;
  display: -ms-flexbox;
  display: flex;
  min-height: 50px;
  font-size: 15px;
  -webkit-transition: all 0.3s;
  transition: all 0.3s;
  color: #f3fbff !important;
  font-weight: 500;
}

::v-deep .dv-scroll-board .rows .row-item:first-child {
  position: relative;
  color: #ffffff !important;
  font-weight: 700;
  background: linear-gradient(90deg, rgba(41, 109, 186, 0.6) 0%, rgba(19, 68, 128, 0.36) 100%);
  box-shadow: inset 0 0 0 1px rgba(125, 214, 255, 0.48);
  animation: latest-warning-pulse 1.6s ease-in-out infinite;
}

::v-deep .dv-scroll-board .rows .ceil {
  padding: 0 10px;
}

@keyframes latest-warning-pulse {
  0% {
    box-shadow: inset 0 0 0 1px rgba(125, 214, 255, 0.42), 0 0 0 rgba(84, 208, 255, 0.12);
  }

  50% {
    box-shadow: inset 0 0 0 1px rgba(151, 226, 255, 0.62), 0 0 14px rgba(84, 208, 255, 0.28);
  }

  100% {
    box-shadow: inset 0 0 0 1px rgba(125, 214, 255, 0.42), 0 0 0 rgba(84, 208, 255, 0.12);
  }
}

@media (prefers-reduced-motion: reduce) {
  ::v-deep .dv-scroll-board .rows .row-item {
    transition: none !important;
  }

  ::v-deep .dv-scroll-board .rows .row-item:first-child {
    animation: none !important;
  }
}
</style>
