import { Message, Notification } from 'element-ui'

const MESSAGE_DEFAULTS = {
  duration: 3000,
  offset: 56,
  showClose: true,
  customClass: 'sva-message'
}

const NOTIFY_DEFAULTS = {
  duration: 3000,
  offset: 56
}

const DEFAULT_DURATION = 3000
const MAX_DURATION = 8000

let messageSeq = 0

function clampDuration(duration) {
  const value = Number(duration)
  if (!Number.isFinite(value) || value <= 0) {
    return DEFAULT_DURATION
  }
  return Math.min(value, MAX_DURATION)
}

function purgeMessageDom(keepEl) {
  document.querySelectorAll('body > .el-message').forEach(el => {
    if (keepEl && el === keepEl) {
      return
    }
    if (el.parentNode) {
      el.parentNode.removeChild(el)
    }
  })
}

function normalize(options, type) {
  const source = (typeof options === 'string' || typeof options === 'number')
    ? { message: String(options) }
    : (options && typeof options === 'object' ? options : { message: String(options || '') })
  const customClass = [MESSAGE_DEFAULTS.customClass, source.customClass].filter(Boolean).join(' ')
  return {
    ...MESSAGE_DEFAULTS,
    ...source,
    customClass,
    duration: clampDuration(source.duration != null ? source.duration : MESSAGE_DEFAULTS.duration),
    ...(type ? { type } : {})
  }
}

export function showMessage(options) {
  const seq = (messageSeq += 1)
  Message.closeAll()
  purgeMessageDom()
  const opts = normalize(options)
  const instance = Message(opts)
  const keepEl = instance && instance.$el
  window.setTimeout(() => {
    if (seq !== messageSeq) {
      return
    }
    purgeMessageDom(keepEl)
  }, 50)
  window.setTimeout(() => {
    if (seq !== messageSeq) {
      return
    }
    purgeMessageDom()
  }, opts.duration + 400)
  return instance
}

;['success', 'warning', 'info', 'error'].forEach(type => {
  showMessage[type] = (options) => showMessage(normalize(options, type))
})

showMessage.closeAll = () => {
  messageSeq += 1
  Message.closeAll()
  purgeMessageDom()
}

export function showNotify(options) {
  if (typeof options === 'string') {
    return Notification({ ...NOTIFY_DEFAULTS, title: options })
  }
  return Notification({ ...NOTIFY_DEFAULTS, ...options })
}

;['success', 'warning', 'info', 'error'].forEach(type => {
  showNotify[type] = (options) => {
    if (typeof options === 'string') {
      return Notification({ ...NOTIFY_DEFAULTS, type, title: options })
    }
    return Notification({ ...NOTIFY_DEFAULTS, type, ...options })
  }
})
