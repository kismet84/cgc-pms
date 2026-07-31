import {
  test,
  expect,
  type APIRequestContext,
  type Browser,
  type BrowserContext,
  type Page,
  type Playwright,
} from '@playwright/test'

/**
 * Approval E2E: Pending tasks → modal detail → approve / reject.
 *
 * Current /approval/todo uses vxe-grid and opens details in an Ant modal;
 * it is not the old .wf-todo-page + independent detail route flow.
 */

let sharedContext: BrowserContext
let sharedPage: Page
let apiContext: APIRequestContext

const API_BASE_URL = 'http://localhost:8080'

type TodoTask = {
  id: string
  instanceId: string
  businessId: string
  title: string
  taskStatus: string
  instanceStatus: string
}

type InstanceTask = {
  id: string
  taskStatus: string
  actionType?: string | null
  comment?: string | null
}

type InstanceNode = {
  nodeStatus: string
  tasks?: InstanceTask[]
}

type InstanceRecord = {
  actionType: string
  comment?: string | null
  taskId?: string | null
}

type InstanceDetail = {
  id: string
  title: string
  instanceStatus: string
  availableActions: string[]
  nodes: InstanceNode[]
  records: InstanceRecord[]
}

type ApprovalSample = {
  businessId: string
  contractId: string
  taskId: string
  instanceId: string
  title: string
}

async function createAuthenticatedPage(browser: Browser) {
  const context = await browser.newContext({ storageState: 'e2e/.auth/admin.json' })
  const page = await context.newPage()
  return { context, page }
}

async function expectApiOk<T>(response: Awaited<ReturnType<APIRequestContext['get']>>): Promise<T> {
  expect(response.ok(), `API ${response.url()} should return HTTP 2xx`).toBeTruthy()
  const body = (await response.json()) as { code?: string; message?: string; data?: T }
  expect(body.code, `API ${response.url()} business code`).toMatch(/^(0|00000)$/)
  return body.data as T
}

async function apiPost<T>(url: string, data?: unknown): Promise<T> {
  const response = await apiContext.post(url, data ? { data } : undefined)
  return expectApiOk<T>(response)
}

async function apiGet<T>(url: string, params?: Record<string, string | number>): Promise<T> {
  const response = await apiContext.get(url, params ? { params } : undefined)
  return expectApiOk<T>(response)
}

function runId(prefix: string): string {
  return `${prefix}-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`
}

function actionTypeFromStatus(status: 'APPROVED' | 'REJECTED') {
  return status === 'APPROVED' ? 'APPROVE' : 'REJECT'
}

async function loginApi(playwright: Playwright) {
  apiContext = await playwright.request.newContext({
    baseURL: API_BASE_URL,
  })
  await apiPost('/api/auth/login', {
    username: 'admin',
    password: 'admin123',
  })
}

async function waitForTodoTaskByTitle(title: string, timeoutMs = 10000): Promise<TodoTask> {
  const deadline = Date.now() + timeoutMs
  while (Date.now() < deadline) {
    const page = await apiGet<{ records: TodoTask[] }>('/api/workflow/tasks/todo', {
      pageNo: 1,
      pageSize: 100,
    })
    const task = page.records.find(
      (record) => record.title === title && record.taskStatus === 'PENDING',
    )
    if (task) return task
    await new Promise((resolve) => setTimeout(resolve, 500))
  }
  throw new Error(
    `需要确认：创建审批样本后 ${timeoutMs}ms 内未在待办列表中看到标题为 ${title} 的 PENDING 任务`,
  )
}

async function getInstanceDetail(instanceId: string): Promise<InstanceDetail> {
  return apiGet<InstanceDetail>(`/api/workflow/instances/${instanceId}`)
}

async function createContractApprovalSample(): Promise<ApprovalSample> {
  const projectId = await apiPost<string>('/api/projects', {
    projectCode: runId('PRJ'),
    projectName: `E2E项目-${Date.now()}`,
    projectType: 'BUILDING',
    contractAmount: '5000000',
    plannedStartDate: '2025-01-01',
    plannedEndDate: '2026-12-31',
    status: 'ACTIVE',
  })

  const partyAId = await apiPost<string>('/api/partners', {
    partnerCode: runId('PTA'),
    partnerName: `E2E甲方-${Date.now()}`,
    partnerType: 'PARTY_A',
    status: 'ENABLE',
    blacklistFlag: 0,
  })

  const partyBId = await apiPost<string>('/api/partners', {
    partnerCode: runId('PTB'),
    partnerName: `E2E乙方-${Date.now()}`,
    partnerType: 'PARTY_B',
    status: 'ENABLE',
    blacklistFlag: 0,
  })

  const title = `E2E审批合同-${Date.now()}`
  const contractId = await apiPost<string>('/api/contracts/composite', {
    contract: {
      contractCode: runId('CT'),
      contractName: title,
      contractType: 'SUB',
      projectId,
      partyAId,
      partyBId,
      contractAmount: '1000',
      signedDate: '2025-06-01',
      paymentMethod: '银行转账',
      settlementMethod: '按进度结算',
    },
    items: [
      {
        itemName: 'AUTO',
        itemSpec: '1',
        unit: 'm3',
        quantity: 1,
        unitPrice: '1000',
      },
    ],
    paymentTerms: [
      {
        termName: '进度款',
        paymentRatio: 100,
        paymentAmount: '1000',
        paymentCondition: '完工后支付',
        plannedDate: '2026-06-01',
      },
    ],
    submitForApproval: false,
  })

  await apiPost<void>(`/api/contracts/${contractId}/submit`)
  const task = await waitForTodoTaskByTitle(title)
  return {
    businessId: task.businessId,
    contractId,
    taskId: task.id,
    instanceId: task.instanceId,
    title,
  }
}

async function openApprovalDetailByTitle(page: Page, title: string) {
  const row = page.locator('.vxe-body--row').filter({ hasText: title }).first()
  await expect(row).toBeVisible({ timeout: 10000 })
  await row.locator('.lg-link').first().click()
  await expect(page.locator('.approval-detail-modal .approval-detail-content')).toBeVisible({
    timeout: 10000,
  })
}

async function waitForTaskStatus(
  instanceId: string,
  taskId: string,
  expectedStatus: 'APPROVED' | 'REJECTED',
  expectedComment: string,
  timeoutMs = 10000,
) {
  const deadline = Date.now() + timeoutMs
  while (Date.now() < deadline) {
    const detail = await getInstanceDetail(instanceId)
    const task = detail.nodes
      .flatMap((node) => (Array.isArray(node.tasks) ? node.tasks : []))
      .find((item) => item.id === taskId)
    const record = detail.records.find(
      (item) =>
        item.taskId === taskId &&
        item.actionType === actionTypeFromStatus(expectedStatus) &&
        item.comment === expectedComment,
    )
    if (task?.taskStatus === expectedStatus && record) {
      return detail
    }
    await new Promise((resolve) => setTimeout(resolve, 500))
  }
  throw new Error(
    `需要确认：提交后 ${timeoutMs}ms 内未观察到任务 ${taskId} 进入 ${expectedStatus}，或审批记录缺少评论 ${expectedComment}`,
  )
}

async function waitForApprovalList(page: Page) {
  await page.goto('/approval/todo')
  await expect(page.getByText('处理需要您审批的业务单据')).toBeVisible({ timeout: 10000 })
  await expect(page.locator('.lg-table-wrap')).toBeVisible({ timeout: 10000 })
  await expect(page.locator('.vxe-table').first()).toBeVisible({ timeout: 10000 })
}

test.describe('Approval Workflow', () => {
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

  test('should display pending tasks and view approval detail', async () => {
    const sample = await createContractApprovalSample()
    await waitForApprovalList(sharedPage)

    await expect(sharedPage.locator('.ant-tabs-tab').filter({ hasText: '我的待办' })).toBeVisible()
    await expect(sharedPage.locator('.ant-tabs-tab').filter({ hasText: '我的已办' })).toBeVisible()
    await expect(sharedPage.locator('.ant-tabs-tab').filter({ hasText: '抄送我的' })).toBeVisible()

    await openApprovalDetailByTitle(sharedPage, sample.title)
    await expect(sharedPage.locator('.approval-detail-modal .ant-descriptions')).toBeVisible({
      timeout: 5000,
    })
    await expect(
      sharedPage
        .locator('.approval-detail-modal .approval-detail-section')
        .filter({ hasText: '审批流程' }),
    ).toBeVisible({ timeout: 5000 })
    await expect(
      sharedPage
        .locator('.approval-detail-modal .approval-detail-section')
        .filter({ hasText: '审批记录' }),
    ).toBeVisible({ timeout: 5000 })

    await sharedPage.screenshot({ path: 'e2e/screenshots/approval-detail.png', fullPage: true })
  })

  test('should approve a pending task successfully', async () => {
    const sample = await createContractApprovalSample()
    const approveComment = `E2E自动化审批通过-${Date.now()}`
    await waitForApprovalList(sharedPage)
    await openApprovalDetailByTitle(sharedPage, sample.title)

    const approveBtn = sharedPage
      .locator('.approval-detail-modal .approval-actions button')
      .filter({
        hasText: '同意',
      })
    await expect(approveBtn).toBeVisible({ timeout: 5000 })
    await approveBtn.click()
    const approveModal = sharedPage
      .locator('.ant-modal')
      .filter({ has: sharedPage.locator('textarea[placeholder="审批意见（选填）"]') })
      .last()
    await expect(approveModal).toBeVisible({ timeout: 5000 })
    await approveModal.locator('textarea').fill(approveComment)
    await approveModal.locator('.ant-modal-footer .ant-btn-primary').click()
    await expect(approveModal).toBeHidden({ timeout: 5000 })

    const detail = await waitForTaskStatus(
      sample.instanceId,
      sample.taskId,
      'APPROVED',
      approveComment,
    )
    expect(
      detail.records.some(
        (item) => item.actionType === 'APPROVE' && item.comment === approveComment,
      ),
    ).toBeTruthy()

    await sharedPage.screenshot({
      path: 'e2e/screenshots/approval-approve-success.png',
      fullPage: true,
    })
  })

  test('should reject a pending task with required comment', async () => {
    const sample = await createContractApprovalSample()
    const rejectComment = `E2E测试-驳回原因-${Date.now()}`
    await waitForApprovalList(sharedPage)
    await openApprovalDetailByTitle(sharedPage, sample.title)

    const rejectBtn = sharedPage.locator('.approval-detail-modal .approval-actions button').filter({
      hasText: '驳回',
    })
    await expect(rejectBtn).toBeVisible({ timeout: 5000 })
    await rejectBtn.click()
    const rejectModal = sharedPage
      .locator('.ant-modal')
      .filter({ has: sharedPage.locator('textarea[placeholder="请输入驳回原因（必填）"]') })
      .last()
    await expect(rejectModal).toBeVisible({ timeout: 5000 })
    await rejectModal.locator('textarea').fill(rejectComment)
    await rejectModal.locator('.ant-modal-footer .ant-btn-primary').click()
    await expect(rejectModal).toBeHidden({ timeout: 5000 })

    const detail = await waitForTaskStatus(
      sample.instanceId,
      sample.taskId,
      'REJECTED',
      rejectComment,
    )
    expect(detail.instanceStatus).toBe('REJECTED')

    await sharedPage.screenshot({
      path: 'e2e/screenshots/approval-reject-success.png',
      fullPage: true,
    })
  })
})
