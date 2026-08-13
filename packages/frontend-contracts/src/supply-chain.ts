export * from "./supply-chain/types";
export * from "./supply-chain/sourcing";
export * from "./supply-chain/inventory";
export * from "./supply-chain/requisition";
export * from "./supply-chain/purchase";

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
  supplierSourcingWorkspace: "/supplier-sourcing/workspace",
  supplierPerformanceCandidates: "/supplier-sourcing/performance-candidates",
  supplierSourcingEvents: "/supplier-sourcing/events",
  supplierSourcingQuotes: "/supplier-sourcing/quotes",
  supplierSourcingEvaluations: "/supplier-sourcing/evaluations",
  supplierPerformance: "/supplier-sourcing/performance",
  supplierReturns: "/supplier-sourcing/returns",
  receiptSupplierReturns: "/supplier-returns",
  supplierBlacklists: "/supplier-sourcing/blacklists",
  purchaseRequests: "/purchase-requests",
  purchaseOrders: "/purchase-orders",
  receipts: "/receipts",
  warehouses: "/inventory/warehouses",
  stocks: "/inventory/stock",
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
  receiptReturn: "receipt:return",
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
    "acceptedQuantity",
    "unitPrice",
    "amount",
    "orderedQuantity",
    "receivedQuantity",
    "remainingQuantity",
  ],
} as const;
