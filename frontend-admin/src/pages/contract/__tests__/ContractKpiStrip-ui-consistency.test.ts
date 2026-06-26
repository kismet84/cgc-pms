import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'

import { scanContent } from '../../../../scripts/check-ui-style-consistency.mjs'

const currentDir = dirname(fileURLToPath(import.meta.url))
const sourcePath = 'src/pages/contract/components/ContractKpiStrip.vue'
const source = readFileSync(resolve(currentDir, '../components/ContractKpiStrip.vue'), 'utf-8')

describe('ContractKpiStrip UI consistency', () => {
  it('uses tokenized classes for the desktop KPI summary strip', () => {
    const findings = scanContent(sourcePath, source)

    expect(findings).toEqual([])
    expect(source).toContain('class="cl-kpi-summary"')
    expect(source).toContain('aria-label="合同关键指标"')
    expect(source).toContain('class="cl-kpi-icon is-total"')
    expect(source).toContain('class="cl-kpi-icon is-amount"')
    expect(source).toContain('class="cl-kpi-progress"')
    expect(source).toContain('background: var(--surface)')
    expect(source).toContain('border: 1px solid var(--border-subtle)')
  })
})
