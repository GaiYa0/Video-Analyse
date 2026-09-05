import flvjs from 'flv.js'

export function isFlvUrl(url) {
  return /\.flv($|[?#])/i.test(url || '')
}

export function isHttpOrWsUrl(url) {
  return /^(https?:\/\/|wss?:\/\/)/i.test(url || '')
}

export function isFlvSupported() {
  return flvjs.isSupported()
}

export function canPlayHttpFlv(url) {
  return isFlvUrl(url) && isHttpOrWsUrl(url) && isFlvSupported()
}

const LIVE_FLV_CONFIG = {
  enableStashBuffer: false,
  stashInitialSize: 128,
  autoCleanupSourceBuffer: true
}

export function createLiveFlvPlayer(url, extraConfig) {
  // flv.js: 第一参是 MediaDataSource，第二参才是 Config。
  // 以前把 enableStashBuffer 塞进第一参，直播缓冲策略从未生效。
  return flvjs.createPlayer({
    type: 'flv',
    url,
    isLive: true
  }, Object.assign({}, LIVE_FLV_CONFIG, extraConfig || {}))
}

const STALL_CHECK_MS = 1000
const NUDGE_AFTER_TICKS = 3
const RELOAD_AFTER_TICKS = 8
const MIN_RELOAD_INTERVAL_MS = 5000

const liveWatchdogs = new WeakMap()

function safePlay(videoElement) {
  const ret = videoElement.play()
  if (ret && typeof ret.catch === 'function') {
    ret.catch(() => {})
  }
}

function stopLiveWatchdog(player) {
  const stop = liveWatchdogs.get(player)
  if (stop) {
    stop()
    liveWatchdogs.delete(player)
  }
}

// 只按 currentTime 卡顿重拉。不要订 ERROR：unload 自己会抛，再 reload 会把已销毁的播放器救活。
function startLiveWatchdog(player, videoElement) {
  stopLiveWatchdog(player)

  let dead = false
  let lastTime = -1
  let stalledTicks = 0
  let lastReloadAt = 0

  const reload = () => {
    if (dead || document.hidden) {
      return
    }
    const now = Date.now()
    if (now - lastReloadAt < MIN_RELOAD_INTERVAL_MS) {
      return
    }
    lastReloadAt = now
    stalledTicks = 0
    try {
      player.unload()
      player.load()
      safePlay(videoElement)
    } catch (e) {
      stopLiveWatchdog(player)
    }
  }

  const timer = setInterval(() => {
    if (dead) {
      return
    }
    if (!videoElement.isConnected) {
      stopLiveWatchdog(player)
      return
    }
    if (videoElement.ended) {
      reload()
      return
    }
    if (document.hidden || videoElement.paused) {
      stalledTicks = 0
      lastTime = videoElement.currentTime
      return
    }
    const current = videoElement.currentTime
    if (Math.abs(current - lastTime) < 0.02) {
      stalledTicks += 1
      if (stalledTicks === NUDGE_AFTER_TICKS) {
        safePlay(videoElement)
      } else if (stalledTicks >= RELOAD_AFTER_TICKS) {
        reload()
      }
    } else {
      stalledTicks = 0
    }
    lastTime = current
  }, STALL_CHECK_MS)

  liveWatchdogs.set(player, () => {
    dead = true
    clearInterval(timer)
  })
}

export function attachFlvPlayer(player, videoElement) {
  player.attachMediaElement(videoElement)
  player.load()
  // 直播时长为 0，loop 会让播放器 seek 回 0；开头的缓冲早被 flv.js 丢掉，
  // 画面就永久停在最后一帧。任何直播 video 都不能带 loop。
  videoElement.loop = false
  videoElement.removeAttribute('loop')
  videoElement.playsInline = true
  startLiveWatchdog(player, videoElement)
}

export function playHttpFlv(videoElement, url) {
  if (!videoElement || !url || !canPlayHttpFlv(url)) {
    return null
  }
  const player = createLiveFlvPlayer(url)
  attachFlvPlayer(player, videoElement)
  return player
}

export function destroyFlvPlayer(player) {
  if (!player) {
    return
  }
  stopLiveWatchdog(player)
  try {
    player.unload()
    player.detachMediaElement()
    player.destroy()
  } catch (e) {
    // 卸流失败不能挡住监控墙切格子
  }
}

export function resetVideoElement(videoElement) {
  if (!videoElement) {
    return
  }
  videoElement.pause()
  videoElement.removeAttribute('src')
  videoElement.load()
}

export function applyContainStyle(videoElement) {
  if (!videoElement) {
    return
  }
  videoElement.style.objectFit = 'contain'
  videoElement.style.objectPosition = 'center center'
  videoElement.style.backgroundColor = '#000'
  videoElement.style.width = 'auto'
  videoElement.style.height = 'auto'
  videoElement.style.maxWidth = '100%'
  videoElement.style.maxHeight = '100%'
  videoElement.style.display = 'block'
}
