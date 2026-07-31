export interface MatRequisitionItemVO {
  id?: string
  tenantId?: string
  requisitionId?: string
  materialId?: string
  materialName?: string
  specification?: string
  unit?: string
  quantity?: string
  unitPrice?: string
  amount?: string
  useLocation?: string
  batchNo?: string
  createdBy?: string
  createdAt?: string
  updatedAt?: string
  remark?: string
}

export interface MatRequisitionVO {
  id?: string
  tenantId?: string
  projectId?: string
  projectName?: string
  contractId?: string
  contractName?: string
  partnerId?: string
  partnerName?: string
  requisitionCode?: string
  requisitionDate?: string
  warehouseId?: string
  requisitionerId?: string
  approvalStatus?: string
  totalAmount?: string
  stockOutFlag?: number
  createdBy?: string
  createdAt?: string
  updatedAt?: string
  remark?: string
  items?: MatRequisitionItemVO[]
}

export interface RequisitionQuery {
  pageNo?: number
  pageSize?: number
  projectId?: string
  contractId?: string
  warehouseId?: string
  approvalStatus?: string
  requisitionCode?: string
}
