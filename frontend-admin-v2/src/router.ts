import type { RouteLocationNormalized, Router } from 'vue-router'
import { createRouter, createWebHistory } from 'vue-router'
import { firstAccessiblePath } from './navigation/catalog'
import { normalizeRedirect } from './services/navigation'
import { useSessionStore } from './stores/session'
import { routes } from './router/route-registry'

export { routes } from './router/route-registry'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes,
})

export function installSessionGuard(targetRouter: Router): void {
  targetRouter.beforeEach(async (to) => {
    const session = useSessionStore()

    if (to.meta.public && !to.meta.guestOnly) return true

    if (session.status === 'idle') await session.restore()

    if (to.meta.guestOnly) {
      return session.isAuthenticated ? safeRedirect(to, '/session') : true
    }

    if (!session.isAuthenticated) {
      return { path: '/login', query: { redirect: to.fullPath } }
    }

    if (to.path === '/session') {
      return {
        path: firstAccessiblePath(session.roles, session.permissions) ?? '/forbidden',
        query: to.query,
      }
    }

    if (to.meta.adminOnly && !session.isAdmin) {
      return { path: '/forbidden', query: { from: to.fullPath } }
    }

    if (to.meta.superAdminOnly && !session.roles.includes('SUPER_ADMIN')) {
      return { path: '/forbidden', query: { from: to.fullPath } }
    }

    const requiredPermission =
      to.path === '/inventory/stock' && to.redirectedFrom?.path === '/inventory/transaction'
        ? 'inventory:transaction:list'
        : to.meta.permission
    const requiredPermissions = to.meta.permissions
    const hasRequiredPermission =
      to.meta.adminBypassesPermission && session.isAdmin
        ? true
        : requiredPermissions?.length
          ? requiredPermissions.some((permission) => session.hasPermission(permission))
          : !requiredPermission || session.hasPermission(requiredPermission)
    if (!hasRequiredPermission) {
      return { path: '/forbidden', query: { from: to.fullPath } }
    }

    return true
  })
}

function safeRedirect(to: RouteLocationNormalized, fallback: string): string {
  return normalizeRedirect(to.query.redirect, fallback)
}

installSessionGuard(router)

export default router
