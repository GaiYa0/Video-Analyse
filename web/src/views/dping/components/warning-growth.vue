<template>
  <div>
    <div class="col">
      <el-row :gutter="12">
        <el-col :span="12">
          <div class="grid-content bg-purple">
            <el-col class="growthitem">
              月度增长率：
              <span v-if="growthData.monthGrowthRate > 0"><img src="@/assets/images/home-up.png"
                                                               class="image"/></span>
              <span v-else-if="growthData.monthGrowthRate < 0"><img src="@/assets/images/home-down.png"
                                                                    class="image"/></span>
              <span v-else> </span>
              <span class="gate-color">{{ growthData.monthGrowthRate }}%</span>
            </el-col>
            <el-col class="growthitem">
              季度增长率：
              <span v-if="growthData.quarteGrowthRate > 0"><img src="@/assets/images/home-up.png"
                                                                class="image"/></span>
              <span v-else-if="growthData.quarteGrowthRate < 0"><img src="@/assets/images/home-down.png"
                                                                     class="image"/></span>
              <span v-else> </span>
              <span class="gate-color">{{ growthData.quarteGrowthRate }}%</span>
            </el-col>
            <el-col class="growthitem">
              年度增长率：
              <span v-if="growthData.yearGrowthRate > 0"><img src="@/assets/images/home-up.png"
                                                              class="image"/></span>
              <span v-else-if="growthData.yearGrowthRate < 0"><img src="@/assets/images/home-down.png"
                                                                   class="image"/></span>
              <span v-else> </span>
              <span class="gate-color">{{ growthData.yearGrowthRate }}%</span>
            </el-col>
          </div>
        </el-col>
        <el-col :span="12">
          <div class="grid-content bg-purple-light">
            <el-col class="growthitem">
              月度处置率:
              <span v-if="growthData.monthRectification > 0"><img src="@/assets/images/home-up.png"
                                                                  class="image"/></span>
              <span v-else-if="growthData.monthRectification < 0"><img src="@/assets/images/home-down.png"
                                                                       class="image"/></span>
              <span v-else> </span>
              <span class="gate-color">{{ growthData.monthRectification }}%</span>
            </el-col>
            <el-col class="growthitem">
              季度处置率：
              <span v-if="growthData.quarterRectification > 0"><img src="@/assets/images/home-up.png"
                                                                    class="image"/></span>
              <span v-else-if="growthData.quarterRectification < 0"><img src="@/assets/images/home-down.png"
                                                                         class="image"/></span>
              <span v-else> </span>
              <span class="gate-color">{{ growthData.quarterRectification }}%</span>
            </el-col>
            <el-col class="growthitem">
              年度处置率：
              <span v-if="growthData.yearRectification > 0"><img src="@/assets/images/home-up.png"
                                                                 class="image"/></span>
              <span v-else-if="growthData.yearRectification < 0"><img src="@/assets/images/home-down.png"
                                                                      class="image"/></span>
              <span v-else> </span>
              <span class="gate-color">{{ growthData.yearRectification }}%</span>
            </el-col>
          </div>
        </el-col>
      </el-row>
    </div>
  </div>
</template>

<script>
import {Col as TinyCol, Layout as TinyLayout, Row as TinyRow,} from '@opentiny/vue';

import {getGrowth} from '@/api/system/kanban';

export default {
  components: {
    TinyLayout,
    TinyRow,
    TinyCol
  },

  data() {
    return {
      growthData: {
        quarteGrowthRate: 0.0,
        yearRectification: 0.0,
        monthRectification: 0.0,
        monthGrowthRate: 0.0,
        yearGrowthRate: 0.0,
        quarterRectification: 0.0
      },
      pushRefreshTimer: null
    };
  },

  methods: {
    async fetchData() {
      try {
        const growthRes = await getGrowth();
        this.growthData = growthRes.data;
      } catch (error) {
        console.error(error);
      }
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
    },
  },


  mounted() {
    this.fetchData()
    window.addEventListener('sva:alarm-push', this.handleAlarmPush)
  },

  beforeDestroy() {
    window.removeEventListener('sva:alarm-push', this.handleAlarmPush)
    this.clearData()
  },
};
</script>

<style scoped lang="less">
.col {
  margin-top: 30px;
  background-color: transparent;
  display: flex;
  justify-content: space-around;
  height: 250px;
  text-align: center;
  border-radius: 10px;
  box-shadow: 0 4px 12px 0 rgba(0, 0, 0, 0.05);
}

.data-container {
  display: grid;
  grid-template-columns: 1fr 1fr;
  /* 两列等宽 */
  grid-gap: 15px;
  /* 列与列之间的间距 */
}

.item {
  color: white;
  padding: 6px;
  text-align: left;
}

.clickable {
  cursor: pointer;
  user-select: none;
  transition: color 0.3s ease;
  font-size: small;
}

.growthitem {
  background: url("~@/assets/images/warnGateBg.png") no-repeat;
  background-size: cover;
  color: white;
  padding: 20px;
  font-size: large;
}

.gate-color {
  color: #30FBE5;
}
</style>
