import { describe, expect, it } from 'vitest'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const currentDir = dirname(fileURLToPath(import.meta.url))
const basicLayoutSource = readFileSync(resolve(currentDir, '../BasicLayoutAsync.vue'), 'utf-8')

describe('BasicLayout mobile shell CSS', () => {
  it('uses a compact sidebar rail instead of the removed topbar on narrow screens', () => {
    expect(basicLayoutSource).toContain('@media (max-width: 768px)')
    expect(basicLayoutSource).not.toContain('class="topbar"')
    expect(basicLayoutSource).toMatch(
      /\.main-content\s*\{[^}]*margin-left:\s*var\(--shell-sidebar-collapsed-width\)/s,
    )
    expect(basicLayoutSource).toMatch(/\.sidebar\s*\{[^}]*transform:\s*translateX\(0\)/s)
    expect(basicLayoutSource).toMatch(
      /:deep\(\.ant-layout-sider-collapsed\)\s*\+\s*\.ant-layout\s*\.main-content\s*\{[^}]*margin-left:\s*var\(--shell-sidebar-collapsed-width\)/s,
    )
  })
})
