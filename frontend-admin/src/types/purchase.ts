export interface MatPurchaseOrderVO {
  id: string
  tenantId: string
  projectId: string
  contractId?: string
  partnerId?: string
  requestId?: string
  orderCode: string
  orderType?: string
  orderDate?: string
  deliveryDate?: string
  deliveryTerms?: string
  exceptionPurchaseFlag?: number
  exceptionReason?: string
  totalAmount?: string
  approvalStatus?: string
  orderStatus?: string
  projectName?: string
  contractName?: string
  partnerName?: string
  items?: MatPurchaseOrderItemVO[]
  createdBy?: string
  createdAt?: string
  remark?: string
}

export interface MatPurchaseOrderItemVO {
  id: string
  orderId: string
  requestItemId?: string
  budgetLineId?: string
  materialId?: string
  materialName?: string
  specification?: string
  unit?: string
  quantity?: string
  unitPrice?: string
  taxRate?: string
  amount?: string
  taxAmount?: string
  amountWithoutTax?: string
  receivedQuantity?: string
}

export interface PurchaseOrderQuery {
  pageNum?: number
  pageSize?: number
  projectId?: string
  contractId?: string
  partnerId?: string
  orderStatus?: string
  orderType?: string
  orderCode?: string
}
