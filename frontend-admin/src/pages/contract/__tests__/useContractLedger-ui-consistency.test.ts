import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'
import { scanContent } from '../../../../scripts/check-ui-style-consistency.mjs'

const sourcePath = resolve(__dirname, '../composables/useContractLedger.ts')

describe('useContractLedger UI consistency', () => {
  it('does not define hardcoded colors outside theme tokens', () => {
    const source = readFileSync(sourcePath, 'utf8')
    const findings = scanContent('src/pages/contract/composables/useContractLedger.ts', source)

    expect(findings).toEqual([])
  })

  it('derives contract type and status labels from dictionaries', () => {
    const source = readFileSync(sourcePath, 'utf8')

    expect(source).toContain("const CONTRACT_TYPE_DICT = 'contract_type'")
    expect(source).toContain("const CONTRACT_STATUS_DICT = 'contract_status'")
    expect(source).toContain('fetchDictData(CONTRACT_TYPE_DICT)')
    expect(source).toContain('fetchDictData(CONTRACT_STATUS_DICT)')
    expect(source).not.toContain('export const TYPE_LABEL')
    expect(source).not.toContain('export const STATUS_LABEL')
  })

  it('defaults low-priority ledger columns to hidden while keeping them configurable', () => {
    const source = readFileSync(sourcePath, 'utf8')

    expect(source).toContain("'contract_ledger_cols_v2'")
    expect(source).toContain('useColumnSettings(')
    expect(source).toContain('partyAName: false')
    expect(source).toContain('signedDate: false')
  })
})
