import request from './request'

// 多维搜索：position/rankTier/gender/keyword/page/size
export function search(params) {
  return request.get('/search', { params })
}

// 在线可开黑用户列表，分页：{ page, size }
// options 里可以传 { silent: true }：后台每 2 秒自动刷新的那些次不弹提示
export function onlineList(params, options) {
  return request.get('/online/list', { params, ...options })
}

// 匹配推荐，分页：{ page, size }
export function recommend(params, options) {
  return request.get('/match/recommend', { params, ...options })
}

// 战绩录入（本地读取器上传）
export function importMatches(data) {
  return request.post('/match/import', data)
}

// 战绩录入（网页按钮触发，后端直接连本地客户端读取）
//
// 网页端已经不再调用它：云端后端读不到用户电脑上的 LCU，录入统一走桌面客户端
// （客户端 direct 调 /match/import）。接口和后端都还在，留给本机调试与集成脚本。
export function syncMatches() {
  return request.post('/match/sync')
}

// 查看某用户战绩
export function getUserMatches(userId, params) {
  return request.get(`/user/${userId}/matches`, { params })
}
