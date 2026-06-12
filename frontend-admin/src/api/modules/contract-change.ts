import { request } from '@/api/request'
import type { PageResult } from '@/types/api'
import type { ContractChangeVO, ContractChangeQueryParams } from '@/types/contract-change'

/** 合同变更列表分页查询 */
export function getContractChangeList(params: ContractChangeQueryParams) {
  return request<PageResult<ContractChangeVO>>({
    url: '/contract-changes',
    method: 'get',
    params,
  })
}

/** 合同变更详情 */
export function getContractChangeDetail(id: string) {
  return request<ContractChangeVO>({
    url: `/contract-changes/${id}`,
    method: 'get',
  })
}

/** 新建合同变更 */
export function createContractChange(data: Partial<ContractChangeVO>) {
  return request<number>({
    url: '/contract-changes',
    method: 'post',
    data,
  })
}

/** 更新合同变更 */
export function updateContractChange(id: string, data: Partial<ContractChangeVO>) {
  return request<void>({
    url: `/contract-changes/${id}`,
    method: 'put',
    data,
  })
}

/** 删除合同变更 */
export function deleteContractChange(id: string) {
  return request<void>({
    url: `/contract-changes/${id}`,
    method: 'delete',
  })
}

/** 提交合同变更审批 */
export function submitContractChangeApproval(id: string) {
  return request<void>({
    url: `/contract-changes/${id}/submit`,
    method: 'post',
  })
}
