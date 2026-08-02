import AxeBuilder from '@axe-core/playwright'
import { expect, test, type Page, type Route } from '@playwright/test'
import { installShellPreferencesMock } from './shell-session'
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
    'file:upload',
  ],
}
const denied = { ...business, userId: '2', username: 'denied', permissions: [] }
const budget = {
  id: '9007199254740993',
  projectId: 'P1',
  budgetCode: 'BUD-001',
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
  await installShellPreferencesMock(page)
  await page.route('**/api/auth/userinfo', (route) => fulfill(route, identity))
  await page.route('**/api/auth/refresh', (route) => fulfill(route, null, 401))
  await page.route('**/api/project-context/options', (route) =>
    fulfill(route, [
      { id: 'P1', projectName: '项目一', status: 'ACTIVE' },
      { id: 'P2', projectName: '项目二', status: 'ACTIVE' },
    ]),
  )
  await page.route('**/api/cost-subjects**', (route) =>
    fulfill(route, [
      { id: 'S1', subjectCode: 'COST-001', subjectName: '直接成本', status: 'ENABLE' },
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
    return fulfill(route, {
      id: '9007199254740996',
      version: '0',
      lines: [{ id: 'ML-NEW-1' }],
    })
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
      await page.goto(`${path}?projectId=P1&period=2026-07`)
      await expect(page).toHaveURL(/\/forbidden\?from=/)
      await expect(page.getByText('403', { exact: true })).toBeVisible()
    }
    expect(writes).toEqual([])
  })
  test('switches project and report period from the public shell for measurement', async ({
    page,
  }) => {
    const writes: string[] = []
    const traffic: string[] = []
    await install(page, writes, business, traffic)
    await page.goto('/production-measurement')
    await expect(
      page.getByRole('heading', { name: '产值计量与业主结算', level: 1, exact: true }),
    ).toBeVisible()

    const projectControl = page.locator('#global-project')
    await projectControl.selectOption('P2')
    await expect(page.getByText('ME-P2-', { exact: false }).first()).toBeVisible()

    const periodControl = page.locator('#global-report-period')
    const periodOptions = periodControl.locator('option:not([value=""])')
    const firstPeriod = (await periodOptions.nth(0).getAttribute('value'))!
    const secondPeriod = (await periodOptions.nth(1).getAttribute('value'))!
    await periodControl.selectOption(firstPeriod)
    await expect(page.getByText(`ME-P2-${firstPeriod}`, { exact: true })).toBeVisible()
    await projectControl.selectOption('P1')
    await expect(page.getByText('ME-1', { exact: true })).toBeVisible()
    await periodControl.selectOption(secondPeriod)
    await expect(page).toHaveURL(new RegExp(`projectId=P1.*period=${secondPeriod}`))

    const [firstYear, firstMonth] = firstPeriod.split('-').map(Number)
    const [secondYear, secondMonth] = secondPeriod.split('-').map(Number)
    const firstEnd = new Date(Date.UTC(firstYear!, firstMonth!, 0)).getUTCDate()
    const secondEnd = new Date(Date.UTC(secondYear!, secondMonth!, 0)).getUTCDate()
    const urls = traffic.map((entry) => new URL(entry))
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
      '/production-measurement?projectId=P1&contractId=C1&period=2026-07',
      '9007199254740993.12',
    )
    await page.getByRole('button', { name: '新建计量' }).click()
    const dialog = page.getByRole('dialog', { name: '新建产值计量' })
    await dialog.getByRole('combobox', { name: '业主合同' }).selectOption({ label: '业主合同' })
    await expect(dialog.getByRole('combobox', { name: '计量期间' })).toHaveValue('PR1')
    await expect(dialog.getByRole('checkbox')).toBeVisible()
    await dialog.getByRole('checkbox').check()
    await dialog.getByLabel('本次计量量').fill('9999999999999999.9999')
    await dialog.getByLabel('总体计量依据').setInputFiles({
      name: 'measurement.pdf',
      mimeType: 'application/pdf',
      buffer: Buffer.from('controlled evidence'),
    })
    await dialog.getByLabel('清单一现场完成依据').setInputFiles({
      name: 'measurement-line.pdf',
      mimeType: 'application/pdf',
      buffer: Buffer.from('controlled line evidence'),
    })
    await dialog.getByRole('button', { name: '创建计量' }).click()
    await expect(page.getByText('产值计量草稿已创建')).toBeVisible()
    expect(
      writes.filter((url) => new URL(url).pathname === '/api/production-measurements'),
    ).toHaveLength(1)
    expect(writes.filter((url) => new URL(url).pathname === '/api/files/upload')).toHaveLength(2)
  })
})
