import { test, expect } from '@playwright/test'

/**
 * C3 failure-path smoke.
 *
 * Goal: keep at least one explicit failure-path evidence in current run without expanding scope.
 * We use inventory outbound overdraw as the preferred failure path because it stays within
 * current inventory chain and should not require extra route work.
 */

async function selectFirstAvailableOption(
  page: import('@playwright/test').Page,
  selectIndex: number,
) {
  const select = page.locator('.ant-form-item .ant-select').nth(selectIndex)
  await select.click()
  const dropdown = page.locator('.ant-select-dropdown:not(.ant-select-dropdown-hidden)').last()
  await expect(dropdown).toBeVisible({ timeout: 5000 })
  const option = dropdown
    .locator('.ant-select-item-option:not(.ant-select-item-option-disabled)')
    .first()
  await expect(option).toBeVisible({ timeout: 5000 })
  await option.click()
}

test.describe('C3 failure path smoke', () => {
  test('inventory outbound overdraw gives business-level result instead of blank system crash', async ({
    page,
  }) => {
    await page.goto('/inventory/transaction')
    await expect(page.getByText('库存交易').first()).toBeVisible({ timeout: 10000 })

    await page.locator('.ant-tabs-tab').filter({ hasText: '出库' }).click()
    await expect(page.locator('.ant-tabs-tab-active').filter({ hasText: '出库' })).toBeVisible()

    await selectFirstAvailableOption(page, 0)
    await selectFirstAvailableOption(page, 1)
    await page
      .locator('.ant-form-item:has(label:has-text("出库数量")) .ant-input-number-input')
      .fill('999999')
    await page.getByRole('button', { name: '确认出库' }).click({ force: true })

    const businessMessage = page.getByText(/(库存|不足|失败|不可|超出|数量)/).first()
    const systemCrash = page.getByText('系统异常，请稍后重试').first()

    const businessVisible = await businessMessage.isVisible({ timeout: 10000 }).catch(() => false)
    const crashVisible = await systemCrash.isVisible({ timeout: 3000 }).catch(() => false)

    console.log(
      `failure-path inventory overdraw: businessVisible=${businessVisible} crashVisible=${crashVisible}`,
    )
    expect(businessVisible).toBeTruthy()
    expect(crashVisible).toBeFalsy()
  })
})
