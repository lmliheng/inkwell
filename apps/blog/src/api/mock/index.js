// axios-mock-adapter 拦截器：按 blog-api.md 契约模拟后端行为。
// 联调时把 .env 里的 VITE_USE_MOCK 改为 false 即可直连真实后端。

import MockAdapter from 'axios-mock-adapter'
import http from '../http'
import { db, persist, nextId, resetDb } from './db'

let installed = false

function ok(data = {}, message = 'ok') {
  return [200, { code: 200, success: true, message, data }]
}

function fail(code, message) {
  return [code, { code, success: false, message }]
}

// 从 Authorization: Bearer mock-token-<id> 解析用户
function getUserFromRequest(config) {
  const auth = (config.headers && (config.headers.Authorization || config.headers.authorization)) || ''
  const match = auth.match(/^Bearer\s+(.+)$/i)
  if (!match) return null
  const token = match[1]
  const m = token.match(/^mock-token-(.+)$/)
  if (!m) return null
  return db.users.find((u) => u.id === m[1]) || null
}

function publicUser(u) {
  return {
    id: u.id,
    username: u.username,
    email: u.email,
    role_id: u.role_id,
    avatar: u.avatar || null,
    nickname: u.nickname || null,
    name: u.name || null,
  }
}

function categoryName(id) {
  const c = db.categories.find((c) => c.category_id === id)
  return c ? c.name : ''
}

function decorate(article) {
  const author = db.users.find((u) => u.id === article.user_id)
  return {
    ...article,
    author_name: author ? author.username : '未知作者',
    category_names: (article.category_ids || []).map(categoryName),
  }
}

function buildCommentTree(articleId) {
  const list = db.comments
    .filter((c) => c.article_id === articleId)
    .slice()
    .sort((a, b) => new Date(a.created_at) - new Date(b.created_at))
  const map = {}
  list.forEach((c) => {
    map[c.comment_id] = { ...c, children: [] }
  })
  const roots = []
  list.forEach((c) => {
    if (c.parent_id && map[c.parent_id]) {
      map[c.parent_id].children.push(map[c.comment_id])
    } else {
      roots.push(map[c.comment_id])
    }
  })
  return roots
}

export function setupMock() {
  if (installed) return
  if (import.meta.env.VITE_USE_MOCK !== 'true') return
  installed = true

  const mock = new MockAdapter(http, { delayResponse: 200, onNoMatch: 'passthrough' })

  /* ============ 认证 ============ */
  mock.onPost('/sys/login').reply((config) => {
    const body = JSON.parse(config.data || '{}')
    const user = body.email
      ? db.users.find((u) => u.email === body.email)
      : db.users.find((u) => u.username === body.username)
    if (!user || user.password !== body.password) {
      return fail(401, '用户名/邮箱或密码错误')
    }
    return [
      200,
      {
        code: 200,
        success: true,
        message: '登录成功',
        token: `mock-token-${user.id}`,
        user_info: publicUser(user),
      },
    ]
  })

  mock.onPost('/sys/register').reply((config) => {
    const body = JSON.parse(config.data || '{}')
    if (!body.username || !body.email || !body.password) {
      return fail(400, '用户名、邮箱或密码不能为空')
    }
    if (db.users.some((u) => u.email === body.email)) return fail(400, '邮箱已存在')
    if (db.users.some((u) => u.username === body.username)) return fail(400, '用户名已存在')
    const user = {
      id: nextId('u'),
      username: body.username,
      email: body.email,
      password: body.password,
      role_id: 2, // 新注册用户默认普通用户
    }
    db.users.push(user)
    persist()
    return [
      200,
      {
        code: 200,
        success: true,
        message: '注册成功',
        token: `mock-token-${user.id}`,
        user_info: publicUser(user),
      },
    ]
  })

  /* ============ 文章 ============ */
  mock.onGet('/article/list').reply((config) => {
    const p = config.params || {}
    const page = Number(p.page) || 1
    const pageSize = Number(p.pageSize) || 9
    const categoryId = p.category_id ? String(p.category_id) : ''
    const keyword = String(p.keyword || '').trim().toLowerCase()

    let list = db.articles
      .filter((a) => a.status === 1)
      .filter((a) => (categoryId ? (a.category_ids || []).includes(categoryId) : true))
      .filter((a) => {
        if (!keyword) return true
        const catNames = (a.category_ids || []).map(categoryName).join(' ')
        return (
          a.title.toLowerCase().includes(keyword) ||
          a.content.toLowerCase().includes(keyword) ||
          catNames.toLowerCase().includes(keyword)
        )
      })
      .slice()
      .sort((a, b) => new Date(b.created_at) - new Date(a.created_at))

    const total = list.length
    const start = (page - 1) * pageSize
    list = list.slice(start, start + pageSize).map(decorate)

    return ok({ list, total, page, pageSize }, '获取成功')
  })

  mock.onGet(/\/article\/detail\/([^/?]+)/).reply((config) => {
    const id = config.url.match(/\/article\/detail\/([^/?]+)/)[1]
    const article = db.articles.find((a) => a.article_id === id)
    if (!article) return fail(404, '文章不存在')
    if (article.status !== 1) return fail(400, '文章不是公开的')
    return ok(decorate(article), '获取成功')
  })

  mock.onGet('/article/mine').reply((config) => {
    const user = getUserFromRequest(config)
    if (!user) return fail(401, '未登录或登录过期')
    const canAll = user.role_id === 1 || user.role_id === 3
    let list = db.articles
      .filter((a) => (canAll ? true : a.user_id === user.id))
      .slice()
      .sort((a, b) => new Date(b.created_at) - new Date(a.created_at))
      .map(decorate)
    return ok({ list, total: list.length }, '获取成功')
  })

  mock.onPost('/article/add').reply((config) => {
    const user = getUserFromRequest(config)
    if (!user) return fail(401, '未登录或登录过期')
    const body = JSON.parse(config.data || '{}')
    if (!body.title || !body.content) return fail(400, '标题和内容不能为空')
    const category_ids = Array.isArray(body.category_ids) ? body.category_ids : []
    const article = {
      article_id: nextId('a'),
      title: body.title,
      content: body.content,
      status: 1,
      user_id: user.id,
      category_ids,
      created_at: new Date().toISOString(),
      updated_at: new Date().toISOString(),
    }
    db.articles.push(article)
    persist()
    return ok(decorate(article), '添加成功')
  })

  mock.onPut(/\/article\/update\/([^/?]+)/).reply((config) => {
    const user = getUserFromRequest(config)
    if (!user) return fail(401, '未登录或登录过期')
    const id = config.url.match(/\/article\/update\/([^/?]+)/)[1]
    const article = db.articles.find((a) => a.article_id === id)
    if (!article) return fail(404, '文章不存在')
    const canAll = user.role_id === 1 || user.role_id === 3
    if (!canAll && article.user_id !== user.id) return fail(403, '无权操作该文章')
    const body = JSON.parse(config.data || '{}')
    if (body.title !== undefined) article.title = body.title
    if (body.content !== undefined) article.content = body.content
    if (body.category_ids !== undefined) article.category_ids = body.category_ids
    article.updated_at = new Date().toISOString()
    persist()
    return ok(decorate(article), '更新成功')
  })

  mock.onDelete(/\/article\/delete\/([^/?]+)/).reply((config) => {
    const user = getUserFromRequest(config)
    if (!user) return fail(401, '未登录或登录过期')
    const id = config.url.match(/\/article\/delete\/([^/?]+)/)[1]
    const article = db.articles.find((a) => a.article_id === id)
    if (!article) return fail(404, '文章不存在')
    const canAll = user.role_id === 1 || user.role_id === 3
    if (!canAll && article.user_id !== user.id) return fail(403, '无权操作该文章')
    db.articles = db.articles.filter((a) => a.article_id !== id)
    db.comments = db.comments.filter((c) => c.article_id !== id)
    persist()
    return ok({ article_id: id }, '删除成功')
  })

  /* ============ 博客主页 ============ */
  mock.onGet(/\/blog\/profile\/([^/?]+)/).reply((config) => {
    const username = decodeURIComponent(
      config.url.match(/\/blog\/profile\/([^/?]+)/)[1],
    )
    const u = db.users.find((x) => x.username === username)
    if (!u) return fail(404, '用户不存在')

    const user = {
      id: u.id,
      username: u.username,
      nickname: u.nickname || u.username,
      name: u.name || u.nickname || u.username,
      avatar: u.avatar || '',
      bio: u.bio || '',
      region: u.region || '',
    }

    const p = config.params || {}
    const page = Number(p.page) || 1
    const pageSize = Number(p.pageSize) || 9
    const all = db.articles
      .filter((a) => a.user_id === u.id && a.status === 1)
      .slice()
      .sort((a, b) => new Date(b.created_at) - new Date(a.created_at))
    const total = all.length
    const start = (page - 1) * pageSize
    const list = all.slice(start, start + pageSize).map(decorate)

    return ok({ user, articles: { list, total, page, pageSize } }, '获取成功')
  })

  /* ============ 分类 ============ */
  mock.onGet('/article/category/list').reply(() => {
    const list = db.categories.map((c) => ({
      ...c,
      user_name: (db.users.find((u) => u.id === c.user_id) || {}).username || '',
    }))
    return ok({ list }, '获取成功')
  })

  mock.onPost('/article/category/add').reply((config) => {
    const user = getUserFromRequest(config)
    if (!user) return fail(401, '未登录或登录过期')
    const body = JSON.parse(config.data || '{}')
    const name = (body.category_name || body.name || '').trim()
    if (!name) return fail(400, '分类名称不能为空')
    const category = { category_id: nextId('cat'), name, user_id: user.id }
    db.categories.push(category)
    persist()
    return ok(category, '添加成功')
  })

  mock.onPut('/article/category/update').reply((config) => {
    const user = getUserFromRequest(config)
    if (!user) return fail(401, '未登录或登录过期')
    const body = JSON.parse(config.data || '{}')
    const category = db.categories.find((c) => c.category_id === String(body.category_id))
    if (!category) return fail(404, '分类不存在')
    const canAll = user.role_id === 1 || user.role_id === 3
    if (!canAll && category.user_id !== user.id) return fail(403, '无权操作该分类')
    if (!body.category_name) return fail(400, '分类名称不能为空')
    category.name = body.category_name
    persist()
    return ok(category, '更新成功')
  })

  mock.onDelete('/article/category/delete').reply((config) => {
    const user = getUserFromRequest(config)
    if (!user) return fail(401, '未登录或登录过期')
    const body = JSON.parse(config.data || '{}')
    const categoryId = String(body.category_id)
    const category = db.categories.find((c) => c.category_id === categoryId)
    if (!category) return fail(404, '分类不存在')
    const canAll = user.role_id === 1 || user.role_id === 3
    if (!canAll && category.user_id !== user.id) return fail(403, '无权操作该分类')
    db.categories = db.categories.filter((c) => c.category_id !== categoryId)
    persist()
    return ok({ category_id: categoryId }, '删除成功')
  })

  /* ============ 评论（匿名 + 楼中楼）============ */
  mock.onGet(/\/comment\/list\/([^/?]+)/).reply((config) => {
    const articleId = config.url.match(/\/comment\/list\/([^/?]+)/)[1]
    const list = buildCommentTree(articleId)
    return ok({ list, total: list.length }, '获取成功')
  })

  mock.onPost('/comment/add').reply((config) => {
    const user = getUserFromRequest(config)
    const body = JSON.parse(config.data || '{}')
    if (!body.article_id || !body.content) return fail(400, '文章和评论内容不能为空')
    const article = db.articles.find((a) => a.article_id === String(body.article_id))
    if (!article) return fail(404, '文章不存在')

    let nickname = body.nickname
    if (user) {
      nickname = user.username
    } else if (!nickname || !String(nickname).trim()) {
      return fail(400, '匿名评论请填写昵称')
    }

    const comment = {
      comment_id: nextId('c'),
      article_id: String(body.article_id),
      user_id: user ? user.id : null,
      nickname: nickname || null,
      content: body.content,
      parent_id: body.parent_id || null,
      created_at: new Date().toISOString(),
    }
    db.comments.push(comment)
    persist()
    return ok({ ...comment, children: [] }, '评论成功')
  })

  // 联调辅助：重置 mock 数据（控制台可用）
  if (import.meta.env.DEV) {
    window.__resetBlogMock = () => {
      resetDb()
      window.location.reload()
    }
  }
}
