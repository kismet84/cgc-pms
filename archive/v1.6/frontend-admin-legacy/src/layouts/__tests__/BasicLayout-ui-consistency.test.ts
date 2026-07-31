import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'

import { describe, expect, it } from 'vitest'

import { scanContent } from '../../../scripts/check-ui-style-consistency.mjs'

const sourcePath = 'src/layouts/BasicLayoutAsync.vue'
const source = readFileSync(resolve(__dirname, '../BasicLayoutAsync.vue'), 'utf-8')

describe('BasicLayoutAsync UI consistency', () => {
  it('keeps shell visuals on shared theme variables instead of hardcoded colors', () => {
    const findings = scanContent(sourcePath, source)
    const disallowedRules = new Set(['hex-color', 'rgba-color', 'linear-gradient', 'box-shadow'])

    expect(findings.filter((finding) => disallowedRules.has(finding.rule))).toEqual([])
    expect(source).toContain('var(--shell-bg)')
    expect(source).toContain('var(--shell-sidebar-bg)')
    expect(source).toContain('var(--brand-gradient-avatar)')
    expect(source).toContain('var(--brand-logo-fg)')
  })

  it('avoids inline avatar styles in the layout shell', () => {
    expect(source).not.toContain(':style=')
    expect(source).toContain('class="user-avatar"')
  })
})
