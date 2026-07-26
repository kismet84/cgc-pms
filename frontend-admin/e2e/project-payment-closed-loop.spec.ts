import { readFileSync } from 'node:fs'
import { spawnSync } from 'node:child_process'
import { createHmac } from 'node:crypto'
import {
  expect,
  test,
  type APIRequestContext,
  type APIResponse,
  type Browser,
  type Playwright,
} from '@playwright/test'

const API_BASE = 'http://localhost:8080'
const UI_BASE = 'http://localhost:5173'
const PROOF = readFileSync('e2e/fixtures/sample-invoice.pdf')

type PageResult<T> = { records: T[] }
type Role = { id: string; roleCode: string }
type Task = {
  id: string
  instanceId: string
  businessType: string
  businessId: string
  roundNo: number
}
type Budget = {
  id: string
  version: number
  approvalStatus: string
  status: string
  lines: Array<{
    id: string
    costSubjectId: string
    reservedAmount: string
    consumedAmount: string
  }>
}
type Payment = {
  id: string
  applyCode: string
  projectId: string
  contractId: string
  partnerId: string
  costSubjectId: string
  budgetLineId: string
  expenseCategory: string
  applyAmount: string
  payType: string
  approvalStatus: string
  payStatus: string
  approvalInstanceId: string
}
type PayRecord = { id: string; payStatus: string }
type CashJournal = { id: string; sourceId: string; status: string }
type AccountingEntry = {
  id: string
  payRecordId: string
  entryStatus: string
  reviewStatus: string
  totalDebit: string
  totalCredit: string
}

function unique(prefix: string) {
  return `${prefix}-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`
}

function backendAccessToken(username: string, userId: number, tenantId: number) {
  const result = spawnSync(
    'docker',
    [
      'exec',
      process.env.PLAYWRIGHT_BACKEND_CONTAINER ?? 'cgc-pms-backend-dev',
      'sh',
      '-c',
      'printf %s "$JWT_SECRET"',
    ],
    { encoding: 'utf8' },
  )
  expect(result.status, result.stderr || result.stdout).toBe(0)
  expect(result.stdout).not.toBe('')
  const secret = result.stdout
  const bytes = Buffer.byteLength(secret)
  const [algorithm, digest] =
    bytes >= 64 ? ['HS512', 'sha512'] : bytes >= 48 ? ['HS384', 'sha384'] : ['HS256', 'sha256']
  const issuedAt = Math.floor(Date.now() / 1000)
  const unsigned = [
    Buffer.from(JSON.stringify({ alg: algorithm })).toString('base64url'),
    Buffer.from(
      JSON.stringify({
        sub: username,
        userId,
        username,
        tenantId,
        roleCodes: ['ADMIN'],
        permissions: '',
        tokenType: 'access',
        iat: issuedAt,
        exp: issuedAt + 600,
      }),
    ).toString('base64url'),
  ].join('.')
  return `${unsigned}.${createHmac(digest, secret).update(unsigned).digest('base64url')}`
}

function mysql(sql: string) {
  const result = spawnSync(
    'docker',
    [
      'exec',
      '-i',
      process.env.PLAYWRIGHT_MYSQL_CONTAINER ?? 'cgc-pms-mysql-dev',
      'sh',
      '-c',
      'exec mysql -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" "$1"',
      '--',
      process.env.PLAYWRIGHT_MYSQL_DATABASE ?? 'cgc_pms_demo_v2',
    ],
    { input: sql, encoding: 'utf8' },
  )
  expect(result.status, result.stderr || result.stdout).toBe(0)
}

async function data<T>(response: APIResponse): Promise<T> {
  const body = (await response.json()) as { code?: string; message?: string; data?: T }
  expect(
    response.ok(),
    `${response.url()} should return 2xx: ${body.code ?? ''} ${body.message ?? ''}`,
  ).toBeTruthy()
  expect(body.code, body.message ?? response.url()).toMatch(/^(0|00000)$/)
  return body.data as T
}

async function expectError(response: APIResponse, status: number, code: string) {
  const body = (await response.json()) as { code?: string }
  expect(response.status()).toBe(status)
  expect(body.code).toBe(code)
}

async function get<T>(
  context: APIRequestContext,
  url: string,
  params?: Record<string, string | number>,
) {
  return data<T>(await context.get(url, params ? { params } : undefined))
}

async function post<T>(context: APIRequestContext, url: string, body?: unknown) {
  return data<T>(await context.post(url, body === undefined ? undefined : { data: body }))
}

async function put<T>(
  context: APIRequestContext,
  url: string,
  body?: unknown,
  params?: Record<string, string | number>,
) {
  return data<T>(
    await context.put(url, {
      ...(body === undefined ? {} : { data: body }),
      ...(params ? { params } : {}),
    }),
  )
}

async function login(playwright: Playwright, username = 'admin') {
  const bootstrap = await playwright.request.newContext({ baseURL: API_BASE })
  const loginResult = await data<{ userInfo: { roles: string[]; permissions: string[] } }>(
    await bootstrap.get(`/api/auth/dev-login?username=${encodeURIComponent(username)}`),
  )
  const storageState = await bootstrap.storageState()
  const csrf = storageState.cookies.find((cookie) => cookie.name === 'XSRF-TOKEN')
  expect(csrf, `${username} CSRF cookie`).toBeTruthy()
  await bootstrap.dispose()
  const context = await playwright.request.newContext({
    baseURL: API_BASE,
    storageState,
    extraHTTPHeaders: { 'X-XSRF-TOKEN': decodeURIComponent(csrf!.value) },
  })
  return { context, userInfo: loginResult.userInfo }
}

async function createRoleUser(
  admin: APIRequestContext,
  roleByCode: Map<string, Role>,
  roleCode: string,
  tag: string,
) {
  const username = `${roleCode.toLowerCase()}_${tag}`.slice(0, 48)
  const id = String(
    await post<string>(admin, '/api/system/users', {
      username,
      password: unique('Pwd'),
      realName: `${roleCode}-${tag}`,
      status: 'ENABLE',
      isAdmin: 0,
    }),
  )
  const role = roleByCode.get(roleCode)
  expect(role, `missing role ${roleCode}`).toBeTruthy()
  await put<void>(admin, `/api/system/users/${id}/roles`, { userId: id, roleIds: [role!.id] })
  return { id, username }
}

async function waitTask(
  context: APIRequestContext,
  businessType: string,
  businessId: string,
): Promise<Task> {
  for (let attempt = 0; attempt < 30; attempt += 1) {
    const page = await get<PageResult<Task>>(context, '/api/workflow/tasks/todo', {
      pageNo: 1,
      pageSize: 100,
      businessType,
    })
    const task = page.records.find(
      (row) => row.businessType === businessType && String(row.businessId) === String(businessId),
    )
    if (task) return task
    await new Promise((resolve) => setTimeout(resolve, 200))
  }
  throw new Error(`No todo task for ${businessType}/${businessId}`)
}

async function approve(context: APIRequestContext, businessType: string, businessId: string) {
  const task = await waitTask(context, businessType, businessId)
  await post<void>(context, `/api/workflow/tasks/${task.id}/approve`, {
    action: 'APPROVE',
    comment: 'FLOW-001 通过',
    idempotencyKey: unique('approve'),
  })
  return task
}

async function reject(context: APIRequestContext, businessType: string, businessId: string) {
  const task = await waitTask(context, businessType, businessId)
  await post<void>(context, `/api/workflow/tasks/${task.id}/reject`, {
    action: 'REJECT',
    comment: 'FLOW-001 驳回后修订',
    idempotencyKey: unique('reject'),
  })
  return task
}

async function uploadEvidence(
  context: APIRequestContext,
  businessType: string,
  businessId: string,
  documentType: string,
) {
  return data<{ id: string; virusScanStatus: string }>(
    await context.post('/api/files/upload', {
      multipart: {
        file: {
          name: `${businessType}-${businessId}.pdf`,
          mimeType: 'application/pdf',
          buffer: PROOF,
        },
        businessType,
        businessId,
        documentType,
      },
    }),
  )
}

function findLeaves(
  rows: Array<{ id: string; status?: string; accountCategory?: string; children?: unknown[] }>,
): string[] {
  const result: string[] = []
  for (const row of rows) {
    const children = (row.children ?? []) as Array<{
      id: string
      status?: string
      accountCategory?: string
      children?: unknown[]
    }>
    if (row.status === 'ENABLE' && row.accountCategory === 'COST' && children.length === 0) {
      result.push(String(row.id))
    }
    result.push(...findLeaves(children))
  }
  return result
}

async function assertRolePage(browser: Browser, username: string, path: string) {
  const context = await browser.newContext({ baseURL: UI_BASE })
  const page = await context.newPage()
  try {
    await page.goto(
      `/api/auth/dev-login?username=${encodeURIComponent(username)}&redirect=${encodeURIComponent(path)}`,
    )
    await page.waitForURL((url) => !url.pathname.includes('/login'))
    await expect(page.locator('.basic-layout, .lg-page, .app-page').first()).toBeVisible()
    await expect(page.getByText('403')).toHaveCount(0)
  } finally {
    await context.close()
  }
}

test.describe.configure({ mode: 'serial' })

test('FLOW-001 real-role payment closed loop', async ({ browser, playwright }) => {
  test.setTimeout(180_000)
  const tag = unique('flow001').replaceAll('-', '').slice(-18)
  const opened: APIRequestContext[] = []
  const adminLogin = await login(playwright)
  const admin = adminLogin.context
  opened.push(admin)

  const roles = await get<Role[]>(admin, '/api/system/roles')
  const roleByCode = new Map(roles.map((role) => [role.roleCode, role]))
  const users = {
    pm: await createRoleUser(admin, roleByCode, 'PROJECT_MANAGER', tag),
    cost: await createRoleUser(admin, roleByCode, 'COST_MANAGER', tag),
    department: await createRoleUser(admin, roleByCode, 'DEPARTMENT_MANAGER', tag),
    general: await createRoleUser(admin, roleByCode, 'GENERAL_MANAGER', tag),
    finance: await createRoleUser(admin, roleByCode, 'FINANCE', tag),
    financeReviewer: await createRoleUser(admin, roleByCode, 'FINANCE', `${tag}r`),
    outsider: await createRoleUser(admin, roleByCode, 'FINANCE', `${tag}x`),
  }

  const pmLogin = await login(playwright, users.pm.username)
  const costLogin = await login(playwright, users.cost.username)
  const departmentLogin = await login(playwright, users.department.username)
  const generalLogin = await login(playwright, users.general.username)
  const financeLogin = await login(playwright, users.finance.username)
  const financeReviewerLogin = await login(playwright, users.financeReviewer.username)
  const outsiderLogin = await login(playwright, users.outsider.username)
  const pm = pmLogin.context
  const cost = costLogin.context
  const department = departmentLogin.context
  const general = generalLogin.context
  const finance = financeLogin.context
  const financeReviewer = financeReviewerLogin.context
  const outsider = outsiderLogin.context
  opened.push(pm, cost, department, general, finance, financeReviewer, outsider)

  expect(pmLogin.userInfo.roles).toContain('PROJECT_MANAGER')
  expect(costLogin.userInfo.roles).toContain('COST_MANAGER')
  expect(departmentLogin.userInfo.roles).toContain('DEPARTMENT_MANAGER')
  expect(generalLogin.userInfo.roles).toContain('GENERAL_MANAGER')
  expect(financeLogin.userInfo.roles).toContain('FINANCE')
  expect(financeReviewerLogin.userInfo.roles).toContain('FINANCE')
  expect(outsiderLogin.userInfo.roles).toContain('FINANCE')
  expect(pmLogin.userInfo.permissions).toContain('payment:app:add')
  expect(pmLogin.userInfo.permissions).toContain('payment:app:edit')
  expect(pmLogin.userInfo.permissions).toContain('payment:app:submit')
  expect(pmLogin.userInfo.permissions).toContain('project:query')
  expect(pmLogin.userInfo.permissions).toContain('workflow:resubmit')
  expect(financeLogin.userInfo.permissions).toContain('project:query')
  expect(financeLogin.userInfo.permissions).toContain('payment:record:writeback')

  try {
    const subjectTree = await get<
      Array<{ id: string; status?: string; accountCategory?: string; children?: unknown[] }>
    >(admin, '/api/cost-subjects/tree', { category: 'COST' })
    const costSubjectIds = findLeaves(subjectTree)
    expect(
      costSubjectIds.length,
      'two enabled detail cost subjects required',
    ).toBeGreaterThanOrEqual(2)
    const [costSubjectId, secondCostSubjectId] = costSubjectIds

    const projectId = String(
      await post<string>(admin, '/api/projects', {
        projectName: `FLOW-001项目-${tag}`,
        projectType: 'CONSTRUCTION',
        contractAmount: '600000.00',
        targetCost: '500000.00',
        plannedStartDate: '2026-01-01',
        plannedEndDate: '2027-12-31',
        projectManagerId: users.pm.id,
      }),
    )
    await post<string>(admin, `/api/projects/${projectId}/members`, {
      userId: users.finance.id,
      roleCode: 'FINANCE',
      positionName: '财务经办',
      startDate: '2026-01-01',
      status: 'ACTIVE',
    })
    await post<string>(admin, `/api/projects/${projectId}/members`, {
      userId: users.financeReviewer.id,
      roleCode: 'FINANCE',
      positionName: '财务复核',
      startDate: '2026-01-01',
      status: 'ACTIVE',
    })

    const budgetId = String(
      await post<string>(cost, '/api/project-budgets', {
        projectId,
        versionNo: `V-${tag}`,
        budgetName: `FLOW-001预算-${tag}`,
        totalAmount: '1000000.00',
      }),
    )
    await post<void>(cost, `/api/project-budgets/${budgetId}/lines?version=0`, [
      { costSubjectId, budgetAmount: '600000.00', remark: 'FLOW-001付款科目' },
      { costSubjectId: secondCostSubjectId, budgetAmount: '400000.00', remark: 'FLOW-001对照科目' },
    ])
    let budget = await get<Budget>(cost, `/api/project-budgets/${budgetId}`)
    await post<void>(cost, `/api/project-budgets/${budgetId}/submit?version=${budget.version}`)
    await approve(pm, 'PROJECT_BUDGET', budgetId)
    await approve(cost, 'PROJECT_BUDGET', budgetId)
    await approve(general, 'PROJECT_BUDGET', budgetId)
    budget = await get<Budget>(cost, `/api/project-budgets/${budgetId}`)
    expect(budget.approvalStatus).toBe('APPROVED')
    expect(budget.status).toBe('ACTIVE')
    const budgetLineId = budget.lines.find((line) => line.costSubjectId === costSubjectId)!.id

    await put<void>(pm, `/api/projects/${projectId}/status`, {
      targetStatus: 'ACTIVE',
      reason: 'FLOW-001预算已生效',
    })
    const project = await get<{ status: string }>(pm, `/api/projects/${projectId}`)
    expect(project.status).toBe('ACTIVE')

    const partyAId = String(
      await post<string>(admin, '/api/partners', {
        partnerCode: unique('FLOWA'),
        partnerName: `FLOW-001甲方-${tag}`,
        partnerType: 'CUSTOMER',
        status: 'ENABLE',
        blacklistFlag: 0,
      }),
    )
    const partnerId = String(
      await post<string>(admin, '/api/partners', {
        partnerCode: unique('FLOWP'),
        partnerName: `FLOW-001付款对象-${tag}`,
        partnerType: 'SUBCONTRACTOR',
        bankName: 'FLOW-001银行',
        bankAccount: '6222000012345678',
        status: 'ENABLE',
        blacklistFlag: 0,
      }),
    )
    const contractId = String(
      await post<string>(admin, '/api/contracts/composite', {
        contract: {
          contractCode: unique('FLOWC'),
          contractName: `FLOW-001合同-${tag}`,
          contractType: 'SUB',
          projectId,
          partyAId,
          partyBId: partnerId,
          contractAmount: '600000.00',
          currentAmount: '600000.00',
          taxRate: '0.00',
          taxAmount: '0.00',
          amountWithoutTax: '600000.00',
          signedDate: '2026-01-10',
          paymentMethod: '银行转账',
          settlementMethod: '按进度结算',
        },
        items: [
          {
            itemName: 'FLOW-001',
            itemSpec: '1',
            unit: '项',
            quantity: 1,
            unitPrice: '600000.00',
            amount: '600000.00',
            taxRate: '0.00',
            taxAmount: '0.00',
            amountWithoutTax: '600000.00',
          },
        ],
        paymentTerms: [
          {
            termName: '进度款',
            paymentRatio: 100,
            paymentAmount: '600000.00',
            paymentCondition: '审批后支付',
            plannedDate: '2026-12-31',
          },
        ],
        submitForApproval: false,
      }),
    )
    await put<void>(admin, `/api/contracts/${contractId}/budget-allocations`, [
      { contractId, budgetLineId, allocatedAmount: '600000.00' },
    ])
    const draftContract = await get<{ version: number }>(admin, `/api/contracts/${contractId}`)
    await post<void>(admin, `/api/contracts/${contractId}/submit?version=${draftContract.version}`)
    await approve(pm, 'CONTRACT_APPROVAL', contractId)
    await approve(department, 'CONTRACT_APPROVAL', contractId)
    await approve(general, 'CONTRACT_APPROVAL', contractId)
    const contract = await get<{ approvalStatus: string; contractStatus: string }>(
      admin,
      `/api/contracts/${contractId}`,
    )
    expect(contract.approvalStatus).toBe('APPROVED')
    expect(contract.contractStatus).toBe('PERFORMING')

    const invalidExpense = await pm.post('/api/expenses', {
      data: { projectId, contractId },
    })
    expect(invalidExpense.ok()).toBeFalsy()

    const missingCategoryExpense = await pm.post('/api/expenses', {
      data: {
        projectId,
        contractId,
        costSubjectId,
        budgetLineId,
        payeePartnerId: partnerId,
        expenseDate: '2026-07-26',
        amount: '1000.00',
        description: `FLOW-001缺分类-${tag}`,
      },
    })
    expect(missingCategoryExpense.ok()).toBeFalsy()

    const insufficientExpenseId = String(
      await post<string>(pm, '/api/expenses', {
        projectId,
        contractId,
        costSubjectId,
        budgetLineId,
        payeePartnerId: partnerId,
        expenseCategory: 'MATERIAL',
        expenseDate: '2026-07-26',
        amount: '600001.00',
        description: `FLOW-001预算不足-${tag}`,
      }),
    )
    await uploadEvidence(pm, 'EXPENSE', insufficientExpenseId, 'OTHER')
    const insufficientExpenseSubmit = await pm.post(`/api/expenses/${insufficientExpenseId}/submit`)
    expect(insufficientExpenseSubmit.ok()).toBeFalsy()

    const expenseId = String(
      await post<string>(pm, '/api/expenses', {
        projectId,
        contractId,
        costSubjectId,
        budgetLineId,
        payeePartnerId: partnerId,
        expenseCategory: 'MATERIAL',
        expenseDate: '2026-07-26',
        amount: '100000.00',
        description: `FLOW-001费用-${tag}`,
      }),
    )
    await uploadEvidence(pm, 'EXPENSE', expenseId, 'OTHER')
    await post<void>(pm, `/api/expenses/${expenseId}/submit`)
    await approve(pm, 'EXPENSE', expenseId)
    await approve(cost, 'EXPENSE', expenseId)
    await approve(finance, 'EXPENSE', expenseId)
    expect(
      (await get<{ approvalStatus: string }>(pm, `/api/expenses/${expenseId}`)).approvalStatus,
    ).toBe('APPROVED')

    const paymentId = String(
      await post<string>(pm, '/api/pay-applications', {
        projectId,
        contractId,
        partnerId,
        costSubjectId,
        budgetLineId,
        expenseCategory: 'MATERIAL',
        applyAmount: '100000.00',
        payType: 'BANK_TRANSFER',
        applyReason: `FLOW-001付款-${tag}`,
      }),
    )
    await post<void>(pm, `/api/pay-applications/${paymentId}/sources/batch`, [
      { sourceType: 'EXPENSE', sourceRefId: expenseId, sourceAmount: '100000.00' },
    ])
    const missingPaymentEvidence = await pm.post(`/api/pay-applications/${paymentId}/submit`)
    expect(missingPaymentEvidence.ok()).toBeFalsy()

    const directPaymentId = String(
      await post<string>(pm, '/api/pay-applications', {
        projectId,
        contractId,
        partnerId,
        costSubjectId,
        budgetLineId,
        expenseCategory: 'MATERIAL',
        applyAmount: '1000.00',
        payType: 'BANK_TRANSFER',
        applyReason: `FLOW-001直接付款无权限-${tag}`,
      }),
    )
    const directDenied = await pm.post(`/api/pay-applications/${directPaymentId}/sources/batch`, {
      data: [
        {
          sourceType: 'DIRECT',
          sourceRefId: directPaymentId,
          sourceAmount: '1000.00',
        },
      ],
    })
    expect(directDenied.ok()).toBeFalsy()

    const wrongPayeePaymentId = String(
      await post<string>(pm, '/api/pay-applications', {
        projectId,
        contractId,
        partnerId: partyAId,
        costSubjectId,
        budgetLineId,
        expenseCategory: 'MATERIAL',
        applyAmount: '100000.00',
        payType: 'BANK_TRANSFER',
        applyReason: `FLOW-001付款对象错误-${tag}`,
      }),
    )
    const wrongPayeeSource = await pm.post(
      `/api/pay-applications/${wrongPayeePaymentId}/sources/batch`,
      {
        data: [{ sourceType: 'EXPENSE', sourceRefId: expenseId, sourceAmount: '100000.00' }],
      },
    )
    expect(wrongPayeeSource.ok()).toBeFalsy()

    await uploadEvidence(pm, 'PAYMENT', paymentId, 'PAYMENT_PROOF')
    await post<void>(pm, `/api/pay-applications/${paymentId}/submit`)
    const duplicateSubmit = await pm.post(`/api/pay-applications/${paymentId}/submit`)
    expect(duplicateSubmit.ok()).toBeFalsy()
    budget = await get<Budget>(cost, `/api/project-budgets/${budgetId}`)
    expect(budget.lines.find((line) => line.id === budgetLineId)!.reservedAmount).toBe('100000.00')

    await approve(pm, 'PAY_REQUEST', paymentId)
    const rejectedTask = await reject(department, 'PAY_REQUEST', paymentId)
    let payment = await get<Payment>(pm, `/api/pay-applications/${paymentId}`)
    expect(payment.approvalStatus).toBe('REJECTED')
    await put<void>(pm, `/api/pay-applications/${paymentId}`, {
      projectId,
      contractId,
      partnerId,
      costSubjectId,
      budgetLineId,
      expenseCategory: 'MATERIAL',
      applyAmount: '100000.00',
      payType: 'BANK_TRANSFER',
      applyReason: `FLOW-001驳回修订-${tag}`,
    })
    await post<void>(pm, `/api/workflow/instances/${rejectedTask.instanceId}/resubmit`)
    const roundTwo = await get<{ currentRound: number; records: Array<{ roundNo: number }> }>(
      pm,
      `/api/workflow/instances/${rejectedTask.instanceId}`,
    )
    expect(roundTwo.currentRound).toBe(2)
    expect(roundTwo.records.some((record) => record.roundNo === 1)).toBeTruthy()
    await approve(pm, 'PAY_REQUEST', paymentId)
    await approve(department, 'PAY_REQUEST', paymentId)
    await approve(general, 'PAY_REQUEST', paymentId)
    payment = await get<Payment>(pm, `/api/pay-applications/${paymentId}`)
    expect(payment.approvalStatus).toBe('APPROVED')

    const fundAccount = await post<{ id: string }>(admin, '/api/fund-accounts', {
      accountCode: unique('FLOWA'),
      accountName: `FLOW-001账户-${tag}`,
      accountType: 'BANK',
      bankName: 'FLOW-001银行',
      bankAccountNo: '6222000012345678',
      openingDate: '2026-01-01',
      openingBalance: '10000000.00',
    })
    const firstWriteback = {
      payApplicationId: paymentId,
      payAmount: '60000.00',
      fundAccountId: fundAccount.id,
      paidAt: '2026-07-26 10:00:00',
      payMethod: 'BANK_TRANSFER',
      externalTxnNo: unique('FLOWTXN1'),
    }
    const [firstPayment, duplicate] = await Promise.all([
      post<PayRecord>(finance, '/api/pay-records/writeback', firstWriteback),
      post<PayRecord>(finance, '/api/pay-records/writeback', firstWriteback),
    ])
    expect(duplicate.id).toBe(firstPayment.id)
    const firstJournals = await get<PageResult<CashJournal>>(finance, '/api/cash-journal-entries', {
      pageNo: 1,
      pageSize: 20,
      sourceId: firstPayment.id,
    })
    const firstJournal = firstJournals.records.find((row) => row.sourceId === firstPayment.id)
    expect(firstJournal?.status).toBe('PENDING_ARCHIVE')
    await uploadEvidence(finance, 'CASH_JOURNAL', firstJournal!.id, 'BANK_RECEIPT')
    await post<void>(finance, `/api/cash-journal-entries/${firstJournal!.id}/archive`)
    const duplicateArchive = await finance.post(
      `/api/cash-journal-entries/${firstJournal!.id}/archive`,
    )
    expect(duplicateArchive.ok()).toBeFalsy()

    budget = await get<Budget>(cost, `/api/project-budgets/${budgetId}`)
    expect(budget.lines.find((line) => line.id === budgetLineId)!.reservedAmount).toBe('40000.00')
    expect(budget.lines.find((line) => line.id === budgetLineId)!.consumedAmount).toBe('60000.00')

    const secondPayment = await post<PayRecord>(finance, '/api/pay-records/writeback', {
      payApplicationId: paymentId,
      payAmount: '40000.00',
      fundAccountId: fundAccount.id,
      paidAt: '2026-07-26 11:00:00',
      payMethod: 'BANK_TRANSFER',
      externalTxnNo: unique('FLOWTXN2'),
    })
    const secondJournals = await get<PageResult<CashJournal>>(
      finance,
      '/api/cash-journal-entries',
      {
        pageNo: 1,
        pageSize: 20,
        sourceId: secondPayment.id,
      },
    )
    const secondJournal = secondJournals.records.find((row) => row.sourceId === secondPayment.id)
    expect(secondJournal?.id).not.toBe(firstJournal?.id)
    expect(secondJournal?.status).toBe('PENDING_ARCHIVE')
    await uploadEvidence(finance, 'CASH_JOURNAL', secondJournal!.id, 'BANK_RECEIPT')
    await post<void>(finance, `/api/cash-journal-entries/${secondJournal!.id}/archive`)
    payment = await get<Payment>(finance, `/api/pay-applications/${paymentId}`)
    expect(payment.payStatus).toBe('PAID')
    budget = await get<Budget>(cost, `/api/project-budgets/${budgetId}`)
    expect(budget.lines.find((line) => line.id === budgetLineId)!.reservedAmount).toBe('0.00')
    expect(budget.lines.find((line) => line.id === budgetLineId)!.consumedAmount).toBe('100000.00')

    const invoiceId = String(
      await post<string>(finance, '/api/invoices', {
        payRecordId: firstPayment.id,
        invoiceNo: unique('FLOWINV'),
        invoiceType: 'VAT_SPECIAL',
        documentType: 'ELECTRONIC_INVOICE',
        invoiceAmount: '100000.00',
        invoiceDate: '2026-07-26',
      }),
    )
    await post<void>(finance, `/api/invoices/${invoiceId}/allocations/batch`, [
      { payRecordId: firstPayment.id, allocatedAmount: '60000.00' },
      { payRecordId: secondPayment.id, allocatedAmount: '40000.00' },
    ])
    const allocatedInvoiceDelete = await finance.delete(`/api/invoices/${invoiceId}`)
    expect(allocatedInvoiceDelete.ok()).toBeFalsy()
    await uploadEvidence(finance, 'INVOICE', invoiceId, 'ELECTRONIC_INVOICE')
    await post<void>(finance, `/api/invoices/${invoiceId}/verify`, { verifyStatus: 'VERIFIED' })
    const invoice = await get<{ verifyStatus: string }>(finance, `/api/invoices/${invoiceId}`)
    expect(invoice.verifyStatus).toBe('VERIFIED')
    const verifiedInvoiceReversal = await finance.post(
      `/api/pay-records/${firstPayment.id}/reverse`,
      {
        data: {
          reversalType: 'REVERSAL',
          externalTxnNo: unique('FLOWREV'),
          reversedAt: '2026-07-26 12:00:00',
          reason: 'FLOW-001核验发票阻断冲销',
        },
      },
    )
    expect(verifiedInvoiceReversal.ok()).toBeFalsy()

    const entries = await get<PageResult<AccountingEntry>>(finance, '/api/accounting-entry', {
      pageNo: 1,
      pageSize: 100,
      entryType: 'PAYMENT',
    })
    const paymentEntries = entries.records.filter((entry) =>
      [firstPayment.id, secondPayment.id].includes(String(entry.payRecordId)),
    )
    expect(paymentEntries).toHaveLength(2)
    for (const entry of paymentEntries) {
      expect(entry.totalDebit).toBe(entry.totalCredit)
      await put<void>(financeReviewer, `/api/accounting-entry/${entry.id}/review`, {
        approved: true,
        comment: 'FLOW-001复核',
      })
      await put<void>(financeReviewer, `/api/accounting-entry/${entry.id}/post`)
      const detail = await get<{ entry: AccountingEntry }>(
        finance,
        `/api/accounting-entry/${entry.id}`,
      )
      expect(detail.entry.entryStatus).toBe('POSTED')
    }
    await post(admin, `/api/cost-summary/${projectId}/refresh`)

    const dashboard = await get<{
      totalContractAmount: string
      totalPaidAmount: string
      budgetAmount: string
      budgetReservedAmount: string
      budgetConsumedAmount: string
      budgetExecutionRate: string
      cashOutflowAmount: string
    }>(finance, '/api/dashboard/finance', { projectId })
    expect(dashboard.totalContractAmount).toBe('600000.00')
    expect(dashboard.totalPaidAmount).toBe('100000.00')
    expect(dashboard.budgetAmount).toBe('1000000.00')
    expect(dashboard.budgetReservedAmount).toBe('0.00')
    expect(dashboard.budgetConsumedAmount).toBe('100000.00')
    expect(Number(dashboard.budgetExecutionRate)).toBe(10)
    expect(dashboard.cashOutflowAmount).toBe('100000.00')
    const management = await get<{
      metricSources: Array<{ projectId: string; paidAmount: string }>
    }>(general, '/api/dashboard/management', { projectId })
    expect(
      management.metricSources.some(
        (source) => source.projectId === projectId && source.paidAmount === '100000.00',
      ),
    ).toBeTruthy()

    const trace = await get<{
      project: { id: string }
      contract: { id: string }
      paymentApplication: { id: string }
      approvalRecords: unknown[]
      expenses: Array<{ id: string }>
      paymentRecords: Array<{ id: string }>
      cashJournals: Array<{ id: string }>
      invoices: Array<{ id: string }>
      invoiceAllocations: unknown[]
      budgetLedgers: unknown[]
      accountingEntries: Array<{ id: string }>
    }>(finance, `/api/payment-traces/cash-journals/${secondJournal!.id}`)
    expect(String(trace.project.id)).toBe(projectId)
    expect(String(trace.contract.id)).toBe(contractId)
    expect(String(trace.paymentApplication.id)).toBe(paymentId)
    expect(trace.expenses.some((row) => String(row.id) === expenseId)).toBeTruthy()
    expect(trace.paymentRecords).toHaveLength(2)
    expect(trace.cashJournals).toHaveLength(2)
    expect(trace.invoices.some((row) => String(row.id) === invoiceId)).toBeTruthy()
    expect(trace.invoiceAllocations).toHaveLength(2)
    expect(trace.approvalRecords.length).toBeGreaterThanOrEqual(5)
    expect(trace.budgetLedgers.length).toBeGreaterThan(0)
    expect(trace.accountingEntries).toHaveLength(2)

    mysql(`
      UPDATE ct_contract
         SET contract_status='TERMINATED'
       WHERE id=${contractId}
         AND tenant_id=0
         AND approval_status='APPROVED';
    `)
    const terminatedContract = await get<{ approvalStatus: string; contractStatus: string }>(
      admin,
      `/api/contracts/${contractId}`,
    )
    expect(terminatedContract.approvalStatus).toBe('APPROVED')
    expect(terminatedContract.contractStatus).toBe('TERMINATED')
    const terminatedContractPaymentId = String(
      await post<string>(admin, '/api/pay-applications', {
        projectId,
        contractId,
        partnerId,
        costSubjectId,
        budgetLineId,
        expenseCategory: 'MATERIAL',
        applyAmount: '1000.00',
        payType: 'BANK_TRANSFER',
        applyReason: `FLOW-001终止合同拒绝-${tag}`,
      }),
    )
    await post<void>(admin, `/api/pay-applications/${terminatedContractPaymentId}/sources/batch`, [
      {
        sourceType: 'DIRECT',
        sourceRefId: terminatedContractPaymentId,
        sourceAmount: '1000.00',
      },
    ])
    await uploadEvidence(admin, 'PAYMENT', terminatedContractPaymentId, 'PAYMENT_PROOF')
    await expectError(
      await admin.post(`/api/pay-applications/${terminatedContractPaymentId}/submit`),
      400,
      'CONTRACT_STATUS_INVALID',
    )
    mysql(`
      UPDATE ct_contract
         SET contract_status='PERFORMING'
       WHERE id=${contractId}
         AND tenant_id=0
         AND approval_status='APPROVED'
         AND contract_status='TERMINATED';
    `)
    expect(
      (await get<{ contractStatus: string }>(admin, `/api/contracts/${contractId}`)).contractStatus,
    ).toBe('PERFORMING')

    const outsiderProjectId = String(
      await post<string>(admin, '/api/projects', {
        projectName: `FLOW-001跨项目-${tag}`,
        projectType: 'CONSTRUCTION',
        contractAmount: '10000.00',
        targetCost: '8000.00',
        plannedStartDate: '2026-01-01',
        plannedEndDate: '2027-12-31',
        projectManagerId: users.pm.id,
      }),
    )
    await post<string>(admin, `/api/projects/${outsiderProjectId}/members`, {
      userId: users.outsider.id,
      roleCode: 'FINANCE',
      positionName: '跨项目财务',
      startDate: '2026-01-01',
      status: 'ACTIVE',
    })
    expect(
      String((await get<{ id: string }>(outsider, `/api/projects/${outsiderProjectId}`)).id),
    ).toBe(outsiderProjectId)
    const crossProjectTrace = await outsider.get(
      `/api/payment-traces/cash-journals/${secondJournal!.id}`,
    )
    await expectError(crossProjectTrace, 403, 'AUTH_FORBIDDEN')

    const seed = Date.now()
    const crossTenantId = 800_000_000 + (seed % 100_000_000)
    const crossTenantUserId = seed * 1000 + 11
    const crossTenantUsername = `tenant${tag}`
    const crossTenant = await playwright.request.newContext({
      baseURL: API_BASE,
      storageState: { cookies: [], origins: [] },
      extraHTTPHeaders: {
        Authorization: `Bearer ${backendAccessToken(
          crossTenantUsername,
          crossTenantUserId,
          crossTenantId,
        )}`,
      },
    })
    opened.push(crossTenant)
    const crossTenantProjects = await get<PageResult<{ id: string }>>(
      crossTenant,
      '/api/projects',
      {
        pageNo: 1,
        pageSize: 1,
      },
    )
    expect(crossTenantProjects.records).toHaveLength(0)
    const crossTenantTrace = await crossTenant.get(
      `/api/payment-traces/cash-journals/${secondJournal!.id}`,
    )
    await expectError(crossTenantTrace, 400, 'CASH_JOURNAL_NOT_FOUND')

    await assertRolePage(browser, users.pm.username, '/payment/application')
    await assertRolePage(browser, users.finance.username, '/cash-journal')
    await assertRolePage(browser, users.general.username, '/dashboard')

    await put<void>(pm, `/api/projects/${projectId}/status`, {
      targetStatus: 'SUSPENDED',
      reason: 'FLOW-001暂停项目负向',
    })
    const pausedProjectExpense = await pm.post('/api/expenses', {
      data: {
        projectId,
        contractId,
        costSubjectId,
        budgetLineId,
        payeePartnerId: partnerId,
        expenseCategory: 'MATERIAL',
        expenseDate: '2026-07-26',
        amount: '1000.00',
        description: `FLOW-001暂停项目-${tag}`,
      },
    })
    expect(pausedProjectExpense.ok()).toBeFalsy()
  } finally {
    await Promise.allSettled(opened.map((context) => context.dispose()))
  }
})
