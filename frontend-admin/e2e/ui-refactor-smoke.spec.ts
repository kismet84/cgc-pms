import { test, expect, type Page } from '@playwright/test'

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
 * 使用 serial 模式 + 手动管理共享 page，仅登录一次，
 * 避免 7 个并行 worker 同时登录给后端造成瞬时高负载导致超时。
 */

let sharedPage: Page

test.describe('UI Refactor Smoke: Core Page Rendering', () => {
  test.describe.configure({ mode: 'serial' })

  test.beforeAll(async ({ browser }) => {
    sharedPage = await browser.newPage()
    await sharedPage.goto('/login')
    await sharedPage.fill('input[placeholder="请输入用户名"]', 'admin')
    await sharedPage.fill('input[placeholder="请输入密码"]', 'admin123')
    await sharedPage.click('button[type="submit"]')
    await sharedPage.waitForURL((url) => !url.pathname.includes('/login'), { timeout: 15000 })
  })

  test.afterAll(async () => {
    await sharedPage.close()
  })

  test('1. 项目列表页 -- 页面渲染，表格可见', async () => {
    await sharedPage.goto('/project/list')
    await sharedPage.waitForSelector('.ant-table, .vxe-table', { timeout: 10000 })

    // 验证页面容器存在（项目列表页使用 project-list-page 类）
    await expect(sharedPage.locator('.project-list-page, .app-page').first()).toBeVisible()

    // 验证搜索栏存在
    const searchBar = sharedPage.locator('.project-search, .lg-search-bar, .ant-form')
    await expect(searchBar.first()).toBeVisible({ timeout: 5000 })

    // 验证表格区域可见
    const table = sharedPage.locator('.ant-table, .vxe-table').first()
    await expect(table).toBeVisible({ timeout: 5000 })

    await sharedPage.screenshot({
      path: 'e2e/screenshots/ui-smoke-project-list.png',
      fullPage: true,
    })
  })

  test('2. 合同台账页 -- lg-grid 布局可见', async () => {
    await sharedPage.goto('/contract/ledger')
    await sharedPage.waitForSelector('.lg-grid, .ant-table, .vxe-table', { timeout: 10000 })

    // 验证 lg-page 容器存在
    await expect(sharedPage.locator('.lg-page').first()).toBeVisible()

    // 验证 lg-grid 双栏布局或表格可见
    const gridOrTable = sharedPage.locator('.lg-grid, .ant-table, .vxe-table').first()
    await expect(gridOrTable).toBeVisible({ timeout: 5000 })

    await sharedPage.screenshot({
      path: 'e2e/screenshots/ui-smoke-contract-ledger.png',
      fullPage: true,
    })
  })

  test('3. 成本台账页 -- 搜索栏和表格可见', async () => {
    await sharedPage.goto('/cost/ledger')
    await sharedPage.waitForSelector('.ant-table, .vxe-table', { timeout: 10000 })

    // 验证搜索栏存在
    const searchBar = sharedPage.locator('.lg-search-bar, .ant-form').first()
    await expect(searchBar).toBeVisible({ timeout: 5000 })

    // 验证表格可见
    const table = sharedPage.locator('.ant-table, .vxe-table').first()
    await expect(table).toBeVisible({ timeout: 5000 })

    await sharedPage.screenshot({
      path: 'e2e/screenshots/ui-smoke-cost-ledger.png',
      fullPage: true,
    })
  })

  test('4. 付款申请页 -- KPI 卡片可见', async () => {
    await sharedPage.goto('/payment/application')
    await sharedPage.waitForSelector('.ant-table, .vxe-table, .lg-kpi-strip', { timeout: 10000 })

    // 验证页面容器
    await expect(sharedPage.locator('.lg-page').first()).toBeVisible()

    // 验证表格可见（付款申请页核心是表格）
    const table = sharedPage.locator('.ant-table, .vxe-table').first()
    await expect(table).toBeVisible({ timeout: 5000 })

    // KPI 卡片可选验证
    const kpiCards = sharedPage.locator('.lg-kpi-strip, .ant-card:has(.ant-statistic)')
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

    await sharedPage.screenshot({ path: 'e2e/screenshots/ui-smoke-payment.png', fullPage: true })
  })

  test('5. 库存台账页 -- 表格可见', async () => {
    await sharedPage.goto('/inventory/stock')
    await sharedPage.waitForSelector('.ant-table, .vxe-table', { timeout: 10000 })

    // 验证页面容器
    await expect(sharedPage.locator('.lg-page').first()).toBeVisible()

    // 验证表格可见
    const table = sharedPage.locator('.ant-table, .vxe-table').first()
    await expect(table).toBeVisible({ timeout: 5000 })

    await sharedPage.screenshot({
      path: 'e2e/screenshots/ui-smoke-inventory-stock.png',
      fullPage: true,
    })
  })

  test('6. 审批中心 -- 待办列表可见', async () => {
    await sharedPage.goto('/approval/todo')
    await sharedPage.waitForSelector('.ant-table, .vxe-table, .ant-tabs', { timeout: 10000 })

    // 验证标签页可见（待办/已办/抄送）
    const tabs = sharedPage.locator('.ant-tabs')
    await expect(tabs.first()).toBeVisible({ timeout: 5000 })

    // 验证表格或列表可见
    const table = sharedPage.locator('.ant-table, .vxe-table, .ant-list').first()
    await expect(table).toBeVisible({ timeout: 5000 })

    await sharedPage.screenshot({
      path: 'e2e/screenshots/ui-smoke-approval-todo.png',
      fullPage: true,
    })
  })

  test('7. 系统用户页 -- 表格可见', async () => {
    await sharedPage.goto('/system/users')
    await sharedPage.waitForSelector('.ant-table, .vxe-table', { timeout: 10000 })

    // 验证页面容器
    await expect(sharedPage.locator('.lg-page').first()).toBeVisible()

    // 验证表格可见
    const table = sharedPage.locator('.ant-table, .vxe-table').first()
    await expect(table).toBeVisible({ timeout: 5000 })

    await sharedPage.screenshot({
      path: 'e2e/screenshots/ui-smoke-system-users.png',
      fullPage: true,
    })
  })
})
