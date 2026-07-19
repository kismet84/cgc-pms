import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import {
  ApiClientError,
  apiRequest,
  configureRequestLifecycle,
  isApiClientError,
  resetRequestRecoveryState,
} from '@/services/request'

const fetchMock = vi.fn<typeof fetch>()

function apiResponse<T>(data: T, status = 200, code = '0', message = 'success'): Response {
  return new Response(JSON.stringify({ code, message, traceId: 'trace-test', data }), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

beforeEach(() => {
  fetchMock.mockReset()
  vi.stubGlobal('fetch', fetchMock)
  configureRequestLifecycle({})
  resetRequestRecoveryState()
  document.cookie = 'XSRF-TOKEN=; Max-Age=0; Path=/'
})

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('V2 same-origin request core', () => {
  it('recognizes serialized client errors across module boundaries', () => {
    expect(
      isApiClientError({
        name: 'ApiClientError',
        message: '用户名或密码错误',
        code: 'AUTH_FAILED',
      }),
    ).toBe(true)
    expect(isApiClientError({ message: 'not enough shape' })).toBe(false)
  })

  it('adds CSRF only to unsafe methods and always uses same-origin credentials', async () => {
    document.cookie = 'XSRF-TOKEN=csrf-value; Path=/'
    fetchMock
      .mockResolvedValueOnce(apiResponse({ ok: true }))
      .mockResolvedValueOnce(apiResponse({ ok: true }))

    await apiRequest('/probe', { method: 'GET', recover401: false })
    await apiRequest('/probe', { method: 'POST', body: { value: 'safe' }, recover401: false })

    const getInit = fetchMock.mock.calls[0]?.[1]
    const postInit = fetchMock.mock.calls[1]?.[1]
    expect(getInit?.credentials).toBe('same-origin')
    expect(new Headers(getInit?.headers).has('X-XSRF-TOKEN')).toBe(false)
    expect(new Headers(postInit?.headers).get('X-XSRF-TOKEN')).toBe('csrf-value')
  })

  it('coalesces concurrent 401 responses into one refresh and retries each request once', async () => {
    let protectedCalls = 0
    let refreshCalls = 0
    fetchMock.mockImplementation(async (input) => {
      const url = String(input)
      if (url.endsWith('/auth/refresh')) {
        refreshCalls += 1
        return apiResponse({
          userInfo: {
            userId: '1',
            username: 'admin',
            roles: ['SUPER_ADMIN'],
            permissions: ['*'],
          },
        })
      }
      protectedCalls += 1
      return protectedCalls <= 2
        ? apiResponse(null, 401, 'AUTH_TOKEN_INVALID', 'unauthorized')
        : apiResponse({ value: protectedCalls })
    })

    const [first, second] = await Promise.all([
      apiRequest<{ value: number }>('/protected'),
      apiRequest<{ value: number }>('/protected'),
    ])

    expect(refreshCalls).toBe(1)
    expect(protectedCalls).toBe(4)
    expect(first.value).toBeGreaterThan(2)
    expect(second.value).toBeGreaterThan(2)
  })

  it('fails closed after one refresh failure and emits one expiration notice', async () => {
    const onSessionExpired = vi.fn()
    configureRequestLifecycle({ onSessionExpired })
    let refreshCalls = 0
    fetchMock.mockImplementation(async (input) => {
      if (String(input).endsWith('/auth/refresh')) {
        refreshCalls += 1
        return apiResponse(null, 200, 'AUTH_TOKEN_INVALID', 'refresh rejected')
      }
      return apiResponse(null, 401, 'AUTH_TOKEN_INVALID', 'unauthorized')
    })

    const results = await Promise.allSettled([apiRequest('/protected'), apiRequest('/protected')])

    expect(results.every((result) => result.status === 'rejected')).toBe(true)
    expect(refreshCalls).toBe(1)
    expect(onSessionExpired).toHaveBeenCalledTimes(1)
  })

  it('rejects URL or headers that try to carry authentication secrets', async () => {
    await expect(apiRequest('/probe?access_token=secret')).rejects.toMatchObject({
      code: 'AUTH_SECRET_TRANSPORT_FORBIDDEN',
    })
    await expect(
      apiRequest('/probe', { headers: { Authorization: 'Bearer secret' } }),
    ).rejects.toBeInstanceOf(ApiClientError)
    expect(fetchMock).not.toHaveBeenCalled()
  })

  it('rejects a refresh response that exposes a token in JSON', async () => {
    fetchMock.mockImplementation(async (input) => {
      if (String(input).endsWith('/auth/refresh')) {
        return apiResponse({
          token: 'exposed-secret',
          userInfo: {
            userId: '1',
            username: 'admin',
            roles: ['SUPER_ADMIN'],
            permissions: ['*'],
          },
        })
      }
      return apiResponse(null, 401, 'AUTH_TOKEN_INVALID', 'unauthorized')
    })

    await expect(apiRequest('/protected')).rejects.toMatchObject({
      code: 'AUTH_SESSION_EXPIRED',
    })
  })

  it('deduplicates identical request notices inside the display window', async () => {
    const onError = vi.fn()
    configureRequestLifecycle({ onError })
    fetchMock.mockImplementation(async () => apiResponse(null, 500, 'SERVER_ERROR', '服务暂不可用'))

    await Promise.allSettled([
      apiRequest('/first', { recover401: false }),
      apiRequest('/second', { recover401: false }),
    ])

    expect(onError).toHaveBeenCalledTimes(1)
    expect(onError).toHaveBeenCalledWith({
      code: 'SERVER_ERROR',
      message: '服务暂不可用',
      traceId: 'trace-test',
    })
  })
})
