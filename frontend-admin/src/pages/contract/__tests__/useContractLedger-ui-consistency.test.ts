import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'
import { scanContent } from '../../../../scripts/check-ui-style-consistency.mjs'
import { STATUS_COLOR, TYPE_CHART_COLOR } from '../composables/useContractLedger'

const sourcePath = resolve(__dirname, '../composables/useContractLedger.ts')

describe('useContractLedger UI consistency', () => {
  it('does not define hardcoded colors outside theme tokens', () => {
    const source = readFileSync(sourcePath, 'utf8')
    const findings = scanContent('src/pages/contract/composables/useContractLedger.ts', source)

    expect(findings).toEqual([])
  })

  it('exposes chart colors as semantic CSS variables', () => {
    expect(Object.values(TYPE_CHART_COLOR)).toEqual([
      'var(--primary)',
      'var(--success)',
      'var(--warning)',
      'var(--info)',
      'var(--text-secondary)',
    ])
    expect(Object.values(STATUS_COLOR)).toEqual([
      'var(--text-secondary)',
      'var(--primary)',
      'var(--success)',
      'var(--error)',
    ])
  })

  it('defaults low-priority ledger columns to hidden while keeping them configurable', () => {
    const source = readFileSync(sourcePath, 'utf8')

    expect(source).toContain("const COLS_KEY = 'contract_ledger_cols_v2'")
    expect(source).toContain('partyAName: false')
    expect(source).toContain('signedDate: false')
  })
})
