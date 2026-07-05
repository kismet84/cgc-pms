/** Payment type display mapping */
export type PayType = 'ADVANCE' | 'PROGRESS' | 'FINAL' | 'OTHER'

export const PAY_TYPE_LABEL: Record<PayType, string> = {
  ADVANCE: '预付款',
  PROGRESS: '进度款',
  FINAL: '结算款',
  OTHER: '其他',
}

export const PAY_TYPE_COLOR: Record<PayType, string> = {
  ADVANCE: 'blue',
  PROGRESS: 'processing',
  FINAL: 'success',
  OTHER: 'default',
}

/** Payment status display mapping */
export type PayStatus = 'PENDING' | 'APPROVED' | 'UNPAID' | 'PARTIAL' | 'PARTIALLY_PAID' | 'PAID'

export const PAY_STATUS_LABEL: Record<PayStatus, string> = {
  PENDING: '待付款',
  APPROVED: '已批未付',
  UNPAID: '未支付',
  PARTIAL: '部分支付',
  PARTIALLY_PAID: '部分支付',
  PAID: '已支付',
}

export const PAY_STATUS_COLOR: Record<PayStatus, string> = {
  PENDING: 'default',
  APPROVED: 'warning',
  UNPAID: 'default',
  PARTIAL: 'warning',
  PARTIALLY_PAID: 'warning',
  PAID: 'success',
}

/** Payment application view object */
export interface PayApplicationVO {
  id: string
  applyCode: string
  projectId: string
  contractId: string
  partnerId: string
  payType: PayType
  applyAmount: string
  approvedAmount?: string
  actualPayAmount?: string
  payStatus: PayStatus
  approvalStatus: string
  applyReason?: string
  projectName?: string
  contractName?: string
  partnerName?: string
  createdBy?: string
  createdAt?: string
  updatedAt?: string
  remark?: string
  basis?: PayApplicationBasisVO[]
}

/** Payment application basis view object */
export interface PayApplicationBasisVO {
  id?: string
  tenantId?: string
  payApplicationId?: string
  basisType?: string
  basisId?: string
  basisAmount?: string
  createdBy?: string
  createdAt?: string
  updatedAt?: string
  remark?: string
}

/** Payment record view object */
export interface PayRecordVO {
  id: string
  applicationId: string
  payAmount: string
  payDate: string
  payMethod: string
  voucherNo?: string
  createdBy?: string
  createdAt?: string
}

/** Writeback request */
export interface PayWritebackDTO {
  payApplicationId: string
  payAmount: number
  payDate: string
  payMethod: string
  voucherNo?: string
}
