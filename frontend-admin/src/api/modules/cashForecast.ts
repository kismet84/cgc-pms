import { request } from '@/api/request'

export type CashForecastRow = Record<string, unknown>

export interface CashForecastTrace {
  cycle: CashForecastRow
  lines: CashForecastRow[]
  actions: CashForecastRow[]
  collectionSchedules: CashForecastRow[]
  paymentSchedules: CashForecastRow[]
  actualJournals: CashForecastRow[]
  auditTrail: CashForecastRow[]
}

export interface CashForecastCycleRequest {
  projectId: string
  forecastName: string
  asOfDate: string
  horizonStart: string
  horizonEnd: string
  scenario: 'BASE' | 'OPTIMISTIC' | 'CONSERVATIVE'
  openingBalance: number
  previousCycleId?: string
}

export interface FundingActionRequest {
  lineId: string
  actionType: 'ACCELERATE_COLLECTION' | 'DEFER_PAYMENT' | 'FUND_TRANSFER' | 'FINANCING'
  plannedDate: string
  amount: number
  reason: string
  sourceType?: string
  sourceId?: string
}

export function getCashForecastCycles(projectId: string) {
  return request<CashForecastRow[]>({
    url: '/cash-forecasts/cycles',
    method: 'get',
    params: { projectId },
  })
}

export function getCashForecastTrace(id: string) {
  return request<CashForecastTrace>({ url: `/cash-forecasts/cycles/${id}/trace`, method: 'get' })
}

export function createCashForecastCycle(data: CashForecastCycleRequest) {
  return request<CashForecastTrace>({ url: '/cash-forecasts/cycles', method: 'post', data })
}

export function regenerateCashForecast(id: string) {
  return request<CashForecastTrace>({
    url: `/cash-forecasts/cycles/${id}/regenerate`,
    method: 'post',
  })
}

export function submitCashForecast(id: string) {
  return request<CashForecastTrace>({
    url: `/cash-forecasts/cycles/${id}/submit`,
    method: 'post',
  })
}

export function approveCashForecast(id: string, approved: boolean, comment: string) {
  return request<CashForecastTrace>({
    url: `/cash-forecasts/cycles/${id}/approve`,
    method: 'post',
    data: { approved, comment },
  })
}

export function refreshCashForecastActuals(id: string) {
  return request<CashForecastTrace>({
    url: `/cash-forecasts/cycles/${id}/actuals/refresh`,
    method: 'post',
  })
}

export function rollCashForecast(
  id: string,
  data: { asOfDate: string; horizonEnd: string; forecastName: string },
) {
  return request<CashForecastTrace>({
    url: `/cash-forecasts/cycles/${id}/roll`,
    method: 'post',
    data,
  })
}

export function createFundingAction(cycleId: string, data: FundingActionRequest) {
  return request<CashForecastRow>({
    url: `/cash-forecasts/cycles/${cycleId}/actions`,
    method: 'post',
    data,
  })
}

export function submitFundingAction(id: string) {
  return request<CashForecastRow>({
    url: `/cash-forecasts/actions/${id}/submit`,
    method: 'post',
  })
}

export function approveFundingAction(id: string, approved: boolean, comment: string) {
  return request<CashForecastRow>({
    url: `/cash-forecasts/actions/${id}/approve`,
    method: 'post',
    data: { approved, comment },
  })
}

export function completeFundingAction(
  id: string,
  actualAmount: number,
  completionReference: string,
) {
  return request<CashForecastRow>({
    url: `/cash-forecasts/actions/${id}/complete`,
    method: 'post',
    data: { actualAmount, completionReference },
  })
}
