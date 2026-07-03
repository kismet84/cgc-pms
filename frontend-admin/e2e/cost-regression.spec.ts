import { test, expect, type Page } from '@playwright/test'

/**
 * Cost regression smoke for Task 7.
 *
 * Scope:
 * - Dashboard cost view renders KPI cards and month selector.
 * - Month switch 全部 → 单月 → 全部 stays stable.
 * - Contract ledger and payment list expose amount-related entries for the same environment.
 *
 * This is evidence-oriented smoke, not a full accounting reconciliation.
 */

async function waitForCostDashboard(page: Page) {
  await page.goto('/dashboard')
  await expect(page.locator('.dashboard')).toBeVisible({ timeout: 10000 })
  await expect(page.locator('.cost-reference-shell')).toBeVisible({ timeout: 10000 })
}

test.describe('Cost regression smoke', () => {
  test('dashboard cost view supports month switching without breaking KPI/chart structure', async ({ page }) => {
    await waitForCostDashboard(page)

    await expect(page.locator('.cost-reference-kpi').first()).toBeVisible({ timeout: 5000 })
    await expect(page.getByText('商务成本执行情况')).toBeVisible({ timeout: 5000 })
    await expect(page.getByText('成本科目排名')).toBeVisible({ timeout: 5000 })

    const monthSelect = page.locator('div.dashboard .ant-select').nth(1)
    await expect(monthSelect).toBeVisible({ timeout: 5000 })

    // 全部 -> 单月 -> 全部
    await monthSelect.click()
    let dropdown = page.locator('.ant-select-dropdown:not(.ant-select-dropdown-hidden)').last()
    await expect(dropdown).toBeVisible({ timeout: 5000 })
    const options = dropdown.locator('.ant-select-item-option:not(.ant-select-item-option-disabled)')
    const optionCount = await options.count()
    if (optionCount > 1) {
      await options.nth(1).click()
      await expect(page.locator('.cost-reference-kpi').first()).toBeVisible({ timeout: 5000 })
      await expect(page.locator('.cost-reference-chart canvas').first()).toBeVisible({ timeout: 8000 })

      await monthSelect.click()
      dropdown = page.locator('.ant-select-dropdown:not(.ant-select-dropdown-hidden)').last()
      await expect(dropdown).toBeVisible({ timeout: 5000 })
      await options.first().click()
      await expect(page.locator('.cost-reference-kpi').first()).toBeVisible({ timeout: 5000 })
    }

    await page.screenshot({ path: 'e2e/screenshots/cost-regression-dashboard-month-switch.png', fullPage: true })
  })

  test('contract ledger and payment list expose amount-bearing rows for cost comparison context', async ({ page }) => {
    await page.goto('/contract/ledger')
    await expect(page.locator('.cl-redesign-page')).toBeVisible({ timeout: 10000 })
    await expect(page.getByText('合同总金额(含税)')).toBeVisible({ timeout: 5000 })
    await expect(page.locator('button.cl-contract-link').first()).toBeVisible({ timeout: 5000 })

    await page.goto('/payment/application')
    await expect(page.locator('.payment-page')).toBeVisible({ timeout: 10000 })
    await expect(page.locator('.payment-kpi-summary').getByText('申请金额')).toBeVisible({ timeout: 5000 })
    await expect(page.locator('.vxe-body--row').first()).toBeVisible({ timeout: 5000 })

    await page.screenshot({ path: 'e2e/screenshots/cost-regression-payment-context.png', fullPage: true })
  })
})
