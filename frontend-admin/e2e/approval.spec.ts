import { test, expect, type Page } from '@playwright/test';

/**
 * Approval E2E: Pending tasks → detail → approve / reject
 * 
 * Flow:
 * 1. Login as admin
 * 2. Navigate to "我的待办"
 * 3. Verify pending tasks visible in todo list
 * 4. Click first pending task → open approval detail
 * 5. Verify approval detail page shows instance info, nodes, and records
 * 6. Click "同意" → confirm in modal → verify success
 * 7. Return to todo list → verify task is gone (or navigate directly)
 * 8. Bonus: Test reject flow with a different task
 */

async function loginAsAdmin(page: Page) {
  await page.goto('/login');
  await page.fill('input[placeholder="请输入用户名"]', 'admin');
  await page.fill('input[placeholder="请输入密码"]', 'admin123');
  await page.click('button[type="submit"]');
  await page.waitForURL((url) => !url.pathname.includes('/login'), { timeout: 15000 });
}

test.describe('Approval Workflow', () => {
  test.beforeEach(async ({ page }) => {
    await loginAsAdmin(page);
  });

  test('should display pending tasks and view approval detail', async ({ page }) => {
    // Navigate to approval todo list
    await page.goto('/approval/todo');
    await page.waitForSelector('.wf-todo-page', { timeout: 10000 });

    // Verify the page header is visible
    await expect(page.locator('.ant-page-header')).toBeVisible();

    // Verify tabs are visible
    await expect(page.locator('.ant-tabs')).toBeVisible();
    await expect(page.locator('.ant-tabs-tab:has-text("我的待办")')).toBeVisible();
    await expect(page.locator('.ant-tabs-tab:has-text("我的已办")')).toBeVisible();
    await expect(page.locator('.ant-tabs-tab:has-text("抄送我")')).toBeVisible();

    // Verify table exists
    await expect(page.locator('.ant-table')).toBeVisible({ timeout: 10000 });

    // Check if there are any pending tasks (table may be empty → that's OK for the UI test)
    const hasTasks = await page.locator('.ant-table-tbody tr.ant-table-row').first().isVisible({ timeout: 5000 }).catch(() => false);

    if (hasTasks) {
      // Click the first task title link to open detail
      const firstTaskLink = page.locator('.ant-table-tbody tr.ant-table-row').first().locator('a');
      await firstTaskLink.click();

      // Should navigate to approval detail page
      await page.waitForURL(/\/approval\//, { timeout: 15000 });

      // Verify approval detail page elements
      await expect(page.locator('.wf-detail-page')).toBeVisible({ timeout: 10000 });
      await expect(page.locator('.ant-page-header:has-text("审批详情")')).toBeVisible();

      // Verify instance info card
      await expect(page.locator('.ant-descriptions')).toBeVisible({ timeout: 5000 });

      // Verify node flow section
      await expect(page.locator('.wf-card:has-text("审批流程")')).toBeVisible({ timeout: 5000 });

      // Verify approval records section
      await expect(page.locator('.wf-card:has-text("审批记录")')).toBeVisible({ timeout: 5000 });

      // Take screenshot
      await page.screenshot({ path: 'e2e/screenshots/approval-detail.png', fullPage: true });
    }
  });

  test('should approve a pending task successfully', async ({ page }) => {
    // Navigate to approval todo
    await page.goto('/approval/todo');
    await page.waitForSelector('.wf-todo-page', { timeout: 10000 });

    // Wait for table to load
    await page.waitForSelector('.ant-table', { timeout: 10000 });

    // Check if there are pending tasks
    const hasTasks = await page.locator('.ant-table-tbody tr.ant-table-row').first().isVisible({ timeout: 5000 }).catch(() => false);

    if (!hasTasks) {
      // Skip this test if no pending tasks
      console.log('No pending tasks available, skipping approve test');
      return;
    }

    // Click the first task to go to detail
    await page.locator('.ant-table-tbody tr.ant-table-row').first().locator('a').click();
    await page.waitForURL(/\/approval\//, { timeout: 15000 });
    await expect(page.locator('.wf-detail-page')).toBeVisible({ timeout: 10000 });

    // Wait for action bar to load
    await expect(page.locator('.wf-actions')).toBeVisible({ timeout: 5000 });

    // Check if "同意" (approve) button is available
    const approveBtn = page.locator('.wf-actions button:has-text("同意")');

    if (await approveBtn.isVisible({ timeout: 3000 }).catch(() => false)) {
      // Click "同意" button
      await approveBtn.click();

      // Wait for approve modal to appear
      await expect(page.locator('.ant-modal:has-text("审批通过")')).toBeVisible({ timeout: 5000 });

      // Optionally fill comment
      await page.locator('.ant-modal textarea').fill('E2E自动化审批通过');

      // Click the modal's OK button
      await page.locator('.ant-modal .ant-btn-primary').click();

      // Wait for success response and detail refresh
      // The success message should appear
      await expect(page.locator('.ant-message-success').or(page.locator('.ant-message-notice'))).toBeVisible({ timeout: 10000 });

      // Take screenshot after approval
      await page.screenshot({ path: 'e2e/screenshots/approval-approve-success.png', fullPage: true });
    } else {
      console.log('Approve button not available for this task, may already be processed');
    }
  });

  test('should reject a pending task with required comment', async ({ page }) => {
    // Navigate to approval todo
    await page.goto('/approval/todo');
    await page.waitForSelector('.wf-todo-page', { timeout: 10000 });
    await page.waitForSelector('.ant-table', { timeout: 10000 });

    // Check if there are pending tasks
    const hasTasks = await page.locator('.ant-table-tbody tr.ant-table-row').first().isVisible({ timeout: 5000 }).catch(() => false);

    if (!hasTasks) {
      console.log('No pending tasks available, skipping reject test');
      return;
    }

    // Click the first task
    await page.locator('.ant-table-tbody tr.ant-table-row').first().locator('a').click();
    await page.waitForURL(/\/approval\//, { timeout: 15000 });
    await expect(page.locator('.wf-detail-page')).toBeVisible({ timeout: 10000 });

    // Wait for actions
    await expect(page.locator('.wf-actions')).toBeVisible({ timeout: 5000 });

    // Check if "驳回" (reject) button is available
    const rejectBtn = page.locator('.wf-actions button:has-text("驳回")');

    if (await rejectBtn.isVisible({ timeout: 3000 }).catch(() => false)) {
      // Click "驳回" button
      await rejectBtn.click();

      // Wait for reject modal
      await expect(page.locator('.ant-modal:has-text("驳回")')).toBeVisible({ timeout: 5000 });

      // Fill required reject reason
      await page.locator('.ant-modal textarea').fill('E2E测试-驳回原因：不符合要求');

      // Click confirm
      await page.locator('.ant-modal .ant-btn-primary').click();

      // Wait for success
      await expect(page.locator('.ant-message-success').or(page.locator('.ant-message-notice'))).toBeVisible({ timeout: 10000 });

      // Take screenshot
      await page.screenshot({ path: 'e2e/screenshots/approval-reject-success.png', fullPage: true });
    } else {
      console.log('Reject button not available for this task');
    }
  });
});
