import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import BasicLayout from '@/layouts/BasicLayout.vue'
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
    component: BasicLayout,
    redirect: '/dashboard',
    children: [
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('@/pages/dashboard/index.vue'),
        meta: { title: '首页', icon: 'HomeOutlined' },
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
            meta: { title: '合同台账' },
          },
          {
            path: 'approval',
            name: 'ContractApproval',
            redirect: '/approval/todo',
            meta: { title: '合同审批' },
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
            meta: { title: '成本台账' },
          },
          {
            path: 'summary',
            name: 'CostSummary',
            component: () => import('@/pages/cost/summary.vue'),
            meta: { title: '动态成本汇总', icon: 'FundOutlined' },
          },
        ],
      },
      {
        path: 'cost-target',
        name: 'CostTarget',
        redirect: '/cost-target/index',
        meta: { title: '目标管理', icon: 'AimOutlined' },
        children: [
          {
            path: 'index',
            name: 'CostTargetList',
            component: () => import('@/pages/cost-target/index.vue'),
            meta: { title: '目标管理' },
          },
          {
            path: 'create',
            name: 'CostTargetCreate',
            component: () => import('@/pages/cost-target/edit.vue'),
            meta: { title: '新建目标成本', hidden: true },
          },
          {
            path: ':id/edit',
            name: 'CostTargetEdit',
            component: () => import('@/pages/cost-target/edit.vue'),
            meta: { title: '编辑目标成本', hidden: true },
          },
        ],
      },
      {
        path: 'variation',
        name: 'Variation',
        redirect: '/variation/order',
        meta: { title: '变更签证', icon: 'SwapOutlined' },
        children: [
          {
            path: 'order',
            name: 'VariationOrder',
            component: () => import('@/pages/variation/order.vue'),
            meta: { title: '变更签证' },
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
        ],
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
        meta: { title: '审批管理', icon: 'AuditOutlined' },
        children: [
          {
            path: 'todo',
            name: 'ApprovalTodo',
            component: () => import('@/pages/approval/todo.vue'),
            meta: { title: '我的待办' },
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
        meta: { title: '系统设置', icon: 'SettingOutlined' },
        children: [
          {
            path: 'dict',
            name: 'SystemDict',
            component: () => import('@/pages/system/dict/index.vue'),
            meta: { title: '字典管理' },
          },
        ],
      },
      {
        path: 'profile',
        name: 'Profile',
        component: () => import('@/pages/profile/index.vue'),
        meta: { title: '个人中心', icon: 'UserOutlined' },
      },
      {
        path: 'settings',
        name: 'Settings',
        component: () => import('@/pages/settings/index.vue'),
        meta: { title: '设置', icon: 'ControlOutlined' },
      },
      {
        path: 'help',
        name: 'Help',
        component: () => import('@/pages/help/index.vue'),
        meta: { title: '帮助', icon: 'QuestionCircleOutlined' },
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

router.beforeEach((to) => {
  const userStore = useUserStore()
  if (to.meta?.public || WHITE_LIST.includes(to.path)) {
    return true
  }
  if (!userStore.isLogin) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  return true
})

router.afterEach((to) => {
  const title = (to.meta?.title as string) || ''
  document.title = title ? `${title} - 建筑工程总包项目管理系统` : '建筑工程总包项目管理系统'
})

export default router
