import type { AccountingEntryRecord } from "./operations";
import type { FinanceDecimalString } from "./types";

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
