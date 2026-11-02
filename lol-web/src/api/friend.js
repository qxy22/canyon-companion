import request from './request'

// 我的好友列表（含资料，用于侧边抽屉）
export function friendList(options) {
  return request.get('/friend/list', options)
}

// 好友数（顶栏徽标用，只数行不取资料）
export function friendCount(options) {
  return request.get('/friend/count', options)
}

// 我和某人是不是好友（玩家资料页决定按钮文案）
export function isFriend(userId, options) {
  return request.get(`/friend/${userId}/exists`, options)
}

// 加好友。成功后对方会立刻收到提醒（带提示音）
export function addFriend(userId) {
  return request.post(`/friend/${userId}`)
}

// 删好友
export function removeFriend(userId) {
  return request.delete(`/friend/${userId}`)
}

// 邀请好友加入我当前的队伍
export function inviteFriend(userId) {
  return request.post(`/friend/${userId}/invite`)
}
