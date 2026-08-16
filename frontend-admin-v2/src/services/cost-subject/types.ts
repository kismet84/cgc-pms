export interface CostSubjectRecord {
  id: string
  parentId: string
  subjectCode: string
  subjectName: string
  subjectType: string
  accountCategory: string
  level: number
  sortOrder: number
  status: string
  defaultTargetRatio?: string | null
  ledgerFlag?: number
  children?: CostSubjectRecord[]
}

export interface AccountingDimensionPolicy {
  subjectCode: string
  subjectName: string
  projectRequirement: string
  contractRequirement: string
  partnerRequirement: string
  departmentRequirement: string
  employeeRequirement: string
  allowedContractTypes?: string | null
  allowedPartnerTypes?: string | null
}

export interface AccountingCarryoverMapping {
  categoryCode: string
  categoryName: string
  fulfillmentCode: string
  fulfillmentName: string
  expenseCode: string
  expenseName: string
  status: string
}

export interface AccountingLegacyReview {
  sourceSubjectCode: string
  sourceSubjectName: string
  suggestedSubjectCode?: string | null
  reviewStatus: string
  reviewNote?: string | null
}

export type AccountingLegacyReviewStatus = 'CONFIRMED' | 'IGNORED'

export interface AccountingCatalogOverview {
  policies: AccountingDimensionPolicy[]
  carryoverMappings: AccountingCarryoverMapping[]
  legacyReviews: AccountingLegacyReview[]
  reportRoutes: Array<{ label: string; path: string }>
}

export type AccountCategory =
  'ASSET' | 'LIABILITY' | 'EQUITY' | 'COST' | 'REVENUE' | 'SETTLEMENT' | 'RECEIVABLE'

export interface CostSubjectCommand {
  parentId: string
  subjectCode: string
  subjectName: string
  subjectType: string
  accountCategory: AccountCategory
  sortOrder: number
  status: 'ENABLE' | 'DISABLE'
}

export interface MappingVersionRecord {
  id: string
  versionCode: string
  versionName: string
  status: string
  effectiveDate?: string
  approvalInstanceId?: string
  itemCount: number
  remark?: string
}

export interface MappingVersionCommand {
  versionCode: string
  versionName: string
  effectiveDate: string | null
  remark: string
  items: Array<{
    sourceSubjectId: string
    targetGroupCode: string
    targetSubjectId: string | null
    historicalDisplayName: string
    mappingReason: string
  }>
  rules?: Array<{
    ruleCode: string
    sourceType: string
    businessCategory: string
    projectId: string | null
    costSubjectId: string
    priority: number
    effectiveFrom: string | null
    effectiveTo: string | null
    remark: string
  }>
}

export interface AssignmentRuleRecord {
  id: string
  ruleCode: string
  versionCode: string
  sourceType: string
  businessCategory: string
  projectId?: string
  projectCode?: string | null
  projectName?: string | null
  costSubjectId: string
  subjectCode: string
  subjectName: string
  priority: number
  status: string
  effectiveFrom: string
  effectiveTo?: string
}

export interface AssignmentRuleCommand {
  ruleCode: string
  mappingVersionId: string
  sourceType: string
  businessCategory: string
  projectId: string | null
  costSubjectId: string
  priority: number
  effectiveFrom: string | null
  effectiveTo: string | null
  remark: string
}

export interface ProjectScopeRecord {
  id: string
  projectId: string
  costSubjectId: string
  subjectCode: string
  subjectName: string
  enabled: number
  effectiveFrom: string
  effectiveTo?: string
}

export interface ProjectScopeCommand {
  projectId: string
  costSubjectId: string
  enabled: boolean
  effectiveFrom: string | null
  effectiveTo: string | null
  remark: string
}

export interface SubjectImpactRecord {
  subjectId: string
  costItems: number
  targetItems: number
  forecastItems: number
  budgetLines: number
  payments: number
  expenses: number
  settlementItems: number
  accountingLines: number
  assignmentRules: number
  projectScopes: number
}

export type CostSubjectAuditRow = Record<string, string | number | null>

export interface BidTransferRequestRecord {
  id: string
  requestCode: string
  bidCostId: string
  bidCode?: string | null
  projectId: string
  projectCode?: string | null
  projectName?: string | null
  targetId: string
  targetVersionNo?: string | null
  targetVersionName?: string | null
  mappingVersionId: string
  totalAmount: string
  status: string
  approvalInstanceId?: string | null
  finalTransferId?: string | null
  createdAt?: string | null
}

export interface BidTransferRequestCommand {
  bidCostId: string
  projectId: string
  targetId: string
  mappingVersionId: string
  idempotencyKey?: string
  remark: string
}

export interface FinanceAllocationRequestRecord {
  id: string
  requestCode: string
  projectId: string
  projectCode?: string | null
  projectName?: string | null
  sourceType: string
  sourceId: string
  sourceCode?: string | null
  sourceAmount: string
  allocationBasis: string
  accountingPeriod: string
  costSubjectId: string
  costSubjectCode?: string | null
  costSubjectName?: string | null
  status: string
  approvalInstanceId?: string | null
  finalBatchId?: string | null
  createdAt?: string | null
}

export interface FinanceAllocationRequestCommand {
  sourceType: string
  sourceId: string
  allocationBasis: string
  accountingPeriod: string
  costSubjectId: string
  idempotencyKey?: string
  remark: string
  lines: Array<{ projectId: string; basisValue: string }>
}

export interface GovernanceProjectOption {
  id: string
  projectCode: string
  projectName: string
  projectStatus: string
}

export interface GovernanceSubjectOption {
  id: string
  subjectCode: string
  subjectName: string
  subjectType: string
  status: string
  overheadRuleStatus?: 'ENABLE' | 'DISABLE' | null
}

export interface GovernancePlanOption {
  id: string
  versionCode: string
  versionName: string
  status: string
  effectiveDate?: string | null
}

export interface BidCostOption {
  id: string
  bidCode: string
  bidProjectName: string
  projectId: string
  projectCode: string
  projectName: string
}

export interface TargetVersionOption {
  id: string
  projectId: string
  projectCode: string
  projectName: string
  versionNo: string
  versionName: string
  totalTargetAmount: string
  status: string
  approvalStatus: string
}

export interface FinanceSourceOption {
  sourceType: string
  sourceId: string
  projectId?: string | null
  sourceCode: string
  sourceName?: string | null
  remainingAmount: string
}

export interface GovernanceFormOptions {
  projects: GovernanceProjectOption[]
  costSubjects: GovernanceSubjectOption[]
  rulePlans: GovernancePlanOption[]
  bidCosts: BidCostOption[]
  targetVersions: TargetVersionOption[]
  financeSources: FinanceSourceOption[]
  pendingClassifications: CostSubjectAuditRow[]
}

export interface ClassificationOverrideCommand {
  caseId: string | null
  snapshotId: string | null
  costSubjectId: string
  reason: string
}

export interface ProjectConfigurationRecord {
  project: CostSubjectAuditRow
  subjects: CostSubjectAuditRow[]
  requests: CostSubjectAuditRow[]
}

export interface ProjectConfigCommand {
  projectId: string
  reason: string
  lines: Array<{
    costSubjectId: string
    enabled: boolean
    effectiveFrom: string | null
    effectiveTo: string | null
  }>
}

export interface RecalculationCommand {
  projectId: string | null
  ruleVersionId: string
  cutoffAt: string | null
  batchType: 'HISTORY_RECALCULATION' | 'POST_CLOSE_ADJUSTMENT'
  reason: string
  idempotencyKey?: string
}

export interface ReversalCommand {
  targetType: 'BID_TRANSFER' | 'FINANCE_ALLOCATION' | 'RECALCULATION'
  targetId: string
  reason: string
  idempotencyKey?: string
}

export interface OverheadAllocationRuleRecord {
  id: string
  costSubjectId: string
  allocationBasis: 'DIRECT_LABOR' | 'CONTRACT_AMOUNT' | 'USAGE'
  allocationCycle: 'MONTHLY' | 'PER_OCCURRENCE'
  status: 'ENABLE' | 'DISABLE'
}

export interface OverheadAllocationRuleCommand {
  costSubjectId: string
  allocationBasis: 'DIRECT_LABOR' | 'CONTRACT_AMOUNT'
  allocationCycle: 'MONTHLY'
}

export interface OverheadAllocationExecutionResult {
  period: string
  ruleCount: number
  createdRunCount: number
  duplicateRunCount: number
  costItemCount: number
  allocatedAmount: string
  idempotent: boolean
}
