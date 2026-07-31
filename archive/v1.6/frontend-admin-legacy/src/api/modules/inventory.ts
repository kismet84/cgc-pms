import { request } from '@/api/request'
import type { PageResult } from '@/types/api'
import type {
  WarehouseVO,
  StockLedgerVO,
  PurchaseRequestVO,
  PurchaseRequestItemVO,
  WarehouseQuery,
  StockTransactionParams,
  StockLedgerQuery,
  PurchaseRequestQuery,
  StockKpiVO,
  MatStockVO,
  StockTransferCandidateVO,
  StockTransferParams,
  StockTransferVO,
  StockIncomingSupplyVO,
  StockConsumptionBaselineVO,
} from '@/types/inventory'

// ── 仓库 CRUD ──

/** 仓库分页列表 */
export function getWarehouseList(params: WarehouseQuery) {
  return request<PageResult<WarehouseVO>>({
    url: '/inventory/warehouses',
    method: 'get',
    params,
  })
}

/** 仓库详情 */
export function getWarehouseDetail(id: string) {
  return request<WarehouseVO>({
    url: `/inventory/warehouses/${id}`,
    method: 'get',
  })
}

/** 新建仓库 */
export function createWarehouse(data: Partial<WarehouseVO>) {
  return request<string>({
    url: '/inventory/warehouses',
    method: 'post',
    data,
  })
}

/** 更新仓库 */
export function updateWarehouse(id: string, data: Partial<WarehouseVO>) {
  return request<void>({
    url: `/inventory/warehouses/${id}`,
    method: 'put',
    data,
  })
}

/** 更新仓库状态 */
export function updateWarehouseStatus(id: string, status: string) {
  return request<void>({
    url: `/inventory/warehouses/${id}/status`,
    method: 'put',
    params: { status },
  })
}

/** 删除仓库 */
export function deleteWarehouse(id: string) {
  return request<void>({
    url: `/inventory/warehouses/${id}`,
    method: 'delete',
  })
}

// ── 出入库操作 ──

/** 入库 */
export function stockIn(params: StockTransactionParams) {
  return request<void>({
    url: '/inventory/stock/in',
    method: 'post',
    data: params,
  })
}

/** 出库 */
export function stockOut(params: StockTransactionParams) {
  return request<void>({
    url: '/inventory/stock/out',
    method: 'post',
    data: params,
  })
}

// ── 库存台账 ──

/** 库存台账（当前库存 + 分页流水） */
export function getStockLedger(params: StockLedgerQuery) {
  return request<StockLedgerVO>({
    url: '/inventory/stock/ledger',
    method: 'get',
    params,
  })
}

/** 库存 KPI 统计 */
export function getStockKpi(params?: { warehouseId?: string; projectId?: string }) {
  return request<StockKpiVO>({
    url: '/inventory/stock/kpi',
    method: 'get',
    params,
  })
}

/** 查询当前库存项在同项目其他启用仓库的可调拨余量快照 */
export function getStockTransferCandidates(id: string) {
  return request<StockTransferCandidateVO[]>({
    url: `/inventory/stock/${id}/transfer-candidates`,
    method: 'get',
  })
}

/** 原子提交同项目同物料跨仓调拨 */
export function createStockTransfer(data: StockTransferParams) {
  return request<StockTransferVO>({
    url: '/inventory/stock/transfers',
    method: 'post',
    data,
  })
}

/** 查询当前库存项的已审批采购订单未收货余量快照 */
export function getStockIncomingSupplies(id: string) {
  return request<StockIncomingSupplyVO[]>({
    url: `/inventory/stock/${id}/incoming-supplies`,
    method: 'get',
  })
}

/** 查询当前库存项近 30/90 个日历日的历史净领料事实 */
export function getStockConsumptionBaseline(id: string) {
  return request<StockConsumptionBaselineVO>({
    url: `/inventory/stock/${id}/consumption-baseline`,
    method: 'get',
  })
}

/** 维护当前库存项安全库存阈值 */
export function updateStockSafetyThreshold(id: string, safetyStockQty: string) {
  return request<MatStockVO>({
    url: `/inventory/stock/${id}/safety-threshold`,
    method: 'put',
    data: { safetyStockQty },
  })
}

/** 原子维护当前库存项安全阈值与可选人工补货目标量 */
export function updateStockReplenishmentSettings(
  id: string,
  safetyStockQty: string,
  replenishmentTargetQty: string | null,
  replenishmentLeadDays: number | null,
) {
  return request<MatStockVO>({
    url: `/inventory/stock/${id}/replenishment-settings`,
    method: 'put',
    data: { safetyStockQty, replenishmentTargetQty, replenishmentLeadDays },
  })
}

// ── 采购申请 ──

/** 采购申请分页列表 */
export function getPurchaseRequestList(params: PurchaseRequestQuery) {
  return request<PageResult<PurchaseRequestVO>>({
    url: '/purchase-requests',
    method: 'get',
    params,
  })
}

/** 采购申请详情 */
export function getPurchaseRequestDetail(id: string) {
  return request<PurchaseRequestVO>({
    url: `/purchase-requests/${id}`,
    method: 'get',
  })
}

/** 新建采购申请 */
export function createPurchaseRequest(data: Partial<PurchaseRequestVO>) {
  return request<string>({
    url: '/purchase-requests',
    method: 'post',
    data,
  })
}

/** 更新采购申请 */
export function updatePurchaseRequest(id: string, data: Partial<PurchaseRequestVO>) {
  return request<void>({
    url: `/purchase-requests/${id}`,
    method: 'put',
    data,
  })
}

/** 删除采购申请 */
export function deletePurchaseRequest(id: string) {
  return request<void>({
    url: `/purchase-requests/${id}`,
    method: 'delete',
  })
}

/** 提交采购申请审批 */
export function submitPurchaseRequest(id: string) {
  return request<void>({
    url: `/purchase-requests/${id}/submit`,
    method: 'post',
  })
}

/** 采购申请明细列表 */
export function getPurchaseRequestItems(id: string) {
  return request<PurchaseRequestItemVO[]>({
    url: `/purchase-requests/${id}/items`,
    method: 'get',
  })
}

/** 批量保存采购申请明细 */
export function savePurchaseRequestItems(id: string, items: PurchaseRequestItemVO[]) {
  return request<void>({
    url: `/purchase-requests/${id}/items/batch`,
    method: 'post',
    data: items,
  })
}
