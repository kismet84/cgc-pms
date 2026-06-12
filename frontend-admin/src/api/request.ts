import axios, {
  type AxiosInstance,
  type AxiosResponse,
  type InternalAxiosRequestConfig,
} from 'axios'
import { message } from 'ant-design-vue'
import { useUserStore } from '@/stores/user'
import type { ApiResponse } from '@/types/api'
import { refreshTokenApi } from '@/api/modules/auth'

/** 业务成功码 */
const SUCCESS_CODE = '0'

const service: AxiosInstance = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL ?? '/api',
  timeout: 15000,
  withCredentials: true, // send HttpOnly cookies automatically
})

// 请求拦截器：无需手动附加 Authorization header — HttpOnly Cookie 自动携带
service.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    return config
  },
  (error) => Promise.reject(error),
)

// 是否正在刷新中，防止并发多次刷新
let isRefreshing = false
// 刷新期间排队的请求
let pendingQueue: Array<{ resolve: (v: void) => void; reject: (e: Error) => void }> = []

function processQueue() {
  pendingQueue.forEach((entry) => entry.resolve())
  pendingQueue = []
}

// 响应拦截器：统一解构 data、处理 401 与业务错误
service.interceptors.response.use(
  (response: AxiosResponse<ApiResponse>) => {
    const res = response.data
    if (res == null || typeof res !== 'object' || !('code' in res)) {
      return response.data as unknown as never
    }
    if (res.code === SUCCESS_CODE) {
      return res.data as never
    }
    message.error(res.message || '请求失败')
    return Promise.reject(new Error(res.message || 'Error'))
  },
  async (error) => {
    const status = error?.response?.status
    const originalRequest = error.config as InternalAxiosRequestConfig & { _retry?: boolean }

    if (status === 401 && !originalRequest._retry) {
      if (isRefreshing) {
        // Queue up while another refresh is in flight
        return new Promise<void>((resolve, reject) => {
          pendingQueue.push({ resolve, reject })
        }).then(() => {
          return service(originalRequest)
        })
      }

      originalRequest._retry = true
      isRefreshing = true

      try {
        // Refresh token is sent via HttpOnly cookie — no manual token needed
        await refreshTokenApi()
        processQueue()
        return service(originalRequest)
      } catch {
        // Refresh failed — reject all queued requests, then logout
        pendingQueue.forEach((entry) => entry.reject(new Error('Token refresh failed')))
        pendingQueue = []
        const userStore = useUserStore()
        userStore.logout()
        message.error('登录已过期，请重新登录')
        if (window.location.pathname !== '/login') window.location.href = '/login'
        return Promise.reject(error)
      } finally {
        isRefreshing = false
      }
    }

    if (status !== 401) {
      const msg = error?.response?.data?.message || error.message || '网络异常'
      message.error(msg)
    }
    return Promise.reject(error)
  },
)

export function request<T = unknown>(config: InternalAxiosRequestConfig | object): Promise<T> {
  return service.request<unknown, T>(config as InternalAxiosRequestConfig)
}

export default service
