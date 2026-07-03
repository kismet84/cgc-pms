import { test, expect, type BrowserContext, type Page } from '@playwright/test'
import { createAuthenticatedPage } from './auth-session'

const SYSTEM_ERROR = '系统异常，请稍后重试'
const LEDGER_FAILED = '加载库存台账失败，请稍后重试'

let sharedContext: BrowserContext
let sharedPage: Page

async function expectNoInventoryError(page: Page) {
  await expect(page.getByText(SYSTEM_ERROR)).toHaveCount(0)
  await expect(page.getByText(LEDGER_FAILED)).toHaveCount(0)
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

  test.afterAll(async () => {
    await sharedPage?.close()
    await sharedContext?.close()
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
    await expect(sharedPage.locator('.stock-kpi-label').filter({ hasText: '仓库数量' })).toBeVisible()
    await expect(sharedPage.locator('.stock-kpi-label').filter({ hasText: '物料种类' })).toBeVisible()
    await expect(sharedPage.locator('.stock-kpi-label').filter({ hasText: '低库存物料' })).toBeVisible()
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
})
