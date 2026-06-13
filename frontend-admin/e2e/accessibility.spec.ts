import { test, expect, type Page } from '@playwright/test';

async function loginAsAdmin(page: Page) {
  await page.goto('/login');
  await page.fill('input[placeholder="请输入用户名"]', 'admin');
  await page.fill('input[placeholder="请输入密码"]', 'admin123');
  await page.click('button[type="submit"]');
  await page.waitForURL((url) => !url.pathname.includes('/login'), { timeout: 15000 });
}

test.describe('Accessibility: aria-label on icon buttons', () => {
  test.beforeEach(async ({ page }) => {
    await loginAsAdmin(page);
    await page.waitForSelector('.topbar', { timeout: 10000 });
  });

  test('hamburger menu toggle has dynamic aria-label', async ({ page }) => {
    // Default state: menu expanded, so aria-label should be "折叠菜单"
    const hamburger = page.locator('.hamburger');
    await expect(hamburger).toBeVisible();
    await expect(hamburger).toHaveAttribute('aria-label', '折叠菜单');

    // Click to collapse
    await hamburger.click();
    await expect(hamburger).toHaveAttribute('aria-label', '展开菜单');

    // Click to expand again
    await hamburger.click();
    await expect(hamburger).toHaveAttribute('aria-label', '折叠菜单');
  });

  test('notification bell has aria-label', async ({ page }) => {
    const bellWrapper = page.locator('span[aria-label="通知"]');
    await expect(bellWrapper).toBeVisible();
  });

  test('help icon has aria-label', async ({ page }) => {
    const helpIcon = page.locator('.top-actions [aria-label="帮助"]');
    await expect(helpIcon).toBeVisible();
  });
});

test.describe('Accessibility: login page', () => {
  test('logo has aria-hidden', async ({ page }) => {
    await page.goto('/login');
    await expect(page.locator('.logo[aria-hidden="true"]')).toBeVisible();
  });

  test('forgot password link has button role and keyboard support', async ({ page }) => {
    await page.goto('/login');
    const forgotLink = page.locator('.forgot');
    await expect(forgotLink).toBeVisible();
    await expect(forgotLink).toHaveAttribute('role', 'button');
    await expect(forgotLink).toHaveAttribute('tabindex', '0');

    // Click triggers the handler (shows info message)
    await forgotLink.click();
    await expect(page.locator('.ant-message-info, .ant-message-notice')).toBeVisible({ timeout: 5000 });
  });
});
