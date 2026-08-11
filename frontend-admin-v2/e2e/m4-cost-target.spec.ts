import AxeBuilder from '@axe-core/playwright'
import { expect, test, type Page, type Request } from '@playwright/test'
import { captureRuntimeErrors } from './runtime-errors'
import { installShellPreferencesMock } from './shell-session'

type Identity = 'business' | 'readonly' | 'denied'

const users = {
  business: {
    tenantId: '0',
    userId: '1',
    username: 'cost.manager',
    realName: '目标成本经理',
    roles: ['COST_MANAGER'],
    permissions: [
      'cost:target:query',
      'cost:target:add',
      'cost:target:edit',
      'cost:target:delete',
      'cost:target:submit',
      'cost:target:activate',
    ],
  },
  readonly: {
    tenantId: '0',
    userId: '2',
    username: 'cost.viewer',
    realName: '目标成本查看人',
    roles: ['USER'],
    permissions: ['cost:target:query'],
  },
  denied: {
    tenantId: '0',
    userId: '3',
    username: 'no.cost',
    realName: '无权限用户',
    roles: ['USER'],
    permissions: [],
  },
} as const

const target = {
  id: '81',
  projectId: 'P1',
  versionNo: 'V1',
  versionName: '首版目标成本',
  totalTargetAmount: '9007199254740993.12',
  totalBidCostAmount: '8800000000000000.10',
  totalResponsibilityAmount: '9007199254740993.12',
  isActive: 0,
  approvalStatus: 'DRAFT',
  status: 'DRAFT',
  version: '7',
  remark: '待分解',
}

const item = {
  id: '91',
  targetId: '81',
  projectId: 'P1',
  costSubjectId: 'S1',
  targetAmount: '9007199254740993.12',
  bidCostAmount: '8800000000000000.10',
  responsibilityAmount: '9007199254740993.12',
}

interface CostTargetMockOptions {
  readTarget?: () => typeof target
  onCostRequest?: (
    request: Request,
  ) => { status: number; code?: string; message: string; data?: unknown } | undefined
}

async function installCostTargetMock(
  page: Page,
  readIdentity: () => Identity,
  options: CostTargetMockOptions = {},
): Promise<void> {
  await installShellPreferencesMock(page)
  await page.route('**/api/auth/userinfo', (route) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ code: '0', message: 'success', data: users[readIdentity()] }),
    }),
  )
  await page.route('**/api/auth/refresh', (route) =>
    route.fulfill({
      status: 401,
      contentType: 'application/json',
      body: JSON.stringify({ code: 'AUTH_TOKEN_INVALID', message: 'unauthorized', data: null }),
    }),
  )
  await page.route('**/api/project-context/options', (route) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        code: '0',
        message: 'success',
        data: [{ id: 'P1', projectName: '项目一', projectCode: 'P-001', status: 'ACTIVE' }],
      }),
    }),
  )
  await page.route(/\/api\/system\/users(?:\?.*)?$/, (route) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        code: '0',
        message: 'success',
        data: {
          records: [
            { id: '1', username: 'cost.manager', realName: '目标成本经理', status: 'ENABLE' },
          ],
          total: 1,
          pageNo: 1,
          pageSize: 200,
        },
      }),
    }),
  )
  await page.route('**/api/cost-subjects**', (route) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        code: '0',
        message: 'success',
        data: [{ id: 'S1', subjectCode: 'COST-001', subjectName: '直接成本', status: 'ENABLE' }],
      }),
    }),
  )
  await page.route('**/api/cost-targets**', async (route) => {
    const request = route.request()
    const url = new URL(request.url())
    const override = options.onCostRequest?.(request)
    if (override) {
      await route.fulfill({
        status: override.status,
        contentType: 'application/json',
        body: JSON.stringify({
          code: override.code ?? 'TEST_ERROR',
          message: override.message,
          data: override.data ?? null,
        }),
      })
      return
    }
    const currentTarget = options.readTarget?.() ?? target
    let data: unknown = null
    if (request.method() === 'GET' && /\/cost-targets\/project-manager-options$/.test(url.pathname))
      data = [
        {
          id: '1',
          username: 'cost.manager',
          realName: '目标成本经理',
          eligible: true,
        },
      ]
    else if (request.method() === 'GET' && /\/cost-targets\/81\/items$/.test(url.pathname))
      data = [item]
    else if (request.method() === 'GET' && /\/cost-targets\/81$/.test(url.pathname))
      data = currentTarget
    else if (request.method() === 'GET' && /\/cost-targets$/.test(url.pathname)) {
      data = { records: [currentTarget], total: 1, pageNo: 1, pageSize: 20 }
    } else if (request.method() === 'POST' && /\/cost-targets$/.test(url.pathname)) data = '81'
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ code: '0', message: 'success', data }),
    })
  })
}

test.describe('M4 cost target routes', () => {
  test('redirects root and renders list/edit without placeholders at three viewports', async ({
    page,
  }) => {
    await installCostTargetMock(page, () => 'business')
    const runtimeErrors = captureRuntimeErrors(page)

    await page.goto('/cost-target?projectId=P1#versions')
    await expect(page).toHaveURL(/\/cost-budget\?projectId=P1#versions$/)

    for (const viewport of [
      { width: 1440, height: 900 },
      { width: 1024, height: 768 },
      { width: 390, height: 844 },
    ]) {
      await page.setViewportSize(viewport)
      await page.goto('/cost-target/index?projectId=P1')
      await expect(page.getByRole('heading', { name: '项目成本预算', exact: true })).toBeVisible()
      await expect(page.getByText('首版目标成本', { exact: true })).toBeVisible()
      await expect(page.getByText('¥9,007,199,254,740,993.12').first()).toBeVisible()
      expect(
        await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth),
      ).toBe(true)
      const axe = await new AxeBuilder({ page }).include('.cost-target-page').analyze()
      expect(
        axe.violations.filter((violation) =>
          ['serious', 'critical'].includes(violation.impact ?? ''),
        ),
      ).toEqual([])
    }

    await page.goto('/cost-target/81/edit?projectId=P1')
    await expect(page.getByRole('heading', { name: '编辑项目成本预算' })).toBeVisible()
    await expect(page.locator('input[aria-label="目标成本合计（服务端）"]')).toHaveValue(
      '9007199254740993.12',
    )
    await expect(page.getByText('成本预算明细', { exact: true })).toBeVisible()
    expect(runtimeErrors).toEqual([])
  })

  test('query-only identity sees no writes and denied identity fails closed', async ({
    page,
    browser,
  }) => {
    await installCostTargetMock(page, () => 'readonly')
    await page.goto('/cost-target/index')
    await expect(page.getByRole('button', { name: '新建版本' })).toHaveCount(0)
    await expect(page.getByRole('button', { name: '编辑' })).toHaveCount(0)
    await expect(page.getByRole('button', { name: '提交' })).toHaveCount(0)
    await expect(page.getByRole('button', { name: '删除' })).toHaveCount(0)

    const denied = await browser.newPage()
    await installCostTargetMock(denied, () => 'denied')
    await denied.goto('/cost-target/index')
    await expect(denied).toHaveURL(/\/forbidden\?from=/)
    await denied.close()
  })
})
