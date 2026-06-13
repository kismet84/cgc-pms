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

function seedLocalStorage() {
  localStorage.setItem(
    STORAGE_KEY,
    JSON.stringify({
      userId: '1',
      username: 'admin',
      realName: 'Admin',
      roles: ['ADMIN'],
      permissions: ['*'],
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

    // Reset localStorage
    localStorage.clear()
  })

  it('should call authLogout once on logout', async () => {
    const store = useUserStore()
    mockAuthLogout.mockResolvedValue(undefined)

    await store.logout()

    expect(mockAuthLogout).toHaveBeenCalledOnce()
  })

  it('should clear localStorage cgc_pms_userinfo on logout', async () => {
    seedLocalStorage()
    expect(localStorage.getItem(STORAGE_KEY)).not.toBeNull()

    const store = useUserStore()
    mockAuthLogout.mockResolvedValue(undefined)

    await store.logout()

    expect(localStorage.getItem(STORAGE_KEY)).toBeNull()
  })

  it('should set userInfo to null on logout', async () => {
    seedLocalStorage()
    const store = useUserStore()
    expect(store.userInfo).not.toBeNull()
    mockAuthLogout.mockResolvedValue(undefined)

    await store.logout()

    expect(store.userInfo).toBeNull()
  })

  it('should set isLogin to false after logout', async () => {
    seedLocalStorage()
    const store = useUserStore()
    expect(store.isLogin).toBe(true)
    mockAuthLogout.mockResolvedValue(undefined)

    await store.logout()

    expect(store.isLogin).toBe(false)
  })

  it('should clear localStorage even when authLogout API rejects', async () => {
    seedLocalStorage()
    expect(localStorage.getItem(STORAGE_KEY)).not.toBeNull()

    const store = useUserStore()
    mockAuthLogout.mockRejectedValue(new Error('Network error'))

    // fire-and-forget — errors from the API are silently swallowed
    await store.logout()

    expect(mockAuthLogout).toHaveBeenCalledOnce()
    expect(localStorage.getItem(STORAGE_KEY)).toBeNull()
    expect(store.userInfo).toBeNull()
  })

  it('should NOT await the API call — clears state synchronously before promise settles', () => {
    seedLocalStorage()
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
    expect(localStorage.getItem(STORAGE_KEY)).toBeNull()
    expect(store.userInfo).toBeNull()

    // Clean up
    resolveLogout()
  })
})
