import { test, expect, type BrowserContext, type Page } from '@playwright/test'
import { createAuthenticatedPage } from './auth-session'

const SYSTEM_ERROR = '系统异常，请稍后重试'
const LOAD_FAILED = '加载变更签证列表失败'

let sharedContext: BrowserContext
let sharedPage: Page

async function gotoVariationOrder(page: Page) {
  await page.goto('/variation/order')
  await expect(page.locator('.variation-order-page, .lg-page, .app-page').first()).toBeVisible({
    timeout: 10000,
  })
  await expect(page.locator('body')).toContainText('变更签证')
}

async function expectNoVariationError(page: Page) {
  await expect(page.getByText(SYSTEM_ERROR)).toHaveCount(0)
  await expect(page.getByText(LOAD_FAILED)).toHaveCount(0)
}

test.describe('Variation E2E', () => {
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

  test('variation order page is reachable with KPI and table evidence', async () => {
    await gotoVariationOrder(sharedPage)

    await expect(sharedPage.getByRole('button', { name: '查询' })).toBeVisible()
    await expect(sharedPage.getByRole('button', { name: '重置' })).toBeVisible()
    await expect(sharedPage.getByRole('button', { name: '新建签证' })).toBeVisible()
    await expect(sharedPage.getByText('签证总数')).toBeVisible()
    await expect(sharedPage.locator('.vo-kpi-label').filter({ hasText: '已通过' })).toBeVisible()
    await expect(sharedPage.locator('.vo-kpi-label').filter({ hasText: '成本方向' })).toBeVisible()
    await expect(sharedPage.locator('.vxe-table, .ant-table').first()).toBeVisible({
      timeout: 10000,
    })
    await expectNoVariationError(sharedPage)
  })

  test('variation list shows current chain columns and seeded record evidence', async () => {
    await gotoVariationOrder(sharedPage)

    const tableHead = sharedPage.locator('thead').first()
    await expect(tableHead.getByText('变更编号')).toBeVisible()
    await expect(tableHead.getByText('变更名称')).toBeVisible()
    await expect(tableHead.getByText('变更类型')).toBeVisible()
    await expect(tableHead.getByText('方向')).toBeVisible()
    await expect(tableHead.getByText('项目名称')).toBeVisible()
    await expect(tableHead.getByText('确认金额')).toBeVisible()

    const codeButton = sharedPage.getByRole('button', { name: /^VO-/ }).first()
    await expect(codeButton).toBeVisible({ timeout: 10000 })
    await expect(codeButton).toContainText(/^VO-/)
    await expectNoVariationError(sharedPage)
  })

  test('variation record detail is reachable from list row', async () => {
    await gotoVariationOrder(sharedPage)

    const codeButton = sharedPage.getByRole('button', { name: /^VO-/ }).first()
    await expect(codeButton).toBeVisible({ timeout: 10000 })
    await codeButton.click()

    const modal = sharedPage.locator('.ant-modal').filter({ hasText: '查看变更签证' }).first()
    await expect(modal).toBeVisible({ timeout: 10000 })
    await expect(modal).toContainText('项目')
    await expect(modal).toContainText('合同')
    await expect(modal).toContainText('合作方')
    await expect(modal).toContainText('变更类型')
    await expect(modal).toContainText('变更明细')
    await expect(modal).toContainText('成本')
    await expectNoVariationError(sharedPage)
  })
})
