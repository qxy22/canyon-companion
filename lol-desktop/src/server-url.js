'use strict'

/**
 * 服务器地址规整。
 *
 * 单独成文件是为了能脱离 Electron 单测 —— store.js 里有 app.getPath()，
 * 一 require 就得起整个运行时。
 *
 * 要容忍用户的各种填法：
 *   1.2.3.4            → http://1.2.3.4
 *   1.2.3.4/           → http://1.2.3.4
 *   http://1.2.3.4:8080/ → http://1.2.3.4:8080
 *   https://a.com/     → https://a.com
 *   " 1.2.3.4 "        → http://1.2.3.4
 *   乱码                → ''
 */

function normalizeServerBase(raw) {
  let s = String(raw || '').trim()
  if (!s) return ''
  s = s.replace(/\s+/g, '')
  if (!/^https?:\/\//i.test(s)) s = 'http://' + s

  try {
    const u = new URL(s)
    if (!u.hostname) return ''
    // 默认 80 在 origin 里会被省略，这正是我们要的样子
    return u.origin
  } catch {
    return ''
  }
}

/**
 * 内置的默认服务器地址 —— 装完打开就能用，不需要用户手填。
 *
 * 必须是 nginx 的对外地址（80/443）。后端 8080 只监听服务器的回环地址、
 * 安全组也没开，填它别人电脑上必然连不上。
 *
 * 放这个文件而不是 store.js，是为了能脱离 Electron 单测（store 一 require
 * 就要 app.getPath()）。换服务器改这一行重新打包即可；已装用户走菜单改。
 */
const DEFAULT_SERVER_BASE = 'http://47.110.138.40'

module.exports = { normalizeServerBase, DEFAULT_SERVER_BASE }
