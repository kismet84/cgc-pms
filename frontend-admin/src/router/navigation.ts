export interface NavigationAccess {
  adminOnly?: boolean
  permission?: string
}

export interface WorkspaceTab extends NavigationAccess {
  key: string
  label: string
}

export interface NavigationWorkspace extends NavigationAccess {
  key: string
  label: string
  defaultPath: string
  matchPrefixes?: string[]
  tabs: WorkspaceTab[]
}

export interface NavigationItem {
  key: string
  label: string
  icon: string
  children: NavigationWorkspace[]
}

export const navigationItems: NavigationItem[] = [
  {
    key: '/workbench',
    label: '工作台',
    icon: 'HomeOutlined',
    children: [
      {
        key: '/workbench/cockpit',
        label: '经营驾驶舱',
        defaultPath: '/dashboard',
        tabs: [{ key: '/dashboard', label: '驾驶舱', permission: 'dashboard:view' }],
      },
      {
        key: '/workbench/my-work',
        label: '我的工作',
        defaultPath: '/approval/todo',
        matchPrefixes: ['/approval'],
        tabs: [
          { key: '/approval/todo', label: '待我处理', permission: 'workflow:task:query' },
          { key: '/approval/done', label: '我已处理', permission: 'workflow:task:query' },
          { key: '/approval/cc', label: '抄送我的', permission: 'workflow:cc:query' },
          { key: '/approval/mine', label: '我发起', permission: 'workflow:instance:query' },
        ],
      },
      {
        key: '/workbench/alerts',
        label: '预警中心',
        defaultPath: '/alert',
        tabs: [{ key: '/alert', label: '预警中心', permission: 'alert:view' }],
      },
      {
        key: '/workbench/reports',
        label: '报表中心',
        defaultPath: '/dashboard/reports',
        tabs: [{ key: '/dashboard/reports', label: '报表目录' }],
      },
    ],
  },
  {
    key: '/delivery',
    label: '项目履约',
    icon: 'ProjectOutlined',
    children: [
      {
        key: '/delivery/projects',
        label: '项目管理',
        defaultPath: '/project/list',
        matchPrefixes: ['/project'],
        tabs: [{ key: '/project/list', label: '项目列表', permission: 'project:query' }],
      },
      {
        key: '/delivery/execution',
        label: '计划与现场',
        defaultPath: '/project-schedule',
        tabs: [
          { key: '/project-schedule', label: '项目计划', permission: 'schedule:query' },
          { key: '/site/daily-log', label: '现场日报', permission: 'site:daily:query' },
        ],
      },
      {
        key: '/delivery/control',
        label: '质量与技术',
        defaultPath: '/quality-safety',
        tabs: [
          { key: '/quality-safety', label: '质量安全整改', permission: 'quality:safety:query' },
          {
            key: '/technical-management',
            label: '图纸 RFI 技术闭环',
            permission: 'technical:query',
          },
        ],
      },
      {
        key: '/delivery/closeout',
        label: '项目收尾',
        defaultPath: '/project-closeout',
        tabs: [{ key: '/project-closeout', label: '竣工收尾', permission: 'closeout:query' }],
      },
    ],
  },
  {
    key: '/commercial',
    label: '商务合约',
    icon: 'AuditOutlined',
    children: [
      {
        key: '/commercial/contracts',
        label: '合同与变更',
        defaultPath: '/contract/ledger',
        matchPrefixes: ['/contract', '/variation'],
        tabs: [
          { key: '/contract/ledger', label: '合同台账', permission: 'contract:query' },
          { key: '/variation/order', label: '签证变更', permission: 'variation:order:query' },
        ],
      },
      {
        key: '/commercial/target-cost',
        label: '投标与成本目标',
        defaultPath: '/bid-cost',
        matchPrefixes: ['/cost-target'],
        tabs: [
          { key: '/bid-cost', label: '投标成本', permission: 'bid:query' },
          { key: '/cost-target/index', label: '成本目标', permission: 'cost:target:query' },
        ],
      },
      {
        key: '/commercial/cost-control',
        label: '成本核算与控制',
        defaultPath: '/cost/ledger',
        matchPrefixes: ['/cost'],
        tabs: [
          { key: '/cost/ledger', label: '成本台账', permission: 'cost:ledger:query' },
          { key: '/cost/summary', label: '成本核对', permission: 'cost:summary:view' },
          { key: '/cost/control', label: '动态利润控制', permission: 'cost:control:query' },
        ],
      },
      {
        key: '/commercial/value',
        label: '预算与产值',
        defaultPath: '/budget',
        tabs: [
          { key: '/budget', label: '项目预算', permission: 'budget:query' },
          { key: '/production-measurement', label: '产值计量', permission: 'measurement:query' },
        ],
      },
    ],
  },
  {
    key: '/supply',
    label: '供应链与物资',
    icon: 'ShoppingCartOutlined',
    children: [
      {
        key: '/supply/suppliers',
        label: '供应商管理',
        defaultPath: '/supplier-sourcing',
        tabs: [
          {
            key: '/supplier-sourcing',
            label: '供应商招采履约',
            permission: 'supplier:sourcing:query',
          },
        ],
      },
      {
        key: '/supply/procurement',
        label: '采购执行',
        defaultPath: '/inventory/purchase-request',
        tabs: [
          {
            key: '/inventory/purchase-request',
            label: '采购申请',
            permission: 'purchase:request:list',
          },
          { key: '/purchase/order', label: '采购订单', permission: 'purchase:order:query' },
          { key: '/purchase/receipt', label: '材料验收', permission: 'receipt:query' },
        ],
      },
      {
        key: '/supply/inventory',
        label: '仓储库存',
        defaultPath: '/inventory/warehouse',
        tabs: [
          {
            key: '/inventory/warehouse',
            label: '仓库管理',
            permission: 'inventory:warehouse:query',
          },
          { key: '/inventory/stock', label: '库存台账', permission: 'inventory:stock:list' },
          {
            key: '/inventory/transaction',
            label: '出入库',
            permission: 'inventory:transaction:list',
          },
        ],
      },
      {
        key: '/supply/requisition',
        label: '现场领用',
        defaultPath: '/inventory/material-requisition',
        tabs: [
          {
            key: '/inventory/material-requisition',
            label: '领料申请',
            permission: 'requisition:query',
          },
        ],
      },
    ],
  },
  {
    key: '/subcontract-settlement',
    label: '分包与结算',
    icon: 'BranchesOutlined',
    children: [
      {
        key: '/subcontract-settlement/performance',
        label: '分包履约',
        defaultPath: '/subcontract/task',
        tabs: [
          { key: '/subcontract/task', label: '分包任务', permission: 'subcontract:task:query' },
          {
            key: '/subcontract/measure',
            label: '分包计量',
            permission: 'subcontract:measure:query',
          },
        ],
      },
      {
        key: '/subcontract-settlement/settlements',
        label: '结算管理',
        defaultPath: '/settlement/list',
        matchPrefixes: ['/settlement'],
        tabs: [{ key: '/settlement/list', label: '结算台账', permission: 'settlement:query' }],
      },
    ],
  },
  {
    key: '/finance',
    label: '资金财务',
    icon: 'AccountBookOutlined',
    children: [
      {
        key: '/finance/receivables-payables',
        label: '收付款与发票',
        defaultPath: '/payment/application',
        tabs: [
          { key: '/payment/application', label: '付款申请', permission: 'payment:app:query' },
          { key: '/payment/expense', label: '费用申请', permission: 'expense:query' },
          { key: '/revenue', label: '收入与回款', permission: 'revenue:operations:query' },
          { key: '/invoice', label: '发票管理', permission: 'invoice:query' },
        ],
      },
      {
        key: '/finance/cash',
        label: '资金运营',
        defaultPath: '/finance-operations',
        tabs: [
          {
            key: '/finance-operations',
            label: '资金运营',
            permission: 'finance:operations:query',
          },
          { key: '/cash-journal', label: '资金日记账', permission: 'cashbook:journal:query' },
          {
            key: '/cash-forecast',
            label: '项目资金预测',
            permission: 'finance:forecast:query',
          },
        ],
      },
      {
        key: '/finance/accounting',
        label: '财务核算',
        defaultPath: '/accounting-entry',
        tabs: [
          { key: '/accounting-entry', label: '会计凭证', permission: 'accounting:query' },
          {
            key: '/financial-close',
            label: '财务核算与月结',
            permission: 'finance:close:query',
          },
        ],
      },
    ],
  },
  {
    key: '/master-data',
    label: '基础资料',
    icon: 'FileTextOutlined',
    children: [
      {
        key: '/master-data/partners',
        label: '合作方管理',
        defaultPath: '/partner',
        tabs: [{ key: '/partner', label: '合作方管理', permission: 'partner:query' }],
      },
      {
        key: '/master-data/organization',
        label: '组织架构',
        defaultPath: '/org',
        tabs: [{ key: '/org', label: '组织架构', permission: 'org:query' }],
      },
      {
        key: '/master-data/materials',
        label: '物资主数据',
        defaultPath: '/material/dictionary',
        tabs: [{ key: '/material/dictionary', label: '材料字典', permission: 'material:query' }],
      },
      {
        key: '/master-data/finance',
        label: '财务主数据',
        defaultPath: '/cost/subject',
        tabs: [{ key: '/cost/subject', label: '成本科目', permission: 'cost:query' }],
      },
    ],
  },
  {
    key: '/system-management',
    label: '系统管理',
    icon: 'SettingOutlined',
    children: [
      {
        key: '/system-management/workflow',
        label: '流程配置',
        defaultPath: '/approval/process',
        adminOnly: true,
        tabs: [
          {
            key: '/approval/process',
            label: '审批流程',
            permission: 'workflow:process:query',
            adminOnly: true,
          },
        ],
      },
      {
        key: '/system-management/access-control',
        label: '访问控制',
        defaultPath: '/system/users',
        adminOnly: true,
        tabs: [
          {
            key: '/system/users',
            label: '用户管理',
            permission: 'system:user:query',
            adminOnly: true,
          },
          {
            key: '/system/roles',
            label: '角色管理',
            permission: 'system:role:query',
            adminOnly: true,
          },
          {
            key: '/system/permissions',
            label: '权限清单',
            permission: 'system:permission:query',
            adminOnly: true,
          },
        ],
      },
      {
        key: '/system-management/configuration',
        label: '系统配置',
        defaultPath: '/system/dict',
        adminOnly: true,
        tabs: [
          {
            key: '/system/dict',
            label: '字典管理',
            permission: 'system:dict:query',
            adminOnly: true,
          },
          {
            key: '/system/document-templates',
            label: '业务单据模板',
            permission: 'document:template:query',
            adminOnly: true,
          },
        ],
      },
      {
        key: '/system-management/audit',
        label: '操作审计',
        defaultPath: '/system/audit',
        tabs: [{ key: '/system/audit', label: '操作审计', permission: 'audit:query' }],
      },
      {
        key: '/system-management/data',
        label: '数据维护',
        defaultPath: '/system/data',
        adminOnly: true,
        tabs: [
          {
            key: '/system/data',
            label: '数据维护',
            permission: 'system:data:query',
            adminOnly: true,
          },
        ],
      },
    ],
  },
]

export interface WorkspaceMatch {
  domain: NavigationItem
  workspace: NavigationWorkspace
}

function matchesRoute(path: string, candidate: string) {
  return path === candidate || path.startsWith(`${candidate}/`)
}

export function findWorkspaceByPath(path: string | undefined): WorkspaceMatch | undefined {
  if (!path) return undefined
  for (const domain of navigationItems) {
    for (const workspace of domain.children) {
      if (workspace.tabs.some((tab) => path === tab.key)) {
        return { domain, workspace }
      }
    }
  }

  for (const domain of navigationItems) {
    for (const workspace of domain.children) {
      if (workspace.matchPrefixes?.some((prefix) => matchesRoute(path, prefix))) {
        return { domain, workspace }
      }
    }
  }

  return undefined
}
