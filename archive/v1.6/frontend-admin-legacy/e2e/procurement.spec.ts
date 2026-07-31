import { test, expect, type BrowserContext, type Locator, type Page } from '@playwright/test'
import { createAuthenticatedPage } from './auth-session'

const SYSTEM_ERROR = '系统异常，请稍后重试'
const LOAD_PO_FAILED = '加载采购订单列表失败，请稍后重试'
const LOAD_PR_FAILED = '加载采购申请列表失败，请稍后重试'

let sharedContext: BrowserContext
let sharedPage: Page

async function expectNoProcurementError(page: Page) {
  await expect(page.getByText(SYSTEM_ERROR)).toHaveCount(0)
  await expect(page.getByText(LOAD_PR_FAILED)).toHaveCount(0)
  await expect(page.getByText(LOAD_PO_FAILED)).toHaveCount(0)
}

function firstCodeButton(page: Page, prefix: 'PR' | 'PO'): Locator {
  return page.getByRole('button', { name: new RegExp(`^${prefix}-`) }).first()
}

async function gotoPurchaseRequest(page: Page) {
  await page.goto('/inventory/purchase-request')
  await expect(page.locator('.purchase-request-page, .lg-page, .app-page').first()).toBeVisible({
    timeout: 10000,
  })
  await expect(page.locator('body')).toContainText('采购申请')
}

async function gotoPurchaseOrder(page: Page) {
  await page.goto('/purchase/order')
  await expect(page.locator('.purchase-order-page, .lg-page, .app-page').first()).toBeVisible({
    timeout: 10000,
  })
  await expect(page.locator('body')).toContainText('采购订单')
}

test.describe('Procurement E2E', () => {
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

  test('purchase request page is reachable with KPI and list evidence', async () => {
    await gotoPurchaseRequest(sharedPage)

    await expect(sharedPage.getByRole('button', { name: '查询' })).toBeVisible()
    await expect(sharedPage.getByRole('button', { name: '重置' })).toBeVisible()
    await expect(sharedPage.getByRole('button', { name: '新建申请' })).toBeVisible()
    await expect(sharedPage.getByText('申请总数')).toBeVisible()
    await expect(sharedPage.getByText('草稿申请')).toBeVisible()
    await expect(sharedPage.locator('.purchase-request-table-title')).toContainText('采购申请')
    await expect(sharedPage.locator('.vxe-table, .ant-table').first()).toBeVisible({
      timeout: 10000,
    })

    const codeButton = firstCodeButton(sharedPage, 'PR')
    await expect(codeButton).toBeVisible({ timeout: 10000 })
    await expect(codeButton).toContainText(/^PR-/)
    await expectNoProcurementError(sharedPage)
  })

  test('purchase request list keeps request-to-order chain evidence visible', async () => {
    await gotoPurchaseRequest(sharedPage)

    const tableHead = sharedPage.locator('thead').first()
    await expect(
      sharedPage.locator('.purchase-request-kpi-label').filter({ hasText: '已转PO' }),
    ).toBeVisible()
    await expect(tableHead.getByText('审批状态')).toBeVisible()
    await expect(tableHead.getByText('业务状态')).toBeVisible()
    await expect(tableHead.getByText('申请编号')).toBeVisible()
    await expect(tableHead.getByText('所属项目')).toBeVisible()
    await expect(tableHead.getByText('关联合同')).toBeVisible()
    await expectNoProcurementError(sharedPage)
  })

  test('purchase order page is reachable with KPI and order list evidence', async () => {
    await gotoPurchaseOrder(sharedPage)

    await expect(sharedPage.getByRole('button', { name: '查询' })).toBeVisible()
    await expect(sharedPage.getByRole('button', { name: '重置' })).toBeVisible()
    await expect(sharedPage.getByRole('button', { name: '新建订单' })).toBeVisible()
    await expect(sharedPage.getByText('采购订单数')).toBeVisible()
    await expect(sharedPage.getByText('已下单金额')).toBeVisible()
    await expect(sharedPage.locator('.purchase-order-page')).toContainText('未入库金额')
    await expect(sharedPage.locator('.vxe-table, .ant-table').first()).toBeVisible({
      timeout: 10000,
    })

    const codeButton = firstCodeButton(sharedPage, 'PO')
    await expect(codeButton).toBeVisible({ timeout: 10000 })
    await expect(codeButton).toContainText(/^PO-/)
    await expectNoProcurementError(sharedPage)
  })

  test('purchase order list exposes type and status columns for fulfillment follow-up', async () => {
    await gotoPurchaseOrder(sharedPage)

    const tableHead = sharedPage.locator('thead').first()
    await expect(tableHead.getByText('订单编号')).toBeVisible()
    await expect(tableHead.getByText('订单类型')).toBeVisible()
    await expect(tableHead.getByText('项目名称')).toBeVisible()
    await expect(tableHead.getByText('合同名称')).toBeVisible()
    await expect(tableHead.getByText('供应商')).toBeVisible()
    await expect(tableHead.getByText('订单状态')).toBeVisible()
    await expectNoProcurementError(sharedPage)
  })
})
