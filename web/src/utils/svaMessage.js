import { Message, Notification } from 'element-ui'

const MESSAGE_DEFAULTS = {
  duration: 3000,
  offset: 56,
  showClose: true
}

const NOTIFY_DEFAULTS = {
  duration: 3000,
  offset: 56
}

function normalize(options, type) {
  if (typeof options === 'string' || typeof options === 'number') {
    return { ...MESSAGE_DEFAULTS, type, message: String(options) }
  }
  return { ...MESSAGE_DEFAULTS, type, ...options }
}

export function showMessage(options) {
  Message.closeAll()
  return Message(normalize(options))
}

;['success', 'warning', 'info', 'error'].forEach(type => {
  showMessage[type] = (options) => showMessage(normalize(options, type))
})

showMessage.closeAll = () => Message.closeAll()

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
