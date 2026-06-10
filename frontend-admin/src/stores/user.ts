import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { UserInfo } from '@/types/user'

const TOKEN_KEY = 'cgc_pms_token'

export const useUserStore = defineStore('user', () => {
  const token = ref<string>(localStorage.getItem(TOKEN_KEY) || '')
  const userInfo = ref<UserInfo | null>(null)

  const roles = computed<string[]>(() => userInfo.value?.roles ?? [])
  const permissions = computed<string[]>(() => userInfo.value?.permissions ?? [])
  const isLogin = computed<boolean>(() => !!token.value)

  function setToken(value: string) {
    token.value = value
    localStorage.setItem(TOKEN_KEY, value)
  }

  function setUserInfo(info: UserInfo) {
    userInfo.value = info
  }

  function hasPermission(code: string): boolean {
    return permissions.value.includes(code)
  }

  function logout() {
    token.value = ''
    userInfo.value = null
    localStorage.removeItem(TOKEN_KEY)
  }

  return {
    token,
    userInfo,
    roles,
    permissions,
    isLogin,
    setToken,
    setUserInfo,
    hasPermission,
    logout,
  }
})
