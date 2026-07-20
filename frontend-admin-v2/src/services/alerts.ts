import {
  ALERT_API,
  NOTIFICATION_API,
  type AlertBatchResult,
  type AlertPage,
  type AlertProcessStatus,
  type AlertQuery,
  type NotificationRecord,
  type NotificationUnreadCount,
  type PageResult,
} from '@cgc-pms/frontend-contracts'
import { apiRequest } from '@/services/request'

function search(query: Record<string, unknown>): string {
  const params = new URLSearchParams()
  for (const [key, value] of Object.entries(query)) {
    if (value !== undefined && value !== '') params.set(key, String(value))
  }
  return params.size ? `?${params}` : ''
}

export function loadAlerts(query: AlertQuery, signal?: AbortSignal): Promise<AlertPage> {
  return apiRequest<AlertPage>(`${ALERT_API.list}${search(query)}`, { signal })
}

export function markAlertRead(id: string) {
  return apiRequest(ALERT_API.markRead(id), { method: 'PUT' })
}

export function acknowledgeAlert(id: string, remark: string) {
  return apiRequest(ALERT_API.acknowledge(id), { method: 'PUT', body: { remark } })
}

export function updateAlertStatus(
  id: string,
  processStatus: AlertProcessStatus,
  statusRemark: string,
) {
  return apiRequest(ALERT_API.updateStatus(id), {
    method: 'PUT',
    body: { processStatus, statusRemark },
  })
}

export function batchMarkAlertsRead(alertIds: string[]) {
  return apiRequest<AlertBatchResult>(ALERT_API.batchRead, {
    method: 'PUT',
    body: { alertIds },
  })
}

export function batchUpdateAlertStatus(
  alertIds: string[],
  processStatus: AlertProcessStatus,
  statusRemark: string,
) {
  return apiRequest<AlertBatchResult>(ALERT_API.batchStatus, {
    method: 'PUT',
    body: { alertIds, processStatus, statusRemark },
  })
}

export function evaluateAlerts() {
  return apiRequest<{ alertsGenerated: number; tenantId: string }>(ALERT_API.evaluate, {
    method: 'POST',
  })
}

export function loadNotificationSummary(signal?: AbortSignal) {
  return Promise.all([
    apiRequest<PageResult<NotificationRecord>>(`${NOTIFICATION_API.list}?pageNo=1&pageSize=8`, {
      signal,
    }),
    apiRequest<NotificationUnreadCount>(NOTIFICATION_API.unreadCount, { signal }),
  ])
}

export function markNotificationRead(id: string) {
  return apiRequest(NOTIFICATION_API.markRead(id), { method: 'PUT' })
}

export function markAllNotificationsRead() {
  return apiRequest(NOTIFICATION_API.markAllRead, { method: 'PUT' })
}
