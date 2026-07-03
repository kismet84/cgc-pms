import { test, expect, type BrowserContext, type Page } from '@playwright/test'
import { createAuthenticatedPage } from './auth-session'

let sharedContext: BrowserContext
let sharedPage: Page

test.describe('Project overview navigation', () => {
  test.describe.configure({ mode: 'serial' })

  test.beforeAll(async ({ browser }) => {
    const auth = await createAuthenticatedPage(browser)
    sharedContext = auth.context
    sharedPage = auth.page
  })

  test.afterAll(async () => {
    await sharedPage?.close()
    await sharedContext?.close()
  })

  test('navigates from project list to the first project overview', async () => {
    await sharedPage.goto('/project/list')
    await expect(sharedPage.locator('.project-list-page')).toBeVisible({ timeout: 10000 })
    await expect(sharedPage.locator('.project-query-panel')).toBeVisible({ timeout: 10000 })
    await expect(sharedPage.locator('.project-table-wrap .vxe-table').first()).toBeVisible({ timeout: 10000 })

    const firstProjectLink = sharedPage.locator('.project-code-link').first()
    const hasProjectLink = await firstProjectLink.isVisible({ timeout: 5000 }).catch(() => false)

    if (!hasProjectLink) {
      const totalText = (await sharedPage.locator('.project-table-count').first().textContent())?.trim() ?? '未知'
      throw new Error(`项目列表为空或当前页无可点击项目编号，无法继续验证 /project/:id/overview 链路。当前计数：${totalText}`)
    }

    const projectCode = ((await firstProjectLink.textContent()) ?? '').trim()
    await firstProjectLink.click()

    await sharedPage.waitForURL(/\/project\/[^/]+\/overview/, { timeout: 10000 })
    await expect(sharedPage.locator('.overview')).toBeVisible({ timeout: 10000 })
    await expect(sharedPage.getByText('项目总览')).toBeVisible({ timeout: 10000 })
    await expect(sharedPage.locator('.pt-kpi-strip')).toBeVisible({ timeout: 10000 })
    await expect(sharedPage.locator('.overview-summary')).toBeVisible({ timeout: 10000 })
    await expect(sharedPage.locator('.pt-panel-header').filter({ hasText: '项目经营概览' })).toBeVisible({
      timeout: 10000,
    })

    if (projectCode) {
      await expect(sharedPage.locator('.pt-breadcrumb')).toContainText('项目总览')
    }
  })
})
