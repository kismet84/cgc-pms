export type QualityPlanStatus = "DRAFT" | "ACTIVE" | "COMPLETED" | "CANCELLED";
export type QualityIssueStatus =
  "OPEN" | "RECTIFYING" | "PENDING_REINSPECTION" | "CLOSED";
export type QualitySeverity = "LOW" | "MEDIUM" | "HIGH" | "CRITICAL";

export interface QualityPlanRecord {
  id: string;
  projectId: string;
  planCode: string;
  planName: string;
  inspectionType: "QUALITY" | "SAFETY";
  frequencyType: "SINGLE" | "WEEKLY" | "MONTHLY";
  startDate: string;
  endDate: string;
  ownerUserId: string;
  status: QualityPlanStatus;
  version?: number;
  remark?: string | null;
}

export interface QualityInspectionRecord {
  id: string;
  planId: string;
  projectId: string;
  inspectionCode: string;
  inspectionDate: string;
  location: string;
  inspectorUserId: string;
  conclusion: "PENDING" | "PASS" | "ISSUES";
  summary: string;
  status: "DRAFT" | "SUBMITTED";
  version?: number;
  remark?: string | null;
}

export interface QualityIssueRecord {
  id: string;
  planId: string;
  inspectionId: string;
  projectId: string;
  issueCode: string;
  issueType: "QUALITY" | "SAFETY";
  category: string;
  severity: QualitySeverity;
  title: string;
  description: string;
  responsibleKind: "INTERNAL" | "PARTNER";
  responsiblePartnerId?: string | null;
  responsibleUserId: string;
  dueDate: string;
  status: QualityIssueStatus;
  version?: number;
  remark?: string | null;
}

export interface QualityRectificationRecord {
  id: string;
  issueId: string;
  projectId: string;
  roundNo: number;
  actionDescription: string;
  responsibleUserId: string;
  plannedCompleteDate: string;
  actualCompletedAt?: string | null;
  status: "DRAFT" | "SUBMITTED" | "PASSED" | "REJECTED";
  reinspectionComment?: string | null;
  version?: number;
  remark?: string | null;
}

export interface QualityConsequenceRecord {
  id: string;
  issueId: string;
  projectId: string;
  partnerId: string;
  contractId: string;
  consequenceCode: string;
  decisionType: "NONE" | "FINE" | "REWORK_COST" | "BOTH";
  fineAmount: string;
  reworkCostAmount: string;
  evaluationScore: string;
  evaluationComment: string;
  status: "DRAFT" | "POSTED";
  costItemId?: string | null;
  evaluationId?: string | null;
  version?: number;
  remark?: string | null;
}

export interface QualityTraceRecord {
  plan: QualityPlanRecord;
  inspection: QualityInspectionRecord;
  issue: QualityIssueRecord;
  rectifications: QualityRectificationRecord[];
  consequence?: QualityConsequenceRecord | null;
  evaluation?: Record<string, unknown> | null;
  costItem?: Record<string, unknown> | null;
}

export interface QualityPlanCommand {
  projectId: string;
  planCode: string;
  planName: string;
  inspectionType: QualityPlanRecord["inspectionType"];
  frequencyType: QualityPlanRecord["frequencyType"];
  startDate: string;
  endDate: string;
  ownerUserId: string;
  remark?: string;
}

export interface QualityInspectionCommand {
  planId: string;
  inspectionCode: string;
  inspectionDate: string;
  location: string;
  inspectorUserId: string;
  summary: string;
  remark?: string;
}

export interface QualityIssueCommand {
  inspectionId: string;
  category: string;
  severity: QualitySeverity;
  title: string;
  description: string;
  responsibleKind: QualityIssueRecord["responsibleKind"];
  responsiblePartnerId?: string;
  responsibleUserId: string;
  dueDate: string;
  remark?: string;
}

export interface QualityRectificationCommand {
  issueId: string;
  actionDescription: string;
  responsibleUserId: string;
  plannedCompleteDate: string;
  remark?: string;
}

export interface QualityReinspectionCommand {
  result: "PASS" | "REJECT";
  comment: string;
}

export interface QualityConsequenceCommand {
  issueId: string;
  partnerId: string;
  contractId: string;
  consequenceCode: string;
  decisionType: QualityConsequenceRecord["decisionType"];
  fineAmount: string;
  reworkCostAmount: string;
  evaluationScore: string;
  evaluationComment: string;
  remark?: string;
}

export const QUALITY_API = {
  plans: "/quality-safety/plans",
  plan: (id: string) => `/quality-safety/plans/${id}`,
  activatePlan: (id: string) => `/quality-safety/plans/${id}/activate`,
  completePlan: (id: string) => `/quality-safety/plans/${id}/complete`,
  inspections: "/quality-safety/inspections",
  inspectionIssues: (id: string) => `/quality-safety/inspections/${id}/issues`,
  submitInspection: (id: string) => `/quality-safety/inspections/${id}/submit`,
  issues: "/quality-safety/issues",
  rectifications: "/quality-safety/rectifications",
  submitRectification: (id: string) =>
    `/quality-safety/rectifications/${id}/submit`,
  reinspectRectification: (id: string) =>
    `/quality-safety/rectifications/${id}/reinspect`,
  consequences: "/quality-safety/consequences",
  postConsequence: (id: string) => `/quality-safety/consequences/${id}/post`,
  trace: (id: string) => `/quality-safety/issues/${id}/trace`,
} as const;
