import { onBeforeUnmount, onMounted } from 'vue'

/**
 * 定时自动刷新。
 *
 * 为什么不能直接 setInterval(fn, 2000) 就完事：
 *
 * 1. 页面切到后台后没人看，每 2 秒照打服务器纯属浪费 —— 切走就停，切回来立刻补一次。
 * 2. 请求万一比间隔慢（网络抖动），新请求会堆在旧请求后面越排越多 —— 上一个没回来就不发下一个。
 * 3. 每 2 秒闪一次骨架屏比不刷新还难看 —— 只有第一次加载才显示加载态，之后一律静默。
 * 4. 网络一抖就弹一次红色错误条，2 秒一次的弹法是灾难 —— 自动刷新带 silent，失败了只是保持原样。
 *
 * 用法：
 *   const { refresh } = useAutoRefresh(load, 2000)
 *   // load(isManual) 返回一个 Promise；isManual 为 true 时调用方可以显示加载态
 */
export function useAutoRefresh(load, interval = 2000) {
  let timer = null
  let inFlight = false
  let stopped = false
  // 外部暂停：这个面板当前不在用户看得见的那个 tab 里。
  // 和「页面切到后台」是两件事，各自独立，两个条件都不满足时才真的发请求。
  let paused = false

  function tick(isManual) {
    // 上一次还没回来就不再发新的，避免请求排队堆积
    if (inFlight || paused) return
    inFlight = true
    Promise.resolve(load(isManual))
      .catch(() => {
        // 失败时保留页面上现有的内容，什么都不做
      })
      .finally(() => {
        inFlight = false
      })
  }

  function start() {
    if (timer !== null) return
    timer = setInterval(() => {
      if (document.hidden) return
      tick(false)
    }, interval)
  }

  function stop() {
    if (timer !== null) {
      clearInterval(timer)
      timer = null
    }
  }

  function isAllowed() {
    return !paused && !document.hidden
  }

  /**
   * 面板被切到看不见的 tab 里时调用。
   *
   * 大厅是切换式的：停在「开黑大厅」时，另一边的在线列表用户根本看不到，
   * 让它跟着一起每 2 秒请求就是纯浪费 —— 白打一半的请求量。
   */
  function setPaused(value) {
    paused = value
    if (!isAllowed()) {
      stop()
      return
    }
    start()
    // 切回来时不等下一轮，立刻补一次
    tick(false)
  }

  // 标签页切走时停掉轮询，切回来立刻补一次 —— 顺便让停了几分钟后
  // 回到页面的人马上看到最新数据，而不是等满 2 秒
  function onVisibility() {
    if (!isAllowed()) {
      stop()
    } else {
      start()
      tick(false)
    }
  }

  onMounted(() => {
    if (isAllowed()) start()
    document.addEventListener('visibilitychange', onVisibility)
  })

  onBeforeUnmount(() => {
    stopped = true
    stop()
    document.removeEventListener('visibilitychange', onVisibility)
  })

  /** 手动触发一次（点刷新按钮、刚翻页、刚加入队伍时用），会打断等待立刻拉取 */
  function refresh() {
    if (stopped) return
    tick(true)
  }

  return { refresh, start, stop, setPaused }
}
