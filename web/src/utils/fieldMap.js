/** Read camelCase or snake_case. Empty string is a valid value. */
export function getFieldValue(source, ...keys) {
  if (!source) {
    return undefined
  }
  for (let i = 0; i < keys.length; i += 1) {
    const key = keys[i]
    if (source[key] !== undefined && source[key] !== null) {
      return source[key]
    }
  }
  return undefined
}

/** Same as getFieldValue, but skip empty string (used by screen-wall payload). */
export function pickValue(source, keys, defaultValue) {
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
