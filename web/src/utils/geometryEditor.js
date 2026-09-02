export function clamp01(value) {
  if (value < 0) return 0
  if (value > 1) return 1
  return value
}

export function createEmptyGeometryConfig() {
  return {
    regions: [],
    lines: [],
    behaviorRules: []
  }
}

export function parseGeometryConfigInput(geometryConfig) {
  let parsed = geometryConfig
  if (typeof parsed === 'string') {
    const trimmed = parsed.trim()
    if (!trimmed) {
      return null
    }
    try {
      parsed = JSON.parse(trimmed)
    } catch (error) {
      return null
    }
  }

  if (Array.isArray(parsed)) {
    return {
      regions: parsed,
      lines: []
    }
  }

  return parsed && typeof parsed === 'object' ? parsed : null
}

export function normalizePoint(point) {
  const x = Number(point && point.x)
  const y = Number(point && point.y)
  if (!Number.isFinite(x) || !Number.isFinite(y)) {
    return null
  }
  return {
    x: clamp01(Number(x.toFixed(6))),
    y: clamp01(Number(y.toFixed(6)))
  }
}

export function normalizePointList(points, minimumCount = 0) {
  if (!Array.isArray(points)) {
    return []
  }
  const normalized = points.map(item => normalizePoint(item)).filter(Boolean)
  return normalized.length >= minimumCount ? normalized : []
}

export function buildPrimaryRegion(points) {
  return {
    id: 'region_primary',
    name: '主区域',
    type: 'polygon',
    primary: true,
    closed: true,
    points: normalizePointList(points, 3)
  }
}

export function createRegionConfig(seed, overrides = {}) {
  const nextIndex = seed
  const isPrimary = Boolean(overrides.primary)
  return {
    id: overrides.id || (isPrimary ? 'region_primary' : `region_${nextIndex}`),
    name: overrides.name || (isPrimary ? '主区域' : `区域${nextIndex}`),
    type: 'polygon',
    primary: isPrimary,
    points: normalizePointList(overrides.points, 0),
    ...overrides
  }
}

export function createLineConfig(seed, overrides = {}) {
  const nextIndex = seed
  return {
    id: `line_${nextIndex}`,
    name: `线段${nextIndex}`,
    type: 'tripwire',
    direction: 'both',
    points: [],
    ...overrides
  }
}

export function normalizeLineDirection(direction) {
  if (direction === 'left_to_right' || direction === 'right_to_left') {
    return direction
  }
  return 'both'
}

export function getLineDirectionLabel(direction) {
  if (direction === 'left_to_right') {
    return '正向'
  }
  if (direction === 'right_to_left') {
    return '反向'
  }
  return '双向'
}

export function getNextLineDirection(direction) {
  if (direction === 'left_to_right') {
    return 'right_to_left'
  }
  if (direction === 'right_to_left') {
    return 'both'
  }
  return 'left_to_right'
}

export function getCrossLineDirectionButtonText(direction) {
  return `切换方向: ${getLineDirectionLabel(normalizeLineDirection(direction))}`
}

export function normalizeRegionPrimaryState(regions, preferredPrimaryRegionId = '') {
  const normalizedRegions = (regions || []).map(region => ({
    ...region,
    closed: Boolean(region.closed),
    points: normalizePointList(region.points, 0)
  }))
  const currentPrimaryRegion = normalizedRegions.find(region => region.primary)
  const fallbackPrimaryRegion = normalizedRegions.find(region => region.id === 'region_primary') || normalizedRegions[0] || null
  const primaryRegionId =
    preferredPrimaryRegionId ||
    (currentPrimaryRegion ? currentPrimaryRegion.id : '') ||
    (fallbackPrimaryRegion ? fallbackPrimaryRegion.id : '')

  return normalizedRegions.map(region => ({
    ...region,
    primary: region.id === primaryRegionId
  }))
}

export function drawCanvasTextLabel(ctx, text, x, y, color) {
  if (!ctx || !text) {
    return
  }
  ctx.save()
  ctx.font = '12px sans-serif'
  const textWidth = ctx.measureText(text).width
  const paddingX = 6
  const labelHeight = 18
  const labelX = x
  const labelY = Math.max(0, y - labelHeight + 2)
  ctx.fillStyle = 'rgba(15, 17, 21, 0.68)'
  ctx.fillRect(labelX - 2, labelY, textWidth + paddingX * 2, labelHeight)
  ctx.fillStyle = color
  ctx.fillText(text, labelX + paddingX - 2, labelY + 3)
  ctx.restore()
}

export function drawCanvasLineArrow(ctx, startPoint, endPoint, color, lineWidth = 2) {
  if (!ctx || !startPoint || !endPoint) {
    return
  }
  const dx = Number(endPoint.x) - Number(startPoint.x)
  const dy = Number(endPoint.y) - Number(startPoint.y)
  const length = Math.sqrt(dx * dx + dy * dy)
  if (!Number.isFinite(length) || length < 12) {
    return
  }

  const angle = Math.atan2(dy, dx)
  const arrowSize = Math.max(8, Math.min(14, lineWidth * 4))
  const arrowAngle = Math.PI / 7
  const tipX = endPoint.x
  const tipY = endPoint.y
  const leftX = tipX - arrowSize * Math.cos(angle - arrowAngle)
  const leftY = tipY - arrowSize * Math.sin(angle - arrowAngle)
  const rightX = tipX - arrowSize * Math.cos(angle + arrowAngle)
  const rightY = tipY - arrowSize * Math.sin(angle + arrowAngle)

  ctx.save()
  ctx.fillStyle = color
  ctx.beginPath()
  ctx.moveTo(tipX, tipY)
  ctx.lineTo(leftX, leftY)
  ctx.lineTo(rightX, rightY)
  ctx.closePath()
  ctx.fill()
  ctx.restore()
}

export function drawCanvasCrossLineDirectionIndicator(ctx, startPoint, endPoint, directions, color, lineWidth = 2) {
  if (!ctx || !startPoint || !endPoint || !Array.isArray(directions) || !directions.length) {
    return
  }
  const dx = Number(endPoint.x) - Number(startPoint.x)
  const dy = Number(endPoint.y) - Number(startPoint.y)
  const length = Math.sqrt(dx * dx + dy * dy)
  if (!Number.isFinite(length) || length < 16) {
    return
  }

  const normalX = -dy / length
  const normalY = dx / length
  const midX = (Number(startPoint.x) + Number(endPoint.x)) / 2
  const midY = (Number(startPoint.y) + Number(endPoint.y)) / 2
  const offset = Math.max(14, Math.min(24, length * 0.18))
  const arrowLength = Math.max(18, Math.min(30, length * 0.28))

  directions.forEach(direction => {
    const isLeftToRight = direction === 'left_to_right'
    const start = isLeftToRight
      ? { x: midX + normalX * offset, y: midY + normalY * offset }
      : { x: midX - normalX * offset, y: midY - normalY * offset }
    const end = isLeftToRight
      ? { x: midX - normalX * offset, y: midY - normalY * offset }
      : { x: midX + normalX * offset, y: midY + normalY * offset }
    const unitX = (end.x - start.x) / (Math.sqrt((end.x - start.x) * (end.x - start.x) + (end.y - start.y) * (end.y - start.y)) || 1)
    const unitY = (end.y - start.y) / (Math.sqrt((end.x - start.x) * (end.x - start.x) + (end.y - start.y) * (end.y - start.y)) || 1)
    const shortenedEnd = {
      x: start.x + unitX * arrowLength,
      y: start.y + unitY * arrowLength
    }

    ctx.save()
    ctx.strokeStyle = color
    ctx.lineWidth = Math.max(2, lineWidth)
    ctx.beginPath()
    ctx.moveTo(start.x, start.y)
    ctx.lineTo(shortenedEnd.x, shortenedEnd.y)
    ctx.stroke()
    ctx.restore()

    drawCanvasLineArrow(ctx, start, shortenedEnd, color, Math.max(2, lineWidth))
  })
}
