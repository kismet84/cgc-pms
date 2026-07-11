import type { SysFileVO } from '@/types/file'

export type CashJournalDirection = 'IN' | 'OUT'
export type CashJournalStatus = 'DRAFT' | 'PENDING_ARCHIVE' | 'ARCHIVED' | 'REVERSED'
export type CashJournalSourceType = 'MANUAL' | 'PAY_RECORD' | 'REVERSAL'
export type FundAccountType = 'CASH' | 'BANK'

export interface CashJournalQuery extends Record<string, string | number | boolean | undefined> {
  pageNo?: number
  pageSize?: number
  accountId?: string
  direction?: CashJournalDirection
  status?: CashJournalStatus
  sourceType?: CashJournalSourceType
  sourceId?: string
  projectId?: string
  contractId?: string
  businessDateStart?: string
  businessDateEnd?: string
  hasAttachment?: boolean
  keyword?: string
}

export interface CashJournalCommand {
  accountId?: string
  direction: CashJournalDirection
  amount: string
  businessDate: string
  counterpartyName?: string
  summary: string
  projectId?: string
  contractId?: string
}

export interface CashJournalChangeLog {
  id: string
  journalEntryId: string
  action: string
  reason?: string
  beforeSnapshot?: string
  afterSnapshot?: string
  operatorId?: string
  createdAt: string
}

export interface CashJournalEntryVO {
  id: string
  entryNo: string
  accountId?: string
  accountName?: string
  accountType?: FundAccountType
  direction: CashJournalDirection
  amount: string
  runningBalance?: string
  businessDate: string
  counterpartyName?: string
  summary: string
  projectId?: string
  contractId?: string
  sourceType: CashJournalSourceType
  sourceId?: string
  status: CashJournalStatus
  closureDueAt?: string
  archivedBy?: string
  archivedAt?: string
  reverseOfEntryId?: string
  reversalEntryId?: string
  version: number
  createdAt: string
  attachmentCount: number
  attachments?: SysFileVO[]
  changeLogs?: CashJournalChangeLog[]
}

export interface CashJournalSummaryVO {
  cashBalance: string
  bankBalance: string
  income: string
  expense: string
  pendingCount: number
}

export interface FundAccountCommand {
  accountCode: string
  accountName: string
  accountType: FundAccountType
  bankName?: string
  bankAccountNo?: string
  openingDate: string
  openingBalance: string
  remark?: string
}

export interface FundAccountVO extends FundAccountCommand {
  id: string
  enabledFlag: number
  version: number
}
