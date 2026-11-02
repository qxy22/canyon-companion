/**
 * 错误文案归一化。
 *
 * 原来所有错误都被压成两句话：
 *   后端 → "网络异常，请稍后重试"（且不记日志，出了事无从查起）
 *   前端 → error.message 或 "网络错误"
 * 用户看到"网络异常"时，网络往往是好的。
 *
 * 这里的规则：说清发生了什么，并给出下一步能做什么。
 * 不道歉、不含糊、不把技术细节推给用户。
 */

export const MSG = {
  network: '连不上后端服务。确认 lol-server 已在 8080 端口启动。',
  timeout: '请求超时（超过 10 秒）。后端可能没启动，或读取本机游戏客户端超时。',
  unauthorized: '登录已过期，请重新登录。',
  forbidden: '当前账号没有权限做这件事。',
  notFound: '接口不存在（404）。检查前后端路径是否一致。',
  server: '后端抛异常了（500）。具体原因在 lol-server 的控制台日志里。'
}

/** 后端 GlobalExceptionHandler 的兜底文案，遇到它时替换成更有指向性的一句 */
const BACKEND_GENERIC = '网络异常，请稍后重试'

/**
 * @param {unknown} error axios 抛出的错误
 * @returns {string} 可直接展示给用户的一句话
 */
export function resolveMessage(error) {
  if (!error) return '操作失败，请重试。'

  // 已经归一化过的（业务码分支自己抛的）
  if (error.userMessage) return error.userMessage

  const res = error.response

  // 压根没拿到响应：网络层失败
  if (!res) {
    if (error.code === 'ECONNABORTED' || error.code === 'ETIMEDOUT') return MSG.timeout
    if (error.code === 'ERR_CANCELED') return '请求已取消。'
    return MSG.network
  }

  const body = res.data
  const bodyMessage = body && typeof body === 'object' ? body.message : null
  const code = body && typeof body === 'object' ? body.code : null

  if (res.status === 401 || code === 401) return MSG.unauthorized
  if (res.status === 403) return MSG.forbidden
  if (res.status === 404) return MSG.notFound
  if (res.status >= 500) {
    return bodyMessage && bodyMessage !== BACKEND_GENERIC ? bodyMessage : MSG.server
  }

  if (bodyMessage && bodyMessage !== BACKEND_GENERIC) return bodyMessage
  return `请求失败（HTTP ${res.status}）。`
}

/** 业务码分支：HTTP 200 但 code !== 200 */
export function resolveBusinessMessage(res) {
  const code = res && res.code
  const message = res && res.message

  if (code === 401) return MSG.unauthorized
  if (code >= 500) {
    return message && message !== BACKEND_GENERIC ? message : MSG.server
  }
  return message || `请求失败（code ${code}）。`
}
