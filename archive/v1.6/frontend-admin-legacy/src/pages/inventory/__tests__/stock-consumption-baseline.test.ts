import { describe, expect, it } from 'vitest'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const currentDir = dirname(fileURLToPath(import.meta.url))
const apiSource = readFileSync(resolve(currentDir, '../../../api/modules/inventory.ts'), 'utf8')
const composableSource = readFileSync(
  resolve(currentDir, '../composables/useStockLedger.ts'),
  'utf8',
)
const panelSource = readFileSync(
  resolve(currentDir, '../components/StockAnalysisPanel.vue'),
  'utf8',
)
const pageSource = readFileSync(resolve(currentDir, '../stock.vue'), 'utf8')

describe('stock consumption baseline contract', () => {
  it('uses the stock-scoped read-only endpoint', () => {
    expect(apiSource).toContain('`/inventory/stock/${id}/consumption-baseline`')
    expect(apiSource).toContain('request<StockConsumptionBaselineVO>')
  })

  it('guards asynchronous baseline responses against stock switches', () => {
    expect(composableSource).toContain('const mySeq = ++consumptionBaselineSeq')
    expect(composableSource).toContain(
      'mySeq === consumptionBaselineSeq && stock.value?.id === currentStock.id',
    )
    expect(composableSource).toContain('consumptionBaselineSeq += 1')
  })

  it('shows thirty and ninety day facts without claiming a forecast', () => {
    expect(panelSource).toContain('历史净领料')
    expect(panelSource).toContain('近 30 日')
    expect(panelSource).toContain('近 90 日')
    expect(panelSource).toContain('历史事实，非需求预测')
    expect(panelSource).toContain('历史基线暂不可用')
    expect(panelSource).toContain('consumptionBaseline.window30Start')
    expect(panelSource).toContain('consumptionBaseline.window90Start')
    expect(panelSource).toContain('consumptionBaseline.cutoffAt.slice(0, 10)')
  })

  it('wires baseline state into the analysis panel', () => {
    expect(pageSource).toContain(':consumption-baseline="consumptionBaseline"')
    expect(pageSource).toContain(':consumption-baseline-loading="consumptionBaselineLoading"')
    expect(pageSource).toContain(':consumption-baseline-error="consumptionBaselineError"')
  })
})
