import { request } from '@/api/request'
import type { PageResult } from '@/types/api'
import type {
  AccountingEntryDetailVO,
  AccountingEntryQuery,
  AccountingEntryVO,
} from '@/types/accounting'

export function getAccountingEntries(params: AccountingEntryQuery) {
  return request<PageResult<AccountingEntryVO>>({
    url: '/accounting-entry',
    method: 'get',
    params,
  })
}

export function getAccountingEntryDetail(id: string) {
  return request<AccountingEntryDetailVO>({
    url: `/accounting-entry/${id}`,
    method: 'get',
  })
}

export function postAccountingEntry(id: string) {
  return request<void>({
    url: `/accounting-entry/${id}/post`,
    method: 'put',
  })
}

export function reviewAccountingEntry(id: string, approved: boolean, comment?: string) {
  return request<void>({
    url: `/accounting-entry/${id}/review`,
    method: 'put',
    data: { approved, comment },
  })
}

export function resubmitAccountingEntry(id: string) {
  return request<void>({
    url: `/accounting-entry/${id}/resubmit`,
    method: 'put',
  })
}

export function reverseAccountingEntry(id: string, reason: string) {
  return request<string>({
    url: `/accounting-entry/${id}/reverse`,
    method: 'put',
    data: { reason },
  })
}
