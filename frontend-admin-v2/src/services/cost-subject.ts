import { apiRequest } from './request'

export interface CostSubjectRecord {
  id: string
  parentId: string
  subjectCode: string
  subjectName: string
  subjectType: string
  accountCategory: string
  level: number
  sortOrder: number
  status: string
  defaultTargetRatio?: string | null
  children?: CostSubjectRecord[]
}

export interface CostSubjectCommand {
  parentId: string
  subjectCode: string
  subjectName: string
  subjectType: string
  accountCategory: 'COST'
  sortOrder: number
  status: 'ENABLE' | 'DISABLE'
}

export interface MappingVersionRecord {
  id: string
  versionCode: string
  versionName: string
  status: string
  effectiveDate?: string
  approvalInstanceId?: string
  itemCount: number
  remark?: string
}

export interface AssignmentRuleRecord {
  id: string
  ruleCode: string
  versionCode: string
  sourceType: string
  businessCategory: string
  projectId?: string
  costSubjectId: string
  subjectCode: string
  subjectName: string
  priority: number
  status: string
  effectiveFrom: string
  effectiveTo?: string
}

export interface ProjectScopeRecord {
  id: string
  projectId: string
  costSubjectId: string
  subjectCode: string
  subjectName: string
  enabled: number
  effectiveFrom: string
  effectiveTo?: string
}

export interface SubjectImpactRecord {
  subjectId: string
  costItems: number
  targetItems: number
  forecastItems: number
  budgetLines: number
  payments: number
  expenses: number
  settlementItems: number
  accountingLines: number
  assignmentRules: number
  projectScopes: number
}

export type CostSubjectAuditRow = Record<string, string | number | null>

export interface MappingVersionCommand {
  versionCode: string
  versionName: string
  effectiveDate: string | null
  remark: string
  items: Array<{
    sourceSubjectId: string
    targetGroupCode: string
    targetSubjectId: string | null
    historicalDisplayName: string
    mappingReason: string
  }>
}

export interface AssignmentRuleCommand {
  ruleCode: string
  mappingVersionId: string
  sourceType: string
  businessCategory: string
  projectId: string | null
  costSubjectId: string
  priority: number
  effectiveFrom: string | null
  effectiveTo: string | null
  remark: string
}

export interface ProjectScopeCommand {
  projectId: string
  costSubjectId: string
  enabled: boolean
  effectiveFrom: string | null
  effectiveTo: string | null
  remark: string
}

export interface BidTransferCommand {
  bidCostId: string
  projectId: string
  targetId: string
  mappingVersionId: string
  approvalInstanceId: string
  idempotencyKey: string
  remark: string
}

export interface FinanceAllocationCommand {
  sourceType: string
  sourceId: string
  allocationBasis: string
  accountingPeriod: string
  costSubjectId: string
  approvalInstanceId: string
  idempotencyKey: string
  remark: string
  lines: Array<{ projectId: string; basisValue: string }>
}

export function loadCostSubjectTree(signal?: AbortSignal): Promise<CostSubjectRecord[]> {
  return apiRequest<CostSubjectRecord[]>('/cost-subjects/tree?category=COST', { signal }).then(
    (rows) => rows.map(normalizeSubject),
  )
}

export function loadCostSubject(id: string): Promise<CostSubjectRecord> {
  return apiRequest<CostSubjectRecord>(`/cost-subjects/${requiredId(id)}`).then(normalizeSubject)
}

export function createCostSubject(command: CostSubjectCommand): Promise<string | number> {
  return apiRequest<string | number, CostSubjectCommand>('/cost-subjects', {
    method: 'POST',
    body: command,
  })
}

export function updateCostSubject(id: string, command: CostSubjectCommand): Promise<void> {
  return apiRequest<void, CostSubjectCommand>(`/cost-subjects/${requiredId(id)}`, {
    method: 'PUT',
    body: command,
  })
}

export function toggleCostSubjectStatus(id: string): Promise<void> {
  return apiRequest<void>(`/cost-subjects/${requiredId(id)}/toggle`, { method: 'PUT' })
}

export function deleteCostSubject(id: string): Promise<void> {
  return apiRequest<void>(`/cost-subjects/${requiredId(id)}`, { method: 'DELETE' })
}

export function updateTargetCostRatios(
  ratios: Array<{ subjectCode: string; ratio: string }>,
): Promise<CostSubjectRecord[]> {
  return apiRequest<CostSubjectRecord[], Array<{ subjectCode: string; ratio: string }>>(
    '/cost-subjects/target-ratios',
    { method: 'PUT', body: ratios },
  ).then((rows) => rows.map(normalizeSubject))
}

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
    `/cost-subject-v2/mapping-versions/${requiredId(id)}/activate?${query({
      approvalInstanceId,
    })}`,
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

export function loadBidTransfers(signal?: AbortSignal): Promise<CostSubjectAuditRow[]> {
  return apiRequest<Record<string, unknown>[]>('/cost-subject-v2/bid-transfers', { signal }).then(
    (rows) => rows.map(normalizeAuditRow),
  )
}

export function createBidTransfer(command: BidTransferCommand): Promise<string | number> {
  return apiRequest<string | number, BidTransferCommand>('/cost-subject-v2/bid-transfers', {
    method: 'POST',
    body: command,
  })
}

export function reverseBidTransfer(
  id: string,
  approvalInstanceId: string,
  idempotencyKey: string,
  remark: string,
): Promise<string | number> {
  return apiRequest<string | number>(
    `/cost-subject-v2/bid-transfers/${requiredId(id)}/reverse?${query({
      approvalInstanceId,
      idempotencyKey,
      remark,
    })}`,
    { method: 'POST' },
  )
}

export function loadFinanceAllocations(signal?: AbortSignal): Promise<CostSubjectAuditRow[]> {
  return apiRequest<Record<string, unknown>[]>('/cost-subject-v2/finance-allocations', {
    signal,
  }).then((rows) => rows.map(normalizeAuditRow))
}

export function createFinanceAllocation(
  command: FinanceAllocationCommand,
): Promise<string | number> {
  return apiRequest<string | number, FinanceAllocationCommand>(
    '/cost-subject-v2/finance-allocations',
    { method: 'POST', body: command },
  )
}

export function reverseFinanceAllocation(
  id: string,
  approvalInstanceId: string,
  idempotencyKey: string,
  remark: string,
): Promise<string | number> {
  return apiRequest<string | number>(
    `/cost-subject-v2/finance-allocations/${requiredId(id)}/reverse?${query({
      approvalInstanceId,
      idempotencyKey,
      remark,
    })}`,
    { method: 'POST' },
  )
}

export function loadCostSubjectReconciliation(projectId: string): Promise<CostSubjectAuditRow> {
  return apiRequest<Record<string, unknown>>(
    `/cost-subject-v2/reconciliation?${query({ projectId })}`,
  ).then(normalizeAuditRow)
}

function normalizeSubject(row: CostSubjectRecord): CostSubjectRecord {
  return {
    ...row,
    id: String(row.id),
    parentId: row.parentId == null ? '0' : String(row.parentId),
    defaultTargetRatio: row.defaultTargetRatio == null ? null : String(row.defaultTargetRatio),
    children: row.children?.map(normalizeSubject) ?? [],
  }
}

function normalizeRow<T>(row: Record<string, unknown>): T {
  return Object.fromEntries(
    Object.entries(row).map(([key, value]) => {
      const normalizedKey = key.replace(/_([a-z])/g, (_, letter: string) => letter.toUpperCase())
      return [normalizedKey, isIdKey(normalizedKey) && value != null ? String(value) : value]
    }),
  ) as T
}

function normalizeAuditRow(row: Record<string, unknown>): CostSubjectAuditRow {
  return Object.fromEntries(
    Object.entries(normalizeRow<Record<string, unknown>>(row)).map(([key, value]) => [
      key,
      isAmountKey(key) && value != null ? String(value) : (value as string | number | null),
    ]),
  )
}

function isIdKey(key: string): boolean {
  return key === 'id' || key.endsWith('Id')
}

function isAmountKey(key: string): boolean {
  return /(?:amount|cost|allocated|transferred)$/i.test(key)
}

function query(values: Record<string, string>): string {
  const params = new URLSearchParams()
  for (const [key, value] of Object.entries(values)) {
    if (value.trim()) params.set(key, value.trim())
  }
  return params.toString()
}

function requiredId(value: string): string {
  const normalized = value.trim()
  if (!normalized) throw new TypeError('ID不能为空')
  return encodeURIComponent(normalized)
}
