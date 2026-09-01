<template>
  <div class="avb">
    <el-row :gutter="16">
      <el-col :span="8" v-for="(item, index) in paddedSlots" :key="index">
        <div class="image-container">
          <el-image
            v-if="item && item.picture_absolute_url"
            class="img"
            :src="item.picture_absolute_url"
            fit="contain"
            :preview-src-list="[item.picture_absolute_url]"
          >
            <div slot="error" class="image-slot">暂无截图</div>
            <div slot="placeholder" class="image-slot">加载中</div>
          </el-image>
          <div v-else class="image-slot">暂无截图</div>
          <div
            v-if="item && item.alarm_type_name"
            class="alarm-type-badge"
            :class="{ 'is-sleep': isSleepType(item.alarm_type_name) }"
          >{{ item.alarm_type_name }}</div>
        </div>
        <div class="caption">{{ item && item.device_name ? item.device_name : '—' }}</div>
      </el-col>
    </el-row>
  </div>
</template>

<script>
import {getAlarmPhoto} from '@/api/system/kanban';

export default {
  data() {
    return {
      deviceImages: [],
      timer: null
    };
  },
  computed: {
    paddedSlots() {
      const rows = this.deviceImages.slice(0, 6)
      while (rows.length < 6) {
        rows.push(null)
      }
      return rows
    }
  },
  mounted() {
    this.fetchData()
    this.switper()
  },
  beforeDestroy() {
    this.clearData()
  },
  methods: {
    isSleepType(name) {
      return String(name || '').indexOf('睡岗') !== -1
    },
    async fetchData() {
      try {
        const res = await getAlarmPhoto();
        if (res.code != 200) throw new Error(res.msg);
        this.deviceImages = res.data || [];
      } catch (error) {
        console.error(error);
      }
    },
    switper() {
      if (this.timer) {
        return
      }
      this.timer = setInterval(this.fetchData, 20000);
    },
    clearData() {
      if (this.timer) {
        clearInterval(this.timer)
        this.timer = null
      }
    }
  },
};
</script>

<style lang='scss' scoped>
.avb {
  width: 100%;
  padding: 8px 16px 0;
  box-sizing: border-box;
}

.img {
  width: 100%;
  height: 168px;
}

.image-container {
  border: 1px solid var(--sva-border);
  border-radius: 8px;
  width: 100%;
  height: 168px;
  display: block;
  position: relative;
  overflow: hidden;
  background: var(--sva-surface-2);
  margin-top: 8px;
}

.image-slot {
  width: 100%;
  height: 168px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--sva-text-muted);
  font-size: 13px;
  background: var(--sva-surface-2);
}

.alarm-type-badge {
  position: absolute;
  top: 8px;
  right: 8px;
  z-index: 1;
}

.caption {
  margin-top: 6px;
  height: 24px;
  line-height: 24px;
  color: var(--sva-text-muted);
  font-size: 13px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
