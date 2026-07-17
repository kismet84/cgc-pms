import { request } from '@/api/request'

export type FinancialRow = Record<string, unknown>
export interface FinancialTrace {
  period: FinancialRow
  checks: FinancialRow[]
  accountReconciliations: FinancialRow[]
  bankReconciliations: FinancialRow[]
  entries: FinancialRow[]
  auditTrail: FinancialRow[]
}

export function getFinancialPeriods(year?: number) {
  return request<FinancialRow[]>({
    url: '/financial-close/periods',
    method: 'get',
    params: { year },
  })
}
export function createFinancialPeriod(fiscalYear: number, fiscalMonth: number) {
  return request<FinancialRow>({
    url: '/financial-close/periods',
    method: 'post',
    data: { fiscalYear, fiscalMonth },
  })
}
export function runFinancialCloseChecks(year: number, month: number) {
  return request<FinancialTrace>({
    url: `/financial-close/periods/${year}/${month}/checks`,
    method: 'post',
  })
}
export function closeFinancialPeriod(year: number, month: number, comment?: string) {
  return request<FinancialTrace>({
    url: `/financial-close/periods/${year}/${month}/close`,
    method: 'post',
    data: { comment },
  })
}
export function reopenFinancialPeriod(year: number, month: number, reason: string) {
  return request<FinancialTrace>({
    url: `/financial-close/periods/${year}/${month}/reopen`,
    method: 'post',
    data: { reason },
  })
}
export function getFinancialCloseTrace(periodId: string) {
  return request<FinancialTrace>({
    url: `/financial-close/periods/${periodId}/trace`,
    method: 'get',
  })
}
export function getFinancialStatements(year: number, month: number) {
  return request<FinancialRow>({
    url: `/financial-close/periods/${year}/${month}/statements`,
    method: 'get',
  })
}
export function createAdjustmentEntry(data: FinancialRow) {
  return request<FinancialRow>({ url: '/financial-close/adjustments', method: 'post', data })
}
export function resolveBankReconciliation(id: string, data: FinancialRow) {
  return request<FinancialRow>({
    url: `/financial-close/bank-reconciliations/${id}/resolve`,
    method: 'post',
    data,
  })
}
