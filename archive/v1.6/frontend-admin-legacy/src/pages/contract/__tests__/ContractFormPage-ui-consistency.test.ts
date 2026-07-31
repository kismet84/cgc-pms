import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'
import { scanContent } from '../../../../scripts/check-ui-style-consistency.mjs'

const sourcePath = resolve(__dirname, '..', 'ContractFormPage.vue')
const source = readFileSync(sourcePath, 'utf-8')

describe('ContractFormPage UI consistency', () => {
  it('uses semantic classes and shared tokens instead of inline visual styles', () => {
    const findings = scanContent('src/pages/contract/ContractFormPage.vue', source)
    const blockingRules = ['inline-style', 'hex-color', 'border-radius']

    expect(findings.filter((finding) => blockingRules.includes(finding.rule))).toEqual([])
    expect(source).toContain('class="cf-wizard-panel pt-panel"')
    expect(source).toContain('class="cf-full-control"')
    expect(source).toContain('class="cf-date-picker"')
    expect(source).toContain('background: var(--surface)')
    expect(source).toContain('border-radius: var(--radius-md)')
  })
})
