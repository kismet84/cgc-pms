import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'

const currentDir = dirname(fileURLToPath(import.meta.url))
const stylesheet = readFileSync(resolve(currentDir, '../global-app-redesign.css'), 'utf-8')

describe('shared modal liquid-glass contract', () => {
  it('styles the shared Ant Design modal entry without forcing a desktop review width', () => {
    expect(stylesheet).toContain('.ant-modal-root .ant-modal-mask')
    expect(stylesheet).toContain(':root .ant-modal-root .ant-modal .ant-modal-content')
    expect(stylesheet).toContain('background: var(--modal-surface)')
    expect(stylesheet).toContain('box-shadow: var(--modal-shadow)')
    expect(stylesheet).toMatch(
      /\.ant-modal-content \{[\s\S]*?display: flex;[\s\S]*?flex-direction: column;[\s\S]*?max-height: calc\(100vh - 48px\);[\s\S]*?overflow: hidden;/,
    )
    expect(stylesheet).toMatch(
      /\.ant-modal-body \{[\s\S]*?flex: 1 1 auto;[\s\S]*?min-height: 0;[\s\S]*?overflow: auto;/,
    )
    expect(stylesheet).toMatch(/\.ant-modal-header,[\s\S]*?\.ant-modal-footer \{\s*flex: 0 0 auto;/)
    expect(stylesheet).not.toContain('1040px')
  })

  it('keeps mobile and accessibility fallbacks in the shared contract', () => {
    expect(stylesheet).toContain('@media (max-width: 640px)')
    expect(stylesheet).toContain('align-items: flex-end')
    expect(stylesheet).toContain('@supports not ((backdrop-filter: blur(1px))')
    expect(stylesheet).toContain('background: var(--modal-surface-solid)')
    expect(stylesheet).toContain('@media (prefers-reduced-motion: reduce)')
    expect(stylesheet).toContain('animation: none !important')
    expect(stylesheet).toContain('@media (forced-colors: active)')
    expect(stylesheet).toContain('background: Canvas')
  })
})
