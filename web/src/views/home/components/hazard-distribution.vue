<template>
  <div class="dist-page">
    <section>
      <div class="section-head">
        <h3>报警类型统计</h3>
        <div>
          <span class="clickable" @click="selectTime('1', 1)" :style="timeStyle('1', 1)">周</span>
          <span class="time-sep"> | </span>
          <span class="clickable" @click="selectTime('2', 1)" :style="timeStyle('2', 1)">月</span>
          <span class="time-sep"> | </span>
          <span class="clickable" @click="selectTime('3', 1)" :style="timeStyle('3', 1)">季度</span>
          <span class="time-sep"> | </span>
          <span class="clickable" @click="selectTime('4', 1)" :style="timeStyle('4', 1)">年</span>
        </div>
      </div>
      <div class="col">
        <div ref="domainDis" class="echart"></div>
      </div>
    </section>
    <section>
      <div class="section-head">
        <h3>报警类型分布</h3>
        <div>
          <span class="clickable" @click="selectTime('1', 3)" :style="timeStyle('1', 3)">周</span>
          <span class="time-sep"> | </span>
          <span class="clickable" @click="selectTime('2', 3)" :style="timeStyle('2', 3)">月</span>
          <span class="time-sep"> | </span>
          <span class="clickable" @click="selectTime('3', 3)" :style="timeStyle('3', 3)">季度</span>
          <span class="time-sep"> | </span>
          <span class="clickable" @click="selectTime('4', 3)" :style="timeStyle('4', 3)">年</span>
        </div>
      </div>
      <div class="col">
        <div ref="typeDis" class="echart"></div>
      </div>
    </section>
  </div>
</template>

<script>
import {getColumn, getTypeSpread} from '@/api/system/kanban';
import * as echarts from "echarts";
import {
  SVA_CHART_BAR,
  svaCategoryAxis,
  svaGrid,
  svaTooltip,
  svaValueAxis
} from '@/utils/chartTheme';

export default {
  props: {
    orgIndex: {
      type: String,
      default: ''
    }
  },

  data() {
    return {
      domainData: {
        names: [],
        values: []
      },
      typeData: {
        names: [],
        values: []
      },
      selectedTime1: '2',
      selectedTime3: '2',
      time: ['时间', '周', '月', '季度', '年'],
      domainChart: null,
      typeChart: null
    };
  },

  computed: {
    timeStyle() {
      return (time, number) => ({
        'color': this[`selectedTime${number}`] === time ? 'var(--sva-accent)' : 'var(--sva-text-muted)',
        'font-weight': this[`selectedTime${number}`] === time ? 'bold' : 'normal'
      });
    }
  },

  methods: {
    onChartResize() {
      if (this.domainChart) this.domainChart.resize();
      if (this.typeChart) this.typeChart.resize();
    },

    horizontalBarOption(names, values) {
      return {
        tooltip: Object.assign({
          trigger: "axis",
          axisPointer: { type: "shadow" }
        }, svaTooltip),
        grid: svaGrid({ left: 12, right: 24 }),
        xAxis: svaValueAxis(),
        yAxis: svaCategoryAxis({
          data: names,
          axisLabel: {
            color: '#e6edf3',
            fontSize: 12,
            width: 88,
            overflow: 'truncate'
          }
        }),
        series: [
          {
            type: "bar",
            barMaxWidth: 18,
            data: values,
            itemStyle: { color: SVA_CHART_BAR }
          }
        ]
      };
    },

    ensureChart(refName, chartKey) {
      const el = this.$refs[refName];
      if (!el) return null;
      if (!this[chartKey]) {
        this[chartKey] = echarts.init(el);
      }
      return this[chartKey];
    },

    initDomainEcharts() {
      const chart = this.ensureChart('domainDis', 'domainChart');
      if (!chart) return;
      chart.setOption(this.horizontalBarOption(this.domainData.names, this.domainData.values), true);
      chart.off('click');
      chart.on('click', (params) => {
        this.$router.push({
          path: "/warning/warning",
          query: {withQue: 8, time: this.time[this.selectedTime1], alarm_type_name: params.name}
        });
      });
    },

    async fetchDomain() {
      this.domainData = { names: [], values: [] };
      const domainRes = await getColumn(this.orgIndex, this.selectedTime1);
      (domainRes.data || []).forEach(item => {
        this.domainData.names.push(item.alarm_type_name);
        this.domainData.values.push(item.num);
      });
      this.$nextTick(() => this.initDomainEcharts());
    },

    initTypeEcharts() {
      const chart = this.ensureChart('typeDis', 'typeChart');
      if (!chart) return;
      chart.setOption(this.horizontalBarOption(this.typeData.names, this.typeData.values), true);
      chart.off('click');
      chart.on('click', (params) => {
        this.$router.push({
          path: "/warning/warning",
          query: {withQue: 8, time: this.time[this.selectedTime3], alarm_type_name: params.name}
        });
      });
    },

    async fetchTypeSpread() {
      this.typeData = { names: [], values: [] };
      const typeRes = await getTypeSpread(this.orgIndex, this.selectedTime3);
      (typeRes.data || []).forEach((item) => {
        this.typeData.names.push(item.alarm_type_name);
        this.typeData.values.push(item.num);
      });
      this.$nextTick(() => this.initTypeEcharts());
    },

    async fetchData() {
      try {
        await Promise.all([
          this.fetchDomain(),
          this.fetchTypeSpread()
        ]);
      } catch (error) {
        console.error(error);
      }
    },

    selectTime(time, number) {
      this[`selectedTime${number}`] = time;
    }
  },

  mounted() {
    this.fetchData();
    window.addEventListener("resize", this.onChartResize);
  },

  beforeDestroy() {
    window.removeEventListener("resize", this.onChartResize);
    if (this.domainChart) {
      this.domainChart.dispose();
      this.domainChart = null;
    }
    if (this.typeChart) {
      this.typeChart.dispose();
      this.typeChart = null;
    }
  },

  watch: {
    orgIndex() {
      this.fetchData();
    },
    selectedTime1() {
      this.fetchDomain();
    },
    selectedTime3() {
      this.fetchTypeSpread();
    },
  }
};
</script>

<style scoped lang="less">
.dist-page {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
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

.clickable {
  cursor: pointer;
  user-select: none;
  transition: color 0.3s ease;
  color: var(--sva-text-muted);
  font-size: small;
}

@media (max-width: 1100px) {
  .dist-page {
    grid-template-columns: 1fr;
  }
}
</style>
