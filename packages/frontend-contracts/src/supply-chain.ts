import type { PageResult } from "./api";

export type SupplyChainDecimalString = string;

export const SUPPLY_CHAIN_QUERY_PERMISSIONS = {
  supplierSourcing: "supplier:sourcing:query",
  purchaseRequest: "purchase:request:list",
  purchaseOrder: "purchase:order:query",
  receipt: "receipt:query",
  warehouse: "inventory:warehouse:list",
  stock: "inventory:stock:list",
  transaction: "inventory:transaction:list",
  requisition: "requisition:query",
} as const;

export const SUPPLY_CHAIN_API = {
  supplierSourcingEvents: "/supplier-sourcing/events",
  supplierSourcingQuotes: "/supplier-sourcing/quotes",
  supplierSourcingEvaluations: "/supplier-sourcing/evaluations",
  supplierPerformance: "/supplier-sourcing/performance",
  supplierReturns: "/supplier-sourcing/returns",
  supplierBlacklists: "/supplier-sourcing/blacklists",
  purchaseRequests: "/purchase-requests",
  purchaseOrders: "/purchase-orders",
  receipts: "/receipts",
  warehouses: "/inventory/warehouses",
  stockLedger: "/inventory/stock/ledger",
  stockKpi: "/inventory/stock/kpi",
  requisitions: "/requisitions",
  materials: "/materials",
} as const;

export const SUPPLIER_SOURCING_PERMISSIONS = {
  query: "supplier:sourcing:query",
  maintain: "supplier:sourcing:maintain",
  quote: "supplier:sourcing:quote",
  evaluate: "supplier:sourcing:evaluate",
  award: "supplier:sourcing:award",
  performance: "supplier:performance:evaluate",
  blacklistReview: "supplier:blacklist:review",
} as const;

export const PURCHASE_EXECUTION_PERMISSIONS = {
  requestQuery: "purchase:request:list",
  requestAdd: "purchase:request:add",
  requestEdit: "purchase:request:edit",
  requestDelete: "purchase:request:delete",
  requestSubmit: "purchase:request:submit",
  orderQuery: "purchase:order:query",
  orderAdd: "purchase:order:add",
  orderEdit: "purchase:order:edit",
  orderDelete: "purchase:order:delete",
  orderSubmit: "purchase:order:submit",
  receiptQuery: "receipt:query",
  receiptAdd: "receipt:add",
  receiptEdit: "receipt:edit",
  receiptDelete: "receipt:delete",
  receiptSubmit: "receipt:submit",
} as const;

export const INVENTORY_WORKSPACE_PERMISSIONS = {
  warehouseQuery: "inventory:warehouse:list",
  warehouseAdd: "inventory:warehouse:add",
  warehouseEdit: "inventory:warehouse:edit",
  warehouseDelete: "inventory:warehouse:delete",
  stockQuery: "inventory:stock:list",
  stockEdit: "inventory:stock:edit",
  transactionQuery: "inventory:transaction:list",
  transactionAdd: "inventory:transaction:add",
} as const;

export const REQUISITION_PERMISSIONS = {
  query: "requisition:query",
  add: "requisition:add",
  edit: "requisition:edit",
  delete: "requisition:delete",
  submit: "requisition:submit",
  stockOut: "requisition:stock-out",
  return: "requisition:return",
} as const;

export type SourcingEventStatus =
  "DRAFT" | "PUBLISHED" | "EVALUATING" | "AWARDED" | "CONTRACTED" | "CANCELLED";
export type SourcingInvitationStatus =
  "PENDING" | "INVITED" | "DECLINED" | "QUOTED" | "DISQUALIFIED";
export type SupplierQuoteStatus =
  "DRAFT" | "SUBMITTED" | "WINNER" | "LOST" | "INVALID";

export interface SourcingEventRecord {
  id: string;
  projectId: string;
  purchaseRequestId: string;
  sourcingCode: string;
  sourcingTitle: string;
  sourcingType: "INQUIRY" | "TENDER";
  deadline: string;
  currencyCode: string;
  status: SourcingEventStatus;
  awardedQuoteId?: string | null;
  awardedPartnerId?: string | null;
  contractId?: string | null;
  awardReason?: string | null;
  version?: number | null;
}

export interface SourcingSupplierRecord {
  id: string;
  sourcingEventId: string;
  partnerId: string;
  invitationStatus: SourcingInvitationStatus;
  disqualificationReason?: string | null;
}

export interface SupplierQuoteRecord {
  id: string;
  sourcingEventId: string;
  sourcingSupplierId: string;
  partnerId: string;
  quoteCode: string;
  totalAmount: SupplyChainDecimalString;
  taxRate: SupplyChainDecimalString;
  deliveryDays: number;
  validityDate: string;
  commercialTerms: string;
  status: SupplierQuoteStatus;
  version?: number | null;
}

export interface BidEvaluationRecord {
  id: string;
  sourcingEventId: string;
  quoteId: string;
  partnerId: string;
  commercialScore: SupplyChainDecimalString;
  technicalScore: SupplyChainDecimalString;
  deliveryScore: SupplyChainDecimalString;
  qualityScore: SupplyChainDecimalString;
  totalScore: SupplyChainDecimalString;
  evaluationComment: string;
}

export interface SupplierPerformanceRecord {
  id: string;
  projectId: string;
  partnerId: string;
  contractId: string;
  purchaseOrderId: string;
  evaluationCode: string;
  periodStart: string;
  periodEnd: string;
  deliveryScore: SupplyChainDecimalString;
  qualityScore: SupplyChainDecimalString;
  serviceScore: SupplyChainDecimalString;
  commercialScore: SupplyChainDecimalString;
  totalScore: SupplyChainDecimalString;
  grade: "A" | "B" | "C" | "D" | "E";
  evaluationComment: string;
  recommendBlacklist: number;
  status: "DRAFT" | "CONFIRMED";
}

export interface SupplierReturnRecord {
  id: string;
  projectId: string;
  partnerId: string;
  contractId: string;
  purchaseOrderId: string;
  receiptId: string;
  returnCode: string;
  returnDate: string;
  returnQuantity: SupplyChainDecimalString;
  returnAmount: SupplyChainDecimalString;
  reason: string;
  status: "DRAFT" | "CONFIRMED";
}

export interface SupplierBlacklistRecord {
  id: string;
  performanceEvaluationId: string;
  partnerId: string;
  projectId: string;
  actionType: "ADD";
  reason: string;
  status: "DRAFT" | "SUBMITTED" | "APPROVED" | "REJECTED";
  reviewComment?: string | null;
}

export interface SourcingTraceRecord {
  event: SourcingEventRecord;
  purchaseRequest: { id: string; requestCode?: string; requestName?: string };
  invitedSuppliers: SourcingSupplierRecord[];
  quotes: SupplierQuoteRecord[];
  bidEvaluations: BidEvaluationRecord[];
  contract?: {
    id: string;
    contractCode?: string;
    contractName?: string;
  } | null;
  purchaseOrders: Array<{ id: string; orderCode?: string }>;
  receipts: Array<{ id: string; receiptCode?: string }>;
  supplierReturns: SupplierReturnRecord[];
  settlements: Array<{ id: string; settlementCode?: string }>;
  performanceEvaluations: SupplierPerformanceRecord[];
  blacklistRecords: SupplierBlacklistRecord[];
  qualitySafetyFacts: Array<{
    id: string;
    evaluationType?: string;
    score?: SupplyChainDecimalString;
  }>;
}

export interface SourcingEventCommand {
  projectId: string;
  purchaseRequestId: string;
  sourcingCode: string;
  sourcingTitle: string;
  sourcingType: "INQUIRY" | "TENDER";
  deadline: string;
  currencyCode: string;
  remark?: string;
}

export interface SupplierQuoteCommand {
  sourcingEventId: string;
  partnerId: string;
  quoteCode: string;
  totalAmount: SupplyChainDecimalString;
  taxRate: SupplyChainDecimalString;
  deliveryDays: number;
  validityDate: string;
  commercialTerms: string;
  remark?: string;
}

export interface BidEvaluationCommand {
  quoteId: string;
  commercialScore: SupplyChainDecimalString;
  technicalScore: SupplyChainDecimalString;
  deliveryScore: SupplyChainDecimalString;
  qualityScore: SupplyChainDecimalString;
  evaluationComment: string;
}

export interface SupplierReturnCommand {
  receiptId: string;
  returnCode: string;
  returnDate: string;
  returnQuantity: SupplyChainDecimalString;
  returnAmount: SupplyChainDecimalString;
  reason: string;
}

export const SUPPLY_CHAIN_DECIMAL_FIELDS = {
  stock: [
    "availableQty",
    "inventoryValue",
    "averageUnitCost",
    "safetyStockQty",
    "replenishmentTargetQty",
  ],
  transaction: ["quantity", "availableAfter", "unitCost", "amount"],
  purchaseRequest: ["totalAmount"],
  purchaseOrder: ["totalAmount"],
  purchaseOrderItem: [
    "quantity",
    "unitPrice",
    "taxRate",
    "amount",
    "taxAmount",
    "amountWithoutTax",
    "receivedQuantity",
  ],
  receipt: ["totalAmount"],
  receiptItem: [
    "actualQuantity",
    "qualifiedQuantity",
    "unqualifiedQuantity",
    "unitPrice",
    "amount",
    "orderedQuantity",
    "receivedQuantity",
    "remainingQuantity",
  ],
} as const;

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
  warehouseCode: string;
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
  status?: string | null;
}

export type MaterialPage = PageResult<MaterialRecord>;

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

export interface RequisitionQuery {
  pageNo?: number;
  pageSize?: number;
  projectId?: string;
  contractId?: string;
  warehouseId?: string;
  approvalStatus?: string;
  requisitionCode?: string;
}

export interface RequisitionItemRecord {
  id?: string | null;
  requisitionId?: string | null;
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

export interface PurchaseOrderQuery {
  pageNum?: number;
  pageSize?: number;
  projectId?: string;
  contractId?: string;
  partnerId?: string;
  orderStatus?: string;
  orderType?: string;
  orderCode?: string;
}

export interface PurchaseRequestQuery {
  pageNum?: number;
  pageSize?: number;
  projectId?: string;
  approvalStatus?: string;
  status?: string;
  requestCode?: string;
}

export interface PurchaseRequestRecord {
  id: string;
  tenantId: string;
  projectId: string;
  projectName?: string | null;
  contractId?: string | null;
  contractName?: string | null;
  purpose?: string | null;
  requestCode?: string | null;
  totalAmount?: SupplyChainDecimalString | null;
  approvalStatus?: string | null;
  status?: string | null;
  createdBy?: string | null;
  createdTime?: string | null;
  updatedTime?: string | null;
  remark?: string | null;
  items?: PurchaseRequestItemRecord[] | null;
}

export interface PurchaseRequestItemRecord {
  id?: string | null;
  requestId?: string | null;
  requestCode?: string | null;
  materialId?: string | null;
  materialName?: string | null;
  wbsTaskId?: string | null;
  budgetLineId?: string | null;
  subTaskId?: string | null;
  quantity: SupplyChainDecimalString;
  estimatedUnitPrice?: SupplyChainDecimalString | null;
  estimatedAmount?: SupplyChainDecimalString | null;
  unit?: string | null;
  plannedDate?: string | null;
  remark?: string | null;
}

export type PurchaseRequestPage = PageResult<PurchaseRequestRecord>;

export interface PurchaseRequestCommand {
  projectId: string;
  contractId?: string;
  purpose?: string;
  remark?: string;
}

export interface PurchaseOrderCommand {
  projectId: string;
  requestId?: string;
  contractId?: string;
  partnerId?: string;
  orderType?: string;
  orderDate?: string;
  deliveryDate?: string;
  deliveryTerms?: string;
  exceptionPurchaseFlag?: number;
  exceptionReason?: string;
  remark?: string;
}

export interface PurchaseOrderItemRecord {
  id?: string | null;
  orderId?: string | null;
  requestItemId?: string | null;
  wbsTaskId?: string | null;
  budgetLineId?: string | null;
  projectId?: string | null;
  materialId?: string | null;
  materialName?: string | null;
  specification?: string | null;
  unit?: string | null;
  quantity: SupplyChainDecimalString;
  unitPrice: SupplyChainDecimalString;
  taxRate?: SupplyChainDecimalString | null;
  amount?: SupplyChainDecimalString | null;
  taxAmount?: SupplyChainDecimalString | null;
  amountWithoutTax?: SupplyChainDecimalString | null;
  receivedQuantity?: SupplyChainDecimalString | null;
  remark?: string | null;
}

export interface PurchaseOrderRecord {
  id: string;
  tenantId: string;
  projectId: string;
  orderCode: string;
  contractId?: string | null;
  partnerId?: string | null;
  requestId?: string | null;
  orderType?: string | null;
  orderDate?: string | null;
  deliveryDate?: string | null;
  totalAmount?: SupplyChainDecimalString | null;
  approvalStatus?: string | null;
  orderStatus?: string | null;
  projectName?: string | null;
  contractName?: string | null;
  partnerName?: string | null;
  createdAt?: string | null;
  remark?: string | null;
}

export type PurchaseOrderPage = PageResult<PurchaseOrderRecord>;

export interface ReceiptQuery {
  pageNum?: number;
  pageSize?: number;
  projectId?: string;
  orderId?: string;
  contractId?: string;
  partnerId?: string;
  receiptCode?: string;
  qualityStatus?: string;
}

export interface ReceiptRecord {
  id: string;
  tenantId: string;
  projectId: string;
  receiptCode: string;
  projectName?: string | null;
  orderId?: string | null;
  orderCode?: string | null;
  contractId?: string | null;
  contractName?: string | null;
  partnerId?: string | null;
  partnerName?: string | null;
  receiptDate?: string | null;
  warehouseId?: string | null;
  qualityStatus?: string | null;
  totalAmount?: SupplyChainDecimalString | null;
  approvalStatus?: string | null;
  createdAt?: string | null;
  updatedAt?: string | null;
  remark?: string | null;
}

export type ReceiptPage = PageResult<ReceiptRecord>;

export interface ReceiptCommand {
  projectId: string;
  orderId: string;
  contractId?: string;
  partnerId?: string;
  receiptDate?: string;
  warehouseId?: string;
  receiverId?: string;
  receiptMode?: "INVENTORY" | "DIRECT_CONSUMPTION";
  qualityStatus?: string;
  remark?: string;
}

export interface ReceiptItemRecord {
  id?: string | null;
  receiptId?: string | null;
  orderItemId?: string | null;
  materialId?: string | null;
  materialName?: string | null;
  wbsTaskId?: string | null;
  budgetLineId?: string | null;
  specification?: string | null;
  unit?: string | null;
  actualQuantity: SupplyChainDecimalString;
  qualifiedQuantity: SupplyChainDecimalString;
  unqualifiedQuantity: SupplyChainDecimalString;
  unitPrice?: SupplyChainDecimalString | null;
  amount?: SupplyChainDecimalString | null;
  orderedQuantity?: SupplyChainDecimalString | null;
  receivedQuantity?: SupplyChainDecimalString | null;
  remainingQuantity?: SupplyChainDecimalString | null;
  useLocation?: string | null;
  batchNo?: string | null;
  dispositionType?: string | null;
  dispositionStatus?: string | null;
  dispositionReason?: string | null;
  remark?: string | null;
}
