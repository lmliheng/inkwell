import http from './http'

/**
 * 社交互动 API：关注 / 点赞 / 收藏 / 互动通知
 * 需要登录的接口由 axios 拦截器自动携带 Authorization。
 */

// ===== 关注 =====

// 关注 / 取消关注（toggle）
export function toggleFollow(username) {
  return http.post(`/social/follow/${encodeURIComponent(username)}`).then((res) => res.data)
}

// 某用户关注列表（公开）
export function getFollowing(username) {
  return http.get(`/social/following/${encodeURIComponent(username)}`).then((res) => res.data)
}

// 某用户粉丝列表（公开）
export function getFollowers(username) {
  return http.get(`/social/followers/${encodeURIComponent(username)}`).then((res) => res.data)
}

// 关注/粉丝统计 + 是否已关注（公开，未登录也可）
export function getSocialStats(username) {
  return http.get(`/social/stats/${encodeURIComponent(username)}`).then((res) => res.data)
}

// ===== 点赞 =====

export function toggleLike(articleId) {
  return http.post(`/social/like/${articleId}`).then((res) => res.data)
}

// ===== 收藏 =====

export function toggleFavorite(articleId) {
  return http.post(`/social/favorite/${articleId}`).then((res) => res.data)
}

// 我的收藏列表（私有）
export function getMyFavorites() {
  return http.get('/social/my-favorites').then((res) => res.data)
}

// 批量查询当前用户对若干文章的点赞/收藏状态
export function getSocialStatus(ids) {
  return http.get('/social/status', { params: { ids: (ids || []).join(',') } }).then((res) => res.data)
}

// ===== 互动通知 =====

export function getNotifications(params) {
  return http.get('/social/notifications', { params }).then((res) => res.data)
}

export function getUnreadCount() {
  return http.get('/social/notifications/unread-count').then((res) => res.data)
}

export function markNotificationRead(id) {
  return http.post('/social/notifications/read', { id }).then((res) => res.data)
}

export function markAllNotificationsRead() {
  return http.post('/social/notifications/read', { all: true }).then((res) => res.data)
}
