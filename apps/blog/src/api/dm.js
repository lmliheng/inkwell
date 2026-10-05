import http from './http'

// ===== 私信（DM）REST =====

// 会话列表（含未读）
export function getDmConversations() {
  return http.get('/dm/conversations').then((res) => res.data)
}

// 与某人的历史消息
export function getDmMessages(otherId, params) {
  return http.get(`/dm/messages/${otherId}`, { params }).then((res) => res.data)
}

// 未读总数
export function getDmUnreadCount() {
  return http.get('/dm/unread-count').then((res) => res.data)
}

// 标记会话已读
export function markDmRead(otherId) {
  return http.post('/dm/read', { other_id: otherId }).then((res) => res)
}

// 我的文章列表（文章优化 Agent 选择用）
export function getMyArticlesList(params) {
  return http.get('/article/mine', { params }).then((res) => res.data)
}
