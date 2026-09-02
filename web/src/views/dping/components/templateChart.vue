<template>
  <div ref="templateChart" id="templateChart" class="templateChart"></div>
</template>

<script>
import echartsLifecycle from '@/mixins/echartsLifecycle'

export default {
  mixins: [echartsLifecycle],
  echartsFields: ['chart'],
  props: {
    option: {
      type: Object,
      require: true,
    },
  },
  methods: {
    initChart() {
      const chart = this.ensureChart('chart', this.$refs.templateChart)
      if (!chart) {
        return
      }
      this.setOption()
      this.bindChartResize(() => {
        this.chart && this.chart.resize()
      })
    },
    setOption() {
      if (this.chart && this.option) {
        this.chart.setOption(this.option)
      }
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
