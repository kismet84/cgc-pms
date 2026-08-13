import type { PageResult } from "../api";
import type { FinanceDecimalString } from "./types";

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
  bidCostId?: string;
  costSubjectId?: string;
  bidDepositId?: string;
  costSubjectRootCode?: string;
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
  bidCostId?: string | null;
  costSubjectId?: string | null;
  bidDepositId?: string | null;
  bidDepositType?: string | null;
  costSubjectCode?: string | null;
  costSubjectName?: string | null;
  costSubjectAccountCategory?: string | null;
  counterpartyName?: string | null;
  summary?: string | null;
  sourceType: string;
  sourceId?: string | null;
  status: string;
  reverseOfEntryId?: string | null;
  reversalEntryId?: string | null;
  version?: number | null;
  createdAt?: string | null;
  createdBy?: string | null;
  archivedBy?: string | null;
  archivedAt?: string | null;
  attachmentCount?: number;
}

export type CashJournalPage = PageResult<CashJournalRecord>;

export interface CashJournalCreateCommand {
  accountId?: string | null;
  direction: "IN" | "OUT";
  amount: FinanceDecimalString;
  businessDate: string;
  counterpartyName?: string | null;
  summary: string;
  projectId?: string | null;
  contractId?: string | null;
  bidCostId?: string | null;
  costSubjectId?: string | null;
  bidDepositId?: string | null;
}

export interface CashJournalSummary {
  cashBalance: FinanceDecimalString;
  bankBalance: FinanceDecimalString;
  income: FinanceDecimalString;
  expense: FinanceDecimalString;
  pendingCount: number;
  cumulativeCashOut: FinanceDecimalString;
  cumulativeCashIn: FinanceDecimalString;
  outstandingDeposit: FinanceDecimalString;
  actualBidExpense: FinanceDecimalString;
  cashNetOutflow: FinanceDecimalString;
}
