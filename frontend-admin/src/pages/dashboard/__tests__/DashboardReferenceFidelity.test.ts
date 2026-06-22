import { describe, expect, it } from 'vitest'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const currentDir = dirname(fileURLToPath(import.meta.url))
const read = (p: string) => readFileSync(resolve(currentDir, '..', p), 'utf-8')

// Read all split files to form the full source picture
const indexSource = read('index.vue')
const composableSource = read('composables/useDashboardData.ts')
const chartOptsSource = read('utils/chartOptions.ts')
const pmViewSource = read('components/DashboardPmView.vue')
const bmViewSource = read('components/DashboardBmView.vue')
const costViewSource = read('components/DashboardCostView.vue')
const financeViewSource = read('components/DashboardFinanceView.vue')
const mgmtViewSource = read('components/DashboardMgmtView.vue')

const allViews = [
  pmViewSource,
  bmViewSource,
  costViewSource,
  financeViewSource,
  mgmtViewSource,
].join('\n')
const allSource = [indexSource, composableSource, chartOptsSource, ...allViews.split('\n')].join(
  '\n',
)

describe('Dashboard reference fidelity', () => {
  it('keeps the approved dashboard copy and adds the reference chart/table structure', () => {
    for (const label of [
      '项目经营概览',
      '成本构成分析',
      '资金收支概览',
      '临期合同（30天内到期）',
      '项目总',
      '商务经理',
      '成本经理',
      '财务',
      '管理层',
    ]) {
      expect(allSource).toContain(label)
    }

    expect(allSource).toContain('pm-reference-grid')
    expect(allSource).toContain('pmBusinessOverviewOption')
    expect(allSource).toContain('pmCostCompositionOption')
    expect(allSource).toContain('pmFundingOverviewOption')

    // The v-chart with these options is now in DashboardPmView.vue
    expect(pmViewSource).toContain('v-chart')
    expect(pmViewSource).toContain('pmBusinessOverviewOption')
    expect(pmViewSource).toContain('pmCostCompositionOption')
    expect(pmViewSource).toContain('pmFundingOverviewOption')

    // Chart ordering within pmViewSource
    expect(
      /pmBusinessOverviewOption[\s\S]*pmCostCompositionOption[\s\S]*pmFundingOverviewOption/.test(
        pmViewSource,
      ),
    ).toBe(true)

    expect(allSource).not.toContain('目标成本管理')
  })
})
