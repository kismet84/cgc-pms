import {
  AUTH_API,
  isLoginResult,
  isUserInfo,
  type LoginParams,
  type LoginResult,
  type UserInfo,
} from '@cgc-pms/frontend-contracts'
import { ApiClientError, apiRequest, resetRequestRecoveryState } from './request'

export async function login(params: LoginParams): Promise<LoginResult> {
  const result = await apiRequest<unknown, LoginParams>(AUTH_API.login, {
    method: 'POST',
    body: params,
    notifyError: false,
    recover401: false,
  })
  if (!isLoginResult(result) || hasExposedToken(result)) throw malformedAuthResponse()
  resetRequestRecoveryState()
  return { userInfo: result.userInfo }
}

export async function getCurrentUser(): Promise<UserInfo> {
  const result = await apiRequest<UserInfo>(AUTH_API.userInfo)
  if (!isUserInfo(result)) throw malformedAuthResponse()
  resetRequestRecoveryState()
  return result
}

export async function logout(): Promise<void> {
  await apiRequest<void>(AUTH_API.logout, {
    method: 'POST',
    notifyError: false,
    recover401: false,
  })
}

function malformedAuthResponse(): ApiClientError {
  return new ApiClientError({
    code: 'AUTH_RESPONSE_INVALID',
    message: '认证响应格式无效',
  })
}

function hasExposedToken(value: unknown): boolean {
  if (!value || typeof value !== 'object') return false
  const candidate = value as { token?: unknown; refreshToken?: unknown }
  return (
    (typeof candidate.token === 'string' && candidate.token.length > 0) ||
    (typeof candidate.refreshToken === 'string' && candidate.refreshToken.length > 0)
  )
}
