import { test, expect } from '@playwright/test'

test.describe('Login Smoke Test', () => {
  test('should login with valid credentials and redirect to dashboard', async ({ page }) => {
    // Navigate to login page
    await page.goto('/login')
    await expect(page).toHaveURL(/\/login/)

    // Fill in credentials
    await page.fill('input[placeholder="请输入用户名"]', 'admin')
    await page.fill('input[placeholder="请输入密码"]', 'admin123')

    // Submit the form
    await page.click('button[type="submit"]')

    // Wait for navigation away from login page
    await page.waitForURL((url) => !url.pathname.includes('/login'), { timeout: 15000 })

    // Assert we are on a dashboard/home page
    await expect(page).not.toHaveURL(/\/login/)

    // Verify some dashboard content is visible
    // The dashboard typically has the app title or navigation
    await expect(page.locator('.basic-layout')).toBeVisible({ timeout: 10000 })

    // Take screenshot on success
    await page.screenshot({ path: 'e2e/screenshots/login-success.png', fullPage: false })
  })

  test('should show error for invalid credentials', async ({ page }) => {
    await page.goto('/login')

    await page.fill('input[placeholder="请输入用户名"]', 'admin')
    await page.fill('input[placeholder="请输入密码"]', 'wrong-password')

    await page.click('button[type="submit"]')

    // Wait for error message to appear
    await expect(page.getByText('用户名或密码错误').first()).toBeVisible({ timeout: 10000 })
  })
})
