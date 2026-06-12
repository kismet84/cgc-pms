import { request } from '@/api/request'
import type { PageParams, PageResult } from '@/types/api'

export interface WfTaskVO {
  id: string
  instanceId: string
  nodeInstanceId: string
  businessType: string
  businessId: string
  approverId: string
  approverName: string
  taskStatus: string
  roundNo: number
  taskVersion: number
  receivedAt: string
  handledAt?: string
  actionType?: string
  comment?: string
  title: string
  instanceStatus: string
}

export interface WfNodeVO {
  id: string
  templateNodeId: string
  nodeCode: string
  nodeName: string
  nodeOrder: number
  approveMode: string
  nodeStatus: string
  roundNo: number
  startedAt?: string
  endedAt?: string
  tasks: WfTaskVO[]
}

export interface WfRecordVO {
  id: string
  instanceId: string
  nodeInstanceId?: string
  taskId?: string
  roundNo: number
  nodeCode?: string
  nodeName?: string
  actionType: string
  actionName: string
  operatorId: string
  operatorName: string
  comment?: string
  recordStatus: string
  createdAt: string
  businessType?: string
  title?: string
  instanceStatus?: string
}

export interface WfCcVO {
  id: string
  instanceId: string
  ccUserId: string
  ccUserName: string
  businessType: string
  businessId?: string
  title: string
  isRead: number
  createdTime: string
  instanceStatus?: string
}

export interface WfInstanceVO {
  id: string
  templateId: string
  templateName: string
  businessType: string
  businessId: string
  projectId?: string
  contractId?: string
  title: string
  amount?: string
  instanceStatus: string
  currentRound: number
  resubmitCount: number
  initiatorId: string
  initiatorName: string
  businessSummary?: string
  startedAt: string
  endedAt?: string
  availableActions: string[]
  nodes: WfNodeVO[]
  records: WfRecordVO[]
}

export interface WorkflowSubmitParams {
  businessType: string
  businessId: number
  title: string
  amount?: string
  projectId?: number
  contractId?: number
  businessSummary?: string
  variables?: string
}

export interface WorkflowActionParams {
  action: string
  comment?: string
  idempotencyKey: string
}

/** 我的待办列表 */
export function getMyTodos(params: PageParams) {
  return request<PageResult<WfTaskVO>>({
    url: '/workflow/tasks/todo',
    method: 'get',
    params,
  })
}

/** 审批实例详情 */
export function getInstanceDetail(instanceId: string) {
  return request<WfInstanceVO>({
    url: `/workflow/instances/${instanceId}`,
    method: 'get',
  })
}

/** 提交审批 */
export function submitApproval(data: WorkflowSubmitParams) {
  return request<string>({
    url: '/workflow/submit',
    method: 'post',
    data,
  })
}

/** 同意 */
export function approveTask(taskId: string, data: WorkflowActionParams) {
  return request<void>({
    url: `/workflow/tasks/${taskId}/approve`,
    method: 'post',
    data,
  })
}

/** 驳回 */
export function rejectTask(taskId: string, data: WorkflowActionParams) {
  return request<void>({
    url: `/workflow/tasks/${taskId}/reject`,
    method: 'post',
    data,
  })
}

/** 撤回 */
export function withdrawInstance(instanceId: string) {
  return request<void>({
    url: `/workflow/instances/${instanceId}/withdraw`,
    method: 'post',
  })
}

/** 重新提交 */
export function resubmitInstance(instanceId: string) {
  return request<void>({
    url: `/workflow/instances/${instanceId}/resubmit`,
    method: 'post',
  })
}

/** 转办 */
export function transferTask(taskId: string, targetUserId: string, comment?: string) {
  return request<void>({
    url: `/workflow/tasks/${taskId}/transfer`,
    method: 'post',
    data: { targetUserId, comment },
  })
}

/** 加签 */
export function addSignTask(taskId: string, additionalUserIds: string[], comment?: string) {
  return request<void>({
    url: `/workflow/tasks/${taskId}/add-sign`,
    method: 'post',
    data: { additionalUserIds, comment },
  })
}

/** 我的已办列表 */
export function getMyDone(params: PageParams) {
  return request<PageResult<WfRecordVO>>({
    url: '/workflow/tasks/done',
    method: 'get',
    params,
  })
}

/** 抄送我的列表 */
export function getMyCc(params: PageParams) {
  return request<PageResult<WfCcVO>>({
    url: '/workflow/tasks/cc',
    method: 'get',
    params,
  })
}
