export * from "./commercial/types";
export * from "./commercial/contracts";
export * from "./commercial/partners";
export * from "./commercial/variation";
export * from "./commercial/bid";
export * from "./commercial/cost";
export * from "./commercial/measurement";

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
  contractProjectOptions: "/contracts/project-options",
  contractKpi: "/contracts/kpi",
  contract: (id: string) => `/contracts/${encodeURIComponent(id)}`,
  contractItems: (id: string) => `/contracts/${encodeURIComponent(id)}/items`,
  contractPaymentTerms: (id: string) =>
    `/contracts/${encodeURIComponent(id)}/payment-terms`,
  contractApprovalRecords: (id: string) =>
    `/contracts/${encodeURIComponent(id)}/approval-records`,
  contractBudgetAllocations: (id: string) =>
    `/contracts/${encodeURIComponent(id)}/budget-allocations`,
  contractSubmit: (id: string) => `/contracts/${encodeURIComponent(id)}/submit`,
  contractSettle: (id: string) => `/contracts/${encodeURIComponent(id)}/settle`,
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
  bidStatus: (id: string) => `/bid-cost/${encodeURIComponent(id)}/status`,
  bidDocuments: (id: string) => `/bid-cost/${encodeURIComponent(id)}/documents`,
  bidDocumentFinalize: (id: string, versionId: string) =>
    `/bid-cost/${encodeURIComponent(id)}/documents/${encodeURIComponent(versionId)}/finalize`,
  bidDocumentVoid: (id: string, versionId: string) =>
    `/bid-cost/${encodeURIComponent(id)}/documents/${encodeURIComponent(versionId)}/void`,
  costTargets: "/cost-targets",
  costTargetDefaultAllocation: "/cost-targets/default-allocation",
  costTargetProjectManagerOptions: "/cost-targets/project-manager-options",
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
  accessibleCostSummary: "/cost-summary",
  costSummary: (projectId: string) =>
    `/cost-summary/${encodeURIComponent(projectId)}`,
  costSummaryHistory: (projectId: string) =>
    `/cost-summary/${encodeURIComponent(projectId)}/history`,
  costSummaryRefresh: (projectId: string) =>
    `/cost-summary/${encodeURIComponent(projectId)}/refresh`,
  accessibleCostControl: "/cost-controls/overview",
  costControl: (projectId: string) =>
    `/cost-controls/projects/${encodeURIComponent(projectId)}/overview`,
  costCorrectiveOwnerOptions: (projectId: string) =>
    `/cost-controls/projects/${encodeURIComponent(projectId)}/corrective-owner-options`,
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
  measurementFormOptions: "/production-measurements/form-options",
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
