import { apiRequest } from './request'

export interface WorkflowTemplateNodeRecord {
  id: string
  templateId: string
  nodeCode: string
  nodeName: string
  nodeOrder: number
  nodeType: string
  approveMode: string
  approverConfig: string
  allowTransfer: number
  allowAddSign: number
  timeoutHours?: number
  remark?: string
}

export interface WorkflowTemplateRecord {
  id: string
  templateCode: string
  templateName: string
  businessType: string
  enabled: number
  amountMin?: string
  amountMax?: string
  remark?: string
  nodeCount: number
  updatedAt?: string
  nodes?: WorkflowTemplateNodeRecord[]
}

export interface WorkflowTemplatePage {
  pageNo: number
  pageSize: number
  total: number
  records: WorkflowTemplateRecord[]
}

export interface WorkflowTemplateQuery {
  pageNo: number
  pageSize: number
  businessType?: string
  enabled?: string
  keyword?: string
}

export interface WorkflowTemplateUpdateCommand {
  templateName: string
  enabled: number
  amountMin: string | null
  amountMax: string | null
  remark: string
}

export interface WorkflowTemplateNodeCommand {
  nodeCode?: string
  nodeName: string
  nodeOrder?: number
  nodeType: 'APPROVAL'
  approveMode: 'SEQUENTIAL' | 'COUNTERSIGN' | 'OR_SIGN'
  approverConfig: string
  allowTransfer: number
  allowAddSign: number
  timeoutHours?: number
  remark: string
}

export function loadWorkflowTemplates(
  query: WorkflowTemplateQuery,
  signal?: AbortSignal,
): Promise<WorkflowTemplatePage> {
  const params = new URLSearchParams({
    pageNo: String(query.pageNo),
    pageSize: String(query.pageSize),
  })
  if (query.businessType) params.set('businessType', query.businessType)
  if (query.enabled !== undefined && query.enabled !== '') params.set('enabled', query.enabled)
  if (query.keyword) params.set('keyword', query.keyword)
  return apiRequest<WorkflowTemplatePage>(`/workflow/templates?${params}`, { signal }).then(
    (page) => ({
      ...page,
      total: Number(page.total),
      records: page.records.map(normalizeTemplate),
    }),
  )
}

export function loadWorkflowTemplate(id: string): Promise<WorkflowTemplateRecord> {
  return apiRequest<WorkflowTemplateRecord>(`/workflow/templates/${requiredId(id)}`).then(
    normalizeTemplate,
  )
}

export function updateWorkflowTemplate(
  id: string,
  command: WorkflowTemplateUpdateCommand,
): Promise<void> {
  return apiRequest<void, WorkflowTemplateUpdateCommand>(`/workflow/templates/${requiredId(id)}`, {
    method: 'PUT',
    body: command,
  })
}

export function createWorkflowTemplateNode(
  templateId: string,
  command: WorkflowTemplateNodeCommand,
): Promise<WorkflowTemplateNodeRecord> {
  return apiRequest<WorkflowTemplateNodeRecord, WorkflowTemplateNodeCommand>(
    `/workflow/templates/${requiredId(templateId)}/nodes`,
    { method: 'POST', body: command },
  ).then(normalizeNode)
}

export function updateWorkflowTemplateNode(
  templateId: string,
  nodeId: string,
  command: WorkflowTemplateNodeCommand,
): Promise<void> {
  return apiRequest<void, WorkflowTemplateNodeCommand>(
    `/workflow/templates/${requiredId(templateId)}/nodes/${requiredId(nodeId)}`,
    { method: 'PUT', body: command },
  )
}

export function deleteWorkflowTemplateNode(templateId: string, nodeId: string): Promise<void> {
  return apiRequest<void>(
    `/workflow/templates/${requiredId(templateId)}/nodes/${requiredId(nodeId)}`,
    { method: 'DELETE' },
  )
}

export function reorderWorkflowTemplateNodes(templateId: string, nodeIds: string[]): Promise<void> {
  return apiRequest<void, { nodeIds: string[] }>(
    `/workflow/templates/${requiredId(templateId)}/nodes/reorder`,
    { method: 'PUT', body: { nodeIds } },
  )
}

function normalizeTemplate(row: WorkflowTemplateRecord): WorkflowTemplateRecord {
  return {
    ...row,
    id: String(row.id),
    amountMin: row.amountMin == null ? undefined : String(row.amountMin),
    amountMax: row.amountMax == null ? undefined : String(row.amountMax),
    nodes: row.nodes?.map(normalizeNode) ?? [],
  }
}

function normalizeNode(row: WorkflowTemplateNodeRecord): WorkflowTemplateNodeRecord {
  return { ...row, id: String(row.id), templateId: String(row.templateId) }
}

function requiredId(id: string): string {
  const value = id.trim()
  if (!value) throw new Error('业务标识不能为空')
  return encodeURIComponent(value)
}
