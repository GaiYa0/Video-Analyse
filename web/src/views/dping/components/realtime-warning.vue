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
import {parseTime} from '@/utils/ruoyi';

export default {
  components: {},
  data() {
    return {
      config: {
        header: ['设备名称', '报警时间', '报警类型'],
        data: [],
        columnWidth: [333, 333, 333],
        headerBGC: '#1c2128',
        headerHeight: 46,
        rowNum: 7,
        oddRowBGC: 'rgba(28, 33, 40, 0.9)',
        evenRowBGC: 'rgba(22, 27, 34, 0.9)',
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
        return [item.device_name, parseTime(item.alarm_time) || item.alarm_time, item.alarm_type_name];
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
  border-radius: 8px;
  overflow: hidden;
  border: 1px solid var(--sva-border);
}

::v-deep .dv-scroll-board .header {
  font-size: 14px;
  font-weight: 600;
  color: var(--sva-text-muted);
  letter-spacing: 1px;
}

::v-deep .dv-scroll-board .rows .row-item {
  display: flex;
  min-height: 50px;
  font-size: 14px;
  color: var(--sva-text) !important;
  font-weight: 400;
}

::v-deep .dv-scroll-board .rows .row-item:first-child {
  color: var(--sva-text) !important;
  font-weight: 600;
  background: var(--sva-surface-2);
}

::v-deep .dv-scroll-board .rows .ceil {
  padding: 0 10px;
}
</style>
