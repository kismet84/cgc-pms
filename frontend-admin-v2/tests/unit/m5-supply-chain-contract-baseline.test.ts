import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { readFileSync, readdirSync } from 'node:fs'
import { resolve } from 'node:path'
import {
  SUPPLY_CHAIN_API,
  SUPPLY_CHAIN_DECIMAL_FIELDS,
  SUPPLY_CHAIN_QUERY_PERMISSIONS,
} from '@cgc-pms/frontend-contracts'
import {
  loadPurchaseOrders,
  loadReceipts,
  loadStockLedger,
  loadWarehouses,
} from '@/services/supply-chain'

const fetchMock = vi.fn<typeof fetch>()
const supplyChainPagesRoot = resolve('src/pages/supply-chain')
const supplyChainPages = readdirSync(supplyChainPagesRoot)
  .filter((name) => name.endsWith('.vue'))
  .map((name) => ({
    name,
    source: readFileSync(resolve(supplyChainPagesRoot, name), 'utf-8'),
  }))

function apiResponse<T>(data: T, status = 200): Response {
  return new Response(JSON.stringify({ code: status === 200 ? '0' : String(status), data }), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

beforeEach(() => {
  fetchMock.mockReset()
  fetchMock.mockImplementation(async () =>
    apiResponse({ records: [], total: 0, pageNo: 1, pageSize: 20 }),
  )
  vi.stubGlobal('fetch', fetchMock)
})

afterEach(() => vi.unstubAllGlobals())

describe('M5 supply chain contract baseline', () => {
  it('enforces the V2 design baseline on every supply-chain page', () => {
    expect(supplyChainPages).toHaveLength(4)

    for (const { name, source } of supplyChainPages) {
      const pageBody = source.split('<V2Dialog', 1)[0] ?? ''
      const template = source.slice(source.indexOf('<template>'), source.lastIndexOf('</template>'))

      expect(pageBody, `${name} inline detail surface`).not.toMatch(
        /<V2Card\b[^>]*(?:title="[^"]*(?:详情|追溯|链路)|class="[^"]*__detail)/,
      )
      expect(template, `${name} composite table heading`).not.toMatch(
        /<th\b[^>]*>[^<]*(?:\/|／)[^<]*<\/th>/,
      )

      for (const [, expression] of template.matchAll(/\{\{([\s\S]*?)\}\}/g)) {
        if (!/(?:^|\.)[A-Za-z]*[Ss]tatus\b/.test(expression ?? '')) continue
        expect(expression, `${name} raw status interpolation`).toMatch(/[?(]/)
      }
    }
  })

  it('freezes backend-authoritative query permissions and excludes manual stock movement', () => {
    expect(SUPPLY_CHAIN_QUERY_PERMISSIONS).toEqual({
      supplierSourcing: 'supplier:sourcing:query',
      purchaseRequest: 'purchase:request:list',
      purchaseOrder: 'purchase:order:query',
      receipt: 'receipt:query',
      warehouse: 'inventory:warehouse:list',
      stock: 'inventory:stock:list',
      transaction: 'inventory:transaction:list',
      requisition: 'requisition:query',
    })
    expect(Object.values(SUPPLY_CHAIN_API)).not.toContain('/inventory/stock/in')
    expect(Object.values(SUPPLY_CHAIN_API)).not.toContain('/inventory/stock/out')
  })

  it('sends only encoded GET canaries with abort signals', async () => {
    const signal = new AbortController().signal

    await loadWarehouses({ pageNo: 2, projectId: ' P/1 ', warehouseName: ' A&B ' }, signal)
    await loadStockLedger(
      { warehouseId: ' W/1 ', materialId: ' M&1 ', pageNo: 3, keyword: ' 钢 筋 ' },
      signal,
    )
    await loadPurchaseOrders({ projectId: ' P/1 ', orderStatus: ' APPROVED ' }, signal)

    expect(fetchMock.mock.calls.map(([url]) => String(url))).toEqual([
      '/api/inventory/warehouses?pageNo=2&projectId=P%2F1&warehouseName=A%26B',
      '/api/inventory/stock/ledger?warehouseId=W%2F1&materialId=M%261&pageNo=3&keyword=%E9%92%A2+%E7%AD%8B',
      '/api/purchase-orders?projectId=P%2F1&orderStatus=APPROVED',
    ])
    for (const [, options] of fetchMock.mock.calls) {
      expect(options).toMatchObject({ method: 'GET', body: undefined, signal })
    }
  })

  it('keeps empty pages and authoritative decimal strings unchanged', async () => {
    fetchMock
      .mockImplementationOnce(async () =>
        apiResponse({ records: [], total: 0, pageNo: 1, pageSize: 20 }),
      )
      .mockImplementationOnce(async () =>
        apiResponse({
          stock: {
            id: '1',
            warehouseId: '2',
            materialId: '3',
            availableQty: '9007199254740993.0001',
            inventoryValue: '9007199254740993.01',
            averageUnitCost: '0.000001',
            safetyStockQty: '0',
            replenishmentTargetQty: null,
          },
          txns: {
            records: [
              {
                id: '4',
                warehouseId: '2',
                materialId: '3',
                txnType: 'OUT',
                quantity: '0.0001',
                availableAfter: '-0.0001',
                unitCost: '0.000001',
                amount: '0',
              },
            ],
            total: 1,
            pageNo: 1,
            pageSize: 20,
          },
        }),
      )
      .mockImplementationOnce(async () =>
        apiResponse({
          records: [{ id: '5', tenantId: '1', projectId: '2', orderCode: 'PO-1' }],
          total: 1,
          pageNo: 1,
          pageSize: 20,
        }),
      )

    await expect(loadWarehouses()).resolves.toMatchObject({ records: [], total: 0 })
    await expect(loadStockLedger({ warehouseId: '2', materialId: '3' })).resolves.toMatchObject({
      stock: {
        availableQty: '9007199254740993.0001',
        inventoryValue: '9007199254740993.01',
        averageUnitCost: '0.000001',
        safetyStockQty: '0',
      },
      txns: {
        records: [
          {
            quantity: '0.0001',
            availableAfter: '-0.0001',
            unitCost: '0.000001',
            amount: '0',
          },
        ],
      },
    })
    await expect(loadPurchaseOrders()).resolves.toMatchObject({
      records: [{ id: '5', orderCode: 'PO-1' }],
    })
    expect(Object.values(SUPPLY_CHAIN_DECIMAL_FIELDS).flat()).toContain('remainingQuantity')
  })

  it.each([
    ['warehouse 403', 403, () => loadWarehouses()],
    ['stock ledger 404', 404, () => loadStockLedger({ warehouseId: '1', materialId: '2' })],
    ['receipt 422', 422, () => loadReceipts()],
    ['purchase order 500', 500, () => loadPurchaseOrders()],
  ])('preserves %s as a typed request failure', async (_name, status, request) => {
    fetchMock.mockImplementationOnce(async () => apiResponse(null, status))
    await expect(request()).rejects.toMatchObject({ code: String(status), status })
  })

  it('allows all warehouses but still requires a material before sending a request', async () => {
    await loadStockLedger({ warehouseId: ' ', materialId: '2' })
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/inventory/stock/ledger?materialId=2',
      expect.any(Object),
    )
    fetchMock.mockClear()
    expect(() => loadStockLedger({ warehouseId: '1', materialId: ' ' })).toThrow('物料ID不能为空')
    expect(fetchMock).not.toHaveBeenCalled()
  })

  it('keeps the contract and service UI-free, Legacy-free and read-only', () => {
    const sources = [
      '../packages/frontend-contracts/src/supply-chain.ts',
      ...['types', 'support', 'inventory', 'requisition', 'purchase', 'sourcing'].map(
        (module) => `src/services/supply-chain/${module}.ts`,
      ),
    ]
      .map((file) => readFileSync(resolve(file), 'utf-8'))
      .join('\n')

    expect(sources).not.toMatch(/from ["'](?:vue|pinia|vue-router)/)
    expect(sources).not.toContain('frontend-admin/')
    expect(sources).not.toMatch(/\b(?:parseFloat|parseInt)\s*\(/)
    expect(sources).not.toMatch(/\bNumber\s*\(/)
    expect(sources).not.toMatch(/method:\s*["'](?:POST|PUT|PATCH|DELETE)["']/)
  })

  it('matches backend permission and fail-closed stock movement sources', () => {
    const warehouseController = readFileSync(
      resolve(
        '../backend/src/main/java/com/cgcpms/inventory/controller/MatWarehouseController.java',
      ),
      'utf-8',
    )
    const stockController = readFileSync(
      resolve('../backend/src/main/java/com/cgcpms/inventory/controller/MatStockController.java'),
      'utf-8',
    )

    expect(warehouseController).toContain(
      "hasAnyAuthority('inventory:warehouse:list','inventory:stock:list','inventory:transaction:list')",
    )
    expect(warehouseController).not.toContain("hasAuthority('inventory:warehouse:query')")
    expect(stockController.match(/MANUAL_STOCK_MOVEMENT_DISABLED/g)).toHaveLength(2)
  })

  it('exposes receipt GET without introducing a second request layer', async () => {
    await loadReceipts({ projectId: ' P/1 ', qualityStatus: ' QUALIFIED ' })
    expect(fetchMock.mock.calls[0]?.[0]).toBe(
      '/api/receipts?projectId=P%2F1&qualityStatus=QUALIFIED',
    )
    expect(fetchMock.mock.calls[0]?.[1]).toMatchObject({ method: 'GET', body: undefined })
  })
})
