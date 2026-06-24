import { request } from '@/api/request'
import type { PageResult } from '@/types/api'
import type { MatRequisitionVO, MatRequisitionItemVO, RequisitionQuery } from '@/types/requisition'

/** 领料申请列表分页查询 */
export function getRequisitionList(params: RequisitionQuery) {
  return request<PageResult<MatRequisitionVO>>({
    url: '/requisitions',
    method: 'get',
    params,
  })
}

/** 领料申请详情 */
export function getRequisitionDetail(id: string) {
  return request<MatRequisitionVO>({
    url: `/requisitions/${id}`,
    method: 'get',
  })
}

/** 新建领料申请 */
export function createRequisition(data: Partial<MatRequisitionVO>) {
  return request<string>({
    url: '/requisitions',
    method: 'post',
    data,
  })
}

/** 更新领料申请 */
export function updateRequisition(id: string, data: Partial<MatRequisitionVO>) {
  return request<void>({
    url: `/requisitions/${id}`,
    method: 'put',
    data,
  })
}

/** 删除领料申请 */
export function deleteRequisition(id: string) {
  return request<void>({
    url: `/requisitions/${id}`,
    method: 'delete',
  })
}

/** 提交审批 */
export function submitRequisitionForApproval(id: string) {
  return request<void>({
    url: `/requisitions/${id}/submit`,
    method: 'post',
  })
}

/** 领料申请明细列表 */
export function getRequisitionItems(id: string) {
  return request<MatRequisitionItemVO[]>({
    url: `/requisitions/${id}/items`,
    method: 'get',
  })
}

/** 批量保存领料申请明细 */
export function saveRequisitionItems(id: string, items: MatRequisitionItemVO[]) {
  return request<void>({
    url: `/requisitions/${id}/items/batch`,
    method: 'post',
    data: items,
  })
}
