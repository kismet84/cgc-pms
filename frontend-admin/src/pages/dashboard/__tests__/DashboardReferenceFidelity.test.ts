import { describe, expect, it } from 'vitest'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const currentDir = dirname(fileURLToPath(import.meta.url))
const dashboardSource = readFileSync(resolve(currentDir, '../index.vue'), 'utf-8')

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
      expect(dashboardSource).toContain(label)
    }

    expect(dashboardSource).toContain('pm-reference-grid')
    expect(dashboardSource).toContain('pmBusinessOverviewOption')
    expect(dashboardSource).toContain('pmCostCompositionOption')
    expect(dashboardSource).toContain('pmFundingOverviewOption')
    expect(dashboardSource).toMatch(
      /<v-chart[\s\S]*pmBusinessOverviewOption[\s\S]*<v-chart[\s\S]*pmCostCompositionOption[\s\S]*<v-chart[\s\S]*pmFundingOverviewOption/,
    )
    expect(dashboardSource).not.toContain('目标成本管理')
  })
})
