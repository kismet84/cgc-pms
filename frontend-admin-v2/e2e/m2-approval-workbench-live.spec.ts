import AxeBuilder from '@axe-core/playwright'
import { expect, test, type Page } from '@playwright/test'

const runLiveApproval = process.env.V2_LIVE_APPROVAL === '1'
const controlledInstanceId = '520000000000009541'
const roleAccounts = [
  'demo.manager',
  'demo.business',
  'demo.cost',
  'demo.purchase',
  'demo.production',
  'demo.chief',
  'demo.finance',
  'admin',
] as const
const instanceStatuses = ['RUNNING', 'APPROVED', 'REJECTED', 'WITHDRAWN', 'VOIDED'] as const

async function rewritePermissions(page: Page, rewrite: (permissions: string[]) => string[]) {
  await page.route('**/api/auth/userinfo', async (route) => {
    const response = await route.fetch()
    const envelope = (await response.json()) as {
      data: { permissions: string[] }
    }
    await route.fulfill({
      response,
      json: {
        ...envelope,
        data: {
          ...envelope.data,
          permissions: rewrite(envelope.data.permissions),
        },
      },
    })
  })
}

test.describe('M2 live approval workbench', () => {
  test.skip(!runLiveApproval, 'Set V2_LIVE_APPROVAL=1 only against the local test/demo runtime')

  test.beforeEach(async ({ page }) => {
    expect((await page.goto('/api/auth/dev-login?username=admin'))?.ok()).toBe(true)
  })

  test('legacy approval entry and detail deep links reach the V2 workbench', async ({ page }) => {
    await page.goto('/v2/approval?projectId=520000000000009001')
    await expect(page).toHaveURL(/\/v2\/approval\/todo\?projectId=520000000000009001$/)

    await page.goto(`/v2/approval/${controlledInstanceId}?returnTab=todo`)
    await expect(page).toHaveURL(
      new RegExp(`/v2/approval/instances/${controlledInstanceId}\\?returnTab=todo$`),
    )
    await expect(page.getByRole('dialog')).toHaveClass(/v2-dialog-standard/)
  })

  test('all nine M2 ledger routes resolve to accepted V2 pages or redirects', async ({ page }) => {
    const routes = [
      '/v2/dashboard',
      '/v2/dashboard/reports',
      '/v2/alert',
      '/v2/approval',
      '/v2/approval/todo',
      '/v2/approval/done',
      '/v2/approval/cc',
      '/v2/approval/mine',
      `/v2/approval/${controlledInstanceId}`,
    ]

    for (const path of routes) {
      expect((await page.goto(path))?.ok()).toBe(true)
      await expect(page.locator('.shell-placeholder')).toHaveCount(0)
      await expect(page.getByRole('main')).toBeVisible()
    }

    await expect(page).toHaveURL(
      new RegExp(`/v2/approval/instances/${controlledInstanceId}(?:\\?.*)?$`),
    )
  })

  for (const viewport of [
    { name: 'desktop', width: 1440, height: 900 },
    { name: 'tablet', width: 1024, height: 768 },
    { name: 'mobile', width: 390, height: 844 },
  ]) {
    test(`${viewport.name} exposes four scoped lists without page overflow`, async ({ page }) => {
      await page.setViewportSize(viewport)
      const response = page.waitForResponse((item) =>
        item.url().includes('/api/workflow/tasks/todo?pageNo=1&pageSize=20'),
      )
      await page.goto('/v2/approval/todo')
      expect((await response).ok()).toBe(true)
      await expect(page.getByRole('heading', { level: 1, name: '审批工作台' })).toBeAttached()
      await expect(page.getByRole('navigation', { name: '审批列表' })).toHaveCount(0)
      await expect(page.getByRole('heading', { name: '筛选条件', exact: true })).toHaveCount(0)
      const layout = await page.locator('.workflow-page').evaluate((element) => {
        const style = getComputedStyle(element)
        const bounds = element.getBoundingClientRect()
        return {
          spacing: [
            style.paddingTop,
            style.paddingRight,
            style.paddingBottom,
            style.paddingLeft,
            style.rowGap,
          ],
          right: bounds.right,
          bottom: bounds.bottom,
          viewportHeight: window.innerHeight,
        }
      })
      expect(layout.spacing).toEqual(['10px', '10px', '10px', '10px', '10px'])
      expect(layout.right).toBeGreaterThanOrEqual(viewport.width - 1)
      expect(layout.bottom).toBeGreaterThanOrEqual(layout.viewportHeight - 1)
      const shellTabs = page.getByRole('navigation', { name: '工作区标签页' })
      await expect(shellTabs.getByRole('link', { name: '待我处理', exact: true })).toHaveAttribute(
        'aria-current',
        'page',
      )
      if (viewport.name === 'mobile') {
        await expect(page.locator('.workflow-filter__keyword')).toBeHidden()
        await expect(page.locator('.workflow-filter__business-type')).toBeHidden()
        await expect(page.locator('.workflow-filter__search')).toBeHidden()
        const status = page.locator('.workflow-filter__status')
        const reset = page.getByRole('button', { name: '重置', exact: true })
        await expect(status).toBeVisible()
        await expect(reset).toBeVisible()
        const [statusBox, resetBox] = await Promise.all([
          status.locator('.v2-select__trigger').boundingBox(),
          reset.boundingBox(),
        ])
        expect((statusBox?.y ?? 0) + (statusBox?.height ?? 0)).toBeCloseTo(
          (resetBox?.y ?? 0) + (resetBox?.height ?? 0),
          0,
        )
        const filtered = page.waitForResponse((item) => {
          const url = new URL(item.url())
          return (
            url.pathname.endsWith('/api/workflow/tasks/todo') &&
            url.searchParams.get('instanceStatus') === 'RUNNING'
          )
        })
        await status.getByRole('button', { name: /^实例状态：/ }).click()
        await status.getByRole('option', { name: '审批中' }).click()
        expect((await filtered).ok()).toBe(true)
      }
      await shellTabs.getByRole('link', { name: '我发起', exact: true }).click()
      await expect(page).toHaveURL(/\/v2\/approval\/mine$/)
      await expect(page.getByText('审批场景：已驳回', { exact: true })).toBeVisible()
      expect(
        await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth),
      ).toBe(true)

      await page.locator('.workflow-table__title').first().click()
      const responsiveDialog = page.getByRole('dialog')
      await expect(responsiveDialog).toBeVisible()
      const dialogBounds = await responsiveDialog.boundingBox()
      expect(dialogBounds?.width).toBeLessThanOrEqual(viewport.width)
      if (viewport.name === 'mobile') {
        expect(dialogBounds?.y).toBeLessThanOrEqual(1)
        await expect(responsiveDialog.locator('.v2-dialog__title')).toHaveCSS('font-size', '16px')
        await expect(responsiveDialog.locator('.workflow-summary dd').first()).toHaveCSS(
          'font-size',
          '13px',
        )
      }
      await page.keyboard.press('Escape')
      await expect(page).toHaveURL(/\/v2\/approval\/mine$/)
    })
  }

  test('uses sidebar role switcher and Chinese business type filter', async ({ page }) => {
    await page.goto('/v2/approval/todo')
    await expect(page.getByRole('cell', { name: '合同审批', exact: true })).toBeVisible()
    const headers = page.getByRole('columnheader')
    await expect(headers.nth(0)).toHaveText('审批事项')
    await expect(headers.nth(1)).toHaveText('业务编号')
    await expect(page.getByRole('button', { name: '查看', exact: true })).toHaveCount(0)
    await expect(page.locator('.workflow-pagination > span')).toHaveCSS('font-size', '12px')

    await page.getByRole('button', { name: '在建项目临期材料采购合同审批' }).click()
    await expect(page).toHaveURL(/\/v2\/approval\/instances\/520000000000009541/)
    const detailDialog = page.getByRole('dialog', { name: '在建项目临期材料采购合同审批' })
    await expect(detailDialog).toBeVisible()
    await expect(detailDialog).toHaveCSS('backdrop-filter', /blur\([^)]+\)/)
    await page.locator('.v2-dialog__backdrop').click({ position: { x: 4, y: 4 } })
    await expect(page).toHaveURL(/\/v2\/approval\/todo$/)
    await expect(detailDialog).toHaveCount(0)
    await expect(page.getByRole('cell', { name: '合同审批', exact: true })).toBeVisible()

    await expect(page.locator('#global-project')).toHaveAttribute('aria-disabled', 'true')
    const periodControl = page.locator('#global-report-period')
    await periodControl.click()
    const periodMenu = page.getByRole('listbox', { name: '报告期' })
    const periodControlBox = await periodControl.boundingBox()
    const periodMenuBox = await periodMenu.boundingBox()
    expect(periodMenuBox?.x).toBeCloseTo(periodControlBox?.x ?? 0, 0)
    const period = await periodMenu
      .locator('[role="option"][data-value]:not([data-value=""])')
      .first()
    const periodValue = await period.getAttribute('data-value')
    expect(periodValue).toMatch(/^\d{4}-\d{2}$/)
    const [, year, month] = /^(\d{4})-(\d{2})$/.exec(periodValue!)!
    const lastDay = new Date(Date.UTC(Number(year), Number(month), 0)).getUTCDate()
    const filteredByPeriod = page.waitForResponse((item) => {
      const url = new URL(item.url())
      return (
        url.pathname === '/api/workflow/tasks/todo' &&
        url.searchParams.get('startTime') === `${periodValue}-01T00:00:00` &&
        url.searchParams.get('endTime') ===
          `${periodValue}-${String(lastDay).padStart(2, '0')}T23:59:59`
      )
    })
    await period.click()
    expect((await filteredByPeriod).ok()).toBe(true)

    const businessType = page.getByRole('button', { name: /^业务类型：/ })
    await businessType.click()
    const businessTypeOptions = page.getByRole('listbox', { name: '业务类型' })
    const controlBox = await businessType.boundingBox()
    const menuBox = await businessTypeOptions.boundingBox()
    expect(menuBox?.x).toBeCloseTo(controlBox?.x ?? 0, 0)
    await expect(businessTypeOptions.getByRole('option').first()).toHaveCSS('font-size', '12px')
    await businessTypeOptions.getByRole('option', { name: '合同审批' }).click()
    await expect(businessType).toContainText('合同审批')

    const instanceStatus = page.getByRole('button', { name: /^实例状态：/ })
    await instanceStatus.click()
    const statusOptions = page.getByRole('listbox', { name: '实例状态' })
    const statusControlBox = await instanceStatus.boundingBox()
    const statusMenuBox = await statusOptions.boundingBox()
    expect(statusMenuBox?.x).toBeCloseTo(statusControlBox?.x ?? 0, 0)
    await expect(statusOptions.getByRole('option', { name: '已作废' })).toBeVisible()
    await statusOptions.getByRole('option', { name: '审批中' }).click()
    const filtered = page.waitForResponse((item) => {
      const url = new URL(item.url())
      return (
        url.searchParams.get('businessType') === 'CONTRACT_APPROVAL' &&
        url.searchParams.get('instanceStatus') === 'RUNNING'
      )
    })
    await page.getByRole('button', { name: '查询', exact: true }).click()
    expect((await filtered).ok()).toBe(true)

    const sidebar = page.locator('#shell-navigation')
    await sidebar.getByRole('button', { name: '切换角色测试账号' }).click()
    await expect(sidebar.getByRole('region', { name: '角色测试账号' })).toBeVisible()
    const switched = page.waitForResponse((item) =>
      item.url().includes('/api/auth/dev-login?username=demo.cost'),
    )
    await sidebar.getByRole('button', { name: /成本经理/ }).click()
    expect((await switched).ok()).toBe(true)
    await expect(page).toHaveURL(/\/v2\/approval\/todo/)
    await expect(page.getByRole('banner').getByText('演示成本经理', { exact: true })).toBeVisible()
  })

  test('eight roles expose all five instance states and scoped lists', async ({ page }) => {
    for (const username of roleAccounts) {
      expect((await page.goto(`/api/auth/dev-login?username=${username}`))?.ok()).toBe(true)
      await page.goto('/v2/approval/todo')
      await page.locator('.workflow-table__title').first().click()
      await expect(page.getByRole('dialog').getByLabel('审批动作')).toBeVisible()
      for (const status of instanceStatuses) {
        const response = await page.request.get(
          `/api/workflow/instances/mine?pageNo=1&pageSize=20&instanceStatus=${status}`,
        )
        expect(response.ok(), `${username} ${status}`).toBe(true)
        const payload = (await response.json()) as { data: { total: string | number } }
        expect(Number(payload.data.total), `${username} ${status}`).toBeGreaterThanOrEqual(1)
      }
      for (const endpoint of ['tasks/todo', 'tasks/done', 'tasks/cc'] as const) {
        const response = await page.request.get(`/api/workflow/${endpoint}?pageNo=1&pageSize=20`)
        expect(response.ok(), `${username} ${endpoint}`).toBe(true)
        const payload = (await response.json()) as { data: { total: string | number } }
        expect(Number(payload.data.total), `${username} ${endpoint}`).toBeGreaterThanOrEqual(1)
      }
    }
  })

  test('shows completed action statuses in Chinese', async ({ page }) => {
    await page.goto('/v2/approval/done')
    await expect(page.getByRole('cell', { name: '已同意', exact: true }).first()).toBeVisible()
    await expect(page.getByRole('cell', { name: '已驳回', exact: true }).first()).toBeVisible()
    await expect(page.getByRole('cell', { name: /APPROVE|REJECT/ })).toHaveCount(0)
  })

  test('deep link reads server actions but does not execute them during verification', async ({
    page,
  }) => {
    const response = page.waitForResponse((item) =>
      item.url().endsWith(`/api/workflow/instances/${controlledInstanceId}`),
    )
    await page.goto(`/v2/approval/instances/${controlledInstanceId}?returnTab=todo`)
    expect((await response).ok()).toBe(true)
    await expect(page.getByRole('heading', { name: '在建项目临期材料采购合同审批' })).toBeVisible()
    await expect(page.getByRole('button', { name: '同意', exact: true })).toBeVisible()
    await expect(page.getByRole('button', { name: '驳回', exact: true })).toBeVisible()
    await expect(page.getByRole('button', { name: '撤回', exact: true })).toBeVisible()
    await expect(page.getByRole('button', { name: '转办', exact: true })).toBeVisible()
    await expect(page.getByRole('button', { name: '加签', exact: true })).toBeVisible()

    const accessibility = await new AxeBuilder({ page }).include('.workflow-page').analyze()
    expect(accessibility.violations).toEqual([])
  })

  test('unknown instance fails closed', async ({ page }) => {
    await page.goto('/v2/approval/instances/999999999999999999?returnTab=todo')
    await expect(page.getByRole('heading', { name: '无法显示审批详情' })).toBeVisible()
  })

  test('process management remains unmigrated', async ({ page }) => {
    await rewritePermissions(page, (permissions) => [...permissions, 'workflow:process:query'])
    await page.goto('/v2/approval/process')
    await expect(page.getByText('业务页面建设中', { exact: true })).toBeVisible()
  })

  test('server actions remain hidden when client action permission is absent', async ({ page }) => {
    await rewritePermissions(page, (permissions) =>
      permissions.filter((permission) => !permission.startsWith('workflow:')),
    )
    await page.goto(`/v2/approval/instances/${controlledInstanceId}`)
    await expect(page.getByRole('heading', { name: '在建项目临期材料采购合同审批' })).toBeVisible()
    await expect(page.getByRole('button', { name: '同意', exact: true })).toHaveCount(0)
    await expect(page.getByRole('button', { name: '驳回', exact: true })).toHaveCount(0)
  })
})
