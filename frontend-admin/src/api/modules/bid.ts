import { request } from '@/api/request'
import type { PageResult } from '@/types/api'
import type { BidCostQuery, BidCostVO } from '@/types/bid'

export function getBidCosts(params: BidCostQuery) {
  return request<PageResult<BidCostVO>>({
    url: '/bid-cost',
    method: 'get',
    params,
  })
}
