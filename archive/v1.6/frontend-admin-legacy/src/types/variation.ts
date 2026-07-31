export interface VarOrderVO {
  id: string
  tenantId: string
  projectId: string
  contractId?: string
  partnerId?: string
  varCode: string
  varName: string
  eventDate?: string
  claimDeadline?: string
  eventDescription?: string
  causeCategory?: string
  responsibleParty?: string
  businessMatterKey?: string
  varType?: string
  direction?: string
  reportedAmount?: string
  approvedAmount?: string
  confirmedAmount?: string
  estimatedCostAmount?: string
  ownerConfirmFlag?: number
  ownerStatus?: string
  internalApprovalInstanceId?: string
  generatedContractChangeId?: string
  impactDays?: number
  approvalStatus?: string
  costGeneratedFlag?: number
  projectName?: string
  contractName?: string
  partnerName?: string
  items?: VarOrderItemVO[]
  ownerSubmissions?: VariationOwnerSubmission[]
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
  claimUnitPrice?: string
  claimAmount?: string
  costSubjectId?: string
}

export interface VariationOwnerSubmissionItem {
  id: number | string
  claimed_amount: string | number
  confirmed_amount?: string | number
  item_name: string
  reduction_reason?: string
}

export interface VariationOwnerSubmission {
  id: number | string
  revision_no: number
  status: string
  submitted_amount: string | number
  submitted_at: string
  items: VariationOwnerSubmissionItem[]
}

export interface VariationOwnerReviewLine {
  submissionItemId: number | string
  confirmedAmount: string | number
  reductionReason?: string
}
