import { apiRequest } from '../request'
import { normalizeAuditRow, query, requiredId } from './normalize'
import type {
  ClassificationOverrideCommand,
  CostSubjectAuditRow,
  GovernanceFormOptions,
  ProjectConfigCommand,
  ProjectConfigurationRecord,
  RecalculationCommand,
  ReversalCommand,
} from './types'

function rows(value: unknown): CostSubjectAuditRow[] {
  return Array.isArray(value)
    ? value.map((item) => normalizeAuditRow(item as Record<string, unknown>))
    : []
}

export function overrideClassification(command: ClassificationOverrideCommand): Promise<string> {
  return apiRequest<string, ClassificationOverrideCommand>(
    '/cost-subject-v2/classification-overrides',
    {
      method: 'POST',
      body: command,
    },
  )
}

export function loadGovernanceFormOptions(signal?: AbortSignal): Promise<GovernanceFormOptions> {
  return apiRequest<Record<string, unknown>>('/cost-subject-v2/form-options', { signal }).then(
    (value) => ({
      projects: rows(value.projects) as unknown as GovernanceFormOptions['projects'],
      costSubjects: rows(value.costSubjects) as unknown as GovernanceFormOptions['costSubjects'],
      rulePlans: rows(value.rulePlans) as unknown as GovernanceFormOptions['rulePlans'],
      bidCosts: rows(value.bidCosts) as unknown as GovernanceFormOptions['bidCosts'],
      targetVersions: rows(
        value.targetVersions,
      ) as unknown as GovernanceFormOptions['targetVersions'],
      financeSources: rows(
        value.financeSources,
      ) as unknown as GovernanceFormOptions['financeSources'],
      pendingClassifications: rows(value.pendingClassifications),
    }),
  )
}

export function loadMappingVersionDetail(id: string): Promise<Record<string, unknown>> {
  return apiRequest<Record<string, unknown>>(`/cost-subject-v2/mapping-versions/${requiredId(id)}`)
}

export function generateInitialRulePlan(): Promise<Record<string, unknown>> {
  return apiRequest<Record<string, unknown>>('/cost-subject-v2/mapping-versions/generate-initial', {
    method: 'POST',
  })
}

export function validateRulePlan(id: string): Promise<CostSubjectAuditRow> {
  return apiRequest<Record<string, unknown>>(
    `/cost-subject-v2/mapping-versions/${requiredId(id)}/validate`,
    { method: 'POST' },
  ).then(normalizeAuditRow)
}

export function submitRulePlan(id: string): Promise<CostSubjectAuditRow> {
  return apiRequest<Record<string, unknown>>(
    `/cost-subject-v2/mapping-versions/${requiredId(id)}/submit`,
    { method: 'POST' },
  ).then(normalizeAuditRow)
}

export function trialRulePlan(
  id: string,
  sourceType: string,
  businessCategory: string,
  projectId: string,
): Promise<CostSubjectAuditRow> {
  return apiRequest<Record<string, unknown>>(
    `/cost-subject-v2/mapping-versions/${requiredId(id)}/trial?${query({
      sourceType,
      businessCategory,
      projectId,
    })}`,
  ).then(normalizeAuditRow)
}

export function diffRulePlan(id: string, baseId: string): Promise<CostSubjectAuditRow> {
  return apiRequest<Record<string, unknown>>(
    `/cost-subject-v2/mapping-versions/${requiredId(id)}/diff?${query({ baseId })}`,
  ).then(normalizeAuditRow)
}

export function loadProjectConfiguration(
  projectId: string,
  signal?: AbortSignal,
): Promise<ProjectConfigurationRecord> {
  return apiRequest<Record<string, unknown>>(
    `/cost-subject-v2/project-config?${query({ projectId })}`,
    { signal },
  ).then((value) => ({
    project: normalizeAuditRow((value.project ?? {}) as Record<string, unknown>),
    subjects: rows(value.subjects),
    requests: rows(value.requests),
  }))
}

export function createProjectConfigRequest(
  command: ProjectConfigCommand,
): Promise<CostSubjectAuditRow> {
  return apiRequest<Record<string, unknown>, ProjectConfigCommand>(
    '/cost-subject-v2/project-config-requests',
    { method: 'POST', body: command },
  ).then(normalizeAuditRow)
}

export function submitProjectConfigRequest(id: string): Promise<CostSubjectAuditRow> {
  return apiRequest<Record<string, unknown>>(
    `/cost-subject-v2/project-config-requests/${requiredId(id)}/submit`,
    { method: 'POST' },
  ).then(normalizeAuditRow)
}

export function cancelProjectConfigRequest(id: string): Promise<CostSubjectAuditRow> {
  return apiRequest<Record<string, unknown>>(
    `/cost-subject-v2/project-config-requests/${requiredId(id)}/cancel`,
    { method: 'POST' },
  ).then(normalizeAuditRow)
}

export function loadRecalculationBatches(signal?: AbortSignal): Promise<CostSubjectAuditRow[]> {
  return apiRequest<Record<string, unknown>[]>('/cost-subject-v2/recalculation-batches', {
    signal,
  }).then((value) => value.map(normalizeAuditRow))
}

export function createRecalculationBatch(
  command: RecalculationCommand,
): Promise<CostSubjectAuditRow> {
  return apiRequest<Record<string, unknown>, RecalculationCommand>(
    '/cost-subject-v2/recalculation-batches',
    { method: 'POST', body: command },
  ).then(normalizeAuditRow)
}

export function submitRecalculationBatch(id: string): Promise<CostSubjectAuditRow> {
  return apiRequest<Record<string, unknown>>(
    `/cost-subject-v2/recalculation-batches/${requiredId(id)}/submit`,
    { method: 'POST' },
  ).then(normalizeAuditRow)
}

export function cancelRecalculationBatch(id: string): Promise<void> {
  return apiRequest<void>(`/cost-subject-v2/recalculation-batches/${requiredId(id)}/cancel`, {
    method: 'POST',
  })
}

export function loadReversalRequests(signal?: AbortSignal): Promise<CostSubjectAuditRow[]> {
  return apiRequest<Record<string, unknown>[]>('/cost-subject-v2/reversal-requests', {
    signal,
  }).then((value) => value.map(normalizeAuditRow))
}

export function createReversalRequest(command: ReversalCommand): Promise<CostSubjectAuditRow> {
  return apiRequest<Record<string, unknown>, ReversalCommand>(
    '/cost-subject-v2/reversal-requests',
    { method: 'POST', body: command },
  ).then(normalizeAuditRow)
}

export function submitReversalRequest(id: string): Promise<CostSubjectAuditRow> {
  return apiRequest<Record<string, unknown>>(
    `/cost-subject-v2/reversal-requests/${requiredId(id)}/submit`,
    { method: 'POST' },
  ).then(normalizeAuditRow)
}

export function cancelReversalRequest(id: string): Promise<CostSubjectAuditRow> {
  return apiRequest<Record<string, unknown>>(
    `/cost-subject-v2/reversal-requests/${requiredId(id)}/cancel`,
    { method: 'POST' },
  ).then(normalizeAuditRow)
}
