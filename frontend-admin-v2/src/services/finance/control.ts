import {
  type AccountingEntryDetail,
  type AccountingEntryPage,
  type AccountingEntryQuery,
  type CashForecastCycleRecord,
  type CashForecastCycleCommand,
  type CashForecastTrace,
  type FinanceOperationsFormOptions,
  type FinanceOperationsWorkspace,
  type FinancePeriodCommand,
  type FinancePeriodRecord,
  type FinancialCloseTrace,
  type FinancialStatement,
  type PaymentScheduleCommand,
} from '@cgc-pms/frontend-contracts'
import { apiRequest } from '@/services/request'
import { requiredId, withQuery } from './support'

export const loadAccountingEntries = (query: AccountingEntryQuery = {}, signal?: AbortSignal) =>
  apiRequest<AccountingEntryPage>(withQuery('/accounting-entry/workspace', query), { signal })
export const loadAccountingEntryDetail = (id: string, signal?: AbortSignal) =>
  apiRequest<AccountingEntryDetail>(`/accounting-entry/workspace/${requiredId(id)}`, { signal })

export const loadFinanceOperationsWorkspace = (projectId?: string, signal?: AbortSignal) =>
  apiRequest<FinanceOperationsWorkspace>(
    withQuery('/finance-operations/workspace', { projectId }),
    { signal },
  )
export const loadFinanceOperationsFormOptions = (projectId: string, signal?: AbortSignal) =>
  apiRequest<FinanceOperationsFormOptions>(
    withQuery('/finance-operations/form-options', { projectId: requiredId(projectId) }),
    { signal },
  )
export const createPaymentSchedule = (body: PaymentScheduleCommand) =>
  apiRequest<void>('/finance-operations/schedules', { method: 'POST', body })
export const rebuildFinanceSnapshot = (projectId: string) =>
  apiRequest<void>(`/finance-operations/snapshots/${requiredId(projectId)}/rebuild`, {
    method: 'POST',
  })
export const generateFinanceAlerts = () =>
  apiRequest<void>('/finance-operations/alerts/generate', { method: 'POST' })
export const handleFinanceAlert = (id: string, status: 'RESOLVED' | 'IGNORED', note: string) =>
  apiRequest<void>(`/finance-operations/alerts/${requiredId(id)}/handle`, {
    method: 'POST',
    body: { status, note },
  })

export const loadCashForecastCycles = (projectId?: string, signal?: AbortSignal) =>
  apiRequest<CashForecastCycleRecord[]>(withQuery('/cash-forecasts/workspace', { projectId }), {
    signal,
  })
export const loadCashForecastTrace = (id: string, signal?: AbortSignal) =>
  apiRequest<CashForecastTrace>(`/cash-forecasts/workspace/${requiredId(id)}`, { signal })
export const createCashForecast = (body: CashForecastCycleCommand) =>
  apiRequest<void>('/cash-forecasts/cycles', { method: 'POST', body })
export const regenerateCashForecast = (id: string) =>
  apiRequest<void>(`/cash-forecasts/cycles/${requiredId(id)}/regenerate`, { method: 'POST' })
export const submitCashForecast = (id: string) =>
  apiRequest<void>(`/cash-forecasts/cycles/${requiredId(id)}/submit`, { method: 'POST' })
export const approveCashForecast = (id: string, approved: boolean, comment: string) =>
  apiRequest<void>(`/cash-forecasts/cycles/${requiredId(id)}/approve`, {
    method: 'POST',
    body: { approved, comment },
  })
export const refreshCashForecastActuals = (id: string) =>
  apiRequest<void>(`/cash-forecasts/cycles/${requiredId(id)}/actuals/refresh`, { method: 'POST' })

export const reviewAccountingEntry = (id: string, approved: boolean, comment?: string) =>
  apiRequest<void>(`/accounting-entry/${requiredId(id)}/review`, {
    method: 'PUT',
    body: { approved, comment },
  })
export const postAccountingEntry = (id: string) =>
  apiRequest<void>(`/accounting-entry/${requiredId(id)}/post`, { method: 'PUT' })
export const resubmitAccountingEntry = (id: string) =>
  apiRequest<void>(`/accounting-entry/${requiredId(id)}/resubmit`, { method: 'PUT' })
export const reverseAccountingEntry = (id: string, reason: string) =>
  apiRequest<void>(`/accounting-entry/${requiredId(id)}/reverse`, {
    method: 'PUT',
    body: { reason },
  })

export const loadFinancePeriods = (year?: number, signal?: AbortSignal) =>
  apiRequest<FinancePeriodRecord[]>(withQuery('/financial-close/workspace', { year }), { signal })
export const createFinancePeriod = (body: FinancePeriodCommand) =>
  apiRequest<void>('/financial-close/periods', { method: 'POST', body })
export const loadFinancialCloseTrace = (id: string, signal?: AbortSignal) =>
  apiRequest<FinancialCloseTrace>(`/financial-close/workspace/${requiredId(id)}`, { signal })
export const loadFinancialStatement = (year: number, month: number, signal?: AbortSignal) =>
  apiRequest<FinancialStatement>(`/financial-close/workspace/${year}/${month}/statements`, {
    signal,
  })
export const runFinancialCloseChecks = (year: number, month: number) =>
  apiRequest<void>(`/financial-close/periods/${year}/${month}/checks`, { method: 'POST' })
export const closeFinancePeriod = (year: number, month: number, comment?: string) =>
  apiRequest<void>(`/financial-close/periods/${year}/${month}/close`, {
    method: 'POST',
    body: { comment },
  })
export const reopenFinancePeriod = (year: number, month: number, reason: string) =>
  apiRequest<void>(`/financial-close/periods/${year}/${month}/reopen`, {
    method: 'POST',
    body: { reason },
  })
