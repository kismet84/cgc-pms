import { request } from '@/api/request'
import type { PageResult } from '@/types/api'
import type {
  InvoiceVO,
  PayRecordBrief,
  InvoiceRecognizeResultVO,
  InvoicePaymentAllocationVO,
} from '@/types/invoice'

/** 发票列表分页查询 */
export function getInvoiceList(params: Record<string, unknown>) {
  return request<PageResult<InvoiceVO>>({
    url: '/invoices',
    method: 'get',
    params,
  })
}

/** 发票详情 */
export function getInvoiceDetail(id: string) {
  return request<InvoiceVO>({
    url: `/invoices/${id}`,
    method: 'get',
  })
}

/** 新建发票 */
export function createInvoice(data: Partial<InvoiceVO>) {
  return request<string>({
    url: '/invoices',
    method: 'post',
    data,
  })
}

/** 登记发票（关联付款记录） */
export function registerInvoice(data: Partial<InvoiceVO>) {
  return request<string>({
    url: '/invoices/register',
    method: 'post',
    data,
  })
}

/** 更新发票 */
export function updateInvoice(id: string, data: Partial<InvoiceVO>) {
  return request<void>({
    url: `/invoices/${id}`,
    method: 'put',
    data,
  })
}

/** 删除发票 */
export function deleteInvoice(id: string) {
  return request<void>({
    url: `/invoices/${id}`,
    method: 'delete',
  })
}

/** 核验发票（状态切换：PENDING → VERIFIED / ABNORMAL） */
export function verifyInvoice(id: string, verifyStatus: string) {
  return request<void>({
    url: `/invoices/${id}/verify`,
    method: 'put',
    data: { verifyStatus },
  })
}

export function getInvoiceAllocations(id: string) {
  return request<InvoicePaymentAllocationVO[]>({
    url: `/invoices/${id}/allocations`,
    method: 'get',
  })
}

export function saveInvoiceAllocations(id: string, items: InvoicePaymentAllocationVO[]) {
  return request<void>({ url: `/invoices/${id}/allocations/batch`, method: 'post', data: items })
}

export function getInvoiceWriteOffProgress(id: string) {
  return request<Record<string, unknown>>({
    url: `/finance-operations/invoices/${id}/write-off`,
    method: 'get',
  })
}

export function markInvoiceException(id: string, status: string, reason: string) {
  return request<void>({
    url: `/finance-operations/invoices/${id}/exception`,
    method: 'post',
    data: { status, reason },
  })
}

/** 发票 OCR 识别（上传 PDF） */
export function recognizeInvoice(file: File, signal?: AbortSignal) {
  const formData = new FormData()
  formData.append('file', file)
  return request<InvoiceRecognizeResultVO>({
    url: '/invoices/recognize',
    method: 'post',
    data: formData,
    timeout: 120000,
    signal,
  })
}

/** 付款记录列表（用于关联下拉） */
export function getPayRecordList(params?: Record<string, unknown>) {
  return request<PageResult<PayRecordBrief>>({
    url: '/pay-records',
    method: 'get',
    params: { pageNo: 1, pageSize: 200, ...params },
  })
}
