import { describe, expect, it } from 'vitest'
import { existsSync, readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const currentDir = dirname(fileURLToPath(import.meta.url))
const source = readFileSync(resolve(currentDir, '../ledger.vue'), 'utf-8')
const apiSource = readFileSync(resolve(currentDir, '../../../api/modules/cost.ts'), 'utf-8')
const componentDir = resolve(currentDir, '../components')

function readLocalComponent(name: string) {
  const path = resolve(componentDir, name)
  return existsSync(path) ? readFileSync(path, 'utf-8') : ''
}

const overviewSource = readLocalComponent('CostLedgerOverview.vue')
const tablePanelSource = readLocalComponent('CostLedgerTablePanel.vue')

describe('CostLedger production guards', () => {
  it('only exposes overhead execution to admins or users holding both required permissions', () => {
    expect(source).toContain("import { useUserStore } from '@/stores/user'")
    expect(source).toMatch(
      /const canExecuteAllocation = computed\([\s\S]*isAllocationAdmin\.value[\s\S]*userStore\.hasPermission\('cost:ledger:query'\)[\s\S]*userStore\.hasPermission\('overhead:execute'\)/,
    )
    expect(source).toContain('v-if="canExecuteAllocation"')
    expect(source).toContain('data-testid="execute-overhead-allocation"')
  })

  it('converts the selected month to month-end, disables incomplete months, and never sends tenantId', () => {
    expect(source).toContain(
      "dayjs(`${allocationMonth.value}-01`).endOf('month').format('YYYY-MM-DD')",
    )
    expect(source).toContain(
      "return !current.startOf('month').isBefore(dayjs().startOf('month'))",
    )
    expect(source).toContain(':disabled-date="disableIncompleteMonth"')
    expect(source).toContain('仅可选择已完整结束的月份')
    expect(apiSource).toMatch(/params:\s*\{ period \}/)
    expect(apiSource).not.toMatch(/executeOverheadAllocation[\s\S]*tenantId/)
  })

  it('requires a second confirmation and keeps the modal open on failure', () => {
    expect(source).toContain('title="确认执行间接费分摊"')
    expect(source).toContain('ok-text="确认执行"')
    expect(source).toContain('@ok="confirmAllocation"')
    expect(source).toMatch(
      /const result = await executeOverheadAllocation\(allocationPeriod\.value\)[\s\S]*allocationModalOpen\.value = false[\s\S]*handleSearch\(\)[\s\S]*catch \(error: unknown\) \{[\s\S]*message\.error/,
    )
    const catchBlock = source.match(/catch \(error: unknown\) \{[\s\S]*?\n  \} finally/)?.[0] ?? ''
    expect(catchBlock).not.toContain('allocationModalOpen.value = false')
    expect(source).toContain('相同租户、规则和月份不可重复生成')
  })

  it('shows a dedicated idempotent result and refreshes the ledger after success', () => {
    expect(source).toMatch(/if \(result\.idempotent\) \{[\s\S]*已执行，无需重复生成成本/)
    expect(source).toMatch(/allocationModalOpen\.value = false\s*\n\s*handleSearch\(\)/)
  })

  it('uses pageNo instead of legacy pageNum in refs and request params', () => {
    expect(source).toContain('const pageNo = ref(1)')
    expect(source).toMatch(/const params: CostLedgerQueryParams = \{\s*pageNo:\s*pageNo\.value/)
    expect(source).not.toMatch(/pageNum:\s*pageNum\.value/)
  })

  it('renders all required backend filter controls and keeps them in request params', () => {
    expect(overviewSource).toContain('v-model:value="filter.contractId"')
    expect(overviewSource).toContain('v-model:value="filter.partnerId"')
    expect(overviewSource).toContain('v-model:value="filter.costType"')
    expect(overviewSource).toMatch(
      /<a-range-picker[\s\S]*v-model:value="filter\.dateRange"[\s\S]*value-format="YYYY-MM-DD"/,
    )
    expect(source).toContain('const contractOptions = ref(contractList.value ?? [])')
    expect(source).toMatch(
      /async function loadContractOptions\(projectId\?: string\) \{[\s\S]*try \{/,
    )
    expect(source).toContain(
      'contractOptions.value = await referenceStore.fetchContracts({ projectId })',
    )
    expect(source).toMatch(/catch \(e: unknown\) \{[\s\S]*contractOptions\.value = \[\]/)
    expect(source).toMatch(
      /const params: CostLedgerQueryParams = \{[\s\S]*contractId: filter\.contractId/,
    )
    expect(source).toMatch(
      /const params: CostLedgerQueryParams = \{[\s\S]*partnerId: filter\.partnerId/,
    )
    expect(source).toMatch(
      /const params: CostLedgerQueryParams = \{[\s\S]*costType: filter\.costType/,
    )
    expect(source).toMatch(
      /const params: CostLedgerQueryParams = \{[\s\S]*startDate: filter\.dateRange\?\.\[0\]/,
    )
    expect(source).toMatch(
      /const params: CostLedgerQueryParams = \{[\s\S]*endDate: filter\.dateRange\?\.\[1\]/,
    )
    expect(overviewSource).toContain('v-for="contract in contractOptions"')
    expect(source).not.toContain("message.error('加载合同")
  })

  it('clears both contractId and partnerId when project changes', () => {
    expect(source).toMatch(
      /function onProjectChange\(val: string \| undefined\) \{[\s\S]*filter\.contractId = undefined[\s\S]*filter\.partnerId = undefined/,
    )
    expect(source).toContain('await loadContractOptions(val)')
  })

  it('normalizes nullable reference-store project lists before passing them to child panels', () => {
    expect(source).toContain('const projectOptions = computed(() => projectList.value ?? [])')
    expect(source).toContain(':project-list="projectOptions"')
    expect(source).not.toContain(':project-list="projectList"')
  })

  it('renders mobile fallback with loading empty data tri-state and desktop-only column settings', () => {
    expect(tablePanelSource).toMatch(/<div v-if="isMobile" class="cost-ledger-mobile-list">/)
    expect(tablePanelSource).toMatch(/v-if="loading"/)
    expect(tablePanelSource).toMatch(/v-else-if="!tableData\.length"/)
    expect(tablePanelSource).toMatch(/<template v-else>/)
    expect(tablePanelSource).toMatch(/class="cost-ledger-mobile-card"/)
    expect(tablePanelSource).toMatch(
      /class="cost-ledger-mobile-card"[\s\S]*@click="showDetail\(row\)"/,
    )
    expect(tablePanelSource).toMatch(/ColumnSettingsButton[\s\S]*v-if="!isMobile"/)
    expect(tablePanelSource).toMatch(/<div v-else class="lg-table-wrap cost-ledger-table-wrap">/)
  })

  it('keeps contract option loading non-blocking for main data requests', () => {
    expect(source).toMatch(
      /onMounted\(async \(\) => \{[\s\S]*void loadContractOptions\(filter\.projectId\)[\s\S]*void loadCostSubjectOptions\(\)[\s\S]*fetchData\(\)[\s\S]*fetchSummary\(\)/,
    )
  })

  it('splits ledger into local visual-baseline components without changing mount points', () => {
    expect(source).toContain("import CostLedgerOverview from './components/CostLedgerOverview.vue'")
    expect(source).toContain(
      "import CostLedgerTablePanel from './components/CostLedgerTablePanel.vue'",
    )
    expect(source).toContain(
      "import CostLedgerAnalysisRail from './components/CostLedgerAnalysisRail.vue'",
    )
    expect(source).toContain(
      "import CostLedgerDetailDrawer from './components/CostLedgerDetailDrawer.vue'",
    )
    expect(source).toMatch(/<CostLedgerOverview[\s\S]*\/>/)
    expect(source).toMatch(/<CostLedgerTablePanel[\s\S]*\/>/)
    expect(source).toMatch(/<CostLedgerAnalysisRail[\s\S]*\/>/)
    expect(source).toMatch(/<CostLedgerDetailDrawer[\s\S]*\/>/)
  })

  it('keeps visual baseline classes and scoped styles in local components', () => {
    const overview = readLocalComponent('CostLedgerOverview.vue')
    const tablePanel = readLocalComponent('CostLedgerTablePanel.vue')
    const analysisRail = readLocalComponent('CostLedgerAnalysisRail.vue')
    const detailDrawer = readLocalComponent('CostLedgerDetailDrawer.vue')

    expect(existsSync(resolve(componentDir, 'CostLedgerOverview.vue'))).toBe(true)
    expect(existsSync(resolve(componentDir, 'CostLedgerTablePanel.vue'))).toBe(true)
    expect(existsSync(resolve(componentDir, 'CostLedgerAnalysisRail.vue'))).toBe(true)
    expect(existsSync(resolve(componentDir, 'CostLedgerDetailDrawer.vue'))).toBe(true)

    expect(overview).toContain('class="lg-search-bar cost-ledger-query-panel"')
    expect(overview).toContain('.cost-ledger-query-panel')
    expect(overview).toContain('class="lg-kpi-strip cost-ledger-kpi-summary"')
    expect(overview).toContain('.cost-ledger-kpi-summary')

    expect(tablePanel).toContain('class="cost-ledger-mobile-card"')
    expect(tablePanel).toContain('class="lg-table-wrap cost-ledger-table-wrap"')
    expect(tablePanel).toContain('.cost-ledger-mobile-card')
    expect(tablePanel).toContain('.cost-ledger-table-wrap')

    expect(analysisRail).toContain('class="lg-analysis-rail cost-ledger-analysis-rail"')
    expect(analysisRail).toContain('.cost-ledger-analysis-panel')

    expect(detailDrawer).toContain('class="cost-ledger-detail-drawer"')
    expect(detailDrawer).toContain('.cost-ledger-detail-drawer :deep(.ant-drawer-body)')
    expect(detailDrawer).toContain('.cost-ledger-detail-summary')
  })
})
