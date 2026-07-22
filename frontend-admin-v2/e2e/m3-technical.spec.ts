import AxeBuilder from '@axe-core/playwright'
import { expect, test, type Page } from '@playwright/test'
import { captureRuntimeErrors } from './runtime-errors'

const runLiveTechnical = process.env.V2_LIVE_TECHNICAL === '1'
const runtimeErrors = new WeakMap<Page, string[]>()
const controlledProjectId = process.env.V2_TECHNICAL_PROJECT_ID || '520000000000000001'

async function login(page: Page, username: string) {
  expect((await page.goto(`/api/auth/dev-login?username=${username}`))?.ok()).toBe(true)
}

async function openTechnical(page: Page) {
  await page.goto(`/v2/technical-management?projectId=${controlledProjectId}`)
  await expect(page.getByRole('heading', { level: 1, name: '图纸 RFI 技术闭环' })).toBeVisible()
  await expect(page.locator('.shell-placeholder')).toHaveCount(0)
  await expect(page.locator('.v2-page-state--loading')).toHaveCount(0)
  await expect(page.getByRole('alert', { name: '页面暂时无法显示' })).toHaveCount(0)
}

test.describe('M3 live technical management workspace', () => {
  test.skip(!runLiveTechnical, 'Set V2_LIVE_TECHNICAL=1 only against local test/demo runtime')
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
      await openTechnical(page)
      expect(
        await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth),
      ).toBe(true)
      const result = await new AxeBuilder({ page }).include('.technical-page').analyze()
      expect(
        result.violations.filter((item) => ['serious', 'critical'].includes(item.impact ?? '')),
      ).toEqual([])
    }
  })

  test('query-only account exposes no write action and sends no mutation', async ({ page }) => {
    const mutations: string[] = []
    page.on('request', (request) => {
      if (!['GET', 'HEAD'].includes(request.method())) mutations.push(request.url())
    })
    await login(page, 'demo.tech.query')
    await openTechnical(page)
    for (const action of [
      '新建方案',
      '提交方案',
      '接收图纸',
      '登记会审',
      '发起 RFI',
      '设计回复',
      '接受/驳回',
      '登记交底',
      '确认归档',
    ])
      await expect(page.getByRole('button', { name: action, exact: true })).toHaveCount(0)
    expect(mutations).toEqual([])
  })

  for (const [username, action] of [
    ['demo.tech.scheme-maintain', '新建方案'],
    ['demo.tech.scheme-submit', '提交方案'],
    ['demo.tech.drawing-receive', '接收图纸'],
    ['demo.tech.drawing-review', '登记会审'],
    ['demo.tech.rfi-raise', '发起 RFI'],
    ['demo.tech.rfi-respond', '设计回复'],
    ['demo.tech.rfi-accept', '接受/驳回'],
    ['demo.tech.disclosure', '登记交底'],
    ['demo.tech.archive', '确认归档'],
  ] as const) {
    test(`${username} exposes its authorized action`, async ({ page }) => {
      await login(page, username)
      await openTechnical(page)
      await expect(page.getByRole('button', { name: action, exact: true }).first()).toBeVisible()
    })
  }
})
