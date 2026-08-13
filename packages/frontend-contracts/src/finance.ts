export * from "./finance/types";
export * from "./finance/revenue";
export * from "./finance/applications";
export * from "./finance/cashbook";
export * from "./finance/operations";
export * from "./finance/payment-trace";
export * from "./finance/control";

export const FINANCE_QUERY_PERMISSIONS = {
  payment: "payment:app:query",
  expense: "expense:query",
  revenue: "revenue:operations:query",
  invoice: "invoice:query",
  operations: "finance:operations:query",
  journal: "cashbook:journal:query",
  forecast: "finance:forecast:query",
  accounting: "accounting:query",
  close: "finance:close:query",
} as const;

export const FINANCE_API = {
  payments: "/pay-applications",
  expenses: "/expenses",
  contractRevenues: "/revenue-operations/settlement-revenue-options",
  revenueSettlements: "/revenue-operations/settlements",
  invoices: "/invoices",
  revenueReceivables: "/revenue-operations/receivables",
  revenueSalesInvoices: "/revenue-operations/sales-invoices",
  revenueCollections: "/revenue-operations/collections",
  schedules: "/finance-operations/schedules",
  journal: "/cash-journal-entries",
  forecastCycles: "/cash-forecasts/cycles",
  accountingEntries: "/accounting-entry",
  periods: "/financial-close/periods",
} as const;

export const FINANCE_DECIMAL_FIELDS = {
  payment: ["applyAmount", "approvedAmount", "actualPayAmount"],
  expense: ["amount", "convertedAmount", "paidAmount", "availableToConvert"],
  invoice: ["invoiceAmount", "taxRate", "taxAmount"],
  journal: ["amount", "runningBalance"],
  accounting: ["totalDebit", "totalCredit"],
} as const;
