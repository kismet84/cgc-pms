import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'
import { scanContent } from '../../../../scripts/check-ui-style-consistency.mjs'

const currentDir = dirname(fileURLToPath(import.meta.url))
const source = readFileSync(resolve(currentDir, '..', 'ContractDetailPage.vue'), 'utf-8')

describe('contract detail page UI consistency', () => {
  it('keeps page-level visual styles tokenized and class-based', () => {
    const findings = scanContent('src/pages/contract/ContractDetailPage.vue', source)

    expect(findings).toEqual([])
    expect(source).not.toContain('style="')
    expect(source).toContain('class="contract-detail-contract-amount"')
    expect(source).toContain('class="contract-detail-action-tag"')
    expect(source).toContain('class="contract-detail-record-node"')
    expect(source).toContain('class="contract-detail-record-comment"')
    expect(source).toContain('class="contract-detail-record-time"')
  })

  it('does not keep fallback hex or rgba values in mobile detail styles', () => {
    expect(source).not.toContain('var(--surface, #fff)')
    expect(source).not.toContain('var(--border-subtle, #f0f0f0)')
    expect(source).not.toContain('var(--text, #333)')
    expect(source).not.toContain('rgba(')
    expect(source).not.toContain('box-shadow:')
    expect(source).not.toContain('border-radius:')
  })
})
