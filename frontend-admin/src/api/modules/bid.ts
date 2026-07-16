import { request } from '@/api/request'
import type { PageResult } from '@/types/api'
import type {
  BidCostCreatePayload,
  BidCostQuery,
  BidCostUpdatePayload,
  BidCostVO,
} from '@/types/bid'

export function getBidCosts(params: BidCostQuery) {
  return request<PageResult<BidCostVO>>({
    url: '/bid-cost',
    method: 'get',
    params,
  })
}

export function getBidCost(id: string) {
  return request<BidCostVO>({
    url: `/bid-cost/${id}`,
    method: 'get',
  })
}

export function createBidCost(data: BidCostCreatePayload) {
  return request<string>({
    url: '/bid-cost',
    method: 'post',
    data,
  })
}

export function updateBidCost(id: string, data: BidCostUpdatePayload) {
  return request<void>({
    url: `/bid-cost/${id}`,
    method: 'put',
    data,
  })
}

export function deleteBidCost(id: string) {
  return request<void>({
    url: `/bid-cost/${id}`,
    method: 'delete',
  })
}

export function markBidCostAsWon(id: string, projectId: string) {
  return request<void>({
    url: `/bid-cost/${id}/won`,
    method: 'put',
    params: { projectId },
  })
}

export function markBidCostAsLost(id: string) {
  return request<void>({
    url: `/bid-cost/${id}/lost`,
    method: 'put',
  })
}
