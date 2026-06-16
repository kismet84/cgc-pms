import { describe, expect, it } from 'vitest'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const currentDir = dirname(fileURLToPath(import.meta.url))
const dashboardSource = readFileSync(resolve(currentDir, '../index.vue'), 'utf-8')

describe('Dashboard role UI unification', () => {
  it('keeps role labels and moves non-PM dashboards onto the shared role layout language', () => {
    for (const label of ['项目总', '商务经理', '成本经理', '财务', '管理层']) {
      expect(dashboardSource).toContain(label)
    }

    for (const className of [
      'role-dashboard-grid',
      'role-metric-strip',
      'role-analysis-grid',
      'role-table-grid',
    ]) {
      expect(dashboardSource).toContain(className)
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
      expect(dashboardSource).toContain(label)
    }

    const bmTemplate = dashboardSource.match(
      /activeRole === 'bm' && bmData([\s\S]*?)activeRole === 'cost' && costData/,
    )?.[1]
    const costTemplate = dashboardSource.match(
      /activeRole === 'cost' && costData([\s\S]*?)activeRole === 'finance' && financeData/,
    )?.[1]
    const financeTemplate = dashboardSource.match(
      /activeRole === 'finance' && financeData([\s\S]*?)activeRole === 'mgmt' && mgmtData/,
    )?.[1]
    const mgmtTemplate = dashboardSource.match(
      /activeRole === 'mgmt' && mgmtData([\s\S]*?)class="empty-page"/,
    )?.[1]

    for (const [role, template] of [
      ['business manager', bmTemplate],
      ['cost manager', costTemplate],
      ['finance', financeTemplate],
      ['management', mgmtTemplate],
    ] as const) {
      expect(template, `${role} template should be present`).toBeDefined()
      expect(template).toContain('role-dashboard-grid')
      expect(template).toContain('role-metric-strip')
      expect(template).toContain('role-analysis-grid')
      expect(template).toContain('role-table-grid')
      expect(template).not.toContain('class="chart-row"')
    }

    expect(dashboardSource).not.toContain('目标成本管理')
  })
})
