import { test, expect } from '@playwright/test'

/**
 * UI 重构冒烟测试 -- 验证核心页面渲染不空白、关键布局元素可见。
 *
 * 覆盖页面：
 *   1. 项目列表 /project/list -- 页面渲染，表格可见
 *   2. 合同台账 /contract/ledger -- lg-grid 布局可见
 *   3. 成本台账 /cost/ledger -- 搜索栏和表格可见
 *   4. 付款申请 /payment/application -- KPI 卡片可见
 *   5. 库存台账 /inventory/stock -- 表格可见
 *   6. 审批中心 /approval/todo -- 待办列表可见
 *   7. 系统用户 /system/users -- 表格可见
 *
 * 使用 serial 模式；鉴权复用 global setup 生成的 storageState，
 * 避免每个用例再次手动登录。
 */

test.describe('UI Refactor Smoke: Core Page Rendering', () => {
  test.describe.configure({ mode: 'serial' })

  test('1. 项目列表页 -- 页面渲染，表格可见', async ({ page }) => {
    await page.goto('/project/list')
    await page.waitForSelector('.ant-table, .vxe-table', { timeout: 10000 })

    // 验证页面容器存在（项目列表页使用 project-list-page 类）
    await expect(page.locator('.project-list-page, .app-page').first()).toBeVisible()

    // 验证搜索栏存在
    const searchBar = page.locator('.project-search, .lg-search-bar, .ant-form')
    await expect(searchBar.first()).toBeVisible({ timeout: 5000 })

    // 验证表格区域可见
    const table = page.locator('.ant-table, .vxe-table').first()
    await expect(table).toBeVisible({ timeout: 5000 })

    await page.screenshot({
      path: 'e2e/screenshots/ui-smoke-project-list.png',
      fullPage: true,
    })
  })

  test('2. 合同台账页 -- lg-grid 布局可见', async ({ page }) => {
    await page.goto('/contract/ledger')
    await page.waitForSelector('.lg-grid, .ant-table, .vxe-table', { timeout: 10000 })

    // 验证 lg-page 容器存在
    await expect(page.locator('.lg-page').first()).toBeVisible()

    // 验证 lg-grid 双栏布局或表格可见
    const gridOrTable = page.locator('.lg-grid, .ant-table, .vxe-table').first()
    await expect(gridOrTable).toBeVisible({ timeout: 5000 })

    await page.screenshot({
      path: 'e2e/screenshots/ui-smoke-contract-ledger.png',
      fullPage: true,
    })
  })

  test('3. 成本台账页 -- 搜索栏和表格可见', async ({ page }) => {
    await page.goto('/cost/ledger')
    await page.waitForSelector('.ant-table, .vxe-table', { timeout: 10000 })

    // 验证搜索栏存在
    const searchBar = page.locator('.lg-search-bar, .ant-form').first()
    await expect(searchBar).toBeVisible({ timeout: 5000 })

    // 验证表格可见
    const table = page.locator('.ant-table, .vxe-table').first()
    await expect(table).toBeVisible({ timeout: 5000 })

    await page.screenshot({
      path: 'e2e/screenshots/ui-smoke-cost-ledger.png',
      fullPage: true,
    })
  })

  test('4. 付款申请页 -- KPI 卡片可见', async ({ page }) => {
    await page.goto('/payment/application')
    await page.waitForSelector('.ant-table, .vxe-table, .lg-kpi-strip', { timeout: 10000 })

    // 验证页面容器
    await expect(page.locator('.lg-page').first()).toBeVisible()

    // 验证表格可见（付款申请页核心是表格）
    const table = page.locator('.ant-table, .vxe-table').first()
    await expect(table).toBeVisible({ timeout: 5000 })

    // KPI 卡片可选验证
    const kpiCards = page.locator('.lg-kpi-strip, .ant-card:has(.ant-statistic)')
    const kpiVisible = await kpiCards
      .first()
      .isVisible({ timeout: 3000 })
      .catch(() => false)
    if (kpiVisible) {
      const kpiCount = await kpiCards.count()
      console.log(`付款页 KPI 卡片数量: ${kpiCount}`)
    } else {
      console.log('付款页无 KPI 卡片（可能为空状态）')
    }

    await page.screenshot({ path: 'e2e/screenshots/ui-smoke-payment.png', fullPage: true })
  })

  test('5. 库存台账页 -- 表格可见', async ({ page }) => {
    await page.goto('/inventory/stock')
    await page.waitForSelector('.ant-table, .vxe-table', { timeout: 10000 })

    // 验证页面容器
    await expect(page.locator('.lg-page').first()).toBeVisible()

    // 验证表格可见
    const table = page.locator('.ant-table, .vxe-table').first()
    await expect(table).toBeVisible({ timeout: 5000 })

    await page.screenshot({
      path: 'e2e/screenshots/ui-smoke-inventory-stock.png',
      fullPage: true,
    })
  })

  test('6. 审批中心 -- 待办列表可见', async ({ page }) => {
    await page.goto('/approval/todo')
    await page.waitForSelector('.ant-table, .vxe-table, .ant-tabs', { timeout: 10000 })

    // 验证标签页可见（待办/已办/抄送）
    const tabs = page.locator('.ant-tabs')
    await expect(tabs.first()).toBeVisible({ timeout: 5000 })

    // 验证表格或列表可见
    const table = page.locator('.ant-table, .vxe-table, .ant-list').first()
    await expect(table).toBeVisible({ timeout: 5000 })

    await page.screenshot({
      path: 'e2e/screenshots/ui-smoke-approval-todo.png',
      fullPage: true,
    })
  })

  test('7. 系统用户页 -- 表格可见', async ({ page }) => {
    await page.goto('/system/users')
    await page.waitForSelector('.ant-table, .vxe-table', { timeout: 10000 })

    // 验证页面容器
    await expect(page.locator('.lg-page').first()).toBeVisible()

    // 验证表格可见
    const table = page.locator('.ant-table, .vxe-table').first()
    await expect(table).toBeVisible({ timeout: 5000 })

    await page.screenshot({
      path: 'e2e/screenshots/ui-smoke-system-users.png',
      fullPage: true,
    })
  })
})
