import type { PageResult } from "./api";

export type SchedulePlanType = "BASELINE" | "REVISION";
export type SchedulePlanStatus =
  "DRAFT" | "PENDING" | "ACTIVE" | "APPROVED" | "REJECTED" | "SUPERSEDED";

export type ScheduleDeviationStatus =
  "ON_TRACK" | "LAGGING" | "OVERDUE" | "COMPLETED";
export type PeriodType = "MONTHLY" | "WEEKLY";
export type SiteDailyLogStatus = "DRAFT" | "SUBMITTED";

export interface ScheduleRecord {
  id: string;
  projectId: string;
  planCode: string;
  planName: string;
  planType: SchedulePlanType;
  versionNo: number;
  parentPlanId?: string | null;
  correctiveActionId?: string | null;
  plannedStartDate: string;
  plannedEndDate: string;
  status: SchedulePlanStatus;
  version?: number;
  approvalInstanceId?: string | null;
  activatedAt?: string | null;
  remark?: string | null;
}

export interface WbsTaskRecord {
  id: string;
  schedulePlanId: string;
  parentTaskId?: string | null;
  predecessorTaskId?: string | null;
  taskCode: string;
  taskName: string;
  workArea?: string | null;
  responsibleUserId?: string | null;
  plannedStartDate: string;
  plannedEndDate: string;
  weightPercent: string;
  plannedQuantity?: string | null;
  unit?: string | null;
  actualStartDate?: string | null;
  actualEndDate?: string | null;
  actualQuantity?: string | null;
  actualProgress: string;
  status: string;
  sortOrder?: number;
  parentTaskCode?: string | null;
  predecessorTaskCode?: string | null;
  remark?: string | null;
}

export interface PeriodPlanRecord {
  id: string;
  projectId: string;
  schedulePlanId: string;
  parentPeriodPlanId?: string | null;
  periodType: PeriodType;
  periodCode: string;
  periodName: string;
  startDate: string;
  endDate: string;
  status: SchedulePlanStatus;
  version?: number;
  approvalInstanceId?: string | null;
  remark?: string | null;
}

export interface PeriodPlanItemRecord {
  id: string;
  wbsTaskId: string;
  taskCode: string;
  taskName: string;
  targetProgress: string;
  plannedQuantity?: string | null;
  actualProgress: string;
}

export interface PeriodPlanDetail extends PeriodPlanRecord {
  items: PeriodPlanItemRecord[];
}

export interface ScheduleSnapshotRecord {
  id: string;
  projectId: string;
  schedulePlanId: string;
  snapshotDate: string;
  sourceDailyLogId?: string | null;
  plannedProgress: string;
  actualProgress: string;
  deviationPercent: string;
  laggingTaskCount: number;
  status: ScheduleDeviationStatus;
}

export interface CorrectiveActionRecord {
  id: string;
  schedulePlanId: string;
  snapshotId: string;
  actionCode: string;
  reason: string;
  actionPlan: string;
  responsibleUserId: string;
  dueDate: string;
  status: string;
  approvalInstanceId?: string | null;
  revisionSchedulePlanId?: string | null;
  remark?: string | null;
}

export interface ScheduleDetail extends ScheduleRecord {
  tasks: WbsTaskRecord[];
  periodPlans: PeriodPlanRecord[];
  latestSnapshot: ScheduleSnapshotRecord | null;
  correctiveActions: CorrectiveActionRecord[];
}

export interface ScheduleTraceRecord {
  schedule: ScheduleRecord;
  wbsTasks: WbsTaskRecord[];
  periodPlans: PeriodPlanRecord[];
  dailyProgress: Record<string, unknown>[];
  snapshots: ScheduleSnapshotRecord[];
  alerts: Record<string, unknown>[];
  correctiveActions: CorrectiveActionRecord[];
  revisions: ScheduleRecord[];
}

export interface ScheduleCommand {
  projectId: string;
  planCode: string;
  planName: string;
  plannedStartDate: string;
  plannedEndDate: string;
  remark?: string;
}

export interface WbsTaskCommand {
  taskCode: string;
  taskName: string;
  parentTaskCode?: string;
  predecessorTaskCode?: string;
  workArea?: string;
  responsibleUserId?: string;
  plannedStartDate: string;
  plannedEndDate: string;
  weightPercent: string;
  plannedQuantity?: string;
  unit?: string;
  remark?: string;
}

export interface PeriodPlanCommand {
  schedulePlanId: string;
  periodType: PeriodType;
  parentPeriodPlanId?: string;
  periodCode: string;
  periodName: string;
  startDate: string;
  endDate: string;
  remark?: string;
}

export interface PeriodPlanItemCommand {
  wbsTaskId: string;
  targetProgress: string;
  plannedQuantity?: string;
}

export interface DailyProgressCommand {
  wbsTaskId: string;
  currentProgress: string;
  completedQuantity: string;
  workDescription: string;
}

export interface CorrectiveActionCommand {
  snapshotId: string;
  actionCode: string;
  reason: string;
  actionPlan: string;
  responsibleUserId: string;
  dueDate: string;
  remark?: string;
}

export interface SiteDailyDeliveryRecord {
  receiptItemId: string;
  receiptId: string;
  receiptCode: string;
  partnerName?: string | null;
  materialId?: string | null;
  materialName?: string | null;
  actualQuantity?: string | null;
  qualifiedQuantity?: string | null;
}

export interface SiteDailyPlannedTaskRecord {
  id: string;
  taskCode: string;
  taskName: string;
  workArea?: string | null;
  plannedStartDate: string;
  plannedEndDate: string;
  status: string;
  progressPercent?: string | null;
}

export interface SiteDailyRequisitionRecord {
  requisitionId: string;
  requisitionCode: string;
  requisitionItemId: string;
  materialId: string;
  materialName?: string | null;
  materialUnit?: string | null;
  quantity?: string | null;
  useLocation?: string | null;
}

export interface SiteDailyAuditEntryRecord {
  operationType: string;
  userId?: string | null;
  success: boolean;
  createdAt?: string | null;
}

export interface SiteDailyQualitySafetyRecord {
  inspectionId: string;
  inspectionCode: string;
  location?: string | null;
  conclusion?: string | null;
  issueCount: number;
  highSeverityIssueCount: number;
  openIssueCount: number;
}

export interface SiteDailyLogRecord {
  id: string;
  projectId: string;
  projectName?: string | null;
  reportDate: string;
  constructionContent: string;
  issuesDelays?: string | null;
  nextDayPlan?: string | null;
  weatherSummary?: string | null;
  onSiteHeadcount?: number | null;
  deliveries?: SiteDailyDeliveryRecord[];
  requisitions?: SiteDailyRequisitionRecord[];
  plannedTasks?: SiteDailyPlannedTaskRecord[];
  scheduleManaged?: boolean;
  auditTrail?: SiteDailyAuditEntryRecord[];
  status: SiteDailyLogStatus;
  submittedBy?: string | null;
  submittedAt?: string | null;
  createdBy?: string | null;
  createdAt?: string | null;
  updatedAt?: string | null;
}

export interface SiteDailyLogCommand {
  projectId?: string;
  reportDate?: string;
  constructionContent: string;
  issuesDelays?: string;
  nextDayPlan?: string;
  weatherSummary?: string;
  onSiteHeadcount?: number | null;
  expectedUpdatedAt?: string;
}

export interface SiteDailyLogQuery {
  pageNo?: number;
  pageSize?: number;
  projectId?: string;
  startDate?: string;
  endDate?: string;
  status?: SiteDailyLogStatus;
}

export interface SiteFileRecord {
  id: string;
  originalName: string;
  businessType?: string | null;
  businessId?: string | null;
  createdAt?: string | null;
}

export type SiteDailyLogPage = PageResult<SiteDailyLogRecord>;

export const DELIVERY_API = {
  schedules: "/project-schedules",
  schedule: (id: string) => `/project-schedules/${encodeURIComponent(id)}`,
  scheduleTasks: (id: string) =>
    `/project-schedules/${encodeURIComponent(id)}/tasks`,
  scheduleSubmit: (id: string) =>
    `/project-schedules/${encodeURIComponent(id)}/submit`,
  schedulePeriods: (id: string) =>
    `/project-schedules/${encodeURIComponent(id)}/period-plans`,
  period: (id: string) =>
    `/project-schedules/period-plans/${encodeURIComponent(id)}`,
  periodItems: (id: string) =>
    `/project-schedules/period-plans/${encodeURIComponent(id)}/items`,
  periodSubmit: (id: string) =>
    `/project-schedules/period-plans/${encodeURIComponent(id)}/submit`,
  dailyProgress: (dailyLogId: string) =>
    `/project-schedules/daily-logs/${encodeURIComponent(dailyLogId)}/progress`,
  scheduleSnapshots: (id: string) =>
    `/project-schedules/${encodeURIComponent(id)}/snapshots`,
  correctiveActions: (id: string) =>
    `/project-schedules/${encodeURIComponent(id)}/corrective-actions`,
  correctiveSubmit: (id: string) =>
    `/project-schedules/corrective-actions/${encodeURIComponent(id)}/submit`,
  scheduleTrace: (id: string) =>
    `/project-schedules/${encodeURIComponent(id)}/trace`,
  siteDailyLogs: "/site-daily-logs",
  siteDailyLog: (id: string) => `/site-daily-logs/${encodeURIComponent(id)}`,
  siteDailyQualitySafety: (id: string) =>
    `/site-daily-logs/${encodeURIComponent(id)}/quality-safety`,
  siteDailySubmit: (id: string) =>
    `/site-daily-logs/${encodeURIComponent(id)}/submit`,
  files: "/files",
  fileUpload: "/files/upload",
  fileUrl: (id: string) => `/files/${encodeURIComponent(id)}/url`,
  fileDelete: (id: string) => `/files/${encodeURIComponent(id)}`,
} as const;
