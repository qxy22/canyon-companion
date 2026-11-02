import { ref } from 'vue'

/**
 * 全局通知长连接：好友请求、组队邀请、被踢提醒。
 *
 * 它是**单例**，不是每个组件一条：整页只需要一条连接，谁想看通知就订阅事件。
 * 和房间聊天那条连接（useChatSocket）是分开的 —— 那条绑着具体队伍，
 * 退队就该断；而邀请、被踢这些事跟"当前在哪个房间"无关。
 *
 * 断线重连、心跳、指数退避都照房间那套来，理由也一样：
 * 网络抖一下就永久收不到提醒，比没有提醒更让人困惑。
 */

const wsBase = () =>
  `${location.protocol === 'https:' ? 'wss:' : 'ws:'}//${location.host}/ws/notify`

const HEARTBEAT_MS = 30_000
const MAX_RETRY_MS = 30_000

export const notifyState = ref('idle') // idle | connecting | open | closed

/** 订阅者：收到任何一条服务端推送都会调用 */
const listeners = new Set()

let socket = null
let retryDelay = 1000
let retryTimer = null
let heartbeatTimer = null
let stopped = true

export function onNotify(fn) {
  listeners.add(fn)
  return () => listeners.delete(fn)
}

/** 登录后调用。可以重复调用，已经连着就什么都不做。 */
export function connectNotify() {
  stopped = false
  open()
}

/** 登出 / 卸载时调用，之后不再重连 */
export function disconnectNotify() {
  stopped = true
  stopHeartbeat()
  if (retryTimer) {
    clearTimeout(retryTimer)
    retryTimer = null
  }
  if (socket && socket.readyState <= WebSocket.OPEN) {
    try {
      socket.close()
    } catch {
      // 已经断了
    }
  }
  socket = null
  notifyState.value = 'closed'
}

function open() {
  if (stopped || notifyState.value === 'open' || notifyState.value === 'connecting') return

  const token = localStorage.getItem('token')
  if (!token) {
    notifyState.value = 'closed'
    return
  }

  notifyState.value = 'connecting'
  try {
    socket = new WebSocket(`${wsBase()}?token=${encodeURIComponent(token)}`)
  } catch {
    notifyState.value = 'closed'
    scheduleRetry()
    return
  }

  socket.onopen = () => {
    retryDelay = 1000
    notifyState.value = 'open'
    startHeartbeat()
  }

  socket.onmessage = event => {
    let payload
    try {
      payload = JSON.parse(event.data)
    } catch {
      return
    }
    if (!payload || !payload.event || payload.event === 'pong') return
    // 一个订阅者抛异常不能影响其他订阅者
    for (const fn of listeners) {
      try {
        fn(payload)
      } catch {
        // 忽略
      }
    }
  }

  socket.onerror = () => {
    // 拿不到原因，交给 onclose 统一重连
  }

  socket.onclose = () => {
    stopHeartbeat()
    notifyState.value = 'closed'
    socket = null
    scheduleRetry()
  }
}

function scheduleRetry() {
  if (stopped || retryTimer) return
  retryTimer = setTimeout(() => {
    retryTimer = null
    open()
  }, retryDelay)
  retryDelay = Math.min(retryDelay * 2, MAX_RETRY_MS)
}

function startHeartbeat() {
  stopHeartbeat()
  heartbeatTimer = setInterval(() => {
    if (socket && socket.readyState === WebSocket.OPEN) {
      socket.send(JSON.stringify({ type: 'PING' }))
    }
  }, HEARTBEAT_MS)
}

function stopHeartbeat() {
  if (heartbeatTimer) {
    clearInterval(heartbeatTimer)
    heartbeatTimer = null
  }
}
