import { request } from '@/api/request'

export interface QualityPlan {
  id: string
  projectId: string
  planCode: string
  planName: string
  inspectionType: 'QUALITY' | 'SAFETY'
  frequencyType: 'SINGLE' | 'WEEKLY' | 'MONTHLY'
  startDate: string
  endDate: string
  ownerUserId: string
  status: 'DRAFT' | 'ACTIVE' | 'COMPLETED' | 'CANCELLED'
  remark?: string
}

export interface QualityInspection {
  id: string
  planId: string
  projectId: string
  inspectionCode: string
  inspectionDate: string
  location: string
  inspectorUserId: string
  conclusion: 'PENDING' | 'PASS' | 'ISSUES'
  summary: string
  status: 'DRAFT' | 'SUBMITTED'
}

export interface QualityIssue {
  id: string
  planId: string
  inspectionId: string
  projectId: string
  issueCode: string
  issueType: 'QUALITY' | 'SAFETY'
  category: string
  severity: 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL'
  title: string
  description: string
  responsibleKind: 'INTERNAL' | 'PARTNER'
  responsiblePartnerId?: string
  responsibleUserId: string
  dueDate: string
  status: 'OPEN' | 'RECTIFYING' | 'PENDING_REINSPECTION' | 'CLOSED'
}

export interface QualityRectification {
  id: string
  issueId: string
  projectId: string
  roundNo: number
  actionDescription: string
  responsibleUserId: string
  plannedCompleteDate: string
  actualCompletedAt?: string
  status: 'DRAFT' | 'SUBMITTED' | 'PASSED' | 'REJECTED'
  reinspectionComment?: string
}

export interface QualityConsequence {
  id: string
  issueId: string
  partnerId: string
  contractId?: string
  consequenceCode: string
  decisionType: 'NONE' | 'FINE' | 'REWORK_COST' | 'BOTH'
  fineAmount: number
  reworkCostAmount: number
  evaluationScore: number
  evaluationComment: string
  status: 'DRAFT' | 'POSTED'
  costItemId?: string
  evaluationId?: string
}

export interface QualityTrace {
  plan: QualityPlan
  inspection: QualityInspection
  issue: QualityIssue
  rectifications: QualityRectification[]
  consequence?: QualityConsequence
  evaluation?: {
    id: string
    partnerId: string
    evaluationType: 'QUALITY' | 'SAFETY'
    score: number
    evaluationComment: string
    evaluatedAt: string
  }
  costItem?: { id: string; amount: number; sourceType: string; sourceId: string }
}

export interface PlanCommand {
  projectId: string
  planCode: string
  planName: string
  inspectionType: 'QUALITY' | 'SAFETY'
  frequencyType: 'SINGLE' | 'WEEKLY' | 'MONTHLY'
  startDate: string
  endDate: string
  ownerUserId: string
  remark?: string
}

export interface InspectionCommand {
  planId: string
  inspectionCode: string
  inspectionDate: string
  location: string
  inspectorUserId: string
  summary: string
  remark?: string
}

export interface IssueCommand {
  inspectionId: string
  category: string
  severity: QualityIssue['severity']
  title: string
  description: string
  responsibleKind: QualityIssue['responsibleKind']
  responsiblePartnerId?: string
  responsibleUserId: string
  dueDate: string
  remark?: string
}

export interface RectificationCommand {
  issueId: string
  actionDescription: string
  responsibleUserId: string
  plannedCompleteDate: string
  remark?: string
}

export interface ConsequenceCommand {
  issueId: string
  partnerId: string
  contractId?: string
  consequenceCode: string
  decisionType: QualityConsequence['decisionType']
  fineAmount: number
  reworkCostAmount: number
  evaluationScore: number
  evaluationComment: string
  remark?: string
}

export const getQualityPlans = (projectId: string) =>
  request<QualityPlan[]>({ url: '/quality-safety/plans', method: 'get', params: { projectId } })
export const createQualityPlan = (data: PlanCommand) =>
  request<QualityPlan>({ url: '/quality-safety/plans', method: 'post', data })
export const updateQualityPlan = (id: string, data: PlanCommand) =>
  request<QualityPlan>({ url: `/quality-safety/plans/${id}`, method: 'put', data })
export const activateQualityPlan = (id: string) =>
  request<QualityPlan>({ url: `/quality-safety/plans/${id}/activate`, method: 'post' })
export const completeQualityPlan = (id: string) =>
  request<QualityPlan>({ url: `/quality-safety/plans/${id}/complete`, method: 'post' })
export const getQualityInspections = (planId: string) =>
  request<QualityInspection[]>({
    url: '/quality-safety/inspections',
    method: 'get',
    params: { planId },
  })
export const createQualityInspection = (data: InspectionCommand) =>
  request<QualityInspection>({ url: '/quality-safety/inspections', method: 'post', data })
export const createQualityIssue = (inspectionId: string, data: IssueCommand) =>
  request<QualityIssue>({
    url: `/quality-safety/inspections/${inspectionId}/issues`,
    method: 'post',
    data,
  })
export const submitQualityInspection = (id: string) =>
  request<QualityInspection>({ url: `/quality-safety/inspections/${id}/submit`, method: 'post' })
export const getQualityIssues = (projectId: string, status?: string) =>
  request<QualityIssue[]>({
    url: '/quality-safety/issues',
    method: 'get',
    params: { projectId, status },
  })
export const createQualityRectification = (data: RectificationCommand) =>
  request<QualityRectification>({ url: '/quality-safety/rectifications', method: 'post', data })
export const submitQualityRectification = (id: string) =>
  request<QualityRectification>({
    url: `/quality-safety/rectifications/${id}/submit`,
    method: 'post',
  })
export const reinspectQualityRectification = (
  id: string,
  result: 'PASS' | 'REJECT',
  comment: string,
) =>
  request<QualityRectification>({
    url: `/quality-safety/rectifications/${id}/reinspect`,
    method: 'post',
    data: { result, comment },
  })
export const createQualityConsequence = (data: ConsequenceCommand) =>
  request<QualityConsequence>({ url: '/quality-safety/consequences', method: 'post', data })
export const postQualityConsequence = (id: string) =>
  request<QualityConsequence>({ url: `/quality-safety/consequences/${id}/post`, method: 'post' })
export const getQualityTrace = (issueId: string) =>
  request<QualityTrace>({ url: `/quality-safety/issues/${issueId}/trace`, method: 'get' })
