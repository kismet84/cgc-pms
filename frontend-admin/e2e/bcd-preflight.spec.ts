import { test, expect, type Page } from '@playwright/test'

async function loginAsAdmin(page: Page) {
  await page.goto('/login')
  await page.fill('input[placeholder="请输入用户名"]', 'admin')
  await page.fill('input[placeholder="请输入密码"]', 'admin123')
  await page.click('button[type="submit"]')
  await page.waitForURL((url) => !url.pathname.includes('/login'), { timeout: 15000 })
}

async function anyVisible(locator: ReturnType<Page['locator']>) {
  const count = await locator.count()
  for (let index = 0; index < count; index += 1) {
    if (
      await locator
        .nth(index)
        .isVisible()
        .catch(() => false)
    ) {
      return true
    }
  }
  return false
}

async function classifyRoute(page: Page, path: string, readyText: string) {
  await page.goto(path)
  const finalUrl = page.url()
  const stayedOutOfLogin = !new URL(finalUrl).pathname.includes('/login')
  await expect
    .poll(() => anyVisible(page.getByText(readyText)), {
      message: `${path} should show ready marker ${readyText}`,
      timeout: 10000,
    })
    .toBe(true)
  const readyVisible = true
  const bodyText = (
    await page
      .locator('body')
      .innerText({ timeout: 3000 })
      .catch(() => '')
  ).slice(0, 500)
  console.log(
    `PREFLIGHT ${path} url=${finalUrl} stayedOutOfLogin=${stayedOutOfLogin} ready=${readyVisible}`,
  )
  console.log(`PREFLIGHT_BODY ${path} ${bodyText.replace(/\s+/g, ' ')}`)
  expect(stayedOutOfLogin, `${path} should not redirect to login`).toBe(true)
  expect(readyVisible, `${path} should show ready marker ${readyText}`).toBe(true)
  return { path, finalUrl, stayedOutOfLogin, readyVisible, bodyText }
}

test.describe('BCD closure preflight', () => {
  test.beforeEach(async ({ page }) => {
    await loginAsAdmin(page)
  })

  test('auth state and business module routes are reachable', async ({ page }) => {
    await expect(page.locator('.basic-layout')).toBeVisible({ timeout: 10000 })

    const routes = [
      ['/approval/todo', '我的待办'],
      ['/contract/ledger', '合同台账'],
      ['/dashboard', '驾驶舱'],
      ['/inventory/transaction', '库存交易'],
      ['/payment/application', '付款申请'],
    ] as const

    const results = []
    for (const [path, ready] of routes) {
      results.push(await classifyRoute(page, path, ready))
    }

    expect(results.every((result) => result.stayedOutOfLogin)).toBe(true)
  })
})
