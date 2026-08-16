import {
  FINANCE_API,
  type CashJournalCreateCommand,
  type CashJournalPage,
  type CashJournalQuery,
  type CashJournalSummary,
  type FundAccountCommand,
  type FundAccountRecord,
} from '@cgc-pms/frontend-contracts'
import { apiRequest } from '@/services/request'
import { requiredId, withQuery } from './support'
import type { BidFundAccountOption } from './types'

export const loadCashJournal = (query: CashJournalQuery = {}, signal?: AbortSignal) =>
  apiRequest<CashJournalPage>(withQuery(FINANCE_API.journal, query), { signal })
export const loadCashJournalSummary = (query: CashJournalQuery = {}, signal?: AbortSignal) =>
  apiRequest<CashJournalSummary>(withQuery(`${FINANCE_API.journal}/summary`, query), { signal })
export const exportCashJournal = (query: CashJournalQuery = {}) =>
  apiRequest<Blob>(withQuery(`${FINANCE_API.journal}/export`, query))
export const createCashJournal = (command: CashJournalCreateCommand) =>
  apiRequest<CashJournalPage['records'][number]>(FINANCE_API.journal, {
    method: 'POST',
    body: command,
  })

export const loadFundAccounts = (signal?: AbortSignal) =>
  apiRequest<FundAccountRecord[]>('/fund-accounts', { signal })
export const loadManagedFundAccounts = (signal?: AbortSignal) =>
  apiRequest<FundAccountRecord[]>('/fund-accounts/manage', { signal })
export const loadBidFundAccountOptions = (signal?: AbortSignal) =>
  apiRequest<BidFundAccountOption[]>('/fund-accounts/bid-options', { signal })
export const createFundAccount = (body: FundAccountCommand) =>
  apiRequest<FundAccountRecord>('/fund-accounts', { method: 'POST', body })
export const updateFundAccount = (id: string, body: FundAccountCommand) =>
  apiRequest<FundAccountRecord>(`/fund-accounts/${requiredId(id)}`, { method: 'PUT', body })
export const archiveCashJournal = (id: string) =>
  apiRequest<CashJournalPage['records'][number]>(
    `/cash-journal-entries/${requiredId(id)}/archive`,
    { method: 'POST' },
  )
export const reopenCashJournal = (id: string, reason: string) =>
  apiRequest<CashJournalPage['records'][number]>(`/cash-journal-entries/${requiredId(id)}/reopen`, {
    method: 'POST',
    body: { reason },
  })
export const reverseCashJournal = (id: string, reason: string) =>
  apiRequest<CashJournalPage['records'][number]>(
    `/cash-journal-entries/${requiredId(id)}/reverse`,
    { method: 'POST', body: { reason } },
  )
