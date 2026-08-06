import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { UserInfo } from '@cgc-pms/frontend-contracts'
import {
  getSessionNamespaceIdentity,
  registerSessionCacheClearer,
  useSessionStore,
} from '@/stores/session'
import { getCurrentUser, login, logout } from '@/services/auth'

vi.mock('@/services/auth', () => ({
  getCurrentUser: vi.fn(),
  login: vi.fn(),
  logout: vi.fn(),
}))

const currentUser: UserInfo = {
  tenantId: '1001',
  userId: '1',
  username: 'admin',
  realName: '平台管理员',
  roles: ['SUPER_ADMIN'],
  permissions: ['*'],
}

beforeEach(() => {
  setActivePinia(createPinia())
  sessionStorage.clear()
  vi.mocked(getCurrentUser).mockReset()
  vi.mocked(login).mockReset()
  vi.mocked(logout).mockReset()
})

describe('V2 in-memory session store', () => {
  it('stores only user information after login', async () => {
    vi.mocked(login).mockResolvedValue({ userInfo: currentUser })
    const session = useSessionStore()

    await session.login({ tenantId: 1001, username: 'admin', password: 'local-password' })

    expect(session.isAuthenticated).toBe(true)
    expect(session.userInfo).toEqual(currentUser)
    expect(getSessionNamespaceIdentity()).toEqual({ tenantId: '1001', userId: '1' })
    const serializedState = JSON.stringify(session.$state)
    expect(serializedState).not.toContain('local-password')
    expect(serializedState).toContain('"tenantId":"1001"')
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

  it('restores a credential-free session snapshot while offline', async () => {
    vi.mocked(login).mockResolvedValue({ userInfo: currentUser })
    await useSessionStore().login({ tenantId: 1001, username: 'admin', password: 'local-password' })
    setActivePinia(createPinia())
    Object.defineProperty(navigator, 'onLine', { configurable: true, value: false })

    const restored = await useSessionStore().restore()

    expect(restored).toMatchObject({ tenantId: '1001', userId: '1', username: 'offline' })
    expect(getCurrentUser).not.toHaveBeenCalled()
    expect(sessionStorage.getItem('cgc-pms-offline-session')).not.toContain('local-password')
    Object.defineProperty(navigator, 'onLine', { configurable: true, value: true })
  })

  it('rejects an expired offline session snapshot', async () => {
    vi.mocked(login).mockResolvedValue({ userInfo: currentUser })
    await useSessionStore().login({ tenantId: 1001, username: 'admin', password: 'local-password' })
    const snapshot = JSON.parse(String(sessionStorage.getItem('cgc-pms-offline-session')))
    sessionStorage.setItem(
      'cgc-pms-offline-session',
      JSON.stringify({ ...snapshot, expiresAt: Date.now() - 1 }),
    )
    setActivePinia(createPinia())
    Object.defineProperty(navigator, 'onLine', { configurable: true, value: false })
    vi.mocked(getCurrentUser).mockRejectedValue(new Error('offline'))

    expect(await useSessionStore().restore()).toBeNull()
    expect(sessionStorage.getItem('cgc-pms-offline-session')).toBeNull()
    Object.defineProperty(navigator, 'onLine', { configurable: true, value: true })
  })

  it('offers an explicit administrator bypass without weakening permission-only checks', async () => {
    vi.mocked(login).mockResolvedValue({
      userInfo: { ...currentUser, permissions: [] },
    })
    const session = useSessionStore()

    await session.login({ tenantId: 1001, username: 'admin', password: 'local-password' })

    expect(session.hasPermission('system:user:add')).toBe(false)
    expect(session.hasAdminOrPermission('system:user:add')).toBe(true)
  })

  it('clears protected caches even when remote logout fails', async () => {
    vi.mocked(login).mockResolvedValue({ userInfo: currentUser })
    vi.mocked(logout).mockRejectedValue(new Error('network'))
    const clearCache = vi.fn()
    const unregister = registerSessionCacheClearer(clearCache)
    const session = useSessionStore()
    await session.login({ tenantId: 1001, username: 'admin', password: 'local-password' })

    await expect(session.logout()).rejects.toThrow('network')

    expect(session.userInfo).toBeNull()
    expect(session.status).toBe('anonymous')
    expect(clearCache).toHaveBeenCalledTimes(1)
    unregister()
  })
})
