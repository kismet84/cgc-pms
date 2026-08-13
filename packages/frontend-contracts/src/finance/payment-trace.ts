import type { FinanceDecimalString } from "./types";

export interface PaymentTraceEntity {
  id?: string;
  [key: string]: unknown;
}

export interface PaymentTraceRecord {
  project: PaymentTraceEntity;
  contract: PaymentTraceEntity;
  paymentApplication: PaymentTraceEntity;
  approvalInstance?: PaymentTraceEntity | null;
  approvalRecords: PaymentTraceEntity[];
  applicationSources: PaymentTraceEntity[];
  expenses: PaymentTraceEntity[];
  settlements: PaymentTraceEntity[];
  settlementSubMeasures: PaymentTraceEntity[];
  subMeasures: PaymentTraceEntity[];
  subTasks: PaymentTraceEntity[];
  paymentRecords: PaymentTraceEntity[];
  paymentSourceAllocations: PaymentTraceEntity[];
  cashJournals: PaymentTraceEntity[];
  paymentDocuments: PaymentTraceEntity[];
  invoices: PaymentTraceEntity[];
  invoiceAllocations: PaymentTraceEntity[];
  budgetLedgers: PaymentTraceEntity[];
  accountingEntries: PaymentTraceEntity[];
  accountingEntryLines: PaymentTraceEntity[];
  contractBudgetAllocation?: PaymentTraceEntity | null;
  projectBudget?: PaymentTraceEntity | null;
  projectBudgetLine?: PaymentTraceEntity | null;
  costSubject?: PaymentTraceEntity | null;
  materialReceiptItems: PaymentTraceEntity[];
  materialReceipts: PaymentTraceEntity[];
  budgetConservation: Record<string, FinanceDecimalString>;
}
