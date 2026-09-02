export const BEHAVIOR_TYPE_OPTIONS = [
  { value: 'cross_line', label: '跨线' },
  { value: 'enter_region', label: '进区' },
  { value: 'exit_region', label: '出区' },
  { value: 'dwell', label: '停留' },
  { value: 'low_speed', label: '低速' },
  { value: 'loitering', label: '徘徊' },
  { value: 'sleep_on_duty', label: '睡岗' },
  { value: 'sleep', label: '睡觉' },
  { value: 'absence', label: '缺席' },
  { value: 'count_threshold', label: '数量阈值' },
  { value: 'occupancy', label: '占用' },
  { value: 'region_motion', label: '区域运动' },
  { value: 'direction_move', label: '定向通行' },
  { value: 'direction_reverse', label: '逆向通行' },
  { value: 'relation_near', label: '目标接近' },
  { value: 'relation_apart', label: '目标远离' },
  { value: 'relation_not_contains', label: '目标未包含' }
]

export const BEHAVIOR_TYPE_VALUES = BEHAVIOR_TYPE_OPTIONS.map(item => item.value)

const SLEEP_ON_DUTY_ALIASES = ['SLEEP_ON_DUTY', 'sleep_on_duty', '睡岗']
const SLEEP_HEURISTIC_ALIASES = ['sleep', '睡觉']

export function normalizeBehaviorType(value) {
  if (BEHAVIOR_TYPE_VALUES.includes(value)) {
    return value
  }
  return ''
}

export function isKnownBehaviorType(behaviorType) {
  if (behaviorType === undefined || behaviorType === null || behaviorType === '') {
    return false
  }
  if (BEHAVIOR_TYPE_VALUES.includes(behaviorType)) {
    return true
  }
  const raw = String(behaviorType).trim()
  return BEHAVIOR_TYPE_VALUES.includes(raw)
    || SLEEP_ON_DUTY_ALIASES.indexOf(raw) !== -1
    || SLEEP_HEURISTIC_ALIASES.indexOf(raw) !== -1
}

export function getBehaviorTypeLabel(behaviorType) {
  if (behaviorType === undefined || behaviorType === null || behaviorType === '') {
    return ''
  }
  const raw = String(behaviorType).trim()
  if (SLEEP_ON_DUTY_ALIASES.indexOf(raw) !== -1) {
    return '睡岗'
  }
  if (SLEEP_HEURISTIC_ALIASES.indexOf(raw) !== -1) {
    return '睡觉'
  }
  const matched = BEHAVIOR_TYPE_OPTIONS.find(item => item.value === raw)
  return matched ? matched.label : raw
}
