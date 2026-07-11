export type SiteDailyLogStatus = 'DRAFT' | 'SUBMITTED'

export interface SiteDailyLogVO {
  id: string
  projectId: string
  projectName?: string
  reportDate: string
  constructionContent: string
  issuesDelays?: string
  nextDayPlan?: string
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
}
