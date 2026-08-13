import { apiRequest } from '@/services/request'
import { normalizePage, params, type PageResult } from './support'

export interface AuditRecord {
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

export interface DataMaintenanceRetainedGroup {
  code: string
  tableCount: number
  rowCount: number
}

export interface DataMaintenancePreview {
  database: string
  policyFingerprint: string
  eligible: boolean
  blockers: string[]
  retainedGroups: DataMaintenanceRetainedGroup[]
  clearTableCount: number
  clearRowCount: number
  sysFileCount: number
  ignoredViews: string[]
}

export async function loadAuditLogs(
  query: {
    pageNo: number
    pageSize: number
    businessType?: string
    businessId?: string
    userId?: string
    startTime?: string
    endTime?: string
  },
  signal?: AbortSignal,
): Promise<PageResult<AuditRecord>> {
  const page = await apiRequest<PageResult<AuditRecord>>(`/audit-logs?${params(query)}`, { signal })
  return normalizePage(page, (row) => ({
    ...row,
    id: String(row.id),
    userId: row.userId == null ? undefined : String(row.userId),
  }))
}

export function loadDataMaintenancePreview(): Promise<DataMaintenancePreview> {
  return apiRequest<DataMaintenancePreview>('/system/data-maintenance/preview')
}
