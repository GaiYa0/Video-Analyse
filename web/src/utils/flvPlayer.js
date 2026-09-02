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

export function createLiveFlvPlayer(url, extraConfig) {
  return flvjs.createPlayer(Object.assign({
    type: 'flv',
    url,
    isLive: true
  }, extraConfig || {}))
}

export function attachFlvPlayer(player, videoElement) {
  player.attachMediaElement(videoElement)
  player.load()
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
  player.unload()
  player.detachMediaElement()
  player.destroy()
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
