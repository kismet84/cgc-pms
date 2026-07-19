import { describe, expect, it } from 'vitest'
import {
  AUTH_API,
  CSRF_CONTRACT,
  DASHBOARD_ROLE_CONTRACTS,
  buildDashboardReportPeriods,
  isLoginResult,
  normalizeDashboardMonth,
  resolveDashboardRoles,
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
    expect(resolveDashboardRoles(['USER'], ['dashboard:finance:view'])).toEqual(['finance'])
    expect(resolveDashboardRoles(['ADMIN'], [])).toHaveLength(8)
    expect(resolveDashboardRoles([], ['*'])).toHaveLength(8)
  })

  it('normalizes report periods without emitting malformed month parameters', () => {
    expect(normalizeDashboardMonth('2026-07')).toBe('2026-07')
    expect(normalizeDashboardMonth('2026-13')).toBeUndefined()
    expect(normalizeDashboardMonth('July')).toBeUndefined()
    expect(buildDashboardReportPeriods(new Date(2026, 0, 15), 2)).toEqual([
      { value: '2026-01', label: '2026年1月' },
      { value: '2025-12', label: '2025年12月' },
    ])
  })
})
