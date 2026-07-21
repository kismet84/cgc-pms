import AxeBuilder from '@axe-core/playwright'
import { expect, test, type Page } from '@playwright/test'

const runLiveDelivery = process.env.V2_LIVE_DELIVERY === '1'
const splitRoleUser = process.env.V2_SCHEDULE_READONLY_USER
const controlledProjectId = process.env.V2_DELIVERY_PROJECT_ID

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
        page.getByRole('heading', { level: 2, name: '项目计划与施工履约' }),
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
      await expect(page.getByRole('heading', { level: 2, name: '现场日报' })).toBeVisible()
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
    test.skip(
      !splitRoleUser || !controlledProjectId,
      'Set V2_SCHEDULE_READONLY_USER and V2_DELIVERY_PROJECT_ID for split-role acceptance',
    )
    if (!splitRoleUser || !controlledProjectId) return
    const mutatingRequests: string[] = []
    page.on('request', (request) => {
      if (!['GET', 'HEAD'].includes(request.method())) mutatingRequests.push(request.url())
    })

    await login(page, splitRoleUser)
    await page.goto(`/v2/project-schedule?projectId=${controlledProjectId}`)
    await expect(page.getByRole('heading', { level: 2, name: '项目计划与施工履约' })).toBeVisible()
    await expect(page.getByRole('button', { name: '新建基线计划' })).toHaveCount(0)
    await expect(page.getByRole('button', { name: '维护 WBS' })).toHaveCount(0)
    expect(mutatingRequests).toEqual([])

    await login(page, 'demo.production')
    await page.goto(`/v2/site/daily-log?projectId=${controlledProjectId}`)
    await expect(page.getByRole('heading', { level: 2, name: '现场日报' })).toBeVisible()
    await expect(page.getByRole('button', { name: '新建日报' })).toBeVisible()
  })
})
