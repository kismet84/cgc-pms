import type { RouteLocationNormalized, RouteRecordRaw } from 'vue-router'
import { createRouter, createWebHistory, type Router } from 'vue-router'
import AppShell from './layouts/AppShell.vue'
import { firstAccessiblePath, navigationDomains } from './navigation/catalog'
import LoginPage from './pages/auth/LoginPage.vue'
import SessionPage from './pages/auth/SessionPage.vue'
import HealthPage from './pages/HealthPage.vue'
import { normalizeRedirect } from './services/navigation'
import { useSessionStore } from './stores/session'
import type { WorkflowTab } from '@cgc-pms/frontend-contracts'

const ForbiddenPage = () => import('./pages/errors/ForbiddenPage.vue')
const NotFoundPage = () => import('./pages/errors/NotFoundPage.vue')
const ShellPlaceholderPage = () => import('./pages/shell/ShellPlaceholderPage.vue')
const DashboardPage = () => import('./pages/dashboard/DashboardPage.vue')
const WorkflowWorkbenchPage = () => import('./pages/workbench/WorkflowWorkbenchPage.vue')

declare module 'vue-router' {
  interface RouteMeta {
    public?: boolean
    guestOnly?: boolean
    technical?: boolean
    shell?: boolean
    permission?: string
    workflowTab?: WorkflowTab
  }
}

function routeName(path: string): string {
  return `V2Shell${path
    .replaceAll(/[^a-zA-Z0-9]+/g, ' ')
    .trim()
    .replaceAll(/\s+(.)/g, (_, value: string) => value.toUpperCase())
    .replace(/^./, (value) => value.toUpperCase())}`
}

function workflowTab(path: string): WorkflowTab | undefined {
  const value = path.match(/^\/approval\/(todo|done|cc|mine)$/)?.[1]
  return value as WorkflowTab | undefined
}

const registeredPaths = new Set<string>()
const navigationRoutes: RouteRecordRaw[] = navigationDomains.flatMap((domain) =>
  domain.workspaces.flatMap((workspace) =>
    workspace.tabs.flatMap((tab) => {
      if (registeredPaths.has(tab.path)) return []
      registeredPaths.add(tab.path)
      const approvalTab = workflowTab(tab.path)
      return [
        {
          path: tab.path,
          name: routeName(tab.path),
          component:
            tab.path === '/dashboard'
              ? DashboardPage
              : approvalTab
                ? WorkflowWorkbenchPage
                : ShellPlaceholderPage,
          meta: { shell: true, permission: tab.permission, workflowTab: approvalTab },
        },
      ]
    }),
  ),
)

const contextRoutes: RouteRecordRaw[] = [
  {
    path: '/approval/instances/:instanceId',
    name: 'V2WorkflowInstanceDetail',
    component: WorkflowWorkbenchPage,
    meta: { shell: true, workflowTab: 'todo' },
  },
  {
    path: '/project/:projectId/overview',
    name: 'V2ShellProjectOverview',
    component: ShellPlaceholderPage,
    meta: { shell: true, permission: 'project:query' },
  },
  {
    path: '/project/:projectId/members',
    name: 'V2ShellProjectMembers',
    component: ShellPlaceholderPage,
    meta: { shell: true, permission: 'project:member:list' },
  },
  {
    path: '/project/:projectId/edit',
    name: 'V2ShellProjectEdit',
    component: ShellPlaceholderPage,
    meta: { shell: true, permission: 'project:edit' },
  },
  {
    path: '/contract/:id',
    name: 'V2ShellContractDetail',
    component: ShellPlaceholderPage,
    meta: { shell: true, permission: 'contract:query' },
  },
  {
    path: '/contract/:id/edit',
    name: 'V2ShellContractEdit',
    component: ShellPlaceholderPage,
    meta: { shell: true, permission: 'contract:edit' },
  },
  {
    path: '/settlement/:id',
    name: 'V2ShellSettlementDetail',
    component: ShellPlaceholderPage,
    meta: { shell: true, permission: 'settlement:query' },
  },
]

export const routes: RouteRecordRaw[] = [
  { path: '/', redirect: '/session' },
  {
    path: '/health',
    name: 'V2Health',
    component: HealthPage,
    meta: { public: true, technical: true },
  },
  {
    path: '/login',
    name: 'V2Login',
    component: LoginPage,
    meta: { public: true, guestOnly: true },
  },
  {
    path: '/session',
    name: 'V2Session',
    component: SessionPage,
  },
  {
    path: '/shell',
    component: AppShell,
    meta: { shell: true },
    children: [
      ...navigationRoutes,
      ...contextRoutes,
      {
        path: '/forbidden',
        name: 'V2Forbidden',
        component: ForbiddenPage,
        meta: { shell: true },
      },
      { path: '/no-access', redirect: '/forbidden' },
      {
        path: '/:pathMatch(.*)*',
        name: 'V2NotFound',
        component: NotFoundPage,
        meta: { shell: true },
      },
    ],
  },
]

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
      return { path: firstAccessiblePath(session.permissions) ?? '/forbidden', query: to.query }
    }

    if (to.meta.permission && !session.hasPermission(to.meta.permission)) {
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
