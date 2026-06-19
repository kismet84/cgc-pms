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
      '合同状态统计',
      '逾期预警',
      '合同总金额(含税)',
      '未付款金额',
      '新建合同',
      '导出',
      '列设置',
      '合同管理',
      '合同台账',
    ]) {
      expect(ledgerSource).toContain(label)
    }

    expect(ledgerSource).toContain('cl-analysis-rail')
    expect(ledgerSource).toContain('statusDonutOption')
    expect(ledgerSource).toMatch(/<VChart[\s\S]*donutOption[\s\S]*<VChart[\s\S]*statusDonutOption/)
    expect(ledgerSource).not.toContain('目标成本管理')
  })

  it('loads project options only for the project filter', () => {
    expect(ledgerSource).toMatch(/v-model:value="filter\.projectId"[\s\S]*:options="projects\.map/)
    expect(ledgerSource).toMatch(/referenceStore\.fetchProjects\(\)/)

    const contractCodeInput = ledgerSource.match(
      /<a-input[\s\S]*?v-model:value="filter\.contractCode"[\s\S]*?\/>/,
    )?.[0]
    expect(contractCodeInput).toBeTruthy()
    expect(contractCodeInput).not.toContain(':options=')
  })
})
