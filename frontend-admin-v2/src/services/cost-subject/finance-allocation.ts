import { apiRequest } from '../request'
import { normalizeAuditRow, query, requiredId } from './normalize'
import type {
  CostSubjectAuditRow,
  FinanceAllocationRequestCommand,
  FinanceAllocationRequestRecord,
} from './types'

export function loadFinanceAllocations(signal?: AbortSignal): Promise<CostSubjectAuditRow[]> {
  return apiRequest<Record<string, unknown>[]>('/cost-subject-v2/finance-allocations', {
    signal,
  }).then((rows) => rows.map(normalizeAuditRow))
}

export function loadFinanceAllocationRequests(
  signal?: AbortSignal,
): Promise<FinanceAllocationRequestRecord[]> {
  return apiRequest<Record<string, unknown>[]>('/cost-subject-v2/finance-allocation-requests', {
    signal,
  }).then((rows) => rows.map((row) => normalizeAuditRow(row) as FinanceAllocationRequestRecord))
}

export function createFinanceAllocationRequest(
  command: FinanceAllocationRequestCommand,
): Promise<FinanceAllocationRequestRecord> {
  return apiRequest<Record<string, unknown>, FinanceAllocationRequestCommand>(
    '/cost-subject-v2/finance-allocation-requests',
    { method: 'POST', body: command },
  ).then((row) => normalizeAuditRow(row) as FinanceAllocationRequestRecord)
}

export function submitFinanceAllocationRequest(
  id: string,
): Promise<FinanceAllocationRequestRecord> {
  return apiRequest<Record<string, unknown>>(
    `/cost-subject-v2/finance-allocation-requests/${requiredId(id)}/submit`,
    { method: 'POST' },
  ).then((row) => normalizeAuditRow(row) as FinanceAllocationRequestRecord)
}

export function reverseFinanceAllocation(
  id: string,
  approvalInstanceId: string,
  idempotencyKey: string,
  remark: string,
): Promise<string | number> {
  return apiRequest<string | number>(
    `/cost-subject-v2/finance-allocations/${requiredId(id)}/reverse?${query({
      approvalInstanceId,
      idempotencyKey,
      remark,
    })}`,
    { method: 'POST' },
  )
}

export function loadCostSubjectReconciliation(projectId: string): Promise<CostSubjectAuditRow> {
  return apiRequest<Record<string, unknown>>(
    `/cost-subject-v2/reconciliation?${query({ projectId })}`,
  ).then(normalizeAuditRow)
}
