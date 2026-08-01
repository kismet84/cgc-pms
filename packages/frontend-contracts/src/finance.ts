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
  revenueReceivables: "/revenue-operations/receivables",
  revenueSalesInvoices: "/revenue-operations/sales-invoices",
  revenueCollections: "/revenue-operations/collections",
  schedules: "/finance-operations/schedules",
  journal: "/cash-journal-entries",
  forecastCycles: "/cash-forecasts/cycles",
  accountingEntries: "/accounting-entry",
  periods: "/financial-close/periods",
} as const;

export interface OwnerSettlementRecord {
  id: string;
  projectId: string;
  contractId: string;
  revenueId?: string | null;
  customerId: string;
  settlementCode: string;
  settlementPeriod: string;
  settlementDate: string;
  grossAmount: FinanceDecimalString;
  taxAmount: FinanceDecimalString;
  retentionAmount: FinanceDecimalString;
  netReceivableAmount: FinanceDecimalString;
  dueDate: string;
  status: string;
  attachmentCount: number;
  approvalInstanceId?: string | null;
  formulaVersion: string;
  version: string;
  remark?: string | null;
}
export interface ReceivableRecord {
  id: string;
  projectId: string;
  contractId: string;
  settlementId: string;
  customerId: string;
  receivableCode: string;
  receivableType: string;
  originalAmount: FinanceDecimalString;
  collectedAmount: FinanceDecimalString;
  creditedAmount: FinanceDecimalString;
  outstandingAmount: FinanceDecimalString;
  dueDate: string;
  status: string;
  overdue: boolean;
  version: string;
}
export interface SalesInvoiceRecord {
  id: string;
  projectId: string;
  contractId: string;
  customerId: string;
  invoiceCode?: string | null;
  invoiceNo: string;
  invoiceType: string;
  invoiceDate: string;
  amountWithoutTax: FinanceDecimalString;
  taxAmount: FinanceDecimalString;
  totalAmount: FinanceDecimalString;
  allocatedAmount: FinanceDecimalString;
  status: string;
  verificationStatus: string;
  attachmentCount: number;
  version: string;
  remark?: string | null;
}
export interface CollectionRecord {
  id: string;
  projectId: string;
  contractId: string;
  customerId: string;
  fundAccountId: string;
  collectionCode: string;
  externalTxnNo: string;
  collectedAt: string;
  amount: FinanceDecimalString;
  allocatedAmount: FinanceDecimalString;
  unallocatedAmount: FinanceDecimalString;
  payerName: string;
  status: string;
  attachmentCount: number;
  version: string;
  remark?: string | null;
}
export type RevenueRecord =
  | OwnerSettlementRecord
  | ReceivableRecord
  | SalesInvoiceRecord
  | CollectionRecord;
export interface RevenueQuery {
  projectId?: string;
  status?: string;
}
export interface AmountAllocation {
  receivableId: string;
  amount: FinanceDecimalString;
}
export interface OwnerSettlementCommand {
  projectId: string;
  contractId: string;
  revenueId?: string;
  settlementPeriod: string;
  settlementDate: string;
  grossAmount: FinanceDecimalString;
  taxAmount: FinanceDecimalString;
  retentionAmount: FinanceDecimalString;
  dueDate: string;
  customerId: string;
  attachmentCount: number;
  remark?: string;
}
export interface SalesInvoiceCommand {
  projectId: string;
  contractId: string;
  customerId: string;
  invoiceCode?: string;
  invoiceNo: string;
  invoiceType: string;
  invoiceDate: string;
  amountWithoutTax: FinanceDecimalString;
  taxAmount: FinanceDecimalString;
  attachmentCount: number;
  allocations: AmountAllocation[];
  remark?: string;
}
export interface CollectionCommand {
  projectId: string;
  contractId: string;
  customerId: string;
  fundAccountId: string;
  externalTxnNo: string;
  collectedAt: string;
  amount: FinanceDecimalString;
  payerName: string;
  attachmentCount: number;
  allocations?: AmountAllocation[];
  remark?: string;
}
export interface PaymentApplicationCommand {
  projectId: string;
  contractId: string;
  partnerId: string;
  costSubjectId: string;
  budgetLineId: string;
  payType: string;
  applyAmount: FinanceDecimalString;
  applyReason?: string;
  expenseCategory?: string;
}
export interface PayRecordWritebackCommand {
  payApplicationId: string;
  payAmount: FinanceDecimalString;
  paidAt: string;
  fundAccountId: string;
  payMethod: string;
  voucherNo?: string;
  externalTxnNo: string;
  remark?: string;
}
export interface ExpenseApplicationCommand {
  projectId: string;
  contractId: string;
  costSubjectId: string;
  budgetLineId: string;
  payeePartnerId: string;
  expenseCategory: string;
  expenseDate: string;
  amount: FinanceDecimalString;
  description: string;
}
export interface InvoiceCommand {
  payRecordId?: string;
  payApplicationId?: string;
  invoiceNo: string;
  invoiceType: string;
  invoiceAmount: FinanceDecimalString;
  taxRate?: FinanceDecimalString;
  taxAmount?: FinanceDecimalString;
  invoiceDate: string;
  sellerName?: string;
  buyerName?: string;
}

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
  costSubjectId?: string | null;
  budgetLineId?: string | null;
  expenseCategory?: string | null;
  applyCode: string;
  applyAmount: FinanceDecimalString;
  approvedAmount: FinanceDecimalString;
  actualPayAmount: FinanceDecimalString;
  payType: string;
  payStatus: string;
  approvalStatus: string;
  applyReason?: string | null;
  integrityVersion: string;
  projectName?: string | null;
  contractName?: string | null;
  partnerName?: string | null;
  createdAt?: string | null;
  updatedAt?: string | null;
}

export type PaymentApplicationPage = PageResult<PaymentApplicationRecord>;

export interface PaymentApplicationBasisRecord {
  id?: string | null;
  payApplicationId?: string | null;
  basisType: string;
  basisId: string;
  basisAmount: FinanceDecimalString;
  remark?: string | null;
}

export interface PaymentApplicationSourceRecord {
  id?: string | null;
  payApplicationId?: string | null;
  sourceType: string;
  sourceRefId: string;
  sourceAmount: FinanceDecimalString;
  paidAmount?: FinanceDecimalString | null;
  remark?: string | null;
}

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
  costSubjectId?: string | null;
  budgetLineId?: string | null;
  payeePartnerId?: string | null;
  expenseCode: string;
  expenseCategory: string;
  expenseDate: string;
  amount: FinanceDecimalString;
  convertedAmount: FinanceDecimalString;
  paidAmount: FinanceDecimalString;
  availableToConvert: FinanceDecimalString;
  status: string;
  approvalStatus: string;
  description?: string | null;
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
  invoiceType?: string | null;
  invoiceAmount: FinanceDecimalString;
  taxRate?: FinanceDecimalString | null;
  taxAmount?: FinanceDecimalString | null;
  invoiceDate?: string | null;
  verifyStatus: string;
  sellerName?: string | null;
  buyerName?: string | null;
  version?: number | null;
}
export interface InvoiceQuery {
  pageNo?: number;
  pageSize?: number;
  projectId?: string;
  invoiceNo?: string;
  verifyStatus?: string;
}

export type InvoicePage = PageResult<InvoiceRecord>;

export interface PayRecordOption {
  id: string;
  payApplicationId?: string | null;
  contractId?: string | null;
  payAmount: FinanceDecimalString;
  payDate?: string | null;
  voucherNo?: string | null;
}

export type PayRecordOptionPage = PageResult<PayRecordOption>;

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
  projectId?: string;
  entryType?: string;
  sourceType?: string;
  startDate?: string;
  endDate?: string;
  entryStatus?: string;
}

export interface AccountingEntryRecord {
  id: string;
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
  postedAt?: string | null;
  reversedAt?: string | null;
  version?: number | null;
}

export type AccountingEntryPage = PageResult<AccountingEntryRecord>;

export interface PaymentScheduleRecord {
  id: string;
  projectId: string;
  contractId: string;
  payApplicationId?: string | null;
  scheduleName: string;
  plannedDate: string;
  plannedAmount: FinanceDecimalString;
  paidAmount: FinanceDecimalString;
  status: string;
}
export interface FinanceAlertRecord {
  id: string;
  projectId?: string | null;
  alertType: string;
  businessType: string;
  businessId: string;
  severity: string;
  dueAt?: string | null;
  status: string;
  message: string;
}
export interface FinanceSnapshotRecord {
  id: string;
  projectId: string;
  projectName?: string | null;
  snapshotDate: string;
  contractAmount: FinanceDecimalString;
  approvedUnpaidAmount: FinanceDecimalString;
  paidAmount: FinanceDecimalString;
  budgetAmount: FinanceDecimalString;
  cashInflow: FinanceDecimalString;
  cashOutflow: FinanceDecimalString;
  actualCost: FinanceDecimalString;
  profitAmount: FinanceDecimalString;
}
export interface FinanceOperationsSummary {
  projectCount: number;
  fundBalance: FinanceDecimalString;
  forecastInflow: FinanceDecimalString;
  forecastOutflow: FinanceDecimalString;
  financingAmount: FinanceDecimalString;
  fundingGap: FinanceDecimalString;
}
export interface FinanceOperationsWorkspace {
  summary: FinanceOperationsSummary;
  schedules: PaymentScheduleRecord[];
  alerts: FinanceAlertRecord[];
  snapshots: FinanceSnapshotRecord[];
}
export interface FundAccountRecord {
  id: string;
  accountCode: string;
  accountName: string;
  accountType: string;
  bankName?: string | null;
  bankAccountNo?: string | null;
  openingDate: string;
  openingBalance: FinanceDecimalString;
  enabledFlag: number;
  version: number;
}
export interface FundAccountCommand {
  accountCode: string;
  accountName: string;
  accountType: "CASH" | "BANK";
  bankName?: string;
  bankAccountNo?: string;
  openingDate: string;
  openingBalance: FinanceDecimalString;
  remark?: string;
}
export interface CashForecastCycleRecord {
  id: string;
  projectId: string;
  projectName?: string | null;
  cycleCode: string;
  forecastName: string;
  asOfDate: string;
  horizonStart: string;
  horizonEnd: string;
  scenario: string;
  openingBalance: FinanceDecimalString;
  status: string;
  versionNo: number;
  previousCycleId?: string | null;
  sourceCutoffAt?: string | null;
}
export interface CashForecastLineRecord {
  id: string;
  cycleId: string;
  forecastDate: string;
  plannedInflow: FinanceDecimalString;
  plannedOutflow: FinanceDecimalString;
  financingAmount: FinanceDecimalString;
  projectedBalance: FinanceDecimalString;
  gapAmount: FinanceDecimalString;
  actualInflow: FinanceDecimalString;
  actualOutflow: FinanceDecimalString;
  inflowVariance: FinanceDecimalString;
  outflowVariance: FinanceDecimalString;
}
export interface CashFundingActionRecord {
  id: string;
  cycleId: string;
  projectId: string;
  lineId: string;
  actionType: string;
  plannedDate: string;
  amount: FinanceDecimalString;
  status: string;
  actualAmount?: FinanceDecimalString | null;
  completionReference?: string | null;
}
export interface CashJournalFactRecord {
  id: string;
  entryNo: string;
  direction: string;
  amount: FinanceDecimalString;
  businessDate: string;
  sourceType: string;
  sourceId?: string | null;
  status: string;
}
export interface CashForecastTrace {
  cycle: CashForecastCycleRecord;
  lines: CashForecastLineRecord[];
  actions: CashFundingActionRecord[];
  actualJournals: CashJournalFactRecord[];
}
export interface AccountingEntryLineRecord {
  id: string;
  lineNo: number;
  direction: string;
  costSubjectId?: string | null;
  costSubjectName?: string | null;
  accountCode?: string | null;
  accountName?: string | null;
  amount: FinanceDecimalString;
  summary: string;
}
export interface AccountingEntryDetail {
  entry: AccountingEntryRecord;
  lines: AccountingEntryLineRecord[];
}
export interface FinancePeriodRecord {
  id: string;
  periodCode: string;
  fiscalYear: number;
  fiscalMonth: number;
  startDate: string;
  endDate: string;
  status: string;
  issueCount: number;
  lastCheckAt?: string | null;
  closedAt?: string | null;
  reopenedAt?: string | null;
  reopenReason?: string | null;
  version: number;
}
export interface PeriodCheckRecord {
  id: string;
  checkType: string;
  status?: string | null;
  issueCount?: number | null;
  detail?: string | null;
}
export interface ReconciliationRecord {
  id: string;
  type: string;
  status: string;
  expectedAmount?: FinanceDecimalString | null;
  actualAmount?: FinanceDecimalString | null;
  differenceAmount?: FinanceDecimalString | null;
  businessId?: string | null;
}
export interface FinancialCloseTrace {
  period: FinancePeriodRecord;
  checks: PeriodCheckRecord[];
  accountReconciliations: ReconciliationRecord[];
  bankReconciliations: ReconciliationRecord[];
  entries: AccountingEntryRecord[];
}
export interface TrialBalanceRecord {
  accountCode: string;
  accountName: string;
  debit: FinanceDecimalString;
  credit: FinanceDecimalString;
}
export interface FinancialStatement {
  period: FinancePeriodRecord;
  trialBalance: TrialBalanceRecord[];
  receivableOutstanding: FinanceDecimalString;
  payableOutstanding: FinanceDecimalString;
  cashFlow: { inflow: FinanceDecimalString; outflow: FinanceDecimalString };
}

export const FINANCE_DECIMAL_FIELDS = {
  payment: ["applyAmount", "approvedAmount", "actualPayAmount"],
  expense: ["amount", "convertedAmount", "paidAmount", "availableToConvert"],
  invoice: ["invoiceAmount", "taxRate", "taxAmount"],
  journal: ["amount", "runningBalance"],
  accounting: ["totalDebit", "totalCredit"],
} as const;
