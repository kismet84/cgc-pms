export interface SubTaskVO {
  id: string
  tenantId: string
  projectId: string
  contractId?: string
  partnerId?: string
  taskCode: string
  taskName: string
  workArea?: string
  plannedStartDate?: string
  plannedEndDate?: string
  actualStartDate?: string
  actualEndDate?: string
  progressPercent?: string
  status: string
  projectName?: string
  contractName?: string
  partnerName?: string
  createdBy: string
  createdAt: string
  updatedAt: string
  remark?: string
}

export type SubTaskStatus = 'NOT_STARTED' | 'IN_PROGRESS' | 'COMPLETED' | 'SUSPENDED'

// --- 分包计量 ---
export interface SubMeasureVO {
  id: string
  tenantId: string
  projectId: string
  contractId?: string
  partnerId?: string
  measureCode: string
  measurePeriod?: string
  measureDate?: string
  reportedAmount?: string
  approvedAmount?: string
  deductionAmount?: string
  netAmount?: string
  approvalStatus?: string
  costGeneratedFlag?: number
  status?: string
  projectName?: string
  contractName?: string
  partnerName?: string
  items?: SubMeasureItemVO[]
  createdBy?: string
  createdAt?: string
  remark?: string
}

export interface SubMeasureItemVO {
  id: string
  measureId: string
  contractItemId?: string
  itemName?: string
  unit?: string
  contractQuantity?: string
  currentQuantity?: string
  cumulativeQuantity?: string
  unitPrice?: string
  amount?: string
}
