import AxeBuilder from '@axe-core/playwright'
import { expect, test, type Locator, type Page, type Route } from '@playwright/test'
import { captureRuntimeErrors } from './runtime-errors'
import { installShellPreferencesMock } from './shell-session'

const permissions = [
  'subtask:query',
  'subtask:add',
  'subtask:edit',
  'subtask:delete',
  'subcontract:measure:query',
  'subcontract:measure:add',
  'subcontract:measure:edit',
  'subcontract:measure:delete',
  'subcontract:measure:submit',
]

const task = {
  id: 'T1',
  tenantId: 'TENANT1',
  projectId: 'P1',
  projectName: '示范项目',
  contractId: 'C1',
  contractName: '主体劳务分包合同',
  partnerId: 'S1',
  partnerName: '劳务公司甲',
  taskCode: 'ST-001',
  taskName: '地下室劳务',
  progressPercent: '33.3300',
  status: 'IN_PROGRESS',
}
const measure = {
  id: 'M1',
  tenantId: 'TENANT1',
  projectId: 'P1',
  projectName: '示范项目',
  contractId: 'C1',
  contractName: '主体劳务分包合同',
  partnerId: 'S1',
  partnerName: '劳务公司甲',
  subTaskId: 'T1',
  subTaskCode: 'ST-001',
  subTaskName: '地下室劳务',
  measureCode: 'SM-001',
  measurePeriod: '2026-07',
  measureDate: '2026-07-25',
  reportedAmount: '9007199254740993.1234',
  approvedAmount: null,
  deductionAmount: '0.0000',
  netAmount: '9007199254740993.1234',
  approvalStatus: 'DRAFT',
  status: 'DRAFT',
}

async function fulfill(route: Route, data: unknown, status = 200, code = '0') {
  await route.fulfill({
    status,
    contentType: 'application/json',
    body: JSON.stringify({ code, message: status === 200 ? 'success' : 'request failed', data }),
  })
}

async function selectOption(_page: Page, scope: Locator, label: RegExp, option: RegExp) {
  await expect(scope).toHaveCSS('transform', 'none')
  const control = scope.getByRole('combobox', { name: label })
  const choice = control.locator('option').filter({ hasText: option }).first()
  const value = await choice.getAttribute('value')
  expect(value).not.toBeNull()
  await control.selectOption(value!)
}

async function install(page: Page, granted = permissions) {
  await installShellPreferencesMock(page)
  const tasks = [{ ...task }]
  const measures = [{ ...measure }]
  const items: Record<string, unknown[]> = { M1: [] }
  const files: Array<{ id: string; originalName: string }> = []
  const writes: string[] = []
  const controls = { failMeasureDetail: false }
  await page.route('**/api/auth/userinfo', (route) =>
    fulfill(route, {
      userId: '1',
      username: 'subcontractor',
      roles: ['USER'],
      permissions: granted,
    }),
  )
  await page.route('**/api/auth/refresh', (route) => fulfill(route, null, 401))
  await page.route('**/api/project-context/options', (route) =>
    fulfill(route, [
      {
        id: 'P1',
        projectCode: 'PRJ-001',
        projectName: `示范项目${'超长'.repeat(24)}`,
        status: 'ACTIVE',
      },
    ]),
  )
  await page.route('**/api/contracts**', (route) =>
    fulfill(route, {
      records: [
        {
          id: 'C1',
          tenantId: 'TENANT1',
          orgId: 'O1',
          projectId: 'P1',
          projectName: '示范项目',
          contractCode: 'SUB-001',
          contractName: '主体劳务分包合同',
          contractType: 'SUB',
          partyAId: 'A1',
          partyAName: '总包单位',
          partyBId: 'S1',
          partyBName: '劳务公司甲',
          contractAmount: '1000000.00',
          currentAmount: '1000000.00',
          taxRate: '3',
          taxAmount: '0',
          amountWithoutTax: '0',
          signedDate: '2026-01-01',
          startDate: '2026-01-01',
          endDate: '2026-12-31',
          paymentMethod: 'MONTHLY',
          settlementMethod: 'MEASURE',
          paidAmount: '0',
          settlementAmount: '0',
          contractStatus: 'PERFORMING',
          approvalStatus: 'APPROVED',
          createdBy: '1',
          createdAt: '2026-01-01',
          updatedAt: '2026-01-01',
        },
      ],
      total: 1,
      pageNo: 1,
      pageSize: 200,
    }),
  )
  await page.route('**/api/contracts/C1/items', (route) =>
    fulfill(route, [
      {
        id: 'CI1',
        contractId: 'C1',
        itemCode: 'SUB-ITEM-001',
        itemName: '钢筋绑扎',
        unit: '吨',
        quantity: '100.0000',
        unitPrice: '800.00',
        amount: '80000.00',
      },
    ]),
  )
  await page.route('**/api/files**', async (route) => {
    const request = route.request()
    const path = new URL(request.url()).pathname.replace('/api', '')
    if (request.method() === 'GET') return fulfill(route, files)
    writes.push(`${request.method()} ${path}`)
    if (request.method() === 'POST') {
      files.push({ id: 'F1', originalName: '计量依据.pdf' })
      return fulfill(route, files[0])
    }
    files.splice(0)
    return fulfill(route, null)
  })
  await page.route('**/api/sub-tasks**', async (route) => {
    const request = route.request()
    const path = new URL(request.url()).pathname.replace('/api', '')
    const body = request.postDataJSON() as Record<string, unknown> | undefined
    if (request.method() !== 'GET') writes.push(`${request.method()} ${path}`)
    if (request.method() === 'GET' && path === '/sub-tasks')
      return fulfill(route, { records: tasks, total: tasks.length, pageNo: 1, pageSize: 10 })
    if (request.method() === 'GET')
      return fulfill(
        route,
        tasks.find((item) => path === `/sub-tasks/${item.id}`),
      )
    if (request.method() === 'POST') {
      tasks.push({
        ...task,
        ...body,
        id: 'T2',
        taskCode: 'ST-002',
        projectName: '示范项目',
        contractName: '主体劳务分包合同',
        partnerName: '劳务公司甲',
      })
      return fulfill(route, 'T2')
    }
    return fulfill(route, null)
  })
  await page.route('**/api/sub-measures**', async (route) => {
    const request = route.request()
    const path = new URL(request.url()).pathname.replace('/api', '')
    const body = request.postDataJSON() as Record<string, unknown> | undefined
    if (request.method() !== 'GET') writes.push(`${request.method()} ${path}`)
    if (request.method() === 'GET' && path === '/sub-measures') {
      const requestedStatus = new URL(request.url()).searchParams.get('status')
      const visibleMeasures = requestedStatus
        ? measures.filter((item) => item.status === requestedStatus)
        : measures
      return fulfill(route, {
        records: visibleMeasures,
        total: visibleMeasures.length,
        pageNo: 1,
        pageSize: 10,
      })
    }
    if (request.method() === 'GET' && path.endsWith('/items')) {
      const id = path.split('/').at(-2) ?? ''
      return fulfill(route, items[id] ?? [])
    }
    if (request.method() === 'GET') {
      if (controls.failMeasureDetail) return fulfill(route, null, 500, 'DETAIL_READ_FAILED')
      return fulfill(
        route,
        measures.find((item) => path === `/sub-measures/${item.id}`),
      )
    }
    if (path.endsWith('/items/batch')) {
      const id = path.split('/').at(-3) ?? ''
      const commands =
        (body as unknown as Array<{ contractItemId: string; currentQuantity: string }>) ?? []
      items[id] = commands.map((item) => ({
        id: 'MI1',
        measureId: id,
        contractItemId: item.contractItemId,
        itemName: '钢筋绑扎',
        unit: '吨',
        contractQuantity: '100.0000',
        currentQuantity: item.currentQuantity,
        cumulativeQuantity: item.currentQuantity,
        unitPrice: '800.00',
        amount: '8000.00',
      }))
      const row = measures.find((item) => item.id === id)
      if (row) row.reportedAmount = '8000.00'
      return fulfill(route, null)
    }
    if (path.endsWith('/submit')) {
      const id = path.split('/').at(-2)
      const row = measures.find((item) => item.id === id)
      if (row) {
        row.status = 'APPROVING'
        row.approvalStatus = 'APPROVING'
      }
      return fulfill(route, null)
    }
    if (request.method() === 'POST') {
      measures.push({
        ...measure,
        ...body,
        id: 'M2',
        measureCode: 'SM-002',
        projectName: '示范项目',
        contractName: '主体劳务分包合同',
        partnerName: '劳务公司甲',
        reportedAmount: '0.0000',
        netAmount: '0.0000',
      })
      items.M2 = []
      return fulfill(route, 'M2')
    }
    return fulfill(route, null)
  })
  return { writes, tasks, measures, items, files, controls }
}

test.describe('M6 subcontract task and measure V2', () => {
  test('fails closed before business traffic', async ({ page }) => {
    const traffic: string[] = []
    page.on('request', (request) => {
      if (/sub-tasks|sub-measures/.test(request.url())) traffic.push(request.url())
    })
    await install(page, [])
    await page.goto('/subcontract/task?projectId=P1')
    await expect(page).toHaveURL(/\/forbidden\?from=/)
    expect(traffic).toEqual([])
  })

  test('redirects, renders both routes at three viewports and passes axe', async ({ page }) => {
    await install(page)
    const errors = captureRuntimeErrors(page)
    await page.goto('/subcontract?projectId=P1#records')
    await expect(page).toHaveURL(/\/subcontract\/task\?projectId=P1#records/)
    for (const sample of [
      { path: '/subcontract/task', code: 'ST-001' },
      { path: '/subcontract/measure', code: 'SM-001' },
    ])
      for (const viewport of [
        { width: 1440, height: 900 },
        { width: 1024, height: 768 },
        { width: 390, height: 844 },
      ]) {
        await page.setViewportSize(viewport)
        await page.goto(`${sample.path}?projectId=P1`)
        await expect(page.getByText(sample.code, { exact: true }).first()).toBeVisible()
        expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(
          true,
        )
      }
    const axe = await new AxeBuilder({ page }).include('.subcontract-workspace').analyze()
    expect(
      axe.violations.filter((item) => ['serious', 'critical'].includes(item.impact ?? '')),
    ).toEqual([])
    expect(errors).toEqual([])
  })

  test('creates task and re-reads server facts', async ({ page }) => {
    const state = await install(page)
    await page.goto('/subcontract/task?projectId=P1')
    await page.getByRole('button', { name: '新建分包任务' }).click()
    const dialog = page.getByRole('dialog', { name: '新建分包任务' })
    await selectOption(page, dialog, /^分包合同$/, /SUB-001 · 主体劳务分包合同/)
    await expect(dialog.getByLabel('分包单位')).toHaveValue('劳务公司甲')
    await dialog.getByLabel('任务名称').fill('二次结构劳务')
    await dialog.getByRole('button', { name: '保存', exact: true }).dblclick()
    await expect(page.getByText('ST-002', { exact: true }).first()).toBeVisible()
    expect(state.writes.filter((item) => item === 'POST /sub-tasks')).toHaveLength(1)
  })

  test('creates measure, saves server-derived items, uploads and submits once', async ({
    page,
  }) => {
    const state = await install(page)
    await page.goto('/subcontract/measure?projectId=P1')
    await page.getByRole('combobox', { name: '状态' }).selectOption({ label: '草稿' })
    await page.getByRole('button', { name: '新建分包计量' }).click()
    const create = page.getByRole('dialog', { name: '新建分包计量' })
    await selectOption(page, create, /^分包合同$/, /SUB-001 · 主体劳务分包合同/)
    await selectOption(page, create, /^关联任务$/, /ST-001 · 地下室劳务/)
    await create.getByRole('button', { name: '保存', exact: true }).dblclick()
    await expect(page.getByText('SM-002', { exact: true }).first()).toBeVisible()

    await page.getByRole('button', { name: '维护计量清单' }).click()
    const items = page.getByRole('dialog', { name: '维护计量清单' })
    await items.getByRole('button', { name: '添加清单项' }).click()
    const quantity = items.getByLabel('本期数量')
    await quantity.fill('10')
    await quantity.blur()
    await expect(quantity).toHaveValue('10.00')
    await items.getByRole('button', { name: '保存清单' }).click()
    await expect(page.getByText('¥8,000.00', { exact: true }).last()).toBeVisible()

    await page.locator('input[type=file]').setInputFiles({
      name: '计量依据.pdf',
      mimeType: 'application/pdf',
      buffer: Buffer.from('safe evidence'),
    })
    await page.getByRole('button', { name: '上传附件' }).click()
    await expect(page.getByText('计量依据.pdf', { exact: true })).toBeVisible()
    await page.getByRole('button', { name: '提交审批' }).click()
    await page
      .getByRole('dialog', { name: '提交分包计量' })
      .getByRole('button', { name: '确认提交' })
      .click()
    await expect(
      page.getByRole('dialog', { name: '分包计量详情' }).getByText('审批中', { exact: true }),
    ).toHaveText(['审批中', '审批中'])

    expect(state.writes.filter((item) => item === 'POST /sub-measures')).toHaveLength(1)
    expect(state.writes.filter((item) => item.endsWith('/items/batch'))).toHaveLength(1)
    expect(state.writes.filter((item) => item === 'POST /files/upload')).toHaveLength(1)
    expect(state.writes.filter((item) => item.endsWith('/submit'))).toHaveLength(1)
    expect(state.items.M2).toEqual([
      expect.objectContaining({
        currentQuantity: '10.00',
        unitPrice: '800.00',
        amount: '8000.00',
      }),
    ])
  })

  test('reports write success without claiming authority when detail re-read fails', async ({
    page,
  }) => {
    const state = await install(page)
    await page.goto('/subcontract/measure?projectId=P1')
    await page.getByRole('button', { name: 'SM-001' }).click()
    await expect(page.getByRole('dialog', { name: '分包计量详情' })).toBeVisible()
    state.controls.failMeasureDetail = true
    await page.getByRole('button', { name: '提交审批' }).click()
    await page
      .getByRole('dialog', { name: '提交分包计量' })
      .getByRole('button', { name: '确认提交' })
      .click()
    await expect(page.getByText('分包计量已提交，结果未确认', { exact: true })).toBeVisible()
    await expect(page.getByRole('dialog', { name: '分包计量详情' })).toHaveCount(0)
  })
})
