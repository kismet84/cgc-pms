import { request } from '@/api/request'
import type { PageResult } from '@/types/api'
import type { ContractVO, ContractQueryParams, ContractKpiVO } from '@/types/contract'

/** 合同台账分页查询 */
export function getContractLedger(params: ContractQueryParams) {
  return request<PageResult<ContractVO>>({
    url: '/contracts/ledger',
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
  return request<void>({
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
export function getContractKpi(params?: Partial<ContractQueryParams>) {
  return request<ContractKpiVO>({
    url: '/contracts/kpi',
    method: 'get',
    params,
  })
}

/** 提交审批 */
export function submitForApproval(id: string) {
  return request<void>({
    url: `/contracts/${id}/submit`,
    method: 'post',
  })
}
