export interface VarOrderVO {
  id: string
  tenantId: string
  projectId: string
  contractId?: string
  partnerId?: string
  varCode: string
  varName: string
  varType?: string
  direction?: string
  reportedAmount?: string
  approvedAmount?: string
  confirmedAmount?: string
  ownerConfirmFlag?: number
  impactDays?: number
  approvalStatus?: string
  costGeneratedFlag?: number
  projectName?: string
  contractName?: string
  partnerName?: string
  items?: VarOrderItemVO[]
  createdBy?: string
  createdAt?: string
  remark?: string
}

export interface VarOrderItemVO {
  id: string
  varOrderId: string
  itemName?: string
  unit?: string
  quantity?: string
  unitPrice?: string
  amount?: string
  costSubjectId?: string
}
