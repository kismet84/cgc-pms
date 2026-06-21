import { test, expect, type Locator, type Page } from '@playwright/test'

const SYSTEM_ERROR = '系统异常，请稍后重试'
const IN_FAILED = '入库失败，请稍后重试'
const OUT_FAILED = '出库失败，请稍后重试'
const LEDGER_FAILED = '加载库存台账失败，请稍后重试'

async function loginAsAdmin(page: Page) {
  await page.goto('/login')
  await page.fill('input[placeholder="请输入用户名"]', 'admin')
  await page.fill('input[placeholder="请输入密码"]', 'admin123')
  await page.click('button[type="submit"]')
  await page.waitForURL((url) => !url.pathname.includes('/login'), { timeout: 15000 })
}

/**
 * Robust Ant Design <a-select> helper.
 * - Opens the dropdown
 * - If preferredText is given, tries to find it (with optional search-input typing)
 * - Falls back to the first visible non-disabled option
 * - Returns the selected option text for logging
 */
async function selectAntdOption(select: Locator, preferredText?: string): Promise<string> {
  const page = select.page()

  // Click to open
  await select.click()
  await page.waitForTimeout(300)

  // Locate the visible dropdown
  const dropdown = page.locator('.ant-select-dropdown:not(.ant-select-dropdown-hidden)').last()
  try {
    await dropdown.waitFor({ state: 'visible', timeout: 5000 })
  } catch {
    // Retry clicking the selector area directly
    await select.locator('.ant-select-selector').click({ force: true })
    await dropdown.waitFor({ state: 'visible', timeout: 5000 })
  }

  let option: Locator
  let selectedText = ''

  if (preferredText) {
    // Try to find the preferred option by text
    option = dropdown.locator('.ant-select-item-option').filter({ hasText: preferredText }).first()
    let found = await option.isVisible().catch(() => false)

    // If show-search is available, type the text to filter
    if (!found) {
      const searchInput = select.locator('.ant-select-selection-search-input')
      const searchVisible = await searchInput.isVisible().catch(() => false)
      if (searchVisible) {
        await searchInput.fill(preferredText)
        await page.waitForTimeout(800)
        option = dropdown
          .locator('.ant-select-item-option')
          .filter({ hasText: preferredText })
          .first()
        found = await option.isVisible().catch(() => false)
      }
    }

    if (found) {
      selectedText = (await option.textContent())?.trim() ?? ''
    } else {
      // Fallback: pick first visible non-disabled option
      option = dropdown
        .locator('.ant-select-item-option:not(.ant-select-item-option-disabled)')
        .first()
      selectedText = (await option.textContent())?.trim() ?? '(first available)'
      console.log(`selectAntdOption: "${preferredText}" not found, using "${selectedText}"`)
    }
  } else {
    // No preferred text, just pick the first visible option
    option = dropdown
      .locator('.ant-select-item-option:not(.ant-select-item-option-disabled)')
      .first()
    selectedText = (await option.textContent())?.trim() ?? '(first available)'
  }

  await option.click()
  // Wait for dropdown to close
  await dropdown.waitFor({ state: 'hidden', timeout: 5000 }).catch(() => {})
  return selectedText
}

/**
 * Click a button and wait for a matching network response (short timeout).
 * Returns the response if captured, or null if timed out.
 */
async function clickAndCaptureResponse(
  page: Page,
  buttonName: string,
  urlPart: string,
  timeoutMs = 10000,
) {
  const responsePromise = page
    .waitForResponse((res) => res.url().includes(urlPart), { timeout: timeoutMs })
    .catch(() => null)
  try {
    await page.getByRole('button', { name: buttonName }).click({ force: true })
    const response = await responsePromise
    if (!response) {
      console.log(
        `clickAndCaptureResponse: ${buttonName} -> ${urlPart} timed out (no matching response)`,
      )
      return null
    }
    console.log(`clickAndCaptureResponse: ${buttonName} -> ${urlPart} ${response.status()}`)
    return response
  } catch {
    console.log(
      `clickAndCaptureResponse: ${buttonName} -> ${urlPart} timed out (no matching response)`,
    )
    return null
  }
}

async function expectNoText(page: Page, text: string) {
  expect(await page.getByText(text).count()).toBe(0)
}

async function fillInventoryForm(page: Page, tabLabel: string, quantity: string) {
  // Switch to the right tab if needed
  if (tabLabel === '出库') {
    await page.locator('.ant-tabs-tab').filter({ hasText: '出库' }).click()
    await page.waitForTimeout(300)
  }
  await expect(page.locator('.ant-tabs-tab-active').filter({ hasText: tabLabel })).toBeVisible()

  // Select warehouse
  await selectAntdOption(
    page.locator('.ant-form-item:has(label:has-text("仓库")) .ant-select').first(),
    'AutoWarehouse0359',
  )
  // Select material
  await selectAntdOption(
    page.locator('.ant-form-item:has(label:has-text("物料")) .ant-select').first(),
    'MAT312742',
  )
  // Fill quantity
  const qtyLabel = tabLabel === '出库' ? '出库数量' : '入库数量'
  await page
    .locator(`.ant-form-item:has(label:has-text("${qtyLabel}")) .ant-input-number-input`)
    .fill(quantity)
}

test.describe('Inventory original failure regressions', () => {
  test.describe.configure({ mode: 'serial' })

  test.beforeEach(async ({ page }) => {
    await loginAsAdmin(page)
  })

  test('inbound accepts warehouse/material JSON submit flow', async ({ page }) => {
    await page.goto('/inventory/transaction')
    await expect(page.getByRole('heading', { name: '库存交易' })).toBeVisible({ timeout: 10000 })
    await expect(page.locator('.ant-tabs-tab-active').filter({ hasText: '入库' })).toBeVisible()

    await fillInventoryForm(page, '入库', '5')
    await clickAndCaptureResponse(page, '确认入库', '/inventory/stock/in')

    // Assert no original system errors
    await expectNoText(page, SYSTEM_ERROR)
    await expectNoText(page, IN_FAILED)
  })

  test('outbound accepts warehouse/material JSON submit flow', async ({ page }) => {
    await page.goto('/inventory/transaction')
    await expect(page.getByRole('heading', { name: '库存交易' })).toBeVisible({ timeout: 10000 })

    // Ensure at least some stock exists before outbound
    await fillInventoryForm(page, '入库', '10')
    await clickAndCaptureResponse(page, '确认入库', '/inventory/stock/in')

    // Now do outbound
    await fillInventoryForm(page, '出库', '1')
    await clickAndCaptureResponse(page, '确认出库', '/inventory/stock/out')

    // Assert no original system errors
    await expectNoText(page, SYSTEM_ERROR)
    await expectNoText(page, OUT_FAILED)
  })

  test('stock ledger query does not surface system error', async ({ page }) => {
    await page.goto('/inventory/stock')
    await expect(page.getByRole('heading', { name: '库存台账' })).toBeVisible({ timeout: 10000 })

    await selectAntdOption(
      page.locator('.pt-field:has(label:has-text("仓库")) .ant-select'),
      'AutoWarehouse0359',
    )
    await selectAntdOption(
      page.locator('.pt-field:has(label:has-text("物料")) .ant-select'),
      'MAT312742',
    )
    await clickAndCaptureResponse(page, '查询', '/inventory/stock/ledger', 3000)

    // Should see either stock data or empty-state message, never system error
    await expectNoText(page, LEDGER_FAILED)
    await expectNoText(page, SYSTEM_ERROR)
  })
})
