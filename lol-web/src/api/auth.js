import request from './request'

// 登录/注册传 silent: true —— 错误由页面显示在表单里（行内 + 横幅），
// 不再弹全局提示，避免同一句话出现两次。
export function login(data) {
  return request.post('/auth/login', data, { silent: true })
}

export function register(data) {
  return request.post('/auth/register', data, { silent: true })
}

export function getUserInfo() {
  return request.get('/user/info')
}
