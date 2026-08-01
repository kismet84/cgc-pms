import { expect, test, type Page, type Route } from '@playwright/test'

async function fulfill(route: Route, data: unknown) {
  await route.fulfill({
    status: 200,
    contentType: 'application/json',
    body: JSON.stringify({ code: '0', message: 'success', data }),
  })
}

async function install(page: Page) {
  await page.route('**/api/**', (route) => {
    const path = new URL(route.request().url()).pathname
    if (path === '/api/auth/userinfo') {
      return fulfill(route, {
        userId: '1',
        username: 'context.gate',
        realName: '公共上下文门禁',
        roles: ['SUPER_ADMIN'],
        permissions: ['*'],
      })
    }
    if (path === '/api/project-context/options') {
      return fulfill(route, [{ id: 'P1', projectName: '公共上下文验收项目', status: 'ACTIVE' }])
    }
    if (path === '/api/dashboard/project-manager') {
      return fulfill(route, {
        projectId: 'P1',
        projectName: '公共上下文验收项目',
        pendingTaskCount: 0,
        laggingProjectCount: 0,
        pendingApprovalCount: 0,
        expiringContractCount: 0,
        pendingTasks: [],
        laggingProjects: [],
        pendingApprovals: [],
        expiringContracts: [],
      })
    }
    if (path === '/api/auth/refresh') return route.abort()
    return fulfill(route, { records: [], total: 0, pageNo: 1, pageSize: 20 })
  })
}

async function select(page: Page, controlId: string, value: string) {
  await page.locator(controlId).selectOption(value)
}

test('keeps the M4 public context contract in the shared V2 shell', async ({ page }) => {
  await install(page)
  await page.goto('/dashboard')
  await expect(page.locator('#global-project')).toBeEnabled()

  await select(page, '#global-project', 'P1')
  const periodOption = page.locator('#global-report-period option:not([value=""])').first()
  const period = await periodOption.getAttribute('value')
  expect(period, 'concrete report period').toMatch(/^\d{4}-\d{2}$/)
  await page.locator('#global-report-period').selectOption(period!)
  await page.locator('[data-domain="delivery"] .app-shell__domain-link').click()
  await expect
    .poll(() => Object.fromEntries(new URL(page.url()).searchParams))
    .toMatchObject({ projectId: 'P1', period })
  await page.goto(`/dashboard?projectId=P1&period=${period}`)
  await expect(page.locator('#global-project')).toBeEnabled()
  await select(page, '#global-project', '')
  await select(page, '#global-report-period', '')
  await expect.poll(() => new URL(page.url()).search).toBe('')
  await expect(page.getByText(/请(?:先)?选择项目/)).toHaveCount(0)
})

test('removes unavailable context values from the URL after options load', async ({ page }) => {
  await install(page)
  await page.goto('/dashboard?projectId=missing&period=1900-01')

  await expect.poll(() => new URL(page.url()).search).toBe('')
  await expect(page.locator('#global-project')).toHaveAttribute('aria-label', '当前项目')
  await expect(page.locator('#global-report-period')).toHaveAttribute('aria-label', '报告期')
})
