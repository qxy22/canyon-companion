'use strict'

/**
 * 峡谷同行 桌面端主进程。
 *
 * 定位：这个应用不是把整个系统塞进安装包，而是「一个连着你服务器的窗口 + 一双能读本机
 * 游戏客户端的手」。页面照旧从服务器加载，所以好友、组队、聊天、在线状态全都走原来的
 * 后端，一行都不用改；唯一必须在本机做的是「录入战绩」—— 后端在云上，读不到玩家电脑上
 * 的 LCU。
 *
 * 安全边界：
 *  - 页面只能导航到配置里的那个 origin（外加本地的设置页/错误页）；
 *  - 外链交给系统浏览器，不在应用内开窗；
 *  - preload 只暴露 5 个具名方法，不把 ipcRenderer 整个丢给页面。
 */

const { app, BrowserWindow, ipcMain, Menu, shell, dialog } = require('electron')
const path = require('path')
const fs = require('fs')

const store = require('./store')
const lcu = require('./lcu')

const APP_TITLE = '峡谷同行'

let mainWindow = null
/** 录入战绩是独占操作：正在读的时候再点一次，直接回「正在录入中」。 */
let syncing = false

// ------------------------------------------------------------------ 窗口

function allowedOrigin() {
  const { serverBase } = store.get()
  try {
    return serverBase ? new URL(serverBase).origin : null
  } catch {
    return null
  }
}

function createWindow() {
  mainWindow = new BrowserWindow({
    width: 1320,
    height: 880,
    minWidth: 1000,
    minHeight: 640,
    title: APP_TITLE,
    backgroundColor: '#0A1428',
    icon: path.join(__dirname, '..', 'assets', 'icon.ico'),
    show: false,
    autoHideMenuBar: false,
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: false,
      // 页面要发 ws:// 到后端，默认策略允许，这里不动
    },
  })

  // 先隐藏再显示，避免白底闪一下
  mainWindow.once('ready-to-show', () => mainWindow.show())

  bindNavigationGuards(mainWindow)
  bindLoadFailureFallback(mainWindow)

  loadApp(mainWindow)

  mainWindow.on('closed', () => {
    mainWindow = null
  })
}

function loadApp(win) {
  const { serverBase } = store.get()
  if (!serverBase) {
    win.loadFile(path.join(__dirname, 'pages', 'setup.html'))
    return
  }
  win.loadURL(serverBase)
}

function bindNavigationGuards(win) {
  const wc = win.webContents

  wc.on('will-navigate', (event, url) => {
    if (url.startsWith('file://')) return
    const origin = allowedOrigin()
    try {
      if (!origin || new URL(url).origin !== origin) event.preventDefault()
    } catch {
      event.preventDefault()
    }
  })

  wc.setWindowOpenHandler(({ url }) => {
    // 目标站外的一律丢给系统浏览器，别在应用里再开一个没边界的窗口
    if (/^https?:/i.test(url)) shell.openExternal(url)
    return { action: 'deny' }
  })

  // 页面的权限请求一律拒绝：这个应用不需要摄像头/麦克风/通知
  wc.session.setPermissionRequestHandler((_wc, _perm, callback) => callback(false))
}

function bindLoadFailureFallback(win) {
  win.webContents.on('did-fail-load', (_event, code, desc, url, isMainFrame) => {
    if (!isMainFrame) return
    if (!url || url.startsWith('file://')) return
    // -3 是用户主动中断（比如正在跳转），不算错误
    if (code === -3) return
    const target = path.join(__dirname, 'pages', 'offline.html')
    win.loadFile(target, { query: { url, code: String(code), desc: String(desc || '') } })
  })
}

// ------------------------------------------------------------------ 菜单

function buildMenu() {
  const template = [
    {
      label: '文件',
      submenu: [
        {
          label: '设置服务器地址…',
          click: () => openSettings(),
        },
        {
          label: '重新加载页面',
          accelerator: 'F5',
          click: () => {
            if (!mainWindow) return
            // 停在错误页/设置页时重新走一遍加载，别去刷新 file://
            if (mainWindow.webContents.getURL().startsWith('file://')) loadApp(mainWindow)
            else mainWindow.webContents.reload()
          },
        },
        { type: 'separator' },
        { role: 'quit', label: '退出' },
      ],
    },
    {
      label: '编辑',
      submenu: [
        { role: 'undo', label: '撤销' },
        { role: 'redo', label: '重做' },
        { type: 'separator' },
        { role: 'cut', label: '剪切' },
        { role: 'copy', label: '复制' },
        { role: 'paste', label: '粘贴' },
        { role: 'selectAll', label: '全选' },
      ],
    },
    {
      label: '视图',
      submenu: [
        { role: 'resetZoom', label: '实际大小' },
        { role: 'zoomIn', label: '放大' },
        { role: 'zoomOut', label: '缩小' },
        { type: 'separator' },
        { role: 'togglefullscreen', label: '全屏' },
        { type: 'separator' },
        { role: 'toggleDevTools', label: '开发者工具' },
      ],
    },
    {
      label: '帮助',
      submenu: [
        {
          label: '关于',
          click: () => {
            dialog.showMessageBox(mainWindow, {
              type: 'info',
              title: '关于' + APP_TITLE,
              message: APP_TITLE + ' ' + app.getVersion(),
              detail:
                'LOL 战绩查询与交友系统 · 桌面端\n\n' +
                '服务端：' + (store.get().serverBase || '未设置') + '\n' +
                '配置文件：' + store.configPath() + '\n\n' +
                '「录入战绩」需要本机英雄联盟客户端已启动并登录。',
              buttons: ['好'],
            })
          },
        },
      ],
    },
  ]
  Menu.setApplicationMenu(Menu.buildFromTemplate(template))
}

function openSettings() {
  if (!mainWindow) return
  mainWindow.loadFile(path.join(__dirname, 'pages', 'setup.html'), { query: { from: 'menu' } })
}

// ------------------------------------------------------------------ IPC

function registerIpc() {
  ipcMain.handle('config:get', () => {
    const cfg = store.get()
    return {
      serverBase: cfg.serverBase,
      lcuInstallDir: cfg.lcuInstallDir,
      configFile: cfg.configFile,
      version: app.getVersion(),
      platform: process.platform,
    }
  })

  ipcMain.handle('config:set', (_event, patch) => {
    const next = store.set(patch || {})
    return { serverBase: next.serverBase, lcuInstallDir: next.lcuInstallDir }
  })

  /**
   * 探测服务器可达性。设置页是 file:// origin，自己 fetch 会撞 CORS，
   * 所以由主进程代劳。
   *
   * 判定标准：能拿到任何 HTTP 响应就算通 —— /api/online/list 未登录返回 401，
   * 那恰恰说明服务是活的。
   */
  ipcMain.handle('server:probe', async (_event, rawUrl) => {
    const serverBase = store.normalizeServerBase(rawUrl)
    if (!serverBase) return { ok: false, message: '地址格式不对，例如 1.2.3.4 或 http://1.2.3.4' }

    const controller = new AbortController()
    const timer = setTimeout(() => controller.abort(), 8000)
    try {
      const resp = await fetch(serverBase + '/api/online/list', { signal: controller.signal })
      return { ok: true, serverBase, status: resp.status }
    } catch (e) {
      const reason = e.name === 'AbortError' ? '连接超时（8 秒）' : e.message
      return { ok: false, serverBase, message: reason }
    } finally {
      clearTimeout(timer)
    }
  })

  /** 本地页（设置/断网）跳回服务器页面。 */
  ipcMain.handle('app:goto-server', () => {
    if (mainWindow) loadApp(mainWindow)
    return true
  })

  ipcMain.handle('app:reload', () => {
    if (!mainWindow) return false
    const url = mainWindow.webContents.getURL()
    if (url.startsWith('file://')) loadApp(mainWindow)
    else mainWindow.webContents.reload()
    return true
  })

  /** 只探客户端在不在，不读战绩。给「检测游戏」按钮用。 */
  ipcMain.handle('lcu:probe', async () => {
    const cfg = store.get()
    try {
      const { me, found, creds } = await lcu.connectLive(cfg.lcuInstallDir)
      return {
        ok: true,
        summoner: lcu.normalizeSummoner(me),
        installDir: found.installDir,
        port: creds.port,
        source: creds.source,
        tried: found.tried,
      }
    } catch (e) {
      // 排查时用得上：把「找过哪些地方、最后是哪种失败」一起带回去
      if (e.installDir && e.installDir !== cfg.lcuInstallDir) {
        store.set({ lcuInstallDir: e.installDir })
      }
      return {
        ok: false,
        message: e.message || '连接客户端失败',
        diag: e.diag || null,
        tried: e.tried || [],
        installDir: e.installDir || null,
      }
    }
  })

  ipcMain.handle('lcu:sync', async (event, opts) => {
    const options = opts || {}
    if (syncing) return { ok: false, message: '正在录入中，请稍候' }

    const cfg = store.get()
    if (!cfg.serverBase) return { ok: false, message: '还没有设置服务器地址' }

    const token = options.token || ''
    if (!token) return { ok: false, message: '登录状态已失效，请先重新登录' }

    const count = Math.min(Math.max(Number(options.count) || 10, 1), 30)
    const report = text => {
      if (!event.sender.isDestroyed()) event.sender.send('lcu:progress', text)
    }

    syncing = true
    try {
      const result = await lcu.collect(cfg.lcuInstallDir, count, report)

      // 这次自动找到的目录记下来，下次省掉一轮搜索
      if (result.installDir && result.installDir !== cfg.lcuInstallDir) {
        store.set({ lcuInstallDir: result.installDir })
      }

      report(`读取到 ${result.fetchedCount} 场，正在上传…`)
      const uploaded = await postImport(cfg.serverBase, token, result.request)
      if (!uploaded.ok) return uploaded

      return {
        ok: true,
        data: {
          profile: uploaded.data,
          fetchedCount: result.fetchedCount,
          summonerName: result.summoner.name,
          rankSynced: Boolean(result.rank),
          rankTier: result.rank ? result.rank.tier : null,
          rankDivision: result.rank ? result.rank.division : null,
          leaguePoints: result.rank ? result.rank.leaguePoints : null,
          note: noteOf(result),
        },
      }
    } catch (e) {
      // message 是给用户的一句话（已经分过类：没开客户端 / 正在启动 / 凭证失效）。
      // diag 和 tried 是排查用的细节，界面不显示，但会留在日志里。
      if (e.tried && e.tried.length) console.error('[lcu] 排查线索：' + e.tried.join(' | '))
      // 连不上也要记目录：找到过一次，下次就不用再翻盘
      if (e.installDir && e.installDir !== cfg.lcuInstallDir) {
        store.set({ lcuInstallDir: e.installDir })
      }
      return {
        ok: false,
        message: e.message || '录入失败',
        diag: e.diag || null,
        tried: e.tried || [],
      }
    } finally {
      syncing = false
    }
  })
}

/** 文案与后端 LcuSyncService.noteOf 保持一致，用户看到的解释不会因为入口不同而变。 */
function noteOf(result) {
  if (result.fetchedCount === 0) {
    return '客户端里没读到对局。确认游戏已登录，且这个账号打过至少一局排位或匹配。'
  }
  if (!result.rank) {
    return '这个账号当前没有定级，段位保持原样；战绩和实力分已更新。'
  }
  return null
}

/**
 * 把本机读到的战绩上报给服务端。
 *
 * 走的是网页一直在用的 POST /api/match/import —— 契约完全一致，后端不需要知道
 * 请求是桌面端发的还是旧的 /match/sync 发起的。
 */
async function postImport(serverBase, token, body) {
  const url = serverBase + '/api/match/import'
  const controller = new AbortController()
  const timer = setTimeout(() => controller.abort(), 40000)

  try {
    const resp = await fetch(url, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: 'Bearer ' + token,
      },
      body: JSON.stringify(body),
      signal: controller.signal,
    })

    const text = await resp.text()
    let json = null
    try {
      json = JSON.parse(text)
    } catch {
      // 落到下面统一处理
    }

    if (!json) {
      return { ok: false, message: `服务器返回了无法解析的内容（HTTP ${resp.status}）` }
    }
    // 项目约定：业务码与 HTTP 同值，成功是 200
    if (json.code !== 200 && json.code !== 0) {
      return { ok: false, message: json.message || `录入失败（${json.code}）`, code: json.code }
    }
    return { ok: true, data: json.data }
  } catch (e) {
    if (e.name === 'AbortError') {
      return { ok: false, message: '上传超时，请检查网络后重试' }
    }
    return {
      ok: false,
      message: `连不上服务器（${serverBase}）。请确认服务已启动、地址正确：${e.message}`,
    }
  } finally {
    clearTimeout(timer)
  }
}

// ------------------------------------------------------------------ 生命周期

const gotLock = app.requestSingleInstanceLock()
if (!gotLock) {
  app.quit()
} else {
  app.on('second-instance', () => {
    if (!mainWindow) return
    if (mainWindow.isMinimized()) mainWindow.restore()
    mainWindow.focus()
  })

  app.whenReady().then(() => {
    app.setAppUserModelId('com.lol.desktop')
    registerIpc()
    buildMenu()
    createWindow()

    app.on('activate', () => {
      if (BrowserWindow.getAllWindows().length === 0) createWindow()
    })
  })

  app.on('window-all-closed', () => {
    app.quit()
  })
}

// 兜底：不让未捕获异常把主进程带走
process.on('uncaughtException', err => {
  console.error('主进程未捕获异常：', err)
})
