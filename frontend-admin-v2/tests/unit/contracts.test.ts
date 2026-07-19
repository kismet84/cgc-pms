import { describe, expect, it } from 'vitest'
import {
  AUTH_API,
  CSRF_CONTRACT,
  DASHBOARD_ROLE_CONTRACTS,
  isLoginResult,
} from '@cgc-pms/frontend-contracts'

describe('shared no-UI contracts', () => {
  it('freezes the four cookie-session endpoints', () => {
    expect(AUTH_API).toEqual({
      login: '/auth/login',
      userInfo: '/auth/userinfo',
      logout: '/auth/logout',
      refresh: '/auth/refresh',
    })
    expect(CSRF_CONTRACT).toEqual({
      cookieName: 'XSRF-TOKEN',
      headerName: 'X-XSRF-TOKEN',
      safeMethods: ['GET', 'HEAD', 'OPTIONS'],
    })
  })

  it('accepts auth payloads without exposing token fields', () => {
    expect(
      isLoginResult({
        userInfo: {
          userId: '1',
          username: 'admin',
          roles: ['SUPER_ADMIN'],
          permissions: ['*'],
        },
      }),
    ).toBe(true)
    expect(isLoginResult({ userInfo: { username: 'admin' } })).toBe(false)
  })

  it('covers all eight dashboard permission scopes', () => {
    expect(Object.keys(DASHBOARD_ROLE_CONTRACTS)).toHaveLength(8)
    expect(DASHBOARD_ROLE_CONTRACTS.cost).toEqual({
      permission: 'dashboard:cost-manager:view',
      endpoint: '/dashboard/cost-manager',
    })
  })
})
