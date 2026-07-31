export interface BudgetLineVO {
  id?: string
  costSubjectId: string
  costSubjectName?: string
  budgetAmount: string
  reservedAmount?: string
  consumedAmount?: string
  availableAmount?: string
  version?: number
  remark?: string
}

export interface ProjectBudgetVO {
  id: string
  projectId: string
  versionNo: string
  budgetName: string
  totalAmount: string
  approvalStatus: string
  status: string
  active: boolean
  effectiveAt?: string
  version?: number
  createdAt?: string
  updatedAt?: string
  remark?: string
  lines?: BudgetLineVO[]
}

export interface BudgetAvailabilityVO {
  budgetId: string
  budgetLineId: string
  projectId: string
  costSubjectId: string
  budgetAmount: string
  reservedAmount: string
  consumedAmount: string
  availableAmount: string
}
