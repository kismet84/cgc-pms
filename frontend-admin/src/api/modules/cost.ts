import { request } from '@/api/request'
import type { PageResult } from '@/types/api'
import type { CostLedgerVO, CostLedgerQueryParams, CostLedgerSummaryVO } from '@/types/cost'

export interface OverheadAllocationExecutionResult {
  period: string
  ruleCount: number
  createdRunCount: number
  duplicateRunCount: number
  costItemCount: number
  allocatedAmount: string
  idempotent: boolean
}

/** 成本列表分页查询 */
export function getCostLedger(params: CostLedgerQueryParams) {
  return request<PageResult<CostLedgerVO>>({
    url: '/cost-ledger',
    method: 'get',
    params,
  })
}

/** 成本列表汇总统计 */
export function getCostLedgerSummary(params: Partial<CostLedgerQueryParams>) {
  return request<CostLedgerSummaryVO>({
    url: '/cost-ledger/summary',
    method: 'get',
    params,
  })
}

/** 成本列表详情 */
export function getCostLedgerDetail(id: string) {
  return request<CostLedgerVO>({
    url: `/cost-ledger/${id}`,
    method: 'get',
  })
}

/** 执行目标自然月的间接费分摊；租户只能由服务端认证上下文确定。 */
export function executeOverheadAllocation(period: string) {
  return request<OverheadAllocationExecutionResult>({
    url: '/overhead-allocation/execute',
    method: 'post',
    params: { period },
  })
}

// --- 动态成本汇总 ---
import type { CostSummaryVO } from '@/types/cost'

/** 获取动态成本汇总 */
export function getCostSummary(projectId: string) {
  return request<CostSummaryVO>({
    url: `/cost-summary/${projectId}`,
    method: 'get',
  })
}

/** 刷新动态成本汇总 */
export function refreshCostSummary(projectId: string) {
  return request<CostSummaryVO>({
    url: `/cost-summary/${projectId}/refresh`,
    method: 'post',
  })
}
