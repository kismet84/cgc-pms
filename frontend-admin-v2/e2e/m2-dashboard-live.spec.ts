import AxeBuilder from '@axe-core/playwright'
import { expect, test } from '@playwright/test'

const runLiveDashboard = process.env.V2_LIVE_DASHBOARD === '1'

const roles = [
  { label: '项目经理', path: '/api/dashboard/project-manager' },
  { label: '商务经理', path: '/api/dashboard/business-manager' },
  { label: '成本经理', path: '/api/dashboard/cost-manager' },
  { label: '采购经理', path: '/api/dashboard/purchase-manager' },
  { label: '生产经理', path: '/api/dashboard/production-manager' },
  { label: '总工程师', path: '/api/dashboard/chief-engineer' },
  { label: '财务', path: '/api/dashboard/finance' },
  { label: '管理层', path: '/api/dashboard/management' },
] as const

test.describe('M2 live eight-role dashboard', () => {
  test.skip(!runLiveDashboard, 'Set V2_LIVE_DASHBOARD=1 only against the local test/demo runtime')

  test('cost trend follows project and card range selections without duplicate KPI summary', async ({
    page,
  }) => {
    const login = await page.goto('/api/auth/dev-login?username=demo.cost')
    expect(login?.ok()).toBe(true)
    await page.goto('/v2/dashboard?role=cost')

    const trendRows = page.locator('.trend-chart tbody tr')
    await expect(page.locator('.trend-chart canvas')).toBeVisible()
    await expect(trendRows).toHaveCount(7)
    await page.getByRole('button', { name: '近6个月' }).click()
    await expect(trendRows).toHaveCount(6)
    await page.getByRole('button', { name: '近3个月' }).click()
    await expect(trendRows).toHaveCount(3)

    const projectSelect = page.locator('#global-project')
    const activeProject = projectSelect.locator('option', { hasText: '劳务分包在建演示项目' })
    const projectId = await activeProject.getAttribute('value')
    expect(projectId).toBeTruthy()
    const projectResponse = page.waitForResponse((response) => {
      const url = new URL(response.url())
      return (
        url.pathname === '/api/dashboard/cost-manager' &&
        url.searchParams.get('projectId') === projectId
      )
    })
    await projectSelect.selectOption(projectId!)
    expect((await projectResponse).ok()).toBe(true)
    await page.getByRole('button', { name: '当年累计' }).click()
    await expect(trendRows).toHaveCount(7)
    await expect(page.locator('.trend-summary')).toHaveCount(0)
  })

  test('finance view exposes project payment trend and closed-loop indicators', async ({
    page,
  }) => {
    const login = await page.goto('/api/auth/dev-login?username=demo.finance')
    expect(login?.ok()).toBe(true)
    await page.goto('/v2/dashboard?role=finance')

    const projectSelect = page.locator('#global-project')
    const projectId = '520000000000009002'
    await expect(projectSelect.locator(`option[value="${projectId}"]`)).toHaveText(
      '劳务分包在建演示项目',
    )
    const projectResponse = page.waitForResponse((response) => {
      const url = new URL(response.url())
      return (
        url.pathname === '/api/dashboard/finance' && url.searchParams.get('projectId') === projectId
      )
    })
    await projectSelect.selectOption(projectId)
    const response = await projectResponse
    expect(response.ok()).toBe(true)
    const envelope = (await response.json()) as {
      data: {
        pendingPaymentAmount: string
        approvedUnpaidAmount: string
        budgetAmount: string
        totalPaidAmount: string
        trendPoints: unknown[]
      }
    }
    expect(envelope.data.pendingPaymentAmount).toBe('90000.00')
    expect(envelope.data.approvedUnpaidAmount).toBe('120000.00')
    expect(envelope.data.budgetAmount).toBe('3900000.00')
    expect(envelope.data.totalPaidAmount).toBe('340000.00')
    expect(envelope.data.trendPoints).toHaveLength(7)

    await expect(page.getByText('资金支付趋势', { exact: true })).toBeVisible()
    await expect(page.locator('.trend-chart canvas')).toBeVisible()
    await expect(page.locator('.trend-chart tbody tr')).toHaveCount(7)
    await expect(page.getByText('资金闭环指标')).toBeVisible()
    await expect(page.getByText('PROCESSING')).toBeVisible()
  })

  test('business manager defaults to all and can switch between aggregate and specific context', async ({
    page,
  }) => {
    const login = await page.goto('/api/auth/dev-login?username=demo.business')
    expect(login?.ok()).toBe(true)

    const aggregateResponse = page.waitForResponse((response) => {
      const url = new URL(response.url())
      return (
        url.pathname === '/api/dashboard/business-manager' && !url.searchParams.has('projectId')
      )
    })
    await page.goto('/v2/dashboard?role=bm')
    expect((await aggregateResponse).ok()).toBe(true)

    const projectSelect = page.locator('#global-project')
    const periodSelect = page.locator('#global-report-period')
    await expect(projectSelect).toHaveValue('')
    await expect(projectSelect.locator('option:checked')).toHaveText('全部项目')
    await expect(periodSelect).toHaveValue('')
    await expect(periodSelect.locator('option:checked')).toHaveText('全部报告期')

    const projectId = await projectSelect.locator('option').nth(1).getAttribute('value')
    expect(projectId).toBeTruthy()
    const projectResponse = page.waitForResponse((response) => {
      const url = new URL(response.url())
      return (
        url.pathname === '/api/dashboard/business-manager' &&
        url.searchParams.get('projectId') === projectId
      )
    })
    await projectSelect.selectOption(projectId!)
    expect((await projectResponse).ok()).toBe(true)
    await expect(page).toHaveURL(new RegExp(`projectId=${projectId}`))

    const period = await periodSelect.locator('option').nth(1).getAttribute('value')
    expect(period).toBeTruthy()
    await periodSelect.selectOption(period!)
    await expect(page).toHaveURL(new RegExp(`period=${period}`))

    const restoredAggregateResponse = page.waitForResponse((response) => {
      const url = new URL(response.url())
      return (
        url.pathname === '/api/dashboard/business-manager' && !url.searchParams.has('projectId')
      )
    })
    await projectSelect.selectOption('')
    await periodSelect.selectOption('')
    expect((await restoredAggregateResponse).ok()).toBe(true)
    await expect(page).not.toHaveURL(/projectId=|period=/)
  })

  test('defaults to aggregate context and loads the real management view in three viewports', async ({
    page,
  }) => {
    const runtimeProblems: string[] = []
    const dashboardRequests: string[] = []
    const dashboardRequestUrls: string[] = []
    page.on('console', (message) => {
      if (message.type() === 'warning' || message.type() === 'error') {
        runtimeProblems.push(`${message.type()} ${message.text()}`)
      }
    })
    page.on('pageerror', (error) => runtimeProblems.push(`pageerror ${error.message}`))
    page.on('response', (response) => {
      const path = new URL(response.url()).pathname
      if (path.startsWith('/api/dashboard/')) {
        dashboardRequests.push(path)
        dashboardRequestUrls.push(response.url())
        if (response.status() >= 400) runtimeProblems.push(`${response.status()} ${path}`)
      }
    })

    const login = await page.goto('/api/auth/dev-login?username=admin')
    expect(login?.ok()).toBe(true)
    await page.setViewportSize({ width: 1440, height: 900 })
    await page.goto('/v2/dashboard?role=pm')
    await expect(page.getByText('项目经营健康度')).toBeVisible()
    await expect(page.locator('#global-project')).toHaveValue('')
    await expect(page.locator('#global-project option:checked')).toHaveText('全部项目')
    await expect(page.locator('#global-report-period')).toHaveValue('')
    await expect(page.locator('#global-report-period option:checked')).toHaveText('全部报告期')
    expect(
      dashboardRequestUrls.some((url) => {
        const requestUrl = new URL(url)
        return (
          requestUrl.pathname === '/api/dashboard/project-manager' &&
          !requestUrl.searchParams.has('projectId') &&
          !requestUrl.searchParams.has('month')
        )
      }),
    ).toBe(true)
    await expect(
      page.getByRole('navigation', { name: '驾驶舱角色视图' }).getByRole('button'),
    ).toHaveCount(roles.length)

    const managementResponse = page.waitForResponse(
      (response) => new URL(response.url()).pathname === '/api/dashboard/management',
    )
    await page.getByRole('button', { name: '管理层', exact: true }).click()
    expect((await managementResponse).ok()).toBe(true)
    await expect(page.getByText('项目经营健康度')).toBeVisible()

    for (const viewport of [
      { width: 1440, height: 900 },
      { width: 1024, height: 768 },
      { width: 390, height: 844 },
    ]) {
      await page.setViewportSize(viewport)
      await page.goto('/v2/dashboard?role=mgmt')
      await expect(page.getByText('项目经营健康度')).toBeVisible()
      expect(
        await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth),
      ).toBe(true)
      const axe = await new AxeBuilder({ page }).analyze()
      expect(
        axe.violations.filter((violation) =>
          ['serious', 'critical'].includes(violation.impact ?? ''),
        ),
      ).toEqual([])
    }

    expect(
      dashboardRequests.every((path) =>
        ['/api/dashboard/project-manager', '/api/dashboard/management'].includes(path),
      ),
    ).toBe(true)
    expect(runtimeProblems).toEqual([])
  })
})
