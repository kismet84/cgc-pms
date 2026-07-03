import { chromium } from '@playwright/test'

const authStateFile = 'e2e/.auth/admin.json'
const BASE_URL = 'http://localhost:5173'

export default async function globalAuthSetup() {
  const browser = await chromium.launch()
  const context = await browser.newContext({ baseURL: BASE_URL })
  const page = await context.newPage()

  await page.goto('/login')
  await page.locator('input[placeholder="请输入用户名"]').waitFor({ state: 'visible', timeout: 10000 })
  await page.fill('input[placeholder="请输入用户名"]', 'admin')
  await page.fill('input[placeholder="请输入密码"]', 'admin123')
  await page.getByRole('button', { name: '登录' }).click()
  await page.locator('.basic-layout').waitFor({ state: 'visible', timeout: 20000 })
  await page.context().storageState({ path: authStateFile })

  await browser.close()
}
