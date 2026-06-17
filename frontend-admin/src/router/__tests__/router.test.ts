import { describe, it, expect } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'
import { routes } from '@/router'

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

  it('approval process management route is admin-only and lazily loaded', () => {
    const rootRoute = routes.find((r) => r.path === '/')
    const approvalRoute = rootRoute?.children?.find((c) => c.path === 'approval')
    const processRoute = approvalRoute?.children?.find((c) => c.path === 'process')

    expect(processRoute).toBeDefined()
    expect(processRoute?.name).toBe('ApprovalProcess')
    expect(processRoute?.meta?.title).toBe('审批流程管理')
    expect(processRoute?.meta?.adminOnly).toBe(true)
    expect(typeof processRoute!.component).toBe('function')
  })

  it('uses business-domain titles for target cost and approval center', () => {
    const rootRoute = routes.find((r) => r.path === '/')
    const costTargetRoute = rootRoute?.children?.find((c) => c.path === 'cost-target')
    const approvalRoute = rootRoute?.children?.find((c) => c.path === 'approval')

    expect(costTargetRoute?.meta?.title).toBe('目标成本')
    expect(approvalRoute?.meta?.title).toBe('审批中心')
  })

  it('registers dedicated approval done and cc routes', () => {
    const rootRoute = routes.find((r) => r.path === '/')
    const approvalRoute = rootRoute?.children?.find((c) => c.path === 'approval')
    const doneRoute = approvalRoute?.children?.find((c) => c.path === 'done')
    const ccRoute = approvalRoute?.children?.find((c) => c.path === 'cc')

    expect(doneRoute?.name).toBe('ApprovalDone')
    expect(doneRoute?.meta?.title).toBe('我的已办')
    expect(doneRoute?.meta?.approvalTab).toBe('done')
    expect(typeof doneRoute?.component).toBe('function')
    expect(ccRoute?.name).toBe('ApprovalCc')
    expect(ccRoute?.meta?.title).toBe('抄送我的')
    expect(ccRoute?.meta?.approvalTab).toBe('cc')
    expect(typeof ccRoute?.component).toBe('function')
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
})
