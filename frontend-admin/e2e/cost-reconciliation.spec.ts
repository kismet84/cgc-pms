import { test, expect, type BrowserContext, type Page } from '@playwright/test'
import { createAuthenticatedPage } from './auth-session'

let sharedContext: BrowserContext
let sharedPage: Page

test.describe('Cost reconciliation smoke', () => {
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

  test('cost summary page can select a project and render reconciliation KPIs', async () => {
    await sharedPage.goto('/cost/summary')
    await expect(sharedPage.getByText('项目成本明细核对').first()).toBeVisible({ timeout: 10000 })
    await expect(sharedPage.getByText('请选择项目开始核对')).toBeVisible({ timeout: 5000 })

    const projectSelect = sharedPage.locator('.cost-summary-project-select')
    await expect(projectSelect).toBeVisible({ timeout: 5000 })
    await projectSelect.click()

    const dropdown = sharedPage.locator('.ant-select-dropdown:not(.ant-select-dropdown-hidden)').last()
    await expect(dropdown).toBeVisible({ timeout: 5000 })
    const options = dropdown.locator('.ant-select-item-option:not(.ant-select-item-option-disabled)')
    const count = await options.count()
    expect(count).toBeGreaterThan(0)

    await options.first().click()

    await expect(sharedPage.locator('.cost-reconcile-overview')).toBeVisible({ timeout: 10000 })
    await expect(sharedPage.locator('.cost-reconcile-kpis .lg-kpi-card')).toHaveCount(5)
    await expect(sharedPage.getByText('目标成本').first()).toBeVisible({ timeout: 5000 })
    await expect(sharedPage.getByText('合同锁定成本').first()).toBeVisible({ timeout: 5000 })
    await expect(sharedPage.getByText('实际成本').first()).toBeVisible({ timeout: 5000 })
    await expect(sharedPage.getByText('动态成本').first()).toBeVisible({ timeout: 5000 })
    await expect(sharedPage.getByText('成本偏差').first()).toBeVisible({ timeout: 5000 })

    const sourceCards = sharedPage.locator('.cost-source-card')
    await expect(sourceCards).toHaveCount(4)
    await expect(sharedPage.getByText('已付款').first()).toBeVisible({ timeout: 5000 })
    await expect(sharedPage.locator('.cost-toolbar-meta')).toContainText('个科目')
    await expect(sharedPage.locator('.cost-summary-table .vxe-header--row .vxe-cell').first()).toBeVisible({
      timeout: 5000,
    })
    await expect(sharedPage.getByText('核对科目').first()).toBeVisible({ timeout: 5000 })
    await expect(sharedPage.getByText('总偏差率').first()).toBeVisible({ timeout: 5000 })

    await sharedPage.screenshot({ path: 'e2e/screenshots/cost-reconciliation-summary.png', fullPage: true })
  })
})
