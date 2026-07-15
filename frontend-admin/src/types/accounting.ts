export type AccountingEntryStatus = 'DRAFT' | 'POSTED' | 'REVERSED'

export interface AccountingEntryQuery {
  pageNo: number
  pageSize: number
  entryType?: string
  sourceType?: string
  startDate?: string
  endDate?: string
  entryStatus?: AccountingEntryStatus
}

export interface AccountingEntryVO {
  id: string
  entryCode: string
  entryDate: string
  entryType: string
  sourceType: string
  sourceId: string
  entryStatus: AccountingEntryStatus
  totalDebit: string
  totalCredit: string
  createdAt?: string
}

export interface AccountingEntryLineVO {
  id: string
  entryId: string
  lineNo: number
  direction: 'DEBIT' | 'CREDIT'
  costSubjectId: string
  amount: string
  summary?: string
}

export interface AccountingEntryDetailVO {
  entry: AccountingEntryVO
  lines: AccountingEntryLineVO[]
  subjectNames: Record<string, string>
}
