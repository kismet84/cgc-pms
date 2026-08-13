import { apiRequest } from '../request'
import { normalizeRow, query, requiredId } from './normalize'
import type {
  AssignmentRuleCommand,
  AssignmentRuleRecord,
  MappingVersionCommand,
  MappingVersionRecord,
  ProjectScopeCommand,
  ProjectScopeRecord,
  SubjectImpactRecord,
} from './types'

export function loadMappingVersions(signal?: AbortSignal): Promise<MappingVersionRecord[]> {
  return apiRequest<Record<string, unknown>[]>('/cost-subject-v2/mapping-versions', {
    signal,
  }).then((rows) => rows.map((row) => normalizeRow<MappingVersionRecord>(row)))
}

export function createMappingVersion(command: MappingVersionCommand): Promise<string | number> {
  return apiRequest<string | number, MappingVersionCommand>('/cost-subject-v2/mapping-versions', {
    method: 'POST',
    body: command,
  })
}

export function activateMappingVersion(id: string, approvalInstanceId: string): Promise<void> {
  return apiRequest<void>(
    `/cost-subject-v2/mapping-versions/${requiredId(id)}/activate?${query({ approvalInstanceId })}`,
    { method: 'POST' },
  )
}

export function loadAssignmentRules(signal?: AbortSignal): Promise<AssignmentRuleRecord[]> {
  return apiRequest<Record<string, unknown>[]>('/cost-subject-v2/rules', { signal }).then((rows) =>
    rows.map((row) => normalizeRow<AssignmentRuleRecord>(row)),
  )
}

export function createAssignmentRule(command: AssignmentRuleCommand): Promise<string | number> {
  return apiRequest<string | number, AssignmentRuleCommand>('/cost-subject-v2/rules', {
    method: 'POST',
    body: command,
  })
}

export function loadProjectScopes(
  projectId: string,
  signal?: AbortSignal,
): Promise<ProjectScopeRecord[]> {
  return apiRequest<Record<string, unknown>[]>(`/cost-subject-v2/scopes?${query({ projectId })}`, {
    signal,
  }).then((rows) => rows.map((row) => normalizeRow<ProjectScopeRecord>(row)))
}

export function saveProjectScope(command: ProjectScopeCommand): Promise<string | number> {
  return apiRequest<string | number, ProjectScopeCommand>('/cost-subject-v2/scopes', {
    method: 'POST',
    body: command,
  })
}

export function loadSubjectImpact(subjectId: string): Promise<SubjectImpactRecord> {
  return apiRequest<Record<string, unknown>>(
    `/cost-subject-v2/impact/${requiredId(subjectId)}`,
  ).then((row) => normalizeRow<SubjectImpactRecord>(row))
}
