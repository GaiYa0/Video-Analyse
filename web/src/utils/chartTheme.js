/** 深色 ECharts 主题。数值写死，避免 canvas 读不到 CSS 变量。 */
export const SVA_CHART_TEXT = '#e6edf3'
export const SVA_CHART_MUTED = '#8b949e'
export const SVA_CHART_BORDER = 'rgba(255, 255, 255, 0.08)'
export const SVA_CHART_ACCENT = '#6b9bb8'
export const SVA_CHART_BAR = 'rgba(107, 155, 184, 0.85)'
export const SVA_CHART_LINE = 'rgba(107, 155, 184, 0.95)'
export const SVA_CHART_AREA = 'rgba(107, 155, 184, 0.22)'

export const svaTooltip = {
  backgroundColor: '#161b22',
  borderColor: SVA_CHART_BORDER,
  textStyle: { color: SVA_CHART_TEXT }
}

export function svaGrid(extra) {
  return Object.assign({
    containLabel: true,
    left: 8,
    right: 16,
    top: 24,
    bottom: 8
  }, extra || {})
}

export function svaCategoryAxis(extra) {
  return Object.assign({
    type: 'category',
    axisLabel: { color: SVA_CHART_TEXT, fontSize: 12 },
    axisLine: { lineStyle: { color: SVA_CHART_BORDER } },
    axisTick: { show: false },
    splitLine: { show: false }
  }, extra || {})
}

export function svaValueAxis(extra) {
  return Object.assign({
    type: 'value',
    axisLabel: { color: SVA_CHART_MUTED, fontSize: 11 },
    axisLine: { show: false },
    axisTick: { show: false },
    splitLine: { lineStyle: { color: SVA_CHART_BORDER, type: 'dashed' } }
  }, extra || {})
}
