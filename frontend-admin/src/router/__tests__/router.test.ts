import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'
import { getUserInfo } from '@/api/modules/auth'
import router, { routes, handleAuthGuard } from '@/router'
import { useUserStore } from '@/stores/user'
import type { UserInfo } from '@/types/user'

vi.mock('@/api/modules/auth', async () => {
  const actual = await vi.importActual<typeof import('@/api/modules/auth')>('@/api/modules/auth')
  return {
    ...actual,
    getUserInfo: vi.fn(),
  }
})

const mockGetUserInfo = vi.mocked(getUserInfo)

const sessionUserInfo: UserInfo = {
  userId: '1',
  username: 'dev-admin',
  realName: '开发管理员',
  roles: ['ADMIN'],
  permissions: ['*'],
  roleName: '管理员',
}

beforeEach(() => {
  sessionStorage.clear()
  mockGetUserInfo.mockReset()
  setActivePinia(createPinia())
})

function buildUserInfo(overrides: Partial<UserInfo>): UserInfo {
  return {
    ...sessionUserInfo,
    ...overrides,
  }
}

describe('router lazy loading', () => {
  it('login route is lazily loaded', () => {
    const loginRoute = routes.find((r) => r.path === '/login')
    expect(loginRoute).toBeDefined()
    // Dynamic import functions (() => import(...)) have typeof 'function'.
    // Static imports resolve to component objects (typeof 'object').
    expect(typeof loginRoute!.component).toBe('function')
  })

  it('login route is public (meta.public)', () => {
    const loginRoute = routes.find((r) => r.path === '/login')
    expect(loginRoute?.meta?.public).toBe(true)
  })

  it('dashboard child route is lazily loaded', () => {
    const rootRoute = routes.find((r) => r.path === '/')
    const dashboardRoute = rootRoute?.children?.find((c) => c.path === 'dashboard')
    expect(dashboardRoute).toBeDefined()
    expect(typeof dashboardRoute!.component).toBe('function')
  })

  it('root route component is lazy (BasicLayout via dynamic import)', () => {
    // When Task 3 is complete, BasicLayout will be imported via
    //   component: () => import('@/layouts/BasicLayout.vue')
    // instead of static import. That makes typeof a function.
    const rootRoute = routes.find((r) => r.path === '/')
    expect(rootRoute).toBeDefined()
    expect(typeof rootRoute!.component).toBe('function')
  })

  it('Not Found (404) catch-all route is lazily loaded', () => {
    const notFoundRoute = routes.find((r) => r.name === 'NotFound')
    expect(notFoundRoute).toBeDefined()
    expect(typeof notFoundRoute!.component).toBe('function')
  })

  it('contract ledger child route is lazily loaded', () => {
    const rootRoute = routes.find((r) => r.path === '/')
    const contractRoute = rootRoute?.children?.find((c) => c.path === 'contract')
    const ledgerRoute = contractRoute?.children?.find((c) => c.path === 'ledger')
    expect(ledgerRoute).toBeDefined()
    expect(typeof ledgerRoute!.component).toBe('function')
  })

  it('partner route is registered and lazily loaded', () => {
    const rootRoute = routes.find((r) => r.path === '/')
    const partnerRoute = rootRoute?.children?.find((c) => c.path === 'partner')
    expect(partnerRoute).toBeDefined()
    expect(partnerRoute?.name).toBe('Partner')
    expect(partnerRoute?.meta?.title).toBe('合作方管理')
    expect(partnerRoute?.meta?.icon).toBe('TeamOutlined')
    expect(typeof partnerRoute!.component).toBe('function')
  })

  it('registers the cash journal route with query permission', () => {
    const rootRoute = routes.find((r) => r.path === '/')
    const cashJournalRoute = rootRoute?.children?.find((c) => c.path === 'cash-journal')

    expect(cashJournalRoute?.name).toBe('CashJournal')
    expect(cashJournalRoute?.meta?.title).toBe('资金日记账')
    expect(cashJournalRoute?.meta?.permission).toBe('cashbook:journal:query')
    expect(typeof cashJournalRoute?.component).toBe('function')
  })

  it.each([
    ['/cash-journal', 'cashbook:journal:query'],
    ['/contract/ledger', 'contract:query'],
  ])('injects %s permission into the actual normalized route', (path, permission) => {
    expect(router.resolve(path).meta.permission).toBe(permission)
  })

  it('approval process route is admin-only and lazily loaded', () => {
    const rootRoute = routes.find((r) => r.path === '/')
    const approvalRoute = rootRoute?.children?.find((c) => c.path === 'approval')
    const processRoute = approvalRoute?.children?.find((c) => c.path === 'process')

    expect(processRoute).toBeDefined()
    expect(processRoute?.name).toBe('ApprovalProcess')
    expect(processRoute?.meta?.title).toBe('审批流程')
    expect(processRoute?.meta?.adminOnly).toBe(true)
    expect(typeof processRoute!.component).toBe('function')
  })

  it('uses business-domain titles for target cost and approval center', () => {
    const rootRoute = routes.find((r) => r.path === '/')
    const costTargetRoute = rootRoute?.children?.find((c) => c.path === 'cost-target')
    const approvalRoute = rootRoute?.children?.find((c) => c.path === 'approval')

    expect(costTargetRoute?.meta?.title).toBe('成本目标')
    expect(approvalRoute?.meta?.title).toBe('审批中心')
  })

  it('uses the transaction list authority for the inventory transaction entry', () => {
    const rootRoute = routes.find((r) => r.path === '/')
    const inventoryRoute = rootRoute?.children?.find((c) => c.name === 'Inventory')
    const transactionRoute = inventoryRoute?.children?.find(
      (c) => c.name === 'InventoryTransaction',
    )

    expect(transactionRoute?.meta?.permission).toBe('inventory:transaction:list')
  })

  it('uses the backend list authority for the project members entry', () => {
    const rootRoute = routes.find((r) => r.path === '/')
    const projectRoute = rootRoute?.children?.find((c) => c.name === 'Project')
    const membersRoute = projectRoute?.children?.find((c) => c.name === 'ProjectMembers')

    expect(membersRoute?.meta?.permission).toBe('project:member:list')
  })

  it('registers the operation audit route with audit query permission', () => {
    const rootRoute = routes.find((r) => r.path === '/')
    const systemRoute = rootRoute?.children?.find((c) => c.name === 'System')
    const auditRoute = systemRoute?.children?.find((c) => c.name === 'SystemAudit')

    expect(auditRoute?.meta?.permission).toBe('audit:query')
    expect(auditRoute?.meta?.adminOnly).toBe(false)
    expect(typeof auditRoute?.component).toBe('function')
    expect(router.resolve('/system/audit').meta.permission).toBe('audit:query')
    expect(router.resolve('/system/audit').meta.adminOnly).toBe(false)
  })

  it('registers dedicated approval done, cc and mine routes', () => {
    const rootRoute = routes.find((r) => r.path === '/')
    const approvalRoute = rootRoute?.children?.find((c) => c.path === 'approval')
    const doneRoute = approvalRoute?.children?.find((c) => c.path === 'done')
    const ccRoute = approvalRoute?.children?.find((c) => c.path === 'cc')
    const mineRoute = approvalRoute?.children?.find((c) => c.path === 'mine')

    expect(doneRoute?.name).toBe('ApprovalDone')
    expect(doneRoute?.meta?.title).toBe('我的已办')
    expect(doneRoute?.meta?.approvalTab).toBe('done')
    expect(typeof doneRoute?.component).toBe('function')
    expect(ccRoute?.name).toBe('ApprovalCc')
    expect(ccRoute?.meta?.title).toBe('抄送我的')
    expect(ccRoute?.meta?.approvalTab).toBe('cc')
    expect(typeof ccRoute?.component).toBe('function')
    expect(mineRoute?.name).toBe('ApprovalMine')
    expect(mineRoute?.meta?.title).toBe('我发起')
    expect(mineRoute?.meta?.approvalTab).toBe('mine')
    expect(typeof mineRoute?.component).toBe('function')
  })

  it('matches approval process route before approval detail route', async () => {
    const router = createRouter({
      history: createMemoryHistory(),
      routes,
    })

    await router.push('/approval/process')
    await router.isReady()

    expect(router.currentRoute.value.name).toBe('ApprovalProcess')
  })

  it('hydrates user info from backend session before allowing protected route', async () => {
    mockGetUserInfo.mockResolvedValue(sessionUserInfo)
    const userStore = useUserStore()

    const result = await handleAuthGuard({
      path: '/alert',
      fullPath: '/alert',
      meta: {},
    } as never)

    expect(mockGetUserInfo).toHaveBeenCalledTimes(1)
    expect(userStore.userInfo).toEqual(sessionUserInfo)
    expect(result).toBe(true)
  })

  it('redirects to login when backend session recovery fails', async () => {
    mockGetUserInfo.mockRejectedValue(new Error('401'))

    const result = await handleAuthGuard({
      path: '/alert',
      fullPath: '/alert',
      meta: {},
    } as never)

    expect(mockGetUserInfo).toHaveBeenCalledTimes(1)
    expect(result).toEqual({
      path: '/login',
      query: { redirect: '/alert' },
    })
  })

  it('allows dashboard route when user only has legacy dashboard:view', async () => {
    const userStore = useUserStore()
    userStore.setUserInfo(
      buildUserInfo({
        roles: ['USER'],
        permissions: ['dashboard:view'],
      }),
    )

    const result = await handleAuthGuard({
      path: '/dashboard',
      fullPath: '/dashboard',
      meta: { permission: 'dashboard:view' },
    } as never)

    expect(mockGetUserInfo).not.toHaveBeenCalled()
    expect(result).toBe(true)
  })

  it('allows dashboard route when user only has a scoped dashboard permission', async () => {
    const userStore = useUserStore()
    userStore.setUserInfo(
      buildUserInfo({
        roles: ['USER'],
        permissions: ['dashboard:project-manager:view'],
      }),
    )

    const result = await handleAuthGuard({
      path: '/dashboard',
      fullPath: '/dashboard',
      meta: { permission: 'dashboard:view' },
    } as never)

    expect(mockGetUserInfo).not.toHaveBeenCalled()
    expect(result).toBe(true)
  })

  it('blocks dashboard route when user has no dashboard permission', async () => {
    const userStore = useUserStore()
    userStore.setUserInfo(
      buildUserInfo({
        roles: ['USER'],
        permissions: ['alert:view'],
      }),
    )

    const result = await handleAuthGuard({
      path: '/dashboard',
      fullPath: '/dashboard',
      meta: { permission: 'dashboard:view' },
    } as never)

    expect(mockGetUserInfo).not.toHaveBeenCalled()
    expect(result).toEqual({ path: '/403' })
  })

  it('redirects direct cash journal access when query permission is missing', async () => {
    const userStore = useUserStore()
    userStore.setUserInfo(buildUserInfo({ roles: ['USER'], permissions: ['payment:app:query'] }))

    const result = await handleAuthGuard({
      path: '/cash-journal',
      fullPath: '/cash-journal',
      meta: { permission: 'cashbook:journal:query' },
    } as never)

    expect(result).toEqual({ path: '/403' })
  })

  it.each([
    ['/cash-journal', ['payment:app:query']],
    ['/approval/process', ['workflow:task:query']],
    ['/dashboard', ['alert:view']],
  ])('navigates a logged-in unauthorized user from %s to the Forbidden route', async (path, permissions) => {
    const userStore = useUserStore()
    userStore.setUserInfo(buildUserInfo({ roles: ['USER'], permissions }))
    const testRouter = createRouter({ history: createMemoryHistory(), routes })
    testRouter.beforeEach(handleAuthGuard)

    await testRouter.push(path)

    expect(testRouter.currentRoute.value.name).toBe('Forbidden')
    expect(testRouter.currentRoute.value.path).toBe('/403')
  })

  it('allows a logged-in user to enter the permission-free Forbidden route without a redirect loop', async () => {
    const userStore = useUserStore()
    userStore.setUserInfo(buildUserInfo({ roles: ['USER'], permissions: [] }))
    const testRouter = createRouter({ history: createMemoryHistory(), routes })
    testRouter.beforeEach(handleAuthGuard)

    await testRouter.push('/403')

    expect(testRouter.currentRoute.value.name).toBe('Forbidden')
    expect(testRouter.currentRoute.value.meta.permission).toBeUndefined()
  })

  it.each(['ADMIN', 'SUPER_ADMIN'])('allows %s through a protected route without a duplicated permission grant', async (role) => {
    const userStore = useUserStore()
    userStore.setUserInfo(buildUserInfo({ roles: [role], permissions: [] }))

    const result = await handleAuthGuard({
      path: '/cash-journal',
      fullPath: '/cash-journal',
      meta: { permission: 'cashbook:journal:query' },
    } as never)

    expect(result).toBe(true)
  })
})
