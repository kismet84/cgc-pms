import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { UserInfo } from '@cgc-pms/frontend-contracts'
import { registerSessionCacheClearer, useSessionStore } from '@/stores/session'
import { getCurrentUser, login, logout } from '@/services/auth'

vi.mock('@/services/auth', () => ({
  getCurrentUser: vi.fn(),
  login: vi.fn(),
  logout: vi.fn(),
}))

const currentUser: UserInfo = {
  userId: '1',
  username: 'admin',
  realName: '平台管理员',
  roles: ['SUPER_ADMIN'],
  permissions: ['*'],
}

beforeEach(() => {
  setActivePinia(createPinia())
  vi.mocked(getCurrentUser).mockReset()
  vi.mocked(login).mockReset()
  vi.mocked(logout).mockReset()
})

describe('V2 in-memory session store', () => {
  it('stores only user information after login', async () => {
    vi.mocked(login).mockResolvedValue({ userInfo: currentUser })
    const session = useSessionStore()

    await session.login({ username: 'admin', password: 'local-password' })

    expect(session.isAuthenticated).toBe(true)
    expect(session.userInfo).toEqual(currentUser)
    const serializedState = JSON.stringify(session.$state)
    expect(serializedState).not.toContain('local-password')
    expect(serializedState.toLowerCase()).not.toContain('token')
  })

  it('coalesces concurrent user restoration', async () => {
    vi.mocked(getCurrentUser).mockResolvedValue(currentUser)
    const session = useSessionStore()

    const [first, second] = await Promise.all([session.restore(), session.restore()])

    expect(getCurrentUser).toHaveBeenCalledTimes(1)
    expect(first).toEqual(currentUser)
    expect(second).toEqual(currentUser)
  })

  it('clears protected caches even when remote logout fails', async () => {
    vi.mocked(login).mockResolvedValue({ userInfo: currentUser })
    vi.mocked(logout).mockRejectedValue(new Error('network'))
    const clearCache = vi.fn()
    const unregister = registerSessionCacheClearer(clearCache)
    const session = useSessionStore()
    await session.login({ username: 'admin', password: 'local-password' })

    await expect(session.logout()).rejects.toThrow('network')

    expect(session.userInfo).toBeNull()
    expect(session.status).toBe('anonymous')
    expect(clearCache).toHaveBeenCalledTimes(1)
    unregister()
  })
})
