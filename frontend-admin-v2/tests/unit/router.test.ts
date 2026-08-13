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
    tenantId: '1001',
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
  it('registers communication separately from notification permissions', () => {
    const route = routes
      .flatMap((item) => item.children ?? [])
      .find((item) => item.path === '/communication')

    expect(route?.meta).toMatchObject({
      permission: 'communication:view',
      adminBypassesPermission: true,
    })
  })

  it('registers the project file center with its query permission', () => {
    const route = routes
      .flatMap((item) => item.children ?? [])
      .find((item) => item.path === '/project/files')

    expect(route?.meta?.permission).toBe('project:file:query')
  })

  it('loads the canonical cost target page without a budget wrapper', () => {
    const source = readFileSync(resolve('src/router/components.ts'), 'utf8')
    const route = routes
      .flatMap((item) => item.children ?? [])
      .find((item) => item.path === '/cost-budget')

    expect(route?.meta?.permission).toBe('cost:target:query')
    expect(source).toContain("'/cost-budget': CostTargetPage")
    expect(source).not.toContain('CostBudgetPage')
  })

  it('loads each purchase execution route through its focused workspace', () => {
    const source = readFileSync(resolve('src/router/components.ts'), 'utf8')

    expect(source).toContain("'/inventory/purchase-request': PurchaseRequestWorkspace")
    expect(source).toContain("'/purchase/order': PurchaseOrderWorkspace")
    expect(source).toContain("'/purchase/receipt': MaterialReceiptWorkspace")
    expect(source).not.toMatch(
      /'\/(?:inventory\/purchase-request|purchase\/(?:order|receipt))': PurchaseExecutionPage/,
    )
  })

  it('keeps retired Legacy as frozen ledger universe and locks acceptance counts', () => {
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

    expect(ledger.source).toBe(
      ['archive', 'v1.6', 'frontend-admin-legacy', 'src', 'router', 'index.ts'].join('/'),
    )
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
      ...[
        ['Taxonomy', 'CostSubjectTaxonomyPage'],
        ['Rules', 'CostSubjectRulesPage'],
        ['Scope', 'CostSubjectScopePage'],
        ['Trace', 'CostSubjectTracePage'],
      ].map(([suffix, page]) =>
        expect.objectContaining({
          name: `CostSubject${suffix}`,
          status: 'V2_ACCEPTED',
          v2View: `@/pages/master-data/cost-subject/${page}.vue`,
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
        v2View: '@/pages/system/access-control/UserManagementPage.vue',
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
        v2View: '@/pages/system/access-control/RoleManagementPage.vue',
      }),
      expect.objectContaining({
        name: 'SystemPermissions',
        status: 'V2_ACCEPTED',
        v2View: '@/pages/system/access-control/PermissionListPage.vue',
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

  it('registers technical entry routes', () => {
    expect(routes.find((route) => route.name === 'V2Health')).toMatchObject({ path: '/health' })
    expect(routes.find((route) => route.name === 'V2Login')).toMatchObject({ path: '/login' })
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

  it('merges cost target and budget routes while keeping legacy deep links compatible', async () => {
    vi.mocked(getCurrentUser).mockResolvedValue(
      user(['cost:target:query', 'cost:target:add', 'cost:target:edit']),
    )
    const router = guardedRouter()

    await router.push('/cost-target?projectId=P1#versions')
    await router.isReady()
    expect(router.currentRoute.value.fullPath).toBe('/cost-budget?projectId=P1#versions')

    await router.push('/cost-target/81/edit?projectId=P1')
    expect(router.currentRoute.value.fullPath).toBe('/cost-target/81/edit?projectId=P1')

    vi.mocked(getCurrentUser).mockResolvedValue(user(['cost:target:query']))
    const budgetRouter = guardedRouter()
    await budgetRouter.push('/budget?projectId=P1#versions')
    await budgetRouter.isReady()
    expect(budgetRouter.currentRoute.value.fullPath).toBe('/cost-budget?projectId=P1#versions')
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

  it('guards reports with the catalog permission and accepts employee self-service permissions', async () => {
    for (const [permission, path] of [
      ['report:catalog:query', '/dashboard/reports'],
      ['site:daily:self', '/site/daily-log'],
      ['purchase:request:self', '/inventory/purchase-request'],
      ['requisition:self', '/inventory/material-requisition'],
    ] as const) {
      setActivePinia(createPinia())
      vi.mocked(getCurrentUser).mockResolvedValue(user([permission]))
      const router = guardedRouter()

      await router.push(path)
      await router.isReady()

      expect(router.currentRoute.value.path).toBe(path)
    }

    setActivePinia(createPinia())
    vi.mocked(getCurrentUser).mockResolvedValue(user([]))
    const forbiddenRouter = guardedRouter()
    await forbiddenRouter.push('/dashboard/reports')
    await forbiddenRouter.isReady()
    expect(forbiddenRouter.currentRoute.value.path).toBe('/forbidden')
  })

  it('uses wildcard permission for the administrator sample without role-name checks', async () => {
    vi.mocked(getCurrentUser).mockResolvedValue(user(['*']))
    const router = guardedRouter()

    await router.push('/session')
    await router.isReady()

    expect(router.currentRoute.value.path).toBe('/dashboard')
  })

  it('keeps the session compatibility entry for anonymous and role-scoped users', async () => {
    vi.mocked(getCurrentUser).mockRejectedValueOnce(new Error('anonymous'))
    const anonymousRouter = guardedRouter()
    await anonymousRouter.push('/session?source=legacy#resume')
    await anonymousRouter.isReady()
    expect(anonymousRouter.currentRoute.value.path).toBe('/login')
    expect(anonymousRouter.currentRoute.value.query.redirect).toBe('/session?source=legacy#resume')

    setActivePinia(createPinia())
    vi.mocked(getCurrentUser).mockResolvedValueOnce(user(['project:query']))
    const authenticatedRouter = guardedRouter()
    await authenticatedRouter.push('/session')
    await authenticatedRouter.isReady()
    expect(authenticatedRouter.currentRoute.value.path).toBe('/approval/todo')
  })

  it('aligns workflow configuration routing with the API admin gate', async () => {
    for (const [roles, permissions, allowed] of [
      [['USER'], ['workflow:process:query'], false],
      [['ADMIN'], [], true],
      [['ADMIN'], ['workflow:process:query'], true],
      [['SUPER_ADMIN'], [], true],
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
    expect(normalizeRedirect('/v2')).toBe('/v2')
    expect(normalizeRedirect('/project/list')).toBe('/project/list')
  })
})
