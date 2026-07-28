import AxeBuilder from '@axe-core/playwright'
import { expect, test, type Locator, type Page, type Route } from '@playwright/test'
import { captureRuntimeErrors } from './runtime-errors'

async function selectBusinessOption(
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
  const option = scope.getByRole('option', { name: optionName })
  await expect(option).toBeVisible()
  await option.focus()
  await option.press('Enter')
  await expect(trigger).toHaveAccessibleName(optionName)
}

const permissions = [
  'purchase:request:list',
  'purchase:request:add',
  'purchase:request:edit',
  'purchase:request:delete',
  'purchase:request:submit',
  'purchase:order:query',
  'purchase:order:add',
  'purchase:order:edit',
  'purchase:order:delete',
  'purchase:order:submit',
  'receipt:query',
  'receipt:add',
  'receipt:edit',
  'receipt:delete',
  'receipt:submit',
]

const state = {
  requests: [
    {
      id: 'PR1',
      tenantId: 'T1',
      projectId: 'P1',
      projectName: '示范项目',
      contractId: 'C1',
      contractName: '钢材采购合同',
      requestCode: 'PR-001',
      purpose: '主体结构',
      approvalStatus: 'DRAFT',
      status: 'DRAFT',
    },
  ],
  requestItems: [
    {
      id: 'PRI1',
      requestId: 'PR1',
      materialId: 'M1',
      materialName: '钢筋',
      quantity: '10.0000',
      estimatedUnitPrice: '3.25',
    },
  ],
  orders: [
    {
      id: 'PO1',
      tenantId: 'T1',
      projectId: 'P1',
      projectName: '示范项目',
      requestId: 'PR1',
      contractId: 'C1',
      partnerId: 'S1',
      partnerName: '供应商甲',
      orderCode: 'PO-001',
      totalAmount: '32.50',
      approvalStatus: 'DRAFT',
      orderStatus: 'DRAFT',
    },
  ],
  orderItems: [
    {
      id: 'POI1',
      orderId: 'PO1',
      requestItemId: 'PRI1',
      materialId: 'M1',
      materialName: '钢筋',
      quantity: '10.0000',
      unitPrice: '3.25',
      receivedQuantity: '2.0000',
    },
  ],
  receipts: [
    {
      id: 'RC1',
      tenantId: 'T1',
      projectId: 'P1',
      projectName: '示范项目',
      orderId: 'PO1',
      orderCode: 'PO-001',
      receiptCode: 'RC-001',
      totalAmount: '6.50',
      approvalStatus: 'DRAFT',
      qualityStatus: 'PARTIAL',
    },
  ],
  receiptItems: [
    {
      id: 'RCI1',
      receiptId: 'RC1',
      orderItemId: 'POI1',
      materialName: '钢筋',
      actualQuantity: '2.0000',
      qualifiedQuantity: '2.0000',
      unqualifiedQuantity: '0.0000',
      orderedQuantity: '10.0000',
      receivedQuantity: '2.0000',
      remainingQuantity: '8.0000',
    },
  ],
}

async function fulfill(route: Route, data: unknown, status = 200, code = '0') {
  await route.fulfill({
    status,
    contentType: 'application/json',
    body: JSON.stringify({
      code,
      message: status === 200 ? 'success' : '验收数量超过订单剩余数量',
      data,
    }),
  })
}

async function install(page: Page, granted = permissions, rejectReceiptItems = false) {
  const writes: Array<{ path: string; body: unknown }> = []
  await page.route('**/api/auth/userinfo', (route) =>
    fulfill(route, { userId: '1', username: 'buyer', roles: ['USER'], permissions: granted }),
  )
  await page.route('**/api/auth/refresh', (route) => fulfill(route, null, 401))
  await page.route('**/api/project-context/options', (route) =>
    fulfill(route, [{ id: 'P1', projectName: `示范项目${'超长'.repeat(30)}`, status: 'ACTIVE' }]),
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
  await page.route('**/api/contracts**', (route) =>
    fulfill(route, {
      records: [
        {
          id: 'C1',
          contractCode: 'CT-001',
          contractName: '钢材采购合同',
          contractType: 'PURCHASE',
          approvalStatus: 'APPROVED',
        },
      ],
      total: 1,
      pageNo: 1,
      pageSize: 200,
    }),
  )
  await page.route('**/api/project-budgets**', (route) => {
    const path = new URL(route.request().url()).pathname
    return path === '/api/project-budgets/B1'
      ? fulfill(route, {
          id: 'B1',
          active: true,
          status: 'ACTIVE',
          lines: [{ id: 'BL1', costSubjectName: '钢材采购' }],
        })
      : fulfill(route, {
          records: [{ id: 'B1', active: true, status: 'ACTIVE' }],
          total: 1,
          pageNo: 1,
          pageSize: 100,
        })
  })
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
  await page.route('**/api/files**', (route) => fulfill(route, []))
  await page.route('**/api/{purchase-requests,purchase-orders,receipts}**', async (route) => {
    const request = route.request()
    const url = new URL(request.url())
    const path = url.pathname.replace('/api', '')
    const method = request.method()
    const body = request.postDataJSON() as Record<string, unknown> | undefined
    if (method !== 'GET') writes.push({ path: `${method} ${path}`, body })

    const domain = path.startsWith('/purchase-requests')
      ? 'request'
      : path.startsWith('/purchase-orders')
        ? 'order'
        : 'receipt'
    const collection =
      domain === 'request' ? state.requests : domain === 'order' ? state.orders : state.receipts
    const items =
      domain === 'request'
        ? state.requestItems
        : domain === 'order'
          ? state.orderItems
          : state.receiptItems
    const base =
      domain === 'request'
        ? '/purchase-requests'
        : domain === 'order'
          ? '/purchase-orders'
          : '/receipts'

    if (method === 'GET' && path === base)
      return fulfill(route, {
        records: collection,
        total: collection.length,
        pageNo: 1,
        pageSize: 100,
      })
    if (method === 'GET' && path.endsWith('/items')) return fulfill(route, items)
    if (method === 'GET')
      return fulfill(
        route,
        collection.find((item) => path === `${base}/${item.id}`),
      )
    if (method === 'POST' && path === base) {
      const sequence = collection.length + 1
      const id = `${domain === 'request' ? 'PR' : domain === 'order' ? 'PO' : 'RC'}${sequence}`
      const next = {
        id,
        tenantId: 'T1',
        projectId: 'P1',
        projectName: '示范项目',
        approvalStatus: 'DRAFT',
        ...(domain === 'request' ? { requestCode: `PR-00${sequence}`, status: 'DRAFT' } : {}),
        ...(domain === 'order'
          ? { orderCode: `PO-00${sequence}`, orderStatus: 'DRAFT', requestId: body?.requestId }
          : {}),
        ...(domain === 'receipt'
          ? { receiptCode: `RC-00${sequence}`, qualityStatus: 'PENDING', orderId: body?.orderId }
          : {}),
      }
      collection.push(next as never)
      return fulfill(route, id)
    }
    if (path.endsWith('/items/batch')) {
      if (domain === 'receipt' && rejectReceiptItems)
        return fulfill(route, null, 409, 'RECEIPT_QUANTITY_EXCEEDED')
      items.push(...(((body as unknown as object[]) ?? []) as never[]))
      return fulfill(route, null)
    }
    if (path.endsWith('/submit')) {
      const id = path.split('/').at(-2)
      const record = collection.find((item) => item.id === id)
      if (record) record.approvalStatus = 'APPROVING'
      return fulfill(route, null)
    }
    return fulfill(route, null)
  })
  return writes
}

test.describe('M5 purchase request, order and receipt V2', () => {
  test.beforeEach(() => {
    state.requests.splice(1)
    state.orders.splice(1)
    state.receipts.splice(1)
    state.requestItems.splice(1)
    state.orderItems.splice(1)
    state.receiptItems.splice(1)
    state.requests[0]!.approvalStatus = 'DRAFT'
    state.orders[0]!.approvalStatus = 'DRAFT'
    state.receipts[0]!.approvalStatus = 'DRAFT'
  })

  test('fails closed on each query permission before business traffic', async ({ page }) => {
    for (const path of ['/inventory/purchase-request', '/purchase/order', '/purchase/receipt']) {
      const traffic: string[] = []
      page.on('request', (request) => {
        if (/purchase-requests|purchase-orders|receipts/.test(request.url()))
          traffic.push(request.url())
      })
      await install(page, [])
      await page.goto(`/v2${path}?projectId=P1`)
      await expect(page).toHaveURL(/\/v2\/forbidden\?from=/)
      expect(traffic).toEqual([])
      await page.unrouteAll({ behavior: 'wait' })
    }
  })

  test('renders all routes, redirect, viewports and accessibility', async ({ page }) => {
    await install(page)
    const errors = captureRuntimeErrors(page)
    await page.goto('/v2/purchase?projectId=P1')
    await expect(page).toHaveURL(/\/v2\/purchase\/order\?projectId=P1/)
    for (const sample of [
      { path: '/inventory/purchase-request', text: 'PR-001' },
      { path: '/purchase/order', text: 'PO-001' },
      { path: '/purchase/receipt', text: 'RC-001' },
    ]) {
      for (const viewport of [
        { width: 1440, height: 900 },
        { width: 1024, height: 768 },
        { width: 390, height: 844 },
      ]) {
        await page.setViewportSize(viewport)
        await page.goto(`/v2${sample.path}?projectId=P1`)
        const surface = page.locator('.purchase-execution-page__table-wrap')
        await expect(surface.getByText(sample.text, { exact: true })).toBeVisible()
        expect(
          await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth),
        ).toBe(true)
      }
    }
    const axe = await new AxeBuilder({ page }).include('.purchase-execution-page').analyze()
    expect(
      axe.violations.filter((item) => ['serious', 'critical'].includes(item.impact ?? '')),
    ).toEqual([])
    expect(errors).toEqual([])
  })

  test('loads authorized purchase facts for all projects', async ({ page }) => {
    const traffic: string[] = []
    page.on('request', (request) => {
      if (/purchase-requests|purchase-orders|receipts/.test(request.url()))
        traffic.push(request.url())
    })
    await install(page)
    for (const sample of [
      { path: '/inventory/purchase-request', text: 'PR-001' },
      { path: '/purchase/order', text: 'PO-001' },
      { path: '/purchase/receipt', text: 'RC-001' },
    ]) {
      await page.goto(`/v2${sample.path}`)
      await expect(page.getByText(sample.text, { exact: true }).first()).toBeVisible()
      await expect(page.getByText(/请(?:先)?选择项目/)).toHaveCount(0)
    }
    expect(traffic.length).toBeGreaterThanOrEqual(3)
    expect(traffic.every((url) => !new URL(url).searchParams.has('projectId'))).toBe(true)
  })

  for (const sample of [
    {
      path: '/inventory/purchase-request',
      label: '采购申请',
      query: 'purchase:request:list',
      add: 'purchase:request:add',
      edit: 'purchase:request:edit',
      delete: 'purchase:request:delete',
    },
    {
      path: '/purchase/order',
      label: '采购订单',
      query: 'purchase:order:query',
      add: 'purchase:order:add',
      edit: 'purchase:order:edit',
      delete: 'purchase:order:delete',
    },
    {
      path: '/purchase/receipt',
      label: '材料验收',
      query: 'receipt:query',
      add: 'receipt:add',
      edit: 'receipt:edit',
      delete: 'receipt:delete',
    },
  ]) {
    test(`${sample.add}, ${sample.edit} and ${sample.delete} fail closed`, async ({ page }) => {
      await install(page, [sample.query, sample.add])
      await page.goto(`/v2${sample.path}?projectId=P1`)
      await expect(page.getByRole('button', { name: `新建${sample.label}` })).toHaveCount(0)
      await install(page, [sample.query, sample.add, sample.edit])
      await page.reload()
      await expect(page.getByRole('button', { name: `新建${sample.label}` })).toHaveCount(0)
      await install(page, [sample.query, sample.add, sample.edit, sample.delete])
      await page.reload()
      await expect(page.getByRole('button', { name: `新建${sample.label}` })).toBeVisible()
    })
  }

  test('creates one request with decimal strings and submits once', async ({ page }) => {
    const writes = await install(page)
    await page.goto('/v2/inventory/purchase-request?projectId=P1')
    await page.getByRole('button', { name: '新建采购申请' }).click()
    const dialog = page.getByRole('dialog', { name: '新建采购申请' })
    await selectBusinessOption(dialog, /^采购合同：/, /CT-001 · 钢材采购合同/)
    await selectBusinessOption(dialog, /^物料：/, /MAT-001 · 钢筋/)
    await selectBusinessOption(dialog, /^预算科目：/, /钢材采购/)
    await dialog.getByLabel('采购用途').fill('地下室钢材')
    await dialog.getByLabel('申请数量').fill('9007199254740993.1234')
    await dialog.getByLabel('预计单价').fill('3.25')
    await dialog.getByLabel('计划日期').fill('2026-06-18')
    await dialog.getByRole('button', { name: '保存', exact: true }).dblclick()
    await expect(page.getByText('PR-002', { exact: true }).first()).toBeVisible()
    await page.getByRole('button', { name: '提交审批' }).dblclick()
    await expect(page.getByText('审批中', { exact: true }).first()).toBeVisible()
    expect(writes.filter((item) => item.path === 'POST /purchase-requests')).toHaveLength(1)
    expect(writes.filter((item) => item.path.endsWith('/items/batch'))).toHaveLength(1)
    expect(
      (
        writes.find((item) => item.path.endsWith('/items/batch'))?.body as Array<{
          quantity: string
        }>
      )[0]?.quantity,
    ).toBe('9007199254740993.1234')
    expect(writes.filter((item) => item.path.endsWith('/submit'))).toHaveLength(1)
  })

  test('creates an order from request source and submits once', async ({ page }) => {
    const writes = await install(page)
    await page.goto('/v2/purchase/order?projectId=P1')
    await page.getByRole('button', { name: '新建采购订单' }).click()
    const dialog = page.getByRole('dialog', { name: '新建采购订单' })
    await selectBusinessOption(dialog, /^采购申请：/, /PR-001 · 主体结构/)
    await selectBusinessOption(dialog, /^供应商：/, /SUP-001 · 供应商甲/)
    await dialog.getByLabel('订单数量').fill('10.0000')
    await dialog.getByLabel('订单单价').fill('3.25')
    await dialog.getByLabel('税率').fill('13')
    await dialog.getByRole('button', { name: '保存', exact: true }).dblclick()
    await expect(page.getByText('PO-002', { exact: true }).first()).toBeVisible()
    await page.getByRole('button', { name: '提交审批' }).dblclick()
    const create = writes.find((item) => item.path === 'POST /purchase-orders')
    expect(create?.body).toMatchObject({ requestId: 'PR1', partnerId: 'S1' })
    const items = writes.find((item) => item.path.endsWith('/items/batch'))?.body as Array<{
      requestItemId: string
      quantity: string
    }>
    expect(items[0]).toMatchObject({ requestItemId: 'PRI1', quantity: '10.0000' })
    expect(writes.filter((item) => item.path.endsWith('/submit'))).toHaveLength(1)
  })

  test('creates partial and complete receipts without client quantity totals', async ({ page }) => {
    const writes = await install(page)
    await page.goto('/v2/purchase/receipt?projectId=P1')
    for (const sample of [
      {
        actual: '3.0000',
        qualified: '2.5000',
        unqualified: '0.5000',
        code: 'RC-002',
        disposition: '退货',
      },
      { actual: '5.0000', qualified: '5.0000', unqualified: '0.0000', code: 'RC-003' },
    ]) {
      await page.getByRole('button', { name: '新建材料验收' }).click()
      const dialog = page.getByRole('dialog', { name: '新建材料验收' })
      await selectBusinessOption(dialog, /^采购订单：/, /PO-001 · 供应商甲/)
      await expect(dialog.getByLabel(/订单明细：钢筋 · 剩余 8.0000/)).toBeVisible()
      await selectBusinessOption(dialog, /^入库仓库：/, /WH-001 · 主仓/)
      await dialog.getByLabel('实收数量').fill(sample.actual)
      await dialog.getByLabel('合格数量', { exact: true }).fill(sample.qualified)
      await dialog.getByLabel('不合格数量', { exact: true }).fill(sample.unqualified)
      if (sample.disposition) {
        await selectBusinessOption(dialog, /^不合格处置：/, new RegExp(sample.disposition))
        await dialog.getByLabel('处置原因').fill('外观检验不合格')
      }
      await dialog.getByRole('button', { name: '保存', exact: true }).click()
      await expect(page.getByText(sample.code, { exact: true }).first()).toBeVisible()
      const detailDialog = page.getByRole('dialog', { name: '材料验收详情' })
      await detailDialog.getByRole('button', { name: '关闭' }).click()
      await expect(detailDialog).toBeHidden()
    }
    const itemWrites = writes.filter((item) => item.path.endsWith('/items/batch'))
    expect(itemWrites).toHaveLength(2)
    expect(
      itemWrites.map((item) => (item.body as Array<{ actualQuantity: string }>)[0]?.actualQuantity),
    ).toEqual(['3.0000', '5.0000'])
  })

  test('shows over-receipt 409 and does not retry', async ({ page }) => {
    const writes = await install(page, permissions, true)
    await page.goto('/v2/purchase/receipt?projectId=P1')
    await page.getByRole('button', { name: '新建材料验收' }).click()
    const dialog = page.getByRole('dialog', { name: '新建材料验收' })
    await selectBusinessOption(dialog, /^采购订单：/, /PO-001 · 供应商甲/)
    await selectBusinessOption(dialog, /^入库仓库：/, /WH-001 · 主仓/)
    await dialog.getByLabel('实收数量').fill('99')
    await dialog.getByLabel('合格数量', { exact: true }).fill('99')
    await dialog.getByLabel('不合格数量', { exact: true }).fill('0')
    await dialog.getByRole('button', { name: '保存', exact: true }).dblclick()
    await expect(
      page.getByText('验收数量超过订单剩余数量；本次新建草稿已回滚', { exact: true }),
    ).toBeVisible()
    expect(writes.filter((item) => item.path.endsWith('/items/batch'))).toHaveLength(1)
    expect(writes.filter((item) => item.path === 'DELETE /receipts/RC2')).toHaveLength(1)
  })
})
