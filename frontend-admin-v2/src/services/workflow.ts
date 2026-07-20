import {
  WORKFLOW_API,
  type PageResult,
  type WorkflowActionBody,
  type WorkflowCc,
  type WorkflowInstance,
  type WorkflowMine,
  type WorkflowQuery,
  type WorkflowRecord,
  type WorkflowTab,
  type WorkflowTask,
} from '@cgc-pms/frontend-contracts'
import { apiRequest } from '@/services/request'

type WorkflowResults = {
  todo: PageResult<WorkflowTask>
  done: PageResult<WorkflowRecord>
  cc: PageResult<WorkflowCc>
  mine: PageResult<WorkflowMine>
}

export function loadWorkflowList<T extends WorkflowTab>(
  tab: T,
  query: WorkflowQuery,
  signal?: AbortSignal,
): Promise<WorkflowResults[T]> {
  const params = new URLSearchParams()
  for (const [key, value] of Object.entries(query)) {
    if (value !== undefined && value !== '') params.set(key, String(value))
  }
  const search = params.size ? `?${params}` : ''
  return apiRequest<WorkflowResults[T]>(`${WORKFLOW_API.list[tab]}${search}`, { signal })
}

export function loadWorkflowBusinessTypes(tab: WorkflowTab, signal?: AbortSignal) {
  return apiRequest<string[]>(WORKFLOW_API.businessTypes(tab), { signal })
}

export function loadWorkflowInstance(instanceId: string, signal?: AbortSignal) {
  return apiRequest<WorkflowInstance>(WORKFLOW_API.detail(instanceId), { signal })
}

export function approveWorkflowTask(taskId: string, body: WorkflowActionBody) {
  return apiRequest<void, WorkflowActionBody>(WORKFLOW_API.approve(taskId), {
    method: 'POST',
    body,
  })
}

export function rejectWorkflowTask(taskId: string, body: WorkflowActionBody) {
  return apiRequest<void, WorkflowActionBody>(WORKFLOW_API.reject(taskId), {
    method: 'POST',
    body,
  })
}

export function withdrawWorkflowInstance(instanceId: string) {
  return apiRequest<void>(WORKFLOW_API.withdraw(instanceId), { method: 'POST' })
}

export function resubmitWorkflowInstance(instanceId: string) {
  return apiRequest<void>(WORKFLOW_API.resubmit(instanceId), { method: 'POST' })
}

export function transferWorkflowTask(taskId: string, targetUserId: string, comment?: string) {
  return apiRequest<void, { targetUserId: string; comment?: string }>(
    WORKFLOW_API.transfer(taskId),
    { method: 'POST', body: { targetUserId, comment } },
  )
}

export function addSignWorkflowTask(taskId: string, additionalUserIds: string[], comment?: string) {
  return apiRequest<void, { additionalUserIds: string[]; comment?: string }>(
    WORKFLOW_API.addSign(taskId),
    { method: 'POST', body: { additionalUserIds, comment } },
  )
}
