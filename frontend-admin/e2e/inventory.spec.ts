import {
  test,
  expect,
  type APIRequestContext,
  type BrowserContext,
  type Page,
  type Playwright,
} from '@playwright/test'
import { createAuthenticatedPage } from './auth-session'

const SYSTEM_ERROR = '系统异常，请稍后重试'
const LEDGER_FAILED = '加载库存台账失败，请稍后重试'
const API_BASE_URL = 'http://localhost:8080'

let sharedContext: BrowserContext
let sharedPage: Page
let apiContext: APIRequestContext
let seededLedgerSample: {
  warehouseName: string
  materialName: string
  quantity: string
} | null = null

async function expectNoInventoryError(page: Page) {
  await expect(page.getByText(SYSTEM_ERROR)).toHaveCount(0)
  await expect(page.getByText(LEDGER_FAILED)).toHaveCount(0)
}

async function apiGet<T>(url: string, params?: Record<string, string | number>) {
  const response = await apiContext.get(url, params ? { params } : undefined)
  expect(response.ok(), `API ${response.url()} should return HTTP 2xx`).toBeTruthy()
  const body = (await response.json()) as { code?: string; message?: string; data?: T }
  expect(body.code, `API ${response.url()} business code`).toMatch(/^(0|00000)$/)
  return body.data as T
}

async function apiPost<T>(url: string, data: Record<string, unknown>) {
  const response = await apiContext.post(url, { data })
  expect(response.ok(), `API ${response.url()} should return HTTP 2xx`).toBeTruthy()
  const body = (await response.json()) as { code?: string; message?: string; data?: T }
  expect(body.code, `API ${response.url()} business code`).toMatch(/^(0|00000)$/)
  return body.data as T
}

function runId(prefix: string) {
  return `${prefix}-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`
}

async function selectOptionByText(page: Page, select: ReturnType<Page['locator']>, text: string) {
  await select.click()
  const dropdown = page.locator('.ant-select-dropdown:not(.ant-select-dropdown-hidden)').last()
  await expect(dropdown).toBeVisible({ timeout: 10000 })
  const options = dropdown.locator('.ant-select-item-option:not(.ant-select-item-option-disabled)')
  const option = options.filter({ hasText: text }).first()
  await expect(option).toBeVisible({ timeout: 10000 })
  await option.click()
}

async function seedInventoryLedgerSample(playwright: Playwright) {
  apiContext = await playwright.request.newContext({ baseURL: API_BASE_URL })
  const loginResponse = await apiContext.post('/api/auth/login', {
    data: { username: 'admin', password: 'admin123' },
  })
  expect(loginResponse.ok(), '库存样本准备登录应返回 HTTP 2xx').toBeTruthy()
  const loginBody = (await loginResponse.json()) as { code?: string; message?: string }
  expect(loginBody.code, '库存样本准备登录业务码').toMatch(/^(0|00000)$/)

  const sampleId = runId('INVLEDGER')
  const warehouseName = `E2E库存回读仓库-${sampleId}`
  const materialName = `E2E库存回读物料-${sampleId}`
  const quantity = '7'

  const projectId = String(
    await apiPost('/api/projects', {
      projectCode: runId('PRJ'),
      projectName: `E2E库存回读项目-${sampleId}`,
      projectType: 'BUILDING',
      contractAmount: '5000000',
      plannedStartDate: '2025-01-01',
      plannedEndDate: '2026-12-31',
      status: 'ACTIVE',
    }),
  )
  const warehouseId = String(
    await apiPost('/api/inventory/warehouses', {
      warehouseCode: runId('WH'),
      projectId,
      warehouseName,
      status: 'ENABLE',
    }),
  )
  const materialId = String(
    await apiPost('/api/materials', {
      materialCode: runId('MAT'),
      materialName,
      unit: 'm3',
      materialType: 'RAW',
    }),
  )

  await apiPost('/api/inventory/stock/in', {
    warehouseId,
    materialId,
    quantity,
    sourceType: 'INIT',
  })

  const ledger = await apiGet<{
    stock: { availableQty: string | number; warehouseName?: string; materialName?: string } | null
    txns?: { records?: Array<{ txnType: string; quantity: string | number; sourceType?: string }> }
  }>('/api/inventory/stock/ledger', {
    warehouseId,
    materialId,
    pageNo: 1,
    pageSize: 20,
  })

  expect(Number(ledger.stock?.availableQty)).toBe(Number(quantity))
  expect(
    ledger.txns?.records?.some(
      (item) => item.txnType === 'IN' && Number(item.quantity) === Number(quantity),
    ),
  ).toBeTruthy()

  seededLedgerSample = { warehouseName, materialName, quantity }
}

async function gotoInventoryTransaction(page: Page) {
  await page.goto('/inventory/transaction')
  await expect(page.locator('.lg-page, .app-page').first()).toBeVisible({ timeout: 10000 })
  await expect(page.locator('body')).toContainText('库存交易')
  await expect(page.locator('body')).toContainText('入库操作')
  await expect(page.locator('body')).toContainText('出库操作')
}

async function gotoStockLedger(page: Page) {
  await page.goto('/inventory/stock')
  await expect(page.locator('.stock-page, .lg-page, .app-page').first()).toBeVisible({
    timeout: 10000,
  })
  await expect(page.locator('body')).toContainText('库存台账')
  await expect(page.locator('.stock-search-bar')).toBeVisible({ timeout: 10000 })
}

test.describe('Inventory E2E', () => {
  test.describe.configure({ mode: 'serial' })

  test.beforeAll(async ({ browser }) => {
    const auth = await createAuthenticatedPage(browser)
    sharedContext = auth.context
    sharedPage = auth.page
  })

  test.beforeAll(async ({ playwright }) => {
    await seedInventoryLedgerSample(playwright)
  })

  test.afterAll(async () => {
    await sharedPage?.close()
    await sharedContext?.close()
    await apiContext?.dispose()
  })

  test('inventory transaction page is reachable and shows inbound and outbound forms', async () => {
    await gotoInventoryTransaction(sharedPage)

    await expect(sharedPage.locator('.ant-tabs-tab').filter({ hasText: '入库' })).toBeVisible()
    await expect(sharedPage.locator('.ant-tabs-tab').filter({ hasText: '出库' })).toBeVisible()
    await expect(
      sharedPage.locator('.ant-tabs-tab-active').filter({ hasText: '入库' }),
    ).toBeVisible()
    await expect(sharedPage.getByText('仓库').first()).toBeVisible()
    await expect(sharedPage.getByText('物料').first()).toBeVisible()
    await expect(sharedPage.getByText('入库数量')).toBeVisible()
    await expect(sharedPage.getByRole('button', { name: '确认入库' })).toBeVisible()
  })

  test('inventory transaction page can switch to outbound tab and expose outbound fields', async () => {
    await gotoInventoryTransaction(sharedPage)

    await sharedPage.locator('.ant-tabs-tab').filter({ hasText: '出库' }).click()
    await expect(
      sharedPage.locator('.ant-tabs-tab-active').filter({ hasText: '出库' }),
    ).toBeVisible()
    await expect(sharedPage.getByText('出库数量')).toBeVisible()
    await expect(sharedPage.getByRole('button', { name: '确认出库' })).toBeVisible()
  })

  test('stock ledger page shows search, KPI, and transaction evidence', async () => {
    await gotoStockLedger(sharedPage)

    await expect(sharedPage.getByRole('button', { name: '查询' })).toBeVisible()
    await expect(sharedPage.getByRole('button', { name: '重置' })).toBeVisible()
    await expect(sharedPage.locator('.stock-table-title')).toContainText('出入库流水')
    await expect(sharedPage.locator('.stock-table-count')).toContainText('共')
    await expect(
      sharedPage.locator('.stock-kpi-label').filter({ hasText: '仓库数量' }),
    ).toBeVisible()
    await expect(
      sharedPage.locator('.stock-kpi-label').filter({ hasText: '物料种类' }),
    ).toBeVisible()
    await expect(
      sharedPage.locator('.stock-kpi-label').filter({ hasText: '低库存物料' }),
    ).toBeVisible()
    await expect(sharedPage.locator('.vxe-table').first()).toBeVisible({ timeout: 10000 })
    await expectNoInventoryError(sharedPage)
  })

  test('stock ledger page exposes table headers and analysis panel', async () => {
    await gotoStockLedger(sharedPage)

    const tableHead = sharedPage.locator('thead').first()
    await expect(tableHead.getByText('流水号')).toBeVisible()
    await expect(tableHead.getByText(/^类型$/)).toBeVisible()
    await expect(tableHead.getByText('变动量')).toBeVisible()
    await expect(tableHead.getByText('变动后余量')).toBeVisible()
    await expect(sharedPage.getByText('库存分析')).toBeVisible()
    await expect(sharedPage.getByText('低库存预警')).toBeVisible()
    await expect(sharedPage.getByText('出入库统计')).toBeVisible()
    await expectNoInventoryError(sharedPage)
  })

  test('stock ledger reads back seeded stock-in transaction', async () => {
    expect(seededLedgerSample, '需要确认：库存回读样本未准备成功').toBeTruthy()
    await gotoStockLedger(sharedPage)

    await selectOptionByText(
      sharedPage,
      sharedPage.locator('.stock-search-bar .ant-select').nth(0),
      seededLedgerSample!.warehouseName,
    )
    await selectOptionByText(
      sharedPage,
      sharedPage.locator('.stock-search-bar .ant-select').nth(1),
      seededLedgerSample!.materialName,
    )
    await sharedPage.getByRole('button', { name: '查询' }).click()

    await expect(
      sharedPage
        .locator('.stock-balance-card, .lg-panel')
        .filter({
          hasText: seededLedgerSample!.warehouseName,
        })
        .first(),
    ).toBeVisible({ timeout: 10000 })
    await expect(sharedPage.locator('body')).toContainText(seededLedgerSample!.materialName)
    await expect(sharedPage.locator('body')).toContainText('7.0000')

    const firstRow = sharedPage.locator('.vxe-body--row').first()
    await expect(firstRow).toContainText('入库')
    await expect(firstRow).toContainText('期初导入')
    await expect(firstRow).toContainText('7.0000')
    await expectNoInventoryError(sharedPage)
  })
})
