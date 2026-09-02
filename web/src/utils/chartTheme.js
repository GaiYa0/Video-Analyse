/** Glance 深色图表主题。数值写死，避免 canvas 读不到 CSS 变量。 */
export const SVA_CHART_TEXT = '#e6edf3'
export const SVA_CHART_MUTED = '#8b949e'
export const SVA_CHART_BORDER = 'rgba(158, 197, 212, 0.10)'
export const SVA_CHART_SPLIT = 'rgba(158, 197, 212, 0.14)'
export const SVA_CHART_LIGHT = '#9ec5d4'
export const SVA_CHART_ACCENT = '#6b9bb8'
export const SVA_CHART_DEEP = '#3d6f86'
export const SVA_CHART_BAR = 'rgba(107, 155, 184, 0.92)'
export const SVA_CHART_LINE = '#9ec5d4'
export const SVA_CHART_AREA = 'rgba(107, 155, 184, 0.18)'
export const SVA_CHART_PALETTE = ['#9ec5d4', '#6b9bb8', '#3d6f86']
export const SVA_CHART_BAR_MAX_WIDTH = 22
export const SVA_CHART_BAR_RADIUS = [0, 4, 4, 0]
export const SVA_CHART_BAR_RADIUS_VERTICAL = [4, 4, 0, 0]

export const svaTooltip = {
  backgroundColor: '#161b22',
  borderColor: SVA_CHART_BORDER,
  textStyle: { color: SVA_CHART_TEXT }
}

export function svaCountTooltip(extra) {
  return Object.assign({
    backgroundColor: '#161b22',
    borderColor: SVA_CHART_BORDER,
    textStyle: { color: SVA_CHART_TEXT },
    valueFormatter: function (value) {
      if (value === undefined || value === null || value === '') {
        return ''
      }
      return value + ' 条'
    }
  }, extra || {})
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
    axisLine: { lineStyle: { color: SVA_CHART_BORDER, width: 1 } },
    axisTick: { show: true, length: 3, lineStyle: { color: SVA_CHART_SPLIT } },
    splitLine: { show: false }
  }, extra || {})
}

export function svaValueAxis(extra) {
  return Object.assign({
    type: 'value',
    splitNumber: 4,
    minInterval: 1,
    axisLabel: { color: SVA_CHART_MUTED, fontSize: 11 },
    axisLine: { show: false },
    axisTick: { show: true, length: 3, lineStyle: { color: SVA_CHART_SPLIT } },
    splitLine: { lineStyle: { color: SVA_CHART_SPLIT, width: 1, type: 'solid' } }
  }, extra || {})
}
