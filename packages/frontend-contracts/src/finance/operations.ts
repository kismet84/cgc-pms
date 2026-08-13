import type { PageResult } from "../api";
import type { FinanceDecimalString } from "./types";

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
