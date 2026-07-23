import AxeBuilder from '@axe-core/playwright'
import { expect, test, type Page } from '@playwright/test'
import { captureRuntimeErrors } from './runtime-errors'

const runLiveDelivery = process.env.V2_LIVE_DELIVERY === '1'
const splitRoleUser = process.env.V2_SCHEDULE_READONLY_USER || 'demo.schedule.query'
const controlledProjectId = process.env.V2_DELIVERY_PROJECT_ID || '520000000000009002'
const scheduleProjectId = process.env.V2_SCHEDULE_PROJECT_ID || '520000000000000001'
const runtimeErrors = new WeakMap<Page, string[]>()

async function login(page: Page, username: string) {
  expect((await page.goto(`/api/auth/dev-login?username=${username}`))?.ok()).toBe(true)
}

async function firstProjectId(page: Page): Promise<string> {
  const response = await page.request.get('/api/projects?pageNo=1&pageSize=1')
  expect(response.ok()).toBe(true)
  const body = (await response.json()) as { data: { records: Array<{ id: string }> } }
  const projectId = body.data.records[0]?.id
  expect(projectId).toBeTruthy()
  return projectId
}

test.describe('M3 live delivery workspace', () => {
  test.skip(!runLiveDelivery, 'Set V2_LIVE_DELIVERY=1 only against local test/demo runtime')
  test.beforeEach(({ page }) => runtimeErrors.set(page, captureRuntimeErrors(page)))
  test.afterEach(({ page }) => expect(runtimeErrors.get(page) ?? []).toEqual([]))

  test('schedule and daily-log routes resolve to real V2 pages', async ({ page }) => {
    await login(page, 'admin')
    const projectId = await firstProjectId(page)

    await page.goto(`/v2/project-schedule?projectId=${projectId}#delivery`)
    await expect(page.locator('.shell-placeholder')).toHaveCount(0)
    await expect(page.getByRole('main')).toContainText('项目计划与施工履约')
    await expect(page.locator('#global-project')).toHaveAttribute('aria-disabled', 'false')
    await expect(page.locator('#global-report-period')).toHaveAttribute('aria-disabled', 'true')

    await page.goto(`/v2/site/daily-log?projectId=${projectId}#delivery`)
    await expect(page.locator('.shell-placeholder')).toHaveCount(0)
    await expect(page.getByRole('main')).toContainText('现场日报')
    await expect(page.locator('#global-project')).toHaveAttribute('aria-disabled', 'false')
    await expect(page.locator('#global-report-period')).toHaveAttribute('aria-disabled', 'false')
    await expect(page.locator('.daily-log-page__filters')).toHaveCount(0)
    await expect(
      page.locator('.daily-log-page__toolbar').getByRole('button', {
        name: '日报状态：全部状态',
      }),
    ).toBeVisible()
  })

  test('schedule detail uses a deep link and returns to the list', async ({ page }) => {
    await login(page, 'admin')
    await page.goto(`/v2/project-schedule?projectId=${scheduleProjectId}`)

    await page.getByRole('button', { name: '履约详情' }).first().click()
    await expect(page).toHaveURL(
      new RegExp(`/v2/project-schedule/[^?]+\\?projectId=${scheduleProjectId}`),
    )
    await expect(page.getByRole('button', { name: '返回计划列表' })).toBeVisible()
    await expect(page.getByRole('button', { name: '履约详情' })).toHaveCount(0)

    await page.reload()
    await expect(page.getByRole('heading', { level: 1, name: '施工履约详情' })).toBeVisible()
    await expect(page.getByRole('button', { name: '返回计划列表' })).toBeVisible()

    await page.getByRole('button', { name: '返回计划列表' }).click()
    await expect(page).toHaveURL(`/v2/project-schedule?projectId=${scheduleProjectId}`)
  })

  test('schedule loads all accessible projects when the shell selects all projects', async ({
    page,
  }) => {
    await login(page, 'admin')
    await page.goto(`/v2/project-schedule?projectId=${scheduleProjectId}`)

    const projectControl = page.locator('#global-project')
    await projectControl.click()
    const allProjectsResponse = page.waitForResponse((response) => {
      const url = new URL(response.url())
      return url.pathname === '/api/project-schedules' && !url.searchParams.has('projectId')
    })
    await projectControl.locator('..').locator('[role="option"][data-value=""]').click()

    expect((await allProjectsResponse).ok()).toBe(true)
    await expect(page).toHaveURL('/v2/project-schedule')
    await expect(page.getByText('当前范围：全部项目')).toBeVisible()
    await expect(page.getByRole('button', { name: '履约详情' }).first()).toBeVisible()
  })

  test('unavailable schedule detail keeps a return path', async ({ page }) => {
    await login(page, 'admin')
    await page.goto(`/v2/project-schedule/not-found?projectId=${scheduleProjectId}`)

    await expect(page.getByRole('heading', { name: '计划详情不可用' })).toBeVisible()
    runtimeErrors.set(
      page,
      (runtimeErrors.get(page) ?? []).filter(
        (error) => !error.includes('/api/project-schedules/not-found'),
      ),
    )
    await page.getByRole('button', { name: '返回计划列表' }).click()
    await expect(page).toHaveURL(`/v2/project-schedule?projectId=${scheduleProjectId}`)
  })

  test('daily-log report period reaches the server as calendar-month bounds', async ({ page }) => {
    await login(page, 'admin')
    const projectId = await firstProjectId(page)
    await page.goto(`/v2/site/daily-log?projectId=${projectId}`)
    const periodControl = page.locator('#global-report-period')
    await periodControl.click()
    const option = periodControl
      .locator('..')
      .locator('[role="option"][data-value]:not([data-value=""])')
      .first()
    const period = await option.getAttribute('data-value')
    expect(period).toMatch(/^\d{4}-\d{2}$/)
    const [, year, month] = /^(\d{4})-(\d{2})$/.exec(period!)!
    const lastDay = new Date(Date.UTC(Number(year), Number(month), 0)).getUTCDate()
    const filtered = page.waitForResponse((response) => {
      const url = new URL(response.url())
      return (
        url.pathname === '/api/site-daily-logs' &&
        url.searchParams.get('startDate') === `${period}-01` &&
        url.searchParams.get('endDate') === `${period}-${String(lastDay).padStart(2, '0')}`
      )
    })
    await option.click()
    expect((await filtered).ok()).toBe(true)

    const statusControl = page.getByRole('button', { name: '日报状态：全部状态' })
    await statusControl.click()
    const statusFiltered = page.waitForResponse((response) => {
      const url = new URL(response.url())
      return (
        url.pathname === '/api/site-daily-logs' &&
        url.searchParams.get('projectId') === projectId &&
        url.searchParams.get('startDate') === `${period}-01` &&
        url.searchParams.get('endDate') === `${period}-${String(lastDay).padStart(2, '0')}` &&
        url.searchParams.get('status') === 'DRAFT'
      )
    })
    await page.getByRole('option', { name: '草稿', exact: true }).click()
    expect((await statusFiltered).ok()).toBe(true)
    await expect(page).toHaveURL(/projectId=.*period=\d{4}-\d{2}.*status=DRAFT/)
    expect(new URL(page.url()).searchParams.has('startDate')).toBe(false)
    expect(new URL(page.url()).searchParams.has('endDate')).toBe(false)

    const clearStatus = page.waitForResponse((response) => {
      const url = new URL(response.url())
      return url.pathname === '/api/site-daily-logs' && !url.searchParams.has('status')
    })
    await page.getByRole('button', { name: '日报状态：草稿' }).click()
    await page.getByRole('option', { name: '全部状态', exact: true }).click()
    expect((await clearStatus).ok()).toBe(true)
    expect(new URL(page.url()).searchParams.has('status')).toBe(false)
  })

  test('daily-log removes legacy hidden date filters in favor of shell period', async ({
    page,
  }) => {
    await login(page, 'admin')
    const unfiltered = page.waitForResponse((response) => {
      const url = new URL(response.url())
      return (
        url.pathname === '/api/site-daily-logs' &&
        !url.searchParams.has('startDate') &&
        !url.searchParams.has('endDate')
      )
    })
    await page.goto('/v2/site/daily-log?startDate=2025-05-01&endDate=2025-05-31')
    expect((await unfiltered).ok()).toBe(true)
    await expect(page).toHaveURL('/v2/site/daily-log')
  })

  test('delivery routes keep layout stable in three viewports with no serious accessibility issue', async ({
    page,
  }) => {
    await login(page, 'admin')
    const projectId = await firstProjectId(page)

    for (const viewport of [
      { width: 1440, height: 900 },
      { width: 1024, height: 768 },
      { width: 390, height: 844 },
    ]) {
      await page.setViewportSize(viewport)
      await page.goto(`/v2/project-schedule?projectId=${projectId}`)
      await expect(
        page.getByRole('heading', { level: 1, name: '项目计划与施工履约' }),
      ).toBeVisible()
      expect(
        await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth),
      ).toBe(true)
      const scheduleAxe = await new AxeBuilder({ page }).include('.schedule-page').analyze()
      expect(
        scheduleAxe.violations.filter((item) =>
          ['serious', 'critical'].includes(item.impact ?? ''),
        ),
      ).toEqual([])

      await page.goto(`/v2/site/daily-log?projectId=${projectId}`)
      await expect(page.getByRole('heading', { level: 1, name: '现场日报' })).toBeVisible()
      expect(
        await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth),
      ).toBe(true)
      const dailyAxe = await new AxeBuilder({ page }).include('.daily-log-page').analyze()
      expect(
        dailyAxe.violations.filter((item) => ['serious', 'critical'].includes(item.impact ?? '')),
      ).toEqual([])
    }
  })

  test('real split roles expose only authorized delivery actions', async ({ page }) => {
    const mutatingRequests: string[] = []
    page.on('request', (request) => {
      if (!['GET', 'HEAD'].includes(request.method())) mutatingRequests.push(request.url())
    })

    await login(page, splitRoleUser)
    await page.goto(`/v2/project-schedule?projectId=${controlledProjectId}`)
    await expect(page.getByRole('heading', { level: 1, name: '项目计划与施工履约' })).toBeVisible()
    await expect(page.getByRole('button', { name: '新建基线计划' })).toHaveCount(0)
    await expect(page.getByRole('button', { name: '维护 WBS' })).toHaveCount(0)
    expect(
      (await page.request.get('/api/project-schedules?projectId=520000000000000001')).status(),
    ).toBe(403)
    expect(mutatingRequests).toEqual([])

    await login(page, 'demo.production')
    await page.goto(`/v2/site/daily-log?projectId=${controlledProjectId}`)
    await expect(page.getByRole('heading', { level: 1, name: '现场日报' })).toBeVisible()
    await expect(page.getByRole('button', { name: '新建日报' })).toBeVisible()
  })
})
