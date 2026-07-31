import AxeBuilder from '@axe-core/playwright'
import { expect, test, type Locator, type Page, type Route } from '@playwright/test'
import { captureRuntimeErrors } from './runtime-errors'

async function selectBusinessOption(
  page: Page,
  scope: Locator,
  triggerName: RegExp,
  optionName: RegExp,
): Promise<void> {
  await scope.evaluate(async (element) => {
    await Promise.all(
      element
        .getAnimations({ subtree: true })
        .map((animation) => animation.finished.catch(() => undefined)),
    )
  })
  const trigger = scope.getByRole('button', { name: triggerName })
  await trigger.click()
  const option = page.getByRole('option', { name: optionName })
  await expect(option).toBeVisible()
  await option.focus()
  await option.press('Enter')
  await expect(trigger).toHaveAccessibleName(optionName)
}

const permissions = [
  'requisition:query',
  'requisition:add',
  'requisition:edit',
  'requisition:delete',
  'requisition:submit',
  'requisition:stock-out',
  'requisition:return',
  'procurement:trace:query',
]
const requisitions = [
  {
    id: 'R1',
    tenantId: 'T1',
    projectId: 'P1',
    projectName: '示范项目',
    contractId: 'C1',
    requisitionCode: 'REQ-001',
    requisitionDate: '2026-07-24',
    warehouseId: 'W1',
    warehouseCode: 'WH-001',
    warehouseName: '主仓',
    approvalStatus: 'DRAFT',
    totalAmount: '32.50',
    stockOutFlag: '0',
  },
]
const items = [
  {
    id: 'RI1',
    requisitionId: 'R1',
    materialId: 'M1',
    materialName: `钢筋${'超长'.repeat(20)}`,
    quantity: '10.0000',
    unitPrice: '3.25',
    amount: '32.50',
    useLocation: '主体结构',
  },
]
const stockTransactions = [
  {
    id: 'TX1',
    warehouseId: 'W1',
    materialId: 'M1',
    txnType: 'OUT',
    quantity: '10.0000',
    availableAfter: '70.0000',
    unitCost: '3.25',
    amount: '32.50',
    sourceType: 'MAT_REQUISITION',
    sourceId: 'R1',
    sourceLineId: 'RI1',
  },
]
let materialReturn: Record<string, unknown> | null = null

async function fulfill(route: Route, data: unknown, status = 200, code = '0') {
  await route.fulfill({
    status,
    contentType: 'application/json',
    body: JSON.stringify({ code, message: status === 200 ? 'success' : '领退料状态已变化', data }),
  })
}

async function install(page: Page, granted = permissions, rejectStockOut = false) {
  const writes: Array<{ path: string; body: unknown }> = []
  await page.route('**/api/auth/userinfo', (route) =>
    fulfill(route, { userId: '1', username: 'keeper', roles: ['USER'], permissions: granted }),
  )
  await page.route('**/api/auth/refresh', (route) => fulfill(route, null, 401))
  await page.route('**/api/project-context/options', (route) =>
    fulfill(route, [{ id: 'P1', projectName: '示范项目', status: 'ACTIVE' }]),
  )
  await page.route('**/api/inventory/warehouses**', (route) =>
    fulfill(route, {
      records: [
        {
          id: 'W1',
          tenantId: 'T1',
          projectId: 'P1',
          warehouseCode: 'WH-001',
          warehouseName: '主仓',
          status: 'ENABLE',
        },
      ],
      total: 1,
      pageNo: 1,
      pageSize: 200,
    }),
  )
  await page.route('**/api/materials**', (route) =>
    fulfill(route, {
      records: [
        {
          id: 'M1',
          materialCode: 'MAT-001',
          materialName: '钢筋',
          specification: 'HRB400',
          unit: '吨',
          status: 'ENABLE',
        },
      ],
      total: 1,
      pageNo: 1,
      pageSize: 200,
    }),
  )
  await page.route('**/api/partners**', (route) =>
    fulfill(route, {
      records: [
        {
          id: 'S1',
          partnerCode: 'SUP-001',
          partnerName: '供应商甲',
          partnerType: 'SUPPLIER',
          status: 'ENABLE',
        },
      ],
    }),
  )
  await page.route('**/api/contracts**', (route) =>
    fulfill(route, {
      records: [
        {
          id: 'C1',
          projectId: 'P1',
          contractCode: 'CT-001',
          contractName: '示范项目材料合同',
          contractStatus: 'PERFORMING',
        },
      ],
      total: 1,
      pageNo: 1,
      pageSize: 200,
    }),
  )
  await page.route('**/api/{requisitions,procurement-traces,material-returns}**', async (route) => {
    const request = route.request()
    const path = new URL(request.url()).pathname.replace('/api', '')
    const method = request.method()
    const body = request.postDataJSON() as Record<string, unknown> | undefined
    if (method !== 'GET') writes.push({ path: `${method} ${path}`, body })

    if (path === '/requisitions' && method === 'GET')
      return fulfill(route, {
        records: requisitions,
        total: requisitions.length,
        pageNo: 1,
        pageSize: 20,
      })
    if (path === '/requisitions' && method === 'POST') {
      requisitions.push({
        ...requisitions[0]!,
        id: 'R2',
        requisitionCode: 'REQ-002',
        approvalStatus: 'DRAFT',
        stockOutFlag: '0',
      })
      return fulfill(route, 'R2')
    }
    if (path.endsWith('/items/batch')) {
      items.push(...((body as unknown as typeof items) ?? []))
      return fulfill(route, null)
    }
    if (path.endsWith('/items') && path.startsWith('/requisitions/')) return fulfill(route, items)
    if (path.endsWith('/submit')) {
      const id = path.split('/').at(-2)
      const requisition = requisitions.find((item) => item.id === id)
      if (requisition) requisition.approvalStatus = 'APPROVING'
      return fulfill(route, null)
    }
    if (path.endsWith('/stock-out')) {
      if (rejectStockOut) return fulfill(route, null, 409, 'REQUISITION_ALREADY_STOCKED_OUT')
      requisitions[0]!.stockOutFlag = '1'
      requisitions[0]!.approvalStatus = 'APPROVED'
      return fulfill(route, null)
    }
    if (path.startsWith('/requisitions/') && method === 'GET')
      return fulfill(
        route,
        requisitions.find((item) => path === `/requisitions/${item.id}`),
      )
    if (path.startsWith('/requisitions/') && method === 'DELETE') return fulfill(route, null)
    if (path.startsWith('/procurement-traces/requisitions/'))
      return fulfill(route, {
        requisition: requisitions[0],
        requisitionItems: items,
        stockTransactions: requisitions[0]!.stockOutFlag === '1' ? stockTransactions : [],
        costs:
          requisitions[0]!.stockOutFlag === '1'
            ? [{ id: 'COST1', amount: '32.50', sourceItemId: 'RI1' }]
            : [],
        materialReturn,
        materialReturnItems: materialReturn
          ? [
              {
                id: 'MRI1',
                returnId: 'MR1',
                requisitionItemId: 'RI1',
                originalStockTxnId: 'TX1',
                materialId: 'M1',
                quantity: '1.0000',
                unitCost: '3.25',
                amount: '3.25',
              },
            ]
          : [],
        approvalInstances: [],
        approvalRecords: [],
      })
    if (path === '/material-returns/confirm') {
      materialReturn = {
        id: 'MR1',
        requisitionId: 'R1',
        returnCode: 'RET-001',
        returnDate: body?.returnDate,
        status: 'CONFIRMED',
        reason: body?.reason,
        totalAmount: '3.25',
        idempotencyKey: body?.idempotencyKey,
      }
      return fulfill(route, 'MR1')
    }
    if (path === '/material-returns/MR1') return fulfill(route, materialReturn)
    if (path === '/material-returns/MR1/items')
      return fulfill(route, [
        {
          id: 'MRI1',
          returnId: 'MR1',
          requisitionItemId: 'RI1',
          originalStockTxnId: 'TX1',
          materialId: 'M1',
          quantity: '1.0000',
          unitCost: '3.25',
          amount: '3.25',
        },
      ])
    if (path.endsWith('/reverse')) {
      if (materialReturn) materialReturn.status = 'REVERSED'
      return fulfill(route, 'MR2')
    }
    return fulfill(route, null)
  })
  return writes
}

async function openDetail(page: Page) {
  await page.getByRole('button', { name: 'REQ-001', exact: true }).click()
  await expect(page.getByRole('dialog', { name: '领退料链路' })).toBeVisible()
  await expect(page.getByRole('dialog', { name: '领退料链路' }).getByText('当前申请')).toBeVisible()
}

test.describe('M5 requisition, stock-out and return V2', () => {
  test.beforeEach(() => {
    requisitions.splice(1)
    requisitions[0]!.approvalStatus = 'DRAFT'
    requisitions[0]!.stockOutFlag = '0'
    items.splice(1)
    materialReturn = null
  })

  test('fails closed before business traffic without query permission', async ({ page }) => {
    const traffic: string[] = []
    page.on(
      'request',
      (request) =>
        /requisitions|material-returns/.test(request.url()) && traffic.push(request.url()),
    )
    await install(page, [])
    await page.goto('/inventory/material-requisition')
    await expect(page).toHaveURL(/\/forbidden\?from=/)
    expect(traffic).toEqual([])
  })

  test('renders pagination surface, three viewports and accessibility', async ({ page }) => {
    await install(page)
    const errors = captureRuntimeErrors(page)
    for (const viewport of [
      { width: 1440, height: 900 },
      { width: 1024, height: 768 },
      { width: 390, height: 844 },
    ]) {
      await page.setViewportSize(viewport)
      await page.goto('/inventory/material-requisition?projectId=P1')
      await expect(page.getByRole('button', { name: 'REQ-001', exact: true })).toBeVisible()
      expect(
        await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth),
      ).toBe(true)
      if (viewport.width === 390) {
        const header = page.locator('.v2-card--page-heading > .v2-card__header')
        await expect(header).toHaveCSS('flex-direction', 'column')
        const [headerBox, actionsBox] = await Promise.all([
          header.boundingBox(),
          header.locator(':scope > .v2-card__actions').boundingBox(),
        ])
        expect(actionsBox?.width).toBeGreaterThan((headerBox?.width ?? 0) * 0.9)
      }
    }
    const axe = await new AxeBuilder({ page }).include('.requisition-page').analyze()
    expect(
      axe.violations.filter((item) => ['serious', 'critical'].includes(item.impact ?? '')),
    ).toEqual([])
    expect(errors).toEqual([])
  })

  test('requires add and edit before showing create action', async ({ page }) => {
    await install(page, ['requisition:query', 'requisition:add'])
    await page.goto('/inventory/material-requisition?projectId=P1')
    await expect(page.getByRole('button', { name: '发起领料申请' })).toHaveCount(0)
    await install(page, ['requisition:query', 'requisition:add', 'requisition:edit'])
    await page.reload()
    await expect(page.getByRole('button', { name: '发起领料申请' })).toBeVisible()
  })

  test('maps report period to server dates and shows project in all-project view', async ({
    page,
  }) => {
    const listRequests: string[] = []
    page.on('request', (request) => {
      if (new URL(request.url()).pathname === '/api/requisitions') listRequests.push(request.url())
    })
    await install(page)
    await page.goto('/inventory/material-requisition?period=2026-07')
    await expect(
      page.getByRole('region', { name: '领料申请列表' }).getByText('示范项目', { exact: true }),
    ).toBeVisible()
    await expect
      .poll(() => listRequests.some((url) => url.includes('dateFrom=2026-07-01')))
      .toBe(true)
    expect(listRequests.some((url) => url.includes('dateTo=2026-07-31'))).toBe(true)
  })

  test('query-only role opens detail without requesting trace and keeps return hidden', async ({
    page,
  }) => {
    const traceRequests: string[] = []
    page.on('request', (request) => {
      if (request.url().includes('/procurement-traces/')) traceRequests.push(request.url())
    })
    await install(page, ['requisition:query'])
    await page.goto('/inventory/material-requisition?projectId=P1')
    await openDetail(page)
    await expect(page.getByText('无追溯权限').first()).toBeVisible()
    await expect(page.getByRole('button', { name: '发起退料' })).toHaveCount(0)
    expect(traceRequests).toEqual([])
  })

  for (const sample of [
    {
      permission: 'requisition:submit',
      approvalStatus: 'DRAFT',
      stockOutFlag: '0',
      action: '提交审批',
    },
    {
      permission: 'requisition:stock-out',
      approvalStatus: 'APPROVED',
      stockOutFlag: '0',
      action: '执行出库',
    },
    {
      permission: 'requisition:return',
      approvalStatus: 'APPROVED',
      stockOutFlag: '1',
      action: '发起退料',
    },
  ]) {
    test(`${sample.permission} fails closed for ordinary user`, async ({ page }) => {
      requisitions[0]!.approvalStatus = sample.approvalStatus
      requisitions[0]!.stockOutFlag = sample.stockOutFlag
      await install(
        page,
        permissions.filter((permission) => permission !== sample.permission),
      )
      await page.goto('/inventory/material-requisition?projectId=P1')
      await openDetail(page)
      await expect(page.getByRole('button', { name: sample.action, exact: true })).toHaveCount(0)
    })
  }

  test('creates decimal-string requisition and submits once', async ({ page }) => {
    const writes = await install(page)
    await page.goto('/inventory/material-requisition?projectId=P1')
    await page.getByRole('button', { name: '发起领料申请' }).click()
    const editor = page.getByRole('dialog', { name: '发起领料申请' })
    await selectBusinessOption(page, editor, /^合同：/, /CT-001 · 示范项目材料合同/)
    await selectBusinessOption(page, editor, /^领用仓库：/, /WH-001 · 主仓/)
    await selectBusinessOption(page, editor, /^物料：/, /MAT-001 · 钢筋/)
    await editor.getByLabel('领用数量').fill('9007199254740993.1234')
    await editor.getByLabel('参考单价').fill('3.25')
    await editor.getByRole('button', { name: '保存并提交审批' }).dblclick()
    await expect(page.getByText('REQ-002', { exact: true }).first()).toBeVisible()
    expect(writes.filter((item) => item.path === 'POST /requisitions')).toHaveLength(1)
    expect(writes.filter((item) => item.path.endsWith('/items/batch'))).toHaveLength(1)
    expect(writes.filter((item) => item.path.endsWith('/submit'))).toHaveLength(1)
  })

  test('stock-out follows approved server state and re-reads trace', async ({ page }) => {
    requisitions[0]!.approvalStatus = 'APPROVED'
    const writes = await install(page)
    await page.goto('/inventory/material-requisition?projectId=P1')
    await openDetail(page)
    await page.getByRole('button', { name: '执行出库' }).dblclick()
    await expect(page.getByText(/TX1|库存流水/).first()).toBeVisible()
    expect(writes.filter((item) => item.path.endsWith('/stock-out'))).toHaveLength(1)
  })

  test('confirms return with source line and idempotency then reverses', async ({ page }) => {
    requisitions[0]!.approvalStatus = 'APPROVED'
    requisitions[0]!.stockOutFlag = '1'
    const writes = await install(page)
    await page.goto('/inventory/material-requisition?projectId=P1')
    await openDetail(page)
    await page.getByRole('button', { name: '发起退料' }).click()
    const dialog = page.getByRole('dialog', { name: '发起退料' })
    await expect(dialog).toBeVisible()
    await page.waitForTimeout(300)
    await dialog.getByRole('button', { name: /领料明细/ }).click()
    await page.getByRole('option', { name: /钢筋/ }).click()
    await dialog.getByRole('button', { name: /原出库流水/ }).click()
    await page.getByRole('option', { name: /出库/ }).click()
    await page.getByLabel('退料数量').fill('1.0000')
    await page.getByLabel('退料原因').fill('现场余料')
    await page.getByRole('button', { name: '确认退料' }).dblclick()
    await expect(page.getByText(/RET-001 · 已确认/)).toBeVisible()
    const returned = writes.find((item) => item.path === 'POST /material-returns/confirm')
      ?.body as { idempotencyKey: string; originalStockTxnId: string }
    expect(returned.idempotencyKey).toBeTruthy()
    expect(returned.originalStockTxnId).toBe('TX1')
    await page.getByRole('button', { name: '冲销退料' }).click()
    await page.getByLabel('冲销原因').fill('误操作')
    await page.getByRole('button', { name: '确认冲销' }).click()
    expect(writes.filter((item) => item.path.endsWith('/reverse'))).toHaveLength(1)
  })

  test('propagates duplicate stock-out conflict without retry', async ({ page }) => {
    requisitions[0]!.approvalStatus = 'APPROVED'
    const writes = await install(page, permissions, true)
    await page.goto('/inventory/material-requisition?projectId=P1')
    await openDetail(page)
    await page.getByRole('button', { name: '执行出库' }).dblclick()
    await expect(page.getByText('领退料状态已变化', { exact: true }).first()).toBeVisible()
    expect(writes.filter((item) => item.path.endsWith('/stock-out'))).toHaveLength(1)
  })
})
