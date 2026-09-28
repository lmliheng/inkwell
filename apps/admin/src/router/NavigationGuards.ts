// import { useAuthStore } from '../store/auto'
import router from './index'
import { useAuthStore } from '@/store/auth'
import { requestUserInfo, type UserInfo } from '@/composables/useRequest'
import { ElMessage } from 'element-plus'

// 从 userInfo 中取 role_id。
// /sys/profile 返回的 user_info 结构为 { user_detail: { role_id, role_name, ... }, user_permission: [...] }
const getRoleId = (userInfo: UserInfo | null | undefined) => {
    const id = userInfo?.user_detail?.role_id
    if (id === undefined || id === null) return null
    return Number(id)
}

// 判断当前用户角色是否在允许的角色列表内；roles 为空/未定义表示不限制
const hasRole = (userInfo: UserInfo | null | undefined, roles: unknown) => {
    if (!Array.isArray(roles) || roles.length === 0) return true
    const roleId = getRoleId(userInfo)
    if (roleId === null) return false
    return roles.includes(roleId)
}

/**
 * @next已经被弃用
 */
router.beforeEach(async (to, from) => {
    const whiteList = ['/auth', '/forgot-password']

    // 读取持久化的 auth（pinia-plugin-persistedstate 会存到 localStorage['auth']）
    let auth = null
    try {
        const authStr = localStorage.getItem('auth')
        auth = authStr ? JSON.parse(authStr) : null
    } catch (e) {
        auth = null
    }
    const token = auth?.token

    if (!token) {
        if (whiteList.includes(to.path)) {
            return true
        } else {
            return '/auth'
        }
    }

    // 已登录访问登录页 → 回首页
    if (to.path === '/auth') {

        return '/'
    }

    // ===== 角色权限校验 =====
    const roles = to.meta?.roles
    if (Array.isArray(roles) && roles.length > 0) {
        const authStore = useAuthStore()
        let userInfo = authStore.userInfo

        // 刷新后 store 可能是空的（userInfo 尚未拉取），尝试从 /sys/profile 拉一次
        if (!userInfo || !userInfo.user_detail) {
            try {
                const res = await requestUserInfo()
                if (res && res.code === 200 && res.user_info) {
                    authStore.setUserInfo(res.user_info)
                    userInfo = res.user_info
                }
            } catch (e) {
                // 拉取失败按无角色处理（下方会被拒绝并重定向）
            }
        }

        if (!hasRole(userInfo, roles)) {
            ElMessage.warning('没有访问该页面的权限')
            return '/'

        }
    }

    return true
})
