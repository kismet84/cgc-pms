import { request } from '@/api/request'
import type { PageResult } from '@/types/api'
import type { CostTargetVO, CostTargetItemVO, CostTargetQueryParams } from '@/types/costTarget'

/** 成本目标版本分页列表 */
export function getCostTargetList(params: CostTargetQueryParams) {
  return request<PageResult<CostTargetVO>>({
    url: '/cost-targets',
    method: 'get',
    params,
  })
}

/** 成本目标版本详情 */
export function getCostTargetDetail(id: string) {
  return request<CostTargetVO>({
    url: `/cost-targets/${id}`,
    method: 'get',
  })
}

/** 新建成本目标版本 */
export function createCostTarget(data: Partial<CostTargetVO>) {
  return request<string>({
    url: '/cost-targets',
    method: 'post',
    data,
  })
}

/** 更新成本目标版本 */
export function updateCostTarget(id: string, data: Partial<CostTargetVO>) {
  return request<void>({
    url: `/cost-targets/${id}`,
    method: 'put',
    data,
  })
}

/** 删除成本目标版本 */
export function deleteCostTarget(id: string) {
  return request<void>({
    url: `/cost-targets/${id}`,
    method: 'delete',
  })
}

/** 激活成本目标版本（版本切换） */
export function activateCostTarget(id: string) {
  return request<void>({
    url: `/cost-targets/${id}/activate`,
    method: 'post',
  })
}

/** 获取成本目标明细项列表 */
export function getCostTargetItems(targetId: string) {
  return request<CostTargetItemVO[]>({
    url: `/cost-targets/${targetId}/items`,
    method: 'get',
  })
}

/** 批量保存成本目标明细项 */
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
