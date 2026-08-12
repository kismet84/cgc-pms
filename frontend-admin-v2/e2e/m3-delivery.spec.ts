import AxeBuilder from '@axe-core/playwright'
import { type Page } from '@playwright/test'
import { expect, test } from './live-test'
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

    await page.goto(`/project-schedule?projectId=${projectId}#delivery`)
    await expect(page.getByRole('main')).toContainText('项目计划与施工履约')
    await expect(page.locator('#global-project')).toBeEnabled()
    await expect(page.locator('#global-report-period')).toBeEnabled()

    await page.goto(`/site/daily-log?projectId=${projectId}#delivery`)
    await expect(page.getByRole('main')).toContainText('现场日报')
    await expect(page.locator('#global-project')).toBeEnabled()
    await expect(page.locator('#global-report-period')).toBeEnabled()
    await expect(page.locator('.daily-log-page__filters')).toHaveCount(0)
    const dailyLogStatus = page.getByRole('combobox', { name: '日报状态' })
    await expect(dailyLogStatus).toBeVisible()
    await expect(dailyLogStatus).toHaveValue('')
  })

  test('schedule detail uses a deep link and returns to the list', async ({ page }) => {
    await login(page, 'admin')
    await page.goto(`/project-schedule?projectId=${scheduleProjectId}`)

    await page.locator('.schedule-page__table .v2-table__record-link').first().click()
    await expect(page).toHaveURL(
      new RegExp(`/project-schedule/[^?]+\\?projectId=${scheduleProjectId}`),
    )
    await expect(page.getByRole('button', { name: '返回计划列表' })).toBeVisible()
    await expect(page.locator('.schedule-page__table .v2-table__record-link')).toHaveCount(0)

    await page.reload()
    await expect(page.getByRole('heading', { level: 1, name: '施工履约详情' })).toBeVisible()
    await expect(page.getByRole('button', { name: '返回计划列表' })).toBeVisible()

    await page.getByRole('button', { name: '返回计划列表' }).click()
    await expect(page).toHaveURL(`/project-schedule?projectId=${scheduleProjectId}`)
  })

  test('schedule loads all accessible projects when the shell selects all projects', async ({
    page,
  }) => {
    await login(page, 'admin')
    await page.goto(`/project-schedule?projectId=${scheduleProjectId}`)

    const projectControl = page.locator('#global-project')
    const allProjectsResponse = page.waitForResponse((response) => {
      const url = new URL(response.url())
      return url.pathname === '/api/project-schedules' && !url.searchParams.has('projectId')
    })
    await projectControl.selectOption('')

    expect((await allProjectsResponse).ok()).toBe(true)
    await expect(page).toHaveURL('/project-schedule')
    await expect(page.getByRole('columnheader', { name: '项目' })).toBeVisible()
    await expect(page.locator('.schedule-page__table .v2-table__record-link').first()).toBeVisible()
  })

  test('unavailable schedule detail keeps a return path', async ({ page }) => {
    await login(page, 'admin')
    await page.goto(`/project-schedule/not-found?projectId=${scheduleProjectId}`)

    await expect(page.getByRole('heading', { name: '计划详情不可用' })).toBeVisible()
    runtimeErrors.set(
      page,
      (runtimeErrors.get(page) ?? []).filter(
        (error) => !error.includes('/api/project-schedules/not-found'),
      ),
    )
    await page.getByRole('button', { name: '返回计划列表' }).click()
    await expect(page).toHaveURL(`/project-schedule?projectId=${scheduleProjectId}`)
  })

  test('daily-log report period reaches the server as calendar-month bounds', async ({ page }) => {
    await login(page, 'admin')
    const projectId = await firstProjectId(page)
    await page.goto(`/site/daily-log?projectId=${projectId}`)
    const periodControl = page.locator('#global-report-period')
    const option = periodControl.locator('option:not([value=""])').first()
    const period = await option.getAttribute('value')
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
    await periodControl.selectOption(period!)
    expect((await filtered).ok()).toBe(true)

    const statusControl = page.getByRole('combobox', { name: '日报状态' })
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
    await statusControl.selectOption({ label: '草稿' })
    expect((await statusFiltered).ok()).toBe(true)
    await expect(page).toHaveURL(/projectId=.*period=\d{4}-\d{2}.*status=DRAFT/)
    expect(new URL(page.url()).searchParams.has('startDate')).toBe(false)
    expect(new URL(page.url()).searchParams.has('endDate')).toBe(false)

    const clearStatus = page.waitForResponse((response) => {
      const url = new URL(response.url())
      return url.pathname === '/api/site-daily-logs' && !url.searchParams.has('status')
    })
    await statusControl.selectOption({ label: '全部状态' })
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
    await page.goto('/site/daily-log?startDate=2025-05-01&endDate=2025-05-31')
    expect((await unfiltered).ok()).toBe(true)
    await expect(page).toHaveURL('/site/daily-log')
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
      await page.goto(`/project-schedule?projectId=${projectId}`)
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

      await page.goto(`/site/daily-log?projectId=${projectId}`)
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

  test('technical and closeout section headings keep the shared computed typography', async ({
    page,
  }) => {
    await login(page, 'admin')

    for (const [route, selector] of [
      ['/technical-management?tab=drawing', '.technical-page__record-sections h3'],
      ['/project-closeout', '.closeout-page__record-sections h3'],
    ]) {
      const separator = route.includes('?') ? '&' : '?'
      await page.goto(`${route}${separator}projectId=${scheduleProjectId}`)
      const headings = page.locator(selector)
      await expect(headings.first()).toBeVisible()
      const typography = await headings.evaluateAll((nodes) =>
        nodes.map((node) => {
          const style = getComputedStyle(node)
          return [style.fontSize, style.fontWeight, style.lineHeight]
        }),
      )
      expect(typography.every((value) => value.join('|') === typography[0]?.join('|'))).toBe(true)
      expect(typography[0]?.[1]).toBe('600')
    }
  })

  test('quality, technical and closeout routes remain usable at three viewports', async ({
    page,
  }) => {
    test.setTimeout(60_000)
    await login(page, 'admin')
    const routes = [
      {
        path: '/quality-safety',
        root: '.quality-page',
        heading: '质量安全整改闭环',
        dialog: '闭环追溯',
      },
      {
        path: '/technical-management',
        root: '.technical-page',
        heading: '图纸 RFI 技术闭环',
        dialog: '图纸闭环追溯',
      },
      {
        path: '/project-closeout',
        root: '.closeout-page',
        heading: '竣工收尾闭环',
        dialog: '收尾追溯',
      },
    ]

    for (const viewport of [
      { width: 1440, height: 900 },
      { width: 1024, height: 768 },
      { width: 390, height: 844 },
    ]) {
      await page.setViewportSize(viewport)
      for (const route of routes) {
        await page.goto(`${route.path}?projectId=${scheduleProjectId}`)
        await expect(page.locator(route.root)).toBeVisible()
        await expect(page.getByRole('heading', { level: 1, name: route.heading })).toBeAttached()
        expect(
          await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth),
        ).toBe(true)
        expect(
          await page.locator('#shell-main-content').evaluate((main) => {
            const canFit = main.scrollHeight <= main.clientHeight
            if (!canFit) main.scrollTop = Math.min(240, main.scrollHeight - main.clientHeight)
            return canFit || main.scrollTop > 0
          }),
        ).toBe(true)
        const axe = await new AxeBuilder({ page }).include(route.root).analyze()
        expect(
          axe.violations.filter((item) => ['serious', 'critical'].includes(item.impact ?? '')),
        ).toEqual([])
      }
    }

    await page.setViewportSize({ width: 1440, height: 900 })
    for (const route of routes) {
      await page.goto(`${route.path}?projectId=${scheduleProjectId}`)
      if (route.path === '/project-closeout') {
        await page.getByRole('button', { name: '追溯', exact: true }).click()
      } else {
        await page
          .getByRole('tab', {
            name: route.path === '/quality-safety' ? /问题整改/ : /图纸管理/,
          })
          .click()
        await page.locator(`${route.root} .v2-table__record-link`).first().click()
      }
      await expect(page.getByRole('dialog', { name: route.dialog })).toBeVisible()
      await page.keyboard.press('Escape')
      await expect(page.getByRole('dialog', { name: route.dialog })).toHaveCount(0)
    }
  })

  test('tab URLs survive reload and browser history navigation', async ({ page }) => {
    await login(page, 'admin')
    for (const flow of [
      {
        path: '/technical-management',
        initial: /技术方案/,
        next: /图纸管理/,
        initialTab: 'scheme',
        nextTab: 'drawing',
      },
      {
        path: '/quality-safety',
        initial: /检查计划/,
        next: /问题整改/,
        initialTab: 'plan',
        nextTab: 'rectification',
      },
    ]) {
      await page.goto(`${flow.path}?tab=${flow.initialTab}&projectId=${scheduleProjectId}`)
      await page.getByRole('tab', { name: flow.next }).click()
      await expect(page).toHaveURL(new RegExp(`tab=${flow.nextTab}`))
      await page.reload()
      await expect(page.getByRole('tab', { name: flow.next })).toHaveAttribute(
        'aria-selected',
        'true',
      )
      await page.goBack()
      await expect(page.getByRole('tab', { name: flow.initial })).toHaveAttribute(
        'aria-selected',
        'true',
      )
      await page.goForward()
      await expect(page.getByRole('tab', { name: flow.next })).toHaveAttribute(
        'aria-selected',
        'true',
      )
    }
  })

  test('real split roles expose only authorized delivery actions', async ({ page }) => {
    const mutatingRequests: string[] = []
    page.on('request', (request) => {
      if (!['GET', 'HEAD'].includes(request.method())) mutatingRequests.push(request.url())
    })

    await login(page, splitRoleUser)
    await page.goto(`/project-schedule?projectId=${controlledProjectId}`)
    await expect(page.getByRole('heading', { level: 1, name: '项目计划与施工履约' })).toBeVisible()
    await expect(page.getByRole('button', { name: '新建基线计划' })).toHaveCount(0)
    await expect(page.getByRole('button', { name: '维护 WBS' })).toHaveCount(0)
    expect(
      (await page.request.get('/api/project-schedules?projectId=520000000000000001')).status(),
    ).toBe(403)
    expect(mutatingRequests).toEqual([])

    await login(page, 'ui26.staff01')
    await page.goto(`/site/daily-log?projectId=${controlledProjectId}`)
    await expect(page.getByRole('heading', { level: 1, name: '现场日报' })).toBeVisible()
    await expect(page.getByRole('button', { name: '新建日报' })).toHaveCount(0)

    const employeeLogin = await page.goto('/api/auth/dev-login?username=ui26.staff01')
    expect(employeeLogin?.ok()).toBe(true)
    const employeePayload = (await employeeLogin?.json()) as {
      data?: { userInfo?: { permissions?: string[] } }
    }
    const employeePermissions = employeePayload.data?.userInfo?.permissions ?? []
    expect(employeePermissions).toContain('site:daily:self')
    expect(employeePermissions).not.toContain('site:daily:edit')
    await page.goto(`/site/daily-log?projectId=${controlledProjectId}`)
    await expect(page.getByRole('heading', { level: 1, name: '现场日报' })).toBeVisible()
    await expect(page.getByRole('button', { name: '新建日报' })).toBeVisible()
    expect(mutatingRequests).toEqual([])
  })
})
