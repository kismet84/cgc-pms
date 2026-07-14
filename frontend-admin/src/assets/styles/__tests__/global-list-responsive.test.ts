import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'

const currentDir = dirname(fileURLToPath(import.meta.url))
const stylesheet = readFileSync(resolve(currentDir, '../global-app-redesign.css'), 'utf-8')
const rolePage = readFileSync(resolve(currentDir, '../../../pages/system/roles/index.vue'), 'utf-8')

function extractMediaBlock(query: string) {
  const start = stylesheet.indexOf(query)
  expect(start).toBeGreaterThanOrEqual(0)

  const openingBrace = stylesheet.indexOf('{', start)
  let depth = 0
  for (let index = openingBrace; index < stylesheet.length; index += 1) {
    if (stylesheet[index] === '{') depth += 1
    if (stylesheet[index] === '}') depth -= 1
    if (depth === 0) return stylesheet.slice(start, index + 1)
  }

  throw new Error(`Unclosed media block: ${query}`)
}

describe('shared list responsive height contract', () => {
  it('gives every supported table-panel nesting a bounded non-zero height at 1200px and below', () => {
    const responsiveBlock = extractMediaBlock('@media (max-width: 1200px)')

    expect(responsiveBlock).toContain('.lg-list-page .lg-grid')
    expect(responsiveBlock).toContain('display: block')
    expect(responsiveBlock).toMatch(
      /\.lg-list-page \.lg-left > \.lg-list-table-panel,[\s\S]*?\.lg-list-page \.ct-main-column > \.lg-list-table-panel,[\s\S]*?\.lg-list-page \.lg-grid > \.lg-list-table-panel \{\s*min-height: clamp\(420px, 58vh, 640px\);\s*\}/,
    )
  })

  it('keeps the desktop workspace height rule above the responsive breakpoint', () => {
    const desktopBlock = extractMediaBlock('@media (min-width: 1201px)')

    expect(desktopBlock).toMatch(
      /\.lg-list-page > \.lg-grid \{\s*min-height: 0;\s*height: var\(--lg-workspace-height\);\s*\}/,
    )
  })

  it('covers the role page through shared layout classes without a page-specific workaround', () => {
    expect(rolePage).toContain('<div class="lg-grid">')
    expect(rolePage).toContain('<main class="lg-list-table-panel">')
    expect(rolePage).toContain('<div class="lg-table-wrap">')
    expect(rolePage).not.toContain('@media (max-width: 1200px)')
  })
})
