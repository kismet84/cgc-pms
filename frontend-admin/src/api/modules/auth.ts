import { request, refreshClient } from '@/api/request'
import type { LoginParams, LoginResult, UserInfo } from '@/types/user'
import type { ApiResponse } from '@/types/api'

export function login(params: LoginParams) {
  return request<LoginResult>({ url: '/auth/login', method: 'post', data: params })
}

export function getUserInfo() {
  return request<UserInfo>({ url: '/auth/userinfo', method: 'get' })
}

export function logout() {
  return request<void>({ url: '/auth/logout', method: 'post' })
}

/**
 * Refresh tokens via HttpOnly cookie.
 * Uses isolated refreshClient to avoid 401-interceptor recursion.
 */
export async function refreshTokenApi(): Promise<LoginResult> {
  const res = await refreshClient.post<ApiResponse<LoginResult>>('/auth/refresh')
  if (res.data?.code === '0') {
    return res.data.data as LoginResult
  }
  throw new Error(res.data?.message || 'Token refresh failed')
}

export function updateProfile(data: {
  realName?: string
  phone?: string
  email?: string
  avatar?: string
}) {
  return request<UserInfo>({ url: '/profile', method: 'put', data })
}

export function changePassword(data: { oldPassword: string; newPassword: string }) {
  return request<void>({ url: '/profile/password', method: 'put', data })
}
