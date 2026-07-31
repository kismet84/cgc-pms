import { describe, expect, it } from 'vitest'
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'

const currentDir = __dirname
const stockSource = readFileSync(resolve(currentDir, '../stock.vue'), 'utf8')
const panelSource = readFileSync(
  resolve(currentDir, '../components/StockAnalysisPanel.vue'),
  'utf8',
)
const apiSource = readFileSync(resolve(currentDir, '../../../api/modules/inventory.ts'), 'utf8')
const typeSource = readFileSync(resolve(currentDir, '../../../types/inventory.ts'), 'utf8')

describe('同项目跨仓库存调拨入口', () => {
  it('只向同时具备库存编辑与流水新增权限的非管理员展示入口', () => {
    expect(stockSource).toContain("userStore.hasPermission('inventory:stock:edit') &&")
    expect(stockSource).toContain("userStore.hasPermission('inventory:transaction:add')")
    expect(panelSource).toContain('v-if="canTransfer"')
  })

  it('从服务端候选携带的库存ID发起而非自行拼装来源仓库存', () => {
    expect(typeSource).toContain('stockId: string')
    expect(stockSource).toContain('sourceStockId: candidate.stockId')
    expect(stockSource).toContain('targetStockId: target.id')
  })

  it('限制最大可调拨量并保留失败请求的幂等键', () => {
    expect(stockSource).toContain(':max="Number(transferCandidate?.transferableQty ?? 0)"')
    expect(stockSource).toContain('quantity > Number(candidate.transferableQty)')
    expect(stockSource).toContain('idempotencyKey: transferKey.value')
    expect(stockSource).not.toContain('transferKey.value = newTransferKey()\n  } catch')
  })

  it('提交独立调拨端点并在成功后刷新库存、候选和流水', () => {
    expect(apiSource).toContain("url: '/inventory/stock/transfers'")
    expect(apiSource).toContain("method: 'post'")
    expect(stockSource).toContain('await createStockTransfer')
    expect(stockSource).toContain('handleSearch()')
  })
})
