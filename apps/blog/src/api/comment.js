import http from './http'

// 某文章评论列表（顶层分页），返回 { list, total, page, pageSize }，children 为楼中楼树
export function listComments(articleId, params = {}) {
  return http.get(`/comment/list/${articleId}`, { params }).then((res) => res.data)
}

// 发表评论：body { article_id, content, parent_id?, nickname? }
// 登录用户不用传 nickname（后端取用户名）；匿名需传 nickname
export function addComment(payload) {
  return http.post('/comment/add', payload).then((res) => res.data)
}
