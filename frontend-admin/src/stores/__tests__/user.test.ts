import { describe, it, expect, vi, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'

const { mockAuthLogout } = vi.hoisted(() => ({
  mockAuthLogout: vi.fn(),
}))

vi.mock('@/api/modules/auth', () => ({
  logout: mockAuthLogout,
}))

import { useUserStore } from '../user'

const STORAGE_KEY = 'cgc_pms_userinfo'

function seedSessionStorage() {
  sessionStorage.setItem(
    STORAGE_KEY,
    JSON.stringify({
      userId: '1',
      username: 'admin',
      roles: ['ADMIN'],
      permissions: ['*'],
      roleName: '系统管理员',
    }),
  )
}

describe('userStore — logout', () => {
  beforeEach(() => {
    // Reset module mock
    vi.resetModules()
    mockAuthLogout.mockReset()

    // Reset pinia
    setActivePinia(createPinia())

    // Reset browser storage
    localStorage.clear()
    sessionStorage.clear()
  })

  it('should call authLogout once on logout', async () => {
    const store = useUserStore()
    mockAuthLogout.mockResolvedValue(undefined)

    await store.logout()

    expect(mockAuthLogout).toHaveBeenCalledOnce()
  })

  it('should clear sessionStorage cgc_pms_userinfo on logout', async () => {
    seedSessionStorage()
    expect(sessionStorage.getItem(STORAGE_KEY)).not.toBeNull()

    const store = useUserStore()
    mockAuthLogout.mockResolvedValue(undefined)

    await store.logout()

    expect(sessionStorage.getItem(STORAGE_KEY)).toBeNull()
  })

  it('should set userInfo to null on logout', async () => {
    seedSessionStorage()
    const store = useUserStore()
    expect(store.userInfo).not.toBeNull()
    mockAuthLogout.mockResolvedValue(undefined)

    await store.logout()

    expect(store.userInfo).toBeNull()
  })

  it('should set isLogin to false after logout', async () => {
    seedSessionStorage()
    const store = useUserStore()
    expect(store.isLogin).toBe(true)
    mockAuthLogout.mockResolvedValue(undefined)

    await store.logout()

    expect(store.isLogin).toBe(false)
  })

  it('should clear sessionStorage even when authLogout API rejects', async () => {
    seedSessionStorage()
    expect(sessionStorage.getItem(STORAGE_KEY)).not.toBeNull()

    const store = useUserStore()
    mockAuthLogout.mockRejectedValue(new Error('Network error'))

    // fire-and-forget — errors from the API are silently swallowed
    await store.logout()

    expect(mockAuthLogout).toHaveBeenCalledOnce()
    expect(sessionStorage.getItem(STORAGE_KEY)).toBeNull()
    expect(store.userInfo).toBeNull()
  })

  it('should persist auth-only fields without PII', () => {
    const store = useUserStore()

    store.setUserInfo({
      userId: '1',
      username: 'admin',
      realName: '管理员',
      phone: '13800138000',
      email: 'admin@example.com',
      avatar: 'https://example.com/avatar.png',
      roles: ['ADMIN'],
      permissions: ['dashboard:view'],
      roleName: '系统管理员',
    })

    const saved = JSON.parse(sessionStorage.getItem(STORAGE_KEY) || '{}') as Record<string, unknown>
    expect(saved).toEqual({
      userId: '1',
      username: 'admin',
      roles: ['ADMIN'],
      permissions: ['dashboard:view'],
      roleName: '系统管理员',
    })
    expect(saved.realName).toBeUndefined()
    expect(saved.phone).toBeUndefined()
    expect(saved.email).toBeUndefined()
    expect(saved.avatar).toBeUndefined()
  })

  it('should keep permission checks working after reload from auth-only cache', () => {
    sessionStorage.setItem(
      STORAGE_KEY,
      JSON.stringify({
        userId: '1',
        username: 'admin',
        roles: ['ADMIN'],
        permissions: ['system:user:view'],
        roleName: '系统管理员',
      }),
    )

    const store = useUserStore()

    expect(store.isLogin).toBe(true)
    expect(store.roles).toEqual(['ADMIN'])
    expect(store.permissions).toEqual(['system:user:view'])
    expect(store.hasPermission('system:user:view')).toBe(true)
  })

  it('should NOT await the API call — clears state synchronously before promise settles', () => {
    seedSessionStorage()
    const store = useUserStore()

    // Use a deferred promise so the API never settles during this tick
    let resolveLogout!: () => void
    const deferred = new Promise<void>((resolve) => {
      resolveLogout = resolve
    })
    mockAuthLogout.mockReturnValue(deferred)

    // Call logout — store must clear state synchronously (fire-and-forget semantics)
    store.logout()
    // The API call was fired but not awaited; the local state is already cleared
    expect(sessionStorage.getItem(STORAGE_KEY)).toBeNull()
    expect(store.userInfo).toBeNull()

    // Clean up
    resolveLogout()
  })
})
