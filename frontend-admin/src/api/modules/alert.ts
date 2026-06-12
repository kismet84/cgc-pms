import { request } from '@/api/request'
import type { AlertLogVO } from '@/types/alert'

export interface AlertListParams {
  projectId?: number
  severity?: string
  isRead?: number
}

export interface MarkReadResult {
  success: boolean
  alertId: number
}

export interface BatchEvaluateResult {
  alertsGenerated: number
  tenantId: number
}

/** 预警列表（按项目/严重度/已读状态筛选） */
export function getAlertList(params: AlertListParams) {
  return request<AlertLogVO[]>({
    url: '/alerts',
    method: 'get',
    params,
  })
}

/** 标记单条预警为已读 */
export function markAlertRead(id: number) {
  return request<MarkReadResult>({
    url: `/alerts/${id}/read`,
    method: 'put',
  })
}

/** 手动触发批量评估 */
export function batchEvaluate() {
  return request<BatchEvaluateResult>({
    url: '/alerts/batch-evaluate',
    method: 'post',
  })
}
