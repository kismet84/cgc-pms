import { test, expect, type Page } from '@playwright/test'

/**
 * User Menu E2E: Login → Profile Edit → Password Change → Settings → Help → Logout
 *
 * Page routes:
 *   /login     — login page (public)
 *   /dashboard — landing after login
 *   /profile   — personal info + password change
 *   /settings  — notification toggle + UI preferences
 *   /help      — keyboard shortcuts + FAQ
 *
 * Selector map:
 *   .user-info                    — user avatar+name dropdown trigger (BasicLayout header)
 *   .ant-dropdown-menu-item       — dropdown menu items rendered by <a-dropdown>
 *   .ant-card:has-text("个人资料")  — profile edit card
 *   .ant-card:has-text("修改密码")  — password change card
 *   .ant-card:has-text("通知设置")  — settings notification card
 *   [aria-label="帮助"]            — help icon button (QuestionCircleOutlined)
 *   .ant-message-notice           — Ant Design message toast container
 *
 * Backend endpoints used:
 *   POST /auth/login            — authentication
 *   PUT  /profile               — update personal info
 *   PUT  /profile/password      — change password
 *   GET  /profile/preferences   — load settings
 *   PUT  /profile/preferences   — save settings
 */

async function loginAsAdmin(page: Page) {
  await page.goto('/login')
  await page.fill('input[placeholder="请输入用户名"]', 'admin')
  await page.fill('input[placeholder="请输入密码"]', 'admin123')
  await page.click('button[type="submit"]')
  await page.waitForURL((url) => !url.pathname.includes('/login'), { timeout: 15000 })
}

/**
 * Open the user dropdown menu and click a menu item by text.
 * The dropdown is triggered by the .user-info div in the topbar.
 */
async function clickUserMenuItem(page: Page, itemText: string) {
  await page.click('.user-info')
  // Wait for the dropdown menu to appear, then click the matching item
  const menuItem = page.locator('.ant-dropdown-menu-item', { hasText: itemText })
  await menuItem.waitFor({ state: 'visible', timeout: 5000 })
  await menuItem.click()
}

test.describe('User Menu Flow', () => {
  test.beforeEach(async ({ page }) => {
    await loginAsAdmin(page)
  })

  test('complete user menu flow: profile edit → password change → settings toggle → help → logout', async ({
    page,
  }) => {
    // ── Step 1-2: Verify logged in on dashboard ──
    await expect(page).not.toHaveURL(/\/login/)
    await expect(page.locator('.basic-layout')).toBeVisible({ timeout: 10000 })

    // ── Step 3: User dropdown → 个人中心 → /profile ──
    await clickUserMenuItem(page, '个人中心')
    await expect(page).toHaveURL(/\/profile/)

    // ── Step 4: Edit phone on profile → save → success ──
    const profileCard = page.locator('.ant-card:has-text("个人资料")')
    await profileCard.waitFor({ state: 'visible', timeout: 10000 })

    // Clear existing phone value and type new one
    const phoneInput = profileCard.locator('input[placeholder="请输入手机号"]')
    await phoneInput.fill('13800138000')

    // Click the primary save button inside the profile card
    await profileCard.locator('button.ant-btn-primary').click()

    // Expect success toast
    await expect(page.locator('.ant-message-notice:has-text("个人资料更新成功")')).toBeVisible({
      timeout: 8000,
    })

    // ── Step 5: Change password → submit → success ──
    const passwordCard = page.locator('.ant-card:has-text("修改密码")')
    await passwordCard.scrollIntoViewIfNeeded()

    await passwordCard.locator('input[placeholder="请输入原密码"]').fill('admin123')
    await passwordCard.locator('input[placeholder="请输入新密码"]').fill('test123456')
    await passwordCard.locator('input[placeholder="请再次输入新密码"]').fill('test123456')

    await passwordCard.locator('button:has-text("修改密码")').click()

    // Expect password change success toast
    await expect(page.locator('.ant-message-notice:has-text("密码修改成功")')).toBeVisible({
      timeout: 8000,
    })

    // ── Step 6: User dropdown → 设置 → /settings → toggle notification → save ──
    await clickUserMenuItem(page, '设置')
    await expect(page).toHaveURL(/\/settings/)

    const notificationCard = page.locator('.ant-card:has-text("通知设置")')
    await notificationCard.waitFor({ state: 'visible', timeout: 10000 })

    // Toggle the switch off (click it regardless of current state — we toggle)
    const notificationSwitch = notificationCard.locator('.ant-switch')
    await notificationSwitch.click()

    // Click "保存设置" button
    await page.locator('button:has-text("保存设置")').click()

    // Expect save success toast
    await expect(page.locator('.ant-message-notice:has-text("保存成功")')).toBeVisible({
      timeout: 8000,
    })

    // ── Step 7: Help icon → /help → verify content ──
    await page.click('[aria-label="帮助"]')
    await expect(page).toHaveURL(/\/help/)

    // Verify "快捷键" card is visible
    await expect(page.locator('.ant-card:has-text("快捷键")')).toBeVisible({ timeout: 10000 })

    // Verify "常见问题" card is visible
    await expect(page.locator('.ant-card:has-text("常见问题")')).toBeVisible({ timeout: 10000 })

    // Take screenshot on success
    await page.screenshot({
      path: 'e2e/screenshots/user-menu-help.png',
      fullPage: true,
    })

    // ── Step 8: User dropdown → 退出登录 → /login ──
    await clickUserMenuItem(page, '退出登录')

    // After logout, expect redirect to /login
    await expect(page).toHaveURL(/\/login/, { timeout: 10000 })

    // Verify login page is visible
    await expect(page.locator('input[placeholder="请输入用户名"]')).toBeVisible({ timeout: 5000 })
  })
})
