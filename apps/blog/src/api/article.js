import http from './http'

// 公开文章列表：query: page / pageSize / category_id / keyword
export function listArticles(params) {
  return http.get('/article/list', { params }).then((res) => res.data)
}

// 文章详情（含作者、分类、正文）
export function getArticle(id) {
  return http.get(`/article/detail/${id}`).then((res) => res.data)
}

// 新增文章：body { title, content, category_ids: [] }
export function addArticle(payload) {
  return http.post('/article/add', payload).then((res) => res.data)
}

// 更新文章（作者本人或 admin/editor）
export function updateArticle(id, payload) {
  return http.put(`/article/update/${id}`, payload).then((res) => res.data)
}

// 删除文章（作者本人或 admin/editor）
export function deleteArticle(id) {
  return http.delete(`/article/delete/${id}`).then((res) => res.data)
}

// 我的文章列表（admin/editor 返回全部）
export function listMyArticles() {
  return http.get('/article/mine').then((res) => res.data)
}

// 归档：公开文章按时间倒序平铺（可选 username 过滤）
export function getArticleArchive(params) {
  return http.get('/article/archive', { params }).then((res) => res.data)
}
