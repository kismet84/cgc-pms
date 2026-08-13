export interface SupplyFormMaterialOption {
  id: string
  materialCode: string
  materialName: string
  specification?: string | null
  unit?: string | null
}

export interface PurchaseRequestFormOptions {
  materials: SupplyFormMaterialOption[]
}

export interface RequisitionFormOptions {
  warehouses: Array<{
    id: string
    warehouseCode: string
    warehouseName: string
    projectId: string
  }>
  materials: SupplyFormMaterialOption[]
  partners: Array<{ id: string; partnerCode: string; partnerName: string }>
  contracts: Array<{ id: string; contractCode: string; contractName: string; projectId: string }>
}

export interface PurchaseRequestApprovalCommand {
  comment?: string
  idempotencyKey: string
  items: Array<{
    itemId: string
    approvedQuantity: string
    approvalVersion: number
    changeReason?: string
  }>
}
