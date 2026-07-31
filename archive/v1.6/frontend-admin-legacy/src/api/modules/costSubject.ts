import { request } from '@/api/request'
import type {
  CostSubjectImpact,
  CostSubjectMappingVersion,
  CostSubjectRule,
  CostSubjectTreeNode,
  CostSubjectV2Row,
  CostSubjectVO,
  ProjectCostSubjectScope,
} from '@/types/costSubject'

function camelizeRow<T>(row: Record<string, unknown>): T {
  return Object.fromEntries(
    Object.entries(row).map(([key, value]) => [
      key.replace(/_([a-z])/g, (_, letter: string) => letter.toUpperCase()),
      value,
    ]),
  ) as T
}

/** 获取成本科目树，category 可选 COST|REVENUE|SETTLEMENT */
export function getCostSubjectTree(category?: string) {
  return request<CostSubjectTreeNode[]>({
    url: '/cost-subjects/tree',
    method: 'get',
    params: category ? { category } : undefined,
  })
}

/** 获取成本科目列表，category 可选 COST|REVENUE|SETTLEMENT */
export function getCostSubjectList(category?: string) {
  return request<CostSubjectVO[]>({
    url: '/cost-subjects',
    method: 'get',
    params: category ? { category } : undefined,
  })
}

/** 获取成本科目详情 */
export function getCostSubjectById(id: string) {
  return request<CostSubjectVO>({
    url: `/cost-subjects/${id}`,
    method: 'get',
  })
}

/** 新建成本科目 */
export function createCostSubject(data: Partial<CostSubjectVO>) {
  return request<string>({
    url: '/cost-subjects',
    method: 'post',
    data,
  })
}

/** 更新成本科目 */
export function updateCostSubject(id: string, data: Partial<CostSubjectVO>) {
  return request<void>({
    url: `/cost-subjects/${id}`,
    method: 'put',
    data,
  })
}

/** 删除成本科目 */
export function deleteCostSubject(id: string) {
  return request<void>({
    url: `/cost-subjects/${id}`,
    method: 'delete',
  })
}

/** 切换成本科目启用/停用状态 */
export function toggleCostSubjectStatus(id: string) {
  return request<void>({
    url: `/cost-subjects/${id}/toggle`,
    method: 'put',
  })
}

export function getCostSubjectMappingVersions() {
  return request<Record<string, unknown>[]>({
    url: '/cost-subject-v2/mapping-versions',
    method: 'get',
  }).then((rows) => rows.map((row) => camelizeRow<CostSubjectMappingVersion>(row)))
}

export function createCostSubjectMappingVersion(data: object) {
  return request<string>({ url: '/cost-subject-v2/mapping-versions', method: 'post', data })
}

export function activateCostSubjectMappingVersion(id: string, approvalInstanceId: string) {
  return request<void>({
    url: `/cost-subject-v2/mapping-versions/${id}/activate`,
    method: 'post',
    params: { approvalInstanceId },
  })
}

export function getCostSubjectRules() {
  return request<Record<string, unknown>[]>({ url: '/cost-subject-v2/rules', method: 'get' }).then(
    (rows) => rows.map((row) => camelizeRow<CostSubjectRule>(row)),
  )
}

export function createCostSubjectRule(data: object) {
  return request<string>({ url: '/cost-subject-v2/rules', method: 'post', data })
}

export function getProjectCostSubjectScopes(projectId: string) {
  return request<Record<string, unknown>[]>({
    url: '/cost-subject-v2/scopes',
    method: 'get',
    params: { projectId },
  }).then((rows) => rows.map((row) => camelizeRow<ProjectCostSubjectScope>(row)))
}

export function saveProjectCostSubjectScope(data: object) {
  return request<string>({ url: '/cost-subject-v2/scopes', method: 'post', data })
}

export function getCostSubjectImpact(subjectId: string) {
  return request<Record<string, unknown>>({
    url: `/cost-subject-v2/impact/${subjectId}`,
    method: 'get',
  }).then((row) => camelizeRow<CostSubjectImpact>(row))
}

export function getBidCostTargetTransfers() {
  return request<Record<string, unknown>[]>({
    url: '/cost-subject-v2/bid-transfers',
    method: 'get',
  }).then((rows) => rows.map((row) => camelizeRow<CostSubjectV2Row>(row)))
}

export function createBidCostTargetTransfer(data: object) {
  return request<string>({ url: '/cost-subject-v2/bid-transfers', method: 'post', data })
}

export function reverseBidCostTargetTransfer(
  id: string,
  approvalInstanceId: string,
  idempotencyKey: string,
) {
  return request<string>({
    url: `/cost-subject-v2/bid-transfers/${id}/reverse`,
    method: 'post',
    params: { approvalInstanceId, idempotencyKey },
  })
}

export function getFinanceCostAllocations() {
  return request<Record<string, unknown>[]>({
    url: '/cost-subject-v2/finance-allocations',
    method: 'get',
  }).then((rows) => rows.map((row) => camelizeRow<CostSubjectV2Row>(row)))
}

export function createFinanceCostAllocation(data: object) {
  return request<string>({ url: '/cost-subject-v2/finance-allocations', method: 'post', data })
}

export function reverseFinanceCostAllocation(
  id: string,
  approvalInstanceId: string,
  idempotencyKey: string,
) {
  return request<string>({
    url: `/cost-subject-v2/finance-allocations/${id}/reverse`,
    method: 'post',
    params: { approvalInstanceId, idempotencyKey },
  })
}

export function getCostSubjectReconciliation(projectId: string) {
  return request<Record<string, unknown>>({
    url: '/cost-subject-v2/reconciliation',
    method: 'get',
    params: { projectId },
  }).then((row) => camelizeRow<CostSubjectV2Row>(row))
}
