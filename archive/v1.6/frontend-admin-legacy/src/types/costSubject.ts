export interface CostSubjectVO {
  id: string
  parentId: string | null
  subjectCode: string
  subjectName: string
  subjectType: string
  accountCategory: string
  level: number
  sortOrder: number
  status: string
}

export interface CostSubjectTreeNode extends CostSubjectVO {
  children?: CostSubjectTreeNode[]
}

export interface CostSubjectMappingVersion {
  id: string
  versionCode: string
  versionName: string
  status: 'DRAFT' | 'ACTIVE' | 'RETIRED'
  effectiveDate?: string
  approvalInstanceId?: string
  itemCount: number
  remark?: string
}

export interface CostSubjectRule {
  id: string
  ruleCode: string
  versionCode: string
  sourceType: string
  businessCategory: string
  projectId?: string
  costSubjectId: string
  subjectCode: string
  subjectName: string
  priority: number
  status: 'DRAFT' | 'ACTIVE' | 'RETIRED'
  effectiveFrom: string
  effectiveTo?: string
}

export interface ProjectCostSubjectScope {
  id: string
  projectId: string
  costSubjectId: string
  subjectCode: string
  subjectName: string
  enabled: number
  effectiveFrom: string
  effectiveTo?: string
}

export interface CostSubjectImpact {
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

export type CostSubjectV2Row = Record<string, string | number | null>
