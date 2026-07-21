export interface NavigationAccess {
  permission?: string
}

export interface WorkspaceTab extends NavigationAccess {
  path: string
  label: string
  workspaceContext?: {
    project: boolean
    period: boolean
  }
}

export interface NavigationWorkspace {
  id: string
  label: string
  defaultPath: string
  matchPrefixes?: string[]
  tabs: WorkspaceTab[]
}

export interface NavigationDomain {
  id: string
  label: string
  badge: string
  workspaces: NavigationWorkspace[]
}

export const navigationDomains: NavigationDomain[] = [
  {
    id: 'workbench',
    label: '工作台',
    badge: '台',
    workspaces: [
      {
        id: 'cockpit',
        label: '经营驾驶舱',
        defaultPath: '/dashboard',
        tabs: [
          {
            path: '/dashboard',
            label: '驾驶舱',
            permission: 'dashboard:view',
            workspaceContext: { project: true, period: true },
          },
        ],
      },
      {
        id: 'my-work',
        label: '我的工作',
        defaultPath: '/approval/todo',
        matchPrefixes: ['/approval'],
        tabs: [
          {
            path: '/approval/todo',
            label: '待我处理',
            workspaceContext: { project: false, period: true },
          },
          {
            path: '/approval/done',
            label: '我已处理',
            workspaceContext: { project: false, period: true },
          },
          {
            path: '/approval/cc',
            label: '抄送我的',
            workspaceContext: { project: false, period: true },
          },
          {
            path: '/approval/mine',
            label: '我发起',
            workspaceContext: { project: false, period: true },
          },
        ],
      },
      {
        id: 'reports',
        label: '报表中心',
        defaultPath: '/dashboard/reports',
        tabs: [{ path: '/dashboard/reports', label: '报表目录' }],
      },
    ],
  },
  {
    id: 'delivery',
    label: '项目履约',
    badge: '项',
    workspaces: [
      {
        id: 'projects',
        label: '项目管理',
        defaultPath: '/project/list',
        matchPrefixes: ['/project'],
        tabs: [
          {
            path: '/project/list',
            label: '项目列表',
            permission: 'project:query',
            workspaceContext: { project: true, period: false },
          },
        ],
      },
      {
        id: 'execution',
        label: '计划与现场',
        defaultPath: '/project-schedule',
        tabs: [
          {
            path: '/project-schedule',
            label: '项目计划',
            permission: 'schedule:query',
            workspaceContext: { project: true, period: false },
          },
          {
            path: '/site/daily-log',
            label: '现场日报',
            permission: 'site:daily:query',
            workspaceContext: { project: true, period: true },
          },
        ],
      },
      {
        id: 'control',
        label: '质量与技术',
        defaultPath: '/quality-safety',
        tabs: [
          { path: '/quality-safety', label: '质量安全整改', permission: 'quality:safety:query' },
          {
            path: '/technical-management',
            label: '图纸 RFI 技术闭环',
            permission: 'technical:query',
          },
        ],
      },
      {
        id: 'closeout',
        label: '项目收尾',
        defaultPath: '/project-closeout',
        tabs: [{ path: '/project-closeout', label: '竣工收尾', permission: 'closeout:query' }],
      },
    ],
  },
  {
    id: 'commercial',
    label: '商务合约',
    badge: '商',
    workspaces: [
      {
        id: 'contracts',
        label: '合同与变更',
        defaultPath: '/contract/ledger',
        matchPrefixes: ['/contract', '/variation'],
        tabs: [
          { path: '/contract/ledger', label: '合同台账', permission: 'contract:query' },
          { path: '/variation/order', label: '签证变更', permission: 'variation:order:query' },
        ],
      },
      {
        id: 'target-cost',
        label: '投标与成本目标',
        defaultPath: '/bid-cost',
        matchPrefixes: ['/cost-target'],
        tabs: [
          { path: '/bid-cost', label: '投标成本', permission: 'bid:query' },
          { path: '/cost-target/index', label: '成本目标', permission: 'cost:target:query' },
        ],
      },
      {
        id: 'cost-control',
        label: '成本核算与控制',
        defaultPath: '/cost/ledger',
        matchPrefixes: ['/cost'],
        tabs: [
          { path: '/cost/ledger', label: '成本台账', permission: 'cost:ledger:query' },
          { path: '/cost/summary', label: '成本核对', permission: 'cost:summary:view' },
          { path: '/cost/control', label: '动态利润控制', permission: 'cost:control:query' },
        ],
      },
      {
        id: 'value',
        label: '预算与产值',
        defaultPath: '/budget',
        tabs: [
          { path: '/budget', label: '项目预算', permission: 'budget:query' },
          { path: '/production-measurement', label: '产值计量', permission: 'measurement:query' },
        ],
      },
    ],
  },
  {
    id: 'supply',
    label: '供应链与物资',
    badge: '供',
    workspaces: [
      {
        id: 'suppliers',
        label: '供应商管理',
        defaultPath: '/supplier-sourcing',
        tabs: [
          {
            path: '/supplier-sourcing',
            label: '供应商招采履约',
            permission: 'supplier:sourcing:query',
          },
        ],
      },
      {
        id: 'procurement',
        label: '采购执行',
        defaultPath: '/inventory/purchase-request',
        tabs: [
          {
            path: '/inventory/purchase-request',
            label: '采购申请',
            permission: 'purchase:request:list',
          },
          { path: '/purchase/order', label: '采购订单', permission: 'purchase:order:query' },
          { path: '/purchase/receipt', label: '材料验收', permission: 'receipt:query' },
        ],
      },
      {
        id: 'inventory',
        label: '仓储库存',
        defaultPath: '/inventory/warehouse',
        tabs: [
          {
            path: '/inventory/warehouse',
            label: '仓库管理',
            permission: 'inventory:warehouse:query',
          },
          { path: '/inventory/stock', label: '库存台账', permission: 'inventory:stock:list' },
          {
            path: '/inventory/transaction',
            label: '出入库',
            permission: 'inventory:transaction:list',
          },
        ],
      },
      {
        id: 'requisition',
        label: '现场领用',
        defaultPath: '/inventory/material-requisition',
        tabs: [
          {
            path: '/inventory/material-requisition',
            label: '领料申请',
            permission: 'requisition:query',
          },
        ],
      },
    ],
  },
  {
    id: 'subcontract-settlement',
    label: '分包与结算',
    badge: '分',
    workspaces: [
      {
        id: 'performance',
        label: '分包履约',
        defaultPath: '/subcontract/task',
        tabs: [
          { path: '/subcontract/task', label: '分包任务', permission: 'subcontract:task:query' },
          {
            path: '/subcontract/measure',
            label: '分包计量',
            permission: 'subcontract:measure:query',
          },
        ],
      },
      {
        id: 'settlements',
        label: '结算管理',
        defaultPath: '/settlement/list',
        matchPrefixes: ['/settlement'],
        tabs: [{ path: '/settlement/list', label: '结算台账', permission: 'settlement:query' }],
      },
    ],
  },
  {
    id: 'finance',
    label: '资金财务',
    badge: '财',
    workspaces: [
      {
        id: 'receivables-payables',
        label: '收付款与发票',
        defaultPath: '/payment/application',
        tabs: [
          { path: '/payment/application', label: '付款申请', permission: 'payment:app:query' },
          { path: '/payment/expense', label: '费用申请', permission: 'expense:query' },
          { path: '/revenue', label: '收入与回款', permission: 'revenue:operations:query' },
          { path: '/invoice', label: '发票管理', permission: 'invoice:query' },
        ],
      },
      {
        id: 'cash',
        label: '资金运营',
        defaultPath: '/finance-operations',
        tabs: [
          {
            path: '/finance-operations',
            label: '资金运营',
            permission: 'finance:operations:query',
          },
          { path: '/cash-journal', label: '资金日记账', permission: 'cashbook:journal:query' },
          { path: '/cash-forecast', label: '项目资金预测', permission: 'finance:forecast:query' },
        ],
      },
      {
        id: 'accounting',
        label: '财务核算',
        defaultPath: '/accounting-entry',
        tabs: [
          { path: '/accounting-entry', label: '会计凭证', permission: 'accounting:query' },
          { path: '/financial-close', label: '财务核算与月结', permission: 'finance:close:query' },
        ],
      },
    ],
  },
  {
    id: 'master-data',
    label: '基础资料',
    badge: '基',
    workspaces: [
      {
        id: 'partners',
        label: '合作方管理',
        defaultPath: '/partner',
        tabs: [{ path: '/partner', label: '合作方管理', permission: 'partner:query' }],
      },
      {
        id: 'organization',
        label: '组织架构',
        defaultPath: '/org',
        tabs: [{ path: '/org', label: '组织架构', permission: 'org:query' }],
      },
      {
        id: 'materials',
        label: '物资主数据',
        defaultPath: '/material/dictionary',
        tabs: [{ path: '/material/dictionary', label: '材料字典', permission: 'material:query' }],
      },
      {
        id: 'finance-data',
        label: '成本科目中心',
        defaultPath: '/cost/subject/taxonomy',
        matchPrefixes: ['/cost/subject'],
        tabs: [
          { path: '/cost/subject/taxonomy', label: '科目体系', permission: 'cost:query' },
          { path: '/cost/subject/rules', label: '归集规则', permission: 'cost:subject:rule:query' },
          {
            path: '/cost/subject/scope',
            label: '项目适用与目标成本',
            permission: 'cost:subject:scope:query',
          },
          {
            path: '/cost/subject/trace',
            label: '影响与转入追踪',
            permission: 'cost:subject:audit:query',
          },
        ],
      },
    ],
  },
  {
    id: 'system-management',
    label: '系统管理',
    badge: '系',
    workspaces: [
      {
        id: 'workflow',
        label: '流程配置',
        defaultPath: '/approval/process',
        tabs: [
          { path: '/approval/process', label: '审批流程', permission: 'workflow:process:query' },
        ],
      },
      {
        id: 'access-control',
        label: '访问控制',
        defaultPath: '/system/users',
        tabs: [
          { path: '/system/users', label: '用户管理', permission: 'system:user:query' },
          { path: '/system/roles', label: '角色管理', permission: 'system:role:query' },
          { path: '/system/permissions', label: '权限清单', permission: 'system:permission:query' },
        ],
      },
      {
        id: 'configuration',
        label: '系统配置',
        defaultPath: '/system/dict',
        tabs: [
          { path: '/system/dict', label: '字典管理', permission: 'system:dict:query' },
          {
            path: '/system/document-templates',
            label: '业务单据模板',
            permission: 'document:template:query',
          },
        ],
      },
      {
        id: 'audit',
        label: '操作审计',
        defaultPath: '/system/audit',
        tabs: [{ path: '/system/audit', label: '操作审计', permission: 'audit:query' }],
      },
      {
        id: 'data',
        label: '数据维护',
        defaultPath: '/system/data',
        tabs: [{ path: '/system/data', label: '数据维护', permission: 'system:data:query' }],
      },
    ],
  },
]

export interface VisibleWorkspace extends NavigationWorkspace {
  tabs: WorkspaceTab[]
}

export interface VisibleDomain extends Omit<NavigationDomain, 'workspaces'> {
  workspaces: VisibleWorkspace[]
}

export function hasAccess(permissions: readonly string[], access: NavigationAccess): boolean {
  return !access.permission || permissions.includes('*') || permissions.includes(access.permission)
}

export function visibleNavigation(permissions: readonly string[]): VisibleDomain[] {
  return navigationDomains.flatMap((domain) => {
    const workspaces = domain.workspaces.flatMap((workspace) => {
      const tabs = workspace.tabs.filter((tab) => hasAccess(permissions, tab))
      return tabs.length ? [{ ...workspace, tabs }] : []
    })
    return workspaces.length ? [{ ...domain, workspaces }] : []
  })
}

export interface WorkspaceMatch {
  domain: NavigationDomain
  workspace: NavigationWorkspace
}

function prefixMatches(path: string, prefix: string): boolean {
  return path === prefix || path.startsWith(`${prefix}/`)
}

export function findWorkspace(path: string): WorkspaceMatch | undefined {
  for (const domain of navigationDomains) {
    for (const workspace of domain.workspaces) {
      if (workspace.tabs.some((tab) => tab.path === path)) return { domain, workspace }
    }
  }
  for (const domain of navigationDomains) {
    for (const workspace of domain.workspaces) {
      if (workspace.matchPrefixes?.some((prefix) => prefixMatches(path, prefix))) {
        return { domain, workspace }
      }
    }
  }
  return undefined
}

export function firstAccessiblePath(permissions: readonly string[]): string | undefined {
  return visibleNavigation(permissions)[0]?.workspaces[0]?.tabs[0]?.path
}

export function permissionForPath(path: string): string | undefined {
  for (const domain of navigationDomains) {
    for (const workspace of domain.workspaces) {
      const tab = workspace.tabs.find((candidate) => candidate.path === path)
      if (tab) return tab.permission
    }
  }
  return undefined
}
