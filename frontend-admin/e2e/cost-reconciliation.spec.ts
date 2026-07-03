import { test, expect } from '@playwright/test'

/**
 * Stronger cost reconciliation smoke for Task 7.
 *
 * It exercises the dedicated /cost/summary page, which is closer to the plan's
 * “合同 / 成本 / 动态汇总” reconciliation intent than generic dashboard smoke.
 */

test.describe('Cost reconciliation smoke', () => {
  test('cost summary page can select a project and render reconciliation KPIs', async ({ page }) => {
    await page.goto('/cost/summary')
    await expect(page.getByText('项目成本明细核对').first()).toBeVisible({ timeout: 10000 })

    const projectSelect = page.locator('.ant-select').first()
    await expect(projectSelect).toBeVisible({ timeout: 5000 })
    await projectSelect.click()

    const dropdown = page.locator('.ant-select-dropdown:not(.ant-select-dropdown-hidden)').last()
    await expect(dropdown).toBeVisible({ timeout: 5000 })
    const options = dropdown.locator('.ant-select-item-option:not(.ant-select-item-option-disabled)')
    const count = await options.count()
    expect(count).toBeGreaterThan(0)
    await options.first().click()

    await expect(page.locator('.cost-reconcile-kpis .lg-kpi-card').first()).toBeVisible({ timeout: 10000 })
    await expect(page.getByText('目标成本').first()).toBeVisible({ timeout: 5000 })
    await expect(page.getByText('合同锁定成本').first()).toBeVisible({ timeout: 5000 })
    await expect(page.getByText('实际成本').first()).toBeVisible({ timeout: 5000 })
    await expect(page.getByText('动态成本').first()).toBeVisible({ timeout: 5000 })
    await expect(page.getByText('成本偏差').first()).toBeVisible({ timeout: 5000 })

    await page.screenshot({ path: 'e2e/screenshots/cost-reconciliation-summary.png', fullPage: true })
  })
})
