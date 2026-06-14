import { describe, it, expect } from 'vitest'
import { routes } from '@/router'

describe('router lazy loading', () => {
  it('login route is lazily loaded', () => {
    const loginRoute = routes.find(r => r.path === '/login')
    expect(loginRoute).toBeDefined()
    // Dynamic import functions (() => import(...)) have typeof 'function'.
    // Static imports resolve to component objects (typeof 'object').
    expect(typeof loginRoute!.component).toBe('function')
  })

  it('login route is public (meta.public)', () => {
    const loginRoute = routes.find(r => r.path === '/login')
    expect(loginRoute?.meta?.public).toBe(true)
  })

  it('dashboard child route is lazily loaded', () => {
    const rootRoute = routes.find(r => r.path === '/')
    const dashboardRoute = rootRoute?.children?.find(c => c.path === 'dashboard')
    expect(dashboardRoute).toBeDefined()
    expect(typeof dashboardRoute!.component).toBe('function')
  })

  it('root route component is lazy (BasicLayout via dynamic import)', () => {
    // When Task 3 is complete, BasicLayout will be imported via
    //   component: () => import('@/layouts/BasicLayout.vue')
    // instead of static import. That makes typeof a function.
    const rootRoute = routes.find(r => r.path === '/')
    expect(rootRoute).toBeDefined()
    expect(typeof rootRoute!.component).toBe('function')
  })

  it('Not Found (404) catch-all route is lazily loaded', () => {
    const notFoundRoute = routes.find(r => r.name === 'NotFound')
    expect(notFoundRoute).toBeDefined()
    expect(typeof notFoundRoute!.component).toBe('function')
  })

  it('contract ledger child route is lazily loaded', () => {
    const rootRoute = routes.find(r => r.path === '/')
    const contractRoute = rootRoute?.children?.find(c => c.path === 'contract')
    const ledgerRoute = contractRoute?.children?.find(c => c.path === 'ledger')
    expect(ledgerRoute).toBeDefined()
    expect(typeof ledgerRoute!.component).toBe('function')
  })
})
