import { describe, expect, it } from 'vitest'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const currentDir = dirname(fileURLToPath(import.meta.url))
const read = (p: string) => readFileSync(resolve(currentDir, '..', p), 'utf-8')

const indexSource = read('index.vue')
const composableSource = read('composables/useDashboardData.ts')
const bmViewSource = read('components/DashboardBmView.vue')
const costViewSource = read('components/DashboardCostView.vue')
const financeViewSource = read('components/DashboardFinanceView.vue')
const mgmtViewSource = read('components/DashboardMgmtView.vue')

const allViews = [bmViewSource, costViewSource, financeViewSource, mgmtViewSource].join('\n')
const fullSource = [indexSource, composableSource, allViews].join('\n')

describe('Dashboard role UI unification', () => {
  it('keeps role labels and moves non-PM dashboards onto the shared role layout language', () => {
    for (const label of ['项目总', '商务经理', '成本经理', '财务', '管理层']) {
      expect(fullSource).toContain(label)
    }

    for (const className of [
      'role-dashboard-grid',
      'role-metric-strip',
      'role-analysis-grid',
      'role-table-grid',
    ]) {
      expect(fullSource).toContain(className)
    }

    for (const label of [
      '合同经营概览',
      '变更签证分析',
      '结算收付概览',
      '成本执行概览',
      '成本构成分析',
      '偏差趋势分析',
      '资金支付概览',
      '付款结构分析',
      '资金风险概览',
      '项目经营总览',
      '项目风险分布',
      '经营趋势概览',
    ]) {
      expect(fullSource).toContain(label)
    }

    // Index.vue delegates to sub-components per role
    for (const role of ['bm', 'cost', 'finance', 'mgmt']) {
      expect(indexSource).toContain(`activeRole === '${role}' && `)
    }

    // Each non-PM view uses the shared role layout patterns
    for (const [role, source] of [
      ['business manager', bmViewSource],
      ['cost manager', costViewSource],
      ['finance', financeViewSource],
      ['management', mgmtViewSource],
    ] as const) {
      expect(source, `${role} template should be present`).toBeDefined()
      expect(source).toContain('role-dashboard-grid')
      expect(source).toContain('role-metric-strip')
      expect(source).toContain('role-analysis-grid')
      expect(source).toContain('role-table-grid')
      expect(source).not.toContain('class="chart-row"')
    }

    expect(fullSource).not.toContain('目标成本管理')
  })
})
