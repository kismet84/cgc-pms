import type { PageResult } from "../api";
import type { SupplyChainDecimalString } from "./types";

export interface WarehouseQuery {
  pageNo?: number;
  pageSize?: number;
  projectId?: string;
  warehouseCode?: string;
  warehouseName?: string;
  status?: string;
}

export interface WarehouseRecord {
  id: string;
  tenantId: string;
  projectId: string;
  warehouseCode: string;
  warehouseName: string;
  status: string;
  projectName?: string | null;
  remark?: string | null;
  createdAt?: string | null;
  updatedAt?: string | null;
}

export interface WarehouseCommand {
  projectId: string;
  warehouseCode?: string;
  warehouseName: string;
  status: "ENABLE" | "DISABLE";
  remark?: string;
}

export type WarehousePage = PageResult<WarehouseRecord>;

export interface MaterialQuery {
  pageNo?: number;
  pageSize?: number;
  materialCode?: string;
  materialName?: string;
  status?: string;
}

export interface MaterialRecord {
  id: string;
  materialCode: string;
  materialName: string;
  specification?: string | null;
  unit?: string | null;
  taxInclusiveInfoPrice?: SupplyChainDecimalString | null;
  infoPricePeriod?: string | null;
  infoPriceSource?: string | null;
  infoPriceVerificationStatus?: string | null;
  infoPriceReviewRequired?: number | null;
  purchasePrice?: SupplyChainDecimalString | null;
  purchasePriceReceiptItemId?: string | null;
  purchasePriceDate?: string | null;
  status?: string | null;
}

export type MaterialPage = PageResult<MaterialRecord>;

export interface StockQuery {
  warehouseId?: string;
  materialId?: string;
  projectId?: string;
  keyword?: string;
  pageNo?: number;
  pageSize?: number;
}

export interface StockLedgerQuery {
  warehouseId?: string;
  materialId: string;
  projectId?: string;
  keyword?: string;
  sortField?: string;
  sortOrder?: "asc" | "desc";
  pageNo?: number;
  pageSize?: number;
}

export interface StockRecord {
  id?: string | null;
  projectId?: string | null;
  projectName?: string | null;
  warehouseId?: string | null;
  materialId: string;
  availableQty: SupplyChainDecimalString;
  inventoryValue: SupplyChainDecimalString;
  averageUnitCost: SupplyChainDecimalString;
  safetyStockQty: SupplyChainDecimalString;
  replenishmentTargetQty?: SupplyChainDecimalString | null;
  replenishmentLeadDays?: number | null;
  warehouseName?: string | null;
  materialName?: string | null;
  materialCode?: string | null;
  unit?: string | null;
  createdTime?: string | null;
  updatedTime?: string | null;
}

export type StockPage = PageResult<StockRecord>;

export interface StockTransactionRecord {
  id: string;
  warehouseId: string;
  materialId: string;
  txnType: string;
  quantity: SupplyChainDecimalString;
  availableAfter: SupplyChainDecimalString;
  unitCost: SupplyChainDecimalString;
  amount: SupplyChainDecimalString;
  sourceType?: string | null;
  sourceId?: string | null;
  sourceCode?: string | null;
  sourceLineId?: string | null;
  materialName?: string | null;
  warehouseName?: string | null;
  createdTime?: string | null;
}

export interface StockLedger {
  stock: StockRecord | null;
  txns: PageResult<StockTransactionRecord>;
}

export interface StockKpiRecord {
  warehouseCount: number;
  lowStockCount: number;
  txnInCount: number;
  txnOutCount: number;
  materialTypeCount: number;
}

export interface StockTransferCandidateRecord {
  stockId: string;
  warehouseId: string;
  warehouseName: string;
  availableQty: SupplyChainDecimalString;
  safetyStockQty: SupplyChainDecimalString;
  transferableQty: SupplyChainDecimalString;
}

export interface StockIncomingSupplyRecord {
  orderId: string;
  orderCode: string;
  deliveryDate?: string | null;
  remainingQty: SupplyChainDecimalString;
}

export interface StockConsumptionBaselineRecord {
  window30Start: string;
  window90Start: string;
  cutoffAt: string;
  grossIssued30: SupplyChainDecimalString;
  returned30: SupplyChainDecimalString;
  netIssued30: SupplyChainDecimalString;
  grossIssued90: SupplyChainDecimalString;
  returned90: SupplyChainDecimalString;
  netIssued90: SupplyChainDecimalString;
}

export interface StockTransferCommand {
  sourceStockId: string;
  targetStockId: string;
  quantity: SupplyChainDecimalString;
  idempotencyKey: string;
  reason: string;
}

export interface StockTransferRecord extends StockTransferCommand {
  id: string;
  projectId: string;
  sourceWarehouseId: string;
  targetWarehouseId: string;
  materialId: string;
  unitCost: SupplyChainDecimalString;
  amount: SupplyChainDecimalString;
  status: string;
  completedAt: string;
}

export interface StockReplenishmentCommand {
  safetyStockQty: SupplyChainDecimalString;
  replenishmentTargetQty: SupplyChainDecimalString | null;
  replenishmentLeadDays: number | null;
}
