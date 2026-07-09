/** Dashboard - Task item (pending task, approval) */
export interface DashboardTaskItemVO {
  taskId: string
  instanceId: string
  businessType: string
  businessId: string
  title: string
  taskStatus: string
  receivedAt: string
  projectId: string
  projectName: string
  itemSummary?: string
  ownerName?: string
  amount?: string
  pendingDays?: number
}

/** Dashboard - Project summary card */
export interface DashboardProjectSummaryVO {
  projectId: string
  projectName: string
  projectCode: string
  status: string
  targetCost: string
  dynamicCost: string
  contractIncome: string
  expectedProfit: string
  costDeviation: string
  paidAmount: string
  contractAmount: string
  pendingTaskCount: number
  riskCount: number
}

/** Dashboard - Contract item */
export interface DashboardContractItemVO {
  contractId: string
  contractCode: string
  contractName: string
  contractType: string
  contractAmount: string
  currentAmount: string
  paidAmount: string
  endDate: string
  projectId: string
  projectName: string
  contractStatus: string
}

/** Dashboard - Project Manager View */
export interface ProjectManagerDashboardVO {
  projectId: string
  projectName: string
  /** KPI cards */
  pendingTaskCount: number
  laggingProjectCount: number
  pendingApprovalCount: number
  expiringContractCount: number
  /** Detail lists */
  pendingTasks: DashboardTaskItemVO[]
  laggingProjects: DashboardProjectSummaryVO[]
  pendingApprovals: DashboardTaskItemVO[]
  expiringContracts: DashboardContractItemVO[]
}

/** Dashboard - Business Manager View */
export interface BusinessManagerDashboardVO {
  projectId: string
  projectName: string
  /** KPI cards */
  totalContractAmount: string
  contractChangeAmount: string
  varOrderAmount: string
  subMeasureAmount: string
  paidRatio: string
  settlementProgress: string
  /** Detail lists */
  recentChanges: DashboardContractItemVO[]
  settlementItems: DashboardProjectSummaryVO[]
}

/** Time range filter values */
export type DashboardTimeRange = 'month' | 'quarter' | 'year'

/** Dashboard - Payment item */
export interface DashboardPaymentItemVO {
  payRecordId: string
  contractId: string
  contractName: string
  partnerName: string
  payAmount: string
  payDate: string
  payStatus: string
  projectId: string
  projectName: string
}

/** Dashboard - Alert item */
export interface DashboardAlertItemVO {
  alertType: string
  severity: string
  message: string
  projectId: string
  projectName: string
  triggeredAt: string
}

export interface DashboardBusinessItemVO {
  sourceType: string
  sourceId: string
  code: string
  title: string
  itemSummary?: string
  status: string
  amount: string
  date: string
  projectId: string
  projectName: string
  partnerName?: string
  ownerName?: string
  overdueDays?: number
  pendingDays?: number
}

/** ── Purchase Manager View ── */
export interface PurchaseManagerDashboardVO {
  projectId: string
  projectName: string
  pendingRequestCount: number
  activeOrderCount: number
  overdueDeliveryCount: number
  pendingReceiptCount: number
  lowStockItemCount: number
  totalOrderAmount: string
  recentRequests: DashboardBusinessItemVO[]
  purchaseOrders: DashboardBusinessItemVO[]
  overdueOrders: DashboardBusinessItemVO[]
  pendingReceipts: DashboardBusinessItemVO[]
}

/** ── Production Manager View (MVP) ── */
export interface ProductionManagerDashboardVO {
  projectId: string
  projectName: string
  receiptCount: number
  requisitionCount: number
  pendingStockOutCount: number
  subMeasureCount: number
  lowStockItemCount: number
  confirmedMeasureAmount: string
  recentReceipts: DashboardBusinessItemVO[]
  recentRequisitions: DashboardBusinessItemVO[]
  recentSubMeasures: DashboardBusinessItemVO[]
}

/** ── Chief Engineer View ── */
export interface ChiefEngineerDashboardVO {
  projectId: string
  projectName: string
  pendingReviewCount: number
  pendingCoordinationCount: number
  openIssueCount: number
  overdueCount: number
  pendingReviews: DashboardBusinessItemVO[]
  pendingCoordinations: DashboardBusinessItemVO[]
  openIssues: DashboardBusinessItemVO[]
  overdueItems: DashboardBusinessItemVO[]
}

/** ── Cost Manager View ── */
export interface CostManagerDashboardVO {
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
  trendPoints: CostManagerTrendPoint[]
  subjectRankings: CostManagerSubjectRanking[]
  overBudgetAlerts: DashboardAlertItemVO[]
  overdueItems: CostManagerOverdueItem[]
  pendingPayments: CostManagerPendingPayment[]
  ledgerRows: CostManagerLedgerRow[]
  ledgerTotal: number
}

export interface CostManagerTrendPoint {
  month: string
  targetCost: string
  dynamicCost: string
  costDeviation: string
}

export interface CostManagerSubjectRanking {
  costSubjectId: string
  costSubjectName: string
  targetCost: string
  actualCost: string
  dynamicCost: string
  costDeviation: string
  ratio: string
}

export interface CostManagerOverdueItem {
  taskId: string
  instanceId: string
  businessType: string
  businessId: string
  title: string
  overdueDays: number
  ownerName: string
  plannedAt: string
  projectId: string
  projectName: string
}

export interface CostManagerPendingPayment {
  payRecordId: string
  contractId: string
  contractName: string
  partnerName: string
  payAmount: string
  payDate: string
  payStatus: string
  projectId: string
  projectName: string
}

export interface CostManagerLedgerRow {
  rowType?: 'cost' | 'contract' | 'fund'
  sourceType?: 'CONTRACT' | 'PAY_RECORD' | 'COST_SUBJECT'
  sourceId?: string
  costSubjectId: string
  costSubjectName: string
  contractCode: string
  contractName: string
  budgetAmount: string
  actualAmount: string
  completionRatio: string
  deviationAmount: string
  deviationRatio: string
  status: string
  ownerName: string
}

/** ── Finance View ── */
export interface FinanceDashboardVO {
  projectId: string
  projectName: string
  pendingPaymentAmount: string
  pendingPaymentCount: number
  approvedUnpaidAmount: string
  overRatioAmount: string
  warrantyExpiringAmount: string
  pendingPayments: DashboardPaymentItemVO[]
  overRatioPayments: DashboardPaymentItemVO[]
}

/** ── Management View (tenant-wide) ── */
export interface ManagementDashboardVO {
  activeProjectCount: number
  totalContractAmount: string
  totalDynamicCost: string
  totalExpectedProfit: string
  totalPaidAmount: string
  totalPendingTaskCount: number
  totalRiskCount: number
  projectRankings: DashboardProjectSummaryVO[]
  metricSources: ManagementMetricSourceVO[]
  majorRisks: DashboardAlertItemVO[]
  overdueItems: DashboardTaskItemVO[]
}

export interface ManagementMetricSourceVO {
  projectId: string
  projectName: string
  sourceType: 'PROJECT_SUMMARY'
  sourceId: string
  contractAmount: string
  dynamicCost: string
  expectedProfit: string
  paidAmount: string
}

/** ── Cost Breakdown Drill-down (max 2 level) ── */
export interface CostBreakdownVO {
  projectId: string
  projectName: string
  targetCost: string
  dynamicCost: string
  expectedProfit: string
  subjectBreakdowns: SubjectBreakdown[]
}

export interface SubjectBreakdown {
  costSubjectId: string
  costSubjectName: string
  level: number
  parentSubjectId: string
  targetCost: string
  contractLockedCost: string
  actualCost: string
  dynamicCost: string
  costDeviation: string
}

/** Dashboard role tabs */
export type DashboardRole =
  | 'pm'
  | 'bm'
  | 'cost'
  | 'purchase'
  | 'production'
  | 'chiefEngineer'
  | 'finance'
  | 'mgmt'

/** Time range options for display */
export const TIME_RANGE_OPTIONS: { value: DashboardTimeRange; label: string }[] = [
  { value: 'month', label: '本月' },
  { value: 'quarter', label: '本季度' },
  { value: 'year', label: '本年' },
]

/** Contract type display map */
export const CONTRACT_TYPE_MAP: Record<string, string> = {
  MAIN: '总包合同',
  SUB: '分包合同',
  PURCHASE: '采购合同',
  LEASE: '租赁合同',
  SERVICE: '服务合同',
}

/** Contract type colors for pie chart */
export const CONTRACT_TYPE_COLORS: Record<string, string> = {
  MAIN: '#3b82f6',
  SUB: '#22c55e',
  PURCHASE: '#f59e0b',
  LEASE: '#8b5cf6',
  SERVICE: '#14b8c7',
}
