import { apiRequest } from '../request'
import { normalizeSubject, requiredId } from './normalize'
import type { AccountingCatalogOverview, CostSubjectCommand, CostSubjectRecord } from './types'

export function loadCostSubjectTree(signal?: AbortSignal): Promise<CostSubjectRecord[]> {
  return apiRequest<CostSubjectRecord[]>('/cost-subjects/accounting-tree', { signal }).then(
    (rows) => rows.map(normalizeSubject),
  )
}

export function loadAccountingCatalogOverview(
  signal?: AbortSignal,
): Promise<AccountingCatalogOverview> {
  return apiRequest<AccountingCatalogOverview>('/cost-subjects/accounting-overview', { signal })
}

export function loadCostSubject(id: string): Promise<CostSubjectRecord> {
  return apiRequest<CostSubjectRecord>(`/cost-subjects/${requiredId(id)}`).then(normalizeSubject)
}

export function createCostSubject(command: CostSubjectCommand): Promise<string | number> {
  return apiRequest<string | number, CostSubjectCommand>('/cost-subjects', {
    method: 'POST',
    body: command,
  })
}

export function updateCostSubject(id: string, command: CostSubjectCommand): Promise<void> {
  return apiRequest<void, CostSubjectCommand>(`/cost-subjects/${requiredId(id)}`, {
    method: 'PUT',
    body: command,
  })
}

export function toggleCostSubjectStatus(id: string): Promise<void> {
  return apiRequest<void>(`/cost-subjects/${requiredId(id)}/toggle`, { method: 'PUT' })
}

export function deleteCostSubject(id: string): Promise<void> {
  return apiRequest<void>(`/cost-subjects/${requiredId(id)}`, { method: 'DELETE' })
}

export function updateTargetCostRatios(
  ratios: Array<{ subjectCode: string; ratio: string }>,
): Promise<CostSubjectRecord[]> {
  return apiRequest<CostSubjectRecord[], Array<{ subjectCode: string; ratio: string }>>(
    '/cost-subjects/target-ratios',
    { method: 'PUT', body: ratios },
  ).then((rows) => rows.map(normalizeSubject))
}
