import { describe, expect, it } from 'vitest'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const currentDir = dirname(fileURLToPath(import.meta.url))
const settlementSource = readFileSync(resolve(currentDir, '../index.vue'), 'utf-8')

describe('SettlementPage watch callback — Bug B-P1-2 fix', () => {
  it('watch on createForm.contractId resets settlementType when contract cleared', () => {
    // The fix: when contractId becomes falsy, reset settlementType
    // Previously: if (!val) createFormPartnerName (no-op — just reads the computed)
    expect(settlementSource).toMatch(
      /if\s*\(\s*!val\s*\)\s*createForm\.settlementType\s*=\s*undefined/,
    )
  })

  it('watch callback no longer has only createFormPartnerName no-op', () => {
    // The old pattern was "if (!val) createFormPartnerName" which does nothing
    // Ensure it's not just reading the computed
    const watchBlock = settlementSource.match(
      /watch\s*\([\s\S]*?createForm\.contractId[\s\S]*?\{[\s\S]*?\}/,
    )
    if (watchBlock) {
      // Should NOT contain the old no-op pattern
      expect(watchBlock[0]).not.toMatch(/\bcreateFormPartnerName\b/)
    }
  })
})
