import AxeBuilder from '@axe-core/playwright'
import { expect, test, type Page, type Route } from '@playwright/test'
import { captureRuntimeErrors } from './runtime-errors'
const business = {
  userId: '1',
  username: 'commercial.manager',
  realName: '商务经理',
  roles: ['USER'],
  permissions: [
    'budget:query',
    'budget:add',
    'budget:edit',
    'budget:delete',
    'budget:submit',
    'measurement:query',
    'measurement:maintain',
    'measurement:submit',
    'measurement:owner:submit',
    'measurement:owner:review',
    'contract:query',
  ],
}
const denied = { ...business, userId: '2', username: 'denied', permissions: [] }
const budget = {
  id: '9007199254740993',
  projectId: 'P1',
  versionNo: 'V1',
  budgetName: '项目预算',
  totalAmount: '9007199254740993.12',
  approvalStatus: 'DRAFT',
  status: 'DRAFT',
  active: false,
  version: '7',
  lines: [
    {
      id: 'L1',
      costSubjectId: 'S1',
      costSubjectName: '材料费',
      budgetAmount: '9007199254740993.12',
      reservedAmount: '0',
      consumedAmount: '-0.01',
      availableAmount: '9007199254740993.13',
    },
  ],
}
const measurement = {
  id: '9007199254740995',
  measure_code: 'ME-1',
  project_id: 'P1',
  period_name: '2026-07',
  current_reported_amount: '9007199254740993.12',
  cumulative_reported_amount: '9007199254740993.12',
  status: 'DRAFT',
  version: '9',
}
async function fulfill(route: Route, data: unknown, status = 200) {
  await route.fulfill({
    status,
    contentType: 'application/json',
    body: JSON.stringify({
      code: status === 200 ? '0' : 'TEST',
      message: status === 200 ? 'success' : '服务异常',
      data,
    }),
  })
}
async function install(page: Page, writes: string[], identity = business, traffic: string[] = []) {
  await page.route('**/api/auth/userinfo', (route) => fulfill(route, identity))
  await page.route('**/api/auth/refresh', (route) => fulfill(route, null, 401))
  await page.route('**/api/project-context/options', (route) =>
    fulfill(route, [
      { id: 'P1', projectName: '项目一', status: 'ACTIVE' },
      { id: 'P2', projectName: '项目二', status: 'ACTIVE' },
    ]),
  )
  await page.route('**/api/contracts**', (route) => {
    const projectId = new URL(route.request().url()).searchParams.get('projectId') ?? 'P1'
    return fulfill(route, {
      records: [{ id: projectId === 'P2' ? 'C2' : 'C1', contractName: '业主合同', projectId }],
      total: 1,
      pageNo: 1,
      pageSize: 100,
    })
  })
  await page.route('**/api/project-budgets**', (route) => {
    const request = route.request()
    const url = new URL(request.url())
    const path = url.pathname
    traffic.push(request.url())
    if (request.method() !== 'GET') writes.push(request.url())
    if (path.endsWith('/availability'))
      return fulfill(route, [
        {
          budgetId: budget.id,
          budgetLineId: 'L1',
          projectId: 'P1',
          costSubjectId: 'S1',
          budgetAmount: '9007199254740993.12',
          reservedAmount: '0',
          consumedAmount: '-0.01',
          availableAmount: '9007199254740993.13',
        },
      ])
    if (path.endsWith(`/project-budgets/${budget.id}`)) return fulfill(route, budget)
    const row =
      url.searchParams.get('projectId') === 'P2'
        ? {
            ...budget,
            id: '9007199254740994',
            projectId: 'P2',
            budgetName: `项目二预算-${url.searchParams.get('startDate')?.slice(0, 7) ?? '全部'}`,
          }
        : budget
    return fulfill(route, { records: [row], total: 1, pageNo: 1, pageSize: 20 })
  })
  await page.route('**/api/production-measurements**', (route) =>
    measurementRoute(route, writes, traffic),
  )
  await page.route('**/api/files/upload**', (route) => {
    writes.push(route.request().url())
    return fulfill(route, { id: 'F1', status: 'CLEAN' })
  })
}
async function measurementRoute(route: Route, writes: string[], traffic: string[] = []) {
  const request = route.request()
  const url = new URL(request.url())
  const path = url.pathname
  traffic.push(request.url())
  if (request.method() !== 'GET') writes.push(request.url())
  if (path.endsWith('/periods'))
    return fulfill(route, [{ id: 'PR1', period_name: '2026-07', status: 'OPEN', version: '2' }])
  if (path.endsWith('/sources'))
    return fulfill(route, [
      {
        sourceType: 'CONTRACT_ITEM',
        sourceId: 'I1',
        itemName: '清单一',
        remainingQuantity: '9999999999999999.9999',
        unitPrice: '0.01',
      },
    ])
  if (path.endsWith('/owner-submissions/list')) return fulfill(route, [])
  if (path === '/api/production-measurements' && request.method() === 'POST')
    return fulfill(route, { id: '9007199254740996', version: '0' })
  if (path === '/api/production-measurements')
    return fulfill(route, [
      url.searchParams.get('projectId') === 'P2'
        ? {
            ...measurement,
            id: '9007199254740997',
            project_id: 'P2',
            measure_code: `ME-P2-${url.searchParams.get('startDate')?.slice(0, 7) ?? '全部'}`,
            current_reported_amount: '22.22',
          }
        : measurement,
    ])
  if (path.endsWith(`/${measurement.id}`))
    return fulfill(route, { ...measurement, lines: [], submissions: [] })
  return fulfill(route, { ...measurement })
}
type BudgetOperation = { method: string; url: string; body: unknown }
async function installBudgetCrud(page: Page, operations: BudgetOperation[]) {
  const records = [{ ...budget, lines: budget.lines.map((line) => ({ ...line })) }]
  await page.unroute('**/api/project-budgets**')
  await page.route('**/api/project-budgets**', async (route) => {
    const request = route.request()
    const url = new URL(request.url())
    const path = url.pathname
    const method = request.method()
    const body = request.postData() ? request.postDataJSON() : null
    if (method !== 'GET') operations.push({ method, url: request.url(), body })
    if (path === '/api/project-budgets' && method === 'POST') {
      const command = body as Record<string, unknown>
      records.push({
        ...budget,
        ...command,
        id: 'NEW-1',
        approvalStatus: 'DRAFT',
        status: 'DRAFT',
        active: false,
        version: '0',
        lines: [],
      })
      return fulfill(route, 'NEW-1')
    }
    if (path === '/api/project-budgets' && method === 'GET')
      return fulfill(route, { records, total: records.length, pageNo: 1, pageSize: 20 })
    const id = path.split('/')[3] ?? ''
    const index = records.findIndex((row) => row.id === id)
    const row = records[index]
    if (!row) return fulfill(route, null, 404)
    if (path.endsWith('/availability'))
      return fulfill(
        route,
        row.lines.map((line) => ({
          budgetId: row.id,
          budgetLineId: line.id ?? 'NEW-LINE',
          projectId: row.projectId,
          costSubjectId: line.costSubjectId,
          budgetAmount: line.budgetAmount,
          reservedAmount: '1.11',
          consumedAmount: '2.22',
          availableAmount: '96.67',
        })),
      )
    if (path.endsWith('/lines') && method === 'POST') {
      row.lines = (body as typeof budget.lines).map((line, lineIndex) => ({
        ...line,
        id: line.id ?? `NEW-LINE-${lineIndex + 1}`,
      }))
      row.version = String(Number(row.version) + 1)
      return fulfill(route, null)
    }
    if (method === 'PUT') {
      Object.assign(row, body, { version: String(Number(row.version) + 1) })
      return fulfill(route, null)
    }
    if (method === 'DELETE') {
      records.splice(index, 1)
      return fulfill(route, null)
    }
    return fulfill(route, row)
  })
}
async function audit(page: Page, path: string, visible: string) {
  for (const viewport of [
    { width: 1440, height: 900 },
    { width: 1024, height: 768 },
    { width: 390, height: 844 },
  ]) {
    await page.setViewportSize(viewport)
    await page.goto(path)
    await expect(page.getByText(visible, { exact: true }).first()).toBeVisible()
    expect(
      await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth),
    ).toBe(true)
    const axe = await new AxeBuilder({ page }).analyze()
    expect(axe.violations.filter((v) => ['serious', 'critical'].includes(v.impact ?? ''))).toEqual(
      [],
    )
  }
}
test.describe('M4 budget and measurement routes', () => {
  test('fails closed with explicit 403 and zero business traffic', async ({ page }) => {
    const writes: string[] = []
    await install(page, writes, denied)
    for (const path of ['/budget', '/production-measurement']) {
      await page.goto(`/v2${path}?projectId=P1&period=2026-07`)
      await expect(page).toHaveURL(/\/v2\/forbidden\?from=/)
      await expect(page.getByText('403', { exact: true })).toBeVisible()
    }
    expect(writes).toEqual([])
  })
  test('renders budget in three viewports, opens big-id detail and deduplicates CAS submit', async ({
    page,
  }) => {
    const writes: string[] = []
    await install(page, writes)
    const errors = captureRuntimeErrors(page)
    await audit(page, '/v2/budget?projectId=P1&period=2026-07', '项目预算')
    await page.getByRole('button', { name: '详情' }).click()
    await expect(page.getByRole('dialog')).toContainText('9007199254740993')
    await expect(page.getByRole('dialog')).toContainText('9007199254740993.13')
    await page.getByRole('button', { name: '关闭' }).click()
    await page.getByRole('button', { name: '提交' }).dblclick()
    await expect(page.getByText('预算已提交')).toBeVisible()
    expect(
      writes.filter((url) => url.includes(`/project-budgets/${budget.id}/submit?version=7`)),
    ).toHaveLength(1)
    expect(errors).toEqual([])
  })
  test('creates, edits, saves lines and deletes a budget through versioned UI interactions', async ({
    page,
  }) => {
    const writes: string[] = []
    const operations: BudgetOperation[] = []
    await install(page, writes)
    await installBudgetCrud(page, operations)
    await page.goto('/v2/budget?projectId=P1&period=2026-07')
    await page.getByRole('button', { name: '新建预算' }).click()
    await page.getByLabel('预算版本号').fill('V-E2E')
    await page.getByLabel('预算名称').fill('E2E新增预算')
    await page.getByLabel('预算总额').fill('100.00')
    await page.getByRole('button', { name: '保存预算' }).click()
    await expect(page.getByText('预算已创建')).toBeVisible()

    let row = page.getByRole('row').filter({ hasText: 'E2E新增预算' })
    await row.getByRole('button', { name: '编辑' }).click()
    await page.getByLabel('预算名称').fill('E2E编辑预算')
    await page.getByRole('button', { name: '保存预算' }).click()
    await expect(page.getByText('预算已更新')).toBeVisible()

    row = page.getByRole('row').filter({ hasText: 'E2E编辑预算' })
    await row.getByRole('button', { name: '详情' }).click()
    await page.getByRole('button', { name: '添加明细' }).click()
    await page.getByLabel('成本科目ID').fill('SUBJECT-E2E')
    await page.getByLabel('预算金额').fill('100.00')
    await page.getByRole('button', { name: '保存明细' }).click()
    await expect(page.getByText('预算明细已保存')).toBeVisible()
    await expect(page.getByRole('dialog')).toContainText('96.67')
    await page.getByRole('button', { name: '关闭' }).click()

    row = page.getByRole('row').filter({ hasText: 'E2E编辑预算' })
    await row.getByRole('button', { name: '删除' }).click()
    await expect(page.getByText('预算已删除')).toBeVisible()
    await expect(page.getByRole('row').filter({ hasText: 'E2E编辑预算' })).toHaveCount(0)

    expect(operations).toEqual([
      expect.objectContaining({
        method: 'POST',
        url: expect.stringMatching(/\/api\/project-budgets$/),
        body: expect.objectContaining({ version: null, totalAmount: '100.00' }),
      }),
      expect.objectContaining({
        method: 'PUT',
        url: expect.stringContaining('/api/project-budgets/NEW-1?version=0'),
        body: expect.objectContaining({ budgetName: 'E2E编辑预算', version: '0' }),
      }),
      expect.objectContaining({
        method: 'POST',
        url: expect.stringContaining('/api/project-budgets/NEW-1/lines?version=1'),
      }),
      expect.objectContaining({
        method: 'DELETE',
        url: expect.stringContaining('/api/project-budgets/NEW-1?version=2'),
      }),
    ])
  })
  test('switches project and report period from the public shell for budget and measurement', async ({
    page,
  }) => {
    const writes: string[] = []
    const traffic: string[] = []
    await install(page, writes, business, traffic)
    await page.goto('/v2/budget')
    await expect(
      page.getByRole('heading', { name: '项目预算', level: 1, exact: true }),
    ).toBeVisible()

    const projectControl = page.locator('#global-project')
    await projectControl.click()
    await projectControl.locator('..').locator('[role="option"][data-value="P2"]').click()
    await expect(page.getByRole('row').filter({ hasText: '项目二预算-全部' })).toBeVisible()

    const periodControl = page.locator('#global-report-period')
    await periodControl.click()
    const periodOptions = periodControl
      .locator('..')
      .locator('[role="option"][data-value]:not([data-value=""])')
    const firstPeriod = (await periodOptions.nth(0).getAttribute('data-value'))!
    const secondPeriod = (await periodOptions.nth(1).getAttribute('data-value'))!
    await periodOptions.nth(0).click()
    await expect(
      page.getByRole('row').filter({ hasText: `项目二预算-${firstPeriod}` }),
    ).toBeVisible()

    await page.getByRole('link', { name: '产值计量' }).click()
    await expect(page.getByText(`ME-P2-${firstPeriod}`, { exact: true })).toBeVisible()
    await projectControl.click()
    await projectControl.locator('..').locator('[role="option"][data-value="P1"]').click()
    await expect(page.getByText('ME-1', { exact: true })).toBeVisible()
    await periodControl.click()
    await periodControl
      .locator('..')
      .locator(`[role="option"][data-value="${secondPeriod}"]`)
      .click()
    await expect(page).toHaveURL(new RegExp(`projectId=P1.*period=${secondPeriod}`))

    const [firstYear, firstMonth] = firstPeriod.split('-').map(Number)
    const [secondYear, secondMonth] = secondPeriod.split('-').map(Number)
    const firstEnd = new Date(Date.UTC(firstYear!, firstMonth!, 0)).getUTCDate()
    const secondEnd = new Date(Date.UTC(secondYear!, secondMonth!, 0)).getUTCDate()
    const urls = traffic.map((entry) => new URL(entry))
    expect(
      urls.some(
        (url) =>
          url.pathname === '/api/project-budgets' &&
          url.searchParams.get('projectId') === 'P2' &&
          url.searchParams.get('startDate') === `${firstPeriod}-01` &&
          url.searchParams.get('endDate') === `${firstPeriod}-${String(firstEnd).padStart(2, '0')}`,
      ),
    ).toBe(true)
    expect(
      urls.some(
        (url) =>
          url.pathname === '/api/production-measurements' &&
          url.searchParams.get('projectId') === 'P2' &&
          url.searchParams.get('startDate') === `${firstPeriod}-01` &&
          url.searchParams.get('endDate') === `${firstPeriod}-${String(firstEnd).padStart(2, '0')}`,
      ),
    ).toBe(true)
    expect(
      urls.some(
        (url) =>
          url.pathname === '/api/production-measurements' &&
          url.searchParams.get('projectId') === 'P1' &&
          url.searchParams.get('startDate') === `${secondPeriod}-01` &&
          url.searchParams.get('endDate') ===
            `${secondPeriod}-${String(secondEnd).padStart(2, '0')}`,
      ),
    ).toBe(true)
  })
  test('renders measurement in three viewports and uploads controlled evidence once', async ({
    page,
  }) => {
    const writes: string[] = []
    await install(page, writes)
    await audit(
      page,
      '/v2/production-measurement?projectId=P1&contractId=C1&period=2026-07',
      '9007199254740993.12',
    )
    await page.getByRole('button', { name: '新建计量' }).click()
    const dialog = page.getByRole('dialog', { name: '新建产值计量' })
    const contract = dialog.getByRole('button', { name: /^业主合同：/ })
    await contract.press('ArrowDown')
    const contractOption = contract.locator('..').getByRole('option', { name: '业主合同' })
    await expect(contractOption).toBeFocused()
    await contractOption.press('Enter')
    await expect(dialog.getByRole('button', { name: '计量期间：2026-07' })).toBeVisible()
    await expect(dialog.getByRole('checkbox')).toBeVisible()
    await dialog.getByRole('checkbox').check()
    await dialog.getByLabel('本次计量量').fill('9999999999999999.9999')
    await dialog.getByLabel('总体计量依据').setInputFiles({
      name: 'measurement.pdf',
      mimeType: 'application/pdf',
      buffer: Buffer.from('controlled evidence'),
    })
    await dialog.getByRole('button', { name: '创建计量' }).dblclick()
    await expect(page.getByText('产值计量草稿已创建')).toBeVisible()
    expect(
      writes.filter((url) => new URL(url).pathname === '/api/production-measurements'),
    ).toHaveLength(1)
    expect(writes.filter((url) => new URL(url).pathname === '/api/files/upload')).toHaveLength(1)
  })
})
