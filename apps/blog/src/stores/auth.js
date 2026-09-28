import { defineStore } from 'pinia'
import { login as apiLogin, register as apiRegister } from '@/api/auth'

const TOKEN_KEY = 'blog_token'
const USER_KEY = 'blog_user'

// 角色编号（与 docs/auth.md、任务约定一致）
// 1 = admin（超级管理员）、2 = user（普通用户）、3 = editor（编辑）
export const ROLE = {
  ADMIN: 1,
  USER: 2,
  EDITOR: 3,
}

function readUser() {
  try {
    return JSON.parse(localStorage.getItem(USER_KEY) || 'null')
  } catch {
    return null
  }
}

// 解析 JWT payload（后端 token 含 id + role_id），
// 当 user_info 里没有 role_id 时用它兜底。
function decodeJwtPayload(token) {
  if (!token) return null
  const raw = token.startsWith('Bearer ') ? token.slice(7) : token
  const parts = raw.split('.')
  if (parts.length < 2) return null
  try {
    const base64 = parts[1].replace(/-/g, '+').replace(/_/g, '/')
    const padded = base64.padEnd(Math.ceil(base64.length / 4) * 4, '=')
    const binary = atob(padded)
    const bytes = Uint8Array.from(binary, (c) => c.charCodeAt(0))
    return JSON.parse(new TextDecoder().decode(bytes))
  } catch {
    return null
  }
}

function toRoleId(value) {
  if (value === null || value === undefined || value === '') return null
  return Number(value)
}

export const useAuthStore = defineStore('auth', {
  state: () => ({
    // 存储格式：Bearer <token>
    token: localStorage.getItem(TOKEN_KEY) || '',
    user: readUser(),
  }),

  getters: {
    isLoggedIn: (state) => !!state.token,
    username: (state) => state.user?.username || '',
    // 展示名：优先昵称 → 姓名 → 用户名（供 Sidebar / 标题 / 欢迎语复用）
    displayName: (state) =>
      (state.user && (state.user.nickname || state.user.name || state.user.username)) || '',
    // 优先取 user_info.id；缺失时从 JWT payload 解码兜底
    userId(state) {
      if (state.user?.id != null) return state.user.id
      const payload = decodeJwtPayload(state.token)
      return payload?.id ?? null
    },
    // 优先取 user_info.role_id；缺失时从 JWT payload 解码兜底
    roleId(state) {
      const fromUser = toRoleId(state.user?.role_id)
      if (fromUser !== null) return fromUser
      const payload = decodeJwtPayload(state.token)
      return toRoleId(payload?.role_id)
    },
    isAdmin() {
      return this.roleId === ROLE.ADMIN
    },
    isUser() {
      return this.roleId === ROLE.USER
    },
    isEditor() {
      return this.roleId === ROLE.EDITOR
    },
    // 管理员 / 编辑可管理所有人的文章（普通用户仅能管理自己的）
    canManageAll() {
      return this.roleId === ROLE.ADMIN || this.roleId === ROLE.EDITOR
    },
  },

  actions: {
    setSession(token, user) {
      const full = token.startsWith('Bearer ') ? token : `Bearer ${token}`
      this.token = full
      this.user = user || null
      localStorage.setItem(TOKEN_KEY, full)
      localStorage.setItem(USER_KEY, JSON.stringify(this.user))
    },

    async login(account, password) {
      const { token, user } = await apiLogin(account, password)
      this.setSession(token, user)
      return user
    },

    async register(payload) {
      const { token, user } = await apiRegister(payload)
      this.setSession(token, user)
      return user
    },

    logout() {
      this.token = ''
      this.user = null
      localStorage.removeItem(TOKEN_KEY)
      localStorage.removeItem(USER_KEY)
    },
  },
})
