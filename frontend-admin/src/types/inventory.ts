import type { PageResult } from './api'

/** 仓库 */
export interface WarehouseVO {
  id: string
  tenantId: string
  projectId: string
  warehouseCode: string
  warehouseName: string
  status: string
  createdBy?: string
  createdAt?: string
  updatedAt?: string
  remark?: string
  projectName?: string
}

/** 库存余额 */
export interface MatStockVO {
  id: string
  tenantId: string
  warehouseId: string
  materialId: string
  availableQty: string
  safetyStockQty: string
  replenishmentTargetQty?: string | null
  replenishmentLeadDays?: number | null
  version: number
  createdTime?: string
  updatedTime?: string
  warehouseName?: string
  materialName?: string
  materialCode?: string
  unit?: string
}

/** 同项目其他仓库可调拨余量（查询快照，不预占） */
export interface StockTransferCandidateVO {
  warehouseId: string
  warehouseName: string
  availableQty: string
  safetyStockQty: string
  transferableQty: string
}

/** 已审批采购订单尚未收货的数量（查询快照，尚未入库） */
export interface StockIncomingSupplyVO {
  orderId: string
  orderCode: string
  deliveryDate: string
  remainingQty: string
}

/** 库存流水 */
export interface MatStockTxnVO {
  id: string
  tenantId: string
  warehouseId: string
  materialId: string
  txnType: string
  quantity: string
  availableAfter: string
  sourceType?: string
  sourceId?: string
  createdTime?: string
  materialName?: string
  warehouseName?: string
}

/** 库存台账（仓库+物料维度的当前库存+流水） */
export interface StockLedgerVO {
  stock: MatStockVO | null
  txns: PageResult<MatStockTxnVO>
}

/** 采购申请主表 */
export interface PurchaseRequestVO {
  id: string
  tenantId: string
  projectId: string
  projectName?: string
  contractId?: string
  contractName?: string
  purpose?: string
  requestCode: string
  approvalStatus: string
  status: string
  createdBy?: string
  createdTime?: string
  updatedTime?: string
  remark?: string
  items?: PurchaseRequestItemVO[]
}

/** 采购申请明细 */
export interface PurchaseRequestItemVO {
  id: string
  tenantId: string
  requestId: string
  materialId: string
  materialName?: string
  budgetLineId?: string
  subTaskId?: string
  quantity: string
  estimatedUnitPrice?: string
  estimatedAmount?: string
  unit?: string
  plannedDate?: string
  createdBy?: string
  createdTime?: string
  updatedTime?: string
  remark?: string
}

/** 仓库查询参数 */
export interface WarehouseQuery {
  pageNo?: number
  pageSize?: number
  projectId?: string
  warehouseCode?: string
  warehouseName?: string
  status?: string
}

/** 入库/出库参数 */
export interface StockTransactionParams {
  warehouseId: string
  materialId: string
  quantity: string
  sourceType?: string
  sourceId?: string
}

/** 库存台账查询参数 */
export interface StockLedgerQuery {
  warehouseId: string
  materialId: string
  projectId?: string
  keyword?: string
  sortField?: string
  sortOrder?: 'asc' | 'desc'
  pageNo?: number
  pageSize?: number
}

/** 采购申请查询参数 */
export interface PurchaseRequestQuery {
  pageNum?: number
  pageSize?: number
  projectId?: string
  approvalStatus?: string
  status?: string
  requestCode?: string
}

/** 库存 KPI VO */
export interface StockKpiVO {
  warehouseCount: number
  lowStockCount: number
  txnInCount: number
  txnOutCount: number
  materialTypeCount: number
}
