import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'

describe('payment editor layout', () => {
  it('uses two columns only for payment and collapses on small screens', () => {
    const page = [
      'src/pages/finance/receivables-workspace/PaymentApplicationPage.vue',
      'src/pages/finance/receivables-workspace/finance-workspace.css',
    ]
      .map((path) => readFileSync(resolve(process.cwd(), path), 'utf8'))
      .join('\n')

    expect(page).toContain('class="finance-workspace__form finance-workspace__form--payment"')
    expect(page).toMatch(
      /\.finance-workspace__form--payment\s*{\s*grid-template-columns: repeat\(2, minmax\(0, 1fr\)\);\s*}/,
    )
    expect(page).toMatch(
      /@media \(max-width: 32\.5rem\)[\s\S]*?\.finance-workspace__form--payment\s*{\s*grid-template-columns: 1fr;/,
    )
  })
})
