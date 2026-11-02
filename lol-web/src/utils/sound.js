/**
 * 提示音。
 *
 * 用 Web Audio 现场合成，不引入音频文件：
 * 1. 不增加包体，也不会因为静态资源 404 或断网而静默失效；
 * 2. 这几个场景只是音高走向不同，合成比找素材更省事，也更容易调得克制。
 *
 * 三个音都刻意做得短、轻：这是网页，不是游戏客户端，响一下提醒到位就够了。
 */

const STORAGE_KEY = 'lol.sound.enabled'

let ctx = null
let enabled = localStorage.getItem(STORAGE_KEY) !== 'off'

function audio() {
  if (ctx) return ctx
  const Ctx = window.AudioContext || window.webkitAudioContext
  if (!Ctx) return null
  try {
    ctx = new Ctx()
  } catch {
    return null
  }
  return ctx
}

/**
 * 浏览器的自动播放策略：没有用户手势之前 AudioContext 是 suspended 的，
 * 直接播只会静默失败。挂一次全局手势把它唤醒，之后就一劳永逸。
 * 这两个监听都是 once，不常驻。
 */
export function armAudio() {
  const resume = () => {
    const c = audio()
    if (c && c.state === 'suspended') c.resume().catch(() => {})
    window.removeEventListener('pointerdown', resume)
    window.removeEventListener('keydown', resume)
  }
  window.addEventListener('pointerdown', resume, { once: true })
  window.addEventListener('keydown', resume, { once: true })
}

function tone(c, { f, t, d, g = 0.055, type = 'sine' }) {
  const startAt = c.currentTime + 0.02 + t
  const osc = c.createOscillator()
  const gain = c.createGain()
  osc.type = type
  osc.frequency.setValueAtTime(f, startAt)
  // 淡入淡出都用短的，避免"咔"的爆音
  gain.gain.setValueAtTime(0.0001, startAt)
  gain.gain.linearRampToValueAtTime(g, startAt + 0.015)
  gain.gain.exponentialRampToValueAtTime(0.0001, startAt + d)
  osc.connect(gain).connect(c.destination)
  osc.start(startAt)
  osc.stop(startAt + d + 0.03)
}

function play(notes) {
  if (!enabled) return
  const c = audio()
  if (!c) return
  if (c.state === 'suspended') c.resume().catch(() => {})
  notes.forEach(n => {
    try {
      tone(c, n)
    } catch {
      // 播放失败不能影响页面上的提示本身
    }
  })
}

export function isSoundEnabled() {
  return enabled
}

export function setSoundEnabled(value) {
  enabled = Boolean(value)
  localStorage.setItem(STORAGE_KEY, enabled ? 'on' : 'off')
  if (enabled) play([{ f: 880, t: 0, d: 0.1 }])
}

/** 加好友：两声轻的上行，像门铃，不打断人 */
export function playFriendAdded() {
  play([{ f: 784, t: 0, d: 0.12 }, { f: 1046, t: 0.1, d: 0.18 }])
}

/** 邀请进队：三声上行，比加好友更"有件事等你做" */
export function playInvite() {
  play([
    { f: 659, t: 0, d: 0.11 },
    { f: 784, t: 0.1, d: 0.11 },
    { f: 1046, t: 0.2, d: 0.26, g: 0.065 }
  ])
}

/** 被移出房间：下行两声、三角波，一听就知道不是好事 */
export function playKicked() {
  play([
    { f: 392, t: 0, d: 0.18, g: 0.07, type: 'triangle' },
    { f: 262, t: 0.16, d: 0.3, g: 0.07, type: 'triangle' }
  ])
}
