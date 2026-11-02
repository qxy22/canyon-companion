import { ref } from 'vue'

/**
 * 房间聊天的长连接。
 *
 * 三件事是这个封装存在的理由：
 * 1. 断线重连 —— 网络抖一下就再也收不到消息，聊天就直接废了。
 * 2. 心跳 —— 长时间不说话的连接会被中间层悄悄掐掉。
 * 3. 服务端主动关闭时不再重连 —— 被踢出队伍、队伍解散之后，再连也连不上，
 *    只会陷入「连上又被关」的死循环。
 */

// 跟页面同一个 host，开发环境由 Vite 代理到后端
const wsBase = () =>
  `${location.protocol === 'https:' ? 'wss:' : 'ws:'}//${location.host}/ws/chat`

const HEARTBEAT_MS = 30_000
const MAX_RETRY_MS = 30_000

export function createChatSocket(teamupId, handlers = {}) {
  const state = ref('idle') // idle | connecting | open | closed

  let socket = null
  let retryDelay = 1000
  let retryTimer = null
  let heartbeatTimer = null
  let stopped = false // 服务端要求关闭 / 主动 close 之后不再重连

  function open() {
    if (stopped || state.value === 'open' || state.value === 'connecting') return

    const token = localStorage.getItem('token')
    if (!token) {
      state.value = 'closed'
      handlers.onFatal?.('登录状态已失效，请重新登录')
      return
    }

    state.value = 'connecting'
    try {
      socket = new WebSocket(
        `${wsBase()}?token=${encodeURIComponent(token)}&teamupId=${encodeURIComponent(teamupId)}`
      )
    } catch (e) {
      state.value = 'closed'
      scheduleRetry()
      return
    }

    socket.onopen = () => {
      retryDelay = 1000
      state.value = 'open'
      startHeartbeat()
      handlers.onOpen?.()
    }

    socket.onmessage = event => {
      let payload
      try {
        payload = JSON.parse(event.data)
      } catch {
        return
      }
      switch (payload.event) {
        case 'message':
          handlers.onMessage?.(payload.data)
          break
        case 'notice':
          handlers.onNotice?.(payload.message)
          break
        case 'pong':
          break
        case 'error':
          handlers.onError?.(payload.message)
          break
        case 'closed':
          // 被踢出队伍 / 队伍解散。这里必须停下来，否则会一直重连一直被拒
          stopped = true
          handlers.onClosed?.(payload.message || '你已不在该队伍里')
          break
        default:
          break
      }
    }

    socket.onerror = () => {
      // 具体原因拿不到，交给 onclose 统一处理重连
    }

    socket.onclose = () => {
      stopHeartbeat()
      state.value = 'closed'
      if (!stopped) scheduleRetry()
    }
  }

  function scheduleRetry() {
    if (stopped || retryTimer) return
    retryTimer = setTimeout(() => {
      retryTimer = null
      open()
    }, retryDelay)
    // 指数退避，最长 30 秒，别把后端打爆
    retryDelay = Math.min(retryDelay * 2, MAX_RETRY_MS)
  }

  function startHeartbeat() {
    stopHeartbeat()
    heartbeatTimer = setInterval(() => {
      sendRaw({ type: 'PING' })
    }, HEARTBEAT_MS)
  }

  function stopHeartbeat() {
    if (heartbeatTimer) {
      clearInterval(heartbeatTimer)
      heartbeatTimer = null
    }
  }

  function sendRaw(payload) {
    if (socket && socket.readyState === WebSocket.OPEN) {
      socket.send(JSON.stringify(payload))
      return true
    }
    return false
  }

  /** 发一条消息。type 由调用方给：TEXT / IMAGE / VIDEO / STICKER */
  function send(payload) {
    if (state.value !== 'open') {
      handlers.onError?.('连接还没建立，等一下再发')
      return false
    }
    return sendRaw(payload)
  }

  /** 主动断开（离开页面时用）。服务端发起的关闭不会走这里。 */
  function close() {
    stopped = true
    stopHeartbeat()
    if (retryTimer) {
      clearTimeout(retryTimer)
      retryTimer = null
    }
    if (socket && socket.readyState <= WebSocket.OPEN) {
      socket.close()
    }
    socket = null
    state.value = 'closed'
  }

  return { state, open, send, close }
}
