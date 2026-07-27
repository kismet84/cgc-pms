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

function user(permissions: string[], roles: string[] = ['USER']): UserInfo {
  return {
    userId: '1',
    username: 'tester',
    roles,
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
  it('keeps Legacy as ledger universe and locks current V2 acceptance counts', () => {
    const ledger = JSON.parse(
      readFileSync(resolve(process.cwd(), '../docs/ui-v2/route-migration-ledger.json'), 'utf8'),
    ) as {
      source: string
      summary: { legacyOnly: number; v2Accepted: number; v2SourceAvailable: number }
      routes: Array<{
        name: string
        path: string
        status: string
        v2View: string | null
        permission: string | null
      }>
    }
    const costTargetRoutes = ledger.routes.filter((route) => route.path.startsWith('/cost-target'))
    const costSubjectRoutes = ledger.routes.filter((route) =>
      route.path.startsWith('/cost/subject'),
    )

    expect(ledger.source).toBe(['frontend-admin', 'src', 'router', 'index.ts'].join('/'))
    expect(ledger.summary).toMatchObject({
      legacyOnly: 0,
      v2Accepted: 87,
      v2SourceAvailable: 0,
    })
    expect(
      ledger.routes.filter((route) => ['Login', 'Forbidden', 'NotFound'].includes(route.name)),
    ).toEqual([
      expect.objectContaining({
        name: 'Login',
        status: 'V2_ACCEPTED',
        v2View: '@/pages/auth/LoginPage.vue',
      }),
      expect.objectContaining({
        name: 'Forbidden',
        status: 'V2_ACCEPTED',
        v2View: '@/router.ts#V2LegacyForbiddenRedirect',
      }),
      expect.objectContaining({
        name: 'NotFound',
        status: 'V2_ACCEPTED',
        v2View: '@/pages/errors/NotFoundPage.vue',
      }),
    ])
    expect(
      ledger.routes.filter((route) => ['Profile', 'Settings', 'Help'].includes(route.name)),
    ).toEqual(
      ['Profile', 'Settings', 'Help'].map((name) =>
        expect.objectContaining({
          name,
          status: 'V2_ACCEPTED',
          v2View: '@/pages/account/AccountPage.vue',
          permission: null,
        }),
      ),
    )
    expect(
      ledger.routes.filter((route) =>
        ['Partner', 'Org', 'Material', 'MaterialDictionary'].includes(route.name),
      ),
    ).toEqual([
      expect.objectContaining({
        name: 'Partner',
        status: 'V2_ACCEPTED',
        v2View: '@/pages/master-data/PartnerPage.vue',
        permission: 'partner:query',
      }),
      expect.objectContaining({
        name: 'Org',
        status: 'V2_ACCEPTED',
        v2View: '@/pages/master-data/OrganizationPage.vue',
        permission: 'org:list',
      }),
      expect.objectContaining({
        name: 'Material',
        status: 'V2_ACCEPTED',
        v2View: '@/router.ts#V2MaterialRedirect',
        permission: 'material:dict:list',
      }),
      expect.objectContaining({
        name: 'MaterialDictionary',
        status: 'V2_ACCEPTED',
        v2View: '@/pages/master-data/MaterialDictionaryPage.vue',
        permission: 'material:dict:list',
      }),
    ])
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
    expect(costSubjectRoutes).toEqual([
      expect.objectContaining({
        name: 'CostSubject',
        status: 'V2_ACCEPTED',
        v2View: '@/router.ts#V2CostSubjectRootRedirect',
        permission: 'cost:query',
      }),
      ...['Taxonomy', 'Rules', 'Scope', 'Trace'].map((suffix) =>
        expect.objectContaining({
          name: `CostSubject${suffix}`,
          status: 'V2_ACCEPTED',
          v2View: '@/pages/master-data/CostSubjectPage.vue',
        }),
      ),
    ])
    expect(
      ledger.routes.filter((route) =>
        [
          'System',
          'SystemDict',
          'SystemUsers',
          'SystemData',
          'RoleManagement',
          'SystemPermissions',
          'SystemAudit',
          'DocumentTemplateManagement',
        ].includes(route.name),
      ),
    ).toEqual([
      expect.objectContaining({
        name: 'System',
        status: 'V2_ACCEPTED',
        v2View: '@/router.ts#V2SystemRedirect',
        permission: 'system:dict:list',
      }),
      expect.objectContaining({
        name: 'SystemDict',
        status: 'V2_ACCEPTED',
        v2View: '@/pages/system/DictionaryPage.vue',
        permission: 'system:dict:list',
      }),
      expect.objectContaining({
        name: 'SystemUsers',
        status: 'V2_ACCEPTED',
        v2View: '@/pages/system/AccessControlPage.vue',
      }),
      expect.objectContaining({
        name: 'SystemData',
        status: 'V2_ACCEPTED',
        v2View: '@/pages/system/DataMaintenancePage.vue',
        permission: null,
      }),
      expect.objectContaining({
        name: 'RoleManagement',
        status: 'V2_ACCEPTED',
        v2View: '@/pages/system/AccessControlPage.vue',
      }),
      expect.objectContaining({
        name: 'SystemPermissions',
        status: 'V2_ACCEPTED',
        v2View: '@/pages/system/AccessControlPage.vue',
        permission: 'system:menu:query',
      }),
      expect.objectContaining({
        name: 'SystemAudit',
        status: 'V2_ACCEPTED',
        v2View: '@/pages/system/AuditPage.vue',
        permission: 'audit:query',
      }),
      expect.objectContaining({
        name: 'DocumentTemplateManagement',
        status: 'V2_ACCEPTED',
        v2View: '@/pages/system/DocumentTemplatePage.vue',
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
    const supplierSourcing = shell?.children?.find((route) => route.path === '/supplier-sourcing')
    const purchaseRoot = shell?.children?.find((route) => route.path === '/purchase')
    const purchaseRequest = shell?.children?.find(
      (route) => route.path === '/inventory/purchase-request',
    )
    const purchaseOrder = shell?.children?.find((route) => route.path === '/purchase/order')
    const purchaseReceipt = shell?.children?.find((route) => route.path === '/purchase/receipt')
    const inventoryRoot = shell?.children?.find((route) => route.path === '/inventory')
    const warehouse = shell?.children?.find((route) => route.path === '/inventory/warehouse')
    const stock = shell?.children?.find((route) => route.path === '/inventory/stock')
    const transaction = shell?.children?.find((route) => route.path === '/inventory/transaction')
    const requisition = shell?.children?.find(
      (route) => route.path === '/inventory/material-requisition',
    )
    const scheduleDetail = shell?.children?.find(
      (route) => route.path === '/project-schedule/:scheduleId',
    )
    const accountRoutes = ['/profile', '/settings', '/help'].map((path) =>
      shell?.children?.find((route) => route.path === path),
    )
    const partner = shell?.children?.find((route) => route.path === '/partner')
    const org = shell?.children?.find((route) => route.path === '/org')
    const materialRoot = shell?.children?.find((route) => route.path === '/material')
    const materialDictionary = shell?.children?.find(
      (route) => route.path === '/material/dictionary',
    )
    const costSubjectRoot = shell?.children?.find((route) => route.path === '/cost/subject')
    const costSubjectRoutes = [
      '/cost/subject/taxonomy',
      '/cost/subject/rules',
      '/cost/subject/scope',
      '/cost/subject/trace',
    ].map((path) => shell?.children?.find((route) => route.path === path))
    const systemRoutes = [
      '/system/users',
      '/system/roles',
      '/system/permissions',
      '/system/dict',
      '/system/audit',
      '/system/document-templates',
      '/system/data',
    ].map((path) => shell?.children?.find((route) => route.path === path))
    const systemRoot = shell?.children?.find((route) => route.path === '/system')

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
    expect(supplierSourcing?.meta?.permission).toBe('supplier:sourcing:query')
    expect(String(supplierSourcing?.component)).not.toContain('ShellPlaceholderPage')
    expect(purchaseRoot?.redirect).toBeTypeOf('function')
    expect(purchaseRoot?.meta?.permission).toBe('purchase:order:query')
    expect(purchaseRequest?.meta?.permission).toBe('purchase:request:list')
    expect(purchaseOrder?.meta?.permission).toBe('purchase:order:query')
    expect(purchaseReceipt?.meta?.permission).toBe('receipt:query')
    for (const route of [purchaseRequest, purchaseOrder, purchaseReceipt]) {
      expect(String(route?.component)).not.toContain('ShellPlaceholderPage')
    }
    expect(inventoryRoot?.redirect).toBeTypeOf('function')
    expect(inventoryRoot?.meta?.permission).toBe('inventory:warehouse:list')
    expect(warehouse?.meta?.permission).toBe('inventory:warehouse:list')
    expect(stock?.meta?.permission).toBe('inventory:stock:list')
    expect(transaction?.meta?.permission).toBe('inventory:transaction:list')
    expect(requisition?.meta?.permission).toBe('requisition:query')
    for (const route of [warehouse, stock, transaction, requisition]) {
      expect(String(route?.component)).not.toContain('ShellPlaceholderPage')
    }
    expect(scheduleDetail?.meta?.permission).toBe('schedule:query')
    expect(String(scheduleDetail?.component)).not.toContain('ShellPlaceholderPage')
    for (const route of accountRoutes) {
      expect(route?.meta?.permission).toBeUndefined()
      expect(String(route?.component)).not.toContain('ShellPlaceholderPage')
    }
    expect(partner?.meta?.permission).toBe('partner:query')
    expect(org?.meta?.permission).toBe('org:list')
    expect(materialRoot?.meta?.permission).toBe('material:dict:list')
    expect(materialRoot?.redirect).toBeTypeOf('function')
    expect(materialDictionary?.meta?.permission).toBe('material:dict:list')
    for (const route of [partner, org, materialDictionary]) {
      expect(String(route?.component)).not.toContain('ShellPlaceholderPage')
    }
    expect(costSubjectRoot?.meta?.permission).toBe('cost:query')
    expect(costSubjectRoot?.redirect).toBeTypeOf('function')
    for (const route of costSubjectRoutes) {
      expect(String(route?.component)).not.toContain('ShellPlaceholderPage')
    }
    expect(costSubjectRoutes.map((route) => route?.meta?.permission)).toEqual([
      'cost:query',
      'cost:subject:rule:query',
      'cost:subject:scope:query',
      'cost:subject:audit:query',
    ])
    expect(systemRoot?.redirect).toBeTypeOf('function')
    expect(systemRoot?.meta).toMatchObject({ permission: 'system:dict:list', adminOnly: true })
    expect(systemRoutes.map((route) => route?.meta?.permission)).toEqual([
      'system:user:query',
      'system:role:query',
      'system:menu:query',
      'system:dict:list',
      'audit:query',
      'document:template:query',
      undefined,
    ])
    expect(systemRoutes.map((route) => route?.meta?.adminOnly)).toEqual([
      true,
      true,
      true,
      true,
      undefined,
      true,
      undefined,
    ])
    expect(systemRoutes[6]?.meta?.superAdminOnly).toBe(true)
    for (const route of systemRoutes) {
      expect(String(route?.component)).not.toContain('ShellPlaceholderPage')
    }
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

  it('guards master-data routes with API-aligned permissions and preserves material redirect', async () => {
    vi.mocked(getCurrentUser).mockResolvedValue(
      user(['partner:query', 'org:list', 'material:dict:list']),
    )
    const router = guardedRouter()

    await router.push('/org?view=tree')
    await router.isReady()
    expect(router.currentRoute.value.fullPath).toBe('/org?view=tree')

    await router.push('/material?source=legacy#dictionary')
    expect(router.currentRoute.value.fullPath).toBe('/material/dictionary?source=legacy#dictionary')
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

  it('keeps cost-subject root query and hash on the taxonomy redirect', async () => {
    vi.mocked(getCurrentUser).mockResolvedValue(user(['cost:query']))
    const router = guardedRouter()

    await router.push('/cost/subject?source=e2e#mapping')
    await router.isReady()

    expect(router.currentRoute.value.fullPath).toBe('/cost/subject/taxonomy?source=e2e#mapping')
  })

  it('fails a cost-subject tab closed without its exact read permission', async () => {
    vi.mocked(getCurrentUser).mockResolvedValue(user(['cost:query']))
    const router = guardedRouter()

    await router.push('/cost/subject/rules?source=deep-link')
    await router.isReady()

    expect(router.currentRoute.value.path).toBe('/forbidden')
    expect(router.currentRoute.value.query.from).toBe('/cost/subject/rules?source=deep-link')
  })

  it('keeps transaction-only access on the inventory ledger redirect', async () => {
    vi.mocked(getCurrentUser).mockResolvedValue(user(['inventory:transaction:list']))
    const router = guardedRouter()

    await router.push('/inventory/transaction?projectId=P1')
    await router.isReady()

    expect(router.currentRoute.value.fullPath).toBe('/inventory/stock?projectId=P1#transactions')
  })

  it('fails the inventory ledger redirect closed without looping', async () => {
    vi.mocked(getCurrentUser).mockResolvedValue(user([]))
    const router = guardedRouter()

    await router.push('/inventory/transaction?projectId=P1')
    await router.isReady()

    expect(router.currentRoute.value.path).toBe('/forbidden')
    expect(router.currentRoute.value.query.from).toBe('/inventory/stock?projectId=P1#transactions')
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

  it('allows every authenticated user to open account self-service deep links', async () => {
    vi.mocked(getCurrentUser).mockResolvedValue(user([]))
    const router = guardedRouter()

    for (const path of ['/profile?source=menu#details', '/settings', '/help']) {
      await router.push(path)
      await router.isReady()
      expect(router.currentRoute.value.fullPath).toBe(path)
    }
  })

  it('redirects an anonymous account deep link to login', async () => {
    vi.mocked(getCurrentUser).mockRejectedValue(new Error('anonymous'))
    const router = guardedRouter()

    await router.push('/settings?source=deep-link#preferences')
    await router.isReady()

    expect(router.currentRoute.value.path).toBe('/login')
    expect(router.currentRoute.value.query.redirect).toBe('/settings?source=deep-link#preferences')
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

  it('fails closed workflow configuration unless role and permission both match', async () => {
    for (const [roles, permissions, allowed] of [
      [['USER'], ['workflow:process:query'], false],
      [['ADMIN'], [], false],
      [['ADMIN'], ['workflow:process:query'], true],
      [['SUPER_ADMIN'], ['workflow:process:query'], true],
    ] as const) {
      setActivePinia(createPinia())
      vi.mocked(getCurrentUser).mockResolvedValue(user([...permissions], [...roles]))
      const router = guardedRouter()

      await router.push('/approval/process?source=deep-link#nodes')
      await router.isReady()

      expect(router.currentRoute.value.path).toBe(allowed ? '/approval/process' : '/forbidden')
      if (!allowed) {
        expect(router.currentRoute.value.query.from).toBe(
          '/approval/process?source=deep-link#nodes',
        )
      }
    }
  })

  it('guards system routes with role and API-aligned permissions', async () => {
    for (const [roles, permissions, path, allowed] of [
      [['USER'], ['system:user:query'], '/system/users', false],
      [['ADMIN'], [], '/system/users', false],
      [['ADMIN'], ['system:user:query'], '/system/users', true],
      [['ADMIN'], ['system:dict:list'], '/system?source=legacy#types', true],
      [['USER'], ['audit:query'], '/system/audit', true],
      [['ADMIN'], [], '/system/audit', false],
      [['ADMIN'], ['*'], '/system/data', false],
      [['SUPER_ADMIN'], [], '/system/data', true],
    ] as const) {
      setActivePinia(createPinia())
      vi.mocked(getCurrentUser).mockResolvedValue(user([...permissions], [...roles]))
      const router = guardedRouter()

      await router.push(path)
      await router.isReady()

      if (path.startsWith('/system?') && allowed) {
        expect(router.currentRoute.value.fullPath).toBe('/system/dict?source=legacy#types')
      } else {
        expect(router.currentRoute.value.path).toBe(allowed ? path : '/forbidden')
      }
    }
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

  it('keeps the Legacy 403 deep link and query/hash compatible', async () => {
    vi.mocked(getCurrentUser).mockResolvedValue(user([]))
    const router = guardedRouter()

    await router.push('/403?from=%2Fsystem%2Fusers#denied')
    await router.isReady()

    expect(router.currentRoute.value.fullPath).toBe('/forbidden?from=/system/users#denied')
  })

  it('requires a restored session before rendering an unknown route', async () => {
    vi.mocked(getCurrentUser).mockRejectedValue(new Error('anonymous'))
    const router = guardedRouter()

    await router.push('/definitely-not-a-v2-route?source=deep-link')
    await router.isReady()

    expect(router.currentRoute.value.path).toBe('/login')
    expect(router.currentRoute.value.query.redirect).toBe(
      '/definitely-not-a-v2-route?source=deep-link',
    )
  })

  it('accepts only internal non-login redirects', () => {
    expect(normalizeRedirect('/session?from=login')).toBe('/session?from=login')
    expect(normalizeRedirect('https://evil.example')).toBe('/session')
    expect(normalizeRedirect('//evil.example')).toBe('/session')
    expect(normalizeRedirect('/login?redirect=/session')).toBe('/session')
  })
})
