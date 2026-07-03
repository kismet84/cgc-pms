import { existsSync, readFileSync } from 'node:fs'
import type { Browser } from '@playwright/test'

const authStateFile = 'e2e/.auth/admin.json'
const authUserInfoFile = 'e2e/.auth/admin-user.json'
const BASE_URL = 'http://localhost:5173'
const fallbackUserInfo = {
  userId: '1',
  username: 'admin',
  roles: ['SUPER_ADMIN'],
  permissions: ['*'],
  roleName: 'SUPER_ADMIN',
}

export async function createAuthenticatedPage(browser: Browser) {
  const context = await browser.newContext({ storageState: authStateFile })
  const userInfo = existsSync(authUserInfoFile)
    ? JSON.parse(readFileSync(authUserInfoFile, 'utf-8'))
    : fallbackUserInfo

  await context.addInitScript((persistedUserInfo) => {
    window.sessionStorage.setItem('cgc_pms_userinfo', JSON.stringify(persistedUserInfo))
  }, userInfo)

  const page = await context.newPage()
  await page.goto(`${BASE_URL}/`, { waitUntil: 'domcontentloaded' })
  await page.evaluate((persistedUserInfo) => {
    window.sessionStorage.setItem('cgc_pms_userinfo', JSON.stringify(persistedUserInfo))
  }, userInfo)
  return { context, page }
}
