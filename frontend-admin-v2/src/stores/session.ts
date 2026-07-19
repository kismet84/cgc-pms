import type { LoginParams, SessionStatus, UserInfo } from '@cgc-pms/frontend-contracts'
import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { getCurrentUser, login as loginRequest, logout as logoutRequest } from '@/services/auth'
import type { RequestNotice } from '@/services/request'

type SessionCacheClearer = () => void | Promise<void>
const cacheClearers = new Set<SessionCacheClearer>()

export function registerSessionCacheClearer(clearer: SessionCacheClearer): () => void {
  cacheClearers.add(clearer)
  return () => cacheClearers.delete(clearer)
}

export const useSessionStore = defineStore('v2-session', () => {
  const userInfo = ref<UserInfo | null>(null)
  const status = ref<SessionStatus>('idle')
  const requestNotice = ref<RequestNotice | null>(null)
  let restoreTask: Promise<UserInfo | null> | null = null

  const isAuthenticated = computed(
    () => status.value === 'authenticated' && userInfo.value !== null,
  )
  const roles = computed(() => userInfo.value?.roles ?? [])
  const permissions = computed(() => userInfo.value?.permissions ?? [])

  async function login(params: LoginParams): Promise<UserInfo> {
    status.value = 'authenticating'
    requestNotice.value = null
    try {
      const result = await loginRequest(params)
      userInfo.value = result.userInfo
      status.value = 'authenticated'
      return result.userInfo
    } catch (error) {
      userInfo.value = null
      status.value = 'anonymous'
      throw error
    }
  }

  async function restore(): Promise<UserInfo | null> {
    if (userInfo.value && status.value === 'authenticated') return userInfo.value
    if (restoreTask) return restoreTask

    status.value = 'restoring'
    restoreTask = getCurrentUser()
      .then((currentUser) => {
        userInfo.value = currentUser
        status.value = 'authenticated'
        return currentUser
      })
      .catch(() => {
        userInfo.value = null
        status.value = 'anonymous'
        return null
      })
      .finally(() => {
        restoreTask = null
      })
    return restoreTask
  }

  async function logout(): Promise<void> {
    status.value = 'signing-out'
    try {
      await logoutRequest()
    } finally {
      await clearSession()
    }
  }

  async function clearSession(notice?: RequestNotice): Promise<void> {
    userInfo.value = null
    status.value = 'anonymous'
    requestNotice.value = notice ?? null
    await Promise.all([...cacheClearers].map((clearer) => Promise.resolve(clearer())))
  }

  function setRequestNotice(notice: RequestNotice | null): void {
    requestNotice.value = notice
  }

  function hasPermission(code: string): boolean {
    return permissions.value.includes('*') || permissions.value.includes(code)
  }

  return {
    userInfo,
    status,
    requestNotice,
    isAuthenticated,
    roles,
    permissions,
    login,
    restore,
    logout,
    clearSession,
    setRequestNotice,
    hasPermission,
  }
})
