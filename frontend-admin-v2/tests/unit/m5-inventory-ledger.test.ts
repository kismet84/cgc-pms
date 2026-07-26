import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { INVENTORY_WORKSPACE_PERMISSIONS } from '@cgc-pms/frontend-contracts'
import {
  createStockTransfer,
  createWarehouse,
  deleteWarehouse,
  loadStockConsumptionBaseline,
  loadStockIncomingSupplies,
  loadStockKpi,
  loadStockLedger,
  loadStockTransferCandidates,
  loadStocks,
  loadWarehouse,
  loadWarehouses,
  updateStockReplenishment,
  updateWarehouse,
  updateWarehouseStatus,
} from '@/services/supply-chain'

const fetchMock = vi.fn<typeof fetch>()
const response = (data: unknown = {}) =>
  new Response(JSON.stringify({ code: '0', message: 'success', data }), {
    status: 200,
    headers: { 'Content-Type': 'application/json' },
  })

beforeEach(() => {
  fetchMock
    .mockReset()
    .mockResolvedValue(response({ records: [], total: 0, pageNo: 1, pageSize: 20 }))
  vi.stubGlobal('fetch', fetchMock)
})
afterEach(() => vi.unstubAllGlobals())

describe('M5 inventory workspace contract', () => {
  it('keeps warehouse, stock and transaction permissions separate', () => {
    expect(Object.values(INVENTORY_WORKSPACE_PERMISSIONS)).toEqual([
      'inventory:warehouse:list',
      'inventory:warehouse:add',
      'inventory:warehouse:edit',
      'inventory:warehouse:delete',
      'inventory:stock:list',
      'inventory:stock:edit',
      'inventory:transaction:list',
      'inventory:transaction:add',
    ])
  })

  it('uses server facts, pagination, abort and no manual stock movement', () => {
    const source = readFileSync(
      resolve(process.cwd(), 'src/pages/supply-chain/InventoryWorkspacePage.vue'),
      'utf8',
    )
    expect(source).toContain("route.path === '/inventory/warehouse' ? 'warehouse' : 'stock'")
    expect(source).not.toContain("route.path === '/inventory/transaction'")
    expect(source).toContain('loadStocks')
    expect(source).toContain('title="全部库存余额"')
    expect(source).toContain('title="库存明细与流水"')
    expect(source).toContain(
      'panel-class="v2-dialog-standard v2-detail-dialog inventory-ledger-drawer"',
    )
    expect(source).toContain(':close-on-backdrop="true"')
    expect(source).toContain('class="v2-table__record-link"')
    expect(source).toContain('@click="openStock(item)"')
    expect(source).toContain('pageNo: pageNo.value')
    expect(source).toContain('const pageSize = 10')
    expect(source).toContain('<th v-if="!projectId">项目</th>')
    expect(source).toContain("item.projectName || '项目信息缺失'")
    expect(source).not.toContain("item.projectName || item.projectId || '项目信息缺失'")
    expect(source).toContain('aria-label="库存流水列表"')
    expect(source).toContain('tabindex="0"')
    expect(source).toContain('controller?.abort()')
    expect(source).toContain('await loadPage()')
    expect(source).toContain('crypto.randomUUID()')
    expect(source).toContain("{ value: '', label: '全部仓库' }")
    expect(source).toContain('allow-empty')
    expect(source).not.toContain('inventory-workspace-page__filters')
    expect(source).not.toContain('v-if="!errorMessage && !filter.materialId"')
    expect(source).toContain(':options="materialOptions"')
    expect(source).not.toContain('label="物料ID"')
    expect(source).toContain("{{ item.warehouseName || '仓库名称缺失' }}")
    expect(source).toContain("MAT_RECEIPT: '采购验收'")
    expect(source).toContain("MAT_REQUISITION: '领料'")
    expect(source).toContain("MATERIAL_RETURN_REVERSAL: '退料冲销'")
    expect(source).toContain("STOCK_TRANSFER: '库存调拨'")
    expect(source).not.toMatch(/PURCHASE_RECEIPT|\\bREQUISITION:|\\bTRANSFER:/)
    expect(source).toContain('loadStockKpi(')
    expect(source).toContain("session.hasPermission('material:dict:list')")
    expect(source).toContain('canReadMaterials.value')
    expect(source).toContain('canReadStock.value')
    expect(source).not.toMatch(
      /stock\/in|stock\/out|Number\(ledger\.stock\.(?:availableQty|safetyStockQty)\)|availableQty\s*[+]=|inventoryValue\s*[+]=|frontend-admin\/src/,
    )
  })

  it('encodes ids and preserves decimal strings for every inventory endpoint', async () => {
    const signal = new AbortController().signal
    fetchMock.mockImplementation(async (url, init) => {
      if (init?.method === 'POST' && String(url).endsWith('/inventory/warehouses'))
        return response('9007199254740993')
      if (String(url).includes('transfer-candidates')) return response([])
      if (String(url).includes('incoming-supplies')) return response([])
      if (String(url).includes('consumption-baseline')) return response({ netIssued30: '1.0000' })
      if (String(url).endsWith('/kpi')) return response({ warehouseCount: 1 })
      if (String(url).includes('/ledger'))
        return response({ stock: null, txns: { records: [], total: 0 } })
      return response({ id: 'W/1' })
    })
    await loadWarehouses({ projectId: ' P/1 ', pageNo: 2 }, signal)
    await loadWarehouse('W/1', signal)
    const id = await createWarehouse({
      projectId: 'P1',
      warehouseCode: 'W1',
      warehouseName: '仓库',
      status: 'ENABLE',
    })
    await updateWarehouse('W/1', {
      projectId: 'P1',
      warehouseCode: 'W1',
      warehouseName: '仓库',
      status: 'ENABLE',
    })
    await updateWarehouseStatus('W/1', 'DISABLE')
    await deleteWarehouse('W/1')
    await loadStockLedger({ warehouseId: 'W/1', materialId: 'M/1', pageNo: 2 }, signal)
    await loadStockLedger({ materialId: 'M/1', pageNo: 2 }, signal)
    await loadStocks({ warehouseId: 'W/1', keyword: '钢 筋', pageNo: 2 }, signal)
    await loadStockKpi({ warehouseId: 'W/1' }, signal)
    await loadStockTransferCandidates('S/1', signal)
    await loadStockIncomingSupplies('S/1', signal)
    await loadStockConsumptionBaseline('S/1', signal)
    await updateStockReplenishment('S/1', {
      safetyStockQty: '9007199254740993.1234',
      replenishmentTargetQty: '10.0000',
      replenishmentLeadDays: 7,
    })
    await createStockTransfer({
      sourceStockId: 'S1',
      targetStockId: 'S2',
      quantity: '1.2500',
      idempotencyKey: 'K1',
      reason: '平衡库存',
    })
    expect(id).toBe('9007199254740993')
    const calls = fetchMock.mock.calls
    expect(calls.map(([url]) => String(url))).toContain('/api/inventory/warehouses/W%2F1')
    expect(calls.map(([url]) => String(url))).toContain(
      '/api/inventory/stock/S%2F1/transfer-candidates',
    )
    expect(calls.map(([url]) => String(url))).toContain(
      '/api/inventory/stock/ledger?materialId=M%2F1&pageNo=2',
    )
    expect(calls.map(([url]) => String(url))).toContain(
      '/api/inventory/stock?warehouseId=W%2F1&keyword=%E9%92%A2+%E7%AD%8B&pageNo=2',
    )
    expect(calls.filter(([, init]) => init?.signal === signal)).toHaveLength(9)
    const settings = calls.find(([url]) => String(url).includes('/replenishment-settings'))
    expect(JSON.parse(String(settings?.[1]?.body)).safetyStockQty).toBe('9007199254740993.1234')
  })
})
