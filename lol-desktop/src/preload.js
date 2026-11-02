'use strict'

/**
 * 页面与本机能力之间的唯一通道。
 *
 * 只暴露具名方法，不把 ipcRenderer / require 交给页面 —— 页面是从服务器加载的，
 * 哪天服务端被换了内容，也不能让它顺手在本机为所欲为。
 */

const { contextBridge, ipcRenderer } = require('electron')

/** 页面登录态存在自己 origin 的 localStorage 里，preload 同源可读。 */
function readToken() {
  try {
    return window.localStorage.getItem('token') || ''
  } catch {
    return ''
  }
}

contextBridge.exposeInMainWorld('lolDesktop', {
  /** 页面用它判断「我是在桌面端还是浏览器里」。浏览器里是 undefined。 */
  isDesktop: true,

  getConfig: () => ipcRenderer.invoke('config:get'),
  setConfig: patch => ipcRenderer.invoke('config:set', patch),

  /** 设置页用：由主进程代为探测服务器是否可达（file:// 页面自己做会撞 CORS）。 */
  probeServer: url => ipcRenderer.invoke('server:probe', url),
  gotoServer: () => ipcRenderer.invoke('app:goto-server'),
  reload: () => ipcRenderer.invoke('app:reload'),

  probeLcu: () => ipcRenderer.invoke('lcu:probe'),

  /** 读本机 LCU 并上报。返回 { ok, data|message }，形状与网页版 /match/sync 对齐。 */
  syncMatches: (opts = {}) =>
    ipcRenderer.invoke('lcu:sync', {
      ...opts,
      token: opts.token || readToken(),
    }),

  /**
   * 录入过程中的进度文字。返回取消订阅函数，组件卸载时调用，避免重复注册。
   */
  onSyncProgress: callback => {
    const handler = (_event, text) => callback(text)
    ipcRenderer.on('lcu:progress', handler)
    return () => ipcRenderer.removeListener('lcu:progress', handler)
  },
})
