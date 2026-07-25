import type { PageResult } from "./api";

export type FinanceDecimalString = string;

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
  revenueSettlements: "/revenue-operations/settlements",
  invoices: "/invoices",
  schedules: "/finance-operations/schedules",
  journal: "/cash-journal-entries",
  forecastCycles: "/cash-forecasts/cycles",
  accountingEntries: "/accounting-entry",
  periods: "/financial-close/periods",
} as const;

export interface PaymentApplicationQuery {
  pageNo?: number;
  pageSize?: number;
  projectId?: string;
  contractId?: string;
  partnerId?: string;
  payStatus?: string;
  approvalStatus?: string;
  applyCode?: string;
}

export interface PaymentApplicationRecord {
  id: string;
  tenantId: string;
  projectId: string;
  contractId?: string | null;
  partnerId?: string | null;
  applyCode: string;
  applyAmount: FinanceDecimalString;
  approvedAmount: FinanceDecimalString;
  actualPayAmount: FinanceDecimalString;
  payType: string;
  payStatus: string;
  approvalStatus: string;
  integrityVersion: string;
  projectName?: string | null;
  contractName?: string | null;
  partnerName?: string | null;
  createdAt?: string | null;
  updatedAt?: string | null;
}

export type PaymentApplicationPage = PageResult<PaymentApplicationRecord>;

export interface ExpenseApplicationQuery {
  pageNo?: number;
  pageSize?: number;
  projectId?: string;
  contractId?: string;
  approvalStatus?: string;
}

export interface ExpenseApplicationRecord {
  id: string;
  projectId: string;
  contractId?: string | null;
  expenseCode: string;
  expenseCategory: string;
  expenseDate: string;
  amount: FinanceDecimalString;
  convertedAmount: FinanceDecimalString;
  paidAmount: FinanceDecimalString;
  availableToConvert: FinanceDecimalString;
  status: string;
  approvalStatus: string;
  version?: number | null;
  createdAt?: string | null;
  updatedAt?: string | null;
}

export type ExpenseApplicationPage = PageResult<ExpenseApplicationRecord>;

export interface InvoiceRecord {
  id: string;
  projectId?: string | null;
  contractId?: string | null;
  payApplicationId?: string | null;
  payRecordId?: string | null;
  invoiceNo: string;
  invoiceAmount: FinanceDecimalString;
  taxRate?: FinanceDecimalString | null;
  taxAmount?: FinanceDecimalString | null;
  verifyStatus: string;
  version?: number | null;
}

export type InvoicePage = PageResult<InvoiceRecord>;

export interface CashJournalQuery {
  pageNo?: number;
  pageSize?: number;
  accountId?: string;
  direction?: string;
  status?: string;
  sourceType?: string;
  sourceId?: string;
  projectId?: string;
  contractId?: string;
  hasAttachment?: boolean;
  businessDateStart?: string;
  businessDateEnd?: string;
  keyword?: string;
}

export interface CashJournalRecord {
  id: string;
  entryNo: string;
  accountId?: string | null;
  accountName?: string | null;
  accountType?: string | null;
  direction: string;
  amount: FinanceDecimalString;
  runningBalance: FinanceDecimalString;
  businessDate: string;
  projectId?: string | null;
  contractId?: string | null;
  sourceType: string;
  sourceId?: string | null;
  status: string;
  reverseOfEntryId?: string | null;
  reversalEntryId?: string | null;
  version?: number | null;
  createdAt?: string | null;
}

export type CashJournalPage = PageResult<CashJournalRecord>;

export interface AccountingEntryQuery {
  pageNo?: number;
  pageSize?: number;
  entryType?: string;
  sourceType?: string;
  startDate?: string;
  endDate?: string;
  entryStatus?: string;
}

export interface AccountingEntryRecord {
  id: string;
  tenantId: string;
  entryCode: string;
  entryDate: string;
  entryType: string;
  sourceType: string;
  sourceId: string;
  projectId?: string | null;
  contractId?: string | null;
  entryStatus: string;
  reviewStatus: string;
  periodId?: string | null;
  totalDebit: FinanceDecimalString;
  totalCredit: FinanceDecimalString;
  originalEntryId?: string | null;
  reversedEntryId?: string | null;
  version?: number | null;
}

export type AccountingEntryPage = PageResult<AccountingEntryRecord>;

export const FINANCE_DECIMAL_FIELDS = {
  payment: ["applyAmount", "approvedAmount", "actualPayAmount"],
  expense: ["amount", "convertedAmount", "paidAmount", "availableToConvert"],
  invoice: ["invoiceAmount", "taxRate", "taxAmount"],
  journal: ["amount", "runningBalance"],
  accounting: ["totalDebit", "totalCredit"],
} as const;
