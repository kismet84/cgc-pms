import { request } from '@/api/request'
import type { LoginParams, LoginResult, UserInfo } from '@/types/user'

/** 登录 */
export function login(params: LoginParams) {
  return request<LoginResult>({
    url: '/auth/login',
    method: 'post',
    data: params,
  })
}

/** 获取当前用户信息 */
export function getUserInfo() {
  return request<UserInfo>({
    url: '/auth/userinfo',
    method: 'get',
  })
}

/** 退出登录 */
export function logout() {
  return request<void>({
    url: '/auth/logout',
    method: 'post',
  })
}
