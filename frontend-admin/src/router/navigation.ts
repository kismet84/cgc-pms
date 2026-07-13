export interface NavigationItem {
  key: string
  label: string
  icon?: string
  adminOnly?: boolean
  permission?: string
  matchPrefixes?: string[]
  children?: NavigationItem[]
}

export const navigationItems: NavigationItem[] = [
  {
    key: '/workbench',
    label: '工作台',
    icon: 'HomeOutlined',
    matchPrefixes: ['/dashboard', '/alert', '/approval/todo'],
    children: [
      { key: '/dashboard', label: '首页驾驶舱' },
      { key: '/alert', label: '预警中心' },
      { key: '/approval/todo', label: '我的待办' },
    ],
  },
  {
    key: '/project-operations',
    label: '项目经营',
    icon: 'ProjectOutlined',
    matchPrefixes: [
      '/project/list',
      '/contract',
      '/variation',
      '/cost/ledger',
      '/cost/summary',
      '/cost-target',
      '/site/daily-log',
    ],
    children: [
      { key: '/project/list', label: '项目列表' },
      { key: '/contract/ledger', label: '合同台账' },
      { key: '/variation/order', label: '签证变更' },
      { key: '/cost-target/index', label: '成本目标' },
      { key: '/cost/ledger', label: '成本台账' },
      { key: '/cost/summary', label: '成本核对' },
      { key: '/site/daily-log', label: '现场日报', permission: 'site:daily:query' },
    ],
  },
  {
    key: '/procurement-inventory',
    label: '采购库存',
    icon: 'ShoppingCartOutlined',
    matchPrefixes: ['/purchase', '/inventory'],
    children: [
      { key: '/inventory/purchase-request', label: '采购申请' },
      { key: '/purchase/order', label: '采购订单' },
      { key: '/purchase/receipt', label: '材料验收' },
      { key: '/inventory/warehouse', label: '仓库管理' },
      { key: '/inventory/stock', label: '库存台账' },
      { key: '/inventory/transaction', label: '出入库记录' },
      { key: '/inventory/material-requisition', label: '领料申请' },
    ],
  },
  {
    key: '/subcontract-domain',
    label: '分包计量',
    icon: 'BranchesOutlined',
    matchPrefixes: ['/subcontract'],
    children: [
      { key: '/subcontract/task', label: '分包任务' },
      { key: '/subcontract/measure', label: '分包计量' },
    ],
  },
  {
    key: '/settlement-domain',
    label: '结算收付',
    icon: 'AccountBookOutlined',
    matchPrefixes: ['/settlement', '/payment', '/cash-journal', '/invoice'],
    children: [
      { key: '/settlement/list', label: '结算台账' },
      { key: '/payment/application', label: '付款申请' },
      { key: '/cash-journal', label: '资金日记账', permission: 'cashbook:journal:query' },
      { key: '/invoice', label: '发票管理' },
    ],
  },
  {
    key: '/master-data',
    label: '基础资料',
    icon: 'FileTextOutlined',
    matchPrefixes: ['/partner', '/org', '/material', '/cost/subject'],
    children: [
      { key: '/partner', label: '合作方管理' },
      { key: '/org', label: '组织架构' },
      { key: '/material/dictionary', label: '材料字典' },
      { key: '/cost/subject', label: '成本科目' },
    ],
  },
  {
    key: '/workflow-system',
    label: '流程与系统',
    icon: 'SettingOutlined',
    matchPrefixes: [
      '/approval',
      '/approval/done',
      '/approval/cc',
      '/approval/mine',
      '/approval/process',
      '/system',
    ],
    children: [
      { key: '/approval/process', label: '审批流程', adminOnly: true },
      { key: '/system/users', label: '用户管理', adminOnly: true },
      { key: '/system/roles', label: '角色管理', adminOnly: true },
      { key: '/system/permissions', label: '权限清单', adminOnly: true },
      { key: '/system/dict', label: '字典管理', adminOnly: true },
      { key: '/system/data', label: '数据管理', adminOnly: true },
      { key: '/system/audit', label: '操作审计', permission: 'audit:query' },
    ],
  },
]
