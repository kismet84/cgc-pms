import { request } from '@/api/request'
import type { PageResult } from '@/types/api'
import type {
  CashJournalCommand,
  CashJournalEntryVO,
  CashJournalQuery,
  CashJournalSummaryVO,
  FundAccountCommand,
  FundAccountVO,
} from '@/types/cashbook'

export function getCashJournalList(params: CashJournalQuery) {
  return request<PageResult<CashJournalEntryVO>>({
    url: '/cash-journal-entries',
    method: 'get',
    params,
  })
}

export function getCashJournalSummary(params: CashJournalQuery) {
  return request<CashJournalSummaryVO>({
    url: '/cash-journal-entries/summary',
    method: 'get',
    params,
  })
}

export function getCashJournalDetail(id: string) {
  return request<CashJournalEntryVO>({
    url: `/cash-journal-entries/${id}`,
    method: 'get',
  })
}

export function createCashJournalEntry(data: CashJournalCommand) {
  return request<CashJournalEntryVO>({
    url: '/cash-journal-entries',
    method: 'post',
    data,
  })
}

export function updateCashJournalEntry(id: string, data: CashJournalCommand) {
  return request<CashJournalEntryVO>({
    url: `/cash-journal-entries/${id}`,
    method: 'put',
    data,
  })
}

export function archiveCashJournalEntry(id: string) {
  return request<CashJournalEntryVO>({
    url: `/cash-journal-entries/${id}/archive`,
    method: 'post',
  })
}

export function reverseCashJournalEntry(id: string, reason: string) {
  return request<CashJournalEntryVO>({
    url: `/cash-journal-entries/${id}/reverse`,
    method: 'post',
    data: { reason },
  })
}

export function reopenCashJournalEntry(id: string, reason: string) {
  return request<CashJournalEntryVO>({
    url: `/cash-journal-entries/${id}/reopen`,
    method: 'post',
    data: { reason },
  })
}

export function exportCashJournal(params: CashJournalQuery) {
  return request<Blob>({
    url: '/cash-journal-entries/export',
    method: 'get',
    params,
    responseType: 'blob',
  })
}

export function getFundAccounts() {
  return request<FundAccountVO[]>({ url: '/fund-accounts', method: 'get' })
}

export function getManageableFundAccounts() {
  return request<FundAccountVO[]>({ url: '/fund-accounts/manage', method: 'get' })
}

export function createFundAccount(data: FundAccountCommand) {
  return request<FundAccountVO>({ url: '/fund-accounts', method: 'post', data })
}

export function updateFundAccount(id: string, data: FundAccountCommand) {
  return request<FundAccountVO>({ url: `/fund-accounts/${id}`, method: 'put', data })
}

export function setFundAccountEnabled(id: string, enabled: boolean) {
  return request<FundAccountVO>({
    url: `/fund-accounts/${id}/enabled`,
    method: 'put',
    params: { enabled },
  })
}
