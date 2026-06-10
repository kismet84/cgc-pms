import axios, {
  type AxiosInstance,
  type AxiosResponse,
  type InternalAxiosRequestConfig,
} from 'axios'
import { message } from 'ant-design-vue'
import { useUserStore } from '@/stores/user'
import type { ApiResponse } from '@/types/api'

/** 业务成功码 */
const SUCCESS_CODE = '0'

const service: AxiosInstance = axios.create({
  baseURL: '/api',
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

// 响应拦截器：统一解构 data、处理 401 与业务错误
service.interceptors.response.use(
  (response: AxiosResponse<ApiResponse>) => {
    const res = response.data
    // 非标准响应（如文件流）直接返回
    if (res == null || typeof res !== 'object' || !('code' in res)) {
      return response.data as unknown as never
    }
    if (res.code === SUCCESS_CODE) {
      return res.data as never
    }
    message.error(res.message || '请求失败')
    return Promise.reject(new Error(res.message || 'Error'))
  },
  (error) => {
    const status = error?.response?.status
    if (status === 401) {
      const userStore = useUserStore()
      userStore.logout()
      message.error('登录已过期，请重新登录')
      if (window.location.pathname !== '/login') {
        window.location.href = '/login'
      }
    } else {
      const msg = error?.response?.data?.message || error.message || '网络异常'
      message.error(msg)
    }
    return Promise.reject(error)
  },
)

/** 带返回类型的请求封装，自动解包 ApiResponse.data */
export function request<T = unknown>(config: InternalAxiosRequestConfig | object): Promise<T> {
  return service.request<unknown, T>(config as InternalAxiosRequestConfig)
}

export default service
