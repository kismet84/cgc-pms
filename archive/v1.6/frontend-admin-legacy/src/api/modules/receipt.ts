import { request } from '@/api/request'
import type { PageResult } from '@/types/api'
import type { MatReceiptVO, MatReceiptItemVO, ReceiptQuery } from '@/types/receipt'

/** 材料验收列表分页查询 */
export function getReceiptList(params: ReceiptQuery) {
  return request<PageResult<MatReceiptVO>>({
    url: '/receipts',
    method: 'get',
    params,
  })
}

/** 材料验收详情 */
export function getReceiptDetail(id: string) {
  return request<MatReceiptVO>({
    url: `/receipts/${id}`,
    method: 'get',
  })
}

/** 新建材料验收 */
export function createReceipt(data: Partial<MatReceiptVO>) {
  return request<string>({
    url: '/receipts',
    method: 'post',
    data,
  })
}

/** 更新材料验收 */
export function updateReceipt(id: string, data: Partial<MatReceiptVO>) {
  return request<void>({
    url: `/receipts/${id}`,
    method: 'put',
    data,
  })
}

/** 删除材料验收 */
export function deleteReceipt(id: string) {
  return request<void>({
    url: `/receipts/${id}`,
    method: 'delete',
  })
}

/** 材料验收明细列表 */
export function getReceiptItems(id: string) {
  return request<MatReceiptItemVO[]>({
    url: `/receipts/${id}/items`,
    method: 'get',
  })
}

/** 批量保存材料验收明细 */
export function saveReceiptItems(id: string, items: MatReceiptItemVO[]) {
  return request<void>({
    url: `/receipts/${id}/items/batch`,
    method: 'post',
    data: items,
  })
}

/** 获取采购订单明细用于验收行选择 */
export function getOrderItemsForReceipt(orderId: string) {
  return request<MatReceiptItemVO[]>({
    url: `/receipts/orders/${orderId}/items`,
    method: 'get',
  })
}

/** 提交审批 */
export function submitReceiptForApproval(id: string) {
  return request<void>({
    url: `/receipts/${id}/submit`,
    method: 'post',
  })
}
