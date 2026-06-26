import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'

import { scanContent } from '../../../scripts/check-ui-style-consistency.mjs'

const sourcePath = 'src/layouts/BasicLayoutShell.vue'
const source = readFileSync(resolve(__dirname, '../BasicLayoutShell.vue'), 'utf-8')

describe('BasicLayoutShell UI consistency', () => {
  it('uses radius tokens for loading shell visuals', () => {
    expect(scanContent(sourcePath, source)).toEqual([])
    expect(source).toContain('border-radius: var(--radius-full)')
  })
})
