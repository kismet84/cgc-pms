import { request } from '@/api/request'
import type { PageParams, PageResult } from '@/types/api'
import type { AlertLogVO, AlertSubscriptionConfig, AlertSubscriptionResponse } from '@/types/alert'

export interface AlertListParams extends PageParams {
  keyword?: string
  projectId?: string
  severity?: string
  isRead?: number
  processStatus?: string
  ruleType?: string
  alertDomain?: string
  category?: string
  triggeredStart?: string
  triggeredEnd?: string
  triggeredAtStart?: string
  triggeredAtEnd?: string
  onlyDefaultScope?: boolean
}

export type AlertListResponse = PageResult<AlertLogVO> | AlertLogVO[]

export interface MarkReadResult {
  success: boolean
  alertId: string
}

export interface UpdateAlertStatusResult {
  success: boolean
  alertId: string
  processStatus: string
}

export type AlertProcessStatus = 'PROCESSED' | 'ARCHIVED' | 'INVALID'

export interface BatchAlertOperatePayload {
  alertIds: Array<string | number>
}

export interface BatchUpdateAlertStatusPayload extends BatchAlertOperatePayload {
  processStatus: AlertProcessStatus
  statusRemark?: string
}

export interface BatchAlertOperationFailure {
  alertId: string | number
  reason: string
}

export interface BatchAlertOperationResult {
  total: number
  success: number
  failed: number
  successIds: Array<string | number>
  failures: BatchAlertOperationFailure[]
}

export interface BatchEvaluateResult {
  alertsGenerated: number
  tenantId: string
}

export type UpdateAlertSubscriptionPayload = Partial<AlertSubscriptionConfig>

/** 预警列表（按项目/严重度/已读状态筛选） */
export function getAlertList(params: AlertListParams) {
  return request<AlertListResponse>({
    url: '/alerts',
    method: 'get',
    params: {
      ...params,
      alertDomain: params.alertDomain ?? params.category,
      triggeredStart: params.triggeredStart ?? params.triggeredAtStart,
      triggeredEnd: params.triggeredEnd ?? params.triggeredAtEnd,
      pageNum: params.pageNo ?? params.pageNum,
    },
  })
}

/** 标记单条预警为已读 */
export function markAlertRead(id: string) {
  return request<MarkReadResult>({
    url: `/alerts/${id}/read`,
    method: 'put',
  })
}

export function updateAlertStatus(
  id: string,
  data: { processStatus: AlertProcessStatus; statusRemark?: string },
) {
  return request<UpdateAlertStatusResult>({
    url: `/alerts/${id}/status`,
    method: 'put',
    data,
  })
}

export function batchMarkAlertRead(data: BatchAlertOperatePayload) {
  return request<BatchAlertOperationResult>({
    url: '/alerts/batch/read',
    method: 'put',
    data,
  })
}

export function batchUpdateAlertStatus(data: BatchUpdateAlertStatusPayload) {
  return request<BatchAlertOperationResult>({
    url: '/alerts/batch/status',
    method: 'put',
    data,
  })
}

/** 手动触发批量评估 */
export function batchEvaluate() {
  return request<BatchEvaluateResult>({
    url: '/alerts/batch-evaluate',
    method: 'post',
  })
}

export function getAlertSubscription() {
  return request<AlertSubscriptionResponse>({
    url: '/alerts/subscription',
    method: 'get',
  })
}

export function updateAlertSubscription(data: UpdateAlertSubscriptionPayload) {
  return request<AlertSubscriptionResponse>({
    url: '/alerts/subscription',
    method: 'put',
    data,
  })
}
