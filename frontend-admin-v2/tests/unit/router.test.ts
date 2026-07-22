import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { UserInfo } from '@cgc-pms/frontend-contracts'
import { installSessionGuard, routes } from '@/router'
import { normalizeRedirect } from '@/services/navigation'
import { getCurrentUser } from '@/services/auth'

vi.mock('@/services/auth', () => ({
  getCurrentUser: vi.fn(),
  login: vi.fn(),
  logout: vi.fn(),
}))

function user(permissions: string[]): UserInfo {
  return {
    userId: '1',
    username: 'tester',
    roles: ['USER'],
    permissions,
  }
}

function guardedRouter() {
  const target = createRouter({ history: createMemoryHistory(), routes })
  installSessionGuard(target)
  return target
}

beforeEach(() => {
  setActivePinia(createPinia())
  vi.mocked(getCurrentUser).mockReset()
})

describe('V2 application-shell routes', () => {
  it('keeps technical routes and exposes permission-bearing shell routes', () => {
    expect(routes.find((route) => route.name === 'V2Health')).toMatchObject({ path: '/health' })
    expect(routes.find((route) => route.name === 'V2Login')).toMatchObject({ path: '/login' })
    const shell = routes.find((route) => route.path === '/shell')
    const dashboard = shell?.children?.find((route) => route.path === '/dashboard')
    const project = shell?.children?.find((route) => route.path === '/project/list')
    const quality = shell?.children?.find((route) => route.path === '/quality-safety')
    const technical = shell?.children?.find((route) => route.path === '/technical-management')
    const closeout = shell?.children?.find((route) => route.path === '/project-closeout')
    const scheduleDetail = shell?.children?.find(
      (route) => route.path === '/project-schedule/:scheduleId',
    )

    expect(dashboard?.meta?.permission).toBe('dashboard:view')
    expect(project?.meta?.permission).toBe('project:query')
    expect(quality?.meta?.permission).toBe('quality:safety:query')
    expect(quality?.meta?.workspaceContext).toEqual({
      project: true,
      period: false,
    })
    expect(String(quality?.component)).not.toContain('ShellPlaceholderPage')
    expect(technical?.meta?.permission).toBe('technical:query')
    expect(technical?.meta?.workspaceContext).toEqual({
      project: true,
      period: false,
    })
    expect(String(technical?.component)).not.toContain('ShellPlaceholderPage')
    expect(closeout?.meta?.permission).toBe('closeout:query')
    expect(closeout?.meta?.workspaceContext).toEqual({
      project: true,
      period: false,
    })
    expect(String(closeout?.component)).not.toContain('ShellPlaceholderPage')
    expect(scheduleDetail?.meta).toMatchObject({
      permission: 'schedule:query',
      workspaceContext: { project: true, period: false },
    })
    expect(String(scheduleDetail?.component)).not.toContain('ShellPlaceholderPage')
    expect(dashboard?.meta?.workspaceContext).toEqual({ project: true, period: true })
    expect(project?.meta?.workspaceContext).toEqual({ project: true, period: false })
    const approval = shell?.children?.find((route) => route.path === '/approval/todo')
    const approvalRoot = shell?.children?.find((route) => route.path === '/approval')
    const approvalDetail = shell?.children?.find(
      (route) => route.path === '/approval/instances/:instanceId',
    )
    const legacyApprovalDetail = shell?.children?.find(
      (route) => route.path === '/approval/:instanceId',
    )
    const alert = shell?.children?.find((route) => route.path === '/alert')
    const reports = shell?.children?.find((route) => route.path === '/dashboard/reports')
    expect(approval?.meta).toMatchObject({
      workflowTab: 'todo',
      workspaceContext: { project: false, period: true },
    })
    expect(approval?.meta?.permission).toBeUndefined()
    expect(approvalRoot?.redirect).toBeTypeOf('function')
    expect(approvalDetail?.meta?.permission).toBeUndefined()
    expect(legacyApprovalDetail?.redirect).toBeTypeOf('function')
    expect(alert?.meta?.permission).toBe('alert:view')
    expect(alert?.redirect).toBeTypeOf('function')
    expect(String(reports?.component)).not.toContain('ShellPlaceholderPage')
    expect(reports?.meta?.workspaceContext).toBeUndefined()
    expect(approvalDetail?.meta?.workspaceContext).toBeUndefined()

    const projectOverview = shell?.children?.find(
      (route) => route.path === '/project/:projectId/overview',
    )
    expect(projectOverview?.meta?.workspaceContext).toBeUndefined()
  })

  it('keeps legacy approval entry and detail deep links compatible', async () => {
    vi.mocked(getCurrentUser).mockResolvedValue(user(['*']))
    const router = guardedRouter()

    await router.push('/approval?projectId=23')
    await router.isReady()
    expect(router.currentRoute.value.fullPath).toBe('/approval/todo?projectId=23')

    await router.push('/approval/81?returnTab=done')
    expect(router.currentRoute.value.fullPath).toBe('/approval/instances/81?returnTab=done')
  })

  it('restores a permitted deep link and blocks a missing permission', async () => {
    vi.mocked(getCurrentUser).mockResolvedValue(user(['project:query']))
    const router = guardedRouter()

    await router.push('/project/list?projectId=23')
    await router.isReady()
    expect(router.currentRoute.value.fullPath).toBe('/project/list?projectId=23')

    await router.push('/contract/ledger')
    expect(router.currentRoute.value.path).toBe('/forbidden')
    expect(router.currentRoute.value.query.from).toBe('/contract/ledger')
  })

  it('restores a permitted project schedule detail deep link', async () => {
    vi.mocked(getCurrentUser).mockResolvedValue(user(['schedule:query']))
    const router = guardedRouter()

    await router.push('/project-schedule/11?projectId=23')
    await router.isReady()

    expect(router.currentRoute.value.fullPath).toBe('/project-schedule/11?projectId=23')
  })

  it('uses wildcard permission for the administrator sample without role-name checks', async () => {
    vi.mocked(getCurrentUser).mockResolvedValue(user(['*']))
    const router = guardedRouter()

    await router.push('/session')
    await router.isReady()

    expect(router.currentRoute.value.path).toBe('/dashboard')
  })

  it('redirects an anonymous deep link to login', async () => {
    vi.mocked(getCurrentUser).mockRejectedValue(new Error('anonymous'))
    const router = guardedRouter()

    await router.push('/project/42/overview')
    await router.isReady()

    expect(router.currentRoute.value.path).toBe('/login')
    expect(router.currentRoute.value.query.redirect).toBe('/project/42/overview')
  })

  it('distinguishes an authenticated unknown route from forbidden access', async () => {
    vi.mocked(getCurrentUser).mockResolvedValue(user(['*']))
    const router = guardedRouter()

    await router.push('/definitely-not-a-v2-route')
    await router.isReady()

    expect(router.currentRoute.value.name).toBe('V2NotFound')
    expect(router.currentRoute.value.path).toBe('/definitely-not-a-v2-route')
  })

  it('accepts only internal non-login redirects', () => {
    expect(normalizeRedirect('/session?from=login')).toBe('/session?from=login')
    expect(normalizeRedirect('https://evil.example')).toBe('/session')
    expect(normalizeRedirect('//evil.example')).toBe('/session')
    expect(normalizeRedirect('/login?redirect=/session')).toBe('/session')
  })
})
