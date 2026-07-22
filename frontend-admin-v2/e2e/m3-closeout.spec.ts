import AxeBuilder from '@axe-core/playwright'
import { expect, test, type Page } from '@playwright/test'
import { captureRuntimeErrors } from './runtime-errors'

const runLiveCloseout = process.env.V2_LIVE_CLOSEOUT === '1'
const closedProjectId = process.env.V2_CLOSEOUT_PROJECT_ID || '520000000000000001'
const runtimeErrors = new WeakMap<Page, string[]>()

async function login(page: Page, username: string) {
  expect((await page.goto(`/api/auth/dev-login?username=${username}`))?.ok()).toBe(true)
}

async function openCloseout(page: Page, projectId = closedProjectId) {
  await page.goto(`/v2/project-closeout?projectId=${projectId}`)
  await expect(page.getByRole('heading', { level: 1, name: '竣工收尾闭环' })).toBeVisible()
  await expect(page.locator('.shell-placeholder')).toHaveCount(0)
  await expect(page.locator('.v2-page-state--loading')).toHaveCount(0)
  await expect(page.getByRole('alert', { name: '页面暂时无法显示' })).toHaveCount(0)
}

test.describe('M3 live project closeout workspace', () => {
  test.skip(!runLiveCloseout, 'Set V2_LIVE_CLOSEOUT=1 only against local test/demo runtime')
  test.beforeEach(({ page }) => runtimeErrors.set(page, captureRuntimeErrors(page)))
  test.afterEach(({ page }) => expect(runtimeErrors.get(page) ?? []).toEqual([]))

  test('real closed chain remains stable and accessible in three viewports', async ({ page }) => {
    await login(page, 'admin')
    for (const viewport of [
      { width: 1440, height: 900 },
      { width: 1024, height: 768 },
      { width: 390, height: 844 },
    ]) {
      await page.setViewportSize(viewport)
      await openCloseout(page)
      await expect(page.getByText('M53-CLOSEOUT-STL-LEGAL')).toBeVisible()
      expect(
        await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth),
      ).toBe(true)
      const result = await new AxeBuilder({ page }).include('.closeout-page').analyze()
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
    await login(page, 'demo.closeout.query')
    await openCloseout(page)
    for (const action of [
      '发起收尾',
      '登记分项验收',
      '登记竣工验收',
      '绑定最终结算',
      '核验尾款回收',
      '登记质保责任',
      '登记缺陷',
      '提交整改',
      '复验缺陷',
      '释放质保',
      '登记档案移交',
      '确认签收',
      '关闭项目',
    ])
      await expect(page.getByRole('button', { name: action, exact: true })).toHaveCount(0)
    expect(mutations).toEqual([])
  })

  for (const [username, action, projectId] of [
    ['demo.closeout.initiate', '发起收尾', '520000000000009003'],
    ['demo.closeout.section', '登记分项验收', '520000000000009002'],
    ['demo.closeout.acceptance', '登记竣工验收', '520000000000009001'],
    ['demo.closeout.settlement', '绑定最终结算', '520000000000009004'],
    ['demo.closeout.collection', '核验尾款回收', '520000000000009005'],
    ['demo.closeout.warranty', '登记质保责任', '520000000000009006'],
    ['demo.closeout.defect', '登记缺陷', '520000000000009008'],
    ['demo.closeout.defect-verify', '复验缺陷', '520000000000009009'],
    ['demo.closeout.archive', '登记档案移交', '520000000000009007'],
    ['demo.closeout.close', '关闭项目', '520000000000009010'],
  ] as const) {
    test(`${username} exposes its authorized stage action`, async ({ page }) => {
      await login(page, username)
      await openCloseout(page, projectId)
      await expect(page.getByRole('button', { name: action, exact: true }).first()).toBeVisible()
    })
  }
})
