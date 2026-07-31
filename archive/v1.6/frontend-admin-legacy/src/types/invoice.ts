/** Invoice type display mapping */
export type InvoiceType = 'VAT_SPECIAL' | 'VAT_NORMAL' | 'OTHER'

export const INVOICE_TYPE_LABEL: Record<InvoiceType, string> = {
  VAT_SPECIAL: '增值税专票',
  VAT_NORMAL: '增值税普票',
  OTHER: '其他',
}

export const INVOICE_TYPE_COLOR: Record<InvoiceType, string> = {
  VAT_SPECIAL: 'blue',
  VAT_NORMAL: 'green',
  OTHER: 'default',
}

/** Verify status display mapping */
export type VerifyStatus = 'PENDING' | 'VERIFIED' | 'ABNORMAL'

export const VERIFY_STATUS_LABEL: Record<VerifyStatus, string> = {
  PENDING: '待核验',
  VERIFIED: '已认证',
  ABNORMAL: '异常',
}

export const VERIFY_STATUS_COLOR: Record<VerifyStatus, string> = {
  PENDING: 'default',
  VERIFIED: 'success',
  ABNORMAL: 'error',
}

/** Invoice view object (matches backend InvoiceVO) */
export interface InvoiceVO {
  id: string
  tenantId?: string
  payRecordId?: string
  payApplicationId?: string
  projectId?: string
  contractId?: string
  partnerId?: string
  documentType?: 'ELECTRONIC_INVOICE' | 'SCANNED_INVOICE'
  integrityVersion?: string
  invoiceNo: string
  invoiceType: InvoiceType
  invoiceAmount: string
  taxRate?: string
  taxAmount?: string
  invoiceDate?: string
  verifyStatus: VerifyStatus
  createdBy?: string
  createdAt?: string
  updatedAt?: string
  remark?: string
  sellerName?: string
  buyerName?: string
  buyerTaxNo?: string
  sellerTaxNo?: string
  exceptionStatus?: 'NORMAL' | 'SUSPECT' | 'REJECTED' | 'PENDING_CREDIT'
  exceptionReason?: string
}

export interface InvoicePaymentAllocationVO {
  id?: string
  invoiceId?: string
  payRecordId: string
  payApplicationId?: string
  allocatedAmount: string
  createdAt?: string
}

/** Invoice recognition result (from PDF extraction) */
export interface InvoiceRecognizeResultVO {
  invoiceNo?: string
  invoiceType?: string
  invoiceAmount?: string
  taxRate?: string
  taxAmount?: string
  invoiceDate?: string
  sellerName?: string
  buyerName?: string
  buyerTaxNo?: string
  sellerTaxNo?: string
  remark?: string
  confidence?: string
}

/** Pay record brief (for dropdown selector) */
export interface PayRecordBrief {
  id: string
  payApplicationId?: string
  contractId?: string
  payAmount?: string
  payDate?: string
  voucherNo?: string
  remark?: string
}
