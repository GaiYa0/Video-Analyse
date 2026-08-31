import { getConfigKey } from '@/api/system/config'

export const OVERLAY_DELAY_CONFIG_KEY = 'sva.overlay.delay.ms'
export const OVERLAY_DELAY_DEFAULT_MS = 500
export const OVERLAY_DELAY_MAX_MS = 5000

export function extractConfigResponseValue(response) {
  if (response === undefined || response === null) {
    return ''
  }
  if (typeof response === 'string' || typeof response === 'number' || typeof response === 'boolean') {
    return response
  }
  if (response.data !== undefined && response.data !== null && response.data !== '') {
    return response.data
  }
  if (response.msg !== undefined && response.msg !== null && response.msg !== '') {
    return response.msg
  }
  return ''
}

export function normalizeOverlayDelayMs(value, fallbackValue = OVERLAY_DELAY_DEFAULT_MS) {
  const fallback = Number.isFinite(Number(fallbackValue)) ? Math.max(0, Number(fallbackValue)) : OVERLAY_DELAY_DEFAULT_MS
  const parsed = Number.parseInt(String(value === undefined || value === null ? '' : value).trim(), 10)
  if (!Number.isFinite(parsed) || parsed < 0) {
    return fallback
  }
  return Math.min(parsed, OVERLAY_DELAY_MAX_MS)
}

export async function loadOverlayDelayMs(fallbackValue = OVERLAY_DELAY_DEFAULT_MS) {
  try {
    const response = await getConfigKey(OVERLAY_DELAY_CONFIG_KEY)
    return normalizeOverlayDelayMs(extractConfigResponseValue(response), fallbackValue)
  } catch (error) {
    return normalizeOverlayDelayMs(fallbackValue)
  }
}