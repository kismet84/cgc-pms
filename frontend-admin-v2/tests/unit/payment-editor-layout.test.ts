import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'

describe('payment editor layout', () => {
  it('uses two columns only for payment and collapses on small screens', () => {
    const page = readFileSync(
      resolve(process.cwd(), 'src/pages/finance/ReceivablesWorkspacePage.vue'),
      'utf8',
    )

    expect(page).toContain("'finance-workspace__form--payment': editorKind === 'payment'")
    expect(page).toMatch(
      /\.finance-workspace__form--payment\s*{\s*grid-template-columns: repeat\(2, minmax\(0, 1fr\)\);\s*}/,
    )
    expect(page).toMatch(
      /@media \(max-width: 32\.5rem\)[\s\S]*?\.finance-workspace__form--payment\s*{\s*grid-template-columns: 1fr;/,
    )
  })
})
