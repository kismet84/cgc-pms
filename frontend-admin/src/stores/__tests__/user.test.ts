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

function seedLegacySessionStorage() {
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

function seedLoggedInStore(store: ReturnType<typeof useUserStore>) {
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
}

describe('userStore — logout', () => {
  beforeEach(() => {
    vi.resetModules()
    mockAuthLogout.mockReset()
    setActivePinia(createPinia())
    localStorage.clear()
    sessionStorage.clear()
  })

  it('should call authLogout once on logout', async () => {
    const store = useUserStore()
    seedLoggedInStore(store)
    mockAuthLogout.mockResolvedValue(undefined)

    await store.logout()

    expect(mockAuthLogout).toHaveBeenCalledOnce()
  })

  it('should clear legacy sessionStorage cgc_pms_userinfo on logout', async () => {
    seedLegacySessionStorage()
    expect(sessionStorage.getItem(STORAGE_KEY)).not.toBeNull()

    const store = useUserStore()
    seedLoggedInStore(store)
    mockAuthLogout.mockResolvedValue(undefined)

    await store.logout()

    expect(sessionStorage.getItem(STORAGE_KEY)).toBeNull()
  })

  it('should set userInfo to null on logout', async () => {
    const store = useUserStore()
    seedLoggedInStore(store)
    expect(store.userInfo).not.toBeNull()
    mockAuthLogout.mockResolvedValue(undefined)

    await store.logout()

    expect(store.userInfo).toBeNull()
  })

  it('should set isLogin to false after logout', async () => {
    const store = useUserStore()
    seedLoggedInStore(store)
    expect(store.isLogin).toBe(true)
    mockAuthLogout.mockResolvedValue(undefined)

    await store.logout()

    expect(store.isLogin).toBe(false)
  })

  it('should clear sessionStorage even when authLogout API rejects', async () => {
    seedLegacySessionStorage()
    expect(sessionStorage.getItem(STORAGE_KEY)).not.toBeNull()

    const store = useUserStore()
    seedLoggedInStore(store)
    mockAuthLogout.mockRejectedValue(new Error('Network error'))

    await store.logout()

    expect(mockAuthLogout).toHaveBeenCalledOnce()
    expect(sessionStorage.getItem(STORAGE_KEY)).toBeNull()
    expect(store.userInfo).toBeNull()
  })

  it('should not persist auth data into sessionStorage', () => {
    const store = useUserStore()

    seedLoggedInStore(store)

    expect(sessionStorage.getItem(STORAGE_KEY)).toBeNull()
  })

  it('should discard legacy auth cache on store init', () => {
    seedLegacySessionStorage()

    const store = useUserStore()

    expect(store.isLogin).toBe(false)
    expect(store.userInfo).toBeNull()
    expect(sessionStorage.getItem(STORAGE_KEY)).toBeNull()
  })

  it('should keep permission checks working for in-memory user info', () => {
    const store = useUserStore()
    seedLoggedInStore(store)

    expect(store.isLogin).toBe(true)
    expect(store.roles).toEqual(['ADMIN'])
    expect(store.permissions).toEqual(['dashboard:view'])
    expect(store.hasPermission('dashboard:view')).toBe(true)
  })

  it('should NOT await the API call — clears state synchronously before promise settles', () => {
    seedLegacySessionStorage()
    const store = useUserStore()
    seedLoggedInStore(store)

    let resolveLogout!: () => void
    const deferred = new Promise<void>((resolve) => {
      resolveLogout = resolve
    })
    mockAuthLogout.mockReturnValue(deferred)

    store.logout()

    expect(sessionStorage.getItem(STORAGE_KEY)).toBeNull()
    expect(store.userInfo).toBeNull()

    resolveLogout()
  })
})
