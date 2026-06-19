import { request } from '@/api/request'
import type { CostSubjectVO, CostSubjectTreeNode } from '@/types/costSubject'

/** 获取成本科目树，category 可选 COST|REVENUE|SETTLEMENT */
export function getCostSubjectTree(category?: string) {
  return request<CostSubjectTreeNode[]>({
    url: '/cost-subjects/tree',
    method: 'get',
    params: category ? { category } : undefined,
  })
}

/** 获取成本科目列表，category 可选 COST|REVENUE|SETTLEMENT */
export function getCostSubjectList(category?: string) {
  return request<CostSubjectVO[]>({
    url: '/cost-subjects',
    method: 'get',
    params: category ? { category } : undefined,
  })
}

/** 获取成本科目详情 */
export function getCostSubjectById(id: string) {
  return request<CostSubjectVO>({
    url: `/cost-subjects/${id}`,
    method: 'get',
  })
}

/** 新建成本科目 */
export function createCostSubject(data: Partial<CostSubjectVO>) {
  return request<string>({
    url: '/cost-subjects',
    method: 'post',
    data,
  })
}

/** 更新成本科目 */
export function updateCostSubject(id: string, data: Partial<CostSubjectVO>) {
  return request<void>({
    url: `/cost-subjects/${id}`,
    method: 'put',
    data,
  })
}

/** 删除成本科目 */
export function deleteCostSubject(id: string) {
  return request<void>({
    url: `/cost-subjects/${id}`,
    method: 'delete',
  })
}

/** 切换成本科目启用/停用状态 */
export function toggleCostSubjectStatus(id: string) {
  return request<void>({
    url: `/cost-subjects/${id}/toggle`,
    method: 'put',
  })
}
