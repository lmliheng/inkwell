import { ref, computed, type Ref } from 'vue'
import { defineStore } from 'pinia'
import { type UserItem, type Permission } from '@/composables/useRequest'
import { type UserInfo } from '@/composables/useRequest'

export const useAuthStore = defineStore('auth', () => {

  const token = ref('')

  const setToken = (newToken: string) => {
    token.value = newToken
  }

  const userInfo: Ref<UserInfo> = ref({})

  const setUserInfo = (newUserInfo: UserInfo) => {
    userInfo.value = newUserInfo
  }

  const tokenTime = ref('')

  const setTokenTime = (newTokenTime: string) => {
    tokenTime.value = newTokenTime
  }

  const rememberMe = ref(true)

  const setRememberMe = (val: boolean) => {
    rememberMe.value = val
  }

  return { token, setToken, userInfo, setUserInfo, tokenTime, setTokenTime, rememberMe, setRememberMe }
}, {
  persist: true,
}
)
