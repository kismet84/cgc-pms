import { describe, expect, it } from 'vitest'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const currentDir = dirname(fileURLToPath(import.meta.url))
const basicLayoutSource = readFileSync(resolve(currentDir, '../BasicLayoutAsync.vue'), 'utf-8')

describe('BasicLayout mobile shell CSS', () => {
  it('removes permanent sidebar offset and uses off-canvas navigation on narrow screens', () => {
    expect(basicLayoutSource).toContain('@media (max-width: 768px)')
    expect(basicLayoutSource).toMatch(/\.topbar,\s*\.main-content\s*\{[^}]*margin-left:\s*0/s)
    expect(basicLayoutSource).toMatch(/\.sidebar\s*\{[^}]*transform:\s*translateX\(-100%\)/s)
    expect(basicLayoutSource).toMatch(
      /:deep\(\.ant-layout-sider-collapsed\)\s*\+\s*\.ant-layout\s*\.topbar,[\s\S]*?margin-left:\s*0/s,
    )
  })
})
