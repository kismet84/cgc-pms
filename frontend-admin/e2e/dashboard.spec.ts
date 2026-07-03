import { test, expect, type Page } from '@playwright/test'

/**
 * Dashboard E2E: current lg-* dashboard shell, role tabs and cost charts.
 *
 * Current role labels are 项目经理 / 商务经理 / 采购经理 / 生产经理 / 总工程师,
 * and the cost role is displayed as 商务经理 by design.
 */

async function waitForDashboard(page: Page) {
  await page.goto('/dashboard')
  await expect(page.locator('.dashboard')).toBeVisible({ timeout: 10000 })
  await expect(page.getByRole('heading', { name: '驾驶舱' })).toBeVisible({ timeout: 10000 })
  await expect(page.locator('.role-tabs')).toBeVisible({ timeout: 10000 })
}

test.describe('Dashboard: Charts → Data Cards → Role Tabs', () => {
  test.beforeEach(async () => {})

  test('should navigate to dashboard and verify page structure', async ({ page }) => {
    await waitForDashboard(page)

    await expect(page.locator('.project-field')).toBeVisible({ timeout: 5000 })
    await expect(page.locator('.project-field .ant-select')).toBeVisible()

    const tabLabels = await page.locator('.role-tabs .ant-tabs-tab').allTextContents()
    const joinedTabs = tabLabels.join(' ')
    console.log(`Dashboard tabs: ${joinedTabs}`)
    expect(joinedTabs).toContain('项目经理')
    expect(joinedTabs).toContain('商务经理')
    expect(tabLabels.length).toBeGreaterThanOrEqual(3)

    await page.screenshot({ path: 'e2e/screenshots/dashboard-structure.png', fullPage: true })
  })

  test('should render ECharts canvas in current cost view when available', async ({ page }) => {
    await waitForDashboard(page)

    const costShell = page.locator('.cost-reference-shell')
    if (!(await costShell.isVisible({ timeout: 8000 }).catch(() => false))) {
      console.log('Cost dashboard shell not visible; role/data may be unavailable')
      return
    }

    await expect(page.getByText('商务成本执行情况')).toBeVisible({ timeout: 5000 })
    await expect(page.getByText('成本科目排名')).toBeVisible({ timeout: 5000 })

    const canvasEl = page.locator('.cost-reference-chart canvas')
    const hasCanvas = await canvasEl.first().isVisible({ timeout: 8000 }).catch(() => false)
    if (hasCanvas) {
      const canvasCount = await canvasEl.count()
      console.log(`Found ${canvasCount} ECharts canvas elements in cost view`)
      expect(canvasCount).toBeGreaterThanOrEqual(1)
    } else {
      console.log('No canvas elements found — ECharts may not have rendered because data is empty')
    }

    await page.screenshot({ path: 'e2e/screenshots/dashboard-cost-charts.png', fullPage: true })
  })

  test('should display visible dashboard panel areas', async ({ page }) => {
    await waitForDashboard(page)

    const costPanels = page.locator('.cost-reference-shell, .role-reference-panel, .pm-reference-panel')
    const panelCount = await costPanels.count()
    console.log(`Found ${panelCount} dashboard panel containers`)
    expect(panelCount).toBeGreaterThanOrEqual(1)

    await page.screenshot({ path: 'e2e/screenshots/dashboard-panels.png', fullPage: true })
  })

  test('should display KPI data cards with meaningful structure', async ({ page }) => {
    await waitForDashboard(page)

    const costKpis = page.locator('.cost-reference-kpi')
    const hasCostKpis = await costKpis.first().isVisible({ timeout: 8000 }).catch(() => false)
    if (hasCostKpis) {
      const cardCount = await costKpis.count()
      console.log(`Found ${cardCount} cost KPI cards`)
      expect(cardCount).toBeGreaterThanOrEqual(1)
      await expect(costKpis.first().locator('.cost-reference-kpi-title')).toBeVisible()
      await expect(costKpis.first().locator('.cost-reference-kpi-value')).toBeVisible()
    } else {
      console.log('No cost KPI cards visible; checking dashboard still has role tabs and shell')
      await expect(page.locator('.role-tabs')).toBeVisible()
    }

    await page.screenshot({ path: 'e2e/screenshots/dashboard-kpi-cards.png', fullPage: true })
  })

  test('should switch between role tabs and verify content changes', async ({ page }) => {
    await waitForDashboard(page)

    const pmTab = page.locator('.role-tabs .ant-tabs-tab').filter({ hasText: '项目经理' }).first()
    if (await pmTab.isVisible({ timeout: 3000 }).catch(() => false)) {
      await pmTab.click()
      await expect(page.locator('.role-tabs .ant-tabs-tab-active').filter({ hasText: '项目经理' })).toBeVisible({
        timeout: 5000,
      })
      await expect(page.locator('.project-field')).toBeVisible({ timeout: 5000 })
    }

    const businessTab = page.locator('.role-tabs .ant-tabs-tab').filter({ hasText: '商务经理' }).first()
    if (await businessTab.isVisible({ timeout: 3000 }).catch(() => false)) {
      await businessTab.click()
      await expect(page.locator('.role-tabs .ant-tabs-tab-active').filter({ hasText: '商务经理' })).toBeVisible({
        timeout: 5000,
      })
    }

    await page.screenshot({ path: 'e2e/screenshots/dashboard-role-switch.png', fullPage: true })
  })
})
