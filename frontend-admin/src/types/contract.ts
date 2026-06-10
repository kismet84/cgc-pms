export type ContractType = 'MAIN' | 'SUB' | 'PURCHASE' | 'LEASE' | 'SERVICE'
export type ContractStatus = 'DRAFT' | 'EXECUTING' | 'COMPLETED' | 'TERMINATED'
export type ApprovalStatus = 'DRAFT' | 'SUBMITTED' | 'APPROVING' | 'APPROVED' | 'REJECTED'

export interface ContractItem {
  id: string
  itemCode: string
  itemName: string
  itemSpec: string
  unit: string
  quantity: number
  unitPrice: string
  amount: string
  taxRate: number
  taxAmount: string
  amountWithoutTax: string
  sortOrder: number
}

export interface ContractPaymentTerm {
  id: string
  termName: string
  paymentRatio: number
  paymentAmount: string
  paymentCondition: string
  plannedDate: string
  actualDate?: string
  termStatus: string
  sortOrder: number
}

export interface ContractVO {
  id: string
  tenantId: string
  orgId: string
  projectId: string
  partnerId: string
  contractCode: string
  contractName: string
  contractType: ContractType
  partyA: string
  partyB: string
  contractAmount: string
  currentAmount: string
  taxRate: number
  taxAmount: string
  amountWithoutTax: string
  signedDate: string
  startDate: string
  endDate: string
  paymentMethod: string
  settlementMethod: string
  warrantyRate: number
  warrantyAmount: string
  contractStatus: ContractStatus
  approvalStatus: ApprovalStatus
  projectName: string
  partnerName: string
  createdBy: string
  createdAt: string
  updatedBy: string
  updatedAt: string
  remark?: string
}

export interface ContractQueryParams {
  projectId?: string
  contractType?: ContractType
  contractStatus?: ContractStatus
  approvalStatus?: ApprovalStatus
  partnerId?: string
  contractCode?: string
  keyword?: string
  startDate?: string
  endDate?: string
  pageNo: number
  pageSize: number
}

export interface ContractKpiVO {
  totalCount: number
  totalAmount: string
  paidAmount: string
  unpaidAmount: string
  overdueCount: number
}
