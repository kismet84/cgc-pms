import { request } from '@/api/request'
import type { PageResult } from '@/types/common'

export interface ContractRevenueVO {
  id: string
  projectId: string
  projectName: string
  contractId: string
  contractName: string
  revenueCode: string
  revenueDate: string
  progressPercent: string
  progressDesc: string
  revenueAmount: string
  revenueTax: string
  revenueAmountWithTax: string
  billedAmount: string
  billedTax: string
  approvalStatus: string
  costItemId: string
  createdBy: string
  createdAt: string
  updatedAt: string
}

export interface ContractRevenueBalanceVO {
  contractId: string
  totalConfirmedRevenue: string
  totalBilled: string
  contractAsset: string
  contractLiability: string
}

export interface ContractRevenueQueryParams {
  pageNo: number
  pageSize: number
  projectId?: string
  contractId?: string
  startDate?: string
  endDate?: string
  approvalStatus?: string
}

export function getRevenuePage(params: ContractRevenueQueryParams) {
  return request<PageResult<ContractRevenueVO>>({
    url: '/contract-revenue',
    method: 'get',
    params,
  })
}

export function getRevenueById(id: string) {
  return request<ContractRevenueVO>({
    url: `/contract-revenue/${id}`,
    method: 'get',
  })
}

export function getRevenueBalance(contractId: string) {
  return request<ContractRevenueBalanceVO>({
    url: `/contract-revenue/balance/${contractId}`,
    method: 'get',
  })
}

export function createRevenue(data: Record<string, unknown>) {
  return request<string>({
    url: '/contract-revenue',
    method: 'post',
    data,
  })
}

export function updateRevenue(id: string, data: Record<string, unknown>) {
  return request<void>({
    url: `/contract-revenue/${id}`,
    method: 'put',
    data,
  })
}

export function deleteRevenue(id: string) {
  return request<void>({
    url: `/contract-revenue/${id}`,
    method: 'delete',
  })
}

export function submitRevenue(id: string) {
  return request<void>({
    url: `/contract-revenue/${id}/submit`,
    method: 'post',
  })
}
