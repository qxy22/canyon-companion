import { ref } from 'vue'
import { goOnline, goOfflineOnUnload, heartbeat } from '../api/presence'

/**
 * 在线状态的自动维护：进站即在线，关页即离线。
 *
 * 三件事各管一段，缺一段就会漏人：
 *   1. 打开页面 / 登录成功 → 报在线；
 *   2. 每 25 秒一次心跳 → 证明"人还在"；
 *   3. 关页面 → keepalive 请求报离线；万一没发出去，服务端 90 秒超时兜底。
 *
 * 为什么不直接监听 beforeunload：那个事件在移动端经常不触发，而且里面做异步
 * 请求会被浏览器掐掉。pagehide + keepalive fetch 是现在能可靠工作的组合。
 */

const HEARTBEAT_MS = 25_000

/** 多个标签页的登记表。关一个标签页时，别的还开着就不能报离线。 */
const TABS_KEY = 'lol_presence_tabs'

/** 超过这么久没更新的标签页视为已经关了（崩溃的那种，不会自己摘登记）。 */
const TAB_FRESH_MS = 90_000

/** 从别的标签页切回来时，隐藏超过这么久就补一次在线（可能已经超时被摘掉了）。 */
const RESUME_AFTER_MS = 60_000

/**
 * 自动把状态改掉之后 +1。Home 监听它重新拉一次资料，
 * 否则身份卡上还写着「离线」，而人其实已经在线了。
 */
export const presenceTick = ref(0)

/** 用户手动选了「游戏中」/「离线」：别再自动把他拉回在线。 */
const paused = ref(false)

let timer = null
let tabId = null
let hiddenAt = 0
let bound = false

// ---------------------------------------------------------------- 多标签页登记

function readTabs() {
  try {
    return JSON.parse(localStorage.getItem(TABS_KEY) || '{}') || {}
  } catch {
    return {}
  }
}

function writeTabs(map) {
  try {
    localStorage.setItem(TABS_KEY, JSON.stringify(map))
  } catch {
    // 隐私模式写不了 localStorage：退化成单标签页逻辑，不影响主流程
  }
}

/** 把自己这个标签页标记成"还活着"，顺手清掉早就没影的那些。 */
function touchTab() {
  const now = Date.now()
  const map = readTabs()
  for (const key of Object.keys(map)) {
    if (now - map[key] > TAB_FRESH_MS) delete map[key]
  }
  map[tabId] = now
  writeTabs(map)
}

// ---------------------------------------------------------------- 动作

function bringOnline() {
  goOnline()
    .then(() => {
      presenceTick.value += 1
    })
    .catch(() => {
      // 拦截器已经判过登录态；这里失败多半是网络，下一轮心跳会再试
    })
}

function beat() {
  if (!localStorage.getItem('token')) {
    stopPresence()
    return
  }
  touchTab()
  heartbeat()
    .then(res => {
      const online = res && res.data ? res.data.online !== false : true
      // 不在索引里了（被超时摘掉，或后台睡过头）：补一次在线。
      // 手动选了「游戏中」的人不能补，否则会把他的选择冲掉。
      if (!online && !paused.value) bringOnline()
    })
    .catch(() => {})
}

function onPageHide() {
  clearTimer()
  // 先把自己摘掉，再看还有没有别人开着 —— 多标签页时关一个不算离线
  const now = Date.now()
  const map = readTabs()
  delete map[tabId]
  for (const key of Object.keys(map)) {
    if (now - map[key] > TAB_FRESH_MS) delete map[key]
  }
  writeTabs(map)

  if (Object.keys(map).length === 0) goOfflineOnUnload()
}

function onPageShow(event) {
  // 从往返缓存（bfcache）里恢复：页面状态还在，但定时器已经被清掉了
  if (event && event.persisted) startPresence()
}

function onVisibility() {
  if (document.visibilityState === 'hidden') {
    hiddenAt = Date.now()
    return
  }
  // 合盖、长期切走之后浏览器会把定时器压到很慢，心跳可能已经超时了
  const away = hiddenAt ? Date.now() - hiddenAt : 0
  hiddenAt = 0
  touchTab()
  if (away > RESUME_AFTER_MS && !paused.value) bringOnline()
}

function clearTimer() {
  if (timer) {
    clearInterval(timer)
    timer = null
  }
}

function bindLifecycle() {
  if (bound) return
  bound = true
  // pagehide 比 beforeunload 可靠：移动端、bfcache、标签页被关都能触发
  window.addEventListener('pagehide', onPageHide)
  window.addEventListener('pageshow', onPageShow)
  document.addEventListener('visibilitychange', onVisibility)
}

// ---------------------------------------------------------------- 对外

/** 进站 / 登录成功后调用。没有 token 就什么都不做。 */
export function startPresence() {
  if (!localStorage.getItem('token')) return
  bindLifecycle()
  if (!tabId) tabId = Math.random().toString(36).slice(2) + Date.now().toString(36)
  touchTab()

  if (!timer) {
    // 立刻报一次，再开始每 25 秒续约一次
    if (!paused.value) bringOnline()
    timer = setInterval(beat, HEARTBEAT_MS)
  }
}

/**
 * 停止自动维护。
 * @param {boolean} offline 是否顺便报一次离线（退出登录时为 true；
 *                          切换账号时也应为 true，否则旧账号会一直挂着在线）
 */
export function stopPresence(offline = false) {
  clearTimer()
  paused.value = false
  if (offline) {
    const map = readTabs()
    if (tabId) delete map[tabId]
    writeTabs(map)
    // 必须用这个同步版本：调用方紧接着就会清掉 localStorage 里的 token，
    // 而 axios 的请求拦截器是在微任务里才去读 token 的 —— 那时已经读不到了，
    // 请求会以 401 收场，人还挂在在线列表里。
    goOfflineOnUnload()
  }
}

/** 用户手动切成「游戏中」/「离线」时调用，别再把他拉回在线。 */
export function pausePresence() {
  paused.value = true
}

/** 用户手动切回「在线」时调用。 */
export function resumePresence() {
  paused.value = false
}

/** 当前是否处于手动状态（给身份卡显示用，暂时没有消费方）。 */
export function isPresencePaused() {
  return paused.value
}

// 页面卸载是最后一道关卡，即使用户没登录也只是空跑。
// 注意这里**不要**监听 storage：每次心跳都会写登记表，而 storage 事件会在标签页之间
// 来回触发，一个写引发另一个写，另一个又引发它 —— 会变成每秒几百次写入的死循环。
// 登记表本来就是"读的时候现算"，不需要额外通知。
if (typeof window !== 'undefined') {
  bindLifecycle()
}
