import request from './request'

// 房间历史消息。beforeId 用来往上翻页，不传就是最新一页
export function chatHistory(teamupId, beforeId, size) {
  return request.get(`/chat/${teamupId}/messages`, { params: { beforeId, size } })
}

/**
 * 上传聊天里的图片 / 视频 / 表情包。
 * 文件走普通 HTTP，不占 WebSocket —— 大文件塞进长连接会把它堵死。
 * kind: IMAGE（图片和表情包，≤5MB） / VIDEO（视频，≤20MB）
 */
export function uploadChatFile(teamupId, file, kind) {
  const form = new FormData()
  form.append('file', file)
  return request.post(`/chat/${teamupId}/upload?kind=${kind}`, form, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 60_000 // 视频大，10 秒的默认超时不够
  })
}

// 我所在各房间的未读数：{ 队伍id: 未读条数 }
export function unreadSummary(options) {
  return request.get('/chat/unread', options)
}

export function markRead(teamupId, lastId) {
  return request.post(`/chat/${teamupId}/read`, { lastId })
}

// 后端给的大小限制，前端不把数字写死在代码里
export function chatLimits() {
  return request.get('/chat/limits')
}
