export type BidStatus = 'BIDDING' | 'WON' | 'LOST'

export interface BidCostQuery {
  pageNo: number
  pageSize: number
  bidStatus?: BidStatus
  keyword?: string
}

export interface BidCostCreatePayload {
  bidProjectName: string
  remark?: string
}

export interface BidCostUpdatePayload {
  bidProjectName: string
  remark?: string
}

export interface BidCostVO {
  id: string
  projectId?: string | null
  bidProjectName: string
  bidStatus: BidStatus
  createdAt?: string
  updatedAt?: string
  remark?: string
}
