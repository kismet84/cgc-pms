import type { PageResult } from '@cgc-pms/frontend-contracts'
import { apiRequest } from '../request'
import { query, requiredId } from './normalize'
import type {
  OverheadAllocationExecutionResult,
  OverheadAllocationRuleCommand,
  OverheadAllocationRuleRecord,
} from './types'

export function loadOverheadAllocationRules(
  signal?: AbortSignal,
): Promise<PageResult<OverheadAllocationRuleRecord>> {
  return apiRequest<PageResult<OverheadAllocationRuleRecord>>(
    '/overhead-allocation/rules?pageNo=1&pageSize=100',
    { signal },
  )
}

export function createOverheadAllocationRule(
  command: OverheadAllocationRuleCommand,
): Promise<string> {
  return apiRequest<string, OverheadAllocationRuleCommand>('/overhead-allocation/rules', {
    method: 'POST',
    body: command,
  })
}

export function updateOverheadAllocationRule(
  id: string,
  command: OverheadAllocationRuleCommand,
): Promise<void> {
  return apiRequest<void, OverheadAllocationRuleCommand>(
    `/overhead-allocation/rules/${requiredId(id)}`,
    { method: 'PUT', body: command },
  )
}

export function setOverheadAllocationRuleStatus(
  id: string,
  status: OverheadAllocationRuleRecord['status'],
): Promise<void> {
  return apiRequest<void>(
    `/overhead-allocation/rules/${requiredId(id)}/status?${query({ status })}`,
    { method: 'PUT' },
  )
}

export function executeOverheadAllocation(
  period: string,
): Promise<OverheadAllocationExecutionResult> {
  return apiRequest<OverheadAllocationExecutionResult>(
    `/overhead-allocation/execute?${query({ period })}`,
    { method: 'POST' },
  )
}
