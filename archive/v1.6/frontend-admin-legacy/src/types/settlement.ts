/** Settlement status (lifecycle state) */
export type SettlementStatus = 'DRAFT' | 'FINALIZED' | 'CANCELLED'

export const SETTLEMENT_STATUS_LABEL: Record<SettlementStatus, string> = {
  DRAFT: '草稿',
  FINALIZED: '已定案',
  CANCELLED: '已作废',
}

export const SETTLEMENT_STATUS_COLOR: Record<SettlementStatus, string> = {
  DRAFT: 'default',
  FINALIZED: 'success',
  CANCELLED: 'error',
}

/** Approval status */
export type ApprovalStatus = 'DRAFT' | 'APPROVING' | 'APPROVED' | 'REJECTED' | 'WITHDRAWN'

export const APPROVAL_STATUS_LABEL: Record<ApprovalStatus, string> = {
  DRAFT: '草稿',
  APPROVING: '审批中',
  APPROVED: '已通过',
  REJECTED: '已驳回',
  WITHDRAWN: '已撤回',
}

export const APPROVAL_STATUS_COLOR: Record<ApprovalStatus, string> = {
  DRAFT: 'default',
  APPROVING: 'processing',
  APPROVED: 'success',
  REJECTED: 'error',
  WITHDRAWN: 'warning',
}

/** Settlement main view object — matches backend StlSettlementVO */
export interface SettlementVO {
  id: string
  tenantId: string
  projectId: string
  contractId: string
  partnerId: string
  settlementCode: string
  settlementType?: string
  /** Snapshot of contract amount */
  contractAmount: string
  /** SUM of confirmed VAR_ORDER amounts (COST direction) */
  changeAmount: string
  /** SUM of approved sub-measure amounts */
  measuredAmount: string
  /** Manual deduction amount */
  deductionAmount: string
  /** SUM of successful pay records */
  paidAmount: string
  /** Auto-computed: contractAmount + changeAmount + measuredAmount - deductionAmount */
  finalAmount: string
  /** Auto-computed: finalAmount - paidAmount - warrantyAmount */
  unpaidAmount: string
  /** Auto-computed: finalAmount × warrantyRate */
  warrantyAmount: string
  /** Settlement lifecycle status */
  settlementStatus: SettlementStatus
  /** Approval workflow status */
  approvalStatus: string
  /** General contract status */
  status: string
  /** Timestamp when finalized (locked) */
  finalizedAt?: string
  /** Joined: project name */
  projectName: string
  /** Joined: contract name */
  contractName: string
  /** Joined: partner name */
  partnerName: string
  createdBy: string
  createdAt: string
  updatedBy?: string
  updatedAt?: string
  remark?: string
  /** Nested items (only populated in detail query) */
  items?: SettlementItemVO[]
}

/** Settlement line item — matches backend StlSettlementItemVO */
export interface SettlementItemVO {
  id: string
  tenantId: string
  settlementId: string
  itemName: string
  unit: string
  quantity: string
  unitPrice: string
  amount: string
  costSubjectId?: string
  sourceType?: string
  sourceId?: string
  createdBy?: string
  createdAt?: string
  updatedAt?: string
  remark?: string
}

/** Settlement list query params */
export interface SettlementQueryParams {
  projectId?: string
  contractId?: string
  partnerId?: string
  settlementStatus?: SettlementStatus
  settlementCode?: string
  settlementType?: string
  keyword?: string
  pageNo: number
  pageSize: number
}

/** KPI stat cards for settlement list page */
export interface SettlementKpiVO {
  totalCount: number
  totalContractAmount: string
  totalFinalAmount: string
  totalChangeAmount: string
  totalPaidAmount: string
  totalUnpaidAmount: string
  draftCount: number
  finalizedCount: number
}

/** Compute result — auto-calculated settlement preview for a contract */
export interface SettlementComputeVO {
  contractId: string
  contractCode: string
  contractName: string
  contractAmount: string
  changeAmount: string
  measuredAmount: string
  deductionAmount: string
  paidAmount: string
  finalAmount: string
  unpaidAmount: string
  warrantyAmount: string
}

/** Change visa item shown in tab 3 */
export interface SettlementVariationItemVO {
  id: string
  varCode: string
  varName: string
  varType: string
  direction: string
  reportedAmount: string
  approvedAmount: string
  confirmedAmount: string
  impactDays?: number
  createdAt: string
}

/** Payment detail item shown in tab 4 */
export interface SettlementPaymentItemVO {
  id: string
  applicationId: string
  applyCode: string
  payType: string
  applyAmount: string
  approvedAmount: string
  actualPayAmount: string
  payStatus: string
  payDate?: string
  voucherNo?: string
  createdAt: string
}

/** Cost detail item shown in tab 5 */
export interface SettlementCostItemVO {
  id: string
  costSubjectName: string
  costType: string
  sourceType: string
  sourceId: string
  sourceItemId?: string
  amount: string
  taxAmount: string
  amountWithoutTax: string
  costDate: string
  costStatus: string
}

/** Attachment item shown in tab 6 */
export interface SettlementAttachmentVO {
  id: string
  originalName: string
  fileSize: number
  fileType: string
  uploadedBy: string
  uploadedAt: string
}

/** Approval record item shown in tab 7 */
export interface SettlementApprovalRecordVO {
  id: string
  nodeName: string
  operatorName: string
  actionType: string
  actionName: string
  comment?: string
  createdAt: string
}
