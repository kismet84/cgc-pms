import {
  createRouter,
  createWebHistory,
  type RouteLocationNormalized,
  type RouteRecordRaw,
} from 'vue-router'
import { getUserInfo } from '@/api/modules/auth'
import { useUserStore } from '@/stores/user'

export const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/pages/login/index.vue'),
    meta: { title: '登录', hidden: true, public: true },
  },
  {
    path: '/',
    component: () => import('@/layouts/BasicLayoutAsync.vue'),
    redirect: '/dashboard',
    children: [
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('@/pages/dashboard/index.vue'),
        meta: { title: '首页', icon: 'HomeOutlined' },
      },
      {
        path: 'dashboard/reports',
        name: 'ReportCatalog',
        component: () => import('@/pages/report/catalog.vue'),
        meta: { title: '报表目录', hidden: true },
      },
      {
        path: 'contract',
        name: 'Contract',
        redirect: '/contract/ledger',
        meta: { title: '合同管理', icon: 'FileTextOutlined' },
        children: [
          {
            path: 'ledger',
            name: 'ContractLedger',
            component: () => import('@/pages/contract/ContractLedgerPage.vue'),
            meta: { title: '合同列表' },
          },
          {
            path: 'create',
            name: 'ContractCreate',
            component: () => import('@/pages/contract/ContractFormPage.vue'),
            meta: { title: '新建合同', icon: 'file-add' },
          },
          {
            path: ':id',
            name: 'ContractDetail',
            component: () => import('@/pages/contract/ContractDetailPage.vue'),
            meta: { title: '合同详情', hidden: true },
          },
          {
            path: ':id/edit',
            name: 'ContractEdit',
            component: () => import('@/pages/contract/ContractFormPage.vue'),
            meta: { title: '编辑合同', hidden: true },
          },
        ],
      },
      {
        path: 'cost',
        name: 'Cost',
        redirect: '/cost/ledger',
        meta: { title: '成本管理', icon: 'DollarOutlined' },
        children: [
          {
            path: 'ledger',
            name: 'CostLedger',
            component: () => import('@/pages/cost/ledger.vue'),
            meta: { title: '成本列表' },
          },
          {
            path: 'summary',
            name: 'CostSummary',
            component: () => import('@/pages/cost/summary.vue'),
            meta: { title: '项目成本明细核对', icon: 'FundOutlined' },
          },
          {
            path: 'subject',
            name: 'CostSubject',
            component: () => import('@/pages/cost-subject/index.vue'),
            meta: { title: '成本科目' },
          },
        ],
      },
      {
        path: 'cost-target',
        name: 'CostTarget',
        redirect: '/cost-target/index',
        meta: { title: '成本目标', icon: 'AimOutlined' },
        children: [
          {
            path: 'index',
            name: 'CostTargetList',
            component: () => import('@/pages/cost-target/index.vue'),
            meta: { title: '成本目标' },
          },
          {
            path: 'create',
            name: 'CostTargetCreate',
            component: () => import('@/pages/cost-target/edit.vue'),
            meta: { title: '新建成本目标', hidden: true },
          },
          {
            path: ':id/edit',
            name: 'CostTargetEdit',
            component: () => import('@/pages/cost-target/edit.vue'),
            meta: { title: '编辑成本目标', hidden: true },
          },
        ],
      },
      {
        path: 'variation',
        name: 'Variation',
        redirect: '/variation/order',
        meta: { title: '签证列表', icon: 'SwapOutlined' },
        children: [
          {
            path: 'order',
            name: 'VariationOrder',
            component: () => import('@/pages/variation/order.vue'),
            meta: { title: '签证列表' },
          },
        ],
      },
      {
        path: 'settlement',
        name: 'Settlement',
        redirect: '/settlement/list',
        meta: { title: '结算管理', icon: 'AccountBookOutlined' },
        children: [
          {
            path: 'list',
            name: 'SettlementList',
            component: () => import('@/pages/settlement/index.vue'),
            meta: { title: '结算列表' },
          },
          {
            path: ':id',
            name: 'SettlementDetail',
            component: () => import('@/pages/settlement/detail.vue'),
            meta: { title: '结算详情', hidden: true },
          },
        ],
      },
      {
        path: 'project',
        name: 'Project',
        redirect: '/project/list',
        meta: { title: '项目管理', icon: 'ProjectOutlined' },
        children: [
          {
            path: 'list',
            name: 'ProjectList',
            component: () => import('@/pages/project/index.vue'),
            meta: { title: '项目列表' },
          },
          {
            path: ':projectId/overview',
            name: 'ProjectOverview',
            component: () => import('@/pages/project/overview.vue'),
            meta: { title: '项目总览', hidden: true },
          },
          {
            path: ':projectId/members',
            name: 'ProjectMembers',
            component: () => import('@/pages/project/members.vue'),
            meta: { title: '项目成员', hidden: true },
          },
          {
            path: ':projectId/edit',
            name: 'ProjectEdit',
            component: () => import('@/pages/project/edit.vue'),
            meta: { title: '编辑项目', hidden: true },
          },
        ],
      },
      {
        path: 'partner',
        name: 'Partner',
        component: () => import('@/pages/partner/index.vue'),
        meta: { title: '合作方管理', icon: 'TeamOutlined' },
      },
      {
        path: 'org',
        name: 'Org',
        component: () => import('@/pages/org/index.vue'),
        meta: { title: '组织架构', icon: 'ApartmentOutlined' },
      },
      {
        path: 'subcontract',
        name: 'Subcontract',
        redirect: '/subcontract/task',
        meta: { title: '分包管理', icon: 'BranchesOutlined' },
        children: [
          {
            path: 'task',
            name: 'SubcontractTask',
            component: () => import('@/pages/subcontract/task.vue'),
            meta: { title: '分包任务' },
          },
          {
            path: 'measure',
            name: 'SubcontractMeasure',
            component: () => import('@/pages/subcontract/measure.vue'),
            meta: { title: '分包计量' },
          },
        ],
      },
      {
        path: 'purchase',
        name: 'Purchase',
        redirect: '/purchase/order',
        meta: { title: '采购管理', icon: 'ShoppingCartOutlined' },
        children: [
          {
            path: 'order',
            name: 'PurchaseOrder',
            component: () => import('@/pages/purchase/order.vue'),
            meta: { title: '采购订单' },
          },
          {
            path: 'receipt',
            name: 'PurchaseReceipt',
            component: () => import('@/pages/receipt/index.vue'),
            meta: { title: '材料验收' },
          },
        ],
      },
      {
        path: 'payment',
        name: 'Payment',
        redirect: '/payment/application',
        meta: { title: '付款管理', icon: 'DollarOutlined' },
        children: [
          {
            path: 'application',
            name: 'PaymentApplication',
            component: () => import('@/pages/payment/index.vue'),
            meta: { title: '付款申请' },
          },
        ],
      },
      {
        path: 'inventory',
        name: 'Inventory',
        redirect: '/inventory/warehouse',
        meta: { title: '库存管理', icon: 'InboxOutlined' },
        children: [
          {
            path: 'warehouse',
            name: 'InventoryWarehouse',
            component: () => import('@/pages/inventory/warehouse.vue'),
            meta: { title: '仓库管理' },
          },
          {
            path: 'stock',
            name: 'InventoryStock',
            component: () => import('@/pages/inventory/stock.vue'),
            meta: { title: '库存台账' },
          },
          {
            path: 'transaction',
            name: 'InventoryTransaction',
            component: () => import('@/pages/inventory/transaction.vue'),
            meta: { title: '出入库' },
          },
          {
            path: 'purchase-request',
            name: 'InventoryPurchaseRequest',
            component: () => import('@/pages/inventory/purchase-request.vue'),
            meta: { title: '采购申请' },
          },
          {
            path: 'material-requisition',
            name: 'InventoryMaterialRequisition',
            component: () => import('@/pages/requisition/index.vue'),
            meta: { title: '领料申请' },
          },
        ],
      },
      {
        path: 'invoice',
        name: 'Invoice',
        component: () => import('@/pages/invoice/index.vue'),
        meta: { title: '发票管理', icon: 'FileTextOutlined' },
      },
      {
        path: 'material',
        name: 'Material',
        redirect: '/material/dictionary',
        meta: { title: '基础数据', icon: 'DatabaseOutlined' },
        children: [
          {
            path: 'dictionary',
            name: 'MaterialDictionary',
            component: () => import('@/pages/material/dictionary.vue'),
            meta: { title: '材料字典' },
          },
        ],
      },
      {
        path: 'alert',
        name: 'Alert',
        component: () => import('@/pages/alert/index.vue'),
        meta: { title: '预警中心', icon: 'AlertOutlined' },
      },
      {
        path: 'approval',
        name: 'Approval',
        redirect: '/approval/todo',
        meta: { title: '审批中心', icon: 'AuditOutlined' },
        children: [
          {
            path: 'todo',
            name: 'ApprovalTodo',
            component: () => import('@/pages/approval/todo.vue'),
            meta: { title: '我的待办', approvalTab: 'todo' },
          },
          {
            path: 'done',
            name: 'ApprovalDone',
            component: () => import('@/pages/approval/todo.vue'),
            meta: { title: '我的已办', approvalTab: 'done' },
          },
          {
            path: 'cc',
            name: 'ApprovalCc',
            component: () => import('@/pages/approval/todo.vue'),
            meta: { title: '抄送我的', approvalTab: 'cc' },
          },
          {
            path: 'mine',
            name: 'ApprovalMine',
            component: () => import('@/pages/approval/todo.vue'),
            meta: { title: '我发起', approvalTab: 'mine' },
          },
          {
            path: 'process',
            name: 'ApprovalProcess',
            component: () => import('@/pages/approval/process.vue'),
            meta: { title: '审批流程', adminOnly: true },
          },
          {
            path: ':instanceId',
            name: 'ApprovalDetail',
            component: () => import('@/pages/approval/detail.vue'),
            meta: { title: '审批详情', hidden: true },
          },
        ],
      },
      {
        path: 'system',
        name: 'System',
        redirect: '/system/dict',
        meta: { title: '系统设置', icon: 'SettingOutlined', adminOnly: true },
        children: [
          {
            path: 'dict',
            name: 'SystemDict',
            component: () => import('@/pages/system/dict/index.vue'),
            meta: { title: '字典管理' },
          },
          {
            path: 'users',
            name: 'SystemUsers',
            component: () => import('@/pages/system/users/index.vue'),
            meta: { title: '用户管理' },
          },
          {
            path: 'data',
            name: 'SystemData',
            component: () => import('@/pages/system/data/index.vue'),
            meta: { title: '数据管理' },
          },
          {
            path: 'roles',
            name: 'RoleManagement',
            component: () => import('@/pages/system/roles/index.vue'),
            meta: { title: '角色管理' },
          },
          {
            path: 'permissions',
            name: 'SystemPermissions',
            component: () => import('@/pages/system/permissions/index.vue'),
            meta: { title: '权限清单' },
          },
        ],
      },
      {
        path: 'profile',
        name: 'Profile',
        component: () => import('@/pages/profile/index.vue'),
        meta: { title: '个人中心', icon: 'UserOutlined', hidden: true },
      },
      {
        path: 'settings',
        name: 'Settings',
        component: () => import('@/pages/settings/index.vue'),
        meta: { title: '设置', icon: 'ControlOutlined', hidden: true },
      },
      {
        path: 'help',
        name: 'Help',
        component: () => import('@/pages/help/index.vue'),
        meta: { title: '帮助', icon: 'QuestionCircleOutlined', hidden: true },
      },
    ],
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'NotFound',
    component: () => import('@/pages/error/404.vue'),
    meta: { title: '页面不存在', hidden: true, public: true },
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

const WHITE_LIST = ['/login']

const ROUTE_PERMISSION_MAP: Record<string, string> = {
  Dashboard: 'dashboard:view',
  Contract: 'contract:query',
  ContractLedger: 'contract:query',
  ContractCreate: 'contract:add',
  ContractDetail: 'contract:query',
  ContractEdit: 'contract:edit',
  Cost: 'cost:query',
  CostLedger: 'cost:ledger:query',
  CostSummary: 'cost:summary:view',
  CostSubject: 'cost:query',
  CostTarget: 'cost:target:query',
  CostTargetList: 'cost:target:query',
  CostTargetCreate: 'cost:target:add',
  CostTargetEdit: 'cost:target:edit',
  Variation: 'variation:order:query',
  VariationOrder: 'variation:order:query',
  Settlement: 'settlement:query',
  SettlementList: 'settlement:query',
  SettlementDetail: 'settlement:query',
  Project: 'project:query',
  ProjectList: 'project:query',
  ProjectOverview: 'project:query',
  ProjectMembers: 'project:member:query',
  ProjectEdit: 'project:edit',
  Partner: 'partner:query',
  Org: 'org:query',
  Subcontract: 'subcontract:task:query',
  SubcontractTask: 'subcontract:task:query',
  SubcontractMeasure: 'subcontract:measure:query',
  Purchase: 'purchase:order:query',
  PurchaseOrder: 'purchase:order:query',
  PurchaseReceipt: 'receipt:query',
  Payment: 'payment:app:query',
  PaymentApplication: 'payment:app:query',
  Inventory: 'inventory:warehouse:query',
  InventoryWarehouse: 'inventory:warehouse:query',
  InventoryStock: 'inventory:stock:query',
  InventoryTransaction: 'inventory:transaction:list',
  InventoryPurchaseRequest: 'purchase:request:query',
  InventoryMaterialRequisition: 'requisition:query',
  Invoice: 'invoice:query',
  Material: 'material:query',
  MaterialDictionary: 'material:query',
  Alert: 'alert:view',
  Approval: 'workflow:task:query',
  ApprovalTodo: 'workflow:task:query',
  ApprovalDone: 'workflow:task:query',
  ApprovalCc: 'workflow:cc:query',
  ApprovalMine: 'workflow:instance:query',
  ApprovalProcess: 'workflow:process:query',
  ApprovalDetail: 'workflow:instance:query',
  System: 'system:dict:query',
  SystemDict: 'system:dict:query',
  SystemUsers: 'system:user:query',
  SystemData: 'system:data:query',
  RoleManagement: 'system:role:query',
  SystemPermissions: 'system:permission:query',
  Profile: 'profile:query',
  Settings: 'settings:query',
  Help: 'help:query',
}

applyRoutePermissions(routes)

let pendingUserInfoRequest: Promise<boolean> | null = null

async function restoreUserSession() {
  const userStore = useUserStore()
  if (userStore.isLogin) {
    return true
  }
  if (!pendingUserInfoRequest) {
    pendingUserInfoRequest = getUserInfo()
      .then((userInfo) => {
        userStore.setUserInfo(userInfo)
        return true
      })
      .catch(() => false)
      .finally(() => {
        pendingUserInfoRequest = null
      })
  }
  return pendingUserInfoRequest
}

function hasDashboardRouteAccess(roles: string[], permissions: string[]) {
  return (
    isAdminRole(roles) ||
    permissions.includes('*') ||
    permissions.includes('dashboard:view') ||
    permissions.some(
      (permission) => permission.startsWith('dashboard:') && permission.endsWith(':view'),
    )
  )
}

export async function handleAuthGuard(to: RouteLocationNormalized) {
  const userStore = useUserStore()
  if (to.meta?.public || WHITE_LIST.includes(to.path)) {
    return true
  }
  if (!(await restoreUserSession())) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  if (to.meta?.adminOnly && !isAdminRole(userStore.roles)) {
    return { path: '/dashboard' }
  }
  if (
    to.meta?.permission === 'dashboard:view' &&
    !hasDashboardRouteAccess(userStore.roles, userStore.permissions)
  ) {
    return false
  }
  if (
    to.meta?.permission !== 'dashboard:view' &&
    to.meta?.permission &&
    !userStore.hasPermission(to.meta.permission)
  ) {
    return { path: '/dashboard' }
  }
  return true
}

router.beforeEach(handleAuthGuard)

function isAdminRole(roles: string[]) {
  return roles.includes('ADMIN') || roles.includes('SUPER_ADMIN')
}

function applyRoutePermissions(routeList: RouteRecordRaw[]) {
  for (const route of routeList) {
    if (typeof route.name === 'string' && !route.meta?.public) {
      const permission = ROUTE_PERMISSION_MAP[route.name]
      if (permission) {
        route.meta = { ...route.meta, permission }
      }
    }
    if (route.children) {
      applyRoutePermissions(route.children)
    }
  }
}

router.afterEach((to) => {
  const title = (to.meta?.title as string) || ''
  document.title = title ? `${title} - 建筑工程总包项目管理系统` : '建筑工程总包项目管理系统'
})

export default router
