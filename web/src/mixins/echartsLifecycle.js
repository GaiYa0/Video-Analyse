import * as echarts from 'echarts'

/**
 * One ECharts instance per field. Refresh data with setOption only.
 * Set `echartsFields: ['chart']` on the component so instances are disposed.
 */
export default {
  data() {
    return {
      $_echartsResizeHandler: null
    }
  },
  beforeDestroy() {
    this.unbindChartResize()
    const fields = this.$options.echartsFields || []
    fields.forEach((field) => this.disposeChart(field))
  },
  methods: {
    ensureChart(field, el, onClick, theme) {
      if (!el) {
        return null
      }
      if (!this[field]) {
        this[field] = theme ? echarts.init(el, theme) : echarts.init(el)
        if (typeof onClick === 'function') {
          this[field].on('click', onClick)
        }
      }
      return this[field]
    },
    disposeChart(field) {
      const chart = this[field]
      if (chart && typeof chart.dispose === 'function' && !chart.isDisposed()) {
        chart.off('click')
        chart.dispose()
      }
      this[field] = null
    },
    bindChartResize(handler) {
      if (this.$_echartsResizeHandler) {
        return
      }
      this.$_echartsResizeHandler = handler
      window.addEventListener('resize', this.$_echartsResizeHandler)
    },
    unbindChartResize() {
      if (this.$_echartsResizeHandler) {
        window.removeEventListener('resize', this.$_echartsResizeHandler)
        this.$_echartsResizeHandler = null
      }
    }
  }
}
