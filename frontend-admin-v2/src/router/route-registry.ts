import type { RouteRecordRaw } from 'vue-router'
import type { WorkflowTab } from '@cgc-pms/frontend-contracts'
import { navigationDomains } from '../navigation/catalog'
import {
  AppShell,
  ForbiddenPage,
  HealthPage,
  LoginPage,
  NotFoundPage,
  navigationComponents,
} from './components'
import { contextRoutes } from './context-routes'

declare module 'vue-router' {
  interface RouteMeta {
    public?: boolean
    guestOnly?: boolean
    technical?: boolean
    shell?: boolean
    permission?: string
    permissions?: string[]
    adminOnly?: boolean
    superAdminOnly?: boolean
    adminBypassesPermission?: boolean
    workflowTab?: WorkflowTab
    migration?: 'pending'
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

function missingRouteComponent(path: string): never {
  throw new Error(`Accepted navigation route has no component: ${path}`)
}

function componentForNavigationPath(path: string) {
  return (
    navigationComponents[path as keyof typeof navigationComponents] ?? missingRouteComponent(path)
  )
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
          component: componentForNavigationPath(tab.path),
          meta: {
            shell: true,
            permission: tab.permission,
            permissions: tab.permissions,
            adminOnly: workspace.adminOnly || tab.adminOnly,
            superAdminOnly: workspace.superAdminOnly || tab.superAdminOnly,
            adminBypassesPermission:
              workspace.adminBypassesPermission || tab.adminBypassesPermission,
            workflowTab: approvalTab,
            migration: tab.migration,
          },
        },
      ]
    }),
  ),
)

export const routes: RouteRecordRaw[] = [
  { path: '/', redirect: '/session' },
  { path: '/v2', redirect: '/' },
  {
    path: '/v2/:pathMatch(.*)*',
    name: 'RetiredV2BaseRedirect',
    redirect: (to) => {
      const path = Array.isArray(to.params.pathMatch)
        ? to.params.pathMatch.join('/')
        : String(to.params.pathMatch ?? '')
      return { path: `/${path}`, query: to.query, hash: to.hash }
    },
  },
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
      {
        path: '/403',
        name: 'V2LegacyForbiddenRedirect',
        redirect: (to) => ({ path: '/forbidden', query: to.query, hash: to.hash }),
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
