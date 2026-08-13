export interface CostSubjectOption {
  id: string
  parentId?: string | null
  subjectCode: string
  subjectName: string
  status: string
}

export interface BidOwnerOption {
  ownerId: string
  ownerName: string | null
}

export interface BidCostOption {
  id: string
  bidCode: string
  bidProjectName: string
}
