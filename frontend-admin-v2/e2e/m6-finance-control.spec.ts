import AxeBuilder from '@axe-core/playwright'
import { expect, test, type Page, type Route } from '@playwright/test'
import { captureRuntimeErrors } from './runtime-errors'

const permissions = [
  'finance:operations:query',
  'finance:operations:maintain',
  'finance:analytics:maintain',
  'cashbook:journal:query',
  'cashbook:journal:maintain',
  'finance:forecast:query',
  'finance:forecast:maintain',
  'finance:forecast:submit',
  'finance:forecast:approve',
  'finance:forecast:refresh',
  'accounting:query',
  'accounting:review',
  'accounting:post',
  'accounting:add',
  'accounting:adjustment:add',
  'finance:close:query',
  'finance:close:check',
  'finance:close:close',
  'finance:close:reopen',
]

async function fulfill(route: Route, data: unknown, status = 200) {
  await route.fulfill({
    status,
    contentType: 'application/json',
    body: JSON.stringify({ code: status === 200 ? '0' : 'TEST_ERROR', data }),
  })
}

async function install(page: Page, writes: string[]) {
  await page.route('**/api/**', async (route) => {
    const request = route.request()
    const path = new URL(request.url()).pathname
    if (request.method() !== 'GET') {
      writes.push(`${request.method()} ${path}`)
      return fulfill(route, null)
    }
    if (path === '/api/project-context/options')
      return fulfill(route, [
        { id: 'P1', projectCode: 'PRJ-001', projectName: '滨江科创中心项目', status: 'ACTIVE' },
      ])
    if (path === '/api/finance-operations/workspace')
      return fulfill(route, {
        schedules: [
          {
            id: 'S1',
            projectId: 'P1',
            contractId: 'C1',
            scheduleName: '主体结构七月进度款',
            plannedDate: '2026-07-28',
            plannedAmount: '680000.00',
            paidAmount: '200000.00',
            status: 'PARTIALLY_PAID',
          },
        ],
        alerts: [
          {
            id: 'A1',
            projectId: 'P1',
            alertType: 'PAYMENT_DUE',
            businessType: 'PAYMENT_SCHEDULE',
            businessId: 'S1',
            severity: 'HIGH',
            dueAt: '2026-07-28T09:00:00',
            status: 'OPEN',
            message: '主体结构进度款即将到期',
          },
        ],
        snapshots: [
          {
            id: 'SN1',
            projectId: 'P1',
            snapshotDate: '2026-07-26',
            contractAmount: '128000000.00',
            approvedUnpaidAmount: '480000.00',
            paidAmount: '8200000.00',
            budgetAmount: '109000000.00',
            cashInflow: '18000000.00',
            cashOutflow: '11600000.00',
            actualCost: '10100000.00',
            profitAmount: '6400000.00',
          },
        ],
      })
    if (path === '/api/fund-accounts')
      return fulfill(route, [
        {
          id: 'FA1',
          accountCode: 'BANK-001',
          accountName: '项目基本户',
          accountType: 'BANK',
          bankName: '中国建设银行',
          openingDate: '2026-01-01',
          openingBalance: '5000000.00',
          enabledFlag: 1,
          version: 0,
        },
      ])
    if (path === '/api/cash-journal-entries')
      return fulfill(route, {
        pageNo: 1,
        pageSize: 50,
        total: 1,
        records: [
          {
            id: 'J1',
            entryNo: 'JRN-202607-001',
            accountId: 'FA1',
            direction: 'IN',
            amount: '1200000.00',
            runningBalance: '6200000.00',
            businessDate: '2026-07-25',
            projectId: 'P1',
            sourceType: 'COLLECTION_RECORD',
            sourceId: 'COL1',
            status: 'ARCHIVED',
          },
        ],
      })
    if (path === '/api/cash-forecasts/workspace')
      return fulfill(route, [
        {
          id: 'CF1',
          projectId: 'P1',
          cycleCode: 'CF-202607-V1',
          forecastName: '七月资金滚动预测',
          asOfDate: '2026-07-26',
          horizonStart: '2026-07-26',
          horizonEnd: '2026-08-25',
          scenario: 'BASE',
          openingBalance: '6200000.00',
          status: 'DRAFT',
          versionNo: 1,
        },
      ])
    if (path === '/api/cash-forecasts/workspace/CF1')
      return fulfill(route, {
        cycle: {
          id: 'CF1',
          projectId: 'P1',
          cycleCode: 'CF-202607-V1',
          forecastName: '七月资金滚动预测',
          asOfDate: '2026-07-26',
          horizonStart: '2026-07-26',
          horizonEnd: '2026-08-25',
          scenario: 'BASE',
          openingBalance: '6200000.00',
          status: 'DRAFT',
          versionNo: 1,
        },
        lines: [
          {
            id: 'CFL1',
            cycleId: 'CF1',
            forecastDate: '2026-07-28',
            plannedInflow: '800000.00',
            plannedOutflow: '680000.00',
            financingAmount: '0.00',
            projectedBalance: '6320000.00',
            gapAmount: '0.00',
            actualInflow: '0.00',
            actualOutflow: '0.00',
            inflowVariance: '0.00',
            outflowVariance: '0.00',
          },
        ],
        actions: [
          {
            id: 'ACT1',
            cycleId: 'CF1',
            projectId: 'P1',
            lineId: 'CFL1',
            actionType: 'ACCELERATE_COLLECTION',
            plannedDate: '2026-07-28',
            amount: '300000.00',
            status: 'PROPOSED',
          },
        ],
        actualJournals: [],
      })
    if (path === '/api/accounting-entry/workspace')
      return fulfill(route, {
        pageNo: 1,
        pageSize: 50,
        total: 1,
        records: [
          {
            id: 'E1',
            entryCode: '记-202607-001',
            entryDate: '2026-07-25',
            entryType: 'COLLECTION',
            sourceType: 'COLLECTION_RECORD',
            sourceId: 'COL1',
            projectId: 'P1',
            entryStatus: 'DRAFT',
            reviewStatus: 'PENDING',
            totalDebit: '1200000.00',
            totalCredit: '1200000.00',
            version: 0,
          },
        ],
      })
    if (path === '/api/accounting-entry/workspace/E1')
      return fulfill(route, {
        entry: {
          id: 'E1',
          entryCode: '记-202607-001',
          entryDate: '2026-07-25',
          entryType: 'COLLECTION',
          sourceType: 'COLLECTION_RECORD',
          sourceId: 'COL1',
          projectId: 'P1',
          entryStatus: 'DRAFT',
          reviewStatus: 'PENDING',
          totalDebit: '1200000.00',
          totalCredit: '1200000.00',
          version: 0,
        },
        lines: [
          {
            id: 'EL1',
            lineNo: 1,
            direction: 'DEBIT',
            accountCode: '1002',
            accountName: '银行存款',
            amount: '1200000.00',
            summary: '业主工程款到账',
          },
        ],
      })
    if (path === '/api/financial-close/workspace')
      return fulfill(route, [
        {
          id: 'FP1',
          periodCode: '2026-07',
          fiscalYear: 2026,
          fiscalMonth: 7,
          startDate: '2026-07-01',
          endDate: '2026-07-31',
          status: 'OPEN',
          issueCount: 0,
          version: 0,
        },
      ])
    if (path === '/api/financial-close/workspace/FP1')
      return fulfill(route, {
        period: {
          id: 'FP1',
          periodCode: '2026-07',
          fiscalYear: 2026,
          fiscalMonth: 7,
          startDate: '2026-07-01',
          endDate: '2026-07-31',
          status: 'OPEN',
          issueCount: 0,
          version: 0,
        },
        checks: [{ id: 'CHK1', checkType: 'TRIAL_BALANCE', status: 'PASS', issueCount: 0 }],
        accountReconciliations: [],
        bankReconciliations: [],
        entries: [],
      })
    if (path === '/api/financial-close/workspace/2026/7/statements')
      return fulfill(route, {
        period: {
          id: 'FP1',
          periodCode: '2026-07',
          fiscalYear: 2026,
          fiscalMonth: 7,
          startDate: '2026-07-01',
          endDate: '2026-07-31',
          status: 'OPEN',
          issueCount: 0,
          version: 0,
        },
        trialBalance: [
          { accountCode: '1002', accountName: '银行存款', debit: '1200000.00', credit: '0.00' },
        ],
        receivableOutstanding: '3600000.00',
        payableOutstanding: '480000.00',
        cashFlow: { inflow: '1200000.00', outflow: '680000.00' },
      })
    return fulfill(route, null)
  })
  await page.route('**/api/auth/userinfo', (route) =>
    fulfill(route, { userId: '1', username: 'finance-user', roles: ['SUPER_ADMIN'], permissions }),
  )
  await page.route('**/api/auth/refresh', (route) => fulfill(route, null, 401))
}

test('five finance-control routes render real-shaped facts and write then reread', async ({
  page,
}) => {
  const writes: string[] = []
  await install(page, writes)
  const runtimeErrors = captureRuntimeErrors(page)
  const failedResponses: string[] = []
  page.on('response', (response) => {
    if (response.status() >= 400 && !response.url().endsWith('/api/auth/refresh'))
      failedResponses.push(`${response.status()} ${response.url()}`)
  })

  const routes = [
    ['/v2/finance-operations?projectId=P1', '资金运营'],
    ['/v2/cash-journal?projectId=P1', '资金日记账'],
    ['/v2/cash-forecast?projectId=P1', '资金预测'],
    ['/v2/accounting-entry?projectId=P1', '会计凭证'],
    ['/v2/financial-close?projectId=P1', '财务月结'],
  ] as const
  for (const viewport of [
    { width: 1440, height: 900 },
    { width: 768, height: 1024 },
    { width: 390, height: 844 },
  ]) {
    await page.setViewportSize(viewport)
    for (const [path, heading] of routes) {
      await page.goto(path)
      await expect(page.getByRole('heading', { name: heading, exact: true })).toBeVisible()
      await expect(page.getByText('状态待确认')).toHaveCount(0)
      expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(
        true,
      )
    }
  }

  await page.setViewportSize({ width: 1440, height: 900 })
  await page.goto('/v2/finance-operations?projectId=P1')
  page.once('dialog', (dialog) => dialog.accept('已核实付款计划'))
  await page
    .getByRole('row')
    .filter({ hasText: '主体结构进度款即将到期' })
    .locator('summary[aria-label="主体结构进度款即将到期更多操作"]')
    .click()
  await page.getByRole('button', { name: '处理', exact: true }).click()
  await expect.poll(() => writes).toContain('POST /api/finance-operations/alerts/A1/handle')
  await expect(page.getByText('主体结构进度款即将到期')).toBeVisible()

  const axe = await new AxeBuilder({ page }).include('.finance-control').analyze()
  expect(
    axe.violations.filter((item) => ['serious', 'critical'].includes(item.impact ?? '')),
  ).toEqual([])
  expect(runtimeErrors).toEqual([])
  expect(failedResponses).toEqual([])
})
