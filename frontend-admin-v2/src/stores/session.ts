import type { LoginParams, SessionStatus, UserInfo } from '@cgc-pms/frontend-contracts'
import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { getCurrentUser, login as loginRequest, logout as logoutRequest } from '@/services/auth'
import { isApiClientError, type RequestNotice } from '@/services/request'

type SessionCacheClearer = () => void | Promise<void>
const cacheClearers = new Set<SessionCacheClearer>()
let activeIdentity: { tenantId: string; userId: string } | null = null
const OFFLINE_SESSION_KEY = 'cgc-pms-offline-session'
const OFFLINE_SESSION_TTL_MS = 8 * 60 * 60 * 1_000

export function hasAdminRole(roles: readonly string[]): boolean {
  return roles.includes('ADMIN') || roles.includes('SUPER_ADMIN')
}

export function registerSessionCacheClearer(clearer: SessionCacheClearer): () => void {
  cacheClearers.add(clearer)
  return () => cacheClearers.delete(clearer)
}

export function getSessionNamespaceIdentity(): { tenantId: string; userId: string } | null {
  return activeIdentity
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
  const isAdmin = computed(() => hasAdminRole(roles.value))

  async function login(params: LoginParams): Promise<UserInfo> {
    status.value = 'authenticating'
    requestNotice.value = null
    try {
      const result = await loginRequest(params)
      const nextIdentity = identity(result.userInfo)
      if (
        activeIdentity &&
        (activeIdentity.tenantId !== nextIdentity.tenantId ||
          activeIdentity.userId !== nextIdentity.userId)
      ) {
        await clearProtectedCaches()
      }
      activeIdentity = nextIdentity
      userInfo.value = result.userInfo
      status.value = 'authenticated'
      rememberOfflineSession(result.userInfo)
      return result.userInfo
    } catch (error) {
      activeIdentity = null
      userInfo.value = null
      status.value = 'anonymous'
      throw error
    }
  }

  async function restore(): Promise<UserInfo | null> {
    if (userInfo.value && status.value === 'authenticated') return userInfo.value
    if (restoreTask) return restoreTask

    status.value = 'restoring'
    const offline = readOfflineSession()
    if (!navigator.onLine && offline) {
      replaceUserInfo(offline)
      return offline
    }
    restoreTask = getCurrentUser(false)
      .then((currentUser) => {
        activeIdentity = identity(currentUser)
        userInfo.value = currentUser
        status.value = 'authenticated'
        rememberOfflineSession(currentUser)
        return currentUser
      })
      .catch((error: unknown) => {
        if (offline && isApiClientError(error) && error.code === 'NETWORK_ERROR') {
          replaceUserInfo(offline)
          return offline
        }
        forgetOfflineSession()
        activeIdentity = null
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
    await clearProtectedCaches()
    userInfo.value = null
    status.value = 'anonymous'
    requestNotice.value = notice ?? null
    activeIdentity = null
    forgetOfflineSession()
  }

  function setRequestNotice(notice: RequestNotice | null): void {
    requestNotice.value = notice
  }

  function replaceUserInfo(currentUser: UserInfo): void {
    activeIdentity = identity(currentUser)
    userInfo.value = currentUser
    status.value = 'authenticated'
    rememberOfflineSession(currentUser)
  }

  function hasPermission(code: string): boolean {
    return permissions.value.includes('*') || permissions.value.includes(code)
  }

  function hasAdminOrPermission(code: string): boolean {
    return hasAdminRole(roles.value) || hasPermission(code)
  }

  return {
    userInfo,
    status,
    requestNotice,
    isAuthenticated,
    roles,
    permissions,
    isAdmin,
    login,
    restore,
    logout,
    clearSession,
    setRequestNotice,
    replaceUserInfo,
    hasPermission,
    hasAdminOrPermission,
  }
})

async function clearProtectedCaches(): Promise<void> {
  await Promise.allSettled([...cacheClearers].map((clearer) => Promise.resolve(clearer())))
}

function identity(userInfo: UserInfo): { tenantId: string; userId: string } {
  return { tenantId: String(userInfo.tenantId), userId: userInfo.userId }
}

function rememberOfflineSession(userInfo: UserInfo): void {
  sessionStorage.setItem(
    OFFLINE_SESSION_KEY,
    JSON.stringify({
      expiresAt: Date.now() + OFFLINE_SESSION_TTL_MS,
      userInfo: {
        tenantId: userInfo.tenantId,
        userId: userInfo.userId,
        username: 'offline',
        roles: userInfo.roles,
        permissions: userInfo.permissions,
      } satisfies UserInfo,
    }),
  )
}

function readOfflineSession(): UserInfo | null {
  try {
    const value = JSON.parse(sessionStorage.getItem(OFFLINE_SESSION_KEY) || 'null') as unknown
    if (!value || typeof value !== 'object') return null
    const snapshot = value as { expiresAt?: unknown; userInfo?: unknown }
    if (typeof snapshot.expiresAt !== 'number' || snapshot.expiresAt <= Date.now()) {
      forgetOfflineSession()
      return null
    }
    const candidate = snapshot.userInfo as Partial<UserInfo> | undefined
    if (
      !candidate ||
      typeof candidate.tenantId !== 'string' ||
      typeof candidate.userId !== 'string' ||
      !Array.isArray(candidate.roles) ||
      !Array.isArray(candidate.permissions)
    )
      return null
    return {
      tenantId: candidate.tenantId,
      userId: candidate.userId,
      username: 'offline',
      roles: candidate.roles.filter((role): role is string => typeof role === 'string'),
      permissions: candidate.permissions.filter(
        (permission): permission is string => typeof permission === 'string',
      ),
    }
  } catch {
    return null
  }
}

function forgetOfflineSession(): void {
  sessionStorage.removeItem(OFFLINE_SESSION_KEY)
}
