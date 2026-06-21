import { describe, expect, it } from 'vitest'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const currentDir = dirname(fileURLToPath(import.meta.url))
const ledgerSource = readFileSync(resolve(currentDir, '../ContractLedgerPage.vue'), 'utf-8')

describe('ContractLedger reference fidelity', () => {
  it('keeps the approved ledger copy and adds the reference analysis rail structure', () => {
    for (const label of [
      '合同类型分布',
      '合同状态',
      '逾期预警',
      '合同总金额',
      '未付款金额',
      '新建合同',
      '列设置',
      '合同管理',
      '合同台账',
    ]) {
      expect(ledgerSource).toContain(label)
    }

    expect(ledgerSource).toMatch(/typePercent|statusBars|kpiStrip|kpiPct/)
    expect(ledgerSource).not.toContain('目标成本管理')
  })

  it('loads project options only for the project filter', () => {
    expect(ledgerSource).toMatch(/v-model:value="filter\.projectId"[\s\S]*v-for="p in projects"/)
    expect(ledgerSource).toMatch(/referenceStore\.fetchProjects\(\)/)

    // 合同类型和状态筛选器存在于工具栏
    expect(ledgerSource).toMatch(/contractType/)
    expect(ledgerSource).toMatch(/contractStatus/)
  })
})
