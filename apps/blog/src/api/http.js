import axios from 'axios'

const baseURL = import.meta.env.VITE_API_BASE || 'http://127.0.0.1:7000'

export class ApiError extends Error {
  constructor(message, code = 0) {
    super(message)
    this.name = 'ApiError'
    this.code = code
  }
}

const http = axios.create({
  baseURL,
  timeout: 15000,
})

const TOKEN_KEY = 'blog_token'

function getToken() {
  return localStorage.getItem(TOKEN_KEY) || ''
}

// 请求拦截器：统一携带 Authorization: Bearer <token>
http.interceptors.request.use((config) => {
  const token = getToken()
  if (token) {
    config.headers.Authorization = token
  }
  return config
})

function toMessage(error) {
  const body = error.response?.data
  if (body?.message) return body.message
  if (error.code === 'ECONNABORTED') return '请求超时，请稍后重试'
  if (error.message === 'Network Error') return '网络连接失败，请确认后端已启动'
  return '请求失败'
}

// 响应拦截器：解包 { code, success, message, data }，失败时统一抛 ApiError
http.interceptors.response.use(
  (response) => {
    const body = response.data
    if (body && typeof body === 'object' && 'success' in body) {
      if (body.success === false || (typeof body.code === 'number' && body.code >= 400)) {
        return Promise.reject(new ApiError(body.message || '请求失败', body.code))
      }
      return body
    }
    return body
  },
  (error) => {
    const status = error.response?.status
    const body = error.response?.data
    const message = toMessage(error)

    // 认证过期：清除本地会话并回到登录页（登录/注册自身的 401 除外）
    if (status === 401) {
      const url = error.config?.url || ''
      if (!url.includes('/sys/login') && !url.includes('/sys/register')) {
        localStorage.removeItem(TOKEN_KEY)
        localStorage.removeItem('blog_user')
        if (window.location.pathname !== '/login') {
          window.location.href = '/login?expired=1'
        }
      }
    }

    return Promise.reject(new ApiError(message, body?.code || status || 0))
  },
)

export default http
