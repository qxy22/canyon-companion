'use strict'

/**
 * 桌面端本地配置。
 *
 * 存在 %APPDATA%/峡谷同行/config.json，只放两样东西：
 *  - serverBase：服务端地址（内置了默认值，装完直接用，一般不用改）
 *  - lcuInstallDir：游戏安装目录（自动发现失败时才需要手填）
 *
 * 登录态不在这里 —— 那是网页自己的 localStorage，桌面端不碰。
 */

const { app } = require('electron')
const fs = require('fs')
const path = require('path')
const { normalizeServerBase, DEFAULT_SERVER_BASE } = require('./server-url')

/**
 * 默认服务端地址写在 server-url.js 里（纯模块，可单测）。
 * 内置而不是让用户填，是为了「装完双击就能用」——桌面端只是把线上看板装进窗口，
 * 目标服务器只有一个。地址并不写死在页面：导航白名单、探活、战绩上报都从配置读，
 * 换服务器走菜单「设置服务器地址…」即可，无需重新打包。
 */
const DEFAULTS = {
  serverBase: DEFAULT_SERVER_BASE,
  lcuInstallDir: '',
}

let cache = null

function configPath() {
  return path.join(app.getPath('userData'), 'config.json')
}

function load() {
  if (cache) return cache
  let disk = {}
  try {
    disk = JSON.parse(fs.readFileSync(configPath(), 'utf8')) || {}
  } catch {
    // 首次启动、文件损坏都走默认值，不弹错
  }
  cache = { ...DEFAULTS, ...disk }
  // 旧版本可能存过空串（那会儿地址要手填）。空值一律回落到默认地址，
  // 否则老用户升级后还会卡在设置页 —— 磁盘上有 "" 会盖掉 DEFAULTS。
  if (!cache.serverBase) cache.serverBase = DEFAULT_SERVER_BASE
  return cache
}

function get() {
  const cfg = load()
  return { ...cfg, configFile: configPath() }
}

function set(patch) {
  const cfg = load()
  const next = { ...cfg }

  if (patch && patch.serverBase !== undefined) {
    // 清空不给存 —— 没有地址这个应用什么也做不了，回落默认值比存空更好
    next.serverBase = normalizeServerBase(patch.serverBase) || DEFAULT_SERVER_BASE
  }
  if (patch && patch.lcuInstallDir !== undefined) {
    next.lcuInstallDir = String(patch.lcuInstallDir || '').trim()
  }

  cache = next
  try {
    fs.mkdirSync(path.dirname(configPath()), { recursive: true })
    fs.writeFileSync(configPath(), JSON.stringify(next, null, 2), 'utf8')
  } catch (e) {
    // 写不进去只影响下次启动，当前会话照常可用
    console.error('保存配置失败：', e.message)
  }
  return get()
}

module.exports = { get, set, normalizeServerBase, configPath }
