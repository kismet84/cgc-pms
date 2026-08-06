import AxeBuilder from '@axe-core/playwright'
import { expect, test, type Page, type Route } from '@playwright/test'
import { captureRuntimeErrors } from './runtime-errors'

const permissions = [
  'payment:app:query',
  'payment:app:add',
  'payment:app:edit',
  'payment:app:delete',
  'payment:app:submit',
  'expense:query',
  'expense:add',
  'expense:edit',
  'expense:delete',
  'expense:submit',
  'revenue:operations:query',
  'revenue:operations:maintain',
  'revenue:collection:reverse',
  'invoice:query',
  'invoice:add',
  'invoice:edit',
  'invoice:delete',
  'invoice:verify',
]

async function fulfill(route: Route, data: unknown, status = 200) {
  const request = route.request()
  await route.fulfill({
    status,
    contentType: 'application/json',
    body: JSON.stringify({
      code: status === 200 ? '0' : 'E2E_API_UNSTUBBED',
      message:
        status === 200 ? 'success' : `${request.method()} ${new URL(request.url()).pathname}`,
      data,
    }),
  })
}

async function install(page: Page) {
  await page.route('**/api/**', async (route) => {
    const path = new URL(route.request().url()).pathname
    if (path === '/api/profile/preferences')
      return fulfill(route, {
        sidebarCollapsed: false,
        notificationEnabled: true,
        theme: 'light',
        tableDensity: 'middle',
      })
    if (path === '/api/project-context/options') {
      return fulfill(route, [
        { id: 'P1', projectCode: 'PRJ-001', projectName: '示范工程项目', status: 'ACTIVE' },
      ])
    }
    if (path === '/api/system/dict/data/by-code/pay_type')
      return fulfill(route, [{ dictLabel: '合同付款', dictValue: 'CONTRACT', status: 'ENABLE' }])
    if (path === '/api/system/dict/data/by-code/expense_category')
      return fulfill(route, [{ dictLabel: '其他', dictValue: 'OTHER', status: 'ENABLE' }])
    if (path === '/api/system/dict/data/by-code/invoice_type')
      return fulfill(route, [
        { dictLabel: '增值税专用发票', dictValue: 'VAT_SPECIAL', status: 'ENABLE' },
      ])
    if (path === '/api/system/dict/data/by-code/pay_method')
      return fulfill(route, [
        { dictLabel: '银行转账', dictValue: 'BANK_TRANSFER', status: 'ENABLE' },
      ])
    if (path === '/api/revenue-operations/settlements') {
      return fulfill(route, [
        {
          id: 'S1',
          projectId: 'P1',
          contractId: 'C1',
          customerId: 'CU1',
          settlementCode: 'OS-001',
          settlementPeriod: '2026-07',
          settlementDate: '2026-07-25',
          grossAmount: '1000000.00',
          taxAmount: '90000.00',
          retentionAmount: '50000.00',
          netReceivableAmount: '950000.00',
          dueDate: '2026-08-25',
          status: 'DRAFT',
          attachmentCount: 1,
          formulaVersion: 'OWNER_SETTLEMENT_V1',
          version: '0',
        },
      ])
    }
    if (path === '/api/revenue-operations/receivables') {
      return fulfill(route, [
        {
          id: 'R1',
          projectId: 'P1',
          contractId: 'C1',
          settlementId: 'S1',
          customerId: 'CU1',
          receivableCode: 'AR-001',
          receivableType: 'SETTLEMENT',
          originalAmount: '950000.00',
          collectedAmount: '200000.00',
          creditedAmount: '0.00',
          outstandingAmount: '750000.00',
          dueDate: '2026-08-25',
          status: 'OPEN',
          overdue: false,
          version: '0',
        },
      ])
    }
    if (path === '/api/revenue-operations/sales-invoices') {
      return fulfill(route, [
        {
          id: 'SI1',
          projectId: 'P1',
          contractId: 'C1',
          customerId: 'CU1',
          invoiceNo: 'SINV-001',
          invoiceType: 'VAT_SPECIAL',
          invoiceDate: '2026-07-25',
          amountWithoutTax: '900000.00',
          taxAmount: '90000.00',
          totalAmount: '990000.00',
          allocatedAmount: '0.00',
          status: 'ISSUED',
          verificationStatus: 'UNVERIFIED',
          attachmentCount: 1,
          version: '0',
        },
      ])
    }
    if (path === '/api/revenue-operations/collections') {
      return fulfill(route, [
        {
          id: 'C1',
          projectId: 'P1',
          contractId: 'C1',
          customerId: 'CU1',
          fundAccountId: 'FA1',
          collectionCode: 'COL-001',
          externalTxnNo: 'BANK-001',
          collectedAt: '2026-07-25T10:00:00',
          amount: '200000.00',
          allocatedAmount: '200000.00',
          unallocatedAmount: '0.00',
          payerName: '示范建设单位',
          status: 'CONFIRMED',
          attachmentCount: 1,
          version: '0',
        },
      ])
    }
    if (path === '/api/pay-applications') {
      return fulfill(route, {
        records: [
          {
            id: 'PA1',
            tenantId: '0',
            projectId: 'P1',
            contractId: 'C1',
            partnerId: 'SUP1',
            applyCode: 'PAY-001',
            applyAmount: '500000.00',
            approvedAmount: '0.00',
            actualPayAmount: '0.00',
            payType: 'CONTRACT',
            payStatus: 'UNPAID',
            approvalStatus: 'DRAFT',
            integrityVersion: 'CLOSED_LOOP_V1',
          },
        ],
        total: 1,
        pageNo: 1,
        pageSize: 20,
      })
    }
    if (path === '/api/expenses') {
      return fulfill(route, {
        records: [
          {
            id: 'E1',
            projectId: 'P1',
            contractId: 'C1',
            expenseCode: 'EXP-001',
            expenseCategory: 'OTHER',
            expenseDate: '2026-07-25',
            amount: '1000.00',
            convertedAmount: '0.00',
            paidAmount: '0.00',
            availableToConvert: '1000.00',
            status: 'DRAFT',
            approvalStatus: 'DRAFT',
          },
        ],
        total: 1,
        pageNo: 1,
        pageSize: 20,
      })
    }
    if (path === '/api/invoices') {
      return fulfill(route, {
        records: [
          {
            id: 'I1',
            projectId: 'P1',
            contractId: 'C1',
            payRecordId: 'PR1',
            invoiceNo: 'INV-001',
            invoiceType: 'VAT_SPECIAL',
            invoiceAmount: '500000.00',
            invoiceDate: '2026-07-25',
            verifyStatus: 'PENDING',
          },
        ],
        total: 1,
        pageNo: 1,
        pageSize: 20,
      })
    }
    return fulfill(route, null, 500)
  })
  await page.route('**/api/auth/userinfo', (route) =>
    fulfill(route, {
      tenantId: '0',
      userId: '1',
      username: 'finance-user',
      roles: ['USER'],
      permissions,
    }),
  )
  await page.route('**/api/auth/refresh', (route) => fulfill(route, null, 401))
}

test('five finance routes render at desktop and mobile without runtime or axe errors', async ({
  page,
}) => {
  test.setTimeout(60_000)
  await install(page)
  const runtimeErrors = captureRuntimeErrors(page)
  const failedResponses: string[] = []
  page.on('response', (response) => {
    if (response.status() >= 400 && !response.url().endsWith('/api/auth/refresh')) {
      failedResponses.push(`${response.status()} ${response.url()}`)
    }
  })

  const routes = [
    ['/payment/application', '付款申请'],
    ['/payment/expense', '费用申请'],
    ['/revenue', '收入与回款'],
    ['/invoice', '发票管理'],
  ] as const
  for (const viewport of [
    { width: 1440, height: 900 },
    { width: 390, height: 844 },
  ]) {
    await page.setViewportSize(viewport)
    for (const [path, heading] of routes) {
      await page.goto(path)
      await expect(page.getByRole('heading', { name: heading, exact: true })).toBeVisible()
      expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(
        true,
      )
    }
  }

  await page.goto('/payment')
  await expect(page).toHaveURL(/\/payment\/application$/)
  await expect(page.locator('.finance-workspace')).toBeVisible()
  const axe = await new AxeBuilder({ page }).include('.finance-workspace').analyze()
  expect(
    axe.violations.filter((item) => ['serious', 'critical'].includes(item.impact ?? '')),
  ).toEqual([])
  expect(runtimeErrors).toEqual([])
  expect(failedResponses).toEqual([])
})
