export type DashboardRole =
  | "pm"
  | "bm"
  | "cost"
  | "purchase"
  | "production"
  | "chiefEngineer"
  | "finance"
  | "mgmt";

export type DashboardTimeRange = "month" | "quarter" | "year";

export const DASHBOARD_ROLES: DashboardRole[] = [
  "pm",
  "bm",
  "cost",
  "purchase",
  "production",
  "chiefEngineer",
  "finance",
  "mgmt",
];

export const DASHBOARD_ROLE_CONTRACTS = {
  pm: {
    permission: "dashboard:project-manager:view",
    endpoint: "/dashboard/project-manager",
  },
  bm: {
    permission: "dashboard:business-manager:view",
    endpoint: "/dashboard/business-manager",
  },
  cost: {
    permission: "dashboard:cost-manager:view",
    endpoint: "/dashboard/cost-manager",
  },
  purchase: {
    permission: "dashboard:purchase-manager:view",
    endpoint: "/dashboard/purchase-manager",
  },
  production: {
    permission: "dashboard:production-manager:view",
    endpoint: "/dashboard/production-manager",
  },
  chiefEngineer: {
    permission: "dashboard:chief-engineer:view",
    endpoint: "/dashboard/chief-engineer",
  },
  finance: {
    permission: "dashboard:finance:view",
    endpoint: "/dashboard/finance",
  },
  mgmt: {
    permission: "dashboard:management:view",
    endpoint: "/dashboard/management",
  },
} as const satisfies Record<
  DashboardRole,
  { permission: string; endpoint: string }
>;

export const DASHBOARD_COST_BREAKDOWN_CONTRACT = {
  permission: "dashboard:cost-breakdown:view",
  endpoint: (projectId: string) =>
    `/dashboard/project/${encodeURIComponent(projectId)}/cost-breakdown`,
} as const;

export function resolveDashboardRoles(
  roles: string[],
  permissions: string[],
): DashboardRole[] {
  if (
    roles.some((role) => role === "ADMIN" || role === "SUPER_ADMIN") ||
    permissions.includes("*")
  ) {
    return [...DASHBOARD_ROLES];
  }
  return DASHBOARD_ROLES.filter((role) =>
    permissions.includes(DASHBOARD_ROLE_CONTRACTS[role].permission),
  );
}

export function normalizeDashboardMonth(
  period: string | null | undefined,
): string | undefined {
  const value = period?.trim();
  return value && /^\d{4}-(0[1-9]|1[0-2])$/.test(value) ? value : undefined;
}

export function buildDashboardReportPeriods(
  now: Date = new Date(),
  count = 12,
) {
  return Array.from({ length: Math.max(0, count) }, (_, index) => {
    const month = new Date(now.getFullYear(), now.getMonth() - index, 1);
    const monthNumber = String(month.getMonth() + 1).padStart(2, "0");
    return {
      value: `${month.getFullYear()}-${monthNumber}`,
      label: `${month.getFullYear()}年${month.getMonth() + 1}月`,
    };
  });
}

export interface DashboardAlertItemVO {
  alertType: string;
  severity: string;
  message: string;
  projectId: string;
  projectName: string;
  triggeredAt: string;
}

export interface DashboardTaskItemVO {
  taskId: string;
  instanceId: string;
  businessType: string;
  businessId: string;
  title: string;
  itemSummary?: string;
  taskStatus: string;
  receivedAt: string;
  ownerName?: string;
  amount?: string;
  pendingDays?: number;
  projectId: string;
  projectName: string;
}

export interface DashboardProjectSummaryVO {
  projectId: string;
  projectName: string;
  projectCode: string;
  status: string;
  targetCost: string;
  dynamicCost: string;
  contractIncome: string;
  expectedProfit: string;
  costDeviation: string;
  paidAmount: string;
  contractAmount: string;
  pendingTaskCount: number;
  riskCount: number;
}

export interface DashboardContractItemVO {
  contractId: string;
  contractCode: string;
  contractName: string;
  contractType: string;
  contractAmount: string;
  currentAmount: string;
  paidAmount: string;
  endDate: string;
  projectId: string;
  projectName: string;
  contractStatus: string;
}

export interface DashboardPaymentItemVO {
  payRecordId: string;
  recordCode?: string;
  contractId: string;
  contractName: string;
  partnerName: string;
  payAmount: string;
  payDate: string;
  payStatus: string;
  projectId: string;
  projectName: string;
}

export interface DashboardBusinessItemVO {
  sourceType: string;
  sourceId: string;
  code: string;
  title: string;
  itemSummary?: string;
  status: string;
  amount: string;
  date: string;
  projectId: string;
  projectName: string;
  partnerName?: string;
  ownerName?: string;
  overdueDays?: number;
  pendingDays?: number;
}

export interface DashboardSupplierScoreVO {
  partnerId: string;
  partnerName: string;
  orderCount: number;
  overdueOrderCount: number;
  lateCompletedCount?: number;
  overdueIncompleteCount?: number;
  onTimeDeliveryRate: string;
  performanceScore: string;
}

export interface ProjectManagerDashboardVO {
  projectId: string;
  projectName: string;
  pendingTaskCount: number;
  laggingProjectCount: number;
  pendingApprovalCount: number;
  expiringContractCount: number;
  pendingTasks: DashboardTaskItemVO[];
  laggingProjects: DashboardProjectSummaryVO[];
  pendingApprovals: DashboardTaskItemVO[];
  expiringContracts: DashboardContractItemVO[];
}

export interface BusinessManagerDashboardVO {
  projectId: string;
  projectName: string;
  totalContractAmount: string;
  contractChangeAmount: string;
  varOrderAmount: string;
  subMeasureAmount: string;
  paidRatio: string;
  settlementProgress: string;
  recentChanges: DashboardContractItemVO[];
  settlementItems: DashboardProjectSummaryVO[];
}

export interface PurchaseManagerDashboardVO {
  projectId: string;
  projectName: string;
  pendingRequestCount: number;
  activeOrderCount: number;
  overdueDeliveryCount: number;
  pendingReceiptCount: number;
  lowStockItemCount: number;
  totalOrderAmount: string;
  recentRequests: DashboardBusinessItemVO[];
  purchaseOrders: DashboardBusinessItemVO[];
  overdueOrders: DashboardBusinessItemVO[];
  pendingReceipts: DashboardBusinessItemVO[];
  supplierScores: DashboardSupplierScoreVO[];
}

export interface ProductionManagerDashboardVO {
  projectId: string;
  projectName: string;
  receiptCount: number;
  requisitionCount: number;
  pendingStockOutCount: number;
  subMeasureCount: number;
  lowStockItemCount: number;
  confirmedMeasureAmount: string;
  recentReceipts: DashboardBusinessItemVO[];
  recentRequisitions: DashboardBusinessItemVO[];
  recentSubMeasures: DashboardBusinessItemVO[];
}

export interface ChiefEngineerDashboardVO {
  projectId: string;
  projectName: string;
  pendingReviewCount: number;
  pendingCoordinationCount: number;
  openIssueCount: number;
  overdueCount: number;
  pendingReviews: DashboardBusinessItemVO[];
  pendingCoordinations: DashboardBusinessItemVO[];
  openIssues: DashboardBusinessItemVO[];
  overdueItems: DashboardBusinessItemVO[];
}

export interface CostManagerDashboardVO {
  projectId: string;
  projectName: string;
  targetCost: string;
  dynamicCost: string;
  costDeviation: string;
  contractLockedCost: string;
  actualCost: string;
  estimatedRemainingCost: string;
  expectedProfit: string;
  contractIncome: string;
  trendPoints: CostManagerTrendPoint[];
  subjectRankings: CostManagerSubjectRanking[];
  overBudgetAlerts: DashboardAlertItemVO[];
  overdueItems: CostManagerOverdueItem[];
  pendingPayments: CostManagerPendingPayment[];
  ledgerRows: CostManagerLedgerRow[];
  ledgerTotal: number;
}

export interface CostManagerTrendPoint {
  month: string;
  targetCost: string;
  dynamicCost: string;
  costDeviation: string;
}

export interface CostManagerSubjectRanking {
  costSubjectId: string;
  costSubjectName: string;
  targetCost: string;
  actualCost: string;
  dynamicCost: string;
  costDeviation: string;
  ratio: string;
}

export interface CostManagerOverdueItem {
  taskId: string;
  instanceId: string;
  businessType: string;
  businessId: string;
  title: string;
  overdueDays: number;
  ownerName: string;
  plannedAt: string;
  projectId: string;
  projectName: string;
}

export type CostManagerPendingPayment = DashboardPaymentItemVO;

export interface CostManagerLedgerRow {
  rowType?: "cost" | "contract" | "fund";
  sourceType?: "CONTRACT" | "PAY_RECORD" | "COST_SUBJECT";
  sourceId?: string;
  costSubjectId: string;
  costSubjectName: string;
  contractCode: string;
  contractName: string;
  budgetAmount: string;
  actualAmount: string;
  completionRatio: string;
  deviationAmount: string;
  deviationRatio: string;
  status: string;
  ownerName: string;
}

export interface FinanceDashboardVO {
  projectId: string;
  projectName: string;
  pendingPaymentAmount: string;
  pendingPaymentCount: number;
  approvedUnpaidAmount: string;
  overRatioAmount: string;
  warrantyExpiringAmount: string;
  totalContractAmount: string;
  totalPaidAmount: string;
  budgetAmount: string;
  budgetConsumedAmount: string;
  budgetExecutionRate: string;
  cashOutflowAmount: string;
  cashBalance: string;
  projectProfit: string;
  metricFormulaVersion: string;
  trendPoints: FinanceDashboardTrendPoint[];
  pendingPayments: DashboardPaymentItemVO[];
  overRatioPayments: DashboardPaymentItemVO[];
  contractFundBreakdowns: FinanceContractFundBreakdown[];
}

export interface FinanceContractFundBreakdown {
  contractId: string;
  projectId: string;
  projectName: string;
  contractCode: string;
  contractName: string;
  contractAmount: string;
  paidAmount: string;
  approvingAmount: string;
  approvedUnpaidAmount: string;
  remainingAmount: string;
  paymentRatio: string;
  paymentRecords: DashboardPaymentItemVO[];
}

export interface FinanceDashboardTrendPoint {
  month: string;
  cashOutflowAmount: string;
  cumulativePaidAmount: string;
  pendingPaymentAmount: string;
}

export interface ManagementMetricSourceVO {
  projectId: string;
  projectName: string;
  sourceType: string;
  sourceId: string;
  contractAmount: string;
  dynamicCost: string;
  expectedProfit: string;
  paidAmount: string;
}

export interface ManagementDashboardVO {
  activeProjectCount: number;
  totalContractAmount: string;
  totalDynamicCost: string;
  totalExpectedProfit: string;
  totalPaidAmount: string;
  totalPendingTaskCount: number;
  totalRiskCount: number;
  projectRankings: DashboardProjectSummaryVO[];
  metricSources: ManagementMetricSourceVO[];
  majorRisks: DashboardAlertItemVO[];
  overdueItems: DashboardTaskItemVO[];
}

export interface SubjectBreakdown {
  costSubjectId: string;
  costSubjectName: string;
  level: number;
  parentSubjectId: string;
  targetCost: string;
  contractLockedCost: string;
  actualCost: string;
  dynamicCost: string;
  costDeviation: string;
}

export interface CostBreakdownVO {
  projectId: string;
  projectName: string;
  targetCost: string;
  dynamicCost: string;
  expectedProfit: string;
  subjectBreakdowns: SubjectBreakdown[];
}

export interface DashboardDataByRole {
  pm: ProjectManagerDashboardVO;
  bm: BusinessManagerDashboardVO;
  cost: CostManagerDashboardVO;
  purchase: PurchaseManagerDashboardVO;
  production: ProductionManagerDashboardVO;
  chiefEngineer: ChiefEngineerDashboardVO;
  finance: FinanceDashboardVO;
  mgmt: ManagementDashboardVO;
}

export type CostManagerDashboard = CostManagerDashboardVO;
