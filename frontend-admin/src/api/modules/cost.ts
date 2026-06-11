import { request } from '@/api/request'
import type { PageResult } from '@/types/api'
import type {
  CostLedgerVO,
  CostLedgerQueryParams,
  CostLedgerSummaryVO,
} from '@/types/cost'

/** 成本台账分页查询 */
export function getCostLedger(params: CostLedgerQueryParams) {
  return request<PageResult<CostLedgerVO>>({
    url: '/cost-ledger',
    method: 'get',
    params,
  })
}

/** 成本台账汇总统计 */
export function getCostLedgerSummary(params: Partial<CostLedgerQueryParams>) {
  return request<CostLedgerSummaryVO>({
    url: '/cost-ledger/summary',
    method: 'get',
    params,
  })
}

/** 成本台账详情 */
export function getCostLedgerDetail(id: string) {
  return request<CostLedgerVO>({
    url: `/cost-ledger/${id}`,
    method: 'get',
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
