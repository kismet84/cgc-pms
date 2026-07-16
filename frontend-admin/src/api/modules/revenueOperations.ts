import { request } from '@/api/request'

export type RevenueRow = Record<string, unknown>

export function getRevenueDashboard(projectId: string) {
  return request<RevenueRow>({ url: `/revenue-operations/dashboard/${projectId}`, method: 'get' })
}

export function getOwnerSettlements(projectId?: string, status?: string) {
  return request<RevenueRow[]>({ url: '/revenue-operations/settlements', method: 'get', params: { projectId, status } })
}

export function createOwnerSettlement(data: RevenueRow) {
  return request<RevenueRow>({ url: '/revenue-operations/settlements', method: 'post', data })
}

export function submitOwnerSettlement(id: string) {
  return request<RevenueRow>({ url: `/revenue-operations/settlements/${id}/submit`, method: 'post' })
}

export function getReceivables(projectId?: string, status?: string) {
  return request<RevenueRow[]>({ url: '/revenue-operations/receivables', method: 'get', params: { projectId, status } })
}

export function getSalesInvoices(projectId?: string) {
  return request<RevenueRow[]>({ url: '/revenue-operations/sales-invoices', method: 'get', params: { projectId } })
}

export function createSalesInvoice(data: RevenueRow) {
  return request<RevenueRow>({ url: '/revenue-operations/sales-invoices', method: 'post', data })
}

export function getCollections(projectId?: string, status?: string) {
  return request<RevenueRow[]>({ url: '/revenue-operations/collections', method: 'get', params: { projectId, status } })
}

export function createCollection(data: RevenueRow) {
  return request<RevenueRow>({ url: '/revenue-operations/collections', method: 'post', data })
}

export function reverseCollection(id: string, data: { reason: string; idempotencyKey: string }) {
  return request<RevenueRow>({ url: `/revenue-operations/collections/${id}/reverse`, method: 'post', data })
}

export function getCollectionSchedules(status?: string) {
  return request<RevenueRow[]>({ url: '/revenue-operations/schedules', method: 'get', params: { status } })
}

export function runRevenueReconciliation(date?: string) {
  return request<RevenueRow>({ url: '/revenue-operations/reconciliations/run', method: 'post', params: { date } })
}

export function getRevenueTrace(journalId: string) {
  return request<RevenueRow>({ url: `/revenue-operations/trace/cash-journals/${journalId}`, method: 'get' })
}
