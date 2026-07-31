import { request } from '@/api/request'
import type { PageParams, PageResult } from '@/types/api'

export interface AuditLogVO {
  id: string
  userId?: string
  operationType?: string
  businessType?: string
  businessId?: string
  httpMethod?: string
  requestPath?: string
  successFlag?: number
  errorCode?: string
  sourceIp?: string
  durationMs?: number
  createdAt?: string
}

export interface AuditLogQuery extends PageParams {
  businessType?: string
  businessId?: string
}

export function getAuditLogs(params: AuditLogQuery) {
  return request<PageResult<AuditLogVO>>({ url: '/audit-logs', method: 'get', params })
}
