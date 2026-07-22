import {
  QUALITY_API,
  type QualityConsequenceCommand,
  type QualityConsequenceRecord,
  type QualityInspectionCommand,
  type QualityInspectionRecord,
  type QualityIssueCommand,
  type QualityIssueRecord,
  type QualityPlanCommand,
  type QualityPlanRecord,
  type QualityRectificationCommand,
  type QualityRectificationRecord,
  type QualityReinspectionCommand,
  type QualityTraceRecord,
} from '@cgc-pms/frontend-contracts'
import { apiRequest } from '@/services/request'

export function loadQualityPlans(projectId: string, signal?: AbortSignal) {
  return apiRequest<QualityPlanRecord[]>(`${QUALITY_API.plans}?projectId=${id(projectId)}`, {
    signal,
  })
}
export function createQualityPlan(command: QualityPlanCommand) {
  return apiRequest<QualityPlanRecord, QualityPlanCommand>(QUALITY_API.plans, {
    method: 'POST',
    body: command,
  })
}
export function updateQualityPlan(planId: string, command: QualityPlanCommand) {
  return apiRequest<QualityPlanRecord, QualityPlanCommand>(QUALITY_API.plan(id(planId)), {
    method: 'PUT',
    body: command,
  })
}
export function activateQualityPlan(planId: string) {
  return apiRequest<QualityPlanRecord>(QUALITY_API.activatePlan(id(planId)), { method: 'POST' })
}
export function completeQualityPlan(planId: string) {
  return apiRequest<QualityPlanRecord>(QUALITY_API.completePlan(id(planId)), { method: 'POST' })
}
export function loadQualityInspections(planId: string, signal?: AbortSignal) {
  return apiRequest<QualityInspectionRecord[]>(`${QUALITY_API.inspections}?planId=${id(planId)}`, {
    signal,
  })
}
export function createQualityInspection(command: QualityInspectionCommand) {
  return apiRequest<QualityInspectionRecord, QualityInspectionCommand>(QUALITY_API.inspections, {
    method: 'POST',
    body: command,
  })
}
export function createQualityIssue(inspectionId: string, command: QualityIssueCommand) {
  return apiRequest<QualityIssueRecord, QualityIssueCommand>(
    QUALITY_API.inspectionIssues(id(inspectionId)),
    { method: 'POST', body: command },
  )
}
export function submitQualityInspection(inspectionId: string) {
  return apiRequest<QualityInspectionRecord>(QUALITY_API.submitInspection(id(inspectionId)), {
    method: 'POST',
  })
}
export function loadQualityIssues(projectId: string, status?: string, signal?: AbortSignal) {
  const query = new URLSearchParams({ projectId: required(projectId) })
  if (status?.trim()) query.set('status', status.trim())
  return apiRequest<QualityIssueRecord[]>(`${QUALITY_API.issues}?${query.toString()}`, { signal })
}
export function createQualityRectification(command: QualityRectificationCommand) {
  return apiRequest<QualityRectificationRecord, QualityRectificationCommand>(
    QUALITY_API.rectifications,
    { method: 'POST', body: command },
  )
}
export function submitQualityRectification(rectificationId: string) {
  return apiRequest<QualityRectificationRecord>(
    QUALITY_API.submitRectification(id(rectificationId)),
    { method: 'POST' },
  )
}
export function reinspectQualityRectification(
  rectificationId: string,
  command: QualityReinspectionCommand,
) {
  return apiRequest<QualityRectificationRecord, QualityReinspectionCommand>(
    QUALITY_API.reinspectRectification(id(rectificationId)),
    { method: 'POST', body: command },
  )
}
export function createQualityConsequence(command: QualityConsequenceCommand) {
  return apiRequest<Record<string, unknown>, QualityConsequenceCommand>(QUALITY_API.consequences, {
    method: 'POST',
    body: command,
  }).then(normalizeConsequence)
}
export function postQualityConsequence(consequenceId: string) {
  return apiRequest<Record<string, unknown>>(QUALITY_API.postConsequence(id(consequenceId)), {
    method: 'POST',
  }).then(normalizeConsequence)
}
export function loadQualityTrace(
  issueId: string,
  signal?: AbortSignal,
): Promise<QualityTraceRecord> {
  return apiRequest<QualityTraceRecord>(QUALITY_API.trace(id(issueId)), { signal }).then(
    (trace) => ({
      ...trace,
      consequence: trace.consequence
        ? normalizeConsequence(trace.consequence as unknown as Record<string, unknown>)
        : null,
    }),
  )
}

function normalizeConsequence(row: Record<string, unknown>): QualityConsequenceRecord {
  return {
    ...(row as unknown as QualityConsequenceRecord),
    id: String(row.id),
    issueId: String(row.issueId),
    projectId: String(row.projectId),
    partnerId: String(row.partnerId),
    contractId: String(row.contractId),
    fineAmount: String(row.fineAmount),
    reworkCostAmount: String(row.reworkCostAmount),
    evaluationScore: String(row.evaluationScore),
  }
}
function required(value: string): string {
  const safe = value.trim()
  if (!safe) throw new TypeError('ID不能为空')
  return safe
}
function id(value: string): string {
  return encodeURIComponent(required(value))
}
