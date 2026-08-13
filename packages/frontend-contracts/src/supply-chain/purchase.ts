import type { PageResult } from "../api";
import type { SupplyChainDecimalString } from "./types";

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
  approvedQuantity?: SupplyChainDecimalString | null;
  approvalVersion?: number | null;
  specification?: string | null;
  unit?: string | null;
  plannedDate?: string | null;
  useLocation?: string | null;
  remark?: string | null;
}

export type PurchaseRequestPage = PageResult<PurchaseRequestRecord>;

export interface PurchaseRequestCommand {
  header: {
    projectId: string;
    remark?: string;
  };
  items: PurchaseRequestItemRecord[];
}

export interface PurchaseOrderPricingSuggestionRecord {
  pricingMode: "FIXED" | "ACTUAL";
  contractItemId: string;
  unitPrice: SupplyChainDecimalString;
  editable: boolean;
  priceSource: "CONTRACT_ITEM" | "RECENT_RECEIPT";
  sourceReceiptItemId?: string | null;
  sourceReceiptDate?: string | null;
}

/**
 * 建立普通采购订单只提交申请、合同及交付条件引用。
 * 服务端按已审批申请快照复制明细并重新读取合同/最近验收定价。
 */
export interface PurchaseOrderFromRequestCommand {
  projectId: string;
  contractId: string;
  requestId: string;
  orderDate?: string;
  deliveryDate?: string;
  deliveryTerms: string;
  remark?: string;
}

export interface PurchaseOrderCommand {
  projectId: string;
  contractId: string;
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
  pricingMode?: "FIXED" | "ACTUAL" | null;
  priceSource?:
    "CONTRACT_ITEM" | "RECENT_RECEIPT" | "MANUAL" | "UNKNOWN" | null;
  priceSourceReceiptItemId?: string | null;
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
  requestCode?: string | null;
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
  deliveryNoteNo?: string | null;
  systemBatchNo?: string | null;
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
  deliveryNoteNo?: string;
  warehouseId?: string;
  receiverId?: string;
  receiptMode?: "INVENTORY" | "DIRECT_CONSUMPTION";
  qualityStatus?: string;
  remark?: string;
}

export interface ReceiptSupplierReturnCommand {
  receiptItemId: string;
  returnKind: "UNQUALIFIED" | "ACCEPTED";
  quantity: SupplyChainDecimalString;
  returnDate: string;
  reason: string;
  idempotencyKey: string;
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
  /** 服务端确认可入账数量；前端不得拆分或推导质量数量。 */
  acceptedQuantity: SupplyChainDecimalString;
  systemBatchNo?: string | null;
  unitPrice?: SupplyChainDecimalString | null;
  amount?: SupplyChainDecimalString | null;
  orderedQuantity?: SupplyChainDecimalString | null;
  receivedQuantity?: SupplyChainDecimalString | null;
  remainingQuantity?: SupplyChainDecimalString | null;
  useLocation?: string | null;
  remark?: string | null;
}
