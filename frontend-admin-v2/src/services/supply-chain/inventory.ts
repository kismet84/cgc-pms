import {
  SUPPLY_CHAIN_API,
  type MaterialPage,
  type MaterialQuery,
  type StockConsumptionBaselineRecord,
  type StockKpiRecord,
  type StockLedger,
  type StockLedgerQuery,
  type StockPage,
  type StockQuery,
  type StockRecord,
  type StockReplenishmentCommand,
  type StockTransferCandidateRecord,
  type StockIncomingSupplyRecord,
  type StockTransferCommand,
  type StockTransferRecord,
  type WarehouseCommand,
  type WarehousePage,
  type WarehouseQuery,
  type WarehouseRecord,
} from '@cgc-pms/frontend-contracts'
import { apiRequest } from '@/services/request'
import {
  createId,
  deleteResource,
  POST_METHOD,
  PUT_METHOD,
  requiredId,
  resourcePath,
  withQuery,
} from './support'

export function loadWarehouses(
  query: WarehouseQuery = {},
  signal?: AbortSignal,
): Promise<WarehousePage> {
  return apiRequest<WarehousePage>(withQuery(SUPPLY_CHAIN_API.warehouses, query), { signal })
}

export function loadMaterials(
  query: MaterialQuery = {},
  signal?: AbortSignal,
): Promise<MaterialPage> {
  return apiRequest<MaterialPage>(withQuery(SUPPLY_CHAIN_API.materials, query), { signal })
}

export function loadWarehouse(id: string, signal?: AbortSignal) {
  return apiRequest<WarehouseRecord>(resourcePath(SUPPLY_CHAIN_API.warehouses, id), { signal })
}

export function createWarehouse(body: WarehouseCommand): Promise<string> {
  return createId(SUPPLY_CHAIN_API.warehouses, body)
}

export function updateWarehouse(id: string, body: WarehouseCommand): Promise<void> {
  return apiRequest<void, WarehouseCommand>(resourcePath(SUPPLY_CHAIN_API.warehouses, id), {
    method: PUT_METHOD,
    body,
  })
}

export function updateWarehouseStatus(id: string, status: 'ENABLE' | 'DISABLE'): Promise<void> {
  return apiRequest<void>(
    withQuery(`${resourcePath(SUPPLY_CHAIN_API.warehouses, id)}/status`, { status }),
    { method: PUT_METHOD },
  )
}

export function deleteWarehouse(id: string): Promise<void> {
  return deleteResource(SUPPLY_CHAIN_API.warehouses, id)
}

export function loadStockLedger(
  query: StockLedgerQuery,
  signal?: AbortSignal,
): Promise<StockLedger> {
  const normalized = {
    ...query,
    materialId: requiredId(query.materialId, '物料ID'),
  }
  return apiRequest<StockLedger>(withQuery(SUPPLY_CHAIN_API.stockLedger, normalized), { signal })
}

export function loadStocks(query: StockQuery = {}, signal?: AbortSignal): Promise<StockPage> {
  return apiRequest<StockPage>(withQuery(SUPPLY_CHAIN_API.stocks, query), { signal })
}

export function loadStockKpi(
  query: { warehouseId?: string; projectId?: string } = {},
  signal?: AbortSignal,
): Promise<StockKpiRecord> {
  return apiRequest<StockKpiRecord>(withQuery(SUPPLY_CHAIN_API.stockKpi, query), { signal })
}

export function loadStockTransferCandidates(id: string, signal?: AbortSignal) {
  return apiRequest<StockTransferCandidateRecord[]>(
    `${resourcePath('/inventory/stock', id)}/transfer-candidates`,
    { signal },
  )
}

export function loadStockIncomingSupplies(id: string, signal?: AbortSignal) {
  return apiRequest<StockIncomingSupplyRecord[]>(
    `${resourcePath('/inventory/stock', id)}/incoming-supplies`,
    { signal },
  )
}

export function loadStockConsumptionBaseline(id: string, signal?: AbortSignal) {
  return apiRequest<StockConsumptionBaselineRecord>(
    `${resourcePath('/inventory/stock', id)}/consumption-baseline`,
    { signal },
  )
}

export function createStockTransfer(body: StockTransferCommand) {
  return apiRequest<StockTransferRecord, StockTransferCommand>('/inventory/stock/transfers', {
    method: POST_METHOD,
    body,
  })
}

export function updateStockReplenishment(id: string, body: StockReplenishmentCommand) {
  return apiRequest<StockRecord, StockReplenishmentCommand>(
    `${resourcePath('/inventory/stock', id)}/replenishment-settings`,
    { method: PUT_METHOD, body },
  )
}
