import { request } from '@/api/request'
import type { PageResult } from '@/types/api'
import type { BudgetAvailabilityVO, BudgetLineVO, ProjectBudgetVO } from '@/types/budget'

export function getBudgetList(params: Record<string, unknown>) {
  return request<PageResult<ProjectBudgetVO>>({ url: '/project-budgets', method: 'get', params })
}

export function getBudgetDetail(id: string) {
  return request<ProjectBudgetVO>({ url: `/project-budgets/${id}`, method: 'get' })
}

export function createBudget(data: Partial<ProjectBudgetVO>) {
  return request<string>({ url: '/project-budgets', method: 'post', data })
}

export function updateBudget(id: string, data: Partial<ProjectBudgetVO>) {
  return request<void>({ url: `/project-budgets/${id}`, method: 'put', data })
}

export function saveBudgetLines(id: string, lines: BudgetLineVO[]) {
  return request<void>({ url: `/project-budgets/${id}/lines`, method: 'post', data: lines })
}

export function submitBudget(id: string) {
  return request<void>({ url: `/project-budgets/${id}/submit`, method: 'post' })
}

export function deleteBudget(id: string) {
  return request<void>({ url: `/project-budgets/${id}`, method: 'delete' })
}

export function getBudgetAvailability(id: string) {
  return request<BudgetAvailabilityVO[]>({
    url: `/project-budgets/${id}/availability`,
    method: 'get',
  })
}
