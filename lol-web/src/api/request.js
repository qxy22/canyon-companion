import axios from 'axios'
import { ElMessage } from 'element-plus'
import router from '../router'
import { resolveMessage, resolveBusinessMessage, MSG } from '../utils/errorMessage'

const request = axios.create({
  baseURL: '/api',
  timeout: 10000
})

// 请求拦截器：自动带上 token
request.interceptors.request.use(config => {
  const token = localStorage.getItem('token')
  if (token) {
    config.headers.Authorization = 'Bearer ' + token
  }
  return config
})

// 登录态失效时的处理：清凭证 → 回登录页，并且只提示一次。
// 并发请求同时 401 时，原来会弹出一排一模一样的提示。
let lastExpiredAt = 0

function handleSessionExpired() {
  localStorage.removeItem('token')
  localStorage.removeItem('user')

  const now = Date.now()
  const shouldNotify = now - lastExpiredAt > 3000
  lastExpiredAt = now

  if (shouldNotify) {
    ElMessage({ type: 'warning', message: MSG.unauthorized, duration: 4000 })
  }

  // 原来用 window.location.href，整页重载会丢掉当前状态；
  // 改用路由跳转，并且只在还停留在需要登录的页面上时才跳。
  if (router.currentRoute.value.meta.requiresAuth) {
    router.replace({ path: '/login', query: { expired: '1' } })
  }
}

/**
 * 统一的提示出口。
 * 传 silent: true 的请求（例如登录/注册）由页面自己把错误显示在表单里，
 * 不弹全局提示，避免同一句话出现两次。
 */
function notify(message, config) {
  if (config && config.silent) return
  ElMessage({ type: 'error', message, duration: 4000, showClose: true })
}

request.interceptors.response.use(
  response => {
    const res = response.data

    // 后端约定：HTTP 200 + code 200 才是成功
    if (res && res.code === 200) {
      return res
    }

    const message = resolveBusinessMessage(res)
    const error = new Error(message)
    error.userMessage = message
    error.businessCode = res && res.code

    if (error.businessCode === 401) {
      handleSessionExpired()
    } else {
      notify(message, response.config)
    }
    return Promise.reject(error)
  },
  error => {
    const status = error.response && error.response.status
    const message = resolveMessage(error)
    error.userMessage = message

    if (status === 401) {
      handleSessionExpired()
    } else {
      notify(message, error.config)
    }
    return Promise.reject(error)
  }
)

export default request
