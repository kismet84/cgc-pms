import { describe, expect, it } from 'vitest'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const currentDir = dirname(fileURLToPath(import.meta.url))
const pageSource = readFileSync(resolve(currentDir, '../stock.vue'), 'utf-8')
const composableSource = readFileSync(resolve(currentDir, '../composables/useStockLedger.ts'), 'utf-8')

describe('stock ledger production hardening', () => {
  it('hydrates and persists filters through route query', () => {
    expect(pageSource).toContain("import { useRoute, useRouter } from 'vue-router'")
    expect(pageSource).toContain('useStockLedger({ route, router })')
    expect(composableSource).toContain('readStringQuery(route.query.projectId)')
    expect(composableSource).toContain('readStringQuery(route.query.warehouseId)')
    expect(composableSource).toContain('readStringQuery(route.query.materialId)')
    expect(composableSource).toContain('readPositiveIntQuery(route.query.pageNo, 1)')
    expect(composableSource).toContain('await router.replace({ path: route.path, query: nextQuery })')
  })

  it('tracks explicit loaded and error states with retry support', () => {
    expect(composableSource).toContain('const listError = ref<string | null>(null)')
    expect(composableSource).toContain('const hasLoaded = ref(false)')
    expect(pageSource).toContain('<a-result')
    expect(pageSource).toContain('title="库存台账加载失败"')
    expect(pageSource).toContain('<LgEmptyState description="暂无符合条件的库存流水">')
  })

  it('starts replenishment only from the selected tenant-scoped warehouse project', () => {
    expect(composableSource).toContain('function handleReplenish()')
    expect(composableSource).toContain('warehouseList.value.find((w) => w.id === stock.value?.warehouseId)?.projectId')
    expect(composableSource).toContain("path: '/inventory/purchase-request'")
    expect(composableSource).toContain('const safetyStockQty = Number(stock.value?.safetyStockQty)')
    expect(composableSource).toContain(
      'const suggestedQuantity = Math.max(0, safetyStockQty - quantity).toFixed(4)',
    )
    expect(composableSource).toContain('quantity: suggestedQuantity')
    expect(pageSource).toContain('@replenish="handleReplenish"')
  })

  it('uses and maintains the selected stock safety threshold', () => {
    expect(composableSource).toContain('updateStockSafetyThreshold')
    expect(composableSource).toContain('Number(stock.value?.safetyStockQty)')
    expect(pageSource).toContain('安全库存阈值')
    expect(pageSource).toContain('handleSafetyThresholdSave')
  })
})
