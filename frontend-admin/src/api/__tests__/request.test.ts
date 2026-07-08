import { afterEach, describe, expect, it, vi } from 'vitest'
import type { InternalAxiosRequestConfig } from 'axios'

const { mockLogout, mockMessageError } = vi.hoisted(() => ({
  mockLogout: vi.fn(),
  mockMessageError: vi.fn(),
}))

vi.mock('@/stores/user', () => ({
  useUserStore: () => ({
    logout: mockLogout,
  }),
}))

vi.mock('ant-design-vue', () => ({
  message: {
    error: mockMessageError,
  },
}))

async function loadRequestModule(options: {
  dev: boolean
  query?: string
  runtimeApiBaseUrl?: string
  envApiBaseUrl?: string
}) {
  vi.resetModules()
  vi.stubEnv('DEV', options.dev)
  vi.stubEnv('VITE_API_BASE_URL', options.envApiBaseUrl ?? '/env-api')

  window.history.pushState({}, '', options.query ?? '/')
  delete (window as Window & { __APP_RUNTIME_CONFIG__?: { apiBaseUrl?: string } })
    .__APP_RUNTIME_CONFIG__
  if (options.runtimeApiBaseUrl) {
    ;(
      window as Window & { __APP_RUNTIME_CONFIG__?: { apiBaseUrl?: string } }
    ).__APP_RUNTIME_CONFIG__ = {
      apiBaseUrl: options.runtimeApiBaseUrl,
    }
  }

  return import('../request')
}

afterEach(() => {
  vi.unstubAllEnvs()
  mockLogout.mockReset()
  mockMessageError.mockReset()
  document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/'
  window.history.pushState({}, '', '/')
  delete (window as Window & { __APP_RUNTIME_CONFIG__?: { apiBaseUrl?: string } })
    .__APP_RUNTIME_CONFIG__
})

describe('api request base URL resolution', () => {
  it('keeps query apiBaseUrl override in DEV', async () => {
    const requestModule = await loadRequestModule({
      dev: true,
      query: '/?apiBaseUrl=%2Fquery-api',
      runtimeApiBaseUrl: '/runtime-api',
      envApiBaseUrl: '/env-api',
    })

    expect(requestModule.default.defaults.baseURL).toBe('/query-api')
    expect(requestModule.refreshClient.defaults.baseURL).toBe('/query-api')
  })

  it('ignores query apiBaseUrl outside DEV and uses controlled config', async () => {
    const requestModule = await loadRequestModule({
      dev: false,
      query: '/?apiBaseUrl=%2Fquery-api',
      runtimeApiBaseUrl: '/runtime-api',
      envApiBaseUrl: '/env-api',
    })

    expect(requestModule.default.defaults.baseURL).toBe('/runtime-api')
    expect(requestModule.refreshClient.defaults.baseURL).toBe('/runtime-api')
  })
})

describe('api request csrf header injection', () => {
  it('adds csrf header for POST requests on service and refreshClient', async () => {
    const requestModule = await loadRequestModule({
      dev: false,
    })

    document.cookie = 'XSRF-TOKEN=csrf%20token'
    const capture = vi.fn(
      async (config: InternalAxiosRequestConfig) =>
        ({
          data: { ok: true },
          status: 200,
          statusText: 'OK',
          headers: {},
          config,
        }) as const,
    )

    await requestModule.request({
      url: '/csrf-check',
      method: 'post',
      adapter: capture,
    })
    await requestModule.refreshClient.post('/auth/refresh', undefined, {
      adapter: capture,
    })

    expect(capture).toHaveBeenCalledTimes(2)
    expect(capture.mock.calls[0]?.[0].headers.get('X-XSRF-TOKEN')).toBe('csrf token')
    expect(capture.mock.calls[1]?.[0].headers.get('X-XSRF-TOKEN')).toBe('csrf token')
  })

  it('does not add csrf header for GET requests', async () => {
    const requestModule = await loadRequestModule({
      dev: false,
    })

    document.cookie = 'XSRF-TOKEN=csrf-token'
    const capture = vi.fn(
      async (config: InternalAxiosRequestConfig) =>
        ({
          data: { ok: true },
          status: 200,
          statusText: 'OK',
          headers: {},
          config,
        }) as const,
    )

    await requestModule.request({
      url: '/csrf-check',
      method: 'get',
      adapter: capture,
    })

    expect(capture).toHaveBeenCalledTimes(1)
    expect(capture.mock.calls[0]?.[0].headers.get('X-XSRF-TOKEN')).toBeUndefined()
  })

  it('does not add csrf header when cookie is missing', async () => {
    const requestModule = await loadRequestModule({
      dev: false,
    })

    const capture = vi.fn(
      async (config: InternalAxiosRequestConfig) =>
        ({
          data: { ok: true },
          status: 200,
          statusText: 'OK',
          headers: {},
          config,
        }) as const,
    )

    await requestModule.request({
      url: '/csrf-check',
      method: 'post',
      adapter: capture,
    })

    expect(capture).toHaveBeenCalledTimes(1)
    expect(capture.mock.calls[0]?.[0].headers.get('X-XSRF-TOKEN')).toBeUndefined()
  })
})

describe('api request error prompts', () => {
  it('uses per-request errorMessage when backend rejects the request', async () => {
    const requestModule = await loadRequestModule({
      dev: false,
    })

    await expect(
      requestModule.request({
        url: '/files/f1/url',
        method: 'get',
        errorMessage: '文件下载失败，请确认权限或链接是否已过期',
        adapter: async (config: InternalAxiosRequestConfig) =>
          ({
            data: {
              code: 'FILE_ACCESS_DENIED',
              message: '无权访问该合同文件',
            },
            status: 200,
            statusText: 'OK',
            headers: {},
            config,
          }) as const,
      }),
    ).rejects.toThrow('无权访问该合同文件')

    expect(mockMessageError).toHaveBeenCalledWith('文件下载失败，请确认权限或链接是否已过期')
  })
})
