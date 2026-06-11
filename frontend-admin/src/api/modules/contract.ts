import { request } from '@/api/request'
import type { PageResult } from '@/types/api'
import type {
  ContractVO,
  ContractQueryParams,
  ContractKpiVO,
  ContractItem,
  ContractPaymentTerm,
  ContractApprovalRecord,
} from '@/types/contract'

/** 合同台账分页查询 */
export function getContractLedger(params: ContractQueryParams) {
  return request<PageResult<ContractVO>>({
    url: '/contracts',
    method: 'get',
    params,
  })
}

/** 合同详情 */
export function getContractDetail(id: string) {
  return request<ContractVO>({
    url: `/contracts/${id}`,
    method: 'get',
  })
}

/** 新建合同 */
export function createContract(data: Partial<ContractVO>) {
  return request<ContractVO>({
    url: '/contracts',
    method: 'post',
    data,
  })
}

/** 更新合同 */
export function updateContract(id: string, data: Partial<ContractVO>) {
  return request<void>({
    url: `/contracts/${id}`,
    method: 'put',
    data,
  })
}

/** 合同 KPI 统计 */
/* TODO: Backend KPI endpoint not yet implemented. Returns stub with default values. */
export function getContractKpi(_params?: Partial<ContractQueryParams>) {
  // return request<ContractKpiVO>({
  //   url: '/contracts/kpi',
  //   method: 'get',
  //   params,
  // })
  return Promise.resolve({
    totalCount: 0,
    totalAmount: '0',
    paidAmount: '0',
    unpaidAmount: '0',
    overdueCount: 0,
  } as ContractKpiVO)
}

/** 提交审批 */
export function submitForApproval(id: string) {
  return request<void>({
    url: `/contracts/${id}/submit`,
    method: 'post',
  })
}

/** 获取合同明细项 */
export function getContractItems(contractId: string) {
  return request<ContractItem[]>({
    url: `/contracts/${contractId}/items`,
    method: 'get',
  })
}

/** 批量保存合同明细项 */
export function saveContractItems(contractId: string, items: Partial<ContractItem>[]) {
  return request<void>({
    url: `/contracts/${contractId}/items/batch`,
    method: 'post',
    data: items,
  })
}

/** 获取合同付款条款 */
export function getPaymentTerms(contractId: string) {
  return request<ContractPaymentTerm[]>({
    url: `/contracts/${contractId}/payment-terms`,
    method: 'get',
  })
}

/** 批量保存合同付款条款 */
export function savePaymentTerms(contractId: string, terms: Partial<ContractPaymentTerm>[]) {
  return request<void>({
    url: `/contracts/${contractId}/payment-terms/batch`,
    method: 'post',
    data: terms,
  })
}

/** 获取合同付款条款（别名，供 store 使用） */
export function getContractPaymentTerms(contractId: string) {
  return getPaymentTerms(contractId)
}

/** 批量保存合同付款条款（别名，供 store 使用） */
export function saveContractPaymentTerms(
  contractId: string,
  terms: Partial<ContractPaymentTerm>[],
) {
  return savePaymentTerms(contractId, terms)
}

/** 获取合同审批记录 */
export function getContractApprovalRecords(contractId: string) {
  return request<ContractApprovalRecord[]>({
    url: `/contracts/${contractId}/approval-records`,
    method: 'get',
  })
}
