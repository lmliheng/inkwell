import http from './http'

/**
 * 博客个人主页（公开）。
 * GET /blog/profile/:username
 * 返回 data = { user: {...}, articles: { list, total, page, pageSize } }
 * 支持 query：page / pageSize（作用于 articles 分页）。
 */
export function getBlogProfile(username, params) {
  return http
    .get(`/blog/profile/${encodeURIComponent(username)}`, { params })
    .then((res) => res.data)
}

/**
 * 博客首页用户列表（公开）。
 * GET /blog/users
 * 返回 data = { list: [{id, username, avatar, bio, name, area, vip, article_count}], total, page, pageSize }
 */
export function getBlogUsers(params) {
  return http
    .get('/blog/users', { params })
    .then((res) => res.data)
}

/**
 * 某用户的已发布文章分页（公开）。
 * GET /blog/articles/:username
 * 返回 data = { list, total, page, pageSize }
 */
export function getBlogArticles(username, params) {
  return http
    .get(`/blog/articles/${encodeURIComponent(username)}`, { params })
    .then((res) => res.data)
}

/**
 * 全站最新文章流（门户首页）。
 * GET /blog/feed?limit=6
 * 返回 data = { list }
 */
export function getBlogFeed(params) {
  return http
    .get('/blog/feed', { params })
    .then((res) => res.data)
}

/**
 * 全站热议文章（按评论数排序，门户首页热门榜）。
 * GET /blog/hot?limit=6
 * 返回 data = { list }
 */
export function getBlogHot(params) {
  return http
    .get('/blog/hot', { params })
    .then((res) => res.data)
}
