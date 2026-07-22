export type CloseoutStatus =
  | 'INITIATED'
  | 'SECTION_ACCEPTANCE'
  | 'FINAL_ACCEPTANCE_PENDING'
  | 'FINAL_ACCEPTANCE_APPROVED'
  | 'FINAL_SETTLEMENT_BOUND'
  | 'TAIL_PAYMENT_COLLECTED'
  | 'WARRANTY_ACTIVE'
  | 'DEFECT_LIABILITY'
  | 'WARRANTY_RELEASED'
  | 'READY_TO_CLOSE'
  | 'CLOSED'

export type SectionAcceptanceStatus = 'DRAFT' | 'ACCEPTED'
export type FinalAcceptanceStatus = 'DRAFT' | 'REJECTED' | 'PENDING' | 'APPROVED'
export type WarrantyStatus = 'ACTIVE' | 'DEFECT_LIABILITY' | 'RELEASED'
export type DefectStatus = 'OPEN' | 'PENDING_VERIFICATION' | 'CLOSED'
export type ArchiveTransferStatus = 'DRAFT' | 'ACCEPTED'
export type CloseoutConclusion = 'PASS' | 'CONDITIONAL_PASS'

export interface ProjectCloseoutRecord {
  id: string
  projectId: string
  closeoutCode: string
  plannedCompletionDate: string
  actualCompletionDate?: string | null
  status: CloseoutStatus
  finalOwnerSettlementId?: string | null
  tailCollectionVerifiedAt?: string | null
  closedAt?: string | null
  remark?: string | null
}

export interface CloseoutSectionAcceptance {
  id: string
  closeoutId: string
  wbsTaskId: string
  taskCode?: string | null
  taskName?: string | null
  qualityInspectionId: string
  acceptanceCode: string
  acceptanceName: string
  acceptanceDate: string
  conclusion: CloseoutConclusion
  status: SectionAcceptanceStatus
  confirmedAt?: string | null
  remark?: string | null
}

export interface CloseoutFinalAcceptance {
  id: string
  closeoutId: string
  acceptanceCode: string
  acceptanceDate: string
  organizer: string
  participantSummary: string
  conclusion: CloseoutConclusion
  acceptanceSummary: string
  status: FinalAcceptanceStatus
  approvalInstanceId?: string | null
  approvedAt?: string | null
  remark?: string | null
}

export interface CloseoutSettlement {
  id: string
  contractId: string
  settlementCode: string
  settlementDate: string
  grossAmount: string
  retentionAmount: string
  netReceivableAmount: string
  status: string
  settlementType?: string | null
}

export interface CloseoutReceivable {
  id: string
  settlementId: string
  contractId: string
  receivableType: 'REGULAR' | 'RETENTION' | string
  receivableCode: string
  originalAmount: string
  collectedAmount: string
  outstandingAmount: string
  dueDate?: string | null
  status: string
}

export interface CloseoutWarranty {
  id: string
  closeoutId: string
  contractId: string
  receivableId: string
  warrantyCode: string
  warrantyAmount: string
  warrantyStartDate: string
  warrantyEndDate: string
  responsibleUserId: string
  status: WarrantyStatus
  releasedAt?: string | null
  remark?: string | null
}

export interface CloseoutDefect {
  id: string
  warrantyId: string
  closeoutId?: string
  projectId?: string
  defectCode: string
  defectTitle: string
  defectDescription: string
  responsibleUserId: string
  rectificationDeadline: string
  status: DefectStatus
  rectificationContent?: string | null
  rectifiedBy?: string | null
  rectifiedAt?: string | null
  verifiedBy?: string | null
  verifiedAt?: string | null
  verificationComment?: string | null
  remark?: string | null
}

export interface CloseoutArchiveTransfer {
  id: string
  closeoutId: string
  transferCode: string
  transferDate: string
  recipientOrganization: string
  recipientName: string
  archiveLocation: string
  transferScope: string
  status: ArchiveTransferStatus
  acceptedAt?: string | null
  remark?: string | null
}

export interface CloseoutWbsReadiness {
  totalTasks: number
  incompleteTasks: number
}

export interface CloseoutWbsTask {
  id: string
  taskCode: string
  taskName: string
  workArea?: string | null
  status: string
  actualProgress: string
}

export interface CloseoutQualityInspection {
  id: string
  inspectionCode: string
  inspectionDate: string
  location?: string | null
  conclusion: string
  status: string
}

export interface CloseoutApprovalRecord {
  id: string
  instanceId?: string | null
  action?: string | null
  comment?: string | null
  createdAt?: string | null
  createdBy?: string | null
  [key: string]: unknown
}

export interface CollectionAllocationRecord {
  id: string
  collectionId: string
  receivableId: string
  allocatedAmount: string
  allocationType?: string | null
  collectionCode?: string | null
  externalTxnNo?: string | null
  collectedAt?: string | null
  collectionStatus?: string | null
  receivableType?: string | null
  [key: string]: unknown
}

export interface CloseoutOverview {
  closeout?: ProjectCloseoutRecord | null
  sectionAcceptances: CloseoutSectionAcceptance[]
  finalAcceptances: CloseoutFinalAcceptance[]
  settlements: CloseoutSettlement[]
  receivables: CloseoutReceivable[]
  warranties: CloseoutWarranty[]
  defects: CloseoutDefect[]
  archiveTransfers: CloseoutArchiveTransfer[]
  wbsReadiness: CloseoutWbsReadiness
  wbsTasks: CloseoutWbsTask[]
  qualityInspections: CloseoutQualityInspection[]
}

export interface CloseoutTrace {
  closeout: ProjectCloseoutRecord
  project: Record<string, unknown>
  sectionAcceptances: Array<Record<string, unknown>>
  finalAcceptances: Array<Record<string, unknown>>
  approvalRecords: CloseoutApprovalRecord[]
  finalSettlement?: Record<string, unknown> | null
  receivables: Array<Record<string, unknown>>
  collectionAllocations: CollectionAllocationRecord[]
  warranties: Array<Record<string, unknown>>
  defects: Array<Record<string, unknown>>
  archiveTransfers: Array<Record<string, unknown>>
}

export interface InitiateCloseoutCommand {
  projectId: string
  closeoutCode: string
  plannedCompletionDate: string
  remark?: string
}

export interface SectionAcceptanceCommand {
  wbsTaskId: string
  qualityInspectionId: string
  acceptanceCode: string
  acceptanceName: string
  acceptanceDate: string
  conclusion: CloseoutConclusion
  remark?: string
}

export interface FinalAcceptanceCommand {
  acceptanceCode: string
  acceptanceDate: string
  organizer: string
  participantSummary: string
  conclusion: CloseoutConclusion
  acceptanceSummary: string
  remark?: string
}

export interface SettlementBindingCommand {
  ownerSettlementId: string
}

export interface WarrantyCommand {
  contractId: string
  receivableId: string
  warrantyCode: string
  warrantyAmount: string
  warrantyStartDate: string
  warrantyEndDate: string
  responsibleUserId: string
  remark?: string
}

export interface DefectCommand {
  defectCode: string
  defectTitle: string
  defectDescription: string
  responsibleUserId: string
  rectificationDeadline: string
  remark?: string
}

export interface RectificationCommand {
  rectificationContent: string
}

export interface DefectVerificationCommand {
  decision: 'ACCEPTED' | 'REJECTED'
  verificationComment: string
}

export interface ArchiveTransferCommand {
  transferCode: string
  transferDate: string
  recipientOrganization: string
  recipientName: string
  archiveLocation: string
  transferScope: string
  remark?: string
}

export interface CloseProjectCommand {
  actualCompletionDate: string
  reason: string
}

export const CLOSEOUT_API = {
  overview: '/project-closeouts/overview',
  initiate: '/project-closeouts',
  sectionAcceptances: (id: string) => `/project-closeouts/${id}/section-acceptances`,
  confirmSectionAcceptance: (id: string) => `/project-closeouts/section-acceptances/${id}/confirm`,
  finalAcceptance: (id: string) => `/project-closeouts/${id}/final-acceptance`,
  submitFinalAcceptance: (id: string) => `/project-closeouts/final-acceptances/${id}/submit`,
  finalSettlement: (id: string) => `/project-closeouts/${id}/final-settlement`,
  verifyTailCollection: (id: string) => `/project-closeouts/${id}/verify-tail-collection`,
  warranties: (id: string) => `/project-closeouts/${id}/warranties`,
  defects: (id: string) => `/project-closeouts/warranties/${id}/defects`,
  rectifyDefect: (id: string) => `/project-closeouts/defects/${id}/rectify`,
  verifyDefect: (id: string) => `/project-closeouts/defects/${id}/verify`,
  releaseWarranty: (id: string) => `/project-closeouts/warranties/${id}/release`,
  archiveTransfer: (id: string) => `/project-closeouts/${id}/archive-transfer`,
  acceptArchiveTransfer: (id: string) => `/project-closeouts/archive-transfers/${id}/accept`,
  close: (id: string) => `/project-closeouts/${id}/close`,
  trace: (id: string) => `/project-closeouts/${id}/trace`,
} as const
