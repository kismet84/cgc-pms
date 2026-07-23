import AxeBuilder from '@axe-core/playwright'
import { expect, test, type Page } from '@playwright/test'
import { captureRuntimeErrors } from './runtime-errors'

type Identity = 'admin' | 'readonly' | 'denied'

const users = {
  admin: {
    userId: '1',
    username: 'contract.admin',
    realName: '合同管理员',
    roles: ['USER'],
    permissions: [
      'contract:query',
      'contract:add',
      'contract:edit',
      'contract:submit',
      'contract:delete',
    ],
  },
  readonly: {
    userId: '2',
    username: 'contract.viewer',
    realName: '合同查看人',
    roles: ['USER'],
    permissions: ['contract:query'],
  },
  denied: {
    userId: '3',
    username: 'no.contract',
    realName: '无权限用户',
    roles: ['USER'],
    permissions: [],
  },
} as const

async function installContractMock(page: Page, readIdentity: () => Identity): Promise<void> {
  await page.route('**/api/auth/userinfo', (route) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        code: '0',
        message: 'success',
        data: users[readIdentity()],
      }),
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
        data: [{ id: 'P1', projectName: '项目一', status: 'ACTIVE' }],
      }),
    }),
  )
  await page.route(/\/api\/partners(?:\?.*)?$/, (route) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        code: '0',
        message: 'success',
        data: {
          records: [
            {
              id: 'A1',
              partnerCode: 'A1',
              partnerName: '甲方一',
              partnerType: 'OWNER',
              status: 'ENABLE',
            },
            {
              id: 'B1',
              partnerCode: 'B1',
              partnerName: '乙方一',
              partnerType: 'SUPPLIER',
              status: 'ENABLE',
            },
          ],
        },
      }),
    }),
  )
  await page.route(/\/api\/contracts\/kpi(?:\?.*)?$/, (route) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        code: '0',
        message: 'success',
        data: {
          totalCount: '1',
          totalAmount: '1200000.00',
          paidAmount: '100000.00',
          unpaidAmount: '1100000.00',
          overdueCount: '0',
        },
      }),
    }),
  )
  await page.route(/\/api\/contracts(?:\?.*)?$/, (route) => {
    if (route.request().method() !== 'GET') {
      return route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ code: '0', message: 'success', data: '9' }),
      })
    }
    return route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        code: '0',
        message: 'success',
        data: {
          records: [
            {
              id: '9',
              tenantId: '1',
              orgId: '1',
              projectId: 'P1',
              contractCode: 'HT-009',
              contractName: '演示合同',
              contractType: 'MAIN',
              partyAId: 'A1',
              partyAName: '甲方一',
              partyBId: 'B1',
              partyBName: '乙方一',
              contractAmount: '1200000.00',
              currentAmount: '1200000.00',
              taxRate: '9',
              taxAmount: '99082.57',
              amountWithoutTax: '1100917.43',
              signedDate: '2026-07-01',
              startDate: '2026-07-01',
              endDate: '2027-07-01',
              paymentMethod: '转账',
              settlementMethod: '月结',
              paidAmount: '100000.00',
              settlementAmount: '0.00',
              contractStatus: 'PERFORMING',
              approvalStatus: 'DRAFT',
              projectName: '项目一',
              createdBy: 'tester',
              createdAt: '2026-07-20 10:00:00',
              updatedAt: '2026-07-20 10:00:00',
              version: '1',
              remark: '备注',
            },
          ],
          total: 1,
          pageNo: 1,
          pageSize: 20,
        },
      }),
    })
  })
  await page.route('**/api/contracts/9', (route) => {
    if (route.request().method() === 'DELETE') {
      return route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ code: '0', message: 'success', data: null }),
      })
    }
    return route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        code: '0',
        message: 'success',
        data: {
          id: '9',
          tenantId: '1',
          orgId: '1',
          projectId: 'P1',
          contractCode: 'HT-009',
          contractName: '演示合同',
          contractType: 'MAIN',
          partyAId: 'A1',
          partyAName: '甲方一',
          partyBId: 'B1',
          partyBName: '乙方一',
          contractAmount: '1200000.00',
          currentAmount: '1200000.00',
          taxRate: '9',
          taxAmount: '99082.57',
          amountWithoutTax: '1100917.43',
          signedDate: '2026-07-01',
          startDate: '2026-07-01',
          endDate: '2027-07-01',
          paymentMethod: '转账',
          settlementMethod: '月结',
          paidAmount: '100000.00',
          settlementAmount: '0.00',
          contractStatus: 'PERFORMING',
          approvalStatus: 'DRAFT',
          projectName: '项目一',
          createdBy: 'tester',
          createdAt: '2026-07-20 10:00:00',
          updatedAt: '2026-07-20 10:00:00',
          version: '1',
          remark: '备注',
        },
      }),
    })
  })
  await page.route('**/api/contracts/9/items', (route) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        code: '0',
        message: 'success',
        data: [
          {
            id: 'I1',
            contractId: '9',
            itemCode: 'ITEM-1',
            itemName: '土建',
            unit: '项',
            quantity: '1',
            amount: '1200000.00',
          },
        ],
      }),
    }),
  )
  await page.route('**/api/contracts/9/payment-terms', (route) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        code: '0',
        message: 'success',
        data: [
          {
            id: 'T1',
            contractId: '9',
            termName: '首付款',
            paymentRatio: '30',
            paymentAmount: '360000.00',
            plannedDate: '2026-08-01',
          },
        ],
      }),
    }),
  )
  await page.route('**/api/contracts/9/approval-records', (route) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        code: '0',
        message: 'success',
        data: [
          {
            id: 'AR1',
            nodeName: '发起',
            operatorName: 'tester',
            actionType: 'SUBMIT',
            actionName: '提交',
            comment: '已发起',
            createdAt: '2026-07-20 10:00:00',
          },
        ],
      }),
    }),
  )
  await page.route('**/api/contracts/9/submit', (route) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ code: '0', message: 'success', data: null }),
    }),
  )
  await page.route('**/api/contracts/9/composite', (route) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ code: '0', message: 'success', data: null }),
    }),
  )
}

test.describe('M4 contract routes', () => {
  test('five routes resolve without placeholder and no serious accessibility issue', async ({
    page,
  }) => {
    const identity: Identity = 'admin'
    await installContractMock(page, () => identity)
    const runtimeErrors = captureRuntimeErrors(page)

    await page.goto('/v2/contract/ledger?projectId=P1#ledger')
    await expect(page.locator('.shell-placeholder')).toHaveCount(0)
    await expect(page.getByRole('heading', { name: '合同列表' })).toBeVisible()
    await expect(page.locator('.contract-page__kpi-grid > div')).toHaveCount(5)
    await expect(
      page.locator('.contract-page__list-card').getByRole('button', { name: '新建合同' }),
    ).toBeVisible()
    await expect(page.getByRole('searchbox', { name: '关键词' })).toHaveCount(0)

    for (const path of ['/v2/contract/create', '/v2/contract/9', '/v2/contract/9/edit']) {
      await page.goto(path)
      await expect(page.locator('.shell-placeholder')).toHaveCount(0)
      await expect(page.getByRole('main')).toBeVisible()
    }

    for (const viewport of [
      { width: 1440, height: 900 },
      { width: 1024, height: 768 },
      { width: 390, height: 844 },
    ]) {
      await page.setViewportSize(viewport)
      await page.goto('/v2/contract/ledger')
      expect(
        await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth),
      ).toBe(true)
      const axe = await new AxeBuilder({ page }).include('.contract-page').analyze()
      expect(
        axe.violations.filter((item) => ['serious', 'critical'].includes(item.impact ?? '')),
      ).toEqual([])
    }

    expect(runtimeErrors).toEqual([])
  })

  test('query-only identity cannot see write actions and no-permission identity fails closed', async ({
    page,
    browser,
  }) => {
    let identity: Identity = 'readonly'
    await installContractMock(page, () => identity)

    await page.goto('/v2/contract/9')
    await expect(page.getByRole('button', { name: '编辑' })).toHaveCount(0)
    await expect(page.getByRole('button', { name: '提交审批' })).toHaveCount(0)
    await expect(page.getByRole('button', { name: '删除' })).toHaveCount(0)

    const denied = await browser.newPage()
    identity = 'denied'
    await installContractMock(denied, () => identity)
    await denied.goto('/v2/contract/ledger')
    await expect(denied).toHaveURL(/\/v2\/forbidden\?from=/)
    await denied.close()
  })
})
