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

/** Axios instance for normal API calls — carries the 401 interceptor. */
const service: AxiosInstance = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL ?? '/api',
  timeout: 15000,
  withCredentials: true, // send HttpOnly cookies automatically
})

/**
 * Isolated Axios instance for token-refresh calls ONLY.
 * Has NO 401 interceptor, preventing self-waiting deadlocks.
 */
export const refreshClient: AxiosInstance = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL ?? '/api',
  timeout: 10_000,
  withCredentials: true,
})

// ── Normal request interceptor ──
service.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => config,
  (error) => Promise.reject(error),
)

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
    message.error(res.message || 'Request failed')
    return Promise.reject(new Error(res.message || 'Error'))
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
        drainQueue(new Error('Token refresh timed out'))
        const userStore = useUserStore()
        userStore.logout()
        message.error('登录已过期，请重新登录')
        if (window.location.pathname !== '/login') window.location.href = '/login'
      }, REFRESH_QUEUE_TIMEOUT)

      try {
        // Use the isolated refreshClient — no 401 recursion possible
        await refreshClient.post('/auth/refresh')
        drainQueue()
        return service(originalRequest)
      } catch (e: unknown) {
        console.error('[refresh]', e)
        drainQueue(new Error('Token refresh failed'))
        const userStore = useUserStore()
        userStore.logout()
        message.error('登录已过期，请重新登录')
        if (window.location.pathname !== '/login') window.location.href = '/login'
        return Promise.reject(error)
      }
    }

    if (status !== 401) {
      const msg = error?.response?.data?.message || error.message || 'Network error'
      message.error(msg)
    }
    return Promise.reject(error)
  },
)

export function request<T = unknown>(config: InternalAxiosRequestConfig | object): Promise<T> {
  return service.request<unknown, T>(config as InternalAxiosRequestConfig)
}

export default service
