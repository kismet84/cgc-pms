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
  costSubjectId?: string
  budgetLineId?: string
  expenseCategory?: string
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
  approvalInstanceId?: string
  integrityVersion?: string
  basis?: PayApplicationBasisVO[]
}

export interface PaymentApplicationSourceVO {
  id?: string
  payApplicationId?: string
  sourceType: 'EXPENSE' | 'SUB_MEASURE' | 'SETTLEMENT' | 'DIRECT'
  sourceRefId: string
  expenseId?: string
  settlementId?: string
  subMeasureId?: string
  sourceAmount: string
  paidAmount?: string
  remark?: string
}

export interface PaymentSourceOptionVO {
  sourceType: 'SUB_MEASURE' | 'SETTLEMENT'
  sourceRefId: string
  documentCode: string
  sourceTotalAmount: string
  committedAmount: string
  availableAmount: string
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
  tenantId?: string
  payApplicationId: string
  contractId?: string
  partnerId?: string
  payAmount: string
  payDate: string
  paidAt?: string
  fundAccountId?: string
  payMethod: string
  voucherNo?: string
  payStatus?: string
  externalTxnNo?: string
  failureReason?: string
  reversedRecordId?: string
  reversedAt?: string
  reversalType?: 'REVERSAL' | 'REFUND'
  createdBy?: string
  createdAt?: string
}

export interface PaymentReversalDTO {
  reversalType: 'REVERSAL' | 'REFUND'
  externalTxnNo: string
  reversedAt: string
  reason: string
}

export interface PaymentFailureDTO {
  payApplicationId: string
  payAmount: number
  externalTxnNo: string
  attemptedAt: string
  failureReason: string
  fundAccountId?: string
  payMethod?: string
}

/** Writeback request */
export interface PayWritebackDTO {
  payApplicationId: string
  payAmount: number
  payDate?: string
  paidAt: string
  fundAccountId: string
  payMethod: string
  voucherNo?: string
  externalTxnNo: string
}

export interface PaymentTraceVO {
  project?: Record<string, unknown>
  contract?: Record<string, unknown>
  paymentApplication: PayApplicationVO
  approvalInstance?: Record<string, unknown>
  approvalRecords: Record<string, unknown>[]
  applicationSources: PaymentApplicationSourceVO[]
  expenses: Record<string, unknown>[]
  settlements: Record<string, unknown>[]
  settlementSubMeasures: Record<string, unknown>[]
  subMeasures: Record<string, unknown>[]
  subTasks: Record<string, unknown>[]
  paymentRecords: PayRecordVO[]
  paymentSourceAllocations: Record<string, unknown>[]
  cashJournals: Record<string, unknown>[]
  invoices: Record<string, unknown>[]
  invoiceAllocations: Record<string, unknown>[]
  budgetLedgers: Record<string, unknown>[]
  accountingEntries: Record<string, unknown>[]
  accountingEntryLines: Record<string, unknown>[]
}
