import { setActivePinia, createPinia } from 'pinia'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import {
  apiRequest,
  configureRequestLifecycle,
  isApiClientError,
  resetRequestRecoveryState,
} from '@/services/request'
import { installSessionGuard } from '@/router'
import { useSessionStore } from '@/stores/session'
import { getCurrentUser, login } from '@/services/auth'
import { centsToDecimalString, parseCents, sumCents } from '@/shared/decimal'
import type { UserInfo } from '@cgc-pms/frontend-contracts'

vi.mock('@/services/auth', () => ({
  getCurrentUser: vi.fn(),
  login: vi.fn(),
  logout: vi.fn(),
}))

const currentUser: UserInfo = {
  tenantId: '1001',
  userId: '1',
  username: 'admin',
  roles: ['SUPER_ADMIN'],
  permissions: ['*'],
}

const fetchMock = vi.fn<typeof fetch>()

function apiResponse<T>(data: T, status = 200, code = '0', message = 'success'): Response {
  return new Response(JSON.stringify({ code, message, traceId: 'trace-test', data }), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

beforeEach(() => {
  setActivePinia(createPinia())
  sessionStorage.clear()
  vi.mocked(getCurrentUser).mockReset()
  vi.mocked(login).mockReset()
  fetchMock.mockReset()
  vi.stubGlobal('fetch', fetchMock)
  configureRequestLifecycle({})
  resetRequestRecoveryState()
})

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('session guard concurrency', () => {
  it('awaits an in-flight restore instead of bouncing the second navigation to /login', async () => {
    let releaseRestore: (user: UserInfo) => void = () => {}
    let restoreStarted: () => void = () => {}
    const started = new Promise<void>((resolve) => {
      restoreStarted = resolve
    })
    vi.mocked(getCurrentUser).mockImplementation(() => {
      restoreStarted()
      return new Promise<UserInfo>((resolve) => {
        releaseRestore = resolve
      })
    })

    // Drive the guard directly: two concurrent navigations through the real router race on
    // which one wins the address bar, which would hide the redirect this test is about.
    let guard: (to: unknown) => Promise<unknown> = async () => true
    installSessionGuard({
      beforeEach(callback: (to: unknown) => Promise<unknown>) {
        guard = callback
      },
    } as never)
    const session = useSessionStore()
    const target = { path: '/dashboard/index', fullPath: '/dashboard/index', meta: {}, query: {} }

    const first = guard(target)
    await started
    expect(session.status).toBe('restoring')

    // Second navigation arrives mid-restore: it must wait for the same task, not decide now.
    const second = guard(target)
    releaseRestore(currentUser)

    expect(await first).toBe(true)
    expect(await second).toBe(true)
    expect(vi.mocked(getCurrentUser)).toHaveBeenCalledOnce()
    expect(session.isAuthenticated).toBe(true)
  })
})

describe('offline session snapshot', () => {
  it('never carries roles or permissions through sessionStorage', async () => {
    vi.mocked(login).mockResolvedValue({ userInfo: currentUser })
    await useSessionStore().login({ tenantId: 1001, username: 'admin', password: 'pw' })

    const raw = String(sessionStorage.getItem('cgc-pms-offline-session'))
    expect(raw).not.toContain('SUPER_ADMIN')
    expect(raw).not.toContain('"*"')

    // A tampered snapshot must not grant anything.
    const snapshot = JSON.parse(raw)
    snapshot.userInfo.roles = ['SUPER_ADMIN']
    snapshot.userInfo.permissions = ['*']
    sessionStorage.setItem('cgc-pms-offline-session', JSON.stringify(snapshot))
    setActivePinia(createPinia())
    Object.defineProperty(navigator, 'onLine', { configurable: true, value: false })

    const session = useSessionStore()
    await session.restore()

    expect(session.roles).toEqual([])
    expect(session.permissions).toEqual([])
    expect(session.hasPermission('cost:target:list')).toBe(false)
    Object.defineProperty(navigator, 'onLine', { configurable: true, value: true })
  })
})

describe('401 after a successful refresh', () => {
  it('expires the session instead of surfacing a plain error forever', async () => {
    const onSessionExpired = vi.fn()
    configureRequestLifecycle({ onSessionExpired })
    let refreshCalls = 0
    fetchMock.mockImplementation(async (input) => {
      if (String(input).endsWith('/auth/refresh')) {
        refreshCalls += 1
        return apiResponse({ userInfo: currentUser })
      }
      return apiResponse(null, 401, 'AUTH_TOKEN_INVALID', 'still unauthorized')
    })

    const error = await apiRequest('/protected', { notifyError: false }).catch(
      (value: unknown) => value,
    )

    expect(refreshCalls).toBe(1)
    expect(isApiClientError(error)).toBe(true)
    expect((error as Error).name).toBe('SessionExpiredError')
    expect(onSessionExpired).toHaveBeenCalledOnce()
  })
})

describe('integer-cent money arithmetic', () => {
  it('keeps cents that Number would lose past MAX_SAFE_INTEGER', () => {
    // 9007199254740993.12 -> Number rounds the integer part down to ...992
    expect(parseCents('9007199254740993.12')).toBe(900719925474099312n)
    expect(centsToDecimalString(parseCents('9007199254740993.12'))).toBe('9007199254740993.12')
  })

  it('rounds half-up at two decimals like the backend', () => {
    expect(centsToDecimalString(parseCents('1.005'))).toBe('1.01')
    expect(centsToDecimalString(parseCents('1.004'))).toBe('1.00')
    expect(centsToDecimalString(parseCents('-1.005'))).toBe('-1.01')
  })

  it('sums and subtracts without float drift', () => {
    expect(centsToDecimalString(sumCents(['0.1', '0.2']))).toBe('0.30')
    const difference = parseCents('9007199254740993.12') - sumCents(['9007199254740993.11'])
    expect(centsToDecimalString(difference)).toBe('0.01')
  })

  it('treats blank and malformed input as zero', () => {
    expect(parseCents(null)).toBe(0n)
    expect(parseCents('')).toBe(0n)
    expect(parseCents('abc')).toBe(0n)
  })
})
