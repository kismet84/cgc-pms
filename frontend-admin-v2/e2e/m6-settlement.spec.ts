import AxeBuilder from '@axe-core/playwright'
import { expect, test, type Locator, type Page, type Route } from '@playwright/test'
import { captureRuntimeErrors } from './runtime-errors'

const permissions = [
  'settlement:query',
  'settlement:add',
  'settlement:edit',
  'settlement:delete',
  'settlement:submit',
]

const baseSettlement = {
  id: 'S1',
  tenantId: 'TENANT1',
  projectId: 'P1',
  projectName: '滨海国际会展中心二期',
  contractId: 'C1',
  contractName: '主体结构劳务分包合同',
  partnerId: 'PARTNER1',
  partnerName: '华东建设劳务有限公司',
  settlementCode: 'STL-2026-001',
  settlementType: 'FINAL',
  contractAmount: '12000000.00',
  changeAmount: '320000.00',
  measuredAmount: '9800000.00',
  deductionAmount: '50000.00',
  paidAmount: '7000000.00',
  finalAmount: '10070000.00',
  warrantyAmount: '503500.00',
  unpaidAmount: '2566500.00',
  amountFormulaVersion: 'APPROVED_MEASURE_VARIATION_DEDUCTION_V2',
  approvalStatus: 'DRAFT',
  status: 'DRAFT',
  settlementStatus: 'DRAFT',
  createdAt: '2026-07-25 10:00:00',
  remark: '主体结构终期结算',
  items: [
    {
      id: 'SI1',
      settlementId: 'S1',
      itemName: '现浇混凝土',
      unit: 'm³',
      quantity: '1250.0000',
      unitPrice: '680.00',
      amount: '850000.00',
      sourceType: 'CT_CONTRACT',
      sourceId: 'CI1',
    },
  ],
}

async function fulfill(route: Route, data: unknown, status = 200, code = '0') {
  await route.fulfill({
    status,
    contentType: 'application/json',
    body: JSON.stringify({ code, message: status === 200 ? 'success' : 'request failed', data }),
  })
}

async function selectOption(page: Page, scope: Locator, label: RegExp, option: RegExp) {
  await expect(scope).toHaveCSS('transform', 'none')
  await scope.getByRole('button', { name: label }).press('ArrowDown')
  const choice = page.getByRole('option', { name: option })
  await expect(choice).toBeVisible()
  await choice.click()
}

async function install(page: Page, granted = permissions) {
  const records = [{ ...baseSettlement, items: [...baseSettlement.items] }]
  const files: Array<{
    id: string
    originalName: string
    uploadedAt: string
    fileType: string
  }> = []
  const writes: string[] = []
  const controls = { failDetail: false }

  await page.route('**/api/auth/userinfo', (route) =>
    fulfill(route, {
      userId: '1',
      username: 'settlement-user',
      roles: ['USER'],
      permissions: granted,
    }),
  )
  await page.route('**/api/auth/refresh', (route) => fulfill(route, null, 401))
  await page.route('**/api/project-context/options', (route) =>
    fulfill(route, [
      {
        id: 'P1',
        projectCode: 'PRJ-2026-001',
        projectName: `滨海国际会展中心二期${'超长'.repeat(18)}`,
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
          orgId: 'ORG1',
          projectId: 'P1',
          projectName: '滨海国际会展中心二期',
          contractCode: 'SUB-2026-001',
          contractName: '主体结构劳务分包合同',
          contractType: 'SUB',
          partyAId: 'A1',
          partyAName: '中建示范总承包有限公司',
          partyBId: 'PARTNER1',
          partyBName: '华东建设劳务有限公司',
          contractAmount: '12000000.00',
          currentAmount: '12000000.00',
          taxRate: '3.00',
          taxAmount: '0.00',
          amountWithoutTax: '0.00',
          signedDate: '2026-01-01',
          startDate: '2026-01-01',
          endDate: '2026-12-31',
          paymentMethod: 'MONTHLY',
          settlementMethod: 'MEASURE',
          paidAmount: '7000000.00',
          settlementAmount: '0.00',
          contractStatus: 'PERFORMING',
          approvalStatus: 'APPROVED',
          createdBy: '1',
          createdAt: '2026-01-01',
          updatedAt: '2026-07-25',
        },
      ],
      total: 1,
      pageNo: 1,
      pageSize: 200,
    }),
  )
  await page.route('**/api/files**', async (route) => {
    const request = route.request()
    const path = new URL(request.url()).pathname.replace('/api', '')
    if (request.method() === 'POST') {
      writes.push(`POST ${path}`)
      files.push({
        id: 'F1',
        originalName: '终期结算审核表.pdf',
        uploadedAt: '2026-07-26 10:00:00',
        fileType: 'application/pdf',
      })
      return fulfill(route, files[0])
    }
    if (request.method() === 'DELETE') {
      writes.push(`DELETE ${path}`)
      files.splice(0)
      return fulfill(route, null)
    }
    return fulfill(route, files)
  })
  await page.route('**/api/settlements**', async (route) => {
    const request = route.request()
    const url = new URL(request.url())
    const path = url.pathname.replace('/api', '')
    const method = request.method()
    if (method !== 'GET') writes.push(`${method} ${path}`)

    if (method === 'GET' && path === '/settlements') {
      const settlementStatus = url.searchParams.get('settlementStatus')
      const approvalStatus = url.searchParams.get('approvalStatus')
      const visible = records.filter(
        (item) =>
          (!settlementStatus || item.settlementStatus === settlementStatus) &&
          (!approvalStatus || item.approvalStatus === approvalStatus),
      )
      return fulfill(route, {
        records: visible,
        total: visible.length,
        pageNo: 1,
        pageSize: 10,
      })
    }
    if (method === 'GET' && path === '/settlements/kpi')
      return fulfill(route, {
        totalCount: records.length,
        totalContractAmount: '12000000.00',
        totalFinalAmount: '10070000.00',
        totalChangeAmount: '320000.00',
        totalPaidAmount: '7000000.00',
        totalUnpaidAmount: '2566500.00',
        draftCount: records.filter((item) => item.settlementStatus === 'DRAFT').length,
        finalizedCount: 0,
      })
    if (method === 'GET' && path.startsWith('/settlements/compute/'))
      return fulfill(route, {
        contractId: 'C1',
        contractAmount: '12000000.00',
        changeAmount: '320000.00',
        measuredAmount: '9800000.00',
        deductionAmount: '0.00',
        paidAmount: '7000000.00',
        finalAmount: '10120000.00',
        warrantyAmount: '506000.00',
        unpaidAmount: '2614000.00',
        amountFormulaVersion: 'APPROVED_MEASURE_VARIATION_DEDUCTION_V2',
      })

    const id = path.split('/')[2] || ''
    const record = records.find((item) => item.id === id)
    if (method === 'GET' && path.endsWith('/sources'))
      return fulfill(route, {
        contractItems: [
          {
            id: 'CI1',
            itemCode: 'SUB-ITEM-001',
            itemName: '现浇混凝土',
            unit: 'm³',
            measuredQuantity: '1250.0000',
            unitPrice: '680.00',
            amount: '850000.00',
          },
        ],
        subMeasures: [
          {
            id: 'SM9007199254740993',
            measureCode: 'SM-2026-006',
            measurePeriod: '2026-06',
            approvedAmount: '9800000.00',
            approvalStatus: 'APPROVED',
          },
        ],
        varOrders: [
          {
            id: 'VO9007199254740993',
            varCode: 'VO-2026-011',
            varName: '地下室结构调整',
            confirmedAmount: '320000.00',
            approvalStatus: 'APPROVED',
          },
        ],
        payRecords: [],
      })
    if (method === 'GET' && path.endsWith('/variations'))
      return fulfill(route, [
        {
          id: 'VO1',
          varCode: 'VO-2026-011',
          varName: '地下室结构调整',
          confirmedAmount: '320000.00',
          approvalStatus: 'APPROVED',
        },
      ])
    if (method === 'GET' && path.endsWith('/payments'))
      return fulfill(route, [
        {
          id: 'PAY1',
          applyCode: 'PAY-2026-023',
          actualPayAmount: '7000000.00',
          payStatus: 'PAID',
        },
      ])
    if (method === 'GET' && path.endsWith('/costs'))
      return fulfill(route, [
        {
          id: 'COST1',
          costSubjectName: '主体结构劳务',
          amount: '9800000.00',
          costStatus: 'CONFIRMED',
        },
      ])
    if (method === 'GET' && path.endsWith('/attachments')) return fulfill(route, files)
    if (method === 'GET' && path.endsWith('/approval-records'))
      return fulfill(route, [
        {
          id: 'AP1',
          nodeName: '项目商务复核',
          operatorName: '王建国',
          actionName: '保存草稿',
          createdAt: '2026-07-25 10:00:00',
        },
      ])
    if (method === 'GET') {
      if (controls.failDetail) return fulfill(route, null, 500, 'SETTLEMENT_DETAIL_FAILED')
      return fulfill(route, record)
    }
    if (method === 'POST' && path === '/settlements') {
      records.push({
        ...baseSettlement,
        ...(request.postDataJSON() as object),
        id: 'S2',
        settlementCode: 'STL-2026-002',
        deductionAmount: '0.00',
        finalAmount: '10120000.00',
        unpaidAmount: '2614000.00',
        items: [],
      })
      return fulfill(route, 'S2')
    }
    if (method === 'POST' && path.endsWith('/items/batch')) {
      if (record) {
        const commands = request.postDataJSON() as Array<{ sourceId: string }>
        record.items = commands.map((command) => ({
          id: 'SI2',
          settlementId: record.id,
          itemName: '现浇混凝土',
          unit: 'm³',
          quantity: '1250.0000',
          unitPrice: '680.00',
          amount: '850000.00',
          sourceType: 'CT_CONTRACT',
          sourceId: command.sourceId,
        }))
      }
      return fulfill(route, null)
    }
    if (method === 'POST' && path.endsWith('/submit')) {
      if (record) record.approvalStatus = 'APPROVING'
      return fulfill(route, null)
    }
    if (method === 'PUT') {
      if (record) Object.assign(record, request.postDataJSON())
      return fulfill(route, null)
    }
    if (method === 'DELETE') {
      const index = records.findIndex((item) => item.id === id)
      if (index >= 0) records.splice(index, 1)
      return fulfill(route, null)
    }
    return fulfill(route, null)
  })
  return { records, files, writes, controls }
}

test.describe('M6 settlement V2', () => {
  test('redirects root and passes desktop, tablet, mobile and axe checks', async ({ page }) => {
    await install(page)
    const errors = captureRuntimeErrors(page)
    await page.goto('/v2/settlement?projectId=P1')
    await expect(page).toHaveURL(/\/v2\/settlement\/list\?projectId=P1/)
    for (const viewport of [
      { width: 1440, height: 900 },
      { width: 1024, height: 768 },
      { width: 390, height: 844 },
    ]) {
      await page.setViewportSize(viewport)
      await page.goto('/v2/settlement/list?projectId=P1')
      await expect(page.getByRole('button', { name: 'STL-2026-001' })).toBeVisible()
      expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(
        true,
      )
    }
    await page.getByRole('button', { name: 'STL-2026-001' }).click()
    await expect(page).toHaveURL(/\/v2\/settlement\/S1/)
    await expect(page.getByText('金额快照', { exact: true })).toBeVisible()
    await expect(page.getByText('项目商务复核', { exact: false })).toBeVisible()
    const axe = await new AxeBuilder({ page }).include('.settlement-workspace').analyze()
    expect(
      axe.violations.filter((item) => ['serious', 'critical'].includes(item.impact ?? '')),
    ).toEqual([])
    expect(errors).toEqual([])
  })

  test('creates, edits items, uploads and submits with one write each', async ({ page }) => {
    const state = await install(page)
    await page.goto('/v2/settlement/list?projectId=P1')
    await page.getByRole('button', { name: '新建结算' }).click()
    const form = page.getByRole('dialog', { name: '新建结算' })
    await selectOption(page, form, /^分包合同：/, /SUB-2026-001 · 主体结构劳务分包合同/)
    await form.getByLabel('终期扣款').fill('0.00')
    await form.getByRole('button', { name: '保存', exact: true }).dblclick()
    await expect(page).toHaveURL(/\/v2\/settlement\/list\?projectId=P1/)
    await expect(page.getByText('STL-2026-002', { exact: true })).toBeVisible()
    await page.getByRole('button', { name: 'STL-2026-002' }).click()
    await expect(page).toHaveURL(/\/v2\/settlement\/S2/)

    await page.getByRole('button', { name: '维护明细' }).click()
    const items = page.getByRole('dialog', { name: '维护结算明细' })
    await items.getByRole('checkbox').check()
    await items.getByRole('button', { name: '保存明细' }).click()
    await expect(page.getByText('1250.0000', { exact: true })).toBeVisible()

    await page.locator('input[type=file]').setInputFiles({
      name: '终期结算审核表.pdf',
      mimeType: 'application/pdf',
      buffer: Buffer.from('safe settlement evidence'),
    })
    await page.getByRole('button', { name: '上传', exact: true }).click()
    await expect(page.getByText(/终期结算审核表\.pdf/)).toBeVisible()

    await page.getByRole('button', { name: '提交审批' }).click()
    await page
      .getByRole('dialog', { name: '提交结算审批' })
      .getByRole('button', { name: '确认提交' })
      .click()
    await expect(
      page
        .getByRole('row')
        .filter({ hasText: 'STL-2026-002' })
        .getByText('审批中', { exact: true }),
    ).toBeVisible()

    expect(state.writes.filter((item) => item === 'POST /settlements')).toHaveLength(1)
    expect(state.writes.filter((item) => item.endsWith('/items/batch'))).toHaveLength(1)
    expect(state.writes.filter((item) => item === 'POST /files/upload')).toHaveLength(1)
    expect(state.writes.filter((item) => item.endsWith('/submit'))).toHaveLength(1)
  })

  test('hides mutations without permissions', async ({ page }) => {
    const readOnly = await install(page, ['settlement:query'])
    await page.goto('/v2/settlement/S1?projectId=P1')
    await expect(page.getByRole('button', { name: '编辑' })).toHaveCount(0)
    await expect(page.getByRole('button', { name: '维护明细' })).toHaveCount(0)
    await expect(page.getByRole('button', { name: '提交审批' })).toHaveCount(0)
    await expect(page.getByRole('button', { name: '删除', exact: true })).toHaveCount(0)
    expect(readOnly.writes).toEqual([])
  })

  test('keeps delete independent from edit and deletes a recoverable draft once', async ({
    page,
  }) => {
    const writable = await install(page, ['settlement:query', 'settlement:delete'])
    await page.goto('/v2/settlement/S1?projectId=P1')
    await expect(page.getByRole('button', { name: '编辑' })).toHaveCount(0)
    await expect(page.getByRole('button', { name: '删除', exact: true })).toBeVisible()
    await page.getByRole('button', { name: '删除', exact: true }).click()
    await page
      .getByRole('dialog', { name: '删除结算草稿' })
      .getByRole('button', { name: '确认删除' })
      .click()
    await expect(page).toHaveURL(/\/v2\/settlement\/list/)
    expect(writable.writes.filter((item) => item === 'DELETE /settlements/S1')).toHaveLength(1)
  })

  test('shows a recoverable error state for failed detail reads', async ({ page }) => {
    const state = await install(page)
    state.controls.failDetail = true
    await page.goto('/v2/settlement/S1?projectId=P1')
    await expect(page.getByText('详情加载失败', { exact: true })).toBeVisible()
    await expect(page.getByRole('button', { name: '重试' })).toBeVisible()
  })
})
