import { describe, expect, it } from 'vitest'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const currentDir = dirname(fileURLToPath(import.meta.url))
const source = readFileSync(resolve(currentDir, '../composables/useStockLedger.ts'), 'utf-8')

describe('stock ledger project contract', () => {
  it('passes projectId to ledger and kpi requests', () => {
    expect(source).toMatch(
      /const filter = reactive\(\{[\s\S]*?projectId: undefined as string \| undefined/,
    )
    expect(source).toMatch(/getStockLedger\(\{[\s\S]*?projectId: filter\.projectId/)
    expect(source).toMatch(/getStockKpi\(\{[\s\S]*?projectId: filter\.projectId/)
  })

  it('refreshes kpi on search and project change', () => {
    expect(source).toMatch(/function handleSearch\(\) \{[\s\S]*?fetchData\(\)[\s\S]*?fetchKpi\(\)/)
    expect(source).toMatch(
      /function onProjectChange\(projectId: string \| undefined\) \{[\s\S]*?fetchKpi\(\)/,
    )
  })
})
