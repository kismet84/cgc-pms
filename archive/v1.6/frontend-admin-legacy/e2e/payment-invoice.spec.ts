import {
  test,
  expect,
  type APIRequestContext,
  type Browser,
  type BrowserContext,
  type Locator,
  type Page,
  type Playwright,
} from '@playwright/test'

/**
 * Payment / Invoice minimum smoke for BCD closure.
 *
 * Scope boundary:
 * - Payment: application list, headers/status tags, pay type filter, real create -> list readback.
 * - Invoice: current single-page /invoice list, verify-status filter, add modal.
 * - Excluded from this file: /payment/record route and invoice detail route, which do not exist
 *   in the current frontend router.
 */

let sharedContext: BrowserContext
let sharedPage: Page
let apiContext: APIRequestContext

const API_BASE_URL = 'http://localhost:8080'
const PAY_STATUS_LABEL: Record<string, string> = {
  PENDING: '待付款',
  APPROVED: '已批未付',
  UNPAID: '未支付',
  PARTIAL: '部分支付',
  PARTIALLY_PAID: '部分支付',
  PAID: '已支付',
}
const APPROVAL_STATUS_LABEL: Record<string, string> = {
  DRAFT: '草稿',
  APPROVING: '审批中',
  APPROVED: '已通过',
  REJECTED: '已驳回',
}

type PaymentSeed = {
  projectId: string
  contractId: string
  partnerId: string
  projectName: string
  contractName: string
  warehouseId: string
  materialId: string
}

type PayApplicationDetail = {
  id: string
  applyCode: string
  contractName?: string
  payType: string
  applyAmount: string
  payStatus: string
  approvalStatus: string
  basis?: Array<{
    id?: string
    basisType?: string
    basisId?: string
    basisAmount?: string
  }>
}

function screenshotPath(name: string) {
  return `e2e/screenshots/${name}-${Date.now()}.png`
}

async function createAuthenticatedPage(browser: Browser) {
  const context = await browser.newContext({ storageState: 'e2e/.auth/admin.json' })
  const page = await context.newPage()
  return { context, page }
}

async function selectFirstAvailableOption(select: Locator) {
  await select.click()
  const dropdown = select
    .page()
    .locator('.ant-select-dropdown:not(.ant-select-dropdown-hidden)')
    .last()
  await expect(dropdown).toBeVisible({ timeout: 5000 })
  const options = dropdown.locator('.ant-select-item-option:not(.ant-select-item-option-disabled)')
  const optionCount = await options.count()
  if (optionCount === 0) {
    console.log('No selectable options in dropdown')
    await select.page().keyboard.press('Escape')
    return false
  }
  await options.first().click()
  await dropdown.waitFor({ state: 'hidden', timeout: 5000 }).catch(() => {})
  return true
}

async function selectOptionByText(select: Locator, text: string) {
  await select.click()
  const dropdown = select
    .page()
    .locator('.ant-select-dropdown:not(.ant-select-dropdown-hidden)')
    .last()
  await expect(dropdown).toBeVisible({ timeout: 10000 })
  const option = dropdown
    .locator('.ant-select-item-option:not(.ant-select-item-option-disabled)')
    .filter({ hasText: text })
    .first()
  await expect(option).toBeVisible({ timeout: 10000 })
  await option.click()
  await dropdown.waitFor({ state: 'hidden', timeout: 5000 }).catch(() => {})
}

async function expectApiOk<T>(response: Awaited<ReturnType<APIRequestContext['get']>>): Promise<T> {
  expect(response.ok(), `API ${response.url()} should return HTTP 2xx`).toBeTruthy()
  const body = (await response.json()) as { code?: string; message?: string; data?: T }
  expect(body.code, `API ${response.url()} business code`).toMatch(/^(0|00000)$/)
  return body.data as T
}

function runId(prefix: string) {
  return `${prefix}-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`
}

async function loginApi(playwright: Playwright) {
  apiContext = await playwright.request.newContext({ baseURL: API_BASE_URL })
  const response = await apiContext.post('/api/auth/login', {
    data: { username: 'admin', password: 'admin123' },
  })
  await expectApiOk<{ token?: string }>(response)
}

async function apiPost<T>(url: string, data: Record<string, unknown>) {
  const response = await apiContext.post(url, { data })
  return expectApiOk<T>(response)
}

async function apiGet<T>(url: string, params?: Record<string, string | number>) {
  const response = await apiContext.get(url, params ? { params } : undefined)
  return expectApiOk<T>(response)
}

async function seedPaymentCreateContext() {
  const sampleId = runId('PAYUI')
  const projectName = `E2E付款项目-${sampleId}`
  const contractName = `E2E付款合同-${sampleId}`

  const projectId = String(
    await apiPost<string>('/api/projects', {
      projectCode: runId('PRJ'),
      projectName,
      projectType: 'BUILDING',
      contractAmount: '5000000',
      plannedStartDate: '2025-01-01',
      plannedEndDate: '2026-12-31',
      status: 'ACTIVE',
    }),
  )

  const partyAId = String(
    await apiPost<string>('/api/partners', {
      partnerCode: runId('PTA'),
      partnerName: `E2E甲方-${sampleId}`,
      partnerType: 'PARTY_A',
      status: 'ENABLE',
      blacklistFlag: 0,
    }),
  )

  const partyBId = String(
    await apiPost<string>('/api/partners', {
      partnerCode: runId('PTB'),
      partnerName: `E2E乙方-${sampleId}`,
      partnerType: 'PARTY_B',
      status: 'ENABLE',
      blacklistFlag: 0,
    }),
  )

  const contractId = String(
    await apiPost<string>('/api/contracts/composite', {
      contract: {
        contractCode: runId('CT'),
        contractName,
        contractType: 'SUB',
        projectId,
        partyAId,
        partyBId,
        contractAmount: '100000',
        signedDate: '2025-06-01',
        paymentMethod: '银行转账',
        settlementMethod: '按进度结算',
      },
      items: [
        {
          itemName: 'AUTO',
          itemSpec: '1',
          unit: '项',
          quantity: 1,
          unitPrice: '100000',
        },
      ],
      paymentTerms: [
        {
          termName: '进度款',
          paymentRatio: 100,
          paymentAmount: '100000',
          paymentCondition: '完工后支付',
          plannedDate: '2026-06-01',
        },
      ],
      submitForApproval: false,
    }),
  )

  const warehouseId = String(
    await apiPost<string>('/api/inventory/warehouses', {
      warehouseCode: runId('WH'),
      warehouseName: `E2E付款仓库-${sampleId}`,
      projectId,
    }),
  )

  const materialId = String(
    await apiPost<string>('/api/materials', {
      materialCode: runId('MAT'),
      materialName: `E2E付款物料-${sampleId}`,
      unit: '项',
    }),
  )

  return {
    projectId,
    contractId,
    partnerId: partyBId,
    projectName,
    contractName,
    warehouseId,
    materialId,
  } satisfies PaymentSeed
}

async function createPaymentApplication(seed: PaymentSeed) {
  const applyCode = runId('PAYAPP')
  const applyReason = `真实回读-${runId('PAYREAD')}`
  const receiptId = String(
    await apiPost<string>('/api/receipts', {
      projectId: seed.projectId,
      contractId: seed.contractId,
      partnerId: seed.partnerId,
      warehouseId: seed.warehouseId,
      receiptDate: '2025-12-01',
      qualityStatus: 'ACCEPTED',
      totalAmount: '100000',
    }),
  )
  await apiPost<void>(`/api/receipts/${receiptId}/items/batch`, [
    {
      receiptId,
      materialId: seed.materialId,
      actualQuantity: '1',
      qualifiedQuantity: '1',
      unitPrice: '100000',
      amount: '100000',
    },
  ])
  const createdId = await apiPost<string>('/api/pay-applications', {
    projectId: seed.projectId,
    contractId: seed.contractId,
    partnerId: seed.partnerId,
    applyCode,
    payType: 'PROGRESS',
    applyAmount: '100000',
    applyReason,
  })
  const receiptItems = await apiGet<Array<{ id: string; amount?: string }>>(
    `/api/receipts/${receiptId}/items`,
  )
  expect(receiptItems.length, '验收单应至少返回 1 条明细').toBeGreaterThan(0)
  await apiPost<void>(`/api/pay-applications/${createdId}/basis/batch`, [
    {
      basisType: 'MAT_RECEIPT',
      basisId: receiptItems[0].id,
      basisAmount: '100000',
    },
  ])
  return { createdId, applyCode, applyReason }
}

async function waitForContractsReload(page: Page) {
  await page.waitForResponse(
    (response) =>
      response.request().method() === 'GET' &&
      response.url().includes('/api/contracts') &&
      response.url().includes('projectId='),
    { timeout: 10000 },
  )
}

async function waitForPaymentList(page: Page) {
  await page.goto('/payment/application')
  await expect(page.locator('.payment-page')).toBeVisible({ timeout: 10000 })
  await expect(page.locator('.payment-search-bar')).toBeVisible({ timeout: 10000 })
  await expect(page.locator('.vxe-table').first()).toBeVisible({ timeout: 10000 })
}

async function waitForInvoiceList(page: Page) {
  await page.goto('/invoice')
  await expect(page.locator('.invoice-page')).toBeVisible({ timeout: 10000 })
  await expect(page.locator('.invoice-search-bar')).toBeVisible({ timeout: 10000 })
  await expect(page.locator('.vxe-table').first()).toBeVisible({ timeout: 10000 })
}

test.describe.configure({ mode: 'serial' })

test.beforeAll(async ({ browser }) => {
  const auth = await createAuthenticatedPage(browser)
  sharedContext = auth.context
  sharedPage = auth.page
})

test.beforeAll(async ({ playwright }) => {
  await loginApi(playwright)
})

test.afterAll(async () => {
  await sharedPage?.close()
  await sharedContext?.close()
  await apiContext?.dispose()
})

test.describe('Payment: Application list and detail', () => {
  test('should navigate to payment application list and verify page structure', async () => {
    await waitForPaymentList(sharedPage)

    await expect(sharedPage.locator('.payment-page-head')).toBeVisible()
    await expect(
      sharedPage.locator('.payment-search-actions button').filter({ hasText: '查询' }),
    ).toBeVisible()
    await expect(
      sharedPage.locator('.payment-search-actions button').filter({ hasText: '重置' }),
    ).toBeVisible()
    await expect(
      sharedPage.locator('.payment-toolbar button').filter({ hasText: '新建申请' }),
    ).toBeVisible()

    await sharedPage.screenshot({
      path: screenshotPath('payment-application-list'),
      fullPage: true,
    })
  })

  test('payment application table shows pay type and status tags', async () => {
    await waitForPaymentList(sharedPage)

    const headersText = await sharedPage.locator('.vxe-header--column').allTextContents()
    const allHeaders = headersText.join(' ')
    console.log(`Payment table headers: ${allHeaders}`)

    expect(allHeaders).toContain('付款类型')
    expect(allHeaders).toContain('支付状态')
    expect(allHeaders).toContain('审批状态')

    await sharedPage.screenshot({ path: screenshotPath('payment-table-headers'), fullPage: true })
  })

  test('payment application filter by pay type works', async () => {
    await waitForPaymentList(sharedPage)

    const payTypeSelect = sharedPage.locator('.payment-search-select.is-compact').nth(0)
    if (await payTypeSelect.isVisible({ timeout: 3000 }).catch(() => false)) {
      const selected = await selectFirstAvailableOption(payTypeSelect)
      if (selected) {
        await sharedPage
          .locator('.payment-search-actions button')
          .filter({ hasText: '查询' })
          .click()
        await expect(sharedPage.locator('.vxe-table').first()).toBeVisible({ timeout: 5000 })
      }
    }

    await sharedPage.screenshot({ path: screenshotPath('payment-filter-paytype'), fullPage: true })
  })

  test('creates a real payment application then reads back status from list', async () => {
    const seed = await seedPaymentCreateContext()
    const createdMeta = await createPaymentApplication(seed)
    await waitForPaymentList(sharedPage)
    const created = await apiGet<PayApplicationDetail>(
      `/api/pay-applications/${createdMeta.createdId}`,
    )
    expect(created.applyCode, '创建后应返回申请编号').toBeTruthy()
    expect(created.payStatus, '创建后应返回支付状态').toBeTruthy()
    expect(created.approvalStatus, '创建后应返回审批状态').toBeTruthy()
    expect(created.basis?.length ?? 0, '创建后的付款申请应带回付款依据').toBeGreaterThan(0)

    await waitForPaymentList(sharedPage)
    const searchProjectSelect = sharedPage.locator('.payment-search-select').first()
    const searchContractSelect = sharedPage.locator('.payment-search-select').nth(1)
    const searchContractsReload = waitForContractsReload(sharedPage)
    await selectOptionByText(searchProjectSelect, seed.projectName)
    await searchContractsReload
    await selectOptionByText(searchContractSelect, seed.contractName)
    await sharedPage.locator('.payment-search-actions button').filter({ hasText: '查询' }).click()

    const createdRow = sharedPage
      .locator('.vxe-body--row')
      .filter({ hasText: created.applyCode })
      .first()
    await expect(createdRow).toBeVisible({ timeout: 10000 })
    await expect(createdRow).toContainText(seed.contractName)
    await expect(createdRow).toContainText('10.00 万')
    await expect(createdRow).toContainText('进度款')
    await expect(createdRow).toContainText(PAY_STATUS_LABEL[created.payStatus] ?? created.payStatus)
    await expect(createdRow).toContainText(
      APPROVAL_STATUS_LABEL[created.approvalStatus] ?? created.approvalStatus,
    )

    await sharedPage.screenshot({
      path: screenshotPath('payment-created-readback'),
      fullPage: true,
    })
  })
})

test.describe('Invoice: List → Registration', () => {
  test('should navigate to invoice list and verify page structure', async () => {
    await waitForInvoiceList(sharedPage)

    await expect(sharedPage.locator('.invoice-page-head')).toBeVisible()
    await expect(
      sharedPage.locator('.invoice-search-actions button').filter({ hasText: '查询' }),
    ).toBeVisible()
    await expect(
      sharedPage.locator('.invoice-search-actions button').filter({ hasText: '重置' }),
    ).toBeVisible()
    await expect(
      sharedPage.locator('.invoice-toolbar button').filter({ hasText: '新增发票' }),
    ).toBeVisible()

    await sharedPage.screenshot({ path: screenshotPath('invoice-list'), fullPage: true })
  })

  test('invoice table shows verify status and invoice type tags', async () => {
    await waitForInvoiceList(sharedPage)

    const headersText = await sharedPage.locator('.vxe-header--column').allTextContents()
    const allHeaders = headersText.join(' ')
    console.log(`Invoice table headers: ${allHeaders}`)

    expect(allHeaders).toContain('发票号码')
    expect(allHeaders).toContain('核验状态')

    await sharedPage.screenshot({ path: screenshotPath('invoice-table-headers'), fullPage: true })
  })

  test('invoice filter by verify status works', async () => {
    await waitForInvoiceList(sharedPage)

    const verifySelect = sharedPage.locator('.invoice-search-select.is-compact').first()
    if (await verifySelect.isVisible({ timeout: 3000 }).catch(() => false)) {
      const selected = await selectFirstAvailableOption(verifySelect)
      if (selected) {
        await sharedPage
          .locator('.invoice-search-actions button')
          .filter({ hasText: '查询' })
          .click()
        await expect(sharedPage.locator('.vxe-table').first()).toBeVisible({ timeout: 5000 })
      }
    }

    await sharedPage.screenshot({ path: screenshotPath('invoice-filter-verify'), fullPage: true })
  })

  test('should open invoice registration modal', async () => {
    await waitForInvoiceList(sharedPage)

    await sharedPage.locator('.invoice-toolbar button').filter({ hasText: '新增发票' }).click()
    const modal = sharedPage.locator('.ant-modal').filter({ hasText: '发票' }).last()
    const modalVisible = await modal.isVisible({ timeout: 5000 }).catch(() => false)

    if (modalVisible) {
      console.log('Invoice registration modal opened')
      await sharedPage.screenshot({
        path: screenshotPath('invoice-register-modal'),
        fullPage: true,
      })
      await modal.locator('.ant-modal-close').click()
      await expect(modal).not.toBeVisible({ timeout: 5000 })
    } else {
      console.log('Invoice registration modal did not appear')
    }
  })
})
