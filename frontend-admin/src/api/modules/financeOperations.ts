import { request } from '@/api/request'

export type FinanceRow = Record<string, unknown>

export function getFinanceAlerts(status = 'OPEN') {
  return request<FinanceRow[]>({ url: '/finance-operations/alerts', method: 'get', params: { status } })
}
export function generateFinanceAlerts() {
  return request<FinanceRow>({ url: '/finance-operations/alerts/generate', method: 'post' })
}
export function handleFinanceAlert(id: string, status: 'RESOLVED' | 'IGNORED', note: string) {
  return request<void>({ url: `/finance-operations/alerts/${id}/handle`, method: 'post', data: { status, note } })
}
export function runFinanceReconciliation(businessDate?: string) {
  return request<FinanceRow>({ url: '/finance-operations/reconciliations/run', method: 'post', params: { businessDate } })
}
export function getPaymentSchedules(status?: string) {
  return request<FinanceRow[]>({ url: '/finance-operations/schedules', method: 'get', params: { status } })
}
export function createPaymentSchedule(data: FinanceRow) {
  return request<FinanceRow>({ url: '/finance-operations/schedules', method: 'post', data })
}
export function rebuildFinanceSnapshot(projectId: string, date?: string) {
  return request<FinanceRow>({ url: `/finance-operations/snapshots/${projectId}/rebuild`, method: 'post', params: { date, mode: 'INCREMENTAL' } })
}
export function getFinanceSnapshots(projectId: string) {
  return request<FinanceRow[]>({ url: `/finance-operations/snapshots/${projectId}`, method: 'get' })
}
export function getIntegrationEndpoints() {
  return request<FinanceRow[]>({ url: '/finance-operations/integrations/endpoints', method: 'get' })
}
export function createIntegrationEndpoint(data: FinanceRow) {
  return request<FinanceRow>({ url: '/finance-operations/integrations/endpoints', method: 'post', data })
}
export function createInvoiceOcrReview(data: FinanceRow) {
  return request<FinanceRow>({ url: '/finance-operations/ocr-reviews', method: 'post', data })
}
