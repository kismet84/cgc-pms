import { test, expect, type APIRequestContext, type Locator, type Page, type Playwright } from '@playwright/test'
import { createAuthenticatedPage } from './auth-session'

const API_BASE_URL = 'http://localhost:8080'

type PaymentSeed = {
  projectId: string
  contractId: string
  partnerId: string
  projectName: string
  contractName: string
}

type PayApplicationDetail = {
  id: string
  applyCode: string
  contractName?: string
  payType: string
}

function runId(prefix: string) {
  return `${prefix}-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`
}

async function expectApiOk<T>(response: Awaited<ReturnType<APIRequestContext['get']>>): Promise<T> {
  expect(response.ok(), `API ${response.url()} should return HTTP 2xx`).toBeTruthy()
  const body = (await response.json()) as { code?: string; data?: T }
  expect(body.code, `API ${response.url()} business code`).toMatch(/^(0|00000)$/)
  return body.data as T
}

async function loginApi(playwright: Playwright) {
  const api = await playwright.request.newContext({ baseURL: API_BASE_URL })
  const response = await api.post('/api/auth/login', {
    data: { username: 'admin', password: 'admin123' },
  })
  await expectApiOk<{ token?: string }>(response)
  return api
}

async function apiPost<T>(api: APIRequestContext, url: string, data: Record<string, unknown>) {
  const response = await api.post(url, { data })
  return expectApiOk<T>(response)
}

async function apiGet<T>(api: APIRequestContext, url: string) {
  const response = await api.get(url)
  return expectApiOk<T>(response)
}

async function seedPaymentCreateContext(api: APIRequestContext) {
  const sampleId = runId('PAYUI')
  const projectName = `E2E付款项目-${sampleId}`
  const contractName = `E2E付款合同-${sampleId}`

  const projectId = String(
    await apiPost<string>(api, '/api/projects', {
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
    await apiPost<string>(api, '/api/partners', {
      partnerCode: runId('PTA'),
      partnerName: `E2E甲方-${sampleId}`,
      partnerType: 'PARTY_A',
      status: 'ENABLE',
      blacklistFlag: 0,
    }),
  )

  const partyBId = String(
    await apiPost<string>(api, '/api/partners', {
      partnerCode: runId('PTB'),
      partnerName: `E2E乙方-${sampleId}`,
      partnerType: 'PARTY_B',
      status: 'ENABLE',
      blacklistFlag: 0,
    }),
  )

  const contractId = String(
    await apiPost<string>(api, '/api/contracts/composite', {
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

  return {
    projectId,
    contractId,
    partnerId: partyBId,
    projectName,
    contractName,
  } satisfies PaymentSeed
}

async function waitForPaymentList(page: Page) {
  await page.goto('/payment/application')
  await expect(page.locator('.payment-page')).toBeVisible({ timeout: 10000 })
  await expect(page.locator('.payment-toolbar button').filter({ hasText: '新建申请' })).toBeVisible()
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

async function selectOptionByText(select: Locator, text: string) {
  await select.click()
  const dropdown = select.page().locator('.ant-select-dropdown:not(.ant-select-dropdown-hidden)').last()
  await expect(dropdown).toBeVisible({ timeout: 10000 })
  const option = dropdown
    .locator('.ant-select-item-option:not(.ant-select-item-option-disabled)')
    .filter({ hasText: text })
    .first()
  await expect(option).toBeVisible({ timeout: 10000 })
  await option.click()
  await dropdown.waitFor({ state: 'hidden', timeout: 5000 }).catch(() => {})
}

test('payment create modal submits applyCode and reads back in list', async ({ browser, playwright }) => {
  const auth = await createAuthenticatedPage(browser)
  const api = await loginApi(playwright)
  const seed = await seedPaymentCreateContext(api)
  const applyCode = runId('PAYAPP')

  try {
    await waitForPaymentList(auth.page)
    await auth.page.locator('.payment-toolbar button').filter({ hasText: '新建申请' }).click()

    const modal = auth.page.locator('.ant-modal').filter({ hasText: '新建付款申请' }).last()
    await expect(modal).toBeVisible({ timeout: 5000 })

    const applyCodeInput = modal.locator('.ant-form-item').filter({ hasText: '申请编号' }).locator('input').first()
    await expect(applyCodeInput).toBeVisible()
    await applyCodeInput.fill(applyCode)

    const projectSelect = modal.locator('.ant-select').nth(0)
    const contractSelect = modal.locator('.ant-select').nth(1)
    const payTypeSelect = modal.locator('.ant-select').nth(2)

    const contractsReload = waitForContractsReload(auth.page)
    await selectOptionByText(projectSelect, seed.projectName)
    await contractsReload
    await selectOptionByText(contractSelect, seed.contractName)
    await selectOptionByText(payTypeSelect, '进度款')

    await modal.locator('input[placeholder="金额（元）"]').fill('100000')
    await modal.locator('textarea[placeholder="申请原因"]').fill(`UI创建-${applyCode}`)

    const createResponsePromise = auth.page.waitForResponse(
      (response) =>
        response.request().method() === 'POST' && /\/api\/pay-applications$/.test(response.url()),
      { timeout: 10000 },
    )
    await modal.getByRole('button', { name: '确定' }).click()
    const createResponse = await createResponsePromise
    expect(createResponse.ok()).toBeTruthy()
    const createPayload = createResponse.request().postDataJSON() as Record<string, unknown>
    expect(createPayload.applyCode).toBe(applyCode)

    const createdBody = (await createResponse.json()) as { code?: string; data?: string }
    expect(createdBody.code).toMatch(/^(0|00000)$/)
    expect(createdBody.data, '创建接口应返回申请单 id').toBeTruthy()

    const created = await apiGet<PayApplicationDetail>(api, `/api/pay-applications/${createdBody.data}`)
    expect(created.applyCode, '后端应返回非空申请编号').toBeTruthy()
    expect(created.contractName).toContain(seed.contractName)

    if (await modal.isVisible().catch(() => false)) {
      await modal.getByRole('button', { name: '取消' }).click()
      await expect(modal).not.toBeVisible({ timeout: 10000 })
    }

    await auth.page.locator('.payment-toolbar button').filter({ hasText: '刷新' }).click()

    const createdRow = auth.page.locator('.vxe-body--row').filter({ hasText: created.applyCode }).first()
    await expect(createdRow).toBeVisible({ timeout: 10000 })
    await expect(createdRow).toContainText(seed.contractName)
    await expect(createdRow).toContainText('进度款')
  } finally {
    await api.dispose()
    await auth.page.close()
    await auth.context.close()
  }
})
