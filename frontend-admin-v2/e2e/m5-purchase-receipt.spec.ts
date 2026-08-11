import AxeBuilder from '@axe-core/playwright'
import { expect, test, type Locator, type Page, type Route } from '@playwright/test'
import { captureRuntimeErrors } from './runtime-errors'
import { installShellPreferencesMock } from './shell-session'

async function selectBusinessOption(
  _page: Page,
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
  const trigger = scope.getByRole('combobox', { name: triggerName })
  const option = trigger.locator('option').filter({ hasText: optionName }).first()
  const value = await option.getAttribute('value')
  expect(value).not.toBeNull()
  await trigger.selectOption(value!)
  await expect(trigger).toHaveValue(value!)
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
      deliveryNoteNo: 'DN-001',
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
      acceptedQuantity: '2.0000',
      systemBatchNo: 'SYS-20260801-001',
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
  await installShellPreferencesMock(page)
  const writes: Array<{ path: string; body: unknown }> = []
  await page.route('**/api/auth/userinfo', (route) =>
    fulfill(route, {
      tenantId: '0',
      userId: '1',
      username: 'buyer',
      roles: ['USER'],
      permissions: granted,
    }),
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
  await page.route('**/api/documents/generations**', (route) =>
    fulfill(route, { records: [], total: 0, pageNo: 1, pageSize: 20 }),
  )
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

    if (method === 'GET' && path === '/purchase-requests/form-options')
      return fulfill(route, {
        materials: [
          {
            id: 'M1',
            materialCode: 'MAT-001',
            materialName: '钢筋',
            specification: 'HRB400',
            unit: '吨',
          },
        ],
      })
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
    if (
      method === 'POST' &&
      (path === base || path === `${base}/with-items` || path === `${base}/from-request`)
    ) {
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
      await page.goto(`${path}?projectId=P1`)
      await expect(page).toHaveURL(/\/forbidden\?from=/)
      expect(traffic).toEqual([])
      await page.unrouteAll({ behavior: 'wait' })
    }
  })

  test('renders all routes, redirect, viewports and accessibility', async ({ page }) => {
    test.setTimeout(60_000)
    await install(page)
    const errors = captureRuntimeErrors(page)
    await page.goto('/purchase?projectId=P1')
    await expect(page).toHaveURL(/\/purchase\/order\?projectId=P1/)
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
        await page.goto(`${sample.path}?projectId=P1`)
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
      await page.goto(`${sample.path}`)
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
      await page.goto(`${sample.path}?projectId=P1`)
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
    await page.goto('/inventory/purchase-request?projectId=P1')
    await page.getByRole('button', { name: '新建采购申请' }).click()
    const dialog = page.getByRole('dialog', { name: '新建采购申请' })
    await selectBusinessOption(page, dialog, /^第1条物料$/, /MAT-001 · 钢筋/)
    await dialog.getByLabel('第1条申请数量').fill('9007199254740993.1234')
    await dialog.getByLabel('第1条计划日期').fill('2026-06-18')
    await dialog.getByLabel('第1条使用部位').fill('地下室')
    await dialog.getByRole('button', { name: '保存', exact: true }).dblclick()
    await expect(page.getByText('PR-002', { exact: true }).first()).toBeVisible()
    await page.getByRole('button', { name: '提交审批' }).dblclick()
    await expect(page.getByText('审批中', { exact: true }).first()).toBeVisible()
    const create = writes.find((item) => item.path === 'POST /purchase-requests/with-items')
    expect(create).toBeDefined()
    expect(
      (
        create?.body as {
          items: Array<{ quantity: string }>
        }
      ).items[0]?.quantity,
    ).toBe('9007199254740993.1234')
    expect(writes.filter((item) => item.path.endsWith('/items/batch'))).toHaveLength(0)
    expect(writes.filter((item) => item.path.endsWith('/submit'))).toHaveLength(1)
  })

  test('creates one order from an approved request and submits once', async ({ page }) => {
    const writes = await install(page)
    await page.goto('/purchase/order?projectId=P1')
    await page.getByRole('button', { name: '新建采购订单' }).click()
    const dialog = page.getByRole('dialog', { name: '新建采购订单' })
    await selectBusinessOption(page, dialog, /^已审批采购申请$/, /PR-001/)
    await selectBusinessOption(page, dialog, /^采购合同$/, /CT-001 · 钢材采购合同/)
    await dialog.getByLabel('交付条件').fill('按合同交付')
    await dialog.getByRole('button', { name: '保存', exact: true }).dblclick()
    await expect(page.getByText('PO-002', { exact: true }).first()).toBeVisible()
    await page.getByRole('button', { name: '提交审批' }).dblclick()
    const create = writes.find((item) => item.path === 'POST /purchase-orders/from-request')
    expect(create?.body).toMatchObject({
      projectId: 'P1',
      contractId: 'C1',
      requestId: 'PR1',
      deliveryTerms: '按合同交付',
    })
    expect(writes.filter((item) => item.path.endsWith('/items/batch'))).toHaveLength(0)
    expect(writes.filter((item) => item.path.endsWith('/submit'))).toHaveLength(1)
  })

  test('creates receipts with server-authoritative accepted quantity', async ({ page }) => {
    const writes = await install(page)
    await page.goto('/purchase/receipt?projectId=P1')
    for (const sample of [
      {
        accepted: '3.0000',
        code: 'RC-002',
      },
      { accepted: '5.0000', code: 'RC-003' },
    ]) {
      await page.getByRole('button', { name: '新建材料验收' }).click()
      const dialog = page.getByRole('dialog', { name: '新建材料验收' })
      await selectBusinessOption(page, dialog, /^采购订单$/, /PO-001 · 供应商甲/)
      await dialog.getByRole('radio', { name: '选择钢筋' }).check()
      await selectBusinessOption(page, dialog, /^入库仓库$/, /WH-001 · 主仓/)
      await dialog.getByLabel('本次合格数量').fill(sample.accepted)
      await dialog.getByRole('button', { name: '保存', exact: true }).click()
      await expect(page.getByText(sample.code, { exact: true }).first()).toBeVisible()
      const detailDialog = page.getByRole('dialog', { name: '材料验收详情' })
      await detailDialog.getByRole('button', { name: '关闭', exact: true }).click()
      await expect(detailDialog).toBeHidden()
    }
    const itemWrites = writes.filter((item) => item.path.endsWith('/items/batch'))
    expect(itemWrites).toHaveLength(2)
    expect(
      itemWrites.map(
        (item) => (item.body as Array<{ acceptedQuantity: string }>)[0]?.acceptedQuantity,
      ),
    ).toEqual(['3.0000', '5.0000'])
  })

  test('shows over-receipt 409 and does not retry', async ({ page }) => {
    const writes = await install(page, permissions, true)
    await page.goto('/purchase/receipt?projectId=P1')
    await page.getByRole('button', { name: '新建材料验收' }).click()
    const dialog = page.getByRole('dialog', { name: '新建材料验收' })
    await selectBusinessOption(page, dialog, /^采购订单$/, /PO-001 · 供应商甲/)
    await selectBusinessOption(page, dialog, /^入库仓库$/, /WH-001 · 主仓/)
    await dialog.getByLabel('本次合格数量').fill('99')
    await dialog.getByRole('button', { name: '保存', exact: true }).dblclick()
    await expect(
      page.getByText('验收数量超过订单剩余数量；本次新建草稿已回滚', { exact: true }),
    ).toBeVisible()
    expect(writes.filter((item) => item.path.endsWith('/items/batch'))).toHaveLength(1)
    expect(writes.filter((item) => item.path === 'DELETE /receipts/RC2')).toHaveLength(1)
  })
})
