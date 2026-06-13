import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { UserInfo } from '@/types/user'

const USER_INFO_KEY = 'cgc_pms_userinfo'

export const useUserStore = defineStore('user', () => {
  const userInfo = ref<UserInfo | null>(loadUserInfo())

  const roles = computed<string[]>(() => userInfo.value?.roles ?? [])
  const permissions = computed<string[]>(() => userInfo.value?.permissions ?? [])
  const isLogin = computed<boolean>(() => !!userInfo.value)

  function setUserInfo(info: UserInfo) {
    userInfo.value = info
    persistUserInfo(info)
  }

  function hasPermission(code: string): boolean {
    return permissions.value.includes(code)
  }

  async function logout() {
    userInfo.value = null
    clearUserInfo()
  }

  // Backward-compat stubs — tokens are now HttpOnly cookies, JS never sees them.
  // These are kept so existing code calling setToken/setRefreshToken doesn't break.
  function setToken(_value: string) {
    // no-op: tokens are set via Set-Cookie by the backend
  }

  function setRefreshToken(_value: string) {
    // no-op: tokens are set via Set-Cookie by the backend
  }

  return {
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

function loadUserInfo(): UserInfo | null {
  try {
    const raw = localStorage.getItem(USER_INFO_KEY)
    if (!raw) return null
    return JSON.parse(raw) as UserInfo
  } catch {
    if (import.meta.env.DEV) {
      console.warn('localStorage operation failed:', 'loadUserInfo')
    }
    return null
  }
}

function persistUserInfo(info: UserInfo) {
  try {
    localStorage.setItem(USER_INFO_KEY, JSON.stringify(info))
  } catch {
    if (import.meta.env.DEV) {
      console.warn('localStorage operation failed:', 'persistUserInfo')
    }
    // localStorage full or unavailable — userInfo lives only in memory
  }
}

function clearUserInfo() {
  try {
    localStorage.removeItem(USER_INFO_KEY)
  } catch {
    if (import.meta.env.DEV) {
      console.warn('localStorage operation failed:', 'clearUserInfo')
    }
    // ignore
  }
}
