import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const currentDir = dirname(fileURLToPath(import.meta.url))
const readPage = (path: string) => readFileSync(resolve(currentDir, path), 'utf-8')

const analysisSurfaces = [
  '../accounting-entry/index.vue',
  '../alert/index.vue',
  '../approval/todo.vue',
  '../bid-cost/index.vue',
  '../cash-journal/index.vue',
  '../contract/components/ContractAnalysisPanel.vue',
  '../cost-target/components/CostTargetAnalysisRail.vue',
  '../cost/components/CostLedgerAnalysisRail.vue',
  '../cost/components/CostSummaryAnalysisRail.vue',
  '../inventory/components/PurchaseRequestAnalysisPanel.vue',
  '../inventory/components/StockAnalysisPanel.vue',
  '../inventory/transaction.vue',
  '../inventory/warehouse.vue',
  '../invoice/components/InvoiceVerifyPanel.vue',
  '../material/dictionary.vue',
  '../partner/index.vue',
  '../payment/components/PaymentOverviewPanel.vue',
  '../project/components/ProjectAnalysisRail.vue',
  '../purchase/components/PurchaseOrderAnalysisRail.vue',
  '../receipt/index.vue',
  '../requisition/index.vue',
  '../settlement/index.vue',
  '../site/daily-log.vue',
  '../subcontract/components/SubcontractMeasureAnalysisRail.vue',
  '../subcontract/task.vue',
  '../system/roles/index.vue',
  '../system/users/index.vue',
  '../variation/components/VariationOrderWorkspace.vue',
].map(readPage)

function analysisRailTemplate(source: string) {
  const classIndex = source.indexOf('lg-analysis-rail')
  const start = source.lastIndexOf('<aside', classIndex)
  const end = source.indexOf('</aside>', classIndex)
  return source.slice(start, end + '</aside>'.length)
}

describe('global analysis rail UI contract', () => {
  it('uses the shared rail, panel and title shell on every analysis surface', () => {
    analysisSurfaces.forEach((source) => {
      expect(source).toContain('lg-analysis-rail')
      expect(source).toContain('lg-analysis-panel')
      expect(source).toContain('lg-analysis-header')
      expect(source).toContain('lg-analysis-heading')
      expect(source).toContain('lg-analysis-description')
      expect(source).toContain('辅助分析')
    })
  })

  it('keeps every analysis rail informational with no buttons', () => {
    analysisSurfaces.forEach((source) => {
      const rail = analysisRailTemplate(source)
      expect(rail).not.toContain('<a-button')
      expect(rail).not.toContain('<button')
    })
  })

  it('uses flat shared rows instead of page-specific cards and chips', () => {
    const dailyLog = readPage('../site/daily-log.vue')
    const bidCost = readPage('../bid-cost/index.vue')
    const receipt = readPage('../receipt/index.vue')
    const requisition = readPage('../requisition/index.vue')
    const measure = readPage('../subcontract/components/SubcontractMeasureAnalysisRail.vue')

    expect(dailyLog).toContain('class="lg-analysis-overview-row"')
    expect(bidCost).toContain('class="lg-analysis-overview-row"')
    expect(receipt).not.toContain('class="receipt-risk-box"')
    expect(requisition).not.toContain('class="requisition-chip"')
    expect(measure).not.toContain('class="subcontract-measure-amount-box"')
  })

  it('loads one global 20vw desktop and mobile-hide contract last', () => {
    const globalStyles = readPage('../../assets/styles/global.css')
    const railStyles = readPage('../../assets/styles/global-analysis-rail.css')

    expect(globalStyles.trimEnd().endsWith("@import './global-analysis-rail.css';")).toBe(true)
    expect(railStyles).toContain('--lg-analysis-rail-width: 20vw')
    expect(railStyles).toContain('@media (min-width: 500px)')
    expect(railStyles).toContain('@media (max-width: 499px)')
    expect(railStyles).toContain('.lg-analysis-rail:not(.alert-analysis-rail--mobile-open)')
    expect(railStyles).toContain("[class*='-bar-track']")
    expect(railStyles).toContain("[class*='analysis-focus']")
    expect(railStyles).toContain('.alert-analysis-kpi')
    expect(railStyles).toMatch(/\.lg-analysis-section\s*\{[\s\S]*gap:\s*6px;/)
    expect(railStyles).toContain('grid-template-columns: 9px minmax(0, 1fr) auto auto !important')
  })
})
