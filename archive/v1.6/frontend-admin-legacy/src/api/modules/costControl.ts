import { request } from '@/api/request'
import type {
  CostControlOverview,
  CostControlRow,
  CorrectivePayload,
  ForecastPayload,
} from '@/types/costControl'

export function getCostControlOverview(projectId: string) {
  return request<CostControlOverview>({
    url: `/cost-controls/projects/${projectId}/overview`,
    method: 'get',
  })
}

export function getCostForecastTrace(id: string) {
  return request<CostControlRow>({ url: `/cost-controls/forecasts/${id}/trace`, method: 'get' })
}

export function createCostForecast(data: ForecastPayload) {
  return request<CostControlRow>({ url: '/cost-controls/forecasts', method: 'post', data })
}

export function updateCostForecast(id: string, data: ForecastPayload) {
  return request<CostControlRow>({ url: `/cost-controls/forecasts/${id}`, method: 'put', data })
}

export function confirmCostForecast(id: string) {
  return request<CostControlRow>({ url: `/cost-controls/forecasts/${id}/confirm`, method: 'post' })
}

export function createCostCorrective(data: CorrectivePayload) {
  return request<CostControlRow>({ url: '/cost-controls/corrective-actions', method: 'post', data })
}

export function submitCostCorrective(id: string) {
  return request<CostControlRow>({
    url: `/cost-controls/corrective-actions/${id}/submit`,
    method: 'post',
  })
}

export function closeCostCorrective(
  id: string,
  data: { actualSavingAmount: number; resultDescription: string },
) {
  return request<CostControlRow>({
    url: `/cost-controls/corrective-actions/${id}/close`,
    method: 'post',
    data,
  })
}
