import AxeBuilder from '@axe-core/playwright'
import { expect, test, type Page, type Route } from '@playwright/test'
import { captureRuntimeErrors } from './runtime-errors'
import { installShellPreferencesMock } from './shell-session'

const allPermissions = [
  'supplier:sourcing:query',
  'supplier:sourcing:maintain',
  'supplier:sourcing:quote',
  'supplier:sourcing:evaluate',
  'supplier:sourcing:award',
  'supplier:performance:evaluate',
  'supplier:blacklist:review',
]
const event = {
  id: 'E1',
  projectId: 'P1',
  purchaseRequestId: 'PR1',
  sourcingCode: 'SRC-001',
  sourcingTitle: `超长供应商与项目名称${'可追溯'.repeat(24)}`,
  sourcingType: 'INQUIRY',
  deadline: '2026-08-30T12:00:00',
  currencyCode: 'CNY',
  status: 'DRAFT',
}
const supplier = {
  id: 'I1',
  sourcingEventId: 'E1',
  partnerId: 'P100',
  invitationStatus: 'INVITED',
}

async function fulfill(route: Route, data: unknown, status = 200) {
  await route.fulfill({
    status,
    contentType: 'application/json',
    body: JSON.stringify({
      code: status === 200 ? '0' : `HTTP_${status}`,
      message: status === 200 ? 'success' : '服务异常',
      data,
    }),
  })
}

async function install(page: Page, permissions = allPermissions) {
  await installShellPreferencesMock(page)
  const writes: Array<{ method: string; path: string; body: unknown }> = []
  const quotes: Array<Record<string, unknown>> = []
  const evaluations: Array<Record<string, unknown>> = []
  await page.route('**/api/auth/userinfo', (route) =>
    fulfill(route, {
      userId: '1',
      username: 'supplier.user',
      roles: ['USER'],
      permissions,
    }),
  )
  await page.route('**/api/auth/refresh', (route) => fulfill(route, null, 401))
  await page.route('**/api/project-context/options', (route) =>
    fulfill(route, [{ id: 'P1', projectName: `总承包项目${'很长'.repeat(20)}`, status: 'ACTIVE' }]),
  )
  await page.route('**/api/partners**', (route) =>
    fulfill(route, {
      records: [
        {
          id: 'P100',
          partnerCode: 'SUP-100',
          partnerName: '供应商甲',
          partnerType: 'SUPPLIER',
          status: 'ENABLE',
        },
        {
          id: 'P200',
          partnerCode: 'SUP-200',
          partnerName: '供应商乙',
          partnerType: 'SUPPLIER',
          status: 'ENABLE',
        },
      ],
    }),
  )
  await page.route('**/api/purchase-requests**', (route) =>
    fulfill(route, {
      records: [
        {
          id: 'PR1',
          projectId: 'P1',
          requestCode: 'PR-001',
          purpose: '采购钢材',
          approvalStatus: 'APPROVED',
        },
      ],
      total: 1,
      pageNum: 1,
      pageSize: 200,
    }),
  )
  await page.route('**/api/purchase-orders**', (route) =>
    fulfill(route, {
      records: [
        {
          id: 'PO1',
          projectId: 'P1',
          orderCode: 'PO-001',
          partnerName: '供应商甲',
          approvalStatus: 'APPROVED',
        },
      ],
      total: 1,
      pageNum: 1,
      pageSize: 200,
    }),
  )
  await page.route('**/api/receipts**', (route) =>
    fulfill(route, {
      records: [
        {
          id: 'RC1',
          projectId: 'P1',
          receiptCode: 'RC-001',
          orderCode: 'PO-001',
          partnerName: '供应商甲',
        },
      ],
      total: 1,
      pageNum: 1,
      pageSize: 200,
    }),
  )
  await page.route('**/api/contracts**', (route) =>
    fulfill(route, {
      records: [
        {
          id: 'C1',
          projectId: 'P1',
          contractCode: 'CT-001',
          contractName: '采购合同',
        },
      ],
      total: 1,
      pageNo: 1,
      pageSize: 200,
    }),
  )
  await page.route('**/api/supplier-sourcing/**', async (route) => {
    const request = route.request()
    const url = new URL(request.url())
    const path = url.pathname
    if (request.method() !== 'GET')
      writes.push({
        method: request.method(),
        path,
        body: request.postData() ? request.postDataJSON() : null,
      })
    if (request.method() === 'GET' && path.endsWith('/events')) return fulfill(route, [event])
    if (request.method() === 'GET' && path.endsWith('/performance')) return fulfill(route, [])
    if (request.method() === 'GET' && path.endsWith('/returns')) return fulfill(route, [])
    if (request.method() === 'GET' && path.endsWith('/trace'))
      return fulfill(route, {
        event,
        purchaseRequest: { id: 'PR1', requestCode: 'PR-001' },
        invitedSuppliers: [supplier],
        quotes,
        bidEvaluations: evaluations,
        contract: event.status === 'CONTRACTED' ? { id: 'C1', contractName: '采购合同' } : null,
        purchaseOrders: [],
        receipts: [],
        supplierReturns: [],
        settlements: [],
        performanceEvaluations: [],
        blacklistRecords: [
          {
            id: 'B1',
            performanceEvaluationId: 'PE1',
            partnerId: 'P100',
            projectId: 'P1',
            actionType: 'ADD',
            reason: '履约不合格',
            status: 'SUBMITTED',
          },
        ],
        qualitySafetyFacts: [],
      })
    if (path.endsWith('/publish')) event.status = 'PUBLISHED'
    else if (path.endsWith('/quotes') && request.method() === 'POST')
      quotes.push({
        id: 'Q1',
        sourcingEventId: 'E1',
        sourcingSupplierId: 'I1',
        partnerId: 'P100',
        status: 'DRAFT',
        ...request.postDataJSON(),
      })
    else if (path.endsWith('/quotes/Q1/submit')) quotes[0]!.status = 'SUBMITTED'
    else if (path.endsWith('/start-evaluation')) event.status = 'EVALUATING'
    else if (path.endsWith('/evaluations')) {
      evaluations.push({
        id: 'EV1',
        sourcingEventId: 'E1',
        partnerId: 'P100',
        totalScore: '88.00',
        ...request.postDataJSON(),
      })
    } else if (path.endsWith('/award')) event.status = 'AWARDED'
    else if (path.endsWith('/link-contract')) {
      event.status = 'CONTRACTED'
      Object.assign(event, { contractId: 'C1' })
    }
    return fulfill(route, event)
  })
  return writes
}

test.describe('M5 supplier sourcing V2', () => {
  test.beforeEach(() => Object.assign(event, { status: 'DRAFT', contractId: undefined }))

  test('fails closed without query permission and sends no sourcing traffic', async ({ page }) => {
    const traffic: string[] = []
    page.on('request', (request) => {
      if (request.url().includes('/api/supplier-sourcing/')) traffic.push(request.url())
    })
    await install(page, [])
    await page.goto('/supplier-sourcing?projectId=P1')
    await expect(page).toHaveURL(/\/forbidden\?from=/)
    await expect(page.getByText('403', { exact: true })).toBeVisible()
    expect(traffic).toEqual([])
  })

  test('renders long facts accessibly without viewport overflow', async ({ page }) => {
    await install(page)
    const errors = captureRuntimeErrors(page)
    for (const viewport of [
      { width: 1440, height: 900 },
      { width: 1024, height: 768 },
      { width: 390, height: 844 },
    ]) {
      await page.setViewportSize(viewport)
      await page.goto('/supplier-sourcing?projectId=P1')
      await expect(page.getByText('SRC-001', { exact: true })).toBeVisible()
      expect(
        await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth),
      ).toBe(true)
      const result = await new AxeBuilder({ page }).include('.supplier-page').analyze()
      expect(
        result.violations.filter((item) => ['serious', 'critical'].includes(item.impact ?? '')),
      ).toEqual([])
    }
    expect(errors).toEqual([])
  })

  test('loads authorized sourcing facts for all projects', async ({ page }) => {
    await install(page)
    await page.goto('/supplier-sourcing')
    await expect(page).toHaveURL(/\/supplier-sourcing$/)
    const eventTable = page.getByRole('table', { name: '招采事件列表' })
    await expect(eventTable).toBeVisible()
    await expect(
      eventTable
        .locator('xpath=ancestor::section[contains(@class,"v2-card")]')
        .locator(':scope > .v2-card__header'),
    ).toHaveCount(0)
    await expect(page.getByText('评价 0', { exact: true })).toBeVisible()
    await expect(page.getByText('退货 0', { exact: true })).toBeVisible()
    await expect(page.getByText('SRC-001', { exact: true })).toBeVisible()
    await expect(page.getByText(/请(?:先)?选择项目/)).toHaveCount(0)
  })

  for (const sample of [
    {
      permission: 'supplier:sourcing:maintain',
      status: 'DRAFT',
      action: '新建招采事件',
      trace: false,
    },
    {
      permission: 'supplier:sourcing:quote',
      status: 'PUBLISHED',
      action: '登记报价',
      trace: true,
    },
    {
      permission: 'supplier:sourcing:evaluate',
      status: 'PUBLISHED',
      action: '开始评审',
      trace: true,
    },
    {
      permission: 'supplier:sourcing:award',
      status: 'AWARDED',
      action: '关联合同',
      trace: true,
    },
    {
      permission: 'supplier:performance:evaluate',
      status: 'CONTRACTED',
      action: '登记履约评价',
      trace: false,
    },
    {
      permission: 'supplier:blacklist:review',
      status: 'CONTRACTED',
      action: '审核',
      trace: true,
    },
  ]) {
    test(`${sample.permission} exposes only its ordinary-user action`, async ({ page }) => {
      event.status = sample.status
      await install(page, ['supplier:sourcing:query', sample.permission])
      await page.goto('/supplier-sourcing?projectId=P1')
      if (sample.trace)
        await page.getByRole('button', { name: event.sourcingCode, exact: true }).click()
      await expect(
        page.getByRole('button', { name: sample.action, exact: true }).first(),
      ).toBeVisible()
      if (sample.permission !== 'supplier:sourcing:maintain')
        await expect(page.getByRole('button', { name: '新建招采事件' })).toHaveCount(0)
    })
  }

  test('runs event, quote, evaluation, award and contract actions once', async ({ page }) => {
    const writes = await install(page)
    await page.goto('/supplier-sourcing?projectId=P1')
    await page.getByRole('button', { name: event.sourcingCode, exact: true }).click()
    await page.getByRole('button', { name: '发布', exact: true }).dblclick()
    await expect(page.getByText('已发布', { exact: true }).first()).toBeVisible()

    await page.getByRole('button', { name: '登记报价' }).click()
    await page.getByLabel('报价编号').fill('Q-001')
    await page.getByLabel('含税总价').fill('9007199254740993.12')
    await page.getByLabel('报价有效期').fill('2026-08-31')
    await page.getByLabel('商务条款').fill('月结')
    await page.getByRole('button', { name: '确认提交' }).dblclick()
    await expect(page.getByText('Q-001', { exact: true })).toBeVisible()

    await page.getByRole('button', { name: '提交报价' }).dblclick()
    await page.getByRole('button', { name: '开始评审' }).dblclick()
    await page.getByRole('button', { name: '评审', exact: true }).click()
    for (const field of ['商务评分', '技术评分', '交付评分', '质量评分'])
      await page.getByLabel(field).fill('88')
    await page.getByLabel('评审意见').fill('符合要求')
    await page.getByRole('button', { name: '确认提交' }).dblclick()

    await page.getByRole('button', { name: '定标', exact: true }).click()
    await page.getByLabel('定标依据').fill('综合评分第一，价格与交期满足采购要求')
    await page.getByRole('button', { name: '确认提交' }).dblclick()
    await page.getByRole('button', { name: '关联合同' }).click()
    const contractDialog = page.getByRole('dialog', { name: '关联合同' })
    await contractDialog
      .getByRole('combobox', { name: '合同' })
      .selectOption({ label: 'CT-001 · 采购合同' })
    await page.getByRole('button', { name: '确认提交' }).dblclick()
    await expect(page.getByText('采购合同', { exact: true })).toBeVisible()

    expect(writes.filter((entry) => entry.path.endsWith('/publish'))).toHaveLength(1)
    expect(writes.filter((entry) => entry.path.endsWith('/quotes/Q1/submit'))).toHaveLength(1)
    expect(writes.filter((entry) => entry.path.endsWith('/award'))).toHaveLength(1)
    expect(writes.filter((entry) => entry.path.endsWith('/link-contract'))).toHaveLength(1)
  })

  test('shows supplier business labels and submits invitations as an id array', async ({
    page,
  }) => {
    const writes = await install(page)
    await page.goto('/supplier-sourcing?projectId=P1')
    await page.getByRole('button', { name: event.sourcingCode, exact: true }).click()
    await page.getByRole('button', { name: '邀请供应商' }).click()
    const inviteDialog = page.getByRole('dialog', { name: '邀请供应商' })
    await inviteDialog
      .getByRole('combobox', { name: '供应商' })
      .selectOption({ label: 'SUP-200 · 供应商乙' })
    await page.getByRole('button', { name: '确认提交' }).click()

    const invitation = writes.find((entry) => entry.path.endsWith('/events/E1/suppliers'))
    expect(invitation?.body).toEqual({ partnerIds: ['P200'] })
  })
})
