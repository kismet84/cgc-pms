import { request } from '@/api/request'
import type { PageResult } from '@/types/api'
import type { MatPurchaseOrderVO, MatPurchaseOrderItemVO, PurchaseOrderQuery } from '@/types/purchase'

/** 采购订单列表分页查询 */
export function getOrderList(params: PurchaseOrderQuery) {
  return request<PageResult<MatPurchaseOrderVO>>({
    url: '/purchase-orders',
    method: 'get',
    params,
  })
}

/** 采购订单详情 */
export function getOrderDetail(id: string) {
  return request<MatPurchaseOrderVO>({
    url: `/purchase-orders/${id}`,
    method: 'get',
  })
}

/** 新建采购订单 */
export function createOrder(data: Partial<MatPurchaseOrderVO>) {
  return request<string>({
    url: '/purchase-orders',
    method: 'post',
    data,
  })
}

/** 更新采购订单 */
export function updateOrder(id: string, data: Partial<MatPurchaseOrderVO>) {
  return request<void>({
    url: `/purchase-orders/${id}`,
    method: 'put',
    data,
  })
}

/** 删除采购订单 */
export function deleteOrder(id: string) {
  return request<void>({
    url: `/purchase-orders/${id}`,
    method: 'delete',
  })
}

/** 采购订单明细列表 */
export function getOrderItems(id: string) {
  return request<MatPurchaseOrderItemVO[]>({
    url: `/purchase-orders/${id}/items`,
    method: 'get',
  })
}

/** 批量保存采购订单明细 */
export function saveOrderItems(id: string, items: MatPurchaseOrderItemVO[]) {
  return request<void>({
    url: `/purchase-orders/${id}/items/batch`,
    method: 'post',
    data: items,
  })
}
