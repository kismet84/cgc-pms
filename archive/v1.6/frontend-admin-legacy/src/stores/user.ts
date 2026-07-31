import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { UserInfo } from '@/types/user'
import { logout as authLogout } from '@/api/modules/auth'
import { useReferenceStore } from './reference'

const USER_INFO_KEY = 'cgc_pms_userinfo'

export const useUserStore = defineStore('user', () => {
  const userInfo = ref<UserInfo | null>(loadUserInfo())

  const roles = computed<string[]>(() => userInfo.value?.roles ?? [])
  const permissions = computed<string[]>(() => userInfo.value?.permissions ?? [])
  const isLogin = computed<boolean>(() => !!userInfo.value)

  function setUserInfo(info: UserInfo) {
    userInfo.value = info
    clearUserInfo()
  }

  function hasPermission(code: string): boolean {
    return permissions.value.includes('*') || permissions.value.includes(code)
  }

  async function logout() {
    authLogout() // fire-and-forget — always clear local state regardless of API result
    userInfo.value = null
    clearUserInfo()
    // 清除参考数据localStorage缓存，防止登出后残留
    try {
      const referenceStore = useReferenceStore()
      referenceStore.invalidateProjects()
      referenceStore.invalidateContracts()
      referenceStore.invalidatePartners()
      referenceStore.invalidateMaterials()
    } catch (error) {
      if (import.meta.env.DEV) {
        console.warn('reference cache cleanup failed during logout', error)
      }
    }
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
  clearUserInfo()
  return null
}

function clearUserInfo() {
  try {
    sessionStorage.removeItem(USER_INFO_KEY)
  } catch (error) {
    if (import.meta.env.DEV) {
      console.warn('sessionStorage operation failed:', 'clearUserInfo', error)
    }
    // ignore
  }
}
