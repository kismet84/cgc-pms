import AxeBuilder from '@axe-core/playwright'
import { expect, test, type Page, type Route } from '@playwright/test'
import { captureRuntimeErrors } from './runtime-errors'

const user = {
  userId: '1',
  username: 'cost.manager',
  realName: '成本经理',
  roles: ['USER'],
  permissions: [
    'cost:ledger:query',
    'cost:summary:view',
    'cost:summary:refresh',
    'cost:control:query',
    'cost:forecast:maintain',
    'cost:forecast:confirm',
    'cost:corrective:maintain',
    'cost:corrective:submit',
  ],
}
const deniedUser = {
  userId: '2',
  username: 'no.cost',
  realName: '无成本权限用户',
  roles: ['USER'],
  permissions: [],
}
const ledger = {
  id: '9007199254740993',
  projectId: 'P1',
  projectName: '项目一',
  costSubjectName: '材料费',
  amount: '9007199254740993.12',
  taxAmount: '0',
  amountWithoutTax: '9007199254740993.12',
  costType: 'DIRECT',
  sourceType: 'MAT_RECEIPT',
  costStatus: 'CONFIRMED',
  costDate: '2026-07-23',
}
const summary = {
  projectId: 'P1',
  projectName: '项目一',
  targetCost: '9007199254740993.12',
  contractLockedCost: '0',
  actualCost: '-0.01',
  paidAmount: '0',
  estimatedRemainingCost: '0.10',
  dynamicCost: '9007199254740993.11',
  contractIncome: '0',
  confirmedRevenue: '0',
  expectedProfit: '-0.01',
  costDeviation: '0',
  responsibilityCost: '0',
  forecastAtCompletionCost: '0',
  forecastProfit: '-0.01',
  profitMargin: '0',
  subjects: [],
}
const latestForecast = {
  id: 'F1',
  forecast_code: 'FC-01',
  forecast_name: '七月预测',
  forecast_at_completion_amount: '9007199254740993.12',
  forecast_profit_amount: '-0.01',
  cost_variance_amount: '0.01',
  status: 'DRAFT',
  version: '7',
}

async function fulfill(route: Route, data: unknown, status = 200) {
  await route.fulfill({
    status,
    contentType: 'application/json',
    body: JSON.stringify({
      code: status === 200 ? '0' : 'TEST_ERROR',
      message: status === 200 ? 'success' : '服务异常',
      data,
    }),
  })
}
async function install(
  page: Page,
  writes: string[],
  requests: string[] = [],
  identity: typeof user | typeof deniedUser = user,
) {
  await page.route('**/api/auth/userinfo', (route) => fulfill(route, identity))
  await page.route('**/api/auth/refresh', (route) => fulfill(route, null, 401))
  await page.route('**/api/project-context/options', (route) =>
    fulfill(route, [{ id: 'P1', projectName: '项目一', status: 'ACTIVE' }]),
  )
  await page.route('**/api/cost-ledger**', (route) => {
    requests.push(route.request().url())
    const url = new URL(route.request().url())
    if (url.pathname.endsWith('/summary'))
      return fulfill(route, {
        totalAmount: '9007199254740993.12',
        totalTaxAmount: '0',
        bySourceType: { MAT_RECEIPT: '9007199254740993.12' },
        byProject: { 项目一: '9007199254740993.12' },
        byCostType: { DIRECT: '9007199254740993.12' },
      })
    if (/\/cost-ledger\/9007199254740993$/.test(url.pathname)) return fulfill(route, ledger)
    return fulfill(route, { records: [ledger], total: 1, pageNo: 1, pageSize: 20 })
  })
  await page.route('**/api/cost-summary/**', (route) => {
    requests.push(route.request().url())
    if (route.request().method() === 'POST') writes.push(route.request().url())
    if (route.request().url().endsWith('/history'))
      return fulfill(route, [
        {
          ...summary,
          id: 'S1',
          tenantId: 'T1',
          summaryDate: '2026-07-23',
          costSubjectId: 'C1',
          costSubjectName: '材料费',
        },
      ])
    return fulfill(route, summary)
  })
  await page.route('**/api/cost-controls/**', (route) => {
    requests.push(route.request().url())
    const request = route.request()
    if (request.method() !== 'GET') writes.push(request.url())
    if (request.url().endsWith('/overview'))
      return fulfill(route, {
        project: { id: 'P1' },
        activeTarget: {},
        targetItems: [],
        forecastInputItems: [{ cost_subject_id: 'C1', recommended_remaining_amount: '0.10' }],
        latestForecast,
        forecastItems: [],
        correctiveActions: [],
        forecastHistory: [latestForecast],
        costSources: [],
        summary: {},
      })
    return fulfill(route, latestForecast)
  })
}

test.describe('M4 costs routes', () => {
  test('returns explicit 403 for every cost route without cost permissions', async ({ page }) => {
    const writes: string[] = []
    const requests: string[] = []
    await install(page, writes, requests, deniedUser)
    for (const path of ['/cost/ledger', '/cost/summary', '/cost/control']) {
      await page.goto(`/v2${path}?projectId=P1&period=2026-07`)
      await expect(page).toHaveURL(/\/v2\/forbidden\?from=/)
      await expect(page.getByText('403', { exact: true })).toBeVisible()
      await expect(page.getByRole('heading', { name: '无权访问此页面' })).toBeVisible()
    }
    expect(writes).toEqual([])
    expect(requests).toEqual([])
  })

  test('redirects /cost and renders ledger at three viewports without placeholders', async ({
    page,
  }) => {
    const writes: string[] = []
    await install(page, writes)
    const errors = captureRuntimeErrors(page)
    await page.goto('/v2/cost?projectId=P1&period=2026-07#items')
    await expect(page).toHaveURL(/\/v2\/cost\/ledger\?projectId=P1&period=2026-07#items$/)
    for (const viewport of [
      { width: 1440, height: 900 },
      { width: 1024, height: 768 },
      { width: 390, height: 844 },
    ]) {
      await page.setViewportSize(viewport)
      await page.goto('/v2/cost/ledger?projectId=P1&period=2026-07')
      await expect(page.getByRole('heading', { name: '成本台账', exact: true })).toBeVisible()
      await expect(page.getByText('9007199254740993.12').first()).toBeVisible()
      await expect(page.getByRole('button', { name: '查询' })).toHaveAttribute('aria-busy', 'false')
      await expect(page.locator('.v2-card__body dl').first()).toHaveCSS('font-size', '12px')
      await expect(page.getByRole('navigation', { name: '成本台账分页' })).toHaveCSS(
        'font-size',
        '12px',
      )
      await expect(page.locator('.shell-placeholder')).toHaveCount(0)
      expect(
        await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth),
      ).toBe(true)
      const axe = await new AxeBuilder({ page }).include('.cost-page').analyze()
      expect(
        axe.violations.filter((v) => ['serious', 'critical'].includes(v.impact ?? '')),
      ).toEqual([])
    }
    await page.getByRole('button', { name: '详情' }).click()
    const detailDialog = page.getByRole('dialog')
    await expect(detailDialog).toHaveClass(/v2-detail-dialog/)
    await expect(detailDialog.locator('.v2-detail-dialog__facts')).toHaveCSS('font-size', '12px')
    await expect(detailDialog).toContainText('9007199254740993')
    expect(writes).toEqual([])
    expect(errors).toEqual([])
  })

  test('shows summary decimals and performs one authorized refresh', async ({ page }) => {
    const writes: string[] = []
    const requests: string[] = []
    await install(page, writes, requests)
    for (const viewport of [
      { width: 1440, height: 900 },
      { width: 1024, height: 768 },
      { width: 390, height: 844 },
    ]) {
      await page.setViewportSize(viewport)
      await page.goto('/v2/cost/summary?projectId=P1&period=2026-07')
      await expect(page.getByText('-0.01').first()).toBeVisible()
      expect(
        await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth),
      ).toBe(true)
      const axe = await new AxeBuilder({ page }).include('.cost-page').analyze()
      expect(
        axe.violations.filter((v) => ['serious', 'critical'].includes(v.impact ?? '')),
      ).toEqual([])
    }
    await page.route('**/api/cost-summary/P1/refresh', (route) => fulfill(route, null, 409))
    const readsBefore = requests.filter((url) =>
      new URL(url).pathname.endsWith('/cost-summary/P1'),
    ).length
    await page.getByRole('button', { name: '刷新汇总' }).dblclick()
    await expect(page.locator('#shell-main-content').getByText('服务异常')).toBeVisible()
    expect(
      requests.filter((url) => new URL(url).pathname.endsWith('/cost-summary/P1')).length,
    ).toBeGreaterThan(readsBefore)
  })

  test('loads control, preserves CAS on repeated confirm and exposes forecast form', async ({
    page,
  }) => {
    const writes: string[] = []
    await install(page, writes)
    for (const viewport of [
      { width: 1440, height: 900 },
      { width: 1024, height: 768 },
      { width: 390, height: 844 },
    ]) {
      await page.setViewportSize(viewport)
      await page.goto('/v2/cost/control?projectId=P1&period=2026-07')
      await expect(page.getByText('9007199254740993.12').first()).toBeVisible()
      expect(
        await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth),
      ).toBe(true)
      const axe = await new AxeBuilder({ page }).include('.cost-page').analyze()
      expect(
        axe.violations.filter((v) => ['serious', 'critical'].includes(v.impact ?? '')),
      ).toEqual([])
    }
    await page.getByRole('button', { name: '确认预测' }).dblclick()
    await expect(page.getByText('完工预测已确认')).toBeVisible()
    expect(writes.filter((url) => url.includes('/forecasts/F1/confirm?version=7'))).toHaveLength(1)
    await page.getByRole('button', { name: '编辑预测' }).click()
    await expect(page.getByRole('dialog')).toContainText('预计剩余成本')
  })
})
