import request from './request'

// 发布开黑组队
export function publishTeamup(data) {
  return request.post('/teamup', data)
}

// 我所在的队伍（含已关闭的），退出/踢人的入口就在这里
// options 里可以传 { silent: true }：后台每 2 秒自动刷新的那些次不弹提示
export function myTeamups(options) {
  return request.get('/teamup/my', options)
}

// 招募中的组队列表，分页：{ page, size }
// options 里可以传 { silent: true }：后台每 2 秒自动刷新的那些次不弹提示
export function listTeamup(params, options) {
  return request.get('/teamup/list', { params, ...options })
}

// 加入组队
export function joinTeamup(id) {
  return request.post(`/teamup/${id}/join`)
}

// 退出组队。最后一个人退出时队伍解散，此时 data 为 null
export function leaveTeamup(id) {
  return request.post(`/teamup/${id}/leave`)
}

// 队长移除某个成员
export function kickMember(id, userId) {
  return request.post(`/teamup/${id}/kick/${userId}`)
}
