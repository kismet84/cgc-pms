import type { PageResult } from "../api";
import type { DecimalString } from "./types";

export interface CostTargetRecord {
  id: string;
  projectId: string;
  versionNo: string;
  versionName: string;
  totalTargetAmount: DecimalString;
  totalBidCostAmount: DecimalString;
  totalResponsibilityAmount: DecimalString;
  sourceContractAmount?: DecimalString | null;
  targetCostRate?: DecimalString | null;
  isActive: number;
  approvalStatus: CostTargetApprovalStatus;
  status: CostTargetStatus;
  effectiveDate?: string | null;
  approvalInstanceId?: string | null;
  version?: string | number | null;
  remark?: string | null;
  createdBy?: string | null;
  createdTime?: string | null;
  updatedTime?: string | null;
}

export type CostTargetApprovalStatus =
  "DRAFT" | "APPROVING" | "APPROVED" | "REJECTED";
export type CostTargetStatus = "DRAFT" | "ACTIVE" | "CANCELLED";

export interface CostTargetQuery {
  pageNo?: number;
  pageSize?: number;
  projectId?: string;
  versionNo?: string;
  approvalStatus?: string;
  isActive?: string | number;
}

export interface CostTargetItemRecord {
  id?: string | null;
  targetId?: string | null;
  projectId?: string | null;
  costSubjectId: string;
  targetAmount: DecimalString;
  bidCostAmount?: DecimalString | null;
  responsibilityAmount?: DecimalString | null;
  responsibleUserId?: string | null;
  responsibilityUnit?: string | null;
  sortOrder?: string | number | null;
  remark?: string | null;
}

export interface CostTargetDefaultAllocationItem extends CostTargetItemRecord {
  subjectCode: string;
  subjectName: string;
  subjectType: string;
  defaultTargetRatio: DecimalString;
}

export interface CostTargetDefaultAllocation {
  projectId: string;
  projectManagerId?: string | null;
  sourceMainContractId: string;
  sourceMainContractCode: string;
  sourceContractAmount: DecimalString;
  targetCostRate: DecimalString;
  totalTargetAmount: DecimalString;
  items: CostTargetDefaultAllocationItem[];
}

export interface CostTargetProjectManagerOption {
  id: string;
  username: string;
  realName?: string | null;
  status: string;
  eligible: boolean;
}

export interface CostTargetSaveCommand {
  id?: string | null;
  projectId: string;
  versionNo: string;
  versionName: string;
  totalTargetAmount: DecimalString;
  totalBidCostAmount?: DecimalString | null;
  totalResponsibilityAmount?: DecimalString | null;
  effectiveDate?: string | null;
  version?: string | number | null;
  remark?: string | null;
}

export interface CostBudgetDraftSaveCommand {
  projectId: string;
  projectManagerId: string;
  versionNo: string;
  versionName: string;
  effectiveDate?: string | null;
  version?: string | number | null;
  remark?: string | null;
  items: CostTargetItemRecord[];
}

export type CostTargetPage = PageResult<CostTargetRecord>;

export interface CostLedgerRecord {
  id: string;
  projectId: string;
  projectName?: string | null;
  contractId?: string | null;
  contractName?: string | null;
  partnerId?: string | null;
  partnerName?: string | null;
  costSubjectId?: string | null;
  costSubjectName?: string | null;
  amount: DecimalString;
  taxAmount: DecimalString;
  amountWithoutTax: DecimalString;
  costType: string;
  sourceType: string;
  sourceId?: string | null;
  sourceCode?: string | null;
  costDate?: string | null;
  costStatus: string;
  remark?: string | null;
}

export interface CostLedgerQuery {
  pageNo?: number;
  pageSize?: number;
  projectId?: string;
  costType?: string;
  sourceType?: string;
  costStatus?: string;
  startDate?: string;
  endDate?: string;
  keyword?: string;
}

export type CostLedgerPage = PageResult<CostLedgerRecord>;

export interface CostLedgerSummary {
  totalAmount: DecimalString;
  totalTaxAmount: DecimalString;
  bySourceType: Record<string, DecimalString>;
  byProject: Record<string, DecimalString>;
  byCostType: Record<string, DecimalString>;
}

export interface CostSummaryHistoryRecord {
  id: string;
  tenantId: string;
  projectId: string;
  projectName: string;
  summaryDate: string;
  costSubjectId: string;
  costSubjectName: string;
  targetCost: DecimalString;
  contractLockedCost: DecimalString;
  actualCost: DecimalString;
  paidAmount: DecimalString;
  estimatedRemainingCost: DecimalString;
  dynamicCost: DecimalString;
  contractIncome: DecimalString;
  confirmedRevenue: DecimalString;
  expectedProfit: DecimalString;
  costDeviation: DecimalString;
  responsibilityCost: DecimalString;
  forecastAtCompletionCost: DecimalString;
  forecastProfit: DecimalString;
  profitMargin: DecimalString;
}

export interface CostProjectSummary {
  projectId: string;
  projectName: string;
  costTargetId?: string | null;
  costForecastId?: string | null;
  targetCost: DecimalString;
  contractLockedCost: DecimalString;
  actualCost: DecimalString;
  paidAmount: DecimalString;
  estimatedRemainingCost: DecimalString;
  dynamicCost: DecimalString;
  contractIncome: DecimalString;
  confirmedRevenue: DecimalString;
  expectedProfit: DecimalString;
  costDeviation: DecimalString;
  responsibilityCost: DecimalString;
  forecastAtCompletionCost: DecimalString;
  forecastProfit: DecimalString;
  profitMargin: DecimalString;
  subjects: CostSummaryHistoryRecord[];
}

export interface AccessibleCostSummary {
  accessibleProjectCount: number;
  projects: CostProjectSummary[];
}

export interface AccessibleCostControlOverview {
  accessibleProjectCount: number;
  forecastProjectCount: number;
  noForecastProjectCount: number;
  contractIncome: DecimalString;
  dynamicCost: DecimalString;
  forecastAtCompletionCost: DecimalString;
  forecastProfit: DecimalString;
  profitMargin: DecimalString;
  projects: CostProjectSummary[];
}

export interface CostForecastItemCommand {
  costSubjectId: string;
  estimatedRemainingAmount: DecimalString;
  remark?: string | null;
}

export interface CostForecastCommand {
  projectId: string;
  forecastCode?: string;
  forecastName: string;
  forecastDate: string;
  items: CostForecastItemCommand[];
  remark?: string | null;
  version?: string | number | null;
}

export interface CostCorrectiveCommand {
  forecastId: string;
  actionCode?: string;
  actionTitle: string;
  rootCause: string;
  actionPlan: string;
  expectedSavingAmount: DecimalString;
  responsibleUserId: string;
  dueDate: string;
  remark?: string | null;
  version?: string | number | null;
}

export interface CostCorrectiveOwnerOption {
  userId: string;
  username: string;
  realName?: string | null;
}

export interface CostCorrectiveCloseCommand {
  actualSavingAmount: DecimalString;
  resultDescription: string;
  version: string | number;
}

export interface CostControlAmountRow extends Record<string, unknown> {
  bid_cost_amount?: DecimalString | null;
  target_amount?: DecimalString | null;
  responsibility_amount?: DecimalString | null;
  committed_amount?: DecimalString | null;
  actual_amount?: DecimalString | null;
  recommended_remaining_amount?: DecimalString | null;
  estimated_remaining_amount?: DecimalString | null;
  forecast_at_completion_amount?: DecimalString | null;
  contract_income_amount?: DecimalString | null;
  forecast_profit_amount?: DecimalString | null;
  cost_variance_amount?: DecimalString | null;
  expected_saving_amount?: DecimalString | null;
  actual_saving_amount?: DecimalString | null;
}

export interface CostControlOverview extends Record<string, unknown> {
  project: CostControlAmountRow;
  activeTarget: CostControlAmountRow;
  targetItems: CostControlAmountRow[];
  forecastInputItems: CostControlAmountRow[];
  latestForecast: CostControlAmountRow;
  forecastItems: CostControlAmountRow[];
  correctiveActions: CostControlAmountRow[];
  forecastHistory: CostControlAmountRow[];
  costSources: CostControlAmountRow[];
  summary: CostControlAmountRow | CostControlAmountRow[];
}

export interface ProjectBudgetRecord {
  id: string;
  projectId: string;
  sourceCostTargetId?: string | null;
  budgetCode: string;
  versionNo: string;
  budgetName: string;
  totalAmount: DecimalString;
  approvalStatus: string;
  status: string;
  active: boolean;
  effectiveAt?: string | null;
  version?: string | number | null;
  createdAt?: string | null;
  updatedAt?: string | null;
  remark?: string | null;
  lines?: BudgetLineRecord[];
}

export interface BudgetLineRecord {
  id?: string | null;
  costSubjectId: string;
  costSubjectName?: string | null;
  budgetAmount: DecimalString;
  reservedAmount?: DecimalString | null;
  consumedAmount?: DecimalString | null;
  availableAmount?: DecimalString | null;
  version?: string | number | null;
  remark?: string | null;
}

export interface BudgetAvailabilityRecord {
  budgetId: string;
  budgetLineId: string;
  projectId: string;
  costSubjectId: string;
  budgetAmount: DecimalString;
  reservedAmount: DecimalString;
  consumedAmount: DecimalString;
  availableAmount: DecimalString;
}

export interface BudgetQuery {
  pageNo?: number;
  pageSize?: number;
  projectId?: string;
  status?: string;
  startDate?: string;
  endDate?: string;
}

export interface BudgetSaveCommand {
  projectId: string;
  versionNo: string;
  budgetName: string;
  totalAmount: DecimalString;
  version?: string | number | null;
  remark?: string | null;
}

export type BudgetPage = PageResult<ProjectBudgetRecord>;
