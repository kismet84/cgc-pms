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

  it('keeps KPI, filters and table in the same project-list order', () => {
    expectOrdered(sources.task, [
      'class="lg-kpi-strip subcontract-task-kpi-summary"',
      'class="lg-search-bar subcontract-task-search-bar"',
      'class="lg-list-table-panel subcontract-task-table-panel"',
    ])
    expectOrdered(sources.measure, [
      'class="lg-kpi-strip subcontract-measure-kpi-summary"',
      'class="lg-search-bar subcontract-measure-search-bar"',
      'class="lg-list-table-panel subcontract-measure-table-panel"',
    ])
    expectOrdered(sources.settlement, [
      'class="lg-kpi-strip settlement-kpi-summary"',
      'class="lg-search-bar settlement-search-bar"',
      'class="lg-list-table-panel settlement-table-panel"',
    ])
    expectOrdered(sources.paymentOverview, [
      'class="lg-kpi-strip payment-kpi-summary"',
      'class="lg-search-bar payment-search-bar"',
      '<slot />',
    ])
    expectOrdered(sources.cashJournal, [
      'class="lg-kpi-strip cash-journal-kpis"',
      'class="lg-search-bar cash-journal-filters"',
      'class="lg-list-table-panel cash-journal-table-card"',
    ])
    expectOrdered(sources.accountingEntry, [
      'class="lg-kpi-strip accounting-entry-kpis"',
      'class="lg-search-bar accounting-entry-filter"',
      'class="lg-list-table-panel accounting-entry-table-panel"',
    ])
    expectOrdered(sources.invoice, [
      '<InvoiceKpiStrip',
      'class="lg-search-bar invoice-search-bar"',
      'class="lg-list-table-panel invoice-table-panel"',
    ])
  })

  it('uses segmented KPI and full analysis panel shells on every redesigned page', () => {
    expect(sources.invoiceKpi).toContain('class="lg-kpi-strip invoice-kpi-summary"')
    expect(sources.paymentOverview).toContain('class="lg-analysis-panel payment-analysis-panel"')
    expect(sources.settlement).toContain('class="lg-analysis-panel settlement-analysis-panel"')
    expect(sources.task).toContain('class="lg-analysis-panel subcontract-task-analysis-panel"')
    expect(sources.invoice).toContain('<InvoiceVerifyPanel :data="tableData" />')
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
