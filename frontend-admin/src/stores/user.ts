import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { UserInfo } from '@/types/user'

const TOKEN_KEY = 'cgc_pms_token'
const REFRESH_TOKEN_KEY = 'cgc_pms_refresh_token'

export const useUserStore = defineStore('user', () => {
  const token = ref<string>(localStorage.getItem(TOKEN_KEY) || '')
  const refreshToken = ref<string>(sessionStorage.getItem(REFRESH_TOKEN_KEY) || '')
  const userInfo = ref<UserInfo | null>(null)

  const roles = computed<string[]>(() => userInfo.value?.roles ?? [])
  const permissions = computed<string[]>(() => userInfo.value?.permissions ?? [])
  const isLogin = computed<boolean>(() => !!token.value)

  function setToken(value: string) {
    token.value = value
    localStorage.setItem(TOKEN_KEY, value)
  }

  function setRefreshToken(value: string) {
    refreshToken.value = value
    sessionStorage.setItem(REFRESH_TOKEN_KEY, value)
  }

  function setUserInfo(info: UserInfo) {
    userInfo.value = info
  }

  function hasPermission(code: string): boolean {
    return permissions.value.includes(code)
  }

  function logout() {
    token.value = ''
    refreshToken.value = ''
    userInfo.value = null
    localStorage.removeItem(TOKEN_KEY)
    sessionStorage.removeItem(REFRESH_TOKEN_KEY)
  }

  return {
    token,
    refreshToken,
    userInfo,
    roles,
    permissions,
    isLogin,
    setToken,
    setRefreshToken,
    setUserInfo,
    hasPermission,
    logout,
  }
})
