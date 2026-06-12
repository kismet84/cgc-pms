export type ChangeType = 'AMOUNT' | 'DURATION' | 'CLAUSE'
export type ChangeApprovalStatus = 'DRAFT' | 'APPROVING' | 'APPROVED' | 'REJECTED' | 'WITHDRAWN'

export const CHANGE_TYPE_LABEL: Record<ChangeType, string> = {
  AMOUNT: '金额变更',
  DURATION: '工期变更',
  CLAUSE: '条款变更',
}

export const CHANGE_TYPE_COLOR: Record<ChangeType, string> = {
  AMOUNT: 'blue',
  DURATION: 'orange',
  CLAUSE: 'purple',
}

export const CHANGE_APPROVAL_LABEL: Record<ChangeApprovalStatus, string> = {
  DRAFT: '草稿',
  APPROVING: '审批中',
  APPROVED: '已通过',
  REJECTED: '已驳回',
  WITHDRAWN: '已撤回',
}

export interface ContractChangeVO {
  id: string
  tenantId?: string
  projectId: string
  contractId: string
  changeCode: string
  changeName: string
  changeType: ChangeType
  beforeAmount: string
  changeAmount: string
  afterAmount: string
  reason?: string
  approvalStatus: ChangeApprovalStatus
  effectiveFlag: number
  costGeneratedFlag: number
  projectName?: string
  contractName?: string
  createdBy?: string
  createdTime?: string
  updatedBy?: string
  updatedTime?: string
  remark?: string
}

export interface ContractChangeQueryParams {
  pageNo: number
  pageSize: number
  projectId?: number
  contractId?: number
  changeType?: ChangeType
  approvalStatus?: ChangeApprovalStatus
  changeCode?: string
}
