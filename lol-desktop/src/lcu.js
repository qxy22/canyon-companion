'use strict'

/**
 * 本机 LCU（英雄联盟客户端）读取。
 *
 * 这是 lol-lcu-reader 那个 Java 模块的 Node 版，逻辑逐条对齐：
 *  - 凭证：优先读 LeagueClientUx.exe 的命令行（国服最可靠），其次日志，最后 lockfile；
 *  - 接口：自签证书 + Basic Auth（用户名固定 riot，密码是 remoting-auth-token）；
 *  - 国服列表接口只给本人数据，10 人阵容要逐场取详情。
 *
 * 比 Java 版多了一步「从运行中进程的 exe 路径反推游戏目录」：
 * 原版把安装目录硬编码成 C:\WeGameApps\英雄联盟，别人装在 D 盘就永远找不到。
 */

const { execFile } = require('child_process')
const https = require('https')
const net = require('net')
const fs = require('fs')
const path = require('path')

const PS_TIMEOUT = 12000

/** 连不上时最多试几轮。客户端启动要几十秒，中途端口是会变的。 */
const CONNECT_ROUNDS = 3
const CONNECT_GAP_MS = 2000

// ------------------------------------------------------------------ 凭证

/** 跑一段 PowerShell，返回标准输出。中文用 UTF-8 输出，避免乱码。 */
function runPowerShell(script) {
  return new Promise(resolve => {
    // -EncodedCommand 传 UTF-16LE base64，避免 ProcessBuilder 式的引号破坏
    const encoded = Buffer.from(script, 'utf16le').toString('base64')
    execFile(
      'powershell.exe',
      ['-NoProfile', '-NonInteractive', '-ExecutionPolicy', 'Bypass', '-EncodedCommand', encoded],
      { timeout: PS_TIMEOUT, windowsHide: true, maxBuffer: 8 * 1024 * 1024 },
      (err, stdout) => resolve(err ? '' : String(stdout || ''))
    )
  })
}

// 新版 Windows 已移除 wmic，这里走 Get-CimInstance。
// 用 [char]10 拼行而不是 `n，省得反引号在 JS 字符串里还要转义。
const CMD_SCRIPT = [
  '[Console]::OutputEncoding=[System.Text.Encoding]::UTF8',
  '$out = @()',
  'try {',
  '  $ps = Get-CimInstance Win32_Process -Filter "Name=\'LeagueClientUx.exe\'" -ErrorAction Stop',
  '  foreach ($p in $ps) {',
  '    if ($p.CommandLine) { $out += ("CMD::" + $p.CommandLine) }',
  '    if ($p.ExecutablePath) { $out += ("EXE::" + $p.ExecutablePath) }',
  '  }',
  '} catch {}',
  '[Console]::Out.Write(($out -join ([char]10)))',
].join('\n')

/**
 * 上面那条带 -Filter 的查询在少数机器上会被安全软件/组策略挡掉（表现为返回空，
 * 而不是报错）。这时换成不带 Filter 的写法再试一次 —— 慢一点，但能拿到。
 */
const CMD_SCRIPT_FALLBACK = [
  '[Console]::OutputEncoding=[System.Text.Encoding]::UTF8',
  '$out = @()',
  'try {',
  '  $ps = Get-CimInstance Win32_Process -ErrorAction Stop',
  "  foreach ($p in $ps) { if ($p.Name -eq 'LeagueClientUx.exe') {",
  '    if ($p.CommandLine) { $out += ("CMD::" + $p.CommandLine) }',
  '    if ($p.ExecutablePath) { $out += ("EXE::" + $p.ExecutablePath) }',
  '  } }',
  '} catch {}',
  '[Console]::Out.Write(($out -join ([char]10)))',
].join('\n')

/**
 * 直接问「LeagueClientUx 正在监听哪个端口」。
 *
 * 为什么需要这条：命令行那套依赖 `Get-CimInstance Win32_Process`，而它在实际机器上
 * 常被安全软件挡掉（现象很阴：进程确实在跑，但拿不到命令行，于是只能去读日志，
 * 读到的是上一次启动的端口 → ECONNREFUSED）。端口这件事其实不必问进程 ——
 * 问「谁在 127.0.0.1 上监听」更直接，也更难被挡。
 */
const PORT_SCRIPT = [
  '[Console]::OutputEncoding=[System.Text.Encoding]::UTF8',
  '$p = @(Get-Process -Name LeagueClientUx -ErrorAction SilentlyContinue)',
  'if ($p.Count -eq 0) { [Console]::Out.Write(""); exit }',
  '$ids = @($p | ForEach-Object { $_.Id })',
  '$out = @()',
  'try {',
  '  $c = @(Get-NetTCPConnection -State Listen -ErrorAction Stop)',
  '  foreach ($x in $c) { if ($ids -contains $x.OwningProcess) { $out += ("PORT::" + $x.LocalPort) } }',
  '} catch {}',
  '[Console]::Out.Write(($out -join ([char]10)))',
].join('\n')

/** 客户端进程正在监听的端口（可能多个）。 */
async function readListeningPorts() {
  const out = await runPowerShell(PORT_SCRIPT)
  const ports = []
  for (const line of out.split(/\r?\n/)) {
    if (!line.startsWith('PORT::')) continue
    const n = Number(line.slice(6).trim())
    if (Number.isInteger(n) && n > 0 && !ports.includes(n)) ports.push(n)
  }
  return ports
}

function parsePortToken(text) {
  const port = /--app-port=(\d+)/.exec(text || '')
  const token = /--remoting-auth-token=([^\s"]+)/.exec(text || '')
  if (!port || !token) return null
  return { port: Number(port[1]), token: token[1] }
}

/**
 * 问安装目录：注册表卸载表。
 *
 * 为什么必须做这件事：默认只猜 C/D/E 盘那几个常见路径，别人装在
 * `F:\Games\英雄联盟` 就永远找不到，而让他手填目录又多一道门槛。
 * 国服卸载项里 `InstallLocation` 经常是空的，但 `UninstallString` 一定有
 * （形如 `C:\WeGameApps\英雄联盟\英雄联盟卸载.exe`），从它反推目录就行。
 */
const REG_SCRIPT = [
  '[Console]::OutputEncoding=[System.Text.Encoding]::UTF8',
  '$out = @()',
  '$keys = @(',
  "  'HKLM:\\SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\Uninstall\\*',",
  "  'HKLM:\\SOFTWARE\\WOW6432Node\\Microsoft\\Windows\\CurrentVersion\\Uninstall\\*',",
  "  'HKCU:\\SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\Uninstall\\*')",
  'try {',
  '  $items = Get-ItemProperty $keys -ErrorAction Stop',
  "  $items = $items | Where-Object { $_.DisplayName -match '英雄联盟|League of Legends|LeagueClient' }",
  '  foreach ($i in $items) {',
  '    if ($i.InstallLocation) { $out += ("LOC::" + $i.InstallLocation) }',
  '    if ($i.UninstallString) { $out += ("UNS::" + $i.UninstallString) }',
  '  }',
  '} catch {}',
  '[Console]::Out.Write(($out -join ([char]10)))',
].join('\n')

/** 只看这些目录名，别的地方装游戏的可能性太低，不值得翻。 */
const INSTALL_KEY = /英雄联盟|league|wegame|riot|tencent|游戏|games/i

const SKIP_DIRS = new Set([
  'windows', '$recycle.bin', 'system volume information', 'programdata', 'users',
  'perflogs', 'appdata', 'cache', 'node_modules', '.git',
])

function isDir(p) {
  try {
    return fs.statSync(p).isDirectory()
  } catch {
    return false
  }
}

/** 像不像游戏安装根目录：底下得有 LeagueClient。 */
function looksLikeInstallDir(dir) {
  if (!isDir(dir)) return false
  if (isDir(path.join(dir, 'LeagueClient'))) return true
  try {
    if (fs.existsSync(path.join(dir, 'LeagueClientUx.exe'))) return true
  } catch { /* 无所谓 */ }
  return false
}

function childDirs(dir) {
  try {
    return fs.readdirSync(dir, { withFileTypes: true })
      .filter(e => e.isDirectory())
      .map(e => path.join(dir, e.name))
  } catch {
    return []
  }
}

/** 从 UninstallString 之类的命令行里抠出目录路径。 */
function dirFromCommandLine(text) {
  let s = String(text || '').trim()
  if (!s) return null
  const quoted = /^"([^"]+)"/.exec(s)
  s = quoted ? quoted[1] : s.split(/\s+(?=\/)/)[0]
  s = s.replace(/^["']|["']$/g, '')
  if (!s) return null
  return path.dirname(s)
}

/** 从注册表问安装目录。 */
async function dirsFromRegistry() {
  const out = await runPowerShell(REG_SCRIPT)
  const found = []
  const seen = new Set()
  for (const line of out.split(/\r?\n/)) {
    const isLoc = line.startsWith('LOC::')
    const isUns = line.startsWith('UNS::')
    if (!isLoc && !isUns) continue
    let dir = isLoc ? line.slice(5).trim() : dirFromCommandLine(line.slice(5))
    if (!dir) continue
    // 卸载程序可能在子目录里（...\uninst\unins000.exe），往上找带 LeagueClient 的那一层
    let up = dir
    for (let i = 0; i < 3 && up; i++) {
      if (looksLikeInstallDir(up)) break
      const parent = path.dirname(up)
      if (parent === up) break
      up = parent
    }
    if (looksLikeInstallDir(up) && !seen.has(up)) {
      seen.add(up)
      found.push(up)
    }
  }
  return found
}

/** 在各盘符下找装着 LeagueClient 的目录（只翻名字像的那几个分支）。 */
function dirsFromDrives() {
  const found = []
  const seen = new Set()

  const walk = (dir, depth) => {
    if (looksLikeInstallDir(dir)) {
      if (!seen.has(dir)) {
        seen.add(dir)
        found.push(dir)
      }
      return
    }
    if (depth >= 2) return
    for (const sub of childDirs(dir)) {
      const name = path.basename(sub).toLowerCase()
      if (SKIP_DIRS.has(name)) continue
      walk(sub, depth + 1)
    }
  }

  for (let code = 67; code <= 90; code++) {
    const drive = String.fromCharCode(code) + ':\\'
    if (!isDir(drive)) continue
    for (const dir of childDirs(drive)) {
      const name = path.basename(dir).toLowerCase()
      if (SKIP_DIRS.has(name)) continue
      // 名字完全不像的话，连一层都不值得翻
      if (!looksLikeInstallDir(dir) && !INSTALL_KEY.test(name)) continue
      walk(dir, 0)
    }
  }
  return found
}

/** 只问「LeagueClientUx 在不在跑」，比拉全量进程列表快一个数量级。 */
const EXISTS_SCRIPT = [
  '[Console]::OutputEncoding=[System.Text.Encoding]::UTF8',
  '$p = Get-Process -Name LeagueClientUx -ErrorAction SilentlyContinue',
  '[Console]::Out.Write($(if ($p) { "YES" } else { "NO" }))',
].join('\n')

/** 从进程读命令行与 exe 路径。返回 { creds, exePaths[], note }。 */
async function readFromProcesses(quick) {
  let out = await runPowerShell(CMD_SCRIPT)
  let note = '进程列表（按名称查询）'
  let running = out.trim().length > 0

  if (!out.trim()) {
    // 读空有两种可能：真没开游戏，或被安全软件挡了。先花一次便宜的判断区分开，
    // 只有进程确实在跑、却拿不到命令行时，才去做昂贵的全量遍历。
    const exists = await runPowerShell(EXISTS_SCRIPT)
    running = /YES/.test(exists)
    if (!running) {
      return { creds: [], exePaths: [], running, note: '进程列表：没有正在运行的 LeagueClientUx.exe' }
    }
    // 全量遍历要几秒，重试轮里不重复做 —— 第一轮没拿到，第二轮也不会拿到
    if (quick) {
      return { creds: [], exePaths: [], running, note: '进程列表：进程在跑，但读不到命令行' }
    }
    out = await runPowerShell(CMD_SCRIPT_FALLBACK)
    note = '进程列表（全量遍历：按名称查询没读到，但进程确实在跑）'
  }
  const creds = []
  const exePaths = []
  for (const line of out.split(/\r?\n/)) {
    if (line.startsWith('CMD::')) {
      const c = parsePortToken(line.slice(5))
      if (c) creds.push(c)
    } else if (line.startsWith('EXE::')) {
      const p = line.slice(5).trim()
      if (p) exePaths.push(p)
    }
  }
  if (!creds.length && !exePaths.length) {
    note += '：没读到任何 LeagueClientUx.exe'
  }
  return { creds, exePaths, running, note }
}

/** 从 exe 路径反推「可能是游戏安装根目录」的几个候选。 */
function dirsFromExe(exePath) {
  // 常见：...\英雄联盟\LeagueClient\LeagueClientUx.exe
  //      ...\League of Legends\LeagueClient\LeagueClientUx.exe
  const dirs = []
  let dir = path.dirname(exePath)
  for (let i = 0; i < 4 && dir; i++) {
    dirs.push(dir)
    const up = path.dirname(dir)
    if (up === dir) break
    dir = up
  }
  return dirs
}

/** 递归收集所有匹配的文件（广度优先，浅的在前）。 */
function collectMatches(root, predicate, maxDepth = 6) {
  const hits = []
  let queue = [{ dir: root, depth: 0 }]
  const visited = new Set()
  while (queue.length) {
    const next = []
    for (const { dir, depth } of queue) {
      let entries
      try {
        entries = fs.readdirSync(dir, { withFileTypes: true })
      } catch {
        continue
      }
      for (const e of entries) {
        const full = path.join(dir, e.name)
        if (e.isFile()) {
          if (predicate(e.name)) hits.push(full)
        } else if (e.isDirectory() && depth < maxDepth) {
          if (visited.has(full)) continue
          visited.add(full)
          // 跳过明显无关的大目录，免得在几个 G 的游戏目录里空转
          if (['node_modules', '.git', 'Cache', 'CachedData'].includes(e.name)) continue
          next.push({ dir: full, depth: depth + 1 })
        }
      }
    }
    queue = next
  }
  return hits
}

/** 同深度内取最先遍历到的那个。 */
function findFile(root, predicate, maxDepth = 6) {
  return collectMatches(root, predicate, maxDepth)[0] || null
}

function mtimeOf(file) {
  try {
    return fs.statSync(file).mtimeMs
  } catch {
    return 0
  }
}

/** lockfile 固定在安装根或 LeagueClient 下，点名找不到才递归。 */
function findLockfile(root) {
  for (const candidate of [path.join(root, 'lockfile'), path.join(root, 'LeagueClient', 'lockfile')]) {
    try {
      if (fs.statSync(candidate).isFile()) return candidate
    } catch { /* 没有就继续 */ }
  }
  return findFile(root, n => n.toLowerCase() === 'lockfile', 2)
}

/**
 * 只认主进程那份日志。
 *
 * 不能写成「名字里含 LeagueClientUx」：同目录下还有
 * `..._LeagueClientUxHelper-gpu-proc.log`、`..._LeagueClientUxRender.log`，
 * 它们是子进程，里面根本没有 --app-port，抓到就白连一次。
 */
function isUxLog(name) {
  return name.toLowerCase().endsWith('leagueclientux.log')
}

/**
 * 找客户端日志。
 *
 * 先按国服固定布局直接点名要那几个目录 —— 命中就只是读一次目录，几毫秒。
 * 只有布局对不上时才递归整个游戏目录（几万个文件，慢一个数量级）。
 */
function findLogs(root) {
  const known = [
    path.join(root, 'LeagueClient', 'Logs', 'LeagueClient Logs'),
    path.join(root, 'Logs', 'LeagueClient Logs'),
    path.join(root, 'LeagueClient', 'Logs'),
    // 国服实测：日志也可能直接躺在 LeagueClient 目录下
    path.join(root, 'LeagueClient'),
  ]
  for (const dir of known) {
    let entries
    try {
      entries = fs.readdirSync(dir, { withFileTypes: true })
    } catch {
      continue
    }
    const hits = entries
      .filter(e => e.isFile() && isUxLog(e.name))
      .map(e => path.join(dir, e.name))
    if (hits.length) return hits.sort((a, b) => mtimeOf(b) - mtimeOf(a))
  }
  return collectMatches(root, isUxLog, 5).sort((a, b) => mtimeOf(b) - mtimeOf(a))
}

function parseLogContent(content) {
  const port = lastMatch(content, /--app-port=(\d+)/g)
  const token = lastMatch(content, /--remoting-auth-token=([^\s]+)/g)
  if (!port || !token) return null
  return { port: Number(port), token }
}

function lastMatch(text, regex) {
  let last = null
  let m
  while ((m = regex.exec(text)) !== null) last = m[1]
  return last
}

function parseLockfile(file) {
  try {
    const line = fs.readFileSync(file, 'utf8').trim()
    if (!line) return null // 国服 lockfile 常被清空
    const parts = line.split(':')
    if (parts.length < 5) return null
    // [0]=name [1]=pid [2]=port [3]=password [4]=protocol
    const port = Number(parts[2])
    if (!Number.isFinite(port) || port <= 0) return null
    return { port, token: parts[3] }
  } catch {
    return null
  }
}

/**
 * 找本机 LCU 凭证。
 *
 * @param {string} [manualDir] 用户手动指定的游戏目录
 * @returns {Promise<{candidates: Array<{port:number,token:string}>, installDir: string|null, tried: string[]}>}
 */
async function discover(manualDir, options) {
  // 重试轮里不再翻日志：日志记的是上一次启动的端口，第二轮翻一遍还是同一个值，
  // 但游戏目录动辄几万个文件，翻一次要几十秒 —— 白等。
  const quick = Boolean(options && options.quick)
  const tried = []
  const candidates = []
  const seen = new Set()
  let installDir = null

  // 来源决定可信度：进程命令行是「此刻真在跑的」，lockfile 次之，
  // 日志最不可靠 —— 它记的是上一次启动的端口，客户端重启后必然过期。
  const push = (c, source) => {
    if (!c) return
    const key = c.port + ':' + c.token
    if (seen.has(key)) return
    seen.add(key)
    candidates.push({ ...c, source })
  }

  // 1) 命令行（国服最可靠）
  const proc = await readFromProcesses(quick)
  tried.push(`${proc.note}：${proc.creds.length} 组凭证、${proc.exePaths.length} 个进程路径`)
  proc.creds.forEach(c => push(c, 'process'))

  // 2) 用户在设置里指定的目录
  const roots = []
  if (manualDir && fs.existsSync(manualDir)) {
    roots.push(manualDir)
    installDir = installDir || manualDir
  }

  // 3) 问系统：注册表卸载表 / 各盘符扫描
  //
  // 这一条是关键 —— 别人把游戏装在 F:\Games\英雄联盟，靠猜路径是猜不到的，
  // 而命令行读不到进程（安全软件挡 CIM）时又反推不出来，于是只能让用户手填。
  // 卸载表里一定记着卸载程序的位置，顺着它就能找到根目录。只在首轮做，慢。
  if (!quick) {
    let reg = []
    try {
      reg = await dirsFromRegistry()
    } catch { /* 问不到就算了 */ }
    if (reg.length) tried.push(`注册表：${reg.join('、')}`)
    for (const d of reg) {
      if (!roots.includes(d)) roots.push(d)
      installDir = installDir || d
    }
  }

  // 盘符扫描不慢（只看盘根 + 名字像的那几个分支的两层），所以不必等注册表扑空
  // 才做 —— 两条路一起跑，谁先命中算谁的，注册表的结果优先。
  if (!quick) {
    const drives = dirsFromDrives()
    if (drives.length) tried.push(`盘符扫描：${drives.join('、')}`)
    for (const d of drives) {
      if (!roots.includes(d)) roots.push(d)
      installDir = installDir || d
    }
  }

  // 4) 从正在跑的进程 exe 路径反推
  for (const exe of proc.exePaths) {
    for (const d of dirsFromExe(exe)) {
      if (fs.existsSync(d) && !roots.includes(d)) roots.push(d)
    }
    const guess = dirsFromExe(exe).find(d => /LeagueClient/i.test(d) || fs.existsSync(path.join(d, 'Game')))
    if (guess) installDir = installDir || guess
  }
  // 4) 常见安装位置兜底
  for (const d of [
    'C:\\WeGameApps\\英雄联盟',
    'C:\\Program Files\\Tencent\\League of Legends',
    'C:\\Riot Games\\League of Legends',
    'D:\\WeGameApps\\英雄联盟',
    'D:\\Riot Games\\League of Legends',
    'D:\\英雄联盟',
    'E:\\英雄联盟',
    'E:\\WeGameApps\\英雄联盟',
  ]) {
    if (fs.existsSync(d) && !roots.includes(d)) roots.push(d)
  }

  // 5) 日志 / lockfile
  //
  // 命令行已经拿到凭证时就别再翻磁盘了：游戏目录动辄几万个文件，递归扫一遍要好几秒，
  // 而这条路径的命中率本来就低。只有连不上客户端（比如进程列表被权限挡住）时才值得走。
  if (!candidates.length) {
    for (const root of roots) {
      // lockfile 就在安装根或 LeagueClient 目录下，先直接点名要，命中就不递归
      const lock = findLockfile(root)
      if (lock) {
        tried.push('lockfile：' + lock)
        push(parseLockfile(lock), 'lockfile')
      }
      if (candidates.length) break
    }
  }

  //
  // 日志是最后兜底，而且必须挑**最新的那一份**：国服日志目录里会堆着几十个
  // 历史文件，随便抓一个很可能就是几天前的，端口早就不对了 —— 连上去必然
  // ECONNREFUSED。按修改时间倒序只试最新两个。
  if (!candidates.length && !quick) {
    for (const root of roots) {
      const logs = findLogs(root)
      for (const log of logs.slice(0, 2)) {
        tried.push('日志：' + log)
        try {
          push(parseLogContent(fs.readFileSync(log, 'utf8')), 'log')
        } catch { /* 读不到就继续 */ }
        if (candidates.length) break
      }
      if (candidates.length) break
    }
  }

  //
  // 6) 「真在监听的端口」×「已经拿到的 token」再组一批候选。
  //
  // 端口来自当前监听，比日志里那个准得多；token 只能沿用已有的（命令行/lockfile/
  // 日志），万一它对不上当前会话，会以 401 的形式暴露出来，那时提示重开客户端即可。
  // 这一批排在日志来源之前 —— 连接的失败点越少越好。
  if (!quick && candidates.length) {
    const tokens = [...new Set(candidates.map(c => c.token))]
    const ports = await readListeningPorts()
    if (ports.length) tried.push(`监听端口：${ports.join('、')}`)
    for (const port of ports) {
      for (const token of tokens) push({ port, token }, 'listen')
    }
  }

  return { candidates, installDir, tried, processRunning: proc.running }
}

// ------------------------------------------------------------------ 调用

/** 对 LCU 发 GET，自签证书一律信任（本地回环，不出网）。 */
function lcuGet(creds, apiPath, timeout = 15000) {
  return new Promise((resolve, reject) => {
    const auth = 'Basic ' + Buffer.from(`riot:${creds.token}`).toString('base64')
    const req = https.request(
      {
        host: '127.0.0.1',
        port: creds.port,
        path: apiPath,
        method: 'GET',
        rejectUnauthorized: false,
        timeout,
        headers: { Authorization: auth, Accept: 'application/json' },
      },
      res => {
        const chunks = []
        res.on('data', c => chunks.push(c))
        res.on('end', () => {
          const body = Buffer.concat(chunks).toString('utf8')
          if (res.statusCode >= 400) {
            reject(new Error(`HTTP ${res.statusCode} on ${apiPath}`))
          } else {
            resolve(body)
          }
        })
      }
    )
    req.on('timeout', () => req.destroy(new Error('连接客户端超时')))
    req.on('error', reject)
    req.end()
  })
}

async function getJson(creds, apiPath, timeout) {
  return JSON.parse(await lcuGet(creds, apiPath, timeout))
}

/** TCP 探活。端口没在监听时立刻知道，不用白等一个 HTTP 超时。 */
function portOpen(port, host = '127.0.0.1', timeout = 1000) {
  return new Promise(resolve => {
    const sock = new net.Socket()
    let settled = false
    const done = v => {
      if (settled) return
      settled = true
      try {
        sock.destroy()
      } catch { /* 已经关了 */ }
      resolve(v)
    }
    sock.setTimeout(timeout)
    sock.once('connect', () => done(true))
    sock.once('timeout', () => done(false))
    sock.once('error', () => done(false))
    try {
      sock.connect(port, host)
    } catch {
      done(false)
    }
  })
}

/**
 * 把连不上的原因归个类。
 *
 * 用户能做的补救完全不同：没开游戏、正在启动、凭证过期，三件事不该说同一句话。
 */
function kindOf(err) {
  const code = err && err.code
  const msg = String((err && err.message) || '')
  if (code === 'ECONNREFUSED') return 'refused'
  if (code === 'ECONNRESET') return 'reset'
  if (code === 'ETIMEDOUT' || /timeout|超时/i.test(msg)) return 'timeout'
  if (/HTTP 401|HTTP 403/.test(msg)) return 'auth'
  // 客户端启动过程中接口还没注册，这时会回 404 —— 是「再等等」不是「失败了」
  if (/HTTP 404/.test(msg)) return 'starting'
  if (/HTTP 5\d\d/.test(msg)) return 'busy'
  return 'unknown'
}

/** 逐个候选试连通性，返回第一个能读到召唤师的凭证。 */
async function pickLiveCredentials(candidates) {
  let last = null
  for (const creds of candidates) {
    const open = await portOpen(creds.port)
    if (!open) {
      last = { kind: 'refused', port: creds.port, source: creds.source }
      continue
    }
    try {
      const me = await getJson(creds, '/lol-summoner/v1/current-summoner', 5000)
      if (me && (me.puuid || me.gameName || me.displayName)) {
        return { creds, me }
      }
      last = { kind: 'empty', port: creds.port, source: creds.source }
    } catch (e) {
      last = { kind: kindOf(e), port: creds.port, source: creds.source, message: e.message }
    }
  }
  const err = new Error((last && last.message) || '所有候选凭证都连不上客户端')
  err.diag = last
  throw err
}

function rankSource(source) {
  if (source === 'process') return 0
  if (source === 'listen') return 1
  if (source === 'lockfile') return 2
  return 3
}

function sourceLabel(source) {
  if (source === 'process') return '客户端进程'
  if (source === 'listen') return '客户端正在监听的端口'
  if (source === 'lockfile') return 'lockfile'
  return '日志（可能是上一次启动留下的）'
}

/** 失败原因 → 用户能照做的一句话。 */
function explainFailure(last, tried) {
  const kind = (last && last.kind) || 'unknown'
  const where = last && last.port ? `（端口 ${last.port}，凭证来自${sourceLabel(last.source)}）` : ''

  if (kind === 'not-found') {
    // 把「找过哪些地方」附上：对方机器上到底是压根没开游戏，还是进程列表被
    // 安全软件挡住了，这两种情况的补救完全不同，光看一句话分不出来。
    const clue = tried && tried.length ? `（找过：${tried.join('；').slice(0, 160)}）` : ''
    return '没找到英雄联盟客户端。请在这台电脑上启动客户端并登录到主界面 —— 只开着 WeGame、登录器，或只开着「峡谷同行」都不算，然后再点一次录入。' + clue
  }
  // 404 和空对象都是「进程在、但还没登录」：LCU 没登录时查不到召唤师。
  // 这跟「没开游戏」是两件事，必须分开说，否则用户会反复开关客户端。
  if (kind === 'starting' || kind === 'empty') {
    return `客户端已启动，但还没读到登录的召唤师${where}。请在英雄联盟里登录游戏账号、进入主界面后再点一次录入 —— 停在登录页不算登录。`
  }
  if (kind === 'refused') {
    return `客户端没有响应${where}。它多半正在启动或刚刚退出：请确认已经登录到主界面，等十几秒再点一次；反复如此就关掉客户端重开。`
  }
  if (kind === 'auth') {
    return `读到了客户端，但凭证已失效${where}。请完全退出英雄联盟后重新登录。`
  }
  if (kind === 'starting') {
    return `客户端还在启动中${where}，接口没准备好。请等它进入主界面后再点一次录入。`
  }
  if (kind === 'timeout' || kind === 'busy') {
    return `连接客户端超时${where}。客户端可能卡住了，重启一下再试。`
  }
  const raw = last && last.message ? `（${last.message}）` : tried && tried.length ? `（${tried.join('；')}）` : ''
  return `连接客户端失败${where}${raw}`
}

/**
 * 连上客户端，连不上会重试。
 *
 * 为什么要重试：客户端从「进程出现」到「接口能应答」中间有几十秒，而且每次
 * 启动端口都会变。只试一轮的话，早点的那一轮必然吃到一个还没监听的端口。
 */
async function connectLive(manualDir, onProgress = () => {}) {
  let last = null
  let tried = []
  let installDir = null

  for (let round = 1; round <= CONNECT_ROUNDS; round++) {
    const found = await discover(manualDir, { quick: round > 1 })
    tried = found.tried
    // 就算这次没连上，问出来的安装目录也是有用的：记下来，下次不用再翻一遍盘
    installDir = installDir || found.installDir

    if (!found.candidates.length) {
      last = { kind: 'not-found' }
      // 游戏压根没开时，再翻两遍盘也不会凭空变出一个客户端，只会让人干等
      // 半分钟。进程不在就直接给结论；只有「进程在跑但连不上」才值得重试。
      if (!found.processRunning) break
    } else {
      // 命令行拿到的最可信，日志里的最可能是过期的
      const ordered = [...found.candidates].sort((a, b) => rankSource(a.source) - rankSource(b.source))
      try {
        const picked = await pickLiveCredentials(ordered)
        return { ...picked, found }
      } catch (e) {
        last = e.diag || { kind: 'unknown', message: e.message }
      }
    }

    if (round < CONNECT_ROUNDS) {
      onProgress(`客户端还没就绪，正在重试（第 ${round + 1} 次）…`)
      await sleep(CONNECT_GAP_MS)
    }
  }

  const err = new Error(explainFailure(last, tried))
  err.diag = last
  err.tried = tried
  err.installDir = installDir
  throw err
}

/** 当前召唤师。国服名字在 gameName，displayName 为空。 */
function normalizeSummoner(node) {
  return {
    puuid: node.puuid || '',
    name: node.gameName || node.displayName || '',
    tagLine: node.tagLine || '',
    level: Number(node.summonerLevel || 0),
  }
}

/** 最近 count 场的 gameId。战绩服务可能 503，重试几次。 */
async function readMatchIds(creds, puuid, count) {
  const apiPath = `/lol-match-history/v1/products/lol/${puuid}/matches?begIndex=0&endIndex=${count}`
  let lastErr = null
  for (let i = 0; i < 3; i++) {
    try {
      const node = await getJson(creds, apiPath, 20000)
      const games = node?.games?.games
      const ids = []
      if (Array.isArray(games)) {
        for (const g of games) {
          const id = Number(g.gameId || 0)
          if (id) ids.push(id)
        }
      }
      return ids
    } catch (e) {
      lastErr = e
      await sleep(1500)
    }
  }
  throw lastErr
}

/**
 * 段位。未定级/接口不可用时返回 null —— 段位不是录入的必要条件，
 * 不能因为它读不到就让整次录入失败。
 */
async function readRank(creds) {
  let node
  try {
    node = await getJson(creds, '/lol-ranked/v1/current-ranked-stats', 8000)
  } catch {
    return null
  }
  const queueMap = node?.queueMap || {}
  for (const queue of ['RANKED_SOLO_5x5', 'RANKED_FLEX_SR']) {
    const q = queueMap[queue]
    if (!q) continue
    const tier = String(q.tier || '').trim().toUpperCase()
    if (!tier || tier === 'NONE') continue
    return {
      tier,
      division: String(q.rank || ''),
      leaguePoints: Number(q.leaguePoints || 0),
      queue,
    }
  }
  return null
}

/**
 * 在 10 人里找到自己。
 * 国服是老结构：participants 里没有 puuid，身份在 participantIdentities，
 * 通过 participantId 关联到 participants。
 */
function findSelf(game, puuid, name) {
  const participants = Array.isArray(game.participants) ? game.participants : []

  for (const p of participants) {
    if (puuid && p.puuid === puuid) return p
  }

  const identities = Array.isArray(game.participantIdentities) ? game.participantIdentities : []
  let selfId = -1
  for (const id of identities) {
    const player = id?.player || {}
    const hit =
      (puuid && player.puuid === puuid) ||
      (name && (name === player.summonerName || name === player.gameName))
    if (hit) {
      selfId = Number(id.participantId || 0)
      break
    }
  }
  if (selfId > 0) {
    for (const p of participants) {
      if (Number(p.participantId) === selfId) return p
    }
  }
  return null
}

/** 提取自己一场的原始战绩，直接对齐后端 MatchImportItem 的字段名。 */
function extractSelf(game, puuid, name) {
  const self = findSelf(game, puuid, name)
  if (!self) return null
  const stats = self.stats || {}
  return {
    gameId: Number(game.gameId || 0),
    win: Boolean(stats.win),
    lane: self.timeline?.lane || '',
    kills: Number(stats.kills || 0),
    deaths: Number(stats.deaths || 0),
    assists: Number(stats.assists || 0),
    cs: Number(stats.totalMinionsKilled || 0),
    visionScore: Number(stats.visionScore || 0),
    duration: Number(game.gameDuration || 0),
    playedAt: Number(game.gameCreation || 0),
  }
}

function sleep(ms) {
  return new Promise(r => setTimeout(r, ms))
}

/**
 * 完整流程：连客户端 → 读身份/段位/最近 count 场 → 返回可直接上报后端的数据。
 *
 * @returns {Promise<{request: object, summoner: object, rank: object|null, fetchedCount: number}>}
 */
async function collect(manualDir, count = 10, onProgress = () => {}) {
  onProgress('正在定位本地游戏客户端…')
  const { creds, me: rawMe, found } = await connectLive(manualDir, onProgress)
  const me = normalizeSummoner(rawMe)
  onProgress(`已连接：${me.name || '未知召唤师'}`)

  const rank = await readRank(creds)

  onProgress(`正在读取最近 ${count} 场战绩…`)
  const ids = await readMatchIds(creds, me.puuid, count)
  const matches = []
  for (let i = 0; i < ids.length; i++) {
    onProgress(`正在读取战绩 ${i + 1}/${ids.length}…`)
    try {
      const game = await getJson(creds, `/lol-match-history/v1/games/${ids[i]}`, 20000)
      const item = extractSelf(game, me.puuid, me.name)
      if (item) matches.push(item)
    } catch {
      // 单场读失败不影响整批 —— 少一场比整次失败好
    }
  }

  return {
    summoner: me,
    rank,
    fetchedCount: matches.length,
    installDir: found.installDir,
    request: {
      puuid: me.puuid,
      summonerName: me.name, // 存纯 gameName，不带 # 后缀
      rankTier: rank ? rank.tier : undefined,
      matches,
    },
  }
}

module.exports = {
  collect,
  discover,
  pickLiveCredentials,
  connectLive,
  normalizeSummoner,
  explainFailure,
  kindOf,
  portOpen,
  lcuGet,
  getJson,
}
