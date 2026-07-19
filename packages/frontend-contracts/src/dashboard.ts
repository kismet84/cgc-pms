export type DashboardRole =
  | 'pm'
  | 'bm'
  | 'cost'
  | 'purchase'
  | 'production'
  | 'chiefEngineer'
  | 'finance'
  | 'mgmt'

export type DashboardTimeRange = 'month' | 'quarter' | 'year'

export const DASHBOARD_ROLE_CONTRACTS = {
  pm: {
    permission: 'dashboard:project-manager:view',
    endpoint: '/dashboard/project-manager',
  },
  bm: {
    permission: 'dashboard:business-manager:view',
    endpoint: '/dashboard/business-manager',
  },
  cost: {
    permission: 'dashboard:cost-manager:view',
    endpoint: '/dashboard/cost-manager',
  },
  purchase: {
    permission: 'dashboard:purchase-manager:view',
    endpoint: '/dashboard/purchase-manager',
  },
  production: {
    permission: 'dashboard:production-manager:view',
    endpoint: '/dashboard/production-manager',
  },
  chiefEngineer: {
    permission: 'dashboard:chief-engineer:view',
    endpoint: '/dashboard/chief-engineer',
  },
  finance: {
    permission: 'dashboard:finance:view',
    endpoint: '/dashboard/finance',
  },
  mgmt: {
    permission: 'dashboard:management:view',
    endpoint: '/dashboard/management',
  },
} as const satisfies Record<DashboardRole, { permission: string; endpoint: string }>

export interface DashboardAlertItem {
  alertType: string
  severity: string
  message: string
  projectId: string
  projectName: string
  triggeredAt: string
}

export interface CostManagerDashboard {
  projectId: string
  projectName: string
  targetCost: string
  dynamicCost: string
  costDeviation: string
  contractLockedCost: string
  actualCost: string
  estimatedRemainingCost: string
  expectedProfit: string
  contractIncome: string
  overBudgetAlerts: DashboardAlertItem[]
}
