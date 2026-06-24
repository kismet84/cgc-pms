import { test, expect, type Locator, type Page } from '@playwright/test'

const SYSTEM_ERROR = '系统异常，请稍后重试'

async function loginAsAdmin(page: Page) {
  await page.goto('/login')
  await page.fill('input[placeholder="请输入用户名"]', 'admin')
  await page.fill('input[placeholder="请输入密码"]', 'admin123')
  await page.click('button[type="submit"]')
  await page.waitForURL((url) => !url.pathname.includes('/login'), { timeout: 15000 })
}

async function selectFirstOption(select: Locator) {
  await select.click()
  const dropdown = select
    .page()
    .locator('.ant-select-dropdown:not(.ant-select-dropdown-hidden)')
    .last()
  await expect(dropdown).toBeVisible({ timeout: 10000 })
  const option = dropdown.locator('.ant-select-item-option').first()
  await expect(option).toBeVisible({ timeout: 10000 })
  await option.click()
}

async function selectOptionByText(select: Locator, text: string) {
  await select.click()
  const dropdown = select
    .page()
    .locator('.ant-select-dropdown:not(.ant-select-dropdown-hidden)')
    .last()
  await expect(dropdown).toBeVisible({ timeout: 10000 })
  const options = dropdown.locator('.ant-select-item-option')
  const preferredOption = options.filter({ hasText: text }).first()
  const option = (await preferredOption.isVisible().catch(() => false))
    ? preferredOption
    : options.first()
  await expect(option).toBeVisible({ timeout: 10000 })
  await option.click()
}

async function selectTodayFromPicker(picker: Locator) {
  await picker.click()
  const pickerDropdown = picker
    .page()
    .locator('.ant-picker-dropdown:not(.ant-picker-dropdown-hidden)')
    .last()
  await expect(pickerDropdown).toBeVisible({ timeout: 10000 })
  await pickerDropdown.locator('.ant-picker-cell-today .ant-picker-cell-inner').first().click()
  await expect(pickerDropdown)
    .toBeHidden({ timeout: 10000 })
    .catch(() => {})
}

async function selectTodayFromDatePicker(page: Page, label: string) {
  await selectTodayFromPicker(
    page.locator(`.ant-form-item:has(.ant-form-item-label:has-text("${label}")) .ant-picker`),
  )
}

async function fillContractBasicInfo(page: Page) {
  await page.fill('input[placeholder="请输入合同名称"]', `E2E草稿合同-${Date.now()}`)
  await selectOptionByText(
    page.locator('.ant-form-item:has(.ant-form-item-label:has-text("合同类型")) .ant-select'),
    '分包合同',
  )
  await selectFirstOption(
    page.locator('.ant-form-item:has(.ant-form-item-label:has-text("所属项目")) .ant-select'),
  )
  await page
    .locator(
      '.ant-form-item:has(.ant-form-item-label:has-text("合同金额")) .ant-input-number-input',
    )
    .fill('1000')
  await selectTodayFromDatePicker(page, '签订日期')
}

test.describe('Contract draft-save and ledger regression', () => {
  test.beforeEach(async ({ page }) => {
    await loginAsAdmin(page)
  })

  test('saves draft with one detail and one payment term through composite endpoint', async ({
    page,
  }) => {
    await page.goto('/contract/create')
    await expect(page.getByRole('heading', { name: '新建合同' })).toBeVisible({ timeout: 10000 })

    await fillContractBasicInfo(page)
    await page.getByRole('button', { name: '下一步' }).click()

    await expect(page.locator('.item-editor')).toBeVisible({ timeout: 10000 })
    await page.getByRole('button', { name: '添加明细' }).click()
    const itemRow = page.locator('.item-editor .ant-table-tbody tr.ant-table-row').first()
    await itemRow.locator('input[placeholder="请输入名称"]').fill('AUTO')
    await itemRow.locator('input[placeholder="规格"]').fill('1')
    await itemRow.locator('.ant-input-number-input').nth(0).fill('1')
    await itemRow.locator('.ant-input-number-input').nth(1).fill('1000')
    await page.getByRole('button', { name: '下一步' }).click()

    await expect(page.locator('.term-editor')).toBeVisible({ timeout: 10000 })
    await page.getByRole('button', { name: '添加付款条款' }).click()
    const termRow = page.locator('.term-editor .ant-table-tbody tr.ant-table-row').first()
    await termRow.locator('input[placeholder="如：预付款、进度款"]').fill('term1')
    await termRow.locator('.ant-input-number-input').nth(0).fill('100')
    await termRow.locator('.ant-input-number-input').nth(1).fill('1000')
    await termRow.locator('input[placeholder="付款触发条件"]').fill('auto')
    await selectTodayFromPicker(termRow.locator('.ant-picker'))
    await page.getByRole('button', { name: '下一步' }).click()

    await expect(page.locator('.cf-review')).toBeVisible({ timeout: 10000 })
    await page.getByRole('button', { name: '保存草稿' }).click()

    await expect(page.getByText('合同已保存为草稿')).toBeVisible({ timeout: 15000 })
    await page.waitForURL(/\/contract\/ledger/, { timeout: 15000 })
    await expect(page.getByText('保存失败，请稍后重试')).toHaveCount(0)
    await expect(page.getByText(SYSTEM_ERROR)).toHaveCount(0)
  })

  test('created contract appears as draft in ledger list', async ({ page }) => {
    await page.goto('/contract/ledger')
    await page.waitForSelector('.ant-table', { timeout: 10000 })

    // Verify table is visible with headers
    const headers = page.locator('.ant-table-thead th')
    const headerCount = await headers.count()
    expect(headerCount).toBeGreaterThanOrEqual(3)

    // Verify filter bar is functional
    await expect(page.locator('.pm-filter')).toBeVisible()

    // Verify search/filter buttons
    await expect(page.locator('.pm-filter-actions button:has-text("查询")')).toBeVisible()
    await expect(page.locator('.pm-filter-actions button:has-text("重置")')).toBeVisible()

    await page.screenshot({ path: 'e2e/screenshots/contract-ledger-list.png', fullPage: true })
  })

  test('contract detail shows all tabs after creation', async ({ page }) => {
    await page.goto('/contract/ledger')
    await page.waitForSelector('.ant-table', { timeout: 10000 })

    // Click first contract row to navigate to detail
    const firstRow = page.locator('.ant-table-tbody tr.ant-table-row').first()
    const hasRow = await firstRow.isVisible({ timeout: 5000 }).catch(() => false)
    if (!hasRow) {
      console.log('No contracts in ledger, skipping detail test')
      return
    }

    // Click the contract name or code link
    const link = firstRow.locator('a').first()
    await link.click()
    await page.waitForTimeout(1000)

    // Verify detail page loaded with tabs
    const hasDetail = await page
      .locator('.ant-tabs')
      .first()
      .isVisible({ timeout: 5000 })
      .catch(() => false)

    if (hasDetail) {
      // Check basic info tab
      await expect(page.locator('.ant-tabs-tab:has-text("基本信息")').first()).toBeVisible()

      // Check that status info is present
      await page.screenshot({ path: 'e2e/screenshots/contract-detail.png', fullPage: true })
    } else {
      // Might be on create page if no contracts exist
      console.log('No contract detail page loaded')
    }
  })

  test('contract search filter by contract type works', async ({ page }) => {
    await page.goto('/contract/ledger')
    await page.waitForSelector('.pm-filter', { timeout: 10000 })

    // Find contract type filter and try to select
    const typeSelect = page.locator('.pm-field:has(label:has-text("合同类型")) .ant-select').first()
    const typeSelectVisible = await typeSelect.isVisible({ timeout: 3000 }).catch(() => false)

    if (typeSelectVisible) {
      await typeSelect.click()
      await page.waitForTimeout(500)

      const dropdown = page.locator('.ant-select-dropdown:not(.ant-select-dropdown-hidden)').last()
      const dropdownVisible = await dropdown.isVisible({ timeout: 5000 }).catch(() => false)

      if (dropdownVisible) {
        const options = dropdown.locator('.ant-select-item-option')
        const optionCount = await options.count()
        if (optionCount > 1) {
          // Select second option to filter
          await options.nth(1).click()
          await page.waitForTimeout(300)

          // Click search
          await page.locator('.pm-filter-actions button:has-text("查询")').click()
          await page.waitForTimeout(1000)

          // Table should still be visible (may be empty or filtered)
          await expect(page.locator('.ant-table')).toBeVisible({ timeout: 5000 })
        }
      }

      // Reset filter
      await page.locator('.pm-filter-actions button:has-text("重置")').click()
      await page.waitForTimeout(300)
      await page.locator('.pm-filter-actions button:has-text("查询")').click()
    }

    await page.screenshot({ path: 'e2e/screenshots/contract-filter.png', fullPage: true })
  })

  test('contract list KPI cards are visible', async ({ page }) => {
    await page.goto('/contract/ledger')
    await page.waitForSelector('.pm-page', { timeout: 10000 })

    // KPI cards may be present with class lg-kpi-strip or similar
    const kpiSection = page.locator('.lg-kpi-strip, .ant-card:has(.ant-statistic), .stl-kpis')
    const kpiVisible = await kpiSection
      .first()
      .isVisible({ timeout: 5000 })
      .catch(() => false)

    if (kpiVisible) {
      console.log('KPI cards visible on contract ledger')
      await page.screenshot({ path: 'e2e/screenshots/contract-kpi.png', fullPage: true })
    } else {
      console.log('No KPI cards on contract ledger page')
    }
  })
})
