import { describe, expect, it } from 'vitest'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const currentDir = dirname(fileURLToPath(import.meta.url))
const basicLayoutSource = readFileSync(resolve(currentDir, '../BasicLayoutAsync.vue'), 'utf-8')

describe('BasicLayout mobile shell CSS', () => {
  it('uses a full-width mobile shell below 500px and restores desktop at 500px', () => {
    expect(basicLayoutSource).toContain(
      "import { useMobileViewport } from '@/composables/useMobileViewport'",
    )
    expect(basicLayoutSource).toContain('@media (width < 500px)')
    expect(basicLayoutSource).toContain('class="mobile-topbar"')
    expect(basicLayoutSource).toContain(':collapsed-width="isMobile ? 0 : 72"')
    expect(basicLayoutSource).toContain(
      'const { isMobile, isCompactDesktop } = useMobileViewport()',
    )
    expect(basicLayoutSource).toContain('if (mobile || compactDesktop)')
    expect(basicLayoutSource).toMatch(/\.main-content\s*\{[^}]*margin-left:\s*0/s)
    expect(basicLayoutSource).toMatch(
      /:deep\(\.ant-layout-sider-collapsed\)\s*\+\s*\.ant-layout\s*\.main-content\s*\{[^}]*margin-left:\s*0/s,
    )
  })
})
