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
})

// 请求拦截器：附加 token
service.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    const userStore = useUserStore()
    if (userStore.token) {
      config.headers.Authorization = `Bearer ${userStore.token}`
    }
    return config
  },
  (error) => Promise.reject(error),
)

// 是否正在刷新中，防止并发多次刷新
let isRefreshing = false
// 刷新期间排队的请求
let pendingQueue: Array<(token: string) => void> = []

function processQueue(newToken: string) {
  pendingQueue.forEach((resolve) => resolve(newToken))
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
      const userStore = useUserStore()
      const rt = userStore.refreshToken

      if (!rt) {
        // No refresh token — force logout
        userStore.logout()
        message.error('登录已过期，请重新登录')
        if (window.location.pathname !== '/login') window.location.href = '/login'
        return Promise.reject(error)
      }

      if (isRefreshing) {
        // Queue up while another refresh is in flight
        return new Promise<string>((resolve) => {
          pendingQueue.push(resolve)
        }).then((newToken) => {
          originalRequest.headers.Authorization = `Bearer ${newToken}`
          return service(originalRequest)
        })
      }

      originalRequest._retry = true
      isRefreshing = true

      try {
        const result = await refreshTokenApi(rt)
        userStore.setToken(result.token)
        userStore.setRefreshToken(result.refreshToken)
        processQueue(result.token)
        originalRequest.headers.Authorization = `Bearer ${result.token}`
        return service(originalRequest)
      } catch {
        // Refresh failed — logout
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
