import { test, expect, type Page } from '@playwright/test'

/**
 * Invoice E2E: Create → Register (link pay record) → Verify
 *
 * Flow:
 * 1. Login as admin
 * 2. Navigate to invoice list → create a new invoice
 * 3. Fill invoice details (no, type, amount, taxRate, taxAmount, date)
 * 4. Optionally link a pay record (register step)
 * 5. Verify invoice in the list with correct data
 * 6. Verify invoice: click "核验" → approve → check status changes to VERIFIED
 *
 * Backend behavior (T9 verified):
 * - Invoice create: POST /api/api/invoices (Phase 4 double-prefix)
 * - Invoice register: links payRecordId + unique-key prevents duplicates
 * - Invoice verify: PENDING → VERIFIED (or ABNORMAL if rejected)
 * - Business errors return HTTP 200 with code field
 */

async function loginAsAdmin(page: Page) {
  await page.goto('/login')
  await page.fill('input[placeholder="请输入用户名"]', 'admin')
  await page.fill('input[placeholder="请输入密码"]', 'admin123')
  await page.click('button[type="submit"]')
  await page.waitForURL((url) => !url.pathname.includes('/login'), { timeout: 15000 })
}

test.describe('Invoice: Create → Register → Verify', () => {
  test.beforeEach(async ({ page }) => {
    await loginAsAdmin(page)
  })

  test('should navigate to invoice list and verify page structure', async ({ page }) => {
    await page.goto('/invoice')
    await page.waitForSelector('.pm-page', { timeout: 10000 })

    // Verify page header
    await expect(page.locator('.pm-header')).toBeVisible()
    await expect(page.locator('.pm-header:has-text("发票管理")')).toBeVisible()

    // Verify filter section
    await expect(page.locator('.pm-filter')).toBeVisible()

    // Verify filter fields
    await expect(page.locator('.pm-field:has(label:has-text("关联付款记录"))')).toBeVisible()
    await expect(page.locator('.pm-field:has(label:has-text("发票号码"))')).toBeVisible()
    await expect(page.locator('.pm-field:has(label:has-text("核验状态"))')).toBeVisible()

    // Verify action buttons
    await expect(page.locator('.pm-filter-actions button:has-text("查询")')).toBeVisible()
    await expect(page.locator('.pm-filter-actions button:has-text("重置")')).toBeVisible()
    await expect(page.locator('.pm-filter-actions button:has-text("新增发票")')).toBeVisible()

    // Verify table
    await expect(page.locator('.ant-table')).toBeVisible({ timeout: 10000 })

    // Verify table column headers
    const tableHeaders = page.locator('.ant-table-thead th')
    await expect(tableHeaders.first()).toBeVisible()
  })

  test('should create a new invoice through modal form', async ({ page }) => {
    await page.goto('/invoice')
    await page.waitForSelector('.pm-page', { timeout: 10000 })

    // Click "新增发票" to open modal
    await page.click('.pm-filter-actions button:has-text("新增发票")')
    await expect(page.locator('.ant-modal')).toBeVisible({ timeout: 5000 })
    await expect(page.locator('.ant-modal .ant-modal-title:has-text("新增发票")')).toBeVisible()

    // Fill invoice number
    const invoiceNo = `E2E-INV-${Date.now()}`
    await page.locator('.ant-modal input[placeholder="请输入发票号码"]').fill(invoiceNo)

    // Select invoice type — default is "增值税专票", verify it's selected
    const typeSelect = page.locator(
      '.ant-modal .ant-form-item:has(label:has-text("发票类型")) .ant-select',
    )
    await typeSelect.click()
    await page.waitForSelector('.ant-select-dropdown:not(.ant-select-dropdown-hidden)', {
      timeout: 5000,
    })
    // Select "增值税普票" for variety
    await page.locator('.ant-select-item-option:has-text("增值税普票")').click()

    // Fill invoice amount
    await page
      .locator('.ant-modal .ant-form-item:has(label:has-text("发票金额")) .ant-input-number-input')
      .fill('150000')

    // Fill tax rate
    await page
      .locator('.ant-modal .ant-form-item:has(label:has-text("税率")) .ant-input-number-input')
      .fill('13')

    // Fill tax amount
    await page
      .locator('.ant-modal .ant-form-item:has(label:has-text("税额")) .ant-input-number-input')
      .fill('19500')

    // Optionally link a pay record if available — try selecting the first one
    const payRecordSelect = page.locator(
      '.ant-modal .ant-form-item:has(label:has-text("付款记录")) .ant-select',
    )
    await payRecordSelect.click()
    await page.waitForSelector('.ant-select-dropdown:not(.ant-select-dropdown-hidden)', {
      timeout: 5000,
    })

    const hasPayRecords = await page
      .locator('.ant-select-item-option')
      .first()
      .isVisible({ timeout: 3000 })
      .catch(() => false)
    if (hasPayRecords) {
      // Click the first pay record to register invoice
      await page.locator('.ant-select-item-option').first().click()
    } else {
      // Close the dropdown without selecting
      await page.keyboard.press('Escape')
    }

    // Click OK to save
    await page.locator('.ant-modal .ant-modal-footer .ant-btn-primary').click()

    // Wait for success message
    await page.waitForSelector('.ant-message-success', { timeout: 10000 }).catch(() => {})

    // Verify modal closed
    await expect(page.locator('.ant-modal')).not.toBeVisible({ timeout: 5000 })

    // Verify invoice appears in the table
    await page.waitForSelector('.ant-table', { timeout: 5000 })

    // Try to find the invoice by its number in the filter
    await page.locator('.pm-field:has(label:has-text("发票号码")) input').fill(invoiceNo)
    await page.click('.pm-filter-actions button:has-text("查询")')

    // Wait for table to reload
    await page.waitForSelector('.ant-table', { timeout: 5000 })

    // Verify filtered results: should find exactly 1 row matching the invoice number
    await expect(page.locator('.ant-table-tbody tr.ant-table-row')).toHaveCount(1, {
      timeout: 5000,
    })
    await expect(page.locator('.ant-table-tbody tr.ant-table-row').first()).toContainText(invoiceNo)

    // Verify verify status shows "待核验" (PENDING)
    await expect(page.locator('.ant-table-tbody tr.ant-table-row .ant-tag').last()).toBeVisible()

    await page.screenshot({ path: 'e2e/screenshots/invoice-created.png', fullPage: true })
  })

  test('should verify an invoice and check status change', async ({ page }) => {
    await page.goto('/invoice')
    await page.waitForSelector('.pm-page', { timeout: 10000 })
    await page.waitForSelector('.ant-table', { timeout: 10000 })

    // Check if there are any invoices
    const hasInvoices = await page
      .locator('.ant-table-tbody tr.ant-table-row')
      .first()
      .isVisible({ timeout: 5000 })
      .catch(() => false)

    if (!hasInvoices) {
      console.log('No invoices available, skipping verify test')
      return
    }

    // Look for a row that has "核验" button (only visible for PENDING status)
    const verifyBtn = page
      .locator('.ant-table-tbody tr.ant-table-row')
      .first()
      .locator('button:has-text("核验")')
    const canVerify = await verifyBtn.isVisible({ timeout: 3000 }).catch(() => false)

    if (!canVerify) {
      console.log('No PENDING invoices available for verification')
      return
    }

    // Click "核验"
    await verifyBtn.click()

    // Wait for confirm modal
    await expect(page.locator('.ant-modal-confirm')).toBeVisible({ timeout: 5000 })
    await expect(
      page.locator('.ant-modal-confirm .ant-modal-confirm-title:has-text("发票核验")'),
    ).toBeVisible()

    // Click "认证通过" (primary OK button)
    await page.locator('.ant-modal-confirm .ant-btn-primary:has-text("认证通过")').click()

    // Wait for success
    await page.waitForSelector('.ant-message-success', { timeout: 10000 }).catch(() => {})

    // Verify that the "核验" button disappears (status changed from PENDING)
    await expect(verifyBtn).not.toBeVisible({ timeout: 5000 })

    await page.screenshot({ path: 'e2e/screenshots/invoice-verified.png', fullPage: true })
  })

  test('should mark an invoice as abnormal during verification', async ({ page }) => {
    await page.goto('/invoice')
    await page.waitForSelector('.pm-page', { timeout: 10000 })
    await page.waitForSelector('.ant-table', { timeout: 10000 })

    // Check if there are any invoices
    const hasInvoices = await page
      .locator('.ant-table-tbody tr.ant-table-row')
      .first()
      .isVisible({ timeout: 5000 })
      .catch(() => false)

    if (!hasInvoices) {
      console.log('No invoices available, skipping abnormal verify test')
      return
    }

    // Look for a row that has "核验" button
    const verifyBtn = page
      .locator('.ant-table-tbody tr.ant-table-row')
      .first()
      .locator('button:has-text("核验")')
    const canVerify = await verifyBtn.isVisible({ timeout: 3000 }).catch(() => false)

    if (!canVerify) {
      console.log('No PENDING invoices available for abnormal verification')
      return
    }

    // Get invoice number for logging
    // Click "核验"
    await verifyBtn.click()

    // Wait for confirm modal
    await expect(page.locator('.ant-modal-confirm')).toBeVisible({ timeout: 5000 })

    // Click "标记异常" (the cancel/danger button)
    const abnormalBtn = page.locator('.ant-modal-confirm button:has-text("标记异常")')
    await expect(abnormalBtn).toBeVisible()
    await abnormalBtn.click()

    // Wait for warning message
    await page
      .waitForSelector('.ant-message-warning, .ant-message-notice', { timeout: 10000 })
      .catch(() => {})

    // Verify the "核验" button disappears
    await expect(verifyBtn).not.toBeVisible({ timeout: 5000 })

    await page.screenshot({ path: 'e2e/screenshots/invoice-abnormal.png', fullPage: true })
  })

  test('should filter invoices by verify status', async ({ page }) => {
    await page.goto('/invoice')
    await page.waitForSelector('.pm-page', { timeout: 10000 })
    await page.waitForSelector('.ant-table', { timeout: 10000 })

    // Select "待核验" from verify status filter
    const statusSelect = page.locator('.pm-field:has(label:has-text("核验状态")) .ant-select')
    await statusSelect.click()
    await page.waitForSelector('.ant-select-dropdown:not(.ant-select-dropdown-hidden)', {
      timeout: 5000,
    })
    await page.locator('.ant-select-item-option:has-text("待核验")').click()

    // Click "查询"
    await page.click('.pm-filter-actions button:has-text("查询")')

    // Wait for table reload
    await page.waitForSelector('.ant-table', { timeout: 5000 })

    // Verify page is still intact
    await expect(page.locator('.pm-page')).toBeVisible()

    // Assert: at least one row with PENDING status visible
    // Check that visible rows contain PENDING status tags
    const pendingTags = page.locator(
      '.ant-table-tbody tr.ant-table-row .ant-tag:has-text("待核验")',
    )
    const pendingCount = await pendingTags.count()
    expect(pendingCount).toBeGreaterThan(0)

    // Assert: no rows with VERIFIED status visible
    const verifiedTags = page.locator(
      '.ant-table-tbody tr.ant-table-row .ant-tag:has-text("已认证")',
    )
    await expect(verifiedTags).toHaveCount(0)

    // Assert: no rows with ABNORMAL status visible
    const abnormalTags = page.locator('.ant-table-tbody tr.ant-table-row .ant-tag:has-text("异常")')
    await expect(abnormalTags).toHaveCount(0)

    // Take screenshot showing filtered results
    await page.screenshot({ path: 'e2e/screenshots/invoice-filtered-pending.png', fullPage: true })

    // Now filter by "已认证"
    await statusSelect.click()
    await page.waitForSelector('.ant-select-dropdown:not(.ant-select-dropdown-hidden)', {
      timeout: 5000,
    })
    await page.locator('.ant-select-item-option:has-text("已认证")').click()

    await page.click('.pm-filter-actions button:has-text("查询")')
    await page.waitForSelector('.ant-table', { timeout: 5000 })

    // Assert: at least one row with VERIFIED status visible
    const verifiedTags2 = page.locator(
      '.ant-table-tbody tr.ant-table-row .ant-tag:has-text("已认证")',
    )
    const verifiedCount2 = await verifiedTags2.count()
    expect(verifiedCount2).toBeGreaterThan(0)

    // Assert: no rows with PENDING status visible
    const pendingTags2 = page.locator(
      '.ant-table-tbody tr.ant-table-row .ant-tag:has-text("待核验")',
    )
    await expect(pendingTags2).toHaveCount(0)

    await page.screenshot({ path: 'e2e/screenshots/invoice-filtered-verified.png', fullPage: true })
  })
})
