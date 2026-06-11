import { request } from '@/api/request'
import type { PageResult } from '@/types/api'
import type { VarOrderVO, VarOrderItemVO } from '@/types/variation'

/** 变更签证列表分页查询 */
export function getVarOrderList(params: Record<string, unknown>) {
  return request<PageResult<VarOrderVO>>({
    url: '/var-orders',
    method: 'get',
    params,
  })
}

/** 变更签证详情（含明细） */
export function getVarOrderDetail(id: string) {
  return request<VarOrderVO>({
    url: `/var-orders/${id}`,
    method: 'get',
  })
}

/** 新建变更签证 */
export function createVarOrder(data: Partial<VarOrderVO>) {
  return request<string>({
    url: '/var-orders',
    method: 'post',
    data,
  })
}

/** 更新变更签证 */
export function updateVarOrder(id: string, data: Partial<VarOrderVO>) {
  return request<void>({
    url: `/var-orders/${id}`,
    method: 'put',
    data,
  })
}

/** 删除变更签证 */
export function deleteVarOrder(id: string) {
  return request<void>({
    url: `/var-orders/${id}`,
    method: 'delete',
  })
}

/** 获取变更签证明细列表 */
export function getVarOrderItems(id: string) {
  return request<VarOrderItemVO[]>({
    url: `/var-orders/${id}/items`,
    method: 'get',
  })
}

/** 批量保存变更签证明细 */
export function saveVarOrderItems(id: string, items: VarOrderItemVO[]) {
  return request<void>({
    url: `/var-orders/${id}/items/batch`,
    method: 'post',
    data: items,
  })
}
