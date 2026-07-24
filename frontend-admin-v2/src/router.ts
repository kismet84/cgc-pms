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
const ReportCatalogPage = () => import('./pages/workbench/ReportCatalogPage.vue')
const ProjectPage = () => import('./pages/projects/ProjectPage.vue')
const ContractPage = () => import('./pages/commercial/ContractPage.vue')
const VariationPage = () => import('./pages/commercial/VariationPage.vue')
const BidCostPage = () => import('./pages/commercial/BidCostPage.vue')
const CostTargetPage = () => import('./pages/commercial/CostTargetPage.vue')
const CostLedgerPage = () => import('./pages/commercial/CostLedgerPage.vue')
const CostSummaryPage = () => import('./pages/commercial/CostSummaryPage.vue')
const CostControlPage = () => import('./pages/commercial/CostControlPage.vue')
const BudgetPage = () => import('./pages/commercial/BudgetPage.vue')
const ProductionMeasurementPage = () => import('./pages/commercial/ProductionMeasurementPage.vue')
const SchedulePage = () => import('./pages/delivery/SchedulePage.vue')
const DailyLogPage = () => import('./pages/delivery/DailyLogPage.vue')
const QualitySafetyPage = () => import('./pages/delivery/QualitySafetyPage.vue')
const TechnicalManagementPage = () => import('./pages/delivery/TechnicalManagementPage.vue')
const ProjectCloseoutPage = () => import('./pages/delivery/ProjectCloseoutPage.vue')

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
              : tab.path === '/dashboard/reports'
                ? ReportCatalogPage
                : approvalTab
                  ? WorkflowWorkbenchPage
                  : tab.path === '/project/list'
                    ? ProjectPage
                    : tab.path === '/contract/ledger'
                      ? ContractPage
                      : tab.path === '/variation/order'
                        ? VariationPage
                        : tab.path === '/bid-cost'
                          ? BidCostPage
                          : tab.path === '/cost-target/index'
                            ? CostTargetPage
                            : tab.path === '/cost/ledger'
                              ? CostLedgerPage
                              : tab.path === '/cost/summary'
                                ? CostSummaryPage
                                : tab.path === '/cost/control'
                                  ? CostControlPage
                                  : tab.path === '/budget'
                                    ? BudgetPage
                                    : tab.path === '/production-measurement'
                                      ? ProductionMeasurementPage
                                      : tab.path === '/project-schedule'
                                        ? SchedulePage
                                        : tab.path === '/site/daily-log'
                                          ? DailyLogPage
                                          : tab.path === '/quality-safety'
                                            ? QualitySafetyPage
                                            : tab.path === '/technical-management'
                                              ? TechnicalManagementPage
                                              : tab.path === '/project-closeout'
                                                ? ProjectCloseoutPage
                                                : ShellPlaceholderPage,
          meta: {
            shell: true,
            permission: tab.permission,
            workflowTab: approvalTab,
          },
        },
      ]
    }),
  ),
)

const contextRoutes: RouteRecordRaw[] = [
  {
    path: '/project',
    name: 'V2ProjectRedirect',
    redirect: (to) => ({ path: '/project/list', query: to.query, hash: to.hash }),
    meta: { shell: true, permission: 'project:query' },
  },
  {
    path: '/alert',
    name: 'V2LegacyAlertRedirect',
    redirect: (to) => ({ path: '/dashboard', query: to.query, hash: '#risk-list' }),
    meta: { shell: true, permission: 'alert:view' },
  },
  {
    path: '/approval',
    name: 'V2LegacyApprovalRedirect',
    redirect: (to) => ({ path: '/approval/todo', query: to.query }),
    meta: { shell: true },
  },
  {
    path: '/approval/instances/:instanceId',
    name: 'V2WorkflowInstanceDetail',
    component: WorkflowWorkbenchPage,
    meta: { shell: true, workflowTab: 'todo' },
  },
  {
    path: '/approval/:instanceId',
    name: 'V2LegacyApprovalDetailRedirect',
    redirect: (to) => ({
      path: `/approval/instances/${String(to.params.instanceId)}`,
      query: to.query,
    }),
    meta: { shell: true },
  },
  {
    path: '/project-schedule/:scheduleId',
    name: 'V2ShellProjectScheduleDetail',
    component: SchedulePage,
    meta: {
      shell: true,
      permission: 'schedule:query',
    },
  },
  {
    path: '/project/:projectId/overview',
    name: 'V2ShellProjectOverview',
    component: ProjectPage,
    meta: { shell: true, permission: 'project:query' },
  },
  {
    path: '/project/:projectId/members',
    name: 'V2ShellProjectMembers',
    component: ProjectPage,
    meta: { shell: true, permission: 'project:member:list' },
  },
  {
    path: '/project/:projectId/edit',
    name: 'V2ShellProjectEdit',
    component: ProjectPage,
    meta: { shell: true, permission: 'project:edit' },
  },
  {
    path: '/contract',
    name: 'V2ContractRootRedirect',
    redirect: (to) => ({ path: '/contract/ledger', query: to.query, hash: to.hash }),
    meta: { shell: true, permission: 'contract:query' },
  },
  {
    path: '/variation',
    name: 'V2VariationRootRedirect',
    redirect: (to) => ({ path: '/variation/order', query: to.query, hash: to.hash }),
    meta: { shell: true, permission: 'variation:order:query' },
  },
  {
    path: '/cost',
    name: 'V2CostRootRedirect',
    redirect: (to) => ({ path: '/cost/ledger', query: to.query, hash: to.hash }),
    meta: { shell: true, permission: 'cost:ledger:query' },
  },
  {
    path: '/cost-target',
    name: 'V2CostTargetRootRedirect',
    redirect: (to) => ({ path: '/cost-target/index', query: to.query, hash: to.hash }),
    meta: { shell: true, permission: 'cost:target:query' },
  },
  {
    path: '/cost-target/create',
    name: 'V2ShellCostTargetCreate',
    component: CostTargetPage,
    meta: {
      shell: true,
      permission: 'cost:target:add',
    },
  },
  {
    path: '/cost-target/:id/edit',
    name: 'V2ShellCostTargetEdit',
    component: CostTargetPage,
    meta: {
      shell: true,
      permission: 'cost:target:edit',
    },
  },
  {
    path: '/contract/create',
    name: 'V2ShellContractCreate',
    component: ContractPage,
    meta: { shell: true, permission: 'contract:add' },
  },
  {
    path: '/contract/:id',
    name: 'V2ShellContractDetail',
    component: ContractPage,
    meta: { shell: true, permission: 'contract:query' },
  },
  {
    path: '/contract/:id/edit',
    name: 'V2ShellContractEdit',
    component: ContractPage,
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
