import request from './request'

// 获取当前用户交友资料
export function getProfile() {
  return request.get('/user/profile')
}

// 获取任意用户的公开资料（点击组员头像、进入玩家资料页时使用）
export function getUserProfile(id) {
  return request.get(`/user/${id}/profile`)
}

// 更新资料（传了哪个字段更新哪个）
export function updateProfile(data) {
  return request.put('/user/profile', data)
}

// 切换在线状态：ONLINE / OFFLINE / BUSY
export function updateStatus(status) {
  return request.put('/user/status', { status })
}

/**
 * 更换头像。
 * 和召唤师名、段位一个道理：头像的唯一来源是这个上传接口，
 * updateProfile 里手填图片地址会被后端拒绝 —— 否则别人可以拿外站链接当头像，
 * 顺带把自己的服务器变成一个访问探针。
 * 大小上限 2MB，格式 png / jpg / jpeg / gif / webp。
 */
export function uploadAvatar(file) {
  const form = new FormData()
  form.append('file', file)
  return request.post('/user/avatar', form, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 30_000
  })
}

// 删掉当前头像，回到昵称首字的默认头像
export function removeAvatar() {
  return request.delete('/user/avatar')
}
