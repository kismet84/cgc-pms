import { request } from '@/api/request'

export type MeasurementRow = Record<string, any>

export function getMeasurementPeriods(projectId?: string, contractId?: string) {
  return request<MeasurementRow[]>({ url: '/production-measurements/periods', method: 'get', params: { projectId, contractId } })
}
export function createMeasurementPeriod(data: MeasurementRow) {
  return request<MeasurementRow>({ url: '/production-measurements/periods', method: 'post', data })
}
export function closeMeasurementPeriod(id: string) {
  return request<MeasurementRow>({ url: `/production-measurements/periods/${id}/close`, method: 'post' })
}
export function getMeasurementSources(projectId: string, contractId: string) {
  return request<MeasurementRow[]>({ url: '/production-measurements/sources', method: 'get', params: { projectId, contractId } })
}
export function getProductionMeasurements(projectId?: string, status?: string) {
  return request<MeasurementRow[]>({ url: '/production-measurements', method: 'get', params: { projectId, status } })
}
export function createProductionMeasurement(data: MeasurementRow) {
  return request<MeasurementRow>({ url: '/production-measurements', method: 'post', data })
}
export function submitProductionMeasurement(id: string) {
  return request<MeasurementRow>({ url: `/production-measurements/${id}/submit`, method: 'post' })
}
export function createOwnerMeasurementSubmission(id: string, data: MeasurementRow) {
  return request<MeasurementRow>({ url: `/production-measurements/${id}/owner-submissions`, method: 'post', data })
}
export function getOwnerMeasurementSubmissions(projectId?: string, status?: string) {
  return request<MeasurementRow[]>({ url: '/production-measurements/owner-submissions/list', method: 'get', params: { projectId, status } })
}
export function getOwnerMeasurementSubmission(id: string) {
  return request<MeasurementRow>({ url: `/production-measurements/owner-submissions/${id}`, method: 'get' })
}
export function reviewOwnerMeasurementSubmission(id: string, data: MeasurementRow) {
  return request<MeasurementRow>({ url: `/production-measurements/owner-submissions/${id}/review`, method: 'post', data })
}
export function getMeasurementSettlementTrace(settlementId: string) {
  return request<MeasurementRow>({ url: `/production-measurements/trace/settlements/${settlementId}`, method: 'get' })
}
