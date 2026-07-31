import type { ReportCatalogItem } from '@/api/modules/report'
import type { Router } from 'vue-router'

type ReportCatalogPageCandidate = Pick<ReportCatalogItem, 'sourceType' | 'target'>
type ReportCatalogExportCandidate = Pick<
  ReportCatalogItem,
  'sourceType' | 'status' | 'target' | 'exportSupport'
>

export function canOpenReportCatalogPage(
  item: ReportCatalogPageCandidate,
  resolve: Router['resolve'],
) {
  if (item.sourceType !== 'page' || !item.target) {
    return false
  }
  const resolved = resolve(item.target)
  return resolved.matched.length > 0 && resolved.name !== 'NotFound'
}

export function hasReportCatalogExportEntry(
  item: ReportCatalogExportCandidate,
  resolve: Router['resolve'],
) {
  return (
    item.exportSupport && item.status === 'available' && canOpenReportCatalogPage(item, resolve)
  )
}
