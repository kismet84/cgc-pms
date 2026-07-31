import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const currentDir = dirname(fileURLToPath(import.meta.url))
const readPage = (path: string) => readFileSync(resolve(currentDir, path), 'utf-8')

const sources = {
  task: readPage('../subcontract/task.vue'),
  measure: readPage('../subcontract/measure.vue'),
  settlement: readPage('../settlement/index.vue'),
  payment: readPage('../payment/index.vue'),
  paymentOverview: readPage('../payment/components/PaymentOverviewPanel.vue'),
  measureAnalysis: readPage('../subcontract/components/SubcontractMeasureAnalysisRail.vue'),
  cashJournal: readPage('../cash-journal/index.vue'),
  accountingEntry: readPage('../accounting-entry/index.vue'),
  invoice: readPage('../invoice/index.vue'),
  invoiceKpi: readPage('../invoice/components/InvoiceKpiStrip.vue'),
  invoiceAnalysis: readPage('../invoice/components/InvoiceVerifyPanel.vue'),
  listQueryPanel: readPage('../../components/list-page/ListQueryPanel.vue'),
  settlementDomainStyle: readPage('../../assets/styles/settlement-domain-list.css'),
}

function expectOrdered(source: string, selectors: string[]) {
  const positions = selectors.map((selector) => source.indexOf(selector))
  positions.forEach((position) => expect(position).toBeGreaterThan(-1))
  positions
    .slice(1)
    .forEach((position, index) => expect(position).toBeGreaterThan(positions[index]))
}

describe('secondary page visual consistency', () => {
  it('uses breadcrumb-only page heads without H1 marketing copy', () => {
    ;[
      sources.task,
      sources.measure,
      sources.settlement,
      sources.paymentOverview,
      sources.cashJournal,
      sources.accountingEntry,
      sources.invoice,
    ].forEach((source) => expect(source).not.toContain('<h1'))
  })

  it('keeps existing procurement pages in their established list order', () => {
    expectOrdered(sources.task, [
      'class="lg-kpi-strip subcontract-task-kpi-summary"',
      'subcontract-task-search-bar procurement-subcontract-query-panel',
      'subcontract-task-table-panel procurement-subcontract-table-panel',
    ])
    expectOrdered(sources.measure, [
      'class="lg-kpi-strip subcontract-measure-kpi-summary"',
      'subcontract-measure-search-bar procurement-subcontract-query-panel',
      'subcontract-measure-table-panel procurement-subcontract-table-panel',
    ])
  })

  it('uses one shared query panel before the table on all settlement-domain pages', () => {
    expectOrdered(sources.settlement, [
      'aria-label="结算查询条件"',
      'settlement-table-panel settlement-domain-table-panel',
    ])
    expectOrdered(sources.paymentOverview, ['aria-label="付款查询条件"', '<slot />'])
    expectOrdered(sources.cashJournal, [
      'aria-label="资金日记账筛选"',
      'cash-journal-table-card settlement-domain-table-panel',
    ])
    expectOrdered(sources.accountingEntry, [
      'aria-label="会计凭证筛选"',
      'accounting-entry-table-panel settlement-domain-table-panel',
    ])
    expectOrdered(sources.invoice, [
      'aria-label="发票查询条件"',
      'invoice-table-panel settlement-domain-table-panel',
    ])
    expect(sources.listQueryPanel).toContain('搜索')
    expect(sources.listQueryPanel).toContain('重置')
    expect(sources.listQueryPanel).toContain('筛选')
  })

  it('uses the project-list full-width query and 20vw desktop analysis layout', () => {
    expect(sources.settlementDomainStyle).toMatch(/grid-template-columns:\s*minmax\(0, 1fr\) 20vw;/)
    expect(sources.settlementDomainStyle).toContain('grid-column: 1 / -1')
    expect(sources.settlementDomainStyle).toContain('padding: 10px 14px !important')
    expect(sources.settlementDomainStyle).toMatch(
      /@media \(width < 500px\)[\s\S]*?display: block !important;/,
    )
    expect(sources.listQueryPanel).toMatch(
      /@media \(width < 500px\)[\s\S]*?\.list-query-search-button,[\s\S]*?\.list-query-reset-button\s*\{[\s\S]*?display:\s*none;/,
    )
  })

  it('uses segmented KPI and full analysis panel shells on every redesigned page', () => {
    expect(sources.invoiceKpi).toContain('class="lg-kpi-strip invoice-kpi-summary"')
    expect(sources.paymentOverview).toContain('class="lg-analysis-panel payment-analysis-panel"')
    expect(sources.settlement).toContain('class="lg-analysis-panel settlement-analysis-panel"')
    expect(sources.task).toContain('class="lg-analysis-panel subcontract-task-analysis-panel"')
    expect(sources.invoice).toContain(
      '<InvoiceVerifyPanel :data="tableData" class="settlement-domain-analysis-rail" />',
    )
  })

  it('uses the project-list analysis rail width token instead of page-specific wide rails', () => {
    ;[
      sources.task,
      sources.measureAnalysis,
      sources.settlement,
      sources.paymentOverview,
      sources.invoiceAnalysis,
    ].forEach((source) => {
      expect(source).toContain('width: var(--lg-rail-width, 240px)')
      expect(source).not.toContain('width: 336px')
    })
  })

  it('does not add a settlement-only blue accent line above the table card', () => {
    expect(sources.settlement).not.toContain('border-top: 3px solid var(--primary)')
  })
})
