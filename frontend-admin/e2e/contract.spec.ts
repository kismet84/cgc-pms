import { test, expect, type Page } from '@playwright/test';

/**
 * Contract E2E: Full contract creation → submit for approval
 * 
 * Flow:
 * 1. Login as admin
 * 2. Navigate to contract ledger → click "新建合同"
 * 3. Step 1: Fill basic contract info
 * 4. Step 2: Add contract items with auto-sum
 * 5. Step 3: Add payment terms with ratio=100%
 * 6. Step 4: Review and submit
 * 7. Verify success message and redirect to ledger
 */

async function loginAsAdmin(page: Page) {
  await page.goto('/login');
  await page.fill('input[placeholder="请输入用户名"]', 'admin');
  await page.fill('input[placeholder="请输入密码"]', 'admin123');
  await page.click('button[type="submit"]');
  await page.waitForURL((url) => !url.pathname.includes('/login'), { timeout: 15000 });
}

test.describe('Contract Creation & Submission', () => {
  test.beforeEach(async ({ page }) => {
    await loginAsAdmin(page);
  });

  test('should create a contract through 4-step wizard and submit for approval', async ({ page }) => {
    // Navigate to contract ledger
    await page.goto('/contract/ledger');
    await page.waitForSelector('.cl-page', { timeout: 10000 });

    // Click "新建合同" button
    await page.click('.cl-toolbar .ant-btn-primary:has-text("新建合同")');
    await page.waitForURL(/\/contract\/create/, { timeout: 10000 });

    // Verify step wizard is visible
    await expect(page.locator('.step-wizard')).toBeVisible();
    await expect(page.locator('.sw-steps')).toBeVisible();

    // ===== Step 1: Basic Info =====
    // Fill contract name
    await page.fill('input[placeholder="请输入合同名称"]', `E2E合同-${Date.now()}`);

    // Select contract type: 分包合同
    await page.click('.ant-form-item:has(.ant-form-item-label:has-text("合同类型")) .ant-select');
    await page.waitForSelector('.ant-select-dropdown:not(.ant-select-dropdown-hidden)', { timeout: 5000 });
    await page.click('.ant-select-item-option:has-text("分包合同")');

    // Select project: click select → search → pick first
    await page.click('.ant-form-item:has(.ant-form-item-label:has-text("所属项目")) .ant-select');
    await page.waitForSelector('.ant-select-dropdown:not(.ant-select-dropdown-hidden)', { timeout: 5000 });
    // Type to filter and pick the first visible option
    const firstProject = page.locator('.ant-select-item-option').first();
    await firstProject.click();

    // Select partner
    await page.click('.ant-form-item:has(.ant-form-item-label:has-text("合作方")) .ant-select');
    await page.waitForSelector('.ant-select-dropdown:not(.ant-select-dropdown-hidden)', { timeout: 5000 });
    const firstPartner = page.locator('.ant-select-item-option').last();
    await firstPartner.click();

    // Fill contract amount
    await page.locator('.ant-form-item:has(.ant-form-item-label:has-text("合同金额")) .ant-input-number-input').fill('500000');

    // Fill signed date (format: YYYY-MM-DD)
    await page.fill('.ant-form-item:has(.ant-form-item-label:has-text("签订日期")) input', '2026-06-12');

    // Fill start date and end date
    await page.fill('.ant-form-item:has(.ant-form-item-label:has-text("开始日期")) input', '2026-06-12');
    await page.fill('.ant-form-item:has(.ant-form-item-label:has-text("结束日期")) input', '2027-06-12');

    // Select payment method
    await page.click('.ant-form-item:has(.ant-form-item-label:has-text("付款方式")) .ant-select');
    await page.waitForSelector('.ant-select-dropdown:not(.ant-select-dropdown-hidden)', { timeout: 5000 });
    await page.click('.ant-select-item-option:has-text("银行转账")');

    // Select settlement method
    await page.click('.ant-form-item:has(.ant-form-item-label:has-text("结算方式")) .ant-select');
    await page.waitForSelector('.ant-select-dropdown:not(.ant-select-dropdown-hidden)', { timeout: 5000 });
    await page.click('.ant-select-item-option:has-text("按进度结算")');

    // Click "下一步" to go to Step 2
    await page.click('.sw-actions button:has-text("下一步")');

    // ===== Step 2: Contract Items =====
    await expect(page.locator('.item-editor')).toBeVisible({ timeout: 5000 });

    // Add a contract item row
    await page.click('.item-editor button:has-text("添加明细")');

    // Fill the first row inline
    // itemName - first input in the table body row
    const itemRow = page.locator('.item-editor .ant-table-tbody tr.ant-table-row').first();
    await itemRow.locator('input[placeholder="请输入名称"]').fill('钢筋材料费');

    // quantity - input-number
    await itemRow.locator('.ant-input-number-input').first().fill('100');

    // unitPrice - second input-number
    await itemRow.locator('.ant-input-number-input').nth(1).fill('3000');

    // Wait for auto-calc: amount should be 300,000.00
    await expect(itemRow.locator('.ant-input-number-input').nth(3)).toHaveValue('300000.00', { timeout: 3000 });

    // Add a second item row
    await page.click('.item-editor button:has-text("添加明细")');
    const itemRow2 = page.locator('.item-editor .ant-table-tbody tr.ant-table-row').nth(1);
    await itemRow2.locator('input[placeholder="请输入名称"]').fill('水泥材料费');
    await itemRow2.locator('.ant-input-number-input').first().fill('200');
    await itemRow2.locator('.ant-input-number-input').nth(1).fill('1000');
    await expect(itemRow2.locator('.ant-input-number-input').nth(3)).toHaveValue('200000.00', { timeout: 3000 });

    // Click "下一步" to go to Step 3
    await page.click('.sw-actions button:has-text("下一步")');

    // ===== Step 3: Payment Terms =====
    await expect(page.locator('.term-editor')).toBeVisible({ timeout: 5000 });

    // Add a payment term with ratio 100%
    await page.click('.term-editor button:has-text("添加付款条款")');

    const termRow = page.locator('.term-editor .ant-table-tbody tr.ant-table-row').first();
    await termRow.locator('input[placeholder="如：预付款、进度款"]').fill('竣工验收后一次性支付');

    // paymentRatio = 100
    await termRow.locator('.ant-input-number-input').first().fill('100');

    // paymentAmount = 500000
    await termRow.locator('.ant-input-number-input').nth(1).fill('500000');

    // paymentCondition
    await termRow.locator('input[placeholder="付款触发条件"]').fill('竣工验收合格后30日内');

    // plannedDate
    await termRow.locator('.ant-picker input').fill('2027-06-30');

    // Click "下一步" to go to Step 4 (Review)
    await page.click('.sw-actions button:has-text("下一步")');

    // ===== Step 4: Review & Submit =====
    await expect(page.locator('.cf-review')).toBeVisible({ timeout: 5000 });

    // Verify review info shows our data
    await expect(page.locator('.cf-review .ant-descriptions')).toBeVisible();

    // Click the StepWizard's "提交" button (the primary button in sw-actions)
    const submitBtn = page.locator('.sw-actions button.ant-btn-primary:has-text("提交")');
    await expect(submitBtn).toBeVisible();
    await submitBtn.click();

    // Wait for success message or redirect
    // The success handler pushes to /contract/ledger
    await page.waitForURL(/\/contract\/ledger/, { timeout: 20000 });

    // Verify we're back on the ledger page
    await expect(page.locator('.cl-page')).toBeVisible({ timeout: 10000 });

    // Take screenshot on success
    await page.screenshot({ path: 'e2e/screenshots/contract-create-success.png', fullPage: true });
  });

  test('should validate required fields on step 1 before proceeding', async ({ page }) => {
    await page.goto('/contract/create');
    await page.waitForSelector('.step-wizard', { timeout: 10000 });

    // Try to click "下一步" without filling anything
    await page.click('.sw-actions button:has-text("下一步")');

    // Should see validation warning or error (Ant Design form validation)
    // The form shows inline error messages
    await expect(page.locator('.ant-form-item-explain-error').first()).toBeVisible({ timeout: 5000 });

    // Now fill required fields minimally
    await page.fill('input[placeholder="请输入合同名称"]', 'E2E验证测试');
    await page.click('.ant-form-item:has(.ant-form-item-label:has-text("合同类型")) .ant-select');
    await page.waitForSelector('.ant-select-dropdown:not(.ant-select-dropdown-hidden)', { timeout: 5000 });
    await page.click('.ant-select-item-option:has-text("总包合同")');

    // Select project
    await page.click('.ant-form-item:has(.ant-form-item-label:has-text("所属项目")) .ant-select');
    await page.waitForSelector('.ant-select-dropdown:not(.ant-select-dropdown-hidden)', { timeout: 5000 });
    await page.locator('.ant-select-item-option').first().click();

    // Select partner
    await page.click('.ant-form-item:has(.ant-form-item-label:has-text("合作方")) .ant-select');
    await page.waitForSelector('.ant-select-dropdown:not(.ant-select-dropdown-hidden)', { timeout: 5000 });
    await page.locator('.ant-select-item-option').last().click();

    // Fill amount
    await page.locator('.ant-form-item:has(.ant-form-item-label:has-text("合同金额")) .ant-input-number-input').fill('100000');

    // Fill date
    await page.fill('.ant-form-item:has(.ant-form-item-label:has-text("签订日期")) input', '2026-06-12');

    // Now it should proceed
    await page.click('.sw-actions button:has-text("下一步")');
    await expect(page.locator('.item-editor')).toBeVisible({ timeout: 5000 });
  });
});
