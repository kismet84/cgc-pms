import { request } from '@/api/request'
import type { PageResult } from '@/types/api'
import type { BidCostCreatePayload, BidCostQuery, BidCostVO } from '@/types/bid'

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
