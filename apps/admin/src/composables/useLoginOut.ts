import { useAuthStore } from '@/store/auth'
// import { pathTag } from '@/config/pathTag'
import router from '@/router'

/**
 * @退出登录
 */
export const loginOut = () => {
    const authStore = useAuthStore()
    authStore.setUserInfo({})
    authStore.setToken('')
    authStore.setTokenTime('')
    router.push('/auth')
}
