export type SiteDailyLogStatus = 'DRAFT' | 'SUBMITTED'

export interface SiteDailyDeliveryVO {
  receiptItemId: string
  receiptId: string
  receiptCode: string
  partnerName?: string
  materialId?: string
  materialName?: string
  actualQuantity?: string
  qualifiedQuantity?: string
}

export interface SiteDailyLogVO {
  id: string
  projectId: string
  projectName?: string
  reportDate: string
  constructionContent: string
  issuesDelays?: string
  nextDayPlan?: string
  weatherSummary?: string
  onSiteHeadcount?: number | null
  deliveries?: SiteDailyDeliveryVO[]
  status: SiteDailyLogStatus
  submittedBy?: string
  submittedAt?: string
  createdBy?: string
  createdAt?: string
  updatedAt?: string
}

export interface SiteDailyLogCommand {
  projectId?: string
  reportDate?: string
  constructionContent: string
  issuesDelays?: string
  nextDayPlan?: string
  weatherSummary?: string
  onSiteHeadcount?: number | null
}
