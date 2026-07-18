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

export interface SiteDailyPlannedTaskVO {
  id: string
  taskCode: string
  taskName: string
  workArea?: string
  plannedStartDate: string
  plannedEndDate: string
  status: string
  progressPercent?: string
}

export interface SiteDailyRequisitionVO {
  requisitionId: string
  requisitionCode: string
  requisitionItemId: string
  materialId: string
  materialName?: string
  materialUnit?: string
  quantity?: string
  useLocation?: string
}

export interface SiteDailyAuditEntryVO {
  operationType: string
  userId?: string
  success: boolean
  createdAt?: string
}

export interface SiteDailyQualitySafetyVO {
  inspectionId: string
  inspectionCode: string
  location?: string
  conclusion?: string
  issueCount: number
  highSeverityIssueCount: number
  openIssueCount: number
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
  requisitions?: SiteDailyRequisitionVO[]
  plannedTasks?: SiteDailyPlannedTaskVO[]
  scheduleManaged?: boolean
  auditTrail?: SiteDailyAuditEntryVO[]
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
