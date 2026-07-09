import type { ReportCatalogItem } from '@/api/modules/report'
import type { Router } from 'vue-router'

type ReportCatalogPageCandidate = Pick<ReportCatalogItem, 'sourceType' | 'target'>

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
