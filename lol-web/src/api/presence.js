import request from './request'

/**
 * 在线状态自动维护（进站在线 / 关页离线）。
 *
 * 和 updateStatus 的分工：那个是用户手动"宣布"自己是什么状态，
 * 这里表达的是"我的页面还开着"。心跳不会覆盖手动选的「游戏中」。
 *
 * 三个请求都用 silent：在线状态是后台维护的，失败不该弹红条打扰用户，
 * 网络抖一下就弹一次错会让页面看起来坏掉了。
 */

// 打开网页 / 登录成功：算在线
export function goOnline() {
  return request.post('/presence/online', null, { silent: true })
}

// 退出登录：算离线
export function goOffline() {
  return request.post('/presence/offline', null, { silent: true })
}

// 心跳：只刷新活跃时间，返回 { online } 表示是否还在在线索引里
export function heartbeat() {
  return request.post('/presence/heartbeat', null, { silent: true })
}

/**
 * 页面卸载时的离线通知。
 *
 * 走原生 fetch 而不是 axios：axios 基于 XHR，带不了 keepalive；
 * 而 keepalive 正是"页面已经关了，这个请求还得发出去"的开关。
 * 发不出去也不会有人看到错误 —— 服务端 90 秒没收到心跳会自己兜底。
 */
export function goOfflineOnUnload() {
  const token = localStorage.getItem('token')
  if (!token) return
  try {
    fetch('/api/presence/offline', {
      method: 'POST',
      keepalive: true,
      headers: { Authorization: 'Bearer ' + token }
    })
  } catch {
    // 卸载路径上不允许抛错
  }
}
