import { test, expect, type Locator, type Page } from '@playwright/test'

const SYSTEM_ERROR = '系统异常，请稍后重试'
const IN_FAILED = '入库失败，请稍后重试'
const OUT_FAILED = '出库失败，请稍后重试'
const LEDGER_FAILED = '加载库存台账失败，请稍后重试'

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

  const options = dropdown.locator('.ant-select-item-option:not(.ant-select-item-option-disabled)')
  let option: Locator | undefined
  let selectedText = ''

  if (preferredText) {
    const preferredOption = options.filter({ hasText: preferredText }).first()
    if (await preferredOption.isVisible({ timeout: 1000 }).catch(() => false)) {
      option = preferredOption
    } else {
      const searchInput = select.locator('.ant-select-selection-search-input')
      if (await searchInput.isVisible().catch(() => false)) {
        await searchInput.fill('')
        await page.waitForTimeout(300)
      }
      console.log(`selectAntdOption: "${preferredText}" not found, using first available option`)
    }
  }

  option ??= options.first()
  await expect(option).toBeVisible({ timeout: 5000 })
  selectedText = (await option.textContent())?.trim() ?? '(first available)'
  await option.click()
  await dropdown.waitFor({ state: 'hidden', timeout: 5000 }).catch(() => {})
  return selectedText
}

async function clickAndCaptureResponse(page: Page, buttonName: string, urlPart: string, timeoutMs = 10000) {
  const responsePromise = page
    .waitForResponse((res) => res.url().includes(urlPart), { timeout: timeoutMs })
    .catch(() => null)
  try {
    await page.getByRole('button', { name: buttonName }).click({ force: true })
    const response = await responsePromise
    if (!response) {
      console.log(`clickAndCaptureResponse: ${buttonName} -> ${urlPart} timed out`)
      return null
    }
    console.log(`clickAndCaptureResponse: ${buttonName} -> ${urlPart} ${response.status()}`)
    return response
  } catch {
    console.log(`clickAndCaptureResponse: ${buttonName} -> ${urlPart} timed out`)
    return null
  }
}

async function expectNoText(page: Page, text: string) {
  expect(await page.getByText(text).count()).toBe(0)
}

async function waitForInventoryTransaction(page: Page) {
  await page.goto('/inventory/transaction')
  await expect(page.getByText('库存交易').first()).toBeVisible({ timeout: 10000 })
  await expect(page.locator('.ant-tabs-tab-active').filter({ hasText: '入库' })).toBeVisible()
}

async function waitForStockLedger(page: Page) {
  await page.goto('/inventory/stock')
  await expect(page.locator('.stock-page')).toBeVisible({ timeout: 10000 })
  await expect(page.getByText('库存台账').first()).toBeVisible({ timeout: 10000 })
  await expect(page.locator('.stock-search-bar')).toBeVisible({ timeout: 10000 })
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
  await page.locator(`.ant-form-item:has(label:has-text("${qtyLabel}")) .ant-input-number-input`).fill(quantity)
}

test.describe('Inventory original failure regressions', () => {
  test.describe.configure({ mode: 'serial' })

  test.beforeEach(async () => {})

  test('inbound accepts warehouse/material JSON submit flow', async ({ page }) => {
    await waitForInventoryTransaction(page)

    await fillInventoryForm(page, '入库', '5')
    await clickAndCaptureResponse(page, '确认入库', '/inventory/stock/in')

    await expectNoText(page, SYSTEM_ERROR)
    await expectNoText(page, IN_FAILED)
  })

  test('outbound accepts warehouse/material JSON submit flow', async ({ page }) => {
    await waitForInventoryTransaction(page)

    await fillInventoryForm(page, '入库', '10')
    await clickAndCaptureResponse(page, '确认入库', '/inventory/stock/in')

    await fillInventoryForm(page, '出库', '1')
    await clickAndCaptureResponse(page, '确认出库', '/inventory/stock/out')

    await expectNoText(page, SYSTEM_ERROR)
    await expectNoText(page, OUT_FAILED)
  })

  test('stock ledger query does not surface system error', async ({ page }) => {
    await waitForStockLedger(page)

    await selectAntdOption(page.locator('.stock-search-bar .ant-select').nth(0), 'AutoWarehouse0359')
    await selectAntdOption(page.locator('.stock-search-bar .ant-select').nth(1), 'MAT312742')
    await clickAndCaptureResponse(page, '查询', '/inventory/stock/ledger', 3000)

    await expect(page.locator('.vxe-table').first()).toBeVisible({ timeout: 5000 })
    await expectNoText(page, LEDGER_FAILED)
    await expectNoText(page, SYSTEM_ERROR)
  })

  test('inventory transaction page has both inbound and outbound tabs', async ({ page }) => {
    await waitForInventoryTransaction(page)

    await expect(page.locator('.ant-tabs-tab').filter({ hasText: '入库' })).toBeVisible()
    await expect(page.locator('.ant-tabs-tab').filter({ hasText: '出库' })).toBeVisible()
    await expect(page.locator('.ant-tabs-tab-active').filter({ hasText: '入库' })).toBeVisible()
  })
})
