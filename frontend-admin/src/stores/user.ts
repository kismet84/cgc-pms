import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { UserInfo } from '@/types/user'
import { logout as authLogout } from '@/api/modules/auth'

const USER_INFO_KEY = 'cgc_pms_userinfo'
type PersistedUserInfo = Pick<UserInfo, 'userId' | 'username' | 'roles' | 'permissions' | 'roleName'>

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
    authLogout() // fire-and-forget — always clear local state regardless of API result
    userInfo.value = null
    clearUserInfo()
  }

  return {
    userInfo,
    roles,
    permissions,
    isLogin,
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
    localStorage.setItem(USER_INFO_KEY, JSON.stringify(toPersistedUserInfo(info)))
  } catch {
    if (import.meta.env.DEV) {
      console.warn('localStorage operation failed:', 'persistUserInfo')
    }
    // localStorage full or unavailable — userInfo lives only in memory
  }
}

function toPersistedUserInfo(info: UserInfo): PersistedUserInfo {
  return {
    userId: info.userId,
    username: info.username,
    roles: info.roles,
    permissions: info.permissions,
    roleName: info.roleName,
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
