import type { RouteLocationNormalized, RouteRecordRaw } from 'vue-router'
import { createRouter, createWebHistory, type Router } from 'vue-router'
import AppShell from './layouts/AppShell.vue'
import { firstAccessiblePath, navigationDomains } from './navigation/catalog'
import LoginPage from './pages/auth/LoginPage.vue'
import HealthPage from './pages/HealthPage.vue'
import { normalizeRedirect } from './services/navigation'
import { useSessionStore } from './stores/session'
import type { WorkflowTab } from '@cgc-pms/frontend-contracts'

const ForbiddenPage = () => import('./pages/errors/ForbiddenPage.vue')
const NotFoundPage = () => import('./pages/errors/NotFoundPage.vue')
const DashboardPage = () => import('./pages/dashboard/DashboardPage.vue')
const WorkflowWorkbenchPage = () => import('./pages/workbench/WorkflowWorkbenchPage.vue')
const ReportCatalogPage = () => import('./pages/workbench/ReportCatalogPage.vue')
const ProjectPage = () => import('./pages/projects/ProjectPage.vue')
const ProjectFileCenterPage = () => import('./pages/project/ProjectFileCenterPage.vue')
const CommunicationPage = () => import('./pages/communication/CommunicationPage.vue')
const ContractPage = () => import('./pages/commercial/ContractPage.vue')
const VariationPage = () => import('./pages/commercial/VariationPage.vue')
const BidCostPage = () => import('./pages/commercial/BidCostPage.vue')
const BidTenderDetailPage = () => import('./pages/commercial/BidTenderDetailPage.vue')
const BidTenderCostPage = () => import('./pages/commercial/BidTenderCostPage.vue')
const CostTargetPage = () => import('./pages/commercial/CostTargetPage.vue')
const CostBudgetPage = () => import('./pages/commercial/CostBudgetPage.vue')
const CostLedgerPage = () => import('./pages/commercial/CostLedgerPage.vue')
const CostSummaryPage = () => import('./pages/commercial/CostSummaryPage.vue')
const CostControlPage = () => import('./pages/commercial/CostControlPage.vue')
const ProductionMeasurementPage = () => import('./pages/commercial/ProductionMeasurementPage.vue')
const SchedulePage = () => import('./pages/delivery/SchedulePage.vue')
const DailyLogPage = () => import('./pages/delivery/DailyLogPage.vue')
const QualitySafetyPage = () => import('./pages/delivery/QualitySafetyPage.vue')
const TechnicalManagementPage = () => import('./pages/delivery/TechnicalManagementPage.vue')
const ProjectCloseoutPage = () => import('./pages/delivery/ProjectCloseoutPage.vue')
const SupplierSourcingPage = () => import('./pages/supply-chain/SupplierSourcingPage.vue')
const PurchaseExecutionPage = () => import('./pages/supply-chain/PurchaseExecutionPage.vue')
const InventoryWorkspacePage = () => import('./pages/supply-chain/InventoryWorkspacePage.vue')
const RequisitionWorkspacePage = () => import('./pages/supply-chain/RequisitionWorkspacePage.vue')
const SubcontractWorkspacePage = () => import('./pages/subcontract/SubcontractWorkspacePage.vue')
const SettlementWorkspacePage = () => import('./pages/settlement/SettlementWorkspacePage.vue')
const ReceivablesWorkspacePage = () => import('./pages/finance/ReceivablesWorkspacePage.vue')
const FinanceControlWorkspacePage = () => import('./pages/finance/FinanceControlWorkspacePage.vue')
const AccountPage = () => import('./pages/account/AccountPage.vue')
const PartnerPage = () => import('./pages/master-data/PartnerPage.vue')
const PartnerDetailPage = () => import('./pages/master-data/PartnerDetailPage.vue')
const OrganizationPage = () => import('./pages/master-data/OrganizationPage.vue')
const MaterialDictionaryPage = () => import('./pages/master-data/MaterialDictionaryPage.vue')
const CostSubjectPage = () => import('./pages/master-data/CostSubjectPage.vue')
const WorkflowProcessPage = () => import('./pages/system/WorkflowProcessPage.vue')
const AccessControlPage = () => import('./pages/system/AccessControlPage.vue')
const DictionaryPage = () => import('./pages/system/DictionaryPage.vue')
const AuditPage = () => import('./pages/system/AuditPage.vue')
const DocumentTemplatePage = () => import('./pages/system/DocumentTemplatePage.vue')
const DataMaintenancePage = () => import('./pages/system/DataMaintenancePage.vue')

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

const navigationComponents = {
  '/dashboard': DashboardPage,
  '/dashboard/reports': ReportCatalogPage,
  '/approval/todo': WorkflowWorkbenchPage,
  '/approval/done': WorkflowWorkbenchPage,
  '/approval/cc': WorkflowWorkbenchPage,
  '/approval/mine': WorkflowWorkbenchPage,
  '/project/list': ProjectPage,
  '/project/files': ProjectFileCenterPage,
  '/communication': CommunicationPage,
  '/contract/ledger': ContractPage,
  '/variation/order': VariationPage,
  '/engineering-tender/records': BidCostPage,
  '/engineering-tender/costs': BidTenderCostPage,
  '/cost-budget': CostBudgetPage,
  '/cost/ledger': CostLedgerPage,
  '/cost/summary': CostSummaryPage,
  '/cost/control': CostControlPage,
  '/production-measurement': ProductionMeasurementPage,
  '/project-schedule': SchedulePage,
  '/site/daily-log': DailyLogPage,
  '/quality-safety': QualitySafetyPage,
  '/technical-management': TechnicalManagementPage,
  '/project-closeout': ProjectCloseoutPage,
  '/supplier-sourcing': SupplierSourcingPage,
  '/inventory/purchase-request': PurchaseExecutionPage,
  '/purchase/order': PurchaseExecutionPage,
  '/purchase/receipt': PurchaseExecutionPage,
  '/inventory/warehouse': InventoryWorkspacePage,
  '/inventory/stock': InventoryWorkspacePage,
  '/inventory/material-requisition': RequisitionWorkspacePage,
  '/subcontract/task': SubcontractWorkspacePage,
  '/subcontract/measure': SubcontractWorkspacePage,
  '/settlement/list': SettlementWorkspacePage,
  '/payment/application': ReceivablesWorkspacePage,
  '/payment/expense': ReceivablesWorkspacePage,
  '/revenue': ReceivablesWorkspacePage,
  '/invoice': ReceivablesWorkspacePage,
  '/finance-operations': FinanceControlWorkspacePage,
  '/cash-journal': FinanceControlWorkspacePage,
  '/fund-accounts': FinanceControlWorkspacePage,
  '/cash-forecast': FinanceControlWorkspacePage,
  '/accounting-entry': FinanceControlWorkspacePage,
  '/financial-close': FinanceControlWorkspacePage,
  '/partner': PartnerPage,
  '/org': OrganizationPage,
  '/material/dictionary': MaterialDictionaryPage,
  '/cost/subject/taxonomy': CostSubjectPage,
  '/cost/subject/rules': CostSubjectPage,
  '/cost/subject/scope': CostSubjectPage,
  '/cost/subject/trace': CostSubjectPage,
  '/approval/process': WorkflowProcessPage,
  '/system/users': AccessControlPage,
  '/system/roles': AccessControlPage,
  '/system/permissions': AccessControlPage,
  '/system/dict': DictionaryPage,
  '/system/audit': AuditPage,
  '/system/document-templates': DocumentTemplatePage,
  '/system/data': DataMaintenancePage,
} as const

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

const contextRoutes: RouteRecordRaw[] = [
  {
    path: '/supplier-sourcing',
    name: 'LegacySupplierSourcing',
    component: SupplierSourcingPage,
    meta: { shell: true, permission: 'supplier:sourcing:query' },
  },
  {
    path: '/bid-cost',
    name: 'LegacyBidCostRedirect',
    redirect: (to) => ({ path: '/engineering-tender/records', query: to.query, hash: to.hash }),
    meta: { shell: true, permission: 'bid:query' },
  },
  {
    path: '/engineering-tender/records/:id',
    name: 'EngineeringTenderDetail',
    component: BidTenderDetailPage,
    meta: { shell: true, permission: 'bid:query' },
  },
  {
    path: '/system',
    name: 'V2SystemRedirect',
    redirect: (to) => ({ path: '/system/dict', query: to.query, hash: to.hash }),
    meta: { shell: true, permission: 'system:dict:list', adminOnly: true },
  },
  {
    path: '/profile',
    name: 'V2Profile',
    component: AccountPage,
    meta: { shell: true },
  },
  {
    path: '/settings',
    name: 'V2Settings',
    component: AccountPage,
    meta: { shell: true },
  },
  {
    path: '/help',
    name: 'V2Help',
    component: AccountPage,
    meta: { shell: true },
  },
  {
    path: '/material',
    name: 'V2MaterialRedirect',
    redirect: (to) => ({ path: '/material/dictionary', query: to.query, hash: to.hash }),
    meta: { shell: true, permission: 'material:dict:list' },
  },
  {
    path: '/payment',
    name: 'V2PaymentRedirect',
    redirect: (to) => ({ path: '/payment/application', query: to.query, hash: to.hash }),
    meta: { shell: true, permission: 'payment:app:query' },
  },
  {
    path: '/inventory',
    name: 'V2InventoryRedirect',
    redirect: (to) => ({ path: '/inventory/warehouse', query: to.query, hash: to.hash }),
    meta: { shell: true, permission: 'inventory:warehouse:list' },
  },
  {
    path: '/inventory/transaction',
    name: 'V2InventoryTransactionRedirect',
    redirect: (to) => ({ path: '/inventory/stock', query: to.query, hash: '#transactions' }),
    meta: { shell: true, permission: 'inventory:transaction:list' },
  },
  {
    path: '/purchase',
    name: 'V2PurchaseRedirect',
    redirect: (to) => ({ path: '/purchase/order', query: to.query, hash: to.hash }),
    meta: { shell: true, permission: 'purchase:order:query' },
  },
  {
    path: '/subcontract',
    name: 'V2SubcontractRedirect',
    redirect: (to) => ({ path: '/subcontract/task', query: to.query, hash: to.hash }),
    meta: { shell: true, permission: 'subtask:query' },
  },
  {
    path: '/settlement',
    name: 'V2SettlementRedirect',
    redirect: (to) => ({ path: '/settlement/list', query: to.query, hash: to.hash }),
    meta: { shell: true, permission: 'settlement:query' },
  },
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
    path: '/partner/:id',
    name: 'V2ShellPartnerDetail',
    component: PartnerDetailPage,
    meta: { shell: true, permission: 'partner:query' },
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
    path: '/cost/subject',
    name: 'V2CostSubjectRootRedirect',
    redirect: (to) => ({
      path: '/cost/subject/taxonomy',
      query: to.query,
      hash: to.hash,
    }),
    meta: { shell: true, permission: 'cost:query' },
  },
  {
    path: '/cost-target',
    name: 'V2CostTargetRootRedirect',
    redirect: (to) => ({
      path: '/cost-budget',
      query: to.query,
      hash: to.hash,
    }),
    meta: { shell: true, permission: 'cost:target:query' },
  },
  {
    path: '/cost-target/index',
    name: 'V2CostTargetIndexRedirect',
    redirect: (to) => ({
      path: '/cost-budget',
      query: to.query,
      hash: to.hash,
    }),
    meta: { shell: true, permission: 'cost:target:query' },
  },
  {
    path: '/budget',
    name: 'V2BudgetRedirect',
    redirect: (to) => ({
      path: '/cost-budget',
      query: to.query,
      hash: to.hash,
    }),
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
    component: SettlementWorkspacePage,
    meta: { shell: true, permission: 'settlement:query' },
  },
]

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
