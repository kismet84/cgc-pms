import { describe, expect, it } from 'vitest'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const currentDir = dirname(fileURLToPath(import.meta.url))
const ledgerSource = readFileSync(resolve(currentDir, '../ContractLedgerPage.vue'), 'utf-8')
const composableSource = readFileSync(
  resolve(currentDir, '../composables/useContractLedger.ts'),
  'utf-8',
)
const analysisSource = readFileSync(
  resolve(currentDir, '../components/ContractAnalysisPanel.vue'),
  'utf-8',
)
const kpiSource = readFileSync(resolve(currentDir, '../components/ContractKpiStrip.vue'), 'utf-8')
const columnSettingsSource = readFileSync(
  resolve(currentDir, '../../../components/list-page/ColumnSettingsButton.vue'),
  'utf-8',
)

describe('ContractLedger reference fidelity', () => {
  it('keeps the approved ledger copy and adds the reference analysis rail structure', () => {
    // Page-level strings
    for (const label of ['新建合同', '合同管理', '合同台账']) {
      expect(ledgerSource).toContain(label)
    }
    expect(ledgerSource).toContain('ColumnSettingsButton')
    expect(columnSettingsSource).toContain('列设置')
    // Analysis panel strings (moved to component)
    for (const label of ['合同类型分布', '合同状态', '逾期预警', '合同总金额', '未付款金额']) {
      expect(
        analysisSource.includes(label) ||
          kpiSource.includes(label) ||
          composableSource.includes(label),
      ).toBe(true)
    }

    // Key computed/function references still reachable
    expect(composableSource).toMatch(/typePercent|statusBars|kpiPct/)
    // KPI strip component references
    expect(kpiSource).toMatch(/kpiStrip|kpiPct|kpi-strip/)
    expect(ledgerSource).not.toContain('目标成本管理')
  })

  it('loads project options only for the project filter', () => {
    expect(ledgerSource).toMatch(/v-model:value="filter\.projectId"[\s\S]*v-for="p in projects"/)
    expect(composableSource).toMatch(/referenceStore\.fetchProjects\(\)/)

    // 合同类型和状态筛选器存在于工具栏
    expect(composableSource).toMatch(/contractType/)
    expect(composableSource).toMatch(/contractStatus/)
  })
})
