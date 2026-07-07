import { mkdirSync } from 'node:fs'
import { dirname } from 'node:path'
import { chromium } from '@playwright/test'

const authStateFile = 'e2e/.auth/admin.json'
const BASE_URL = process.env.PLAYWRIGHT_BASE_URL ?? 'http://localhost:5173'

export default async function globalAuthSetup() {
  mkdirSync(dirname(authStateFile), { recursive: true })

  const browser = await chromium.launch()
  try {
    const context = await browser.newContext({ baseURL: BASE_URL })
    const page = await context.newPage()

    await page.goto('/login')
    await page
      .locator('input[placeholder="请输入用户名"]')
      .waitFor({ state: 'visible', timeout: 10000 })
    await page.fill('input[placeholder="请输入用户名"]', 'admin')
    await page.fill('input[placeholder="请输入密码"]', 'admin123')
    await page.getByRole('button', { name: '登录' }).click()
    await page.waitForURL((url) => !url.pathname.includes('/login'), { timeout: 20000 })

    const loginInputVisible = await page
      .locator('input[placeholder="请输入用户名"]')
      .isVisible()
      .catch(() => false)
    if (loginInputVisible) {
      throw new Error(`global auth setup failed: still on login at ${page.url()}`)
    }

    await page
      .locator('.basic-layout, .lg-page, .app-page')
      .first()
      .waitFor({ state: 'visible', timeout: 20000 })

    await page.context().storageState({ path: authStateFile })
  } finally {
    await browser.close()
  }
}
