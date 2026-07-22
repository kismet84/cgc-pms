import AxeBuilder from '@axe-core/playwright'
import { expect, test, type Page } from '@playwright/test'
import { captureRuntimeErrors } from './runtime-errors'

const runLiveQuality = process.env.V2_LIVE_QUALITY === '1'
const runtimeErrors = new WeakMap<Page, string[]>()
const controlledProjectId = process.env.V2_QUALITY_PROJECT_ID || '520000000000000001'

async function login(page: Page, username: string) {
  expect((await page.goto(`/api/auth/dev-login?username=${username}`))?.ok()).toBe(true)
}

async function openQuality(page: Page) {
  await page.goto(`/v2/quality-safety?projectId=${controlledProjectId}`)
  await expect(page.getByRole('region', { name: '质量安全整改闭环' })).toBeVisible()
  await expect(page.locator('.shell-placeholder')).toHaveCount(0)
  await expect(page.locator('.v2-page-state--loading')).toHaveCount(0)
}

test.describe('M3 live quality and safety workspace', () => {
  test.skip(!runLiveQuality, 'Set V2_LIVE_QUALITY=1 only against local test/demo runtime')
  test.beforeEach(({ page }) => runtimeErrors.set(page, captureRuntimeErrors(page)))
  test.afterEach(({ page }) => expect(runtimeErrors.get(page) ?? []).toEqual([]))

  test('real page remains stable and accessible in three viewports', async ({ page }) => {
    await login(page, 'admin')
    for (const viewport of [
      { width: 1440, height: 900 },
      { width: 1024, height: 768 },
      { width: 390, height: 844 },
    ]) {
      await page.setViewportSize(viewport)
      await openQuality(page)
      expect(
        await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth),
      ).toBe(true)
      const result = await new AxeBuilder({ page }).include('.quality-page').analyze()
      expect(
        result.violations.filter((item) => ['serious', 'critical'].includes(item.impact ?? '')),
      ).toEqual([])
    }
  })

  test('query-only account exposes no write action and sends no mutation', async ({ page }) => {
    const mutatingRequests: string[] = []
    page.on('request', (request) => {
      if (!['GET', 'HEAD'].includes(request.method())) mutatingRequests.push(request.url())
    })
    await login(page, 'demo.qs.query')
    await openQuality(page)
    for (const action of ['新建检查计划', '新建检查', '提交整改', '复检', '登记后果']) {
      await expect(page.getByRole('button', { name: action, exact: true })).toHaveCount(0)
    }
    expect(mutatingRequests).toEqual([])
  })

  for (const [username, action] of [
    ['demo.qs.plan', '新建检查计划'],
    ['demo.qs.inspection', '新建检查'],
    ['demo.qs.rectify', '提交整改'],
    ['demo.qs.reinspect', '复检'],
    ['demo.qs.consequence', '登记后果'],
  ] as const) {
    test(`${username} exposes its authorized action`, async ({ page }) => {
      await login(page, username)
      await openQuality(page)
      await expect(page.getByRole('button', { name: action, exact: true }).first()).toBeVisible()
    })
  }
})
