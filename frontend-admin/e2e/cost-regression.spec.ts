import { test, expect, type BrowserContext, type Page } from '@playwright/test'
import { createAuthenticatedPage } from './auth-session'

let sharedContext: BrowserContext
let sharedPage: Page

async function waitForCostDashboard(page: Page) {
  await page.goto('/dashboard')
  await expect(page.locator('.dashboard')).toBeVisible({ timeout: 10000 })
  await expect(page.locator('.cost-reference-shell')).toBeVisible({ timeout: 10000 })
}

test.describe('Cost regression smoke', () => {
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

  test('dashboard cost view supports month switching without breaking KPI/chart structure', async () => {
    await waitForCostDashboard(sharedPage)

    const costKpis = sharedPage.locator('.cost-reference-kpi')
    await expect(costKpis.first()).toBeVisible({ timeout: 5000 })
    await expect(sharedPage.getByText('商务成本执行情况')).toBeVisible({ timeout: 5000 })
    await expect(sharedPage.getByText('成本科目排名')).toBeVisible({ timeout: 5000 })

    const monthSelect = sharedPage.locator('.dashboard-actions .ant-select').nth(1)
    await expect(monthSelect).toBeVisible({ timeout: 5000 })

    const currentLabel = (
      await monthSelect.locator('.ant-select-selection-item').textContent()
    )?.trim()
    expect(currentLabel?.length ?? 0).toBeGreaterThan(0)

    await monthSelect.click()
    const dropdown = sharedPage
      .locator('.ant-select-dropdown:not(.ant-select-dropdown-hidden)')
      .last()
    await expect(dropdown).toBeVisible({ timeout: 5000 })

    const options = dropdown.locator(
      '.ant-select-item-option:not(.ant-select-item-option-disabled)',
    )
    const optionTexts = (await options.allTextContents()).map((text) => text.trim()).filter(Boolean)
    expect(optionTexts.length).toBeGreaterThan(0)
    expect(optionTexts).toContain('全部')

    const singleMonthLabel = optionTexts.find((text) => text !== '全部')
    if (!singleMonthLabel) {
      throw new Error('需要确认：当前月份筛选未提供单月选项，无法验证“全部/单月”切换证据')
    }

    await options.filter({ hasText: singleMonthLabel }).first().click()
    await expect(monthSelect.locator('.ant-select-selection-item')).toHaveText(singleMonthLabel, {
      timeout: 5000,
    })
    await expect(sharedPage.locator('.cost-reference-chart canvas').first()).toBeVisible({
      timeout: 8000,
    })

    await monthSelect.click()
    const resetDropdown = sharedPage
      .locator('.ant-select-dropdown:not(.ant-select-dropdown-hidden)')
      .last()
    await expect(resetDropdown).toBeVisible({ timeout: 5000 })
    await resetDropdown
      .locator('.ant-select-item-option')
      .filter({ hasText: '全部' })
      .first()
      .click()
    await expect(monthSelect.locator('.ant-select-selection-item')).toHaveText('全部', {
      timeout: 5000,
    })
    await expect(costKpis.first()).toBeVisible({ timeout: 5000 })

    await sharedPage.screenshot({
      path: 'e2e/screenshots/cost-regression-dashboard-month-switch.png',
      fullPage: true,
    })
  })

  test('contract ledger and payment list expose amount-bearing rows for cost comparison context', async () => {
    await sharedPage.goto('/contract/ledger')
    await expect(sharedPage.locator('.cl-redesign-page')).toBeVisible({ timeout: 10000 })
    await expect(sharedPage.getByText('合同总金额(含税)')).toBeVisible({ timeout: 5000 })
    await expect(sharedPage.locator('.cl-table-count')).toContainText('共 ')
    await expect(sharedPage.locator('.vxe-header--row .vxe-cell').first()).toBeVisible({
      timeout: 5000,
    })
    await expect(sharedPage.getByText('合同列表').first()).toBeVisible({ timeout: 5000 })

    await sharedPage.goto('/payment/application')
    await expect(sharedPage.locator('.payment-page')).toBeVisible({ timeout: 10000 })
    await expect(sharedPage.locator('.payment-kpi-summary')).toBeVisible({ timeout: 5000 })
    await expect(sharedPage.getByText('申请金额', { exact: true }).first()).toBeVisible({
      timeout: 5000,
    })
    await expect(sharedPage.locator('.vxe-header--row .vxe-cell').first()).toBeVisible({
      timeout: 5000,
    })
    await expect(sharedPage.getByText('付款申请').first()).toBeVisible({ timeout: 5000 })
    await expect(sharedPage.locator('.payment-kpi-label').first()).toBeVisible({ timeout: 5000 })

    await sharedPage.screenshot({
      path: 'e2e/screenshots/cost-regression-payment-context.png',
      fullPage: true,
    })
  })
})
