import axios, {
  type AxiosInstance,
  type AxiosResponse,
  type InternalAxiosRequestConfig,
} from 'axios'
import { message } from 'ant-design-vue'
import { useUserStore } from '@/stores/user'
import type { ApiResponse } from '@/types/api'

/** Business success code */
const SUCCESS_CODE = '0'

/** Queue timeout in ms — draining forever-queued requests */
const REFRESH_QUEUE_TIMEOUT = 15_000

type RequestConfigWithPrompt = InternalAxiosRequestConfig & { errorMessage?: string }
const REQUEST_ERROR_NOTIFIED = Symbol('request-error-notified')

type RequestErrorWithNotification = object & { [REQUEST_ERROR_NOTIFIED]?: boolean }

export function markRequestErrorNotified<T>(error: T): T {
  if ((typeof error === 'object' && error !== null) || typeof error === 'function') {
    Object.defineProperty(error, REQUEST_ERROR_NOTIFIED, {
      value: true,
      configurable: false,
      enumerable: false,
      writable: false,
    })
  }
  return error
}

export function isRequestErrorNotified(error: unknown): boolean {
  return (
    ((typeof error === 'object' && error !== null) || typeof error === 'function') &&
    (error as RequestErrorWithNotification)[REQUEST_ERROR_NOTIFIED] === true
  )
}

const runtimeApiBaseUrl = (
  window as unknown as { __APP_RUNTIME_CONFIG__?: { apiBaseUrl?: string } }
).__APP_RUNTIME_CONFIG__?.apiBaseUrl
const devQueryApiBaseUrl = import.meta.env.DEV
  ? new URLSearchParams(window.location.search).get('apiBaseUrl')
  : null

const API_BASE_URL =
  devQueryApiBaseUrl || runtimeApiBaseUrl || import.meta.env.VITE_API_BASE_URL || '/api'
const CSRF_COOKIE_NAME = 'XSRF-TOKEN'
const CSRF_HEADER_NAME = 'X-XSRF-TOKEN'
const CSRF_SAFE_METHODS = new Set(['GET', 'HEAD', 'OPTIONS'])

function getCsrfTokenFromCookie(): string | null {
  const cookie = document.cookie
    .split(';')
    .map((item) => item.trim())
    .find((item) => item.startsWith(`${CSRF_COOKIE_NAME}=`))

  if (!cookie) {
    return null
  }

  const rawValue = cookie.slice(CSRF_COOKIE_NAME.length + 1)
  return rawValue ? decodeURIComponent(rawValue) : null
}

function attachCsrfHeader(config: InternalAxiosRequestConfig): InternalAxiosRequestConfig {
  const method = (config.method ?? 'get').toUpperCase()
  if (CSRF_SAFE_METHODS.has(method)) {
    return config
  }

  const csrfToken = getCsrfTokenFromCookie()
  if (!csrfToken) {
    return config
  }

  config.headers.set(CSRF_HEADER_NAME, csrfToken)
  return config
}

function getConfiguredErrorMessage(config?: InternalAxiosRequestConfig): string | undefined {
  return (config as RequestConfigWithPrompt | undefined)?.errorMessage
}

/** Axios instance for normal API calls — carries the 401 interceptor. */
const service: AxiosInstance = axios.create({
  baseURL: API_BASE_URL,
  timeout: 15000,
  withCredentials: true, // send HttpOnly cookies automatically
})

/**
 * Isolated Axios instance for token-refresh calls ONLY.
 * Has NO 401 interceptor, preventing self-waiting deadlocks.
 */
export const refreshClient: AxiosInstance = axios.create({
  baseURL: API_BASE_URL,
  timeout: 10_000,
  withCredentials: true,
})

// ── Normal request interceptor ──
service.interceptors.request.use(attachCsrfHeader, (error) => Promise.reject(error))

refreshClient.interceptors.request.use(attachCsrfHeader, (error) => Promise.reject(error))

// ── Refresh state ──
let isRefreshing = false
let queueTimer: ReturnType<typeof setTimeout> | null = null
let pendingQueue: Array<{ resolve: (v: void) => void; reject: (e: Error) => void }> = []

function drainQueue(err?: Error) {
  if (queueTimer) {
    clearTimeout(queueTimer)
    queueTimer = null
  }
  if (err) {
    pendingQueue.forEach((entry) => entry.reject(err))
  } else {
    pendingQueue.forEach((entry) => entry.resolve())
  }
  pendingQueue = []
  isRefreshing = false
}

// ── Response interceptor ──
service.interceptors.response.use(
  (response: AxiosResponse<ApiResponse>) => {
    const res = response.data
    if (res == null || typeof res !== 'object' || !('code' in res)) {
      return response.data as unknown as never
    }
    if (res.code === SUCCESS_CODE) {
      return res.data as never
    }
    message.error(
      getConfiguredErrorMessage(response.config) || res.message || '操作失败，请稍后重试',
    )
    return Promise.reject(markRequestErrorNotified(new Error(res.message || '操作失败')))
  },
  async (error) => {
    const status = error?.response?.status
    const originalRequest = error.config as InternalAxiosRequestConfig & { _retry?: boolean }

    if (status === 401 && !originalRequest._retry) {
      if (isRefreshing) {
        // Set _retry on the queued request so it won't re-trigger refresh
        originalRequest._retry = true
        // Queue up while another refresh is in flight
        return new Promise<void>((resolve, reject) => {
          pendingQueue.push({ resolve, reject })
        }).then(() => {
          return service(originalRequest)
        })
      }

      originalRequest._retry = true
      isRefreshing = true

      // Start a hard timeout so the queue always drains
      queueTimer = setTimeout(() => {
        drainQueue(markRequestErrorNotified(new Error('Token refresh timed out')))
        const userStore = useUserStore()
        userStore.logout()
        message.error('登录已过期，请重新登录')
        if (window.location.pathname !== '/login') window.location.href = '/login'
      }, REFRESH_QUEUE_TIMEOUT)

      try {
        // Use the isolated refreshClient — no 401 recursion possible
        const refreshRes = await refreshClient.post('/auth/refresh')
        // Validate refresh response — fail-open on malformed response would leak
        // an unrefreshed token into the retry chain
        if (refreshRes.data?.code !== SUCCESS_CODE) {
          throw new Error(refreshRes.data?.message || 'Token refresh failed')
        }
        drainQueue()
        return service(originalRequest)
      } catch (e: unknown) {
        console.error('[refresh]', e)
        drainQueue(markRequestErrorNotified(new Error('Token refresh failed')))
        const userStore = useUserStore()
        userStore.logout()
        message.error('登录已过期，请重新登录')
        if (window.location.pathname !== '/login') window.location.href = '/login'
        return Promise.reject(markRequestErrorNotified(error))
      }
    }

    if (status !== 401) {
      const msg =
        getConfiguredErrorMessage(originalRequest) ||
        error?.response?.data?.message ||
        error.message ||
        '网络异常，请检查连接'
      message.error(msg)
    }
    return Promise.reject(markRequestErrorNotified(error))
  },
)

export function request<T = unknown>(config: InternalAxiosRequestConfig | object): Promise<T> {
  return service.request<unknown, T>(config as InternalAxiosRequestConfig)
}

export default service
