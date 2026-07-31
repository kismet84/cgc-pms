import { request } from '@/api/request'

export type CloseoutRow = Record<string, unknown> & { id: string; status: string }

export interface ProjectCloseout extends CloseoutRow {
  projectId: string
  closeoutCode: string
  plannedCompletionDate: string
  actualCompletionDate?: string
  finalOwnerSettlementId?: string
  tailCollectionVerifiedAt?: string
  closedAt?: string
}

export interface SectionAcceptance extends CloseoutRow {
  closeoutId: string
  wbsTaskId: string
  taskCode: string
  taskName: string
  qualityInspectionId: string
  acceptanceCode: string
  acceptanceName: string
  acceptanceDate: string
  conclusion: string
}

export interface FinalAcceptance extends CloseoutRow {
  closeoutId: string
  acceptanceCode: string
  acceptanceDate: string
  organizer: string
  participantSummary: string
  conclusion: string
  acceptanceSummary: string
  approvalInstanceId?: string
}

export interface CloseoutSettlement extends CloseoutRow {
  contractId: string
  settlementCode: string
  grossAmount: number
  retentionAmount: number
  netReceivableAmount: number
  settlementType: string
}

export interface CloseoutReceivable extends CloseoutRow {
  settlementId: string
  contractId: string
  receivableType: 'REGULAR' | 'RETENTION'
  receivableCode: string
  originalAmount: number
  collectedAmount: number
  outstandingAmount: number
  dueDate: string
}

export interface CloseoutWarranty extends CloseoutRow {
  closeoutId: string
  contractId: string
  receivableId: string
  warrantyCode: string
  warrantyAmount: number
  warrantyStartDate: string
  warrantyEndDate: string
  responsibleUserId: string
}

export interface CloseoutDefect extends CloseoutRow {
  warrantyId: string
  defectCode: string
  defectTitle: string
  responsibleUserId: string
  rectificationDeadline: string
  rectifiedBy?: string
  verifiedBy?: string
  verificationComment?: string
}

export interface ArchiveTransfer extends CloseoutRow {
  closeoutId: string
  transferCode: string
  transferDate: string
  recipientOrganization: string
  recipientName: string
  archiveLocation: string
  transferScope: string
}

export interface CloseoutOverview {
  closeout?: ProjectCloseout
  sectionAcceptances: SectionAcceptance[]
  finalAcceptances: FinalAcceptance[]
  settlements: CloseoutSettlement[]
  receivables: CloseoutReceivable[]
  warranties: CloseoutWarranty[]
  defects: CloseoutDefect[]
  archiveTransfers: ArchiveTransfer[]
  wbsReadiness: { totalTasks: number; incompleteTasks: number }
  wbsTasks: CloseoutRow[]
  qualityInspections: CloseoutRow[]
}

export interface CloseoutTrace {
  closeout: ProjectCloseout
  project: CloseoutRow
  sectionAcceptances: CloseoutRow[]
  finalAcceptances: CloseoutRow[]
  approvalRecords: CloseoutRow[]
  finalSettlement?: CloseoutRow
  receivables: CloseoutRow[]
  collectionAllocations: CloseoutRow[]
  warranties: CloseoutRow[]
  defects: CloseoutRow[]
  archiveTransfers: CloseoutRow[]
}

export const getCloseoutOverview = (projectId: string) =>
  request<CloseoutOverview>({
    url: '/project-closeouts/overview',
    method: 'get',
    params: { projectId },
  })
export const initiateProjectCloseout = (data: Record<string, unknown>) =>
  request<ProjectCloseout>({ url: '/project-closeouts', method: 'post', data })
export const createSectionAcceptance = (closeoutId: string, data: Record<string, unknown>) =>
  request<SectionAcceptance>({
    url: `/project-closeouts/${closeoutId}/section-acceptances`,
    method: 'post',
    data,
  })
export const confirmSectionAcceptance = (id: string) =>
  request<SectionAcceptance>({
    url: `/project-closeouts/section-acceptances/${id}/confirm`,
    method: 'post',
  })
export const createFinalAcceptance = (closeoutId: string, data: Record<string, unknown>) =>
  request<FinalAcceptance>({
    url: `/project-closeouts/${closeoutId}/final-acceptance`,
    method: 'post',
    data,
  })
export const submitFinalAcceptance = (id: string) =>
  request<FinalAcceptance>({
    url: `/project-closeouts/final-acceptances/${id}/submit`,
    method: 'post',
  })
export const bindFinalSettlement = (closeoutId: string, ownerSettlementId: string) =>
  request<ProjectCloseout>({
    url: `/project-closeouts/${closeoutId}/final-settlement`,
    method: 'post',
    data: { ownerSettlementId },
  })
export const verifyTailCollection = (closeoutId: string) =>
  request<ProjectCloseout>({
    url: `/project-closeouts/${closeoutId}/verify-tail-collection`,
    method: 'post',
  })
export const registerCloseoutWarranty = (closeoutId: string, data: Record<string, unknown>) =>
  request<CloseoutWarranty>({
    url: `/project-closeouts/${closeoutId}/warranties`,
    method: 'post',
    data,
  })
export const createCloseoutDefect = (warrantyId: string, data: Record<string, unknown>) =>
  request<CloseoutDefect>({
    url: `/project-closeouts/warranties/${warrantyId}/defects`,
    method: 'post',
    data,
  })
export const rectifyCloseoutDefect = (id: string, data: Record<string, unknown>) =>
  request<CloseoutDefect>({ url: `/project-closeouts/defects/${id}/rectify`, method: 'post', data })
export const verifyCloseoutDefect = (id: string, data: Record<string, unknown>) =>
  request<CloseoutDefect>({ url: `/project-closeouts/defects/${id}/verify`, method: 'post', data })
export const releaseCloseoutWarranty = (id: string) =>
  request<CloseoutWarranty>({ url: `/project-closeouts/warranties/${id}/release`, method: 'post' })
export const createArchiveTransfer = (closeoutId: string, data: Record<string, unknown>) =>
  request<ArchiveTransfer>({
    url: `/project-closeouts/${closeoutId}/archive-transfer`,
    method: 'post',
    data,
  })
export const acceptArchiveTransfer = (id: string) =>
  request<ArchiveTransfer>({
    url: `/project-closeouts/archive-transfers/${id}/accept`,
    method: 'post',
  })
export const closeProjectFromCloseout = (closeoutId: string, data: Record<string, unknown>) =>
  request<CloseoutTrace>({ url: `/project-closeouts/${closeoutId}/close`, method: 'post', data })
export const getCloseoutTrace = (id: string) =>
  request<CloseoutTrace>({ url: `/project-closeouts/${id}/trace`, method: 'get' })
