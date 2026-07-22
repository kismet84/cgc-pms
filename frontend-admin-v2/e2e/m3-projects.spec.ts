import AxeBuilder from '@axe-core/playwright'
import { expect, test, type Page } from '@playwright/test'
import { captureRuntimeErrors } from './runtime-errors'

const runLiveProjects = process.env.V2_LIVE_PROJECTS === '1'
const runtimeErrors = new WeakMap<Page, string[]>()

async function consumeExpectedHttpError(page: Page, status: string) {
  await expect.poll(() => runtimeErrors.get(page) ?? []).toEqual([expect.stringContaining(status)])
  runtimeErrors.get(page)?.splice(0)
}

async function login(page: Page, username: string) {
  expect((await page.goto(`/api/auth/dev-login?username=${username}`))?.ok()).toBe(true)
}

async function rewritePermissions(page: Page, permissions: string[]) {
  await page.route('**/api/auth/userinfo', async (route) => {
    const response = await route.fetch()
    const envelope = (await response.json()) as { data: Record<string, unknown> }
    await route.fulfill({
      response,
      json: { ...envelope, data: { ...envelope.data, permissions } },
    })
  })
}

test.describe('M3 live project object', () => {
  test.skip(!runLiveProjects, 'Set V2_LIVE_PROJECTS=1 only against local test/demo runtime')
  test.beforeEach(({ page }) => runtimeErrors.set(page, captureRuntimeErrors(page)))
  test.afterEach(({ page }) => expect(runtimeErrors.get(page) ?? []).toEqual([]))

  test('five accepted routes resolve without placeholder and preserve legacy redirect context', async ({
    page,
  }) => {
    await login(page, 'admin')
    const pendingList = page.waitForResponse(
      (response) => new URL(response.url()).pathname === '/api/projects',
    )
    await page.goto('/v2/project?keyword=演示#ledger')
    await expect(page).toHaveURL(/\/v2\/project\/list\?keyword=.*#ledger$/)
    const listResponse = await pendingList
    expect(listResponse.ok()).toBe(true)
    const body = (await listResponse.json()) as { data: { records: Array<{ id: string }> } }
    const projectId = body.data.records[0]?.id
    expect(projectId).toBeTruthy()
    for (const path of [
      `/v2/project/${projectId}/overview`,
      `/v2/project/${projectId}/members`,
      `/v2/project/${projectId}/edit`,
    ]) {
      expect((await page.goto(path))?.ok()).toBe(true)
      await expect(page.locator('.shell-placeholder')).toHaveCount(0)
      await expect(page.getByRole('main')).toBeVisible()
    }
  })

  test('shell project context filters the ledger while object routes disable both controls', async ({
    page,
  }) => {
    await login(page, 'admin')
    const projectId = await page.request
      .get('/api/projects?pageNo=1&pageSize=1')
      .then(async (response) => {
        expect(response.ok()).toBe(true)
        const body = (await response.json()) as { data: { records: Array<{ id: string }> } }
        return body.data.records[0]!.id
      })

    await page.goto('/v2/project/list')
    await expect(page.locator('#global-project')).toHaveAttribute('aria-disabled', 'false')
    await expect(page.locator('#global-report-period')).toHaveAttribute('aria-disabled', 'true')
    const detailResponse = page.waitForResponse(
      (response) => new URL(response.url()).pathname === `/api/projects/${projectId}`,
    )
    await page.locator('#global-project').click()
    await page
      .locator('#global-project')
      .locator('..')
      .locator(`[role="option"][data-value="${projectId}"]`)
      .click()
    expect((await detailResponse).ok()).toBe(true)
    await expect(page.locator('.project-page__grid > .v2-card')).toHaveCount(1)

    await page.goto(`/v2/project/${projectId}/overview`)
    await expect(page.locator('.app-shell__object-context')).toHaveCount(0)
    await expect(page.getByText(`对象 project / ${projectId}`)).toHaveCount(0)
    await expect(page.locator('#global-project')).toHaveAttribute('aria-disabled', 'true')
    await expect(page.locator('#global-report-period')).toHaveAttribute('aria-disabled', 'true')
    await expect(page.getByText('当前项目（当前页面不适用）')).toHaveCount(0)
  })

  test('admin project ledger has no serious accessibility issue or horizontal overflow in three viewports', async ({
    page,
  }) => {
    await login(page, 'admin')
    for (const viewport of [
      { width: 1440, height: 900 },
      { width: 1024, height: 768 },
      { width: 390, height: 844 },
    ]) {
      await page.setViewportSize(viewport)
      await page.goto('/v2/project/list?keyword=演示#ledger')
      await expect(page.getByRole('heading', { level: 1, name: '项目台账' })).toBeAttached()
      expect(
        await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth),
      ).toBe(true)
      const axe = await new AxeBuilder({ page }).include('.project-page').analyze()
      expect(
        axe.violations.filter((item) => ['serious', 'critical'].includes(item.impact ?? '')),
      ).toEqual([])
    }
  })

  test('real query-only identity cannot see writes or member route', async ({ page }) => {
    await login(page, 'demo.business')
    await page.goto('/v2/project/list')
    await expect(page.getByRole('button', { name: '新建项目' })).toHaveCount(0)
    await expect(page.getByRole('button', { name: /删除|归档|提交/ })).toHaveCount(0)
    await page.goto('/v2/project/520000000000009002/members')
    await expect(page).toHaveURL(/\/v2\/forbidden/)
  })

  test('real member-readonly identity can read but cannot mutate members', async ({ page }) => {
    await login(page, 'demo.member-readonly')
    await page.goto('/v2/project/520000000000009002/members')
    await expect(page.getByRole('heading', { name: '项目成员' })).toBeVisible()
    await expect(page.getByRole('button', { name: /添加成员|编辑|移除/ })).toHaveCount(0)
    const denied = await page.evaluate(async () => {
      const csrf = document.cookie
        .split(';')
        .map((item) => item.trim())
        .find((item) => item.startsWith('XSRF-TOKEN='))
        ?.split('=')[1]
      const response = await fetch('/api/projects/520000000000009002/members', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'X-XSRF-TOKEN': decodeURIComponent(csrf ?? ''),
        },
        body: JSON.stringify({
          tenantId: '0',
          projectId: '520000000000009002',
          userId: '1',
          roleCode: 'OTH',
        }),
      })
      return { status: response.status, body: await response.json() }
    })
    expect(denied).toMatchObject({ status: 403, body: { code: 'AUTH_FORBIDDEN' } })
    await consumeExpectedHttpError(page, '403 (Forbidden)')
  })

  test('anonymous and permissionless deep links fail closed', async ({ browser }) => {
    const anonymous = await browser.newPage()
    await anonymous.goto('/v2/project/520000000000009002/overview')
    await expect(anonymous).toHaveURL(/\/v2\/login\?redirect=/)
    await anonymous.close()

    const denied = await browser.newPage()
    await login(denied, 'demo.business')
    await rewritePermissions(denied, [])
    await denied.goto('/v2/project/list')
    await expect(denied).toHaveURL(/\/v2\/forbidden/)
    await denied.close()
  })

  test('list recovers after one controlled server failure', async ({ page }) => {
    await login(page, 'admin')
    let failed = false
    await page.route('**/api/projects?*', async (route) => {
      if (!failed) {
        failed = true
        await route.fulfill({
          status: 503,
          contentType: 'application/json',
          body: JSON.stringify({ code: 'TEST_FAILURE', message: '受控故障', data: null }),
        })
      } else await route.continue()
    })
    await page.goto('/v2/project/list')
    await expect(page.getByText('受控故障')).toBeVisible()
    await page.getByRole('button', { name: '刷新' }).click()
    await expect(page.locator('.project-page__grid .v2-card').first()).toBeVisible()
    await consumeExpectedHttpError(page, '503 (Service Unavailable)')
  })

  test('rapid query changes keep only the newest response', async ({ page }) => {
    await login(page, 'admin')
    await page.goto('/v2/project/list')
    await page.route('**/api/projects?*', async (route) => {
      const keyword = new URL(route.request().url()).searchParams.get('keyword')
      if (!['slow', 'fast'].includes(keyword ?? '')) return route.continue()
      if (keyword === 'slow') await new Promise((resolve) => setTimeout(resolve, 500))
      const name = keyword === 'slow' ? '旧响应项目' : '新响应项目'
      await route.fulfill({
        contentType: 'application/json',
        body: JSON.stringify({
          code: '0',
          message: 'success',
          data: {
            total: 1,
            records: [
              {
                id: keyword,
                tenantId: '1',
                orgId: '1',
                projectCode: keyword,
                projectName: name,
                projectType: 'CONSTRUCTION',
                projectAddress: '',
                ownerUnit: '',
                supervisorUnit: '',
                designUnit: '',
                contractAmount: '1.00',
                targetCost: '1.00',
                plannedStartDate: '',
                plannedEndDate: '',
                projectManagerId: '1',
                status: 'ACTIVE',
                approvalStatus: 'DRAFT',
                createdBy: '1',
                createdAt: '',
                updatedAt: '',
              },
            ],
          },
        }),
      })
    })
    const keyword = page.getByLabel('关键词')
    await keyword.fill('slow')
    await page.getByRole('button', { name: '查询' }).click()
    await keyword.fill('fast')
    await page.getByRole('button', { name: '查询' }).click()
    await expect(page.getByText('新响应项目')).toBeVisible()
    await expect(page.getByText('旧响应项目')).toHaveCount(0)
  })

  test('write conflict is shown and followed by authoritative reread', async ({ page }) => {
    await login(page, 'admin')
    await page.goto('/v2/project/list')
    let rereads = 0
    page.on('response', (response) => {
      if (
        response.request().method() === 'GET' &&
        new URL(response.url()).pathname === '/api/projects'
      )
        rereads += 1
    })
    await page.route('**/api/projects', (route) =>
      route.request().method() === 'POST'
        ? route.fulfill({
            status: 409,
            contentType: 'application/json',
            body: JSON.stringify({ code: 'PROJECT_CONFLICT', message: '受控冲突', data: null }),
          })
        : route.continue(),
    )
    await page.getByRole('button', { name: '新建项目' }).click()
    const dialog = page.getByRole('dialog', { name: '新建项目' })
    await dialog.getByLabel('项目名称').fill('冲突项目')
    await dialog.getByRole('button', { name: /^项目类型：/ }).click()
    await dialog.locator('[role="option"]:not(:disabled)').first().click()
    const before = rereads
    await dialog.getByRole('button', { name: '创建并重读' }).click()
    await expect(page.locator('.project-page > .v2-alert').getByText('受控冲突')).toBeAttached()
    await expect.poll(() => rereads).toBeGreaterThan(before)
    await consumeExpectedHttpError(page, '409 (Conflict)')
  })

  test('SUPER_ADMIN creates then deletes a controlled demo project', async ({ page }) => {
    await login(page, 'admin')
    const name = `M3验收项目-${Date.now()}`
    await page.goto('/v2/project/list')
    await page.getByRole('button', { name: '新建项目' }).click()
    const dialog = page.getByRole('dialog', { name: '新建项目' })
    await dialog.getByLabel('项目名称').fill(name)
    await dialog.getByRole('button', { name: /^项目类型：/ }).click()
    await dialog.locator('[role="option"]:not(:disabled)').first().click()
    await dialog.getByRole('button', { name: '创建并重读' }).click()
    const card = page.locator('.v2-card', { hasText: name })
    await expect(card).toBeVisible()
    await card.getByRole('button', { name: '删除' }).click()
    const confirmDialog = page.getByRole('dialog', { name: '删除项目' })
    await expect(confirmDialog).toBeVisible()
    await confirmDialog.getByRole('button', { name: '取消' }).click()
    await expect(confirmDialog).toBeHidden()
    await expect(card).toBeVisible()

    await card.getByRole('button', { name: '删除' }).click()
    await confirmDialog.getByRole('button', { name: '永久删除' }).click()
    await expect(card).toHaveCount(0)
  })
})
