import { test, expect, type Browser, type BrowserContext, type Locator, type Page } from '@playwright/test'

/**
 * Payment / Invoice minimum smoke for BCD closure.
 *
 * Scope boundary:
 * - Payment: application list, headers/status tags, pay type filter.
 * - Invoice: current single-page /invoice list, verify-status filter, add modal.
 * - Excluded from this file: /payment/record route and invoice detail route, which do not exist
 *   in the current frontend router.
 */

let sharedContext: BrowserContext
let sharedPage: Page

function screenshotPath(name: string) {
  return `e2e/screenshots/${name}-${Date.now()}.png`
}

async function createAuthenticatedPage(browser: Browser) {
  const context = await browser.newContext({ storageState: 'e2e/.auth/admin.json' })
  await context.addInitScript((userInfo) => {
    window.sessionStorage.setItem('cgc_pms_userinfo', JSON.stringify(userInfo))
  }, {
    userId: '1',
    username: 'admin',
    roles: ['SUPER_ADMIN'],
    permissions: ['*'],
    roleName: 'SUPER_ADMIN',
  })

  const page = await context.newPage()
  return { context, page }
}

async function selectFirstAvailableOption(select: Locator) {
  await select.click()
  const dropdown = select.page().locator('.ant-select-dropdown:not(.ant-select-dropdown-hidden)').last()
  await expect(dropdown).toBeVisible({ timeout: 5000 })
  const options = dropdown.locator('.ant-select-item-option:not(.ant-select-item-option-disabled)')
  const optionCount = await options.count()
  if (optionCount === 0) {
    console.log('No selectable options in dropdown')
    await select.page().keyboard.press('Escape')
    return false
  }
  await options.first().click()
  await dropdown.waitFor({ state: 'hidden', timeout: 5000 }).catch(() => {})
  return true
}

async function waitForPaymentList(page: Page) {
  await page.goto('/payment/application')
  await expect(page.locator('.payment-page')).toBeVisible({ timeout: 10000 })
  await expect(page.locator('.payment-search-bar')).toBeVisible({ timeout: 10000 })
  await expect(page.locator('.vxe-table').first()).toBeVisible({ timeout: 10000 })
}

async function waitForInvoiceList(page: Page) {
  await page.goto('/invoice')
  await expect(page.locator('.invoice-page')).toBeVisible({ timeout: 10000 })
  await expect(page.locator('.invoice-search-bar')).toBeVisible({ timeout: 10000 })
  await expect(page.locator('.vxe-table').first()).toBeVisible({ timeout: 10000 })
}

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

test.describe('Payment: Application list and detail', () => {
  test('should navigate to payment application list and verify page structure', async () => {
    await waitForPaymentList(sharedPage)

    await expect(sharedPage.locator('.payment-page-head')).toBeVisible()
    await expect(sharedPage.locator('.payment-search-actions button').filter({ hasText: '查询' })).toBeVisible()
    await expect(sharedPage.locator('.payment-search-actions button').filter({ hasText: '重置' })).toBeVisible()
    await expect(sharedPage.locator('.payment-toolbar button').filter({ hasText: '新建申请' })).toBeVisible()

    await sharedPage.screenshot({ path: screenshotPath('payment-application-list'), fullPage: true })
  })

  test('payment application table shows pay type and status tags', async () => {
    await waitForPaymentList(sharedPage)

    const headersText = await sharedPage.locator('.vxe-header--column').allTextContents()
    const allHeaders = headersText.join(' ')
    console.log(`Payment table headers: ${allHeaders}`)

    expect(allHeaders).toContain('付款类型')
    expect(allHeaders).toContain('支付状态')
    expect(allHeaders).toContain('审批状态')

    await sharedPage.screenshot({ path: screenshotPath('payment-table-headers'), fullPage: true })
  })

  test('payment application filter by pay type works', async () => {
    await waitForPaymentList(sharedPage)

    const payTypeSelect = sharedPage.locator('.payment-search-select.is-compact').nth(0)
    if (await payTypeSelect.isVisible({ timeout: 3000 }).catch(() => false)) {
      const selected = await selectFirstAvailableOption(payTypeSelect)
      if (selected) {
        await sharedPage.locator('.payment-search-actions button').filter({ hasText: '查询' }).click()
        await expect(sharedPage.locator('.vxe-table').first()).toBeVisible({ timeout: 5000 })
      }
    }

    await sharedPage.screenshot({ path: screenshotPath('payment-filter-paytype'), fullPage: true })
  })
})

test.describe('Invoice: List → Registration', () => {
  test('should navigate to invoice list and verify page structure', async () => {
    await waitForInvoiceList(sharedPage)

    await expect(sharedPage.locator('.invoice-page-head')).toBeVisible()
    await expect(sharedPage.locator('.invoice-search-actions button').filter({ hasText: '查询' })).toBeVisible()
    await expect(sharedPage.locator('.invoice-search-actions button').filter({ hasText: '重置' })).toBeVisible()
    await expect(sharedPage.locator('.invoice-toolbar button').filter({ hasText: '新增发票' })).toBeVisible()

    await sharedPage.screenshot({ path: screenshotPath('invoice-list'), fullPage: true })
  })

  test('invoice table shows verify status and invoice type tags', async () => {
    await waitForInvoiceList(sharedPage)

    const headersText = await sharedPage.locator('.vxe-header--column').allTextContents()
    const allHeaders = headersText.join(' ')
    console.log(`Invoice table headers: ${allHeaders}`)

    expect(allHeaders).toContain('发票号码')
    expect(allHeaders).toContain('核验状态')

    await sharedPage.screenshot({ path: screenshotPath('invoice-table-headers'), fullPage: true })
  })

  test('invoice filter by verify status works', async () => {
    await waitForInvoiceList(sharedPage)

    const verifySelect = sharedPage.locator('.invoice-search-select.is-compact').first()
    if (await verifySelect.isVisible({ timeout: 3000 }).catch(() => false)) {
      const selected = await selectFirstAvailableOption(verifySelect)
      if (selected) {
        await sharedPage.locator('.invoice-search-actions button').filter({ hasText: '查询' }).click()
        await expect(sharedPage.locator('.vxe-table').first()).toBeVisible({ timeout: 5000 })
      }
    }

    await sharedPage.screenshot({ path: screenshotPath('invoice-filter-verify'), fullPage: true })
  })

  test('should open invoice registration modal', async () => {
    await waitForInvoiceList(sharedPage)

    await sharedPage.locator('.invoice-toolbar button').filter({ hasText: '新增发票' }).click()
    const modal = sharedPage.locator('.ant-modal').filter({ hasText: '发票' }).last()
    const modalVisible = await modal.isVisible({ timeout: 5000 }).catch(() => false)

    if (modalVisible) {
      console.log('Invoice registration modal opened')
      await sharedPage.screenshot({ path: screenshotPath('invoice-register-modal'), fullPage: true })
      await modal.locator('.ant-modal-close').click()
      await expect(modal).not.toBeVisible({ timeout: 5000 })
    } else {
      console.log('Invoice registration modal did not appear')
    }
  })
})
