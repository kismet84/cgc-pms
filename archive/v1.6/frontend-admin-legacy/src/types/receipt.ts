export interface MatReceiptItemVO {
  id: string
  tenantId: string
  receiptId: string
  orderItemId?: string
  materialId?: string
  materialName?: string
  specification?: string
  unit?: string
  actualQuantity?: string
  qualifiedQuantity?: string
  unqualifiedQuantity?: string
  unitPrice?: string
  amount?: string
  useLocation?: string
  batchNo?: string
  dispositionType?: 'RETURN' | 'REPLACE' | 'CONCESSION'
  dispositionStatus?: 'PENDING' | 'COMPLETED'
  dispositionReason?: string
  orderedQuantity?: string
  receivedQuantity?: string
  remainingQuantity?: string
  createdBy?: string
  createdAt?: string
  updatedAt?: string
  remark?: string
}

export interface MatReceiptVO {
  id: string
  tenantId: string
  projectId: string
  projectName?: string
  orderId?: string
  orderCode?: string
  contractId?: string
  contractName?: string
  partnerId?: string
  partnerName?: string
  receiptCode: string
  receiptDate?: string
  warehouseId?: string
  receiverId?: string
  qualityStatus?: string
  totalAmount?: string
  approvalStatus?: string
  costGeneratedFlag?: number
  items?: MatReceiptItemVO[]
  createdBy?: string
  createdAt?: string
  updatedAt?: string
  remark?: string
}

export interface ReceiptQuery {
  pageNum?: number
  pageSize?: number
  projectId?: string
  orderId?: string
  contractId?: string
  partnerId?: string
  receiptCode?: string
  qualityStatus?: string
}
