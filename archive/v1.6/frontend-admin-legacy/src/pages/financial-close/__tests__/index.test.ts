import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'

describe('financial close page source contract', () => {
  const source = readFileSync(
    resolve(dirname(fileURLToPath(import.meta.url)), '../index.vue'),
    'utf-8',
  )

  it('exposes the complete month-end lifecycle', () => {
    expect(source).toContain('运行月结检查')
    expect(source).toContain('执行月结')
    expect(source).toContain('反结账')
    expect(source).toContain('新建调整凭证')
    expect(source).toContain('应收应付对账')
    expect(source).toContain('银企对账')
    expect(source).toContain('凭证与审计追溯')
  })

  it('prevents closing while checks report issues', () => {
    expect(source).toContain(':disabled="issueCount > 0"')
    expect(source).toContain('closeFinancialPeriod')
    expect(source).toContain('reopenFinancialPeriod')
  })
})
