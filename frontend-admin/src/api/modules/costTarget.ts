import { request } from '@/api/request'
import type { PageResult } from '@/types/api'
import type { CostTargetVO, CostTargetItemVO, CostTargetQueryParams } from '@/types/costTarget'

/** 目标成本版本分页列表 */
export function getCostTargetList(params: CostTargetQueryParams) {
  return request<PageResult<CostTargetVO>>({
    url: '/cost-targets',
    method: 'get',
    params,
  })
}

/** 目标成本版本详情 */
export function getCostTargetDetail(id: string) {
  return request<CostTargetVO>({
    url: `/cost-targets/${id}`,
    method: 'get',
  })
}

/** 新建目标成本版本 */
export function createCostTarget(data: Partial<CostTargetVO>) {
  return request<number>({
    url: '/cost-targets',
    method: 'post',
    data,
  })
}

/** 更新目标成本版本 */
export function updateCostTarget(id: string, data: Partial<CostTargetVO>) {
  return request<void>({
    url: `/cost-targets/${id}`,
    method: 'put',
    data,
  })
}

/** 删除目标成本版本 */
export function deleteCostTarget(id: string) {
  return request<void>({
    url: `/cost-targets/${id}`,
    method: 'delete',
  })
}

/** 激活目标成本版本（版本切换） */
export function activateCostTarget(id: string) {
  return request<void>({
    url: `/cost-targets/${id}/activate`,
    method: 'post',
  })
}

/** 获取目标成本明细项列表 */
export function getCostTargetItems(targetId: string) {
  return request<CostTargetItemVO[]>({
    url: `/cost-targets/${targetId}/items`,
    method: 'get',
  })
}

/** 批量保存目标成本明细项 */
export function saveCostTargetItems(targetId: string, items: Partial<CostTargetItemVO>[]) {
  return request<void>({
    url: `/cost-targets/${targetId}/items`,
    method: 'post',
    data: items,
  })
}

/** 提交审批 */
export function submitCostTargetForApproval(targetId: string) {
  return request<void>({
    url: `/cost-targets/${targetId}/submit`,
    method: 'post',
  })
}
