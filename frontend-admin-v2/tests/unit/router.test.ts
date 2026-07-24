import { createPinia, setActivePinia } from 'pinia'
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
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
  it('keeps Legacy as ledger universe and locks four CostTarget V2 acceptances', () => {
    const ledger = JSON.parse(
      readFileSync(resolve(process.cwd(), '../docs/ui-v2/route-migration-ledger.json'), 'utf8'),
    ) as {
      source: string
      summary: { legacyOnly: number; v2Accepted: number; v2SourceAvailable: number }
      routes: Array<{ name: string; path: string; status: string; v2View: string | null }>
    }
    const costTargetRoutes = ledger.routes.filter((route) => route.path.startsWith('/cost-target'))

    expect(ledger.source).toBe(['frontend-admin', 'src', 'router', 'index.ts'].join('/'))
    expect(ledger.summary).toMatchObject({
      legacyOnly: 50,
      v2Accepted: 37,
      v2SourceAvailable: 0,
    })
    expect(costTargetRoutes).toEqual([
      expect.objectContaining({
        name: 'CostTarget',
        status: 'V2_ACCEPTED',
        v2View: '@/router.ts#V2CostTargetRootRedirect',
      }),
      expect.objectContaining({
        name: 'CostTargetList',
        status: 'V2_ACCEPTED',
        v2View: '@/pages/commercial/CostTargetPage.vue',
      }),
      expect.objectContaining({
        name: 'CostTargetCreate',
        status: 'V2_ACCEPTED',
        v2View: '@/pages/commercial/CostTargetPage.vue',
      }),
      expect.objectContaining({
        name: 'CostTargetEdit',
        status: 'V2_ACCEPTED',
        v2View: '@/pages/commercial/CostTargetPage.vue',
      }),
    ])
  })

  it('keeps technical routes and exposes permission-bearing shell routes', () => {
    expect(routes.find((route) => route.name === 'V2Health')).toMatchObject({ path: '/health' })
    expect(routes.find((route) => route.name === 'V2Login')).toMatchObject({ path: '/login' })
    const shell = routes.find((route) => route.path === '/shell')
    const dashboard = shell?.children?.find((route) => route.path === '/dashboard')
    const project = shell?.children?.find((route) => route.path === '/project/list')
    const contractLedger = shell?.children?.find((route) => route.path === '/contract/ledger')
    const contractCreate = shell?.children?.find((route) => route.path === '/contract/create')
    const contractRoot = shell?.children?.find((route) => route.path === '/contract')
    const contractDetail = shell?.children?.find((route) => route.path === '/contract/:id')
    const contractEdit = shell?.children?.find((route) => route.path === '/contract/:id/edit')
    const costTargetList = shell?.children?.find((route) => route.path === '/cost-target/index')
    const costTargetRoot = shell?.children?.find((route) => route.path === '/cost-target')
    const costTargetCreate = shell?.children?.find((route) => route.path === '/cost-target/create')
    const costTargetEdit = shell?.children?.find((route) => route.path === '/cost-target/:id/edit')
    const costRoot = shell?.children?.find((route) => route.path === '/cost')
    const costLedger = shell?.children?.find((route) => route.path === '/cost/ledger')
    const costSummary = shell?.children?.find((route) => route.path === '/cost/summary')
    const costControl = shell?.children?.find((route) => route.path === '/cost/control')
    const budget = shell?.children?.find((route) => route.path === '/budget')
    const measurement = shell?.children?.find((route) => route.path === '/production-measurement')
    const quality = shell?.children?.find((route) => route.path === '/quality-safety')
    const technical = shell?.children?.find((route) => route.path === '/technical-management')
    const closeout = shell?.children?.find((route) => route.path === '/project-closeout')
    const scheduleDetail = shell?.children?.find(
      (route) => route.path === '/project-schedule/:scheduleId',
    )

    expect(dashboard?.meta?.permission).toBe('dashboard:view')
    expect(project?.meta?.permission).toBe('project:query')
    expect(contractLedger?.meta?.permission).toBe('contract:query')
    expect(String(contractLedger?.component)).not.toContain('ShellPlaceholderPage')
    expect(contractCreate?.meta?.permission).toBe('contract:add')
    expect(String(contractCreate?.component)).not.toContain('ShellPlaceholderPage')
    expect(contractRoot?.redirect).toBeTypeOf('function')
    expect(contractDetail?.meta?.permission).toBe('contract:query')
    expect(String(contractDetail?.component)).not.toContain('ShellPlaceholderPage')
    expect(contractEdit?.meta?.permission).toBe('contract:edit')
    expect(String(contractEdit?.component)).not.toContain('ShellPlaceholderPage')
    expect(costTargetList?.meta?.permission).toBe('cost:target:query')
    expect(String(costTargetList?.component)).not.toContain('ShellPlaceholderPage')
    expect(costTargetRoot?.redirect).toBeTypeOf('function')
    expect(costTargetCreate?.meta?.permission).toBe('cost:target:add')
    expect(String(costTargetCreate?.component)).not.toContain('ShellPlaceholderPage')
    expect(costTargetEdit?.meta?.permission).toBe('cost:target:edit')
    expect(String(costTargetEdit?.component)).not.toContain('ShellPlaceholderPage')
    expect(costRoot?.redirect).toBeTypeOf('function')
    for (const route of [costLedger, costSummary, costControl]) {
      expect(String(route?.component)).not.toContain('ShellPlaceholderPage')
    }
    for (const route of [budget, measurement]) {
      expect(String(route?.component)).not.toContain('ShellPlaceholderPage')
    }
    expect(quality?.meta?.permission).toBe('quality:safety:query')
    expect(String(quality?.component)).not.toContain('ShellPlaceholderPage')
    expect(technical?.meta?.permission).toBe('technical:query')
    expect(String(technical?.component)).not.toContain('ShellPlaceholderPage')
    expect(closeout?.meta?.permission).toBe('closeout:query')
    expect(String(closeout?.component)).not.toContain('ShellPlaceholderPage')
    expect(scheduleDetail?.meta?.permission).toBe('schedule:query')
    expect(String(scheduleDetail?.component)).not.toContain('ShellPlaceholderPage')
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
    expect(approval?.meta?.workflowTab).toBe('todo')
    expect(approval?.meta?.permission).toBeUndefined()
    expect(approvalRoot?.redirect).toBeTypeOf('function')
    expect(approvalDetail?.meta?.permission).toBeUndefined()
    expect(legacyApprovalDetail?.redirect).toBeTypeOf('function')
    expect(alert?.meta?.permission).toBe('alert:view')
    expect(alert?.redirect).toBeTypeOf('function')
    expect(String(reports?.component)).not.toContain('ShellPlaceholderPage')
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

  it('keeps contract root redirect and deep links compatible', async () => {
    vi.mocked(getCurrentUser).mockResolvedValue(
      user(['contract:query', 'contract:add', 'contract:edit']),
    )
    const router = guardedRouter()

    await router.push('/contract?projectId=23#ledger')
    await router.isReady()
    expect(router.currentRoute.value.fullPath).toBe('/contract/ledger?projectId=23#ledger')

    await router.push('/contract/81/edit?projectId=23')
    expect(router.currentRoute.value.fullPath).toBe('/contract/81/edit?projectId=23')
  })

  it('keeps cost target root redirect and edit deep link compatible', async () => {
    vi.mocked(getCurrentUser).mockResolvedValue(
      user(['cost:target:query', 'cost:target:add', 'cost:target:edit']),
    )
    const router = guardedRouter()

    await router.push('/cost-target?projectId=P1#versions')
    await router.isReady()
    expect(router.currentRoute.value.fullPath).toBe('/cost-target/index?projectId=P1#versions')

    await router.push('/cost-target/81/edit?projectId=P1')
    expect(router.currentRoute.value.fullPath).toBe('/cost-target/81/edit?projectId=P1')
  })

  it('keeps cost root query and hash on the ledger redirect', async () => {
    vi.mocked(getCurrentUser).mockResolvedValue(user(['cost:ledger:query']))
    const router = guardedRouter()
    await router.push('/cost?projectId=P1&period=2026-07#items')
    await router.isReady()
    expect(router.currentRoute.value.fullPath).toBe(
      '/cost/ledger?projectId=P1&period=2026-07#items',
    )
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
