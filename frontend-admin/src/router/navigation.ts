export interface NavigationItem {
  key: string
  label: string
  icon?: string
  adminOnly?: boolean
  matchPrefixes?: string[]
  children?: NavigationItem[]
}

export const navigationItems: NavigationItem[] = [
  {
    key: '/workbench',
    label: '工作台',
    icon: 'HomeOutlined',
    matchPrefixes: ['/dashboard', '/alert', '/project/list'],
    children: [
      { key: '/dashboard', label: '首页驾驶舱' },
      { key: '/alert', label: '预警中心' },
      { key: '/project/list', label: '项目列表' },
    ],
  },
  {
    key: '/contract-domain',
    label: '合同管理',
    icon: 'FileTextOutlined',
    matchPrefixes: ['/contract', '/variation'],
    children: [
      { key: '/contract/ledger', label: '合同台账' },
      { key: '/variation/order', label: '变更签证' },
    ],
  },
  {
    key: '/cost-domain',
    label: '成本管理',
    icon: 'DollarOutlined',
    matchPrefixes: ['/cost/ledger', '/cost/summary', '/cost-target'],
    children: [
      { key: '/cost/ledger', label: '成本台账' },
      { key: '/cost/summary', label: '成本明细' },
      { key: '/cost-target/index', label: '目标成本' },
    ],
  },
  {
    key: '/procurement-inventory',
    label: '采购与库存',
    icon: 'ShoppingCartOutlined',
    matchPrefixes: ['/purchase', '/inventory'],
    children: [
      { key: '/inventory/purchase-request', label: '采购申请' },
      { key: '/inventory/material-requisition', label: '领料申请' },
      { key: '/purchase/order', label: '采购订单' },
      { key: '/purchase/receipt', label: '材料验收' },
      { key: '/inventory/warehouse', label: '仓库管理' },
      { key: '/inventory/stock', label: '库存台账' },
      { key: '/inventory/transaction', label: '出入库管理' },
    ],
  },
  {
    key: '/subcontract-domain',
    label: '分包管理',
    icon: 'BranchesOutlined',
    matchPrefixes: ['/subcontract'],
    children: [
      { key: '/subcontract/task', label: '分包任务' },
      { key: '/subcontract/measure', label: '分包计量' },
    ],
  },
  {
    key: '/settlement-domain',
    label: '结算管理',
    icon: 'AccountBookOutlined',
    matchPrefixes: ['/settlement', '/payment', '/invoice'],
    children: [
      { key: '/settlement/list', label: '结算列表' },
      { key: '/payment/application', label: '付款申请' },
      { key: '/invoice', label: '发票管理' },
    ],
  },
  {
    key: '/approval-center',
    label: '审批中心',
    icon: 'AuditOutlined',
    matchPrefixes: ['/approval/todo', '/approval/done', '/approval/cc', '/approval/mine'],
    children: [
      { key: '/approval/todo', label: '我的待办' },
      { key: '/approval/done', label: '我的已办' },
      { key: '/approval/cc', label: '抄送我的' },
      { key: '/approval/mine', label: '我发起' },
    ],
  },
  {
    key: '/master-data',
    label: '数据中心',
    icon: 'ProjectOutlined',
    matchPrefixes: ['/partner', '/org', '/material', '/cost/subject', '/approval/process'],
    children: [
      { key: '/partner', label: '合作方管理' },
      { key: '/org', label: '组织架构' },
      { key: '/material/dictionary', label: '材料字典' },
      { key: '/cost/subject', label: '成本科目' },
      { key: '/approval/process', label: '审批流程', adminOnly: true },
    ],
  },
  {
    key: '/system-management',
    label: '系统管理',
    icon: 'SettingOutlined',
    adminOnly: true,
    matchPrefixes: ['/system'],
    children: [
      { key: '/system/users', label: '用户管理' },
      { key: '/system/roles', label: '角色管理' },
      { key: '/system/dict', label: '字典管理' },
      { key: '/system/data', label: '数据管理' },
    ],
  },
]
