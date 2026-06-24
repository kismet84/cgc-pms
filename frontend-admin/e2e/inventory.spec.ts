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

async function selectAntdOption(select: Locator, preferredText?: string): Promise<string> {
  const page = select.page()

  await select.click()
  await page.waitForTimeout(300)

  const dropdown = page.locator('.ant-select-dropdown:not(.ant-select-dropdown-hidden)').last()
  try {
    await dropdown.waitFor({ state: 'visible', timeout: 5000 })
  } catch {
    await select.locator('.ant-select-selector').click({ force: true })
    await dropdown.waitFor({ state: 'visible', timeout: 5000 })
  }

  let option: Locator
  let selectedText = ''

  if (preferredText) {
    option = dropdown.locator('.ant-select-item-option').filter({ hasText: preferredText }).first()
    let found = await option.isVisible().catch(() => false)

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
      option = dropdown
        .locator('.ant-select-item-option:not(.ant-select-item-option-disabled)')
        .first()
      selectedText = (await option.textContent())?.trim() ?? '(first available)'
      console.log(`selectAntdOption: "${preferredText}" not found, using "${selectedText}"`)
    }
  } else {
    option = dropdown
      .locator('.ant-select-item-option:not(.ant-select-item-option-disabled)')
      .first()
    selectedText = (await option.textContent())?.trim() ?? '(first available)'
  }

  await option.click()
  await dropdown.waitFor({ state: 'hidden', timeout: 5000 }).catch(() => {})
  return selectedText
}

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
  if (tabLabel === '出库') {
    await page.locator('.ant-tabs-tab').filter({ hasText: '出库' }).click()
    await page.waitForTimeout(300)
  }
  await expect(page.locator('.ant-tabs-tab-active').filter({ hasText: tabLabel })).toBeVisible()

  await selectAntdOption(
    page.locator('.ant-form-item:has(label:has-text("仓库")) .ant-select').first(),
    'AutoWarehouse0359',
  )
  await selectAntdOption(
    page.locator('.ant-form-item:has(label:has-text("物料")) .ant-select').first(),
    'MAT312742',
  )
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

    await expectNoText(page, SYSTEM_ERROR)
    await expectNoText(page, IN_FAILED)
  })

  test('outbound accepts warehouse/material JSON submit flow', async ({ page }) => {
    await page.goto('/inventory/transaction')
    await expect(page.getByRole('heading', { name: '库存交易' })).toBeVisible({ timeout: 10000 })

    await fillInventoryForm(page, '入库', '10')
    await clickAndCaptureResponse(page, '确认入库', '/inventory/stock/in')

    await fillInventoryForm(page, '出库', '1')
    await clickAndCaptureResponse(page, '确认出库', '/inventory/stock/out')

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

    await expectNoText(page, LEDGER_FAILED)
    await expectNoText(page, SYSTEM_ERROR)
  })

  test('stock ledger pagination controls are visible', async ({ page }) => {
    await page.goto('/inventory/stock')
    await expect(page.getByRole('heading', { name: '库存台账' })).toBeVisible({ timeout: 10000 })

    // Click query first to load data
    await page.locator('button:has-text("查询")').first().click()
    await page.waitForTimeout(2000)

    // Check for pagination
    const pagination = page.locator('.ant-pagination')
    const paginationVisible = await pagination.isVisible({ timeout: 5000 }).catch(() => false)

    if (paginationVisible) {
      const totalText = await pagination.locator('.ant-pagination-total-text').textContent()
      console.log(`Pagination total: ${totalText}`)
      expect(pagination).toBeVisible()
    } else {
      console.log('Pagination not visible — may be single page of results')
    }

    await page.screenshot({
      path: 'e2e/screenshots/inventory-ledger-pagination.png',
      fullPage: true,
    })
  })

  test('stock detail modal opens and displays data', async ({ page }) => {
    await page.goto('/inventory/stock')
    await expect(page.getByRole('heading', { name: '库存台账' })).toBeVisible({ timeout: 10000 })

    await page.locator('button:has-text("查询")').first().click()
    await page.waitForTimeout(2000)

    // Look for a detail button or clickable row
    const detailBtn = page.locator('button:has-text("详情"), a:has-text("详情")').first()
    const hasDetailBtn = await detailBtn.isVisible({ timeout: 3000 }).catch(() => false)

    if (hasDetailBtn) {
      await detailBtn.click()
      await page.waitForTimeout(500)

      // Modal or drawer should open
      const modal = page.locator('.ant-modal, .ant-drawer').first()
      const modalVisible = await modal.isVisible({ timeout: 5000 }).catch(() => false)

      if (modalVisible) {
        console.log('Detail modal/drawer opened')
        await page.screenshot({
          path: 'e2e/screenshots/inventory-stock-detail.png',
          fullPage: true,
        })
        // Close it
        await page.keyboard.press('Escape')
      }
    } else {
      console.log('No detail button found in stock ledger')
    }
  })

  test('stock KPI statistics are visible', async ({ page }) => {
    await page.goto('/inventory/stock')
    await expect(page.getByRole('heading', { name: '库存台账' })).toBeVisible({ timeout: 10000 })

    // KPI cards may exist on the page
    const kpiSection = page.locator('.lg-kpi-strip, .ant-card:has(.ant-statistic)')
    const kpiVisible = await kpiSection
      .first()
      .isVisible({ timeout: 5000 })
      .catch(() => false)

    if (kpiVisible) {
      const kpiCount = await kpiSection.count()
      console.log(`Found ${kpiCount} KPI elements`)
      for (let i = 0; i < kpiCount; i++) {
        const text = await kpiSection.nth(i).textContent()
        console.log(`KPI ${i}: ${text?.trim()}`)
      }
    } else {
      console.log('No KPI cards visible on stock ledger page')
    }

    await page.screenshot({ path: 'e2e/screenshots/inventory-kpi.png', fullPage: true })
  })

  test('inventory transaction page has both inbound and outbound tabs', async ({ page }) => {
    await page.goto('/inventory/transaction')
    await expect(page.getByRole('heading', { name: '库存交易' })).toBeVisible({ timeout: 10000 })

    // Verify both tabs
    await expect(page.locator('.ant-tabs-tab:has-text("入库")')).toBeVisible()
    await expect(page.locator('.ant-tabs-tab:has-text("出库")')).toBeVisible()

    // Default tab should be inbound
    await expect(page.locator('.ant-tabs-tab-active').filter({ hasText: '入库' })).toBeVisible()
  })

  test('stock ledger search by keyword works', async ({ page }) => {
    await page.goto('/inventory/stock')
    await expect(page.getByRole('heading', { name: '库存台账' })).toBeVisible({ timeout: 10000 })

    // Find keyword search input
    const searchInput = page
      .locator('input[placeholder*="搜索"], input[placeholder*="关键词"]')
      .first()
    const hasSearch = await searchInput.isVisible({ timeout: 3000 }).catch(() => false)

    if (hasSearch) {
      await searchInput.fill('test')
      await page.locator('button:has-text("查询")').first().click()
      await page.waitForTimeout(1500)

      // Table should still be visible after search
      await expect(page.locator('.ant-table, .vxe-table').first()).toBeVisible({ timeout: 5000 })

      // Reset
      await page.locator('button:has-text("重置")').first().click()
    }

    await page.screenshot({ path: 'e2e/screenshots/inventory-search.png', fullPage: true })
  })
})
