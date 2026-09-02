<template>
  <div class="trend-page">
    <section class="trend-block">
      <div class="section-head">
        <h3>报警趋势分析</h3>
        <div>
          <span class="clickable" @click="selectTime('周')" :style="timeStyle('周')">周</span>
          <span class="time-sep"> | </span>
          <span class="clickable" @click="selectTime('月')" :style="timeStyle('月')">月</span>
          <span class="time-sep"> | </span>
          <span class="clickable" @click="selectTime('季度')" :style="timeStyle('季度')">季度</span>
          <span class="time-sep"> | </span>
          <span class="clickable" @click="selectTime('年')" :style="timeStyle('年')">年</span>
        </div>
      </div>
      <div class="col">
        <router-link class="chart-link" :to="{ path: '/warning/warning', query: { withQue: 8, time: selectedTime} }">
          <div ref="trend" class="echart"></div>
        </router-link>
      </div>
    </section>
    <section class="growth-block">
      <h3>增长率分析</h3>
      <div class="col growth-panel">
        <div class="growth-grid">
          <div class="growth-col">
            <div class="growth-item">
              <span class="growth-label">月度增长率</span>
              <span class="growth-value">
                <img v-if="growthData.monthGrowthRate > 0" src="@/assets/images/home-up.png" class="trend-icon" alt=""/>
                <img v-else-if="growthData.monthGrowthRate < 0" src="@/assets/images/home-down.png" class="trend-icon" alt=""/>
                {{ growthData.monthGrowthRate }}%
              </span>
            </div>
            <div class="growth-item">
              <span class="growth-label">季度增长率</span>
              <span class="growth-value">
                <img v-if="growthData.quarteGrowthRate > 0" src="@/assets/images/home-up.png" class="trend-icon" alt=""/>
                <img v-else-if="growthData.quarteGrowthRate < 0" src="@/assets/images/home-down.png" class="trend-icon" alt=""/>
                {{ growthData.quarteGrowthRate }}%
              </span>
            </div>
            <div class="growth-item">
              <span class="growth-label">年度增长率</span>
              <span class="growth-value">
                <img v-if="growthData.yearGrowthRate > 0" src="@/assets/images/home-up.png" class="trend-icon" alt=""/>
                <img v-else-if="growthData.yearGrowthRate < 0" src="@/assets/images/home-down.png" class="trend-icon" alt=""/>
                {{ growthData.yearGrowthRate }}%
              </span>
            </div>
          </div>
          <div class="growth-col">
            <div class="growth-item">
              <span class="growth-label">月度处置率</span>
              <span class="growth-value">
                <img v-if="growthData.monthRectification > 0" src="@/assets/images/home-up.png" class="trend-icon" alt=""/>
                <img v-else-if="growthData.monthRectification < 0" src="@/assets/images/home-down.png" class="trend-icon" alt=""/>
                {{ growthData.monthRectification }}%
              </span>
            </div>
            <div class="growth-item">
              <span class="growth-label">季度处置率</span>
              <span class="growth-value">
                <img v-if="growthData.quarterRectification > 0" src="@/assets/images/home-up.png" class="trend-icon" alt=""/>
                <img v-else-if="growthData.quarterRectification < 0" src="@/assets/images/home-down.png" class="trend-icon" alt=""/>
                {{ growthData.quarterRectification }}%
              </span>
            </div>
            <div class="growth-item">
              <span class="growth-label">年度处置率</span>
              <span class="growth-value">
                <img v-if="growthData.yearRectification > 0" src="@/assets/images/home-up.png" class="trend-icon" alt=""/>
                <img v-else-if="growthData.yearRectification < 0" src="@/assets/images/home-down.png" class="trend-icon" alt=""/>
                {{ growthData.yearRectification }}%
              </span>
            </div>
          </div>
        </div>
      </div>
    </section>
  </div>
</template>

<script>
import {getGrowth, getTrend} from '@/api/system/kanban';
import * as echarts from "echarts";
import {
  SVA_CHART_AREA,
  SVA_CHART_LINE,
  svaCategoryAxis,
  svaGrid,
  svaTooltip,
  svaValueAxis
} from '../chartTheme';

export default {
  props: {
    orgIndex: {
      type: String,
      default: ''
    }
  },

  data() {
    return {
      selectedTime: '周',
      chartData: {
        xData: [],
        yData: []
      },
      trendData: {
        week: { xData: [], yData: [] },
        month: { xData: [], yData: [] },
        quarter: { xData: [], yData: [] },
        year: { xData: [], yData: [] }
      },
      growthData: {
        quarteGrowthRate: 0.0,
        yearRectification: 0.0,
        monthRectification: 0.0,
        monthGrowthRate: 0.0,
        yearGrowthRate: 0.0,
        quarterRectification: 0.0
      },
      trendChart: null
    };
  },

  computed: {
    timeStyle() {
      return (time) => ({
        'color': this.selectedTime === time ? 'var(--sva-accent)' : 'var(--sva-text-muted)',
        'font-weight': this.selectedTime === time ? 'bold' : 'normal'
      });
    }
  },

  methods: {
    selectTime(time) {
      this.selectedTime = time;
    },

    onTrendResize() {
      if (this.trendChart) this.trendChart.resize();
    },

    initTrendEcharts() {
      const el = this.$refs.trend;
      if (!el) return;
      if (!this.trendChart) {
        this.trendChart = echarts.init(el);
        window.addEventListener("resize", this.onTrendResize);
      }
      this.trendChart.setOption({
        tooltip: Object.assign({ trigger: 'axis' }, svaTooltip),
        grid: svaGrid(),
        xAxis: svaCategoryAxis({
          boundaryGap: false,
          data: this.chartData.xData,
          axisLabel: {
            color: '#e6edf3',
            fontSize: 11,
            interval: 'auto',
            hideOverlap: true
          }
        }),
        yAxis: svaValueAxis(),
        series: [
          {
            data: this.chartData.yData,
            type: 'line',
            smooth: true,
            symbol: 'circle',
            symbolSize: 6,
            itemStyle: { color: SVA_CHART_LINE },
            lineStyle: { color: SVA_CHART_LINE, width: 2 },
            areaStyle: { color: SVA_CHART_AREA }
          }
        ]
      }, true);
    },

    async fetchData() {
      try {
        const [trendDataRes, growthRes] = await Promise.all([
          getTrend(this.orgIndex),
          getGrowth(this.orgIndex)
        ]);

        this.trendData = {
          week: { xData: [], yData: [] },
          month: { xData: [], yData: [] },
          quarter: { xData: [], yData: [] },
          year: { xData: [], yData: [] }
        };

        (trendDataRes.data.week || []).forEach(item => {
          this.trendData.week.xData.push(`${item.weeks}周`);
          this.trendData.week.yData.push(item.total);
        });
        (trendDataRes.data.month || []).forEach(item => {
          this.trendData.month.xData.push(`${item.months}月`);
          this.trendData.month.yData.push(item.total);
        });
        (trendDataRes.data.quarter || []).forEach(item => {
          this.trendData.quarter.xData.push(`第${item.quarters}季度`);
          this.trendData.quarter.yData.push(item.total);
        });
        (trendDataRes.data.year || []).forEach(item => {
          this.trendData.year.xData.push(`${item.years}年`);
          this.trendData.year.yData.push(item.total);
        });

        this.chartData = this.trendData.week;
        this.growthData = growthRes.data;
        this.$nextTick(() => this.initTrendEcharts());
      } catch (error) {
        console.error(error);
      }
    }
  },

  mounted() {
    this.fetchData();
  },

  beforeDestroy() {
    window.removeEventListener("resize", this.onTrendResize);
    if (this.trendChart) {
      this.trendChart.dispose();
      this.trendChart = null;
    }
  },

  watch: {
    selectedTime(newVal) {
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

    orgIndex() {
      this.fetchData();
    }
  },
};
</script>

<style scoped lang="less">
.trend-page {
  display: grid;
  grid-template-columns: minmax(0, 1.4fr) minmax(240px, 0.8fr);
  gap: 16px;
  min-width: 0;
}

.section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

h3 {
  margin: 0 0 8px;
  color: var(--sva-text);
  font-size: 14px;
  font-weight: 600;
}

.time-sep {
  color: var(--sva-text-muted);
}

.col {
  background-color: var(--sva-surface);
  min-height: 250px;
  border-radius: 10px;
  border: 1px solid var(--sva-border);
  min-width: 0;
}

.echart {
  width: 100%;
  height: 250px;
}

.chart-link {
  display: block;
}

.clickable {
  cursor: pointer;
  user-select: none;
  transition: color 0.3s ease;
  font-size: small;
}

.growth-panel {
  display: flex;
  align-items: stretch;
}

.growth-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px 24px;
  width: 100%;
  padding: 16px 20px;
  box-sizing: border-box;
}

.growth-col {
  display: flex;
  flex-direction: column;
  justify-content: space-evenly;
  gap: 12px;
}

.growth-item {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 6px;
}

.growth-label {
  color: var(--sva-text-muted);
  font-size: 12px;
}

.growth-value {
  display: flex;
  align-items: center;
  gap: 6px;
  color: var(--sva-text);
  font-size: 18px;
  font-weight: 600;
}

.trend-icon {
  width: 12px;
  height: 12px;
}

@media (max-width: 1100px) {
  .trend-page {
    grid-template-columns: 1fr;
  }
}
</style>
