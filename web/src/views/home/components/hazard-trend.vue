<template>
  <div>
    <tiny-layout>
      <tiny-row :flex="true" justify="center">
        <tiny-col :span="9">
          <div style="display: flex; align-items: center; justify-content: space-between;">
            <h3>报警趋势分析</h3>
            <div>
              <span class="clickable" @click="selectTime('周')" :style="timeStyle('周')">周</span>
              <span style="color: grey;"> | </span>
              <span class="clickable" @click="selectTime('月')" :style="timeStyle('月')">月</span>
              <span style="color: grey;"> | </span>
              <span class="clickable" @click="selectTime('季度')" :style="timeStyle('季度')">季度</span>
              <span style="color: grey;"> | </span>
              <span class="clickable" @click="selectTime('年')" :style="timeStyle('年')">年</span>
            </div>
          </div>
          <div class="col">
            <div class="left">
              <router-link :to="{ path: '/warning/warning', query: { withQue: 8, time: this.selectedTime} }">
                <div class="left-content">
                  <div class="echart" id="trend" :style="trendStyle"></div>
                </div>
              </router-link>
            </div>
          </div>
        </tiny-col>
        <tiny-col :span="7">
          <h3>增长率分析</h3>
          <div class="col">
            <div class="left">
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
                      <span>{{ growthData.monthGrowthRate }}%</span>
                    </el-col>
                    <el-col class="growthitem">
                      季度增长率：
                      <span v-if="growthData.quarteGrowthRate > 0"><img src="@/assets/images/home-up.png"
                                                                        class="image"/></span>
                      <span v-else-if="growthData.quarteGrowthRate < 0"><img src="@/assets/images/home-down.png"
                                                                             class="image"/></span>
                      <span v-else> </span>
                      <span>{{ growthData.quarteGrowthRate }}%</span>
                    </el-col>
                    <el-col class="growthitem">
                      年度增长率：
                      <span v-if="growthData.yearGrowthRate > 0"><img src="@/assets/images/home-up.png"
                                                                      class="image"/></span>
                      <span v-else-if="growthData.yearGrowthRate < 0"><img src="@/assets/images/home-down.png"
                                                                           class="image"/></span>
                      <span v-else> </span>
                      <span>{{ growthData.yearGrowthRate }}%</span>
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
                      <span>{{ growthData.monthRectification }}%</span>
                    </el-col>
                    <el-col class="growthitem">
                      季度处置率：
                      <span v-if="growthData.quarterRectification > 0"><img src="@/assets/images/home-up.png"
                                                                            class="image"/></span>
                      <span v-else-if="growthData.quarterRectification < 0"><img src="@/assets/images/home-down.png"
                                                                                 class="image"/></span>
                      <span v-else> </span>
                      <span>{{ growthData.quarterRectification }}%</span>
                    </el-col>
                    <el-col class="growthitem">
                      年度处置率：
                      <span v-if="growthData.yearRectification > 0"><img src="@/assets/images/home-up.png"
                                                                         class="image"/></span>
                      <span v-else-if="growthData.yearRectification < 0"><img src="@/assets/images/home-down.png"
                                                                              class="image"/></span>
                      <span v-else> </span>
                      <span>{{ growthData.yearRectification }}%</span>
                    </el-col>
                  </div>
                </el-col>
              </el-row>

            </div>
          </div>
        </tiny-col>
      </tiny-row>
    </tiny-layout>
  </div>
</template>

<script>
import {Col as TinyCol, Layout as TinyLayout, Row as TinyRow,} from '@opentiny/vue';

import {getGrowth, getTrend} from '@/api/system/kanban';
import * as echarts from "echarts";

export default {
  components: {
    TinyLayout,
    TinyRow,
    TinyCol
  },

  props: {
    orgIndex: {
      type: String,
      default: ''
    }
  },

  data() {
    return {
      like: true,
      value1: 4154.564,
      value2: 1314,
      title: "增长人数",
      trendStyle: {
        float: "left", width: "600px", height: "250px"
      },
      options: [
        {label: '周'},
        {label: '月'},
        {label: '季度'},
        {label: '年'}
      ],
      selectedTime: '周',
      chartData: {
        xData: [],
        yData: []
      },

      trendData: {
        week: {
          xData: [],
          yData: []
        },
        month: {
          xData: [],
          yData: []
        },
        quarter: {
          xData: [],
          yData: []
        },
        year: {
          xData: [],
          yData: []
        }
      },

      growthData: {
        quarteGrowthRate: 0.0,
        yearRectification: 0.0,
        monthRectification: 0.0,
        monthGrowthRate: 0.0,
        yearGrowthRate: 0.0,
        quarterRectification: 0.0
      }
    };
  },

  computed: {
    timeStyle() {
      return (time) => ({
        'color': this.selectedTime === time ? 'blue' : 'black',
        'font-weight': this.selectedTime === time ? 'bold' : 'normal'
      });
    }
  },

  methods: {
    selectTime(time) {
      this.selectedTime = time;
    },

    initTrendEcharts() {
      const option = {
        tooltip: {
          trigger: 'axis'
        },
        xAxis: {
          type: 'category',
          boundaryGap: false,
          data: this.chartData.xData
        },
        yAxis: {
          type: 'value',
          axisLabel: {
            color: "black"
          }
        },
        series: [
          {
            data: this.chartData.yData,
            type: 'line',
            areaStyle: {}
          }
        ]
      };

      const trend = echarts.init(document.getElementById("trend"));
      trend.setOption(option);
      // 随着屏幕大小调节图表
      window.addEventListener("resize", () => {
        trend.resize();
      });
    },

    async fetchData() {
      try {
        const [trendDataRes, growthRes] = await Promise.all([
          getTrend(this.orgIndex),
          getGrowth(this.orgIndex)
        ]);

        this.trendData = {
          week: {
            xData: [],
            yData: []
          },
          month: {
            xData: [],
            yData: []
          },
          quarter: {
            xData: [],
            yData: []
          },
          year: {
            xData: [],
            yData: []
          }
        };

        trendDataRes.data.week.map(item => {
          this.trendData.week.xData.push({
            value: `${item.weeks}周`,
            textStyle: {
              color: "black"
            },
            fontSize: 22
          });
          this.trendData.week.yData.push(item.total);
        });

        trendDataRes.data.month.map(item => {
          this.trendData.month.xData.push({
            value: `${item.months}月`,
            textStyle: {
              color: "black"
            },
            fontSize: 22
          });
          this.trendData.month.yData.push(item.total);
        });

        trendDataRes.data.quarter.map(item => {
          this.trendData.quarter.xData.push({
            value: `第${item.quarters}季度`,
            textStyle: {
              color: "black"
            },
            fontSize: 22
          });
          this.trendData.quarter.yData.push(item.total);
        });

        trendDataRes.data.year.map(item => {
          this.trendData.year.xData.push({
            value: `${item.years}年`,
            textStyle: {
              color: "black"
            },
            fontSize: 22
          });
          this.trendData.year.yData.push(item.total);
        });

        this.chartData = this.trendData.week;
        this.growthData = growthRes.data;
        this.initTrendEcharts();
      } catch (error) {
        console.error(error);
      }
    }
  },

  mounted() {
    this.fetchData();
  },

  watch: {
    selectedTime(newVal, oldVal) {
      if (newVal === '周') {
        this.chartData = this.trendData.week;
      } else if (newVal === '月') {
        this.chartData = this.trendData.month;
      } else if (newVal === '季度') {
        this.chartData = this.trendData.quarter;
      } else {
        this.chartData = this.trendData.year;
      }
      this.initTrendEcharts();
    },

    orgIndex(newVal, oldVal) {
      this.fetchData();
    }
  },
};
</script>

<style scoped lang="less">
.col {
  background-color: white;
  display: flex;
  justify-content: space-around;
  height: 250px;
  text-align: center;
  border-radius: 10px;
  box-shadow: 0 4px 12px 0 rgba(0, 0, 0, 0.05);
}

.col:hover {
  box-shadow: 0 3px 10px 0 rgb(64, 98, 225, 0.45);
}

.left {
  width: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
}


.left-content {
  display: flex;
  align-items: flex-end;
  justify-content: center;
  padding-bottom: 15px;
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
  color: black;
  padding: 8px;
  font-size: large;
}
</style>
