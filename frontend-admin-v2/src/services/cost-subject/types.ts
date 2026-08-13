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
  children?: CostSubjectRecord[]
}

export interface CostSubjectCommand {
  parentId: string
  subjectCode: string
  subjectName: string
  subjectType: string
  accountCategory: 'COST'
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
  idempotencyKey: string
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
  idempotencyKey: string
  remark: string
  lines: Array<{ projectId: string; basisValue: string }>
}
