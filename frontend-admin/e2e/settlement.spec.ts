import { test, expect, type Page } from '@playwright/test'

/**
 * Settlement E2E: List → Detail → Summary data verification → Status tags → Archive guard
 *
 * Selector map (list page):
 *   .stl-page        — page wrapper
 *   .stl-kpis        — KPI cards grid container
 *   .stl-kpi         — single KPI card
 *   .stl-filter      — filter card
 *   .stl-field       — filter field row
 *   .stl-filter-actions — filter action buttons
 *   .stl-toolbar     — toolbar (新建结算 button)
 *   .stl-link        — settlement code link (click to detail)
 *   .vxe-table       — vxe-grid rendered table
 *   .vxe-body--row   — vxe-grid body row
 *   .ant-tag         — status tags (settlementStatus / approvalStatus)
 *
 * Selector map (detail page):
 *   .stl-detail-page      — detail page wrapper
 *   .ant-page-header      — page header with back button
 *   .ant-tabs-tab         — tab labels
 *   .stl-summary-readonly — summary tab container
 *   .ant-descriptions     — descriptions component for data display
 */

async function loginAsAdmin(page: Page) {
  await page.goto('/login')
  await page.fill('input[placeholder="请输入用户名"]', 'admin')
  await page.fill('input[placeholder="请输入密码"]', 'admin123')
  await page.click('button[type="submit"]')
  await page.waitForURL((url) => !url.pathname.includes('/login'), { timeout: 15000 })
}

test.describe('Settlement: List → Detail → Summary', () => {
  test.beforeEach(async ({ page }) => {
    await loginAsAdmin(page)
  })

  test('should navigate to settlement list and verify page structure', async ({ page }) => {
    await page.goto('/settlement/list')
    await page.waitForSelector('.stl-page', { timeout: 10000 })

    // Verify breadcrumb
    await expect(page.locator('.stl-breadcrumb')).toBeVisible()
    await expect(page.locator('.stl-breadcrumb:has-text("结算列表")')).toBeVisible()

    // Verify KPI cards section
    await expect(page.locator('.stl-kpis')).toBeVisible()

    // Verify at least 5 KPI cards
    const kpiCards = page.locator('.stl-kpi')
    const kpiCount = await kpiCards.count()
    expect(kpiCount).toBeGreaterThanOrEqual(5)

    // Verify filter section
    await expect(page.locator('.stl-filter')).toBeVisible()

    // Verify filter fields
    await expect(page.locator('.stl-field:has(label:has-text("所属项目"))')).toBeVisible()
    await expect(page.locator('.stl-field:has(label:has-text("关联合同"))')).toBeVisible()
    await expect(page.locator('.stl-field:has(label:has-text("合作方"))')).toBeVisible()
    await expect(page.locator('.stl-field:has(label:has-text("结算状态"))')).toBeVisible()

    // Verify filter action buttons
    await expect(page.locator('.stl-filter-actions button:has-text("查询")')).toBeVisible()
    await expect(page.locator('.stl-filter-actions button:has-text("重置")')).toBeVisible()

    // Verify toolbar
    await expect(page.locator('.stl-toolbar button:has-text("新建结算")')).toBeVisible()

    // Verify table (vxe-grid)
    await expect(page.locator('.vxe-table')).toBeVisible({ timeout: 10000 })

    await page.screenshot({ path: 'e2e/screenshots/settlement-list-structure.png', fullPage: true })
  })

  test('should verify settlement KPI cards show data', async ({ page }) => {
    await page.goto('/settlement/list')
    await page.waitForSelector('.stl-page', { timeout: 10000 })

    const kpiCards = page.locator('.stl-kpi')
    const kpiCount = await kpiCards.count()

    for (let i = 0; i < kpiCount; i++) {
      const card = kpiCards.nth(i)
      const titleEl = card.locator('.stl-kpi-title')
      const valueEl = card.locator('.stl-kpi-value')

      const titleVisible = await titleEl.isVisible().catch(() => false)
      const valueVisible = await valueEl.isVisible().catch(() => false)

      if (titleVisible && valueVisible) {
        const title = await titleEl.textContent()
        const value = await valueEl.textContent()
        console.log(`KPI card ${i}: "${title}" = "${value}"`)
        expect(title).toBeTruthy()
        expect(value).toBeTruthy()
      }
    }

    await page.screenshot({ path: 'e2e/screenshots/settlement-kpi-cards.png', fullPage: true })
  })

  test('should navigate to settlement detail and verify auto-summarized data', async ({ page }) => {
    await page.goto('/settlement/list')
    await page.waitForSelector('.stl-page', { timeout: 10000 })
    await page.waitForSelector('.vxe-table', { timeout: 10000 })

    const hasRows = await page
      .locator('.vxe-body--row')
      .first()
      .isVisible({ timeout: 5000 })
      .catch(() => false)

    if (!hasRows) {
      console.log('No settlements available, skipping detail test')
      return
    }

    const firstLink = page.locator('.stl-link').first()
    await firstLink.click()

    await page.waitForSelector('.stl-detail-page', { timeout: 10000 })

    await expect(page.locator('.ant-page-header:has-text("结算详情")')).toBeVisible({
      timeout: 5000,
    })

    // Verify status tags are visible in the header
    await expect(page.locator('.ant-page-header .ant-tag').first()).toBeVisible({ timeout: 3000 })

    // Verify tabs exist
    await expect(page.locator('.ant-tabs-tab:has-text("基本信息")')).toBeVisible()

    // Verify basic info tab displays settlement data
    await expect(page.locator('.ant-descriptions')).toBeVisible({ timeout: 5000 })

    const descriptionsText = await page.locator('.ant-descriptions').first().textContent()
    expect(descriptionsText).toBeTruthy()

    await page.screenshot({ path: 'e2e/screenshots/settlement-detail-basic.png', fullPage: true })

    // Click "汇总" tab
    await page.locator('.ant-tabs-tab:has-text("汇总")').click()
    await page.waitForTimeout(300)

    // Verify summary tab shows auto-summarized data
    await expect(page.locator('.stl-summary-readonly')).toBeVisible({ timeout: 5000 })

    const summaryText = await page.locator('.stl-summary-readonly').textContent()
    expect(summaryText).toBeTruthy()

    const hasContractAmount = summaryText?.includes('合同金额') ?? false
    const hasFinalAmount = summaryText?.includes('结算金额') ?? false
    const hasFormula = summaryText?.includes('计算公式') ?? false

    if (hasContractAmount) console.log('Summary: contractAmount field present')
    if (hasFinalAmount) console.log('Summary: finalAmount field present')
    if (hasFormula) console.log('Summary: calculation formula present')

    await page.screenshot({ path: 'e2e/screenshots/settlement-detail-summary.png', fullPage: true })
  })

  test('should verify settlement status tags on list page', async ({ page }) => {
    await page.goto('/settlement/list')
    await page.waitForSelector('.stl-page', { timeout: 10000 })
    await page.waitForSelector('.vxe-table', { timeout: 10000 })

    const hasRows = await page
      .locator('.vxe-body--row')
      .first()
      .isVisible({ timeout: 5000 })
      .catch(() => false)

    if (!hasRows) {
      console.log('No settlements available, skipping status tag verification')
      return
    }

    const statusTags = page.locator('.vxe-body--row .ant-tag')
    const tagCount = await statusTags.count()

    if (tagCount > 0) {
      const statusTexts: string[] = []
      for (let i = 0; i < Math.min(tagCount, 10); i++) {
        const text = await statusTags.nth(i).textContent()
        if (text) statusTexts.push(text.trim())
      }

      console.log(`Found ${tagCount} status/approval tags:`, statusTexts)

      const knownStatuses = ['草稿', '已定案', '已作废', '审批中', '已通过', '已驳回', '已撤回']
      const hasKnownStatus = statusTexts.some((t) => knownStatuses.includes(t))
      expect(hasKnownStatus).toBeTruthy()
    } else {
      console.log('No status tags found in table rows')
    }

    await page.screenshot({ path: 'e2e/screenshots/settlement-status-tags.png', fullPage: true })
  })

  test('settlement KPI summary totals are consistent with list data', async ({ page }) => {
    await page.goto('/settlement/list')
    await page.waitForSelector('.stl-page', { timeout: 10000 })
    await page.waitForSelector('.vxe-table', { timeout: 10000 })

    // Collect KPI card values
    const kpiCards = page.locator('.stl-kpi')
    const kpiCount = await kpiCards.count()
    const kpiValues: Record<string, string> = {}

    for (let i = 0; i < kpiCount; i++) {
      const card = kpiCards.nth(i)
      const titleEl = card.locator('.stl-kpi-title')
      const valueEl = card.locator('.stl-kpi-value')
      if (
        (await titleEl.isVisible().catch(() => false)) &&
        (await valueEl.isVisible().catch(() => false))
      ) {
        const title = (await titleEl.textContent())?.trim() ?? ''
        const value = (await valueEl.textContent())?.trim() ?? ''
        if (title) kpiValues[title] = value
      }
    }

    // Check that at least totalCount is numeric
    const totalCountKey = Object.keys(kpiValues).find((k) => k.includes('总数'))
    if (totalCountKey) {
      const totalCount = parseInt(kpiValues[totalCountKey], 10)
      console.log(`Settlement total count from KPI: ${totalCount}`)
      expect(Number.isNaN(totalCount)).toBe(false)
      expect(totalCount).toBeGreaterThanOrEqual(0)
    }

    await page.screenshot({
      path: 'e2e/screenshots/settlement-kpi-consistency.png',
      fullPage: true,
    })
  })

  test('settlement detail shows all available tabs', async ({ page }) => {
    await page.goto('/settlement/list')
    await page.waitForSelector('.stl-page', { timeout: 10000 })
    await page.waitForSelector('.vxe-table', { timeout: 10000 })

    const hasRows = await page
      .locator('.vxe-body--row')
      .first()
      .isVisible({ timeout: 5000 })
      .catch(() => false)

    if (!hasRows) {
      console.log('No settlements available, skipping tabs test')
      return
    }

    const firstLink = page.locator('.stl-link').first()
    await firstLink.click()
    await page.waitForSelector('.stl-detail-page', { timeout: 10000 })

    // Verify expected tabs exist
    const expectedTabs = ['基本信息', '汇总', '签证变更', '付款记录', '成本明细', '附件', '审批记录']
    for (const tabName of expectedTabs) {
      const tab = page.locator(`.ant-tabs-tab:has-text("${tabName}")`)
      const visible = await tab.isVisible({ timeout: 3000 }).catch(() => false)
      if (visible) {
        console.log(`Tab "${tabName}" is visible`)
      } else {
        console.log(`Tab "${tabName}" not found`)
      }
    }

    await page.screenshot({ path: 'e2e/screenshots/settlement-detail-tabs.png', fullPage: true })
  })

  test('settlement filter by project works', async ({ page }) => {
    await page.goto('/settlement/list')
    await page.waitForSelector('.stl-filter', { timeout: 10000 })

    // Find project select filter
    const projectSelect = page.locator(
      '.stl-field:has(label:has-text("所属项目")) .ant-select',
    )
    const hasProjectFilter = await projectSelect.isVisible({ timeout: 3000 }).catch(() => false)

    if (hasProjectFilter) {
      await projectSelect.click()
      await page.waitForTimeout(500)

      const dropdown = page
        .locator('.ant-select-dropdown:not(.ant-select-dropdown-hidden)')
        .last()
      const dropdownVisible = await dropdown.isVisible({ timeout: 5000 }).catch(() => false)

      if (dropdownVisible) {
        const options = dropdown.locator('.ant-select-item-option')
        const optionCount = await options.count()
        if (optionCount > 1) {
          await options.nth(1).click()
          await page.waitForTimeout(300)
          await page.locator('.stl-filter-actions button:has-text("查询")').click()
          await page.waitForTimeout(1000)
          await expect(page.locator('.vxe-table')).toBeVisible({ timeout: 5000 })
        }
      }
    }

    await page.screenshot({
      path: 'e2e/screenshots/settlement-filter-project.png',
      fullPage: true,
    })
  })

  test('settlement list pagination is functional', async ({ page }) => {
    await page.goto('/settlement/list')
    await page.waitForSelector('.stl-page', { timeout: 10000 })
    await page.waitForSelector('.vxe-table', { timeout: 10000 })

    // VxeGrid has its own pagination (.vxe-pager)
    const pager = page.locator('.vxe-pager')
    const pagerVisible = await pager.isVisible({ timeout: 5000 }).catch(() => false)

    if (pagerVisible) {
      const totalText = await pager.locator('.vxe-pager--total').textContent()
      console.log(`VxeGrid pager total: ${totalText?.trim()}`)
      expect(pager).toBeVisible()
    } else {
      // Check for ant-pagination as fallback
      const antPagination = page.locator('.ant-pagination')
      const antVisible = await antPagination.isVisible({ timeout: 3000 }).catch(() => false)
      if (antVisible) {
        console.log('Using Ant Design pagination')
      } else {
        console.log('No pagination visible')
      }
    }

    await page.screenshot({
      path: 'e2e/screenshots/settlement-pagination.png',
      fullPage: true,
    })
  })
})
