import { request } from '@/api/request'
import type { LoginParams, LoginResult, UserInfo } from '@/types/user'

export function login(params: LoginParams) {
  return request<LoginResult>({ url: '/auth/login', method: 'post', data: params })
}

export function getUserInfo() {
  return request<UserInfo>({ url: '/auth/userinfo', method: 'get' })
}

export function logout() {
  return request<void>({ url: '/auth/logout', method: 'post' })
}

/** Refresh tokens via HttpOnly cookie — no manual token needed. */
export function refreshTokenApi() {
  return request<LoginResult>({
    url: '/auth/refresh',
    method: 'post',
  })
}
