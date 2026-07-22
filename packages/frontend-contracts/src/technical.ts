export type TechnicalStatus =
  | "ACTIVE"
  | "DRAFT"
  | "PENDING"
  | "APPROVED"
  | "REJECTED"
  | "SUPERSEDED"
  | "RECEIVED"
  | "UNDER_REVIEW"
  | "RFI_PENDING"
  | "CONFIRMED"
  | "SUBMITTED"
  | "RESPONDED"
  | "CHANGE_PENDING"
  | "CLOSED"
  | "ACCEPTED"
  | "RECORDED"
  | "ARCHIVED"
  | "CANCELLED"
  | "VOID";

export interface TechnicalRow {
  id: string;
  status: TechnicalStatus;
  remark?: string | null;
}
export interface TechnicalScheme extends TechnicalRow {
  projectId: string;
  schemeCode: string;
  schemeName: string;
  schemeType:
    "GENERAL" | "SPECIAL" | "CONSTRUCTION_ORGANIZATION" | "METHOD_STATEMENT";
  responsibleUserId: string;
  plannedEffectiveDate: string;
  approvalInstanceId?: string | null;
}
export interface TechnicalDrawing extends TechnicalRow {
  projectId: string;
  drawingCode: string;
  drawingName: string;
  specialty: string;
  sourceOrganization: string;
  currentVersionId: string;
  currentVersionNo: string;
  currentVersionStatus: TechnicalStatus;
}
export interface DrawingVersion extends TechnicalRow {
  drawingId: string;
  drawingCode?: string;
  versionNo: string;
  previousVersionId?: string | null;
  sourceRfiId?: string | null;
  receivedAt: string;
  changeSummary?: string | null;
}
export interface DrawingReview extends TechnicalRow {
  drawingVersionId: string;
  reviewCode: string;
  reviewDate: string;
  chairUserId: string;
  participantSummary: string;
  conclusion: "PASS" | "CONDITIONAL" | "REJECTED";
  reviewSummary: string;
  requiresRfi: boolean | number;
}
export interface TechnicalRfi extends TechnicalRow {
  drawingVersionId: string;
  reviewId: string;
  rfiCode: string;
  subject: string;
  priority: "NORMAL" | "HIGH" | "URGENT";
  responseDueDate: string;
}
export interface RfiResponse extends TechnicalRow {
  rfiId: string;
  responseContent: string;
  changeRequired: boolean | number;
  responderName: string;
  respondedBy: string;
  reviewStatus?: "SUBMITTED" | "ACCEPTED" | "REJECTED";
  reviewComment?: string | null;
}
export interface TechnicalDisclosure extends TechnicalRow {
  drawingVersionId: string;
  schemeId?: string | null;
  disclosureCode: string;
  disclosureTitle: string;
  disclosureDate: string;
  presenterUserId: string;
  recipientSummary: string;
  disclosureContent: string;
}
export interface ConstructionReference extends TechnicalRow {
  drawingVersionId: string;
  disclosureId: string;
  dailyLogId: string;
  wbsTaskId: string;
  referenceDate: string;
  workArea: string;
  referenceDescription: string;
}
export interface AcceptanceArchive extends TechnicalRow {
  drawingVersionId: string;
  constructionReferenceId: string;
  qualityInspectionId: string;
  archiveCode: string;
  acceptanceDate: string;
  acceptanceConclusion: "PASS" | "CONDITIONAL_PASS";
  archiveLocation: string;
}
export interface ConstructionFact {
  progressId: string;
  dailyLogId: string;
  reportDate: string;
  wbsTaskId: string;
  taskCode: string;
  taskName: string;
  workArea?: string | null;
  currentProgress: number;
  completedQuantity: number;
}
export interface QualityInspectionFact {
  id: string;
  inspectionCode: string;
  inspectionDate: string;
  location?: string | null;
  conclusion: "PASS";
  status: "SUBMITTED";
}
export interface TechnicalOverview {
  schemes: TechnicalScheme[];
  drawings: TechnicalDrawing[];
  versions: DrawingVersion[];
  reviews: DrawingReview[];
  rfis: TechnicalRfi[];
  responses: RfiResponse[];
  disclosures: TechnicalDisclosure[];
  constructionReferences: ConstructionReference[];
  archives: AcceptanceArchive[];
  constructionFacts: ConstructionFact[];
  qualityInspections: QualityInspectionFact[];
}
export interface DrawingTrace {
  drawing: TechnicalDrawing;
  versions: DrawingVersion[];
  reviews: DrawingReview[];
  rfis: TechnicalRfi[];
  responses: RfiResponse[];
  disclosures: TechnicalDisclosure[];
  schemes: TechnicalScheme[];
  schemeApprovals: Array<Record<string, unknown>>;
  constructionReferences: ConstructionReference[];
  archives: AcceptanceArchive[];
}

export interface SchemeCommand {
  projectId: string;
  schemeCode: string;
  schemeName: string;
  schemeType: TechnicalScheme["schemeType"];
  responsibleUserId: string;
  plannedEffectiveDate: string;
  remark?: string;
}
export interface DrawingReceiptCommand {
  projectId: string;
  drawingCode: string;
  drawingName: string;
  specialty: string;
  sourceOrganization: string;
  versionNo: string;
  receivedAt: string;
  changeSummary?: string;
  remark?: string;
}
export interface DrawingVersionCommand {
  versionNo: string;
  previousVersionId: string;
  sourceRfiId: string;
  receivedAt: string;
  changeSummary: string;
  remark?: string;
}
export interface ReviewCommand {
  reviewCode: string;
  reviewDate: string;
  chairUserId: string;
  participantSummary: string;
  conclusion: DrawingReview["conclusion"];
  reviewSummary: string;
  requiresRfi: boolean;
  remark?: string;
}
export interface RfiCommand {
  rfiCode: string;
  subject: string;
  question: string;
  priority: TechnicalRfi["priority"];
  responseDueDate: string;
  remark?: string;
}
export interface RfiResponseCommand {
  responseContent: string;
  changeRequired: boolean;
  responderName: string;
}
export interface ResponseReviewCommand {
  decision: "ACCEPTED" | "REJECTED";
  reviewComment: string;
}
export interface DisclosureCommand {
  drawingVersionId: string;
  schemeId?: string | null;
  disclosureCode: string;
  disclosureTitle: string;
  disclosureDate: string;
  presenterUserId: string;
  recipientSummary: string;
  disclosureContent: string;
  remark?: string;
}
export interface ConstructionReferenceCommand {
  disclosureId: string;
  dailyLogId: string;
  wbsTaskId: string;
  referenceDate: string;
  workArea: string;
  referenceDescription: string;
  remark?: string;
}
export interface ArchiveCommand {
  constructionReferenceId: string;
  qualityInspectionId: string;
  archiveCode: string;
  acceptanceDate: string;
  acceptanceConclusion: AcceptanceArchive["acceptanceConclusion"];
  archiveLocation: string;
  remark?: string;
}

export const TECHNICAL_API = {
  overview: "/technical-management/overview",
  schemes: "/technical-management/schemes",
  submitScheme: (id: string) => `/technical-management/schemes/${id}/submit`,
  drawings: "/technical-management/drawings",
  versions: (id: string) => `/technical-management/drawings/${id}/versions`,
  reviews: (id: string) =>
    `/technical-management/drawing-versions/${id}/reviews`,
  confirmReview: (id: string) => `/technical-management/reviews/${id}/confirm`,
  rfis: (id: string) => `/technical-management/reviews/${id}/rfis`,
  submitRfi: (id: string) => `/technical-management/rfis/${id}/submit`,
  responses: (id: string) => `/technical-management/rfis/${id}/responses`,
  reviewResponse: (id: string) =>
    `/technical-management/rfi-responses/${id}/review`,
  disclosures: (id: string) =>
    `/technical-management/projects/${id}/disclosures`,
  confirmDisclosure: (id: string) =>
    `/technical-management/disclosures/${id}/confirm`,
  references: (id: string) =>
    `/technical-management/projects/${id}/construction-references`,
  archives: (id: string) => `/technical-management/projects/${id}/archives`,
  confirmArchive: (id: string) =>
    `/technical-management/archives/${id}/confirm`,
  trace: (id: string) => `/technical-management/drawings/${id}/trace`,
} as const;
