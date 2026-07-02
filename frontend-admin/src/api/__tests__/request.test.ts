import { afterEach, describe, expect, it, vi } from 'vitest'

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
  delete (window as Window & { __APP_RUNTIME_CONFIG__?: { apiBaseUrl?: string } }).__APP_RUNTIME_CONFIG__
  if (options.runtimeApiBaseUrl) {
    ;(window as Window & { __APP_RUNTIME_CONFIG__?: { apiBaseUrl?: string } }).__APP_RUNTIME_CONFIG__ = {
      apiBaseUrl: options.runtimeApiBaseUrl,
    }
  }

  return import('../request')
}

afterEach(() => {
  vi.unstubAllEnvs()
  mockLogout.mockReset()
  mockMessageError.mockReset()
  window.history.pushState({}, '', '/')
  delete (window as Window & { __APP_RUNTIME_CONFIG__?: { apiBaseUrl?: string } }).__APP_RUNTIME_CONFIG__
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
