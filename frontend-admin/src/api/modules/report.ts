import { request } from '@/api/request'

export type ReportCatalogSourceType = 'page' | 'api'
export type ReportCatalogStatus = 'available' | 'api_only'

export interface ReportCatalogItem {
  code: string
  name: string
  catalog: string
  sourceType: ReportCatalogSourceType
  target: string
  permissionCode: string
  filterSummary: string
  exportSupport: boolean
  status: ReportCatalogStatus
}

export function getReportCatalog() {
  return request<ReportCatalogItem[]>({
    url: '/reports/catalog',
    method: 'get',
  })
}
