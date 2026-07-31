import { request } from '@/api/request'
import type { PageResult } from '@/types/api'
import type {
  PayApplicationVO,
  PayApplicationBasisVO,
  PaymentApplicationSourceVO,
  PaymentSourceOptionVO,
  PaymentTraceVO,
  PayRecordVO,
  PayWritebackDTO,
  PaymentReversalDTO,
  PaymentFailureDTO,
} from '@/types/payment'

export interface PaymentSourceOptionParams {
  projectId: string
  contractId: string
  partnerId: string
  payType: string
  expenseCategory?: string
}

/** 付款申请列表分页查询 */
export function getApplicationList(params: Record<string, unknown>) {
  return request<PageResult<PayApplicationVO>>({
    url: '/pay-applications',
    method: 'get',
    params,
  })
}

export function reversePayment(id: string, data: PaymentReversalDTO) {
  return request<PayRecordVO>({ url: `/pay-records/${id}/reverse`, method: 'post', data })
}

export function recordPaymentFailure(data: PaymentFailureDTO) {
  return request<PayRecordVO>({ url: '/pay-records/failures', method: 'post', data })
}

/** 付款申请详情 */
export function getApplicationDetail(id: string) {
  return request<PayApplicationVO>({
    url: `/pay-applications/${id}`,
    method: 'get',
  })
}

/** 新建付款申请 */
export function createApplication(data: Partial<PayApplicationVO>) {
  return request<string>({
    url: '/pay-applications',
    method: 'post',
    data,
  })
}

/** 更新付款申请 */
export function updateApplication(id: string, data: Partial<PayApplicationVO>) {
  return request<void>({
    url: `/pay-applications/${id}`,
    method: 'put',
    data,
  })
}

/** 删除付款申请 */
export function deleteApplication(id: string) {
  return request<void>({
    url: `/pay-applications/${id}`,
    method: 'delete',
  })
}

/** 获取付款申请依据明细列表 */
export function getBasisList(id: string) {
  return request<PayApplicationBasisVO[]>({
    url: `/pay-applications/${id}/basis`,
    method: 'get',
  })
}

/** 批量保存付款申请依据明细 */
export function saveBasis(id: string, items: PayApplicationBasisVO[]) {
  return request<void>({
    url: `/pay-applications/${id}/basis/batch`,
    method: 'post',
    data: items,
  })
}

/** 提交审批 */
export function submitForApproval(id: string) {
  return request<void>({
    url: `/pay-applications/${id}/submit`,
    method: 'post',
  })
}

export function getApplicationSources(id: string) {
  return request<PaymentApplicationSourceVO[]>({
    url: `/pay-applications/${id}/sources`,
    method: 'get',
  })
}

export function getPaymentSourceOptions(params: PaymentSourceOptionParams) {
  return request<PaymentSourceOptionVO[]>({
    url: '/pay-applications/source-options',
    method: 'get',
    params,
  })
}

export function saveApplicationSources(id: string, items: PaymentApplicationSourceVO[]) {
  return request<void>({
    url: `/pay-applications/${id}/sources/batch`,
    method: 'post',
    data: items,
  })
}

/** 付款回写 */
export function doWriteback(data: PayWritebackDTO) {
  return request<PayRecordVO>({
    url: '/pay-records/writeback',
    method: 'post',
    data,
  })
}

export function getPaymentTraceByApplication(id: string) {
  return request<PaymentTraceVO>({
    url: `/payment-traces/applications/${id}`,
    method: 'get',
  })
}

export function getPaymentTraceByCashJournal(id: string) {
  return request<PaymentTraceVO>({
    url: `/payment-traces/cash-journals/${id}`,
    method: 'get',
  })
}
