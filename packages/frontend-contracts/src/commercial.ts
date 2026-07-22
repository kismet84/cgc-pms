import type { PageResult } from "./api";

export type DecimalString = string;
export type ContractType = "MAIN" | "SUB" | "PURCHASE" | "LEASE" | "SERVICE";
export type ContractStatus = "DRAFT" | "PERFORMING" | "SETTLED" | "TERMINATED";
export type ApprovalStatus =
  "DRAFT" | "APPROVING" | "APPROVED" | "REJECTED" | "WITHDRAWN";
export type BidStatus = "BIDDING" | "WON" | "LOST";

export interface ContractKpi {
  totalCount: string;
  totalAmount: DecimalString;
  paidAmount: DecimalString;
  unpaidAmount: DecimalString;
  overdueCount: string;
}

export interface ContractQuery {
  pageNo?: number;
  pageSize?: number;
  keyword?: string;
  contractCode?: string;
  contractName?: string;
  contractType?: ContractType;
  contractStatus?: ContractStatus;
  approvalStatus?: ApprovalStatus;
  projectId?: string;
  partyAId?: string;
  partyBId?: string;
  startDate?: string;
  endDate?: string;
}

export interface ContractRecord {
  id: string;
  tenantId: string;
  orgId: string;
  projectId: string;
  contractCode: string;
  contractName: string;
  contractType: ContractType;
  partyAId: string;
  partyAName: string;
  partyBId: string;
  partyBName: string;
  contractAmount: DecimalString;
  currentAmount: DecimalString;
  taxRate: DecimalString;
  taxAmount: DecimalString;
  amountWithoutTax: DecimalString;
  signedDate: string;
  startDate: string;
  endDate: string;
  paymentMethod: string;
  settlementMethod: string;
  paidAmount: DecimalString;
  settlementAmount: DecimalString;
  contractStatus: ContractStatus;
  approvalStatus: ApprovalStatus;
  projectName: string;
  createdBy: string;
  createdAt: string;
  updatedAt: string;
  costGeneratedFlag?: string | number | null;
  version?: string | number | null;
  remark?: string | null;
}

export interface ContractItemRecord {
  id?: string | null;
  tenantId?: string | null;
  contractId?: string | null;
  itemCode?: string | null;
  itemName: string;
  itemSpec?: string | null;
  unit?: string | null;
  quantity?: DecimalString | null;
  unitPrice?: DecimalString | null;
  amount?: DecimalString | null;
  taxRate?: DecimalString | null;
  taxAmount?: DecimalString | null;
  amountWithoutTax?: DecimalString | null;
  sortOrder?: string | number | null;
  remark?: string | null;
}

export interface ContractPaymentTermRecord {
  id?: string | null;
  tenantId?: string | null;
  contractId?: string | null;
  termName: string;
  paymentRatio?: DecimalString | null;
  paymentAmount?: DecimalString | null;
  paymentCondition?: string | null;
  plannedDate?: string | null;
  actualDate?: string | null;
  termStatus?: string | null;
  sortOrder?: string | number | null;
  remark?: string | null;
}

export interface ContractApprovalRecord {
  id: string;
  nodeName: string;
  operatorName: string;
  actionType: string;
  actionName: string;
  comment?: string | null;
  createdAt: string;
}

export interface ContractCompositeRecord {
  contract: ContractRecord;
  items: ContractItemRecord[];
  paymentTerms: ContractPaymentTermRecord[];
  approvalRecords: ContractApprovalRecord[];
}

export interface ContractSaveCommand {
  contract: {
    id?: string | null;
    projectId?: string | null;
    contractName: string;
    contractType: ContractType;
    partyAId?: string | null;
    partyBId?: string | null;
    contractAmount?: DecimalString | null;
    currentAmount?: DecimalString | null;
    paidAmount?: DecimalString | null;
    taxRate?: DecimalString | null;
    taxAmount?: DecimalString | null;
    amountWithoutTax?: DecimalString | null;
    signedDate?: string | null;
    startDate?: string | null;
    endDate?: string | null;
    paymentMethod?: string | null;
    settlementMethod?: string | null;
    settlementAmount?: DecimalString | null;
    version?: string | number | null;
    remark?: string | null;
  };
  items: ContractItemRecord[];
  paymentTerms: ContractPaymentTermRecord[];
}

export interface PartnerQuery {
  pageNo?: number;
  pageSize?: number;
  partnerCode?: string;
  partnerName?: string;
  partnerType?: string;
  status?: string;
}

export interface PartnerRecord {
  id: string;
  partnerCode: string;
  partnerName: string;
  partnerType?: string | null;
  status?: string | null;
}

export interface VariationRecord {
  id: string;
  tenantId: string;
  projectId: string;
  contractId?: string | null;
  partnerId?: string | null;
  varCode: string;
  varName: string;
  reportedAmount?: DecimalString | null;
  approvedAmount?: DecimalString | null;
  confirmedAmount?: DecimalString | null;
  estimatedCostAmount?: DecimalString | null;
  eventDate?: string | null;
  claimDeadline?: string | null;
  eventDescription?: string | null;
  causeCategory?: string | null;
  responsibleParty?: string | null;
  businessMatterKey?: string | null;
  varType?: string | null;
  direction?: string | null;
  approvalStatus?: string | null;
  ownerStatus?: string | null;
  ownerConfirmFlag?: number | null;
  internalApprovalInstanceId?: string | null;
  generatedContractChangeId?: string | null;
  impactDays?: number | null;
  costGeneratedFlag?: number | null;
  projectName?: string | null;
  contractName?: string | null;
  partnerName?: string | null;
  version?: string | number | null;
  createdAt?: string | null;
  updatedAt?: string | null;
  remark?: string | null;
  items?: VariationItemRecord[];
  ownerSubmissions?: VariationOwnerSubmissionRecord[];
}

export interface VariationItemRecord {
  id?: string | null;
  varOrderId?: string | null;
  itemName: string;
  unit?: string | null;
  quantity: DecimalString;
  unitPrice?: DecimalString | null;
  amount?: DecimalString | null;
  claimUnitPrice?: DecimalString | null;
  claimAmount?: DecimalString | null;
  costSubjectId: string;
  remark?: string | null;
}

export interface VariationOwnerSubmissionItemRecord extends Record<
  string,
  unknown
> {
  id: string;
  item_name?: string | null;
  unit?: string | null;
  quantity?: DecimalString | null;
  claimed_unit_price?: DecimalString | null;
  claimed_amount?: DecimalString | null;
  confirmed_amount?: DecimalString | null;
  reduction_reason?: string | null;
}

export interface VariationOwnerSubmissionRecord extends Record<
  string,
  unknown
> {
  id: string;
  revision_no?: string | number | null;
  submission_code?: string | null;
  external_document_no?: string | null;
  submitted_amount?: DecimalString | null;
  confirmed_amount?: DecimalString | null;
  status?: string | null;
  submitted_at?: string | null;
  response_document_no?: string | null;
  response_comment?: string | null;
  reviewed_at?: string | null;
  items?: VariationOwnerSubmissionItemRecord[];
}

export interface VariationQuery {
  pageNo?: number;
  pageSize?: number;
  projectId?: string;
  contractId?: string;
  partnerId?: string;
  varType?: string;
  direction?: string;
  varCode?: string;
  startDate?: string;
  endDate?: string;
}

export interface VariationSaveCommand {
  projectId: string;
  contractId: string;
  partnerId?: string | null;
  varName: string;
  eventDate?: string | null;
  claimDeadline?: string | null;
  eventDescription?: string | null;
  causeCategory?: string | null;
  responsibleParty?: string | null;
  businessMatterKey?: string | null;
  varType: string;
  direction?: string | null;
  impactDays?: number | null;
  version?: string | number | null;
  remark?: string | null;
}

export interface VariationOwnerSubmissionCommand {
  externalDocumentNo: string;
  submittedAt: string;
  remark?: string | null;
}

export interface VariationOwnerReviewCommand {
  conclusion: "CONFIRMED" | "RETURNED";
  responseDocumentNo: string;
  responseComment?: string | null;
  reviewedAt: string;
  items: Array<{
    submissionItemId: string;
    confirmedAmount: DecimalString;
    reductionReason?: string | null;
  }>;
}

export type VariationPage = PageResult<VariationRecord>;
export type VariationTrace = Record<string, unknown>;

export interface BidCostRecord {
  id: string;
  projectId?: string | null;
  bidProjectName: string;
  bidStatus: BidStatus;
  createdAt?: string | null;
  updatedAt?: string | null;
  remark?: string | null;
}

export interface BidCostQuery {
  pageNo?: number;
  pageSize?: number;
  bidStatus?: BidStatus;
  keyword?: string;
  projectId?: string;
  startDate?: string;
  endDate?: string;
}

export interface BidCostSaveCommand {
  bidProjectName: string;
  remark?: string | null;
}

export type BidCostPage = PageResult<BidCostRecord>;

export interface CostTargetRecord {
  id: string;
  projectId: string;
  versionNo: string;
  versionName: string;
  totalTargetAmount: DecimalString;
  totalBidCostAmount: DecimalString;
  totalResponsibilityAmount: DecimalString;
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

export interface CostForecastItemCommand {
  costSubjectId: string;
  estimatedRemainingAmount: DecimalString;
  remark?: string | null;
}

export interface CostForecastCommand {
  projectId: string;
  forecastCode: string;
  forecastName: string;
  forecastDate: string;
  items: CostForecastItemCommand[];
  remark?: string | null;
  version?: string | number | null;
}

export interface CostCorrectiveCommand {
  forecastId: string;
  actionCode: string;
  actionTitle: string;
  rootCause: string;
  actionPlan: string;
  expectedSavingAmount: DecimalString;
  responsibleUserId: string;
  dueDate: string;
  remark?: string | null;
  version?: string | number | null;
}

export interface CostCorrectiveCloseCommand {
  actualSavingAmount: DecimalString;
  resultDescription: string;
  version: string | number;
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

export interface MeasurementAmountRow extends Record<string, unknown> {
  id?: string;
  project_id?: string;
  contract_id?: string;
  period_id?: string;
  version?: string | number | null;
  unit_price?: DecimalString | null;
  current_reported_amount?: DecimalString | null;
  cumulative_reported_amount?: DecimalString | null;
  submitted_amount?: DecimalString | null;
  confirmed_amount?: DecimalString | null;
  deducted_amount?: DecimalString | null;
  tax_amount?: DecimalString | null;
  retention_amount?: DecimalString | null;
  current_reported_quantity?: DecimalString | null;
  submitted_quantity?: DecimalString | null;
  confirmed_quantity?: DecimalString | null;
  remainingQuantity?: DecimalString | null;
}

export interface MeasurementPeriodCommand {
  projectId: string;
  contractId: string;
  periodCode: string;
  periodName: string;
  startDate: string;
  endDate: string;
  cutoffDate: string;
  remark?: string | null;
}

export interface MeasurementLineCommand {
  contractItemId?: string | null;
  contractChangeId?: string | null;
  currentQuantity: DecimalString;
  evidenceCount: number;
}

export interface MeasurementSaveCommand {
  projectId: string;
  contractId: string;
  periodId: string;
  measureDate: string;
  attachmentCount: number;
  lines: MeasurementLineCommand[];
  remark?: string | null;
}

export interface OwnerMeasurementSubmissionCommand {
  externalDocumentNo?: string | null;
  attachmentCount: number;
  remark?: string | null;
  version: string | number;
}

export interface OwnerMeasurementReviewLineCommand {
  measurementLineId: string;
  confirmedQuantity: DecimalString;
  deductionReason?: string | null;
}

export interface OwnerMeasurementReviewCommand {
  decision: "CONFIRMED" | "RETURNED";
  reviewerName: string;
  reviewComment?: string | null;
  settlementDate?: string | null;
  dueDate?: string | null;
  taxAmount?: DecimalString | null;
  retentionAmount?: DecimalString | null;
  attachmentCount?: number | null;
  lines: OwnerMeasurementReviewLineCommand[];
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

export interface ProductionMeasurementAmountRow extends Record<
  string,
  unknown
> {
  unit_price?: DecimalString | null;
  current_reported_amount?: DecimalString | null;
  cumulative_reported_amount?: DecimalString | null;
  submitted_amount?: DecimalString | null;
  confirmed_amount?: DecimalString | null;
  gross_amount?: DecimalString | null;
  deducted_amount?: DecimalString | null;
  tax_amount?: DecimalString | null;
  retention_amount?: DecimalString | null;
  original_amount?: DecimalString | null;
  reported_amount?: DecimalString | null;
}

export type ContractPage = PageResult<ContractRecord>;

export const COMMERCIAL_QUERY_PERMISSIONS = {
  contract: "contract:query",
  variation: "variation:order:query",
  bidCost: "bid:query",
  costTarget: "cost:target:query",
  costLedger: "cost:ledger:query",
  costSummary: "cost:summary:view",
  costControl: "cost:control:query",
  budget: "budget:query",
  measurement: "measurement:query",
} as const;

export const COMMERCIAL_API = {
  contracts: "/contracts",
  contractKpi: "/contracts/kpi",
  contract: (id: string) => `/contracts/${encodeURIComponent(id)}`,
  contractItems: (id: string) => `/contracts/${encodeURIComponent(id)}/items`,
  contractPaymentTerms: (id: string) =>
    `/contracts/${encodeURIComponent(id)}/payment-terms`,
  contractApprovalRecords: (id: string) =>
    `/contracts/${encodeURIComponent(id)}/approval-records`,
  contractSubmit: (id: string) => `/contracts/${encodeURIComponent(id)}/submit`,
  contractCompositeCreate: "/contracts/composite",
  contractCompositeUpdate: (id: string) =>
    `/contracts/${encodeURIComponent(id)}/composite`,
  variations: "/var-orders",
  variation: (id: string) => `/var-orders/${encodeURIComponent(id)}`,
  variationItems: (id: string) =>
    `/var-orders/${encodeURIComponent(id)}/items/batch`,
  variationSubmit: (id: string) =>
    `/var-orders/${encodeURIComponent(id)}/submit`,
  variationOwnerSubmissions: (id: string) =>
    `/var-orders/${encodeURIComponent(id)}/owner-submissions`,
  variationOwnerReview: (id: string, submissionId: string) =>
    `/var-orders/${encodeURIComponent(id)}/owner-submissions/${encodeURIComponent(submissionId)}/review`,
  variationTrace: (id: string) => `/var-orders/${encodeURIComponent(id)}/trace`,
  bidCosts: "/bid-cost",
  bidCost: (id: string) => `/bid-cost/${encodeURIComponent(id)}`,
  bidWon: (id: string) => `/bid-cost/${encodeURIComponent(id)}/won`,
  bidLost: (id: string) => `/bid-cost/${encodeURIComponent(id)}/lost`,
  costTargets: "/cost-targets",
  costTarget: (id: string) => `/cost-targets/${encodeURIComponent(id)}`,
  costTargetItems: (id: string) =>
    `/cost-targets/${encodeURIComponent(id)}/items`,
  costTargetSubmit: (id: string) =>
    `/cost-targets/${encodeURIComponent(id)}/submit`,
  costTargetActivate: (id: string) =>
    `/cost-targets/${encodeURIComponent(id)}/activate`,
  costLedger: "/cost-ledger",
  costLedgerSummary: "/cost-ledger/summary",
  costLedgerDetail: (id: string) => `/cost-ledger/${encodeURIComponent(id)}`,
  costSummary: (projectId: string) =>
    `/cost-summary/${encodeURIComponent(projectId)}`,
  costSummaryHistory: (projectId: string) =>
    `/cost-summary/${encodeURIComponent(projectId)}/history`,
  costSummaryRefresh: (projectId: string) =>
    `/cost-summary/${encodeURIComponent(projectId)}/refresh`,
  costControl: (projectId: string) =>
    `/cost-controls/projects/${encodeURIComponent(projectId)}/overview`,
  costForecasts: "/cost-controls/forecasts",
  costForecast: (id: string) =>
    `/cost-controls/forecasts/${encodeURIComponent(id)}`,
  costForecastConfirm: (id: string) =>
    `/cost-controls/forecasts/${encodeURIComponent(id)}/confirm`,
  costForecastTrace: (id: string) =>
    `/cost-controls/forecasts/${encodeURIComponent(id)}/trace`,
  costCorrectiveActions: "/cost-controls/corrective-actions",
  costCorrectiveAction: (id: string) =>
    `/cost-controls/corrective-actions/${encodeURIComponent(id)}`,
  costCorrectiveSubmit: (id: string) =>
    `/cost-controls/corrective-actions/${encodeURIComponent(id)}/submit`,
  costCorrectiveClose: (id: string) =>
    `/cost-controls/corrective-actions/${encodeURIComponent(id)}/close`,
  budgets: "/project-budgets",
  budget: (id: string) => `/project-budgets/${encodeURIComponent(id)}`,
  budgetLines: (id: string) =>
    `/project-budgets/${encodeURIComponent(id)}/lines`,
  budgetSubmit: (id: string) =>
    `/project-budgets/${encodeURIComponent(id)}/submit`,
  budgetAvailability: (id: string) =>
    `/project-budgets/${encodeURIComponent(id)}/availability`,
  measurements: "/production-measurements",
  measurement: (id: string) =>
    `/production-measurements/${encodeURIComponent(id)}`,
  measurementSubmit: (id: string) =>
    `/production-measurements/${encodeURIComponent(id)}/submit`,
  measurementPeriods: "/production-measurements/periods",
  measurementPeriodClose: (id: string) =>
    `/production-measurements/periods/${encodeURIComponent(id)}/close`,
  measurementSources: "/production-measurements/sources",
  ownerMeasurementSubmissions:
    "/production-measurements/owner-submissions/list",
  ownerMeasurementSubmission: (id: string) =>
    `/production-measurements/owner-submissions/${encodeURIComponent(id)}`,
  ownerMeasurementSubmit: (id: string) =>
    `/production-measurements/${encodeURIComponent(id)}/owner-submissions`,
  ownerMeasurementReview: (id: string) =>
    `/production-measurements/owner-submissions/${encodeURIComponent(id)}/review`,
  measurementSettlementTrace: (id: string) =>
    `/production-measurements/trace/settlements/${encodeURIComponent(id)}`,
  partners: "/partners",
  projectContextOptions: "/project-context/options",
} as const;

export const COMMERCIAL_MONEY_FIELDS = {
  contract: [
    "contractAmount",
    "currentAmount",
    "taxAmount",
    "amountWithoutTax",
    "paidAmount",
    "settlementAmount",
  ],
  contractKpi: ["totalAmount", "paidAmount", "unpaidAmount"],
  contractItem: [
    "quantity",
    "unitPrice",
    "amount",
    "taxRate",
    "taxAmount",
    "amountWithoutTax",
  ],
  contractPaymentTerm: ["paymentRatio", "paymentAmount"],
  variation: [
    "reportedAmount",
    "approvedAmount",
    "confirmedAmount",
    "estimatedCostAmount",
  ],
  costTarget: [
    "totalTargetAmount",
    "totalBidCostAmount",
    "totalResponsibilityAmount",
    "targetAmount",
    "bidCostAmount",
    "responsibilityAmount",
  ],
  costLedger: [
    "amount",
    "taxAmount",
    "amountWithoutTax",
    "totalAmount",
    "totalTaxAmount",
  ],
  costSummary: [
    "targetCost",
    "contractLockedCost",
    "actualCost",
    "paidAmount",
    "estimatedRemainingCost",
    "dynamicCost",
    "contractIncome",
    "confirmedRevenue",
    "expectedProfit",
    "costDeviation",
    "responsibilityCost",
    "forecastAtCompletionCost",
    "forecastProfit",
  ],
  costControl: [
    "bid_cost_amount",
    "target_amount",
    "responsibility_amount",
    "committed_amount",
    "actual_amount",
    "recommended_remaining_amount",
    "estimated_remaining_amount",
    "forecast_at_completion_amount",
    "contract_income_amount",
    "forecast_profit_amount",
    "cost_variance_amount",
    "expected_saving_amount",
    "actual_saving_amount",
  ],
  budget: [
    "totalAmount",
    "budgetAmount",
    "reservedAmount",
    "consumedAmount",
    "availableAmount",
  ],
  measurement: [
    "unit_price",
    "current_reported_amount",
    "cumulative_reported_amount",
    "submitted_amount",
    "confirmed_amount",
    "gross_amount",
    "deducted_amount",
    "tax_amount",
    "retention_amount",
    "original_amount",
    "reported_amount",
  ],
} as const;
