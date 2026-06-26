import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'

import { scanContent } from '../../../../scripts/check-ui-style-consistency.mjs'

const currentDir = dirname(fileURLToPath(import.meta.url))
const sourcePath = 'src/pages/contract/components/ContractAnalysisPanel.vue'
const source = readFileSync(resolve(currentDir, '../components/ContractAnalysisPanel.vue'), 'utf-8')

describe('ContractAnalysisPanel UI consistency', () => {
  it('uses tokenized classes for the unified analysis rail', () => {
    const findings = scanContent(sourcePath, source)

    expect(findings).toEqual([])
    expect(source).toContain('class="lg-analysis-rail cl-analysis-rail"')
    expect(source).toContain('aria-label="合同辅助分析"')
    expect(source).toContain('class="cl-analysis-panel"')
    expect(source).toContain('class="cl-analysis-section"')
    expect(source).toContain('class="cl-warning-head"')
    expect(source).toContain('width: 336px')
    expect(source).toContain('border: 1px solid var(--border-subtle)')
  })
})
