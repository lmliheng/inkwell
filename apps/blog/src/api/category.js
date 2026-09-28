import http from './http'

// 全部分类（公开）
export function listCategories() {
  return http.get('/article/category/list').then((res) => res.data)
}

// 新增分类（登录）
export function addCategory(payload) {
  return http.post('/article/category/add', payload).then((res) => res.data)
}

// 更新分类（登录）
export function updateCategory(payload) {
  return http.put('/article/category/update', payload).then((res) => res.data)
}

// 删除分类（登录）
export function deleteCategory(category_id) {
  return http
    .delete('/article/category/delete', { data: { category_id } })
    .then((res) => res.data)
}
