import AxeBuilder from '@axe-core/playwright'
import { expect, test, type Page, type Route } from '@playwright/test'
import { captureRuntimeErrors } from './runtime-errors'

const permissions = [
  'inventory:warehouse:list',
  'inventory:warehouse:add',
  'inventory:warehouse:edit',
  'inventory:warehouse:delete',
  'inventory:stock:list',
  'inventory:stock:edit',
  'inventory:transaction:list',
  'inventory:transaction:add',
]
const warehouses = [
  {
    id: 'W1',
    tenantId: 'T1',
    projectId: 'P1',
    projectName: '示范项目',
    warehouseCode: 'WH-001',
    warehouseName: `主材仓${'超长'.repeat(20)}`,
    status: 'ENABLE',
    updatedAt: '2026-07-24 22:00:00',
  },
]
const stock = {
  id: 'S1',
  projectId: 'P1',
  projectName: '示范项目',
  warehouseId: 'W1',
  warehouseName: '主材仓',
  materialId: 'M1',
  materialCode: 'MAT-001',
  materialName: `钢筋${'超长'.repeat(20)}`,
  unit: '吨',
  availableQty: '80.0000',
  inventoryValue: '260.00',
  averageUnitCost: '3.250000',
  safetyStockQty: '10.0000',
  replenishmentTargetQty: '100.0000',
  replenishmentLeadDays: 7,
}
const txns = [
  {
    id: 'T1',
    warehouseId: 'W1',
    materialId: 'M1',
    txnType: 'IN',
    quantity: '10.0000',
    availableAfter: '80.0000',
    unitCost: '3.250000',
    amount: '32.50',
    sourceType: 'MAT_RECEIPT',
    sourceId: 'RC1',
    createdTime: '2026-07-24 20:00:00',
  },
]

async function fulfill(route: Route, data: unknown, status = 200, code = '0') {
  await route.fulfill({
    status,
    contentType: 'application/json',
    body: JSON.stringify({ code, message: status === 200 ? 'success' : '库存事实已变化', data }),
  })
}

async function install(page: Page, granted = permissions, rejectTransfer = false) {
  const writes: Array<{ path: string; body: unknown }> = []
  await page.route('**/api/auth/userinfo', (route) =>
    fulfill(route, { userId: '1', username: 'keeper', roles: ['USER'], permissions: granted }),
  )
  await page.route('**/api/auth/refresh', (route) => fulfill(route, null, 401))
  await page.route('**/api/project-context/options', (route) =>
    fulfill(route, [{ id: 'P1', projectName: '示范项目', status: 'ACTIVE' }]),
  )
  await page.route('**/api/materials**', (route) =>
    fulfill(route, {
      records: [
        {
          id: 'M1',
          materialCode: 'MAT-001',
          materialName: '钢筋',
          specification: 'HRB400',
          status: 'ENABLE',
        },
      ],
      total: 1,
      pageNo: 1,
      pageSize: 100,
    }),
  )
  await page.route('**/api/inventory/**', async (route) => {
    const request = route.request()
    const url = new URL(request.url())
    const path = url.pathname.replace('/api', '')
    const method = request.method()
    const body = request.postDataJSON() as Record<string, unknown> | undefined
    if (method !== 'GET') writes.push({ path: `${method} ${path}${url.search}`, body })

    if (path === '/inventory/warehouses' && method === 'GET')
      return fulfill(route, {
        records: warehouses,
        total: warehouses.length,
        pageNo: 1,
        pageSize: 20,
      })
    if (path === '/inventory/warehouses' && method === 'POST') {
      warehouses.push({
        ...warehouses[0]!,
        id: 'W2',
        warehouseCode: 'WH-002',
        warehouseName: String(body?.warehouseName),
      })
      return fulfill(route, 'W2')
    }
    if (path.startsWith('/inventory/warehouses/')) {
      const id = path.split('/').at(-1)
      if (method === 'GET')
        return fulfill(
          route,
          warehouses.find((item) => item.id === id),
        )
      if (method === 'DELETE')
        warehouses.splice(
          warehouses.findIndex((item) => item.id === id),
          1,
        )
      return fulfill(route, null)
    }
    if (path === '/inventory/stock/kpi')
      return fulfill(route, {
        warehouseCount: 1,
        lowStockCount: 0,
        txnInCount: 1,
        txnOutCount: 0,
        materialTypeCount: 1,
      })
    if (path === '/inventory/stock' && method === 'GET')
      return fulfill(route, { records: [stock], total: 1, pageNo: 1, pageSize: 10 })
    if (path === '/inventory/stock/ledger')
      return fulfill(route, { stock, txns: { records: txns, total: 1, pageNo: 1, pageSize: 10 } })
    if (path.endsWith('/transfer-candidates'))
      return fulfill(route, [
        {
          stockId: 'S2',
          warehouseId: 'W2',
          warehouseName: '备用仓',
          availableQty: '40.0000',
          safetyStockQty: '5.0000',
          transferableQty: '35.0000',
        },
      ])
    if (path.endsWith('/incoming-supplies'))
      return fulfill(route, [
        {
          orderId: 'PO1',
          orderCode: 'PO-001',
          deliveryDate: '2026-07-30',
          remainingQty: '20.0000',
        },
      ])
    if (path.endsWith('/consumption-baseline'))
      return fulfill(route, {
        window30Start: '2026-06-25',
        window90Start: '2026-04-26',
        cutoffAt: '2026-07-24T22:00:00',
        grossIssued30: '5.0000',
        returned30: '1.0000',
        netIssued30: '4.0000',
        grossIssued90: '12.0000',
        returned90: '2.0000',
        netIssued90: '10.0000',
      })
    if (path === '/inventory/stock/transfers' && rejectTransfer)
      return fulfill(route, null, 409, 'STOCK_TRANSFER_CONFLICT')
    if (path === '/inventory/stock/transfers')
      return fulfill(route, { ...body, id: 'TR1', status: 'COMPLETED' })
    if (path.endsWith('/replenishment-settings')) {
      Object.assign(stock, body)
      return fulfill(route, stock)
    }
    return fulfill(route, null)
  })
  return writes
}

async function openStockDrawer(page: Page) {
  await expect(page.getByText('MAT-001', { exact: true })).toBeVisible()
  await page.getByRole('button', { name: 'MAT-001', exact: true }).click()
  await expect(page.getByRole('dialog', { name: '库存明细与流水' })).toBeVisible()
}

test.describe('M5 inventory workspace V2', () => {
  test.beforeEach(() => warehouses.splice(1))

  for (const path of ['/inventory/warehouse', '/inventory/stock', '/inventory/transaction']) {
    test(`fails closed on ${path} before inventory traffic`, async ({ page }) => {
      const traffic: string[] = []
      page.on(
        'request',
        (request) => request.url().includes('/api/inventory/') && traffic.push(request.url()),
      )
      await install(page, [])
      await page.goto(`${path}`)
      await expect(page).toHaveURL(/\/forbidden\?from=/)
      expect(traffic).toEqual([])
    })
  }

  test('redirects inventory root and renders warehouse scope', async ({ page }) => {
    await install(page)
    await page.goto('/inventory?projectId=P1')
    await expect(page).toHaveURL(/\/inventory\/warehouse\?projectId=P1/)
    await expect(page.getByText('WH-001', { exact: true })).toBeVisible()
  })

  test('renders three viewports without page overflow and passes axe', async ({ page }) => {
    await install(page)
    const errors = captureRuntimeErrors(page)
    for (const viewport of [
      { width: 1440, height: 900 },
      { width: 1024, height: 768 },
      { width: 390, height: 844 },
    ]) {
      await page.setViewportSize(viewport)
      await page.goto('/inventory/warehouse?projectId=P1')
      await expect(page.getByText('WH-001', { exact: true })).toBeVisible()
      expect(
        await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth),
      ).toBe(true)
      if (viewport.width === 390) {
        const header = page.locator('.v2-card--page-heading > .v2-card__header')
        await expect(header).toHaveCSS('flex-direction', 'column')
        const [headerBox, actionsBox] = await Promise.all([
          header.boundingBox(),
          header.locator(':scope > .v2-card__actions').boundingBox(),
        ])
        expect(actionsBox?.width).toBeGreaterThan((headerBox?.width ?? 0) * 0.9)
      }
    }
    const axe = await new AxeBuilder({ page }).include('.inventory-workspace-page').analyze()
    expect(
      axe.violations.filter((item) => ['serious', 'critical'].includes(item.impact ?? '')),
    ).toEqual([])
    expect(errors).toEqual([])
  })

  test('warehouse write actions use exact permissions and re-read', async ({ page }) => {
    await install(page, ['inventory:warehouse:list'])
    await page.goto('/inventory/warehouse?projectId=P1')
    await expect(page.getByRole('button', { name: '新建仓库' })).toHaveCount(0)
    const writes = await install(page, ['inventory:warehouse:list', 'inventory:warehouse:add'])
    await page.reload()
    await page.getByRole('button', { name: '新建仓库' }).click()
    await expect(page.getByLabel('仓库编码')).toBeDisabled()
    await page.getByLabel('仓库名称').fill('辅材仓')
    await page.getByRole('button', { name: '保存', exact: true }).click()
    await expect(page.getByText('WH-002', { exact: true })).toBeVisible()
    expect(
      writes.find((item) => item.path === 'POST /inventory/warehouses')?.body,
    ).not.toHaveProperty('warehouseCode')
  })

  test('reads quantity, value, incoming and historical facts', async ({ page }) => {
    await install(page)
    await page.goto('/inventory/stock?projectId=P1')
    await openStockDrawer(page)
    const drawer = page.getByRole('dialog', { name: '库存明细与流水' })
    await expect(drawer.getByText('¥260.00', { exact: true })).toBeVisible()
    await expect(page.getByText(/PO-001 · 20.0000/)).toBeVisible()
    await expect(page.getByText(/30日 4.0000；90日 10.0000/)).toBeVisible()
  })

  test('uses ten-row server paging and shows project in all-project stock view', async ({
    page,
  }) => {
    const stockRequests: string[] = []
    page.on('request', (request) => {
      if (new URL(request.url()).pathname === '/api/inventory/stock')
        stockRequests.push(request.url())
    })
    await install(page)
    await page.goto('/inventory/stock')
    await expect(
      page.getByRole('region', { name: '库存台账列表' }).getByText('示范项目', { exact: true }),
    ).toBeVisible()
    await expect.poll(() => stockRequests.some((url) => url.includes('pageSize=10'))).toBe(true)
  })

  test('stock-list-only role skips material dictionary traffic', async ({ page }) => {
    const materialRequests: string[] = []
    page.on('request', (request) => {
      if (new URL(request.url()).pathname === '/api/materials') materialRequests.push(request.url())
    })
    await install(page, ['inventory:stock:list'])
    await page.goto('/inventory/stock?projectId=P1')
    await expect(page.getByRole('button', { name: 'MAT-001', exact: true })).toBeVisible()
    expect(materialRequests).toEqual([])
  })

  test('updates threshold with decimal strings then re-reads', async ({ page }) => {
    const writes = await install(page)
    await page.goto('/inventory/stock?projectId=P1')
    await openStockDrawer(page)
    await page.getByRole('button', { name: '维护阈值' }).click()
    await page.getByLabel('安全库存').fill('12.5000')
    await page.getByRole('button', { name: '提交并读取' }).click()
    expect(
      writes.find((item) => item.path.includes('/replenishment-settings'))?.body,
    ).toMatchObject({ safetyStockQty: '12.5000' })
  })

  test('submits one idempotent transfer and propagates conflict without retry', async ({
    page,
  }) => {
    const writes = await install(page, permissions, true)
    await page.goto('/inventory/stock?projectId=P1')
    await openStockDrawer(page)
    await page.getByRole('button', { name: '库存调拨' }).click()
    const dialog = page.getByRole('dialog', { name: '库存调拨' })
    await expect(dialog).toBeVisible()
    await page.waitForTimeout(300)
    await dialog.getByRole('button', { name: '来源库存：请选择' }).click()
    await page.getByRole('option', { name: /备用仓/ }).click()
    await page.getByLabel('调拨数量').fill('1.2500')
    await page.getByLabel('调拨原因').fill('平衡库存')
    await page.getByRole('button', { name: '提交并读取' }).dblclick()
    await expect(page.getByText('库存事实已变化', { exact: true }).first()).toBeVisible()
    const transfers = writes.filter((item) =>
      item.path.startsWith('POST /inventory/stock/transfers'),
    )
    expect(transfers).toHaveLength(1)
    expect((transfers[0]?.body as { idempotencyKey: string }).idempotencyKey).toBeTruthy()
  })

  test('transaction route is read-only and keeps manual movement absent', async ({ page }) => {
    const stockOnlyRequests: string[] = []
    page.on('request', (request) => {
      if (
        /\/api\/(?:materials|inventory\/stock\/kpi|inventory\/stock\/[^/]+\/(?:transfer-candidates|incoming-supplies|consumption-baseline))/.test(
          request.url(),
        )
      )
        stockOnlyRequests.push(request.url())
    })
    await install(page, ['inventory:transaction:list'])
    await page.goto('/inventory/transaction?projectId=P1')
    await expect(page).toHaveURL(/\/inventory\/stock\?projectId=P1#transactions/)
    await openStockDrawer(page)
    await expect(page.getByText('入库', { exact: true })).toBeVisible()
    await expect(page.getByRole('button', { name: /入库|出库/ })).toHaveCount(0)
    expect(stockOnlyRequests).toEqual([])
  })
})
