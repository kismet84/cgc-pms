import { describe, expect, it } from 'vitest'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const currentDir = dirname(fileURLToPath(import.meta.url))
const source = readFileSync(resolve(currentDir, '../ledger.vue'), 'utf-8')

describe('CostLedger production guards', () => {
  it('uses pageNo instead of legacy pageNum in refs and request params', () => {
    expect(source).toContain('const pageNo = ref(1)')
    expect(source).toMatch(/const params: CostLedgerQueryParams = \{\s*pageNo:\s*pageNo\.value/)
    expect(source).not.toMatch(/pageNum:\s*pageNum\.value/)
  })

  it('renders all required backend filter controls and keeps them in request params', () => {
    expect(source).toContain('v-model:value="filter.contractId"')
    expect(source).toContain('v-model:value="filter.partnerId"')
    expect(source).toContain('v-model:value="filter.costType"')
    expect(source).toMatch(/<a-range-picker[\s\S]*v-model:value="filter\.dateRange"[\s\S]*value-format="YYYY-MM-DD"/)
    expect(source).toContain('const contractOptions = ref(contractList.value ?? [])')
    expect(source).toMatch(/async function loadContractOptions\(projectId\?: string\) \{[\s\S]*try \{/)
    expect(source).toContain('const contracts = await referenceStore.fetchContracts({ projectId })')
    expect(source).toContain('contractOptions.value = contracts')
    expect(source).toMatch(/catch \(e: unknown\) \{[\s\S]*contractOptions\.value = \[\]/)
    expect(source).toMatch(/const params: CostLedgerQueryParams = \{[\s\S]*contractId: filter\.contractId/)
    expect(source).toMatch(/const params: CostLedgerQueryParams = \{[\s\S]*partnerId: filter\.partnerId/)
    expect(source).toMatch(/const params: CostLedgerQueryParams = \{[\s\S]*costType: filter\.costType/)
    expect(source).toMatch(/const params: CostLedgerQueryParams = \{[\s\S]*startDate: filter\.dateRange\?\.\[0\]/)
    expect(source).toMatch(/const params: CostLedgerQueryParams = \{[\s\S]*endDate: filter\.dateRange\?\.\[1\]/)
    expect(source).toContain('v-for="contract in contractOptions"')
    expect(source).not.toContain("message.error('加载合同")
  })

  it('clears both contractId and partnerId when project changes', () => {
    expect(source).toMatch(
      /function onProjectChange\(val: string \| undefined\) \{[\s\S]*filter\.contractId = undefined[\s\S]*filter\.partnerId = undefined/,
    )
    expect(source).toContain('await loadContractOptions(val)')
  })

  it('renders mobile fallback with loading empty data tri-state and desktop-only column settings', () => {
    expect(source).toMatch(/<div v-if="isMobile" class="cost-ledger-mobile-list">/)
    expect(source).toMatch(/v-if="loading"/)
    expect(source).toMatch(/v-else-if="!tableData\.length"/)
    expect(source).toMatch(/<template v-else>/)
    expect(source).toMatch(/class="cost-ledger-mobile-card"/)
    expect(source).toMatch(/class="cost-ledger-mobile-card"[\s\S]*@click="showDetail\(row\)"/)
    expect(source).toMatch(/ColumnSettingsButton[\s\S]*v-if="!isMobile"/)
    expect(source).toMatch(/<div v-else class="lg-table-wrap cost-ledger-table-wrap">/)
  })

  it('keeps contract option loading non-blocking for main data requests', () => {
    expect(source).toMatch(/onMounted\(async \(\) => \{[\s\S]*void loadContractOptions\(filter\.projectId\)[\s\S]*void loadCostSubjectOptions\(\)[\s\S]*fetchData\(\)[\s\S]*fetchSummary\(\)/)
  })
})
