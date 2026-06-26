import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'

import { scanContent } from '../../../../scripts/check-ui-style-consistency.mjs'

const currentDir = dirname(fileURLToPath(import.meta.url))
const sourcePath = 'src/layouts/components/SidebarMenu.vue'
const source = readFileSync(resolve(currentDir, '../SidebarMenu.vue'), 'utf-8')

describe('SidebarMenu UI consistency', () => {
  it('uses shared tokens for collapsed and expanded menu styling', () => {
    const findings = scanContent(sourcePath, source)
    const blockingRules = ['hex-color', 'rgba-color', 'box-shadow', 'border-radius']

    expect(findings.filter((finding) => blockingRules.includes(finding.rule))).toEqual([])
    expect(source).toContain('border-radius: var(--radius-lg)')
    expect(source).toContain('border-color: var(--primary-border-soft)')
    expect(source).toContain('border-inline-start-color: var(--primary)')
    expect(source).toContain('background: var(--primary-hover-bg) !important')
  })
})
