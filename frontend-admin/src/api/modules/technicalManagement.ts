import { request } from '@/api/request'

export type TechnicalRow = Record<string, unknown> & { id: string; status: string }

export interface TechnicalScheme extends TechnicalRow {
  projectId: string
  schemeCode: string
  schemeName: string
  schemeType: string
  responsibleUserId: string
  plannedEffectiveDate: string
  approvalInstanceId?: string
}

export interface TechnicalDrawing extends TechnicalRow {
  projectId: string
  drawingCode: string
  drawingName: string
  specialty: string
  sourceOrganization: string
  currentVersionId: string
  currentVersionNo: string
  currentVersionStatus: string
}

export interface DrawingVersion extends TechnicalRow {
  drawingId: string
  drawingCode: string
  versionNo: string
  previousVersionId?: string
  sourceRfiId?: string
  receivedAt: string
  changeSummary?: string
}

export interface DrawingReview extends TechnicalRow {
  drawingVersionId: string
  reviewCode: string
  reviewDate: string
  chairUserId: string
  participantSummary: string
  conclusion: 'PASS' | 'CONDITIONAL' | 'REJECTED'
  reviewSummary: string
  requiresRfi: boolean | number
}

export interface TechnicalRfi extends TechnicalRow {
  drawingVersionId: string
  reviewId: string
  rfiCode: string
  subject: string
  priority: 'NORMAL' | 'HIGH' | 'URGENT'
  responseDueDate: string
}

export interface RfiResponse extends TechnicalRow {
  rfiId: string
  responseContent: string
  changeRequired: boolean | number
  responderName: string
  respondedBy: string
  reviewStatus: 'PENDING' | 'ACCEPTED' | 'REJECTED'
}

export interface TechnicalDisclosure extends TechnicalRow {
  drawingVersionId: string
  schemeId?: string
  disclosureCode: string
  disclosureTitle: string
  disclosureDate: string
  presenterUserId: string
}

export interface ConstructionReference extends TechnicalRow {
  drawingVersionId: string
  disclosureId: string
  dailyLogId: string
  wbsTaskId: string
  referenceDate: string
  workArea: string
}

export interface AcceptanceArchive extends TechnicalRow {
  drawingVersionId: string
  constructionReferenceId: string
  qualityInspectionId: string
  archiveCode: string
  acceptanceDate: string
  acceptanceConclusion: string
  archiveLocation: string
}

export interface ConstructionFact {
  progressId: string
  dailyLogId: string
  reportDate: string
  wbsTaskId: string
  taskCode: string
  taskName: string
  workArea?: string
  currentProgress: number
  completedQuantity: number
}

export interface QualityInspectionFact {
  id: string
  inspectionCode: string
  inspectionDate: string
  location?: string
  conclusion: 'PASS'
  status: 'SUBMITTED'
}

export interface TechnicalOverview {
  schemes: TechnicalScheme[]
  drawings: TechnicalDrawing[]
  versions: DrawingVersion[]
  reviews: DrawingReview[]
  rfis: TechnicalRfi[]
  responses: RfiResponse[]
  disclosures: TechnicalDisclosure[]
  constructionReferences: ConstructionReference[]
  archives: AcceptanceArchive[]
  constructionFacts: ConstructionFact[]
  qualityInspections: QualityInspectionFact[]
}

export interface DrawingTrace {
  drawing: TechnicalDrawing
  versions: DrawingVersion[]
  reviews: DrawingReview[]
  rfis: TechnicalRfi[]
  responses: RfiResponse[]
  disclosures: TechnicalDisclosure[]
  schemes: TechnicalScheme[]
  schemeApprovals: TechnicalRow[]
  constructionReferences: ConstructionReference[]
  archives: AcceptanceArchive[]
}

export const getTechnicalOverview = (projectId: string) =>
  request<TechnicalOverview>({
    url: '/technical-management/overview',
    method: 'get',
    params: { projectId },
  })
export const createTechnicalScheme = (data: Record<string, unknown>) =>
  request<TechnicalScheme>({ url: '/technical-management/schemes', method: 'post', data })
export const submitTechnicalScheme = (id: string) =>
  request<TechnicalScheme>({ url: `/technical-management/schemes/${id}/submit`, method: 'post' })
export const receiveTechnicalDrawing = (data: Record<string, unknown>) =>
  request<DrawingTrace>({ url: '/technical-management/drawings', method: 'post', data })
export const receiveDrawingVersion = (drawingId: string, data: Record<string, unknown>) =>
  request<DrawingVersion>({
    url: `/technical-management/drawings/${drawingId}/versions`,
    method: 'post',
    data,
  })
export const createDrawingReview = (versionId: string, data: Record<string, unknown>) =>
  request<DrawingReview>({
    url: `/technical-management/drawing-versions/${versionId}/reviews`,
    method: 'post',
    data,
  })
export const confirmDrawingReview = (id: string) =>
  request<DrawingReview>({ url: `/technical-management/reviews/${id}/confirm`, method: 'post' })
export const createTechnicalRfi = (reviewId: string, data: Record<string, unknown>) =>
  request<TechnicalRfi>({
    url: `/technical-management/reviews/${reviewId}/rfis`,
    method: 'post',
    data,
  })
export const submitTechnicalRfi = (id: string) =>
  request<TechnicalRfi>({ url: `/technical-management/rfis/${id}/submit`, method: 'post' })
export const respondTechnicalRfi = (id: string, data: Record<string, unknown>) =>
  request<RfiResponse>({ url: `/technical-management/rfis/${id}/responses`, method: 'post', data })
export const reviewTechnicalRfiResponse = (id: string, data: Record<string, unknown>) =>
  request<RfiResponse>({
    url: `/technical-management/rfi-responses/${id}/review`,
    method: 'post',
    data,
  })
export const createTechnicalDisclosure = (projectId: string, data: Record<string, unknown>) =>
  request<TechnicalDisclosure>({
    url: `/technical-management/projects/${projectId}/disclosures`,
    method: 'post',
    data,
  })
export const confirmTechnicalDisclosure = (id: string) =>
  request<TechnicalDisclosure>({
    url: `/technical-management/disclosures/${id}/confirm`,
    method: 'post',
  })
export const createConstructionReference = (projectId: string, data: Record<string, unknown>) =>
  request<ConstructionReference>({
    url: `/technical-management/projects/${projectId}/construction-references`,
    method: 'post',
    data,
  })
export const createAcceptanceArchive = (projectId: string, data: Record<string, unknown>) =>
  request<AcceptanceArchive>({
    url: `/technical-management/projects/${projectId}/archives`,
    method: 'post',
    data,
  })
export const confirmAcceptanceArchive = (id: string) =>
  request<AcceptanceArchive>({
    url: `/technical-management/archives/${id}/confirm`,
    method: 'post',
  })
export const getDrawingTrace = (id: string) =>
  request<DrawingTrace>({ url: `/technical-management/drawings/${id}/trace`, method: 'get' })
