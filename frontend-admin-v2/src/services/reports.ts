import { REPORT_API, type ReportCatalogItem } from '@cgc-pms/frontend-contracts'
import { apiRequest } from '@/services/request'

export function loadReportCatalog(signal?: AbortSignal) {
  return apiRequest<ReportCatalogItem[]>(REPORT_API.catalog, { signal })
}
