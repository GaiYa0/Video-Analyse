<template>
  <div ref="templateChart" id="templateChart" class="templateChart"></div>
</template>

<script>
import * as echarts from 'echarts'

export default {
  data() {
    return {
      templateChart: null,
    }
  },
  props: {
    option: {
      type: Object,
      require: true,
    },
  },
  methods: {
    initChart() {
      const dom = this.$refs['templateChart']
      this.templateChart = echarts.init(dom)
      this.setOption()
    },
    setOption() {
      this.templateChart.setOption(this.option)
    },
  },
}
</script>

<style scoped>
.templateChart {
  width: 100%;
  height: 100%;
}
</style>
