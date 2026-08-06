import AxeBuilder from '@axe-core/playwright'
import { expect, test, type Page } from '@playwright/test'
import { captureRuntimeErrors } from './runtime-errors'
import { installShellPreferencesMock } from './shell-session'

type Identity = 'business' | 'readonly' | 'denied'

const users = {
  business: {
    tenantId: '0',
    userId: '1',
    username: 'demo.business',
    realName: '商务经理',
    roles: ['COMMERCIAL_MANAGER'],
    permissions: [
      'variation:order:query',
      'variation:order:add',
      'variation:order:edit',
      'variation:order:item:edit',
      'variation:order:delete',
      'variation:order:submit',
      'variation:owner:submit',
      'variation:owner:review',
      'variation:trace',
      'bid:query',
      'bid:add',
      'bid:edit',
      'bid:delete',
      'bid:status',
    ],
  },
  readonly: {
    tenantId: '0',
    userId: '2',
    username: 'commercial.viewer',
    realName: '商务查看人',
    roles: ['USER'],
    permissions: ['variation:order:query', 'bid:query'],
  },
  denied: {
    tenantId: '0',
    userId: '3',
    username: 'no.commercial',
    realName: '无权限用户',
    roles: ['USER'],
    permissions: [],
  },
} as const

const variation = {
  id: '61',
  tenantId: '1',
  orgId: '1',
  projectId: 'P1',
  projectName: '项目一',
  contractId: '9',
  contractName: '演示合同',
  partnerId: 'A1',
  partnerName: '业主一',
  varCode: 'VO-061',
  varName: '基坑设计变更',
  varType: 'DESIGN',
  direction: 'COST',
  reportedAmount: '120000.00',
  approvalStatus: 'DRAFT',
  ownerStatus: 'NOT_SUBMITTED',
  version: '1',
  items: [],
  ownerSubmissions: [],
}

const bid = {
  id: '71',
  tenantId: '1',
  bidCode: 'BID-071',
  bidProjectName: '市民中心投标',
  bidStatus: 'PREPARING',
  projectId: null,
  remark: '投标阶段成本',
  createdAt: '2026-07-22 10:00:00',
  updatedAt: '2026-07-22 10:00:00',
}

async function installCommercialMock(page: Page, readIdentity: () => Identity): Promise<void> {
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
        data: [{ id: 'P1', projectName: '项目一', projectCode: 'P-001', status: 'IN_PROGRESS' }],
      }),
    }),
  )
  await page.route('**/api/system/dict/data/by-code/*', (route) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ code: '0', message: 'success', data: [] }),
    }),
  )
  await page.route(/\/api\/var-orders(?:\?.*)?$/, (route) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        code: '0',
        message: 'success',
        data: { records: [variation], total: 1, pageNo: 1, pageSize: 10 },
      }),
    }),
  )
  await page.route(/\/api\/bid-cost(?:\?.*)?$/, (route) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        code: '0',
        message: 'success',
        data: { records: [bid], total: 1, pageNo: 1, pageSize: 20 },
      }),
    }),
  )
  await page.route('**/api/bid-cost/owners', (route) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ code: '0', message: 'success', data: [] }),
    }),
  )
  await page.route('**/api/bid-cost/71', (route) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ code: '0', message: 'success', data: bid }),
    }),
  )
  await page.route('**/api/bid-cost/71/documents', (route) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ code: '0', message: 'success', data: [] }),
    }),
  )
}

test.describe('M4 variation and bid routes', () => {
  test('routes resolve without placeholders and remain accessible at three viewports', async ({
    page,
  }) => {
    test.setTimeout(60_000)
    const identity: Identity = 'business'
    await installCommercialMock(page, () => identity)
    const runtimeErrors = captureRuntimeErrors(page)

    await page.goto('/variation?projectId=P1#claim')
    await expect(page).toHaveURL(/\/variation\/order\?projectId=P1#claim$/)

    for (const target of [
      {
        path: '/variation/order',
        heading: '签证变更',
        selector: '.variation-page',
        record: '基坑设计变更',
      },
      {
        path: '/bid-cost',
        heading: '投标记录',
        selector: '.bid-cost-page',
        record: '市民中心投标',
      },
    ]) {
      for (const viewport of [
        { width: 1440, height: 900 },
        { width: 1024, height: 768 },
        { width: 390, height: 844 },
      ]) {
        await page.setViewportSize(viewport)
        await page.goto(target.path)
        await expect(page.locator('.shell-placeholder')).toHaveCount(0)
        await expect(page.getByRole('heading', { name: target.heading, exact: true })).toBeVisible()
        await expect(page.getByText(target.record, { exact: true })).toBeVisible()
        if (target.path === '/variation/order' && viewport.width === 1440) {
          await expect(page.locator('.variation-page table thead th')).toHaveCount(8)
          await expect(
            page.locator('.variation-page table tbody tr').first().locator('td'),
          ).toHaveCount(8)
          expect(
            await page
              .locator('.variation-page table tbody td')
              .first()
              .evaluate((element) => getComputedStyle(element).whiteSpace),
          ).toBe('nowrap')
        }
        await page.waitForTimeout(400)
        expect(
          await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth),
        ).toBe(true)
        const axe = await new AxeBuilder({ page }).include(target.selector).analyze()
        expect(
          axe.violations.filter((item) => ['serious', 'critical'].includes(item.impact ?? '')),
        ).toEqual([])
      }
    }

    await page.setViewportSize({ width: 1440, height: 900 })
    await page.goto('/bid-cost')
    await page.getByRole('button', { name: 'BID-071' }).click()
    await expect(page).toHaveURL(/\/engineering-tender\/records\/71\?tab=basic$/)
    await expect(page.getByRole('tab', { name: /基本信息/ })).toBeVisible()

    expect(runtimeErrors).toEqual([])
  })

  test('query-only identity sees no writes and denied identity fails closed', async ({
    page,
    browser,
  }) => {
    let identity: Identity = 'readonly'
    await installCommercialMock(page, () => identity)

    await page.goto('/variation/order')
    await expect(page.getByRole('button', { name: '新建变更' })).toHaveCount(0)
    await expect(page.getByRole('button', { name: '编辑' })).toHaveCount(0)

    await page.goto('/bid-cost')
    await expect(page.getByRole('button', { name: '新建投标记录' })).toHaveCount(0)
    await expect(page.getByRole('button', { name: '编辑' })).toHaveCount(0)
    await expect(page.getByRole('button', { name: '标记中标' })).toHaveCount(0)
    await expect(page.getByRole('button', { name: '标记未中标' })).toHaveCount(0)
    await expect(page.getByRole('button', { name: '删除' })).toHaveCount(0)

    const denied = await browser.newPage()
    identity = 'denied'
    await installCommercialMock(denied, () => identity)
    await denied.goto('/variation/order')
    await expect(denied).toHaveURL(/\/forbidden\?from=/)
    await denied.goto('/bid-cost')
    await expect(denied).toHaveURL(/\/forbidden\?from=/)
    await denied.close()
  })
})
