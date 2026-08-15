import type { PageResult } from "../api";
import type { StockTransactionRecord } from "./inventory";
import type { SupplyChainDecimalString } from "./types";

export interface RequisitionQuery {
  pageNo?: number;
  pageSize?: number;
  projectId?: string;
  dateFrom?: string;
  dateTo?: string;
  contractId?: string;
  warehouseId?: string;
  approvalStatus?: string;
  requisitionCode?: string;
}

export interface RequisitionItemRecord {
  id?: string | null;
  requisitionId?: string | null;
  wbsTaskId?: string | null;
  materialId?: string | null;
  materialName?: string | null;
  specification?: string | null;
  unit?: string | null;
  quantity: SupplyChainDecimalString;
  unitPrice?: SupplyChainDecimalString | null;
  amount?: SupplyChainDecimalString | null;
  useLocation?: string | null;
  batchNo?: string | null;
  remark?: string | null;
}

export interface RequisitionRecord {
  id: string;
  tenantId: string;
  projectId: string;
  projectName?: string | null;
  contractId?: string | null;
  contractName?: string | null;
  partnerId?: string | null;
  partnerName?: string | null;
  requisitionCode?: string | null;
  requisitionDate?: string | null;
  warehouseId?: string | null;
  warehouseCode?: string | null;
  warehouseName?: string | null;
  requisitionerId?: string | null;
  approvalStatus?: string | null;
  totalAmount?: SupplyChainDecimalString | null;
  stockOutFlag?: string | null;
  stockOutBy?: string | null;
  stockOutAt?: string | null;
  remark?: string | null;
  items?: RequisitionItemRecord[] | null;
}

export type RequisitionPage = PageResult<RequisitionRecord>;

export interface RequisitionCommand {
  projectId: string;
  contractId?: string;
  partnerId?: string;
  requisitionDate?: string;
  warehouseId: string;
  requisitionerId?: string;
  remark?: string;
}

export interface MaterialReturnCommand {
  requisitionItemId: string;
  originalStockTxnId: string;
  quantity: SupplyChainDecimalString;
  returnDate: string;
  reason: string;
  idempotencyKey: string;
}

export interface MaterialReturnRecord {
  id: string;
  requisitionId: string;
  returnCode: string;
  returnDate: string;
  status: string;
  reason: string;
  totalAmount: SupplyChainDecimalString;
  idempotencyKey: string;
  confirmedAt?: string | null;
  reversedAt?: string | null;
  reversalReason?: string | null;
  version?: number | null;
}

export interface MaterialReturnItemRecord {
  id: string;
  returnId: string;
  requisitionItemId: string;
  originalStockTxnId: string;
  originalCostItemId?: string | null;
  materialId: string;
  quantity: SupplyChainDecimalString;
  unitCost: SupplyChainDecimalString;
  amount: SupplyChainDecimalString;
}

export interface RequisitionTraceRecord {
  requisition: RequisitionRecord;
  requisitionItems: RequisitionItemRecord[];
  stockTransactions: StockTransactionRecord[];
  costs: Array<{
    id: string;
    amount?: SupplyChainDecimalString;
    sourceItemId?: string;
  }>;
  materialReturn?: MaterialReturnRecord | null;
  materialReturnItems: MaterialReturnItemRecord[];
  approvalInstances: Array<{ id: string; status?: string }>;
  approvalRecords: Array<{ id: string; action?: string }>;
}
