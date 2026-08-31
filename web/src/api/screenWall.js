import request from '@/utils/request'

function pickValue(source, keys, defaultValue) {
  if (!source) {
    return defaultValue
  }
  for (let i = 0; i < keys.length; i += 1) {
    const key = keys[i]
    if (source[key] !== undefined && source[key] !== null && source[key] !== '') {
      return source[key]
    }
  }
  return defaultValue
}

function toBoolean(value, defaultValue = false) {
  if (value === undefined || value === null || value === '') {
    return defaultValue
  }
  if (typeof value === 'boolean') {
    return value
  }
  if (typeof value === 'number') {
    return value !== 0
  }
  if (typeof value === 'string') {
    const normalized = value.trim().toLowerCase()
    if (['true', '1', 'yes', 'y'].includes(normalized)) {
      return true
    }
    if (['false', '0', 'no', 'n'].includes(normalized)) {
      return false
    }
  }
  return Boolean(value)
}

function toEnabledInt(value, defaultValue = 1) {
  return toBoolean(value, defaultValue !== 0) ? 1 : 0
}

export function buildScreenWallUpsertPayload(source = {}) {
  const slotIndexRaw = pickValue(source, ['slotIndex', 'slot_index'], null)
  const slotIndex = slotIndexRaw === null || slotIndexRaw === '' || slotIndexRaw === undefined
    ? null
    : Number(slotIndexRaw)

  return {
    wallCode: pickValue(source, ['wallCode', 'wall_code'], 'main') || 'main',
    sourceType: pickValue(source, ['sourceType', 'source_type'], ''),
    sourceId: pickValue(source, ['sourceId', 'source_id', 'id'], ''),
    deviceId: pickValue(source, ['deviceId', 'device_id', 'apeId', 'ape_id'], ''),
    playUrl: pickValue(source, ['playUrl', 'play_url', 'previewUrl', 'preview_url', 'streamUrl', 'stream_url'], ''),
    title: pickValue(source, ['title', 'name', 'deviceName', 'device_name'], ''),
    slotIndex: Number.isFinite(slotIndex) ? slotIndex : null,
    enabled: toEnabledInt(pickValue(source, ['enabled'], 1), 1),
    taskPushEnabled: toBoolean(pickValue(source, ['taskPushEnabled', 'task_push_enabled', 'pushEnabled', 'push_enabled'], false), false),
    algorithmStreamUrl: pickValue(source, ['algorithmStreamUrl', 'algorithm_stream_url'], '')
  }
}

export function normalizeScreenWallStream(source = {}) {
  const normalized = buildScreenWallUpsertPayload({
    ...source,
    sourceId: pickValue(source, ['sourceId', 'source_id', 'id', 'streamId', 'stream_id'], ''),
    wallCode: pickValue(source, ['wallCode', 'wall_code'], 'main') || 'main'
  })
  return {
    ...normalized,
    id: pickValue(source, ['id', 'streamId', 'stream_id'], ''),
    title: pickValue(source, ['title', 'name', 'deviceName', 'device_name', 'taskName', 'task_name'], normalized.title)
  }
}

export function upsertScreenWallStream(data) {
  return request({
    url: '/screen-wall/streams/upsert',
    method: 'post',
    data: buildScreenWallUpsertPayload(data)
  })
}

export function getScreenWallStreams(wallCode = 'main') {
  return request({
    url: '/screen-wall/streams',
    method: 'get',
    params: { wallCode: wallCode || 'main' }
  })
}

export function deleteScreenWallStream(id) {
  return request({
    url: `/screen-wall/streams/${id}`,
    method: 'delete'
  })
}
