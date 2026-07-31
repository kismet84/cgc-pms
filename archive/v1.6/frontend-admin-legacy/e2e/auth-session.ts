import type { Browser } from '@playwright/test'

const authStateFile = 'e2e/.auth/admin.json'
const BASE_URL = process.env.PLAYWRIGHT_BASE_URL ?? 'http://localhost:5173'

export async function createAuthenticatedPage(browser: Browser) {
  const context = await browser.newContext({ storageState: authStateFile })
  const page = await context.newPage()
  await page.goto(`${BASE_URL}/`, { waitUntil: 'domcontentloaded' })
  return { context, page }
}
