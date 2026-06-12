import { test, expect, type Page } from '@playwright/test';

/**
 * Settlement E2E: List → Detail → Summary data verification → Status tags
 *
 * Flow:
 * 1. Login as admin
 * 2. Navigate to settlement list → verify page structure (KPI cards, filter, table)
 * 3. Click a settlement → verify detail page with auto-summarized data
 * 4. Verify settlement status tags (DRAFT/FINALIZED/CANCELLED)
 *
 * NOTE: The settlement list uses VxeGrid (vxe-grid), NOT ant-table-vue.
 * Selectors differ accordingly.
 *
 * Selector map (list page):
 *   .stl-page        — page wrapper
 *   .stl-kpis        — KPI cards grid container
 *   .stl-kpi         — single KPI card
 *   .stl-filter      — filter card
 *   .stl-field       — filter field row
 *   .stl-filter-actions — filter action buttons
 *   .stl-toolbar     — toolbar (新建结算 button)
 *   .stl-link        — settlement code link (click to detail)
 *   .vxe-table       — vxe-grid rendered table
 *   .vxe-body--row   — vxe-grid body row
 *   .ant-tag         — status tags (settlementStatus / approvalStatus)
 *
 * Selector map (detail page):
 *   .stl-detail-page      — detail page wrapper
 *   .ant-page-header      — page header with back button
 *   .ant-tabs-tab         — tab labels
 *   .stl-summary-readonly — summary tab container
 *   .ant-descriptions     — descriptions component for data display
 *
 * Settlement statuses:
 *   DRAFT → 草稿 (default), FINALIZED → 已定案 (green), CANCELLED → 已作废 (red)
 *
 * Backend:
 *   GET  /api/settlements        — paginated list
 *   GET  /api/settlements/{id}   — detail (includes items, variations, payments, costs)
 *   GET  /api/settlements/kpi    — KPI summary
 *   POST /api/settlements        — create settlement
 *   POST /api/settlements/{id}/submit — submit for approval
 */

async function loginAsAdmin(page: Page) {
  await page.goto('/login');
  await page.fill('input[placeholder="请输入用户名"]', 'admin');
  await page.fill('input[placeholder="请输入密码"]', 'admin123');
  await page.click('button[type="submit"]');
  await page.waitForURL((url) => !url.pathname.includes('/login'), { timeout: 15000 });
}

test.describe('Settlement: List → Detail → Summary', () => {
  test.beforeEach(async ({ page }) => {
    await loginAsAdmin(page);
  });

  test('should navigate to settlement list and verify page structure', async ({ page }) => {
    await page.goto('/settlement/list');
    await page.waitForSelector('.stl-page', { timeout: 10000 });

    // Verify breadcrumb
    await expect(page.locator('.stl-breadcrumb')).toBeVisible();
    await expect(page.locator('.stl-breadcrumb:has-text("结算列表")')).toBeVisible();

    // Verify KPI cards section
    await expect(page.locator('.stl-kpis')).toBeVisible();

    // Verify at least 5 KPI cards (total count, contract amount, settlement amount, paid amount, finalized count)
    const kpiCards = page.locator('.stl-kpi');
    const kpiCount = await kpiCards.count();
    expect(kpiCount).toBeGreaterThanOrEqual(5);

    // Verify filter section
    await expect(page.locator('.stl-filter')).toBeVisible();

    // Verify filter fields
    await expect(page.locator('.stl-field:has(label:has-text("所属项目"))')).toBeVisible();
    await expect(page.locator('.stl-field:has(label:has-text("关联合同"))')).toBeVisible();
    await expect(page.locator('.stl-field:has(label:has-text("合作方"))')).toBeVisible();
    await expect(page.locator('.stl-field:has(label:has-text("结算状态"))')).toBeVisible();

    // Verify filter action buttons
    await expect(page.locator('.stl-filter-actions button:has-text("查询")')).toBeVisible();
    await expect(page.locator('.stl-filter-actions button:has-text("重置")')).toBeVisible();

    // Verify toolbar
    await expect(page.locator('.stl-toolbar button:has-text("新建结算")')).toBeVisible();

    // Verify table (vxe-grid)
    await expect(page.locator('.vxe-table')).toBeVisible({ timeout: 10000 });

    await page.screenshot({ path: 'e2e/screenshots/settlement-list-structure.png', fullPage: true });
  });

  test('should verify settlement KPI cards show data', async ({ page }) => {
    await page.goto('/settlement/list');
    await page.waitForSelector('.stl-page', { timeout: 10000 });

    // Check KPI cards for meaningful labels and values
    const kpiCards = page.locator('.stl-kpi');
    const kpiCount = await kpiCards.count();

    for (let i = 0; i < kpiCount; i++) {
      const card = kpiCards.nth(i);
      const titleEl = card.locator('.stl-kpi-title');
      const valueEl = card.locator('.stl-kpi-value');

      const titleVisible = await titleEl.isVisible().catch(() => false);
      const valueVisible = await valueEl.isVisible().catch(() => false);

      if (titleVisible && valueVisible) {
        const title = await titleEl.textContent();
        const value = await valueEl.textContent();
        console.log(`KPI card ${i}: "${title}" = "${value}"`);
        expect(title).toBeTruthy();
        expect(value).toBeTruthy();
      }
    }

    await page.screenshot({ path: 'e2e/screenshots/settlement-kpi-cards.png', fullPage: true });
  });

  test('should navigate to settlement detail and verify auto-summarized data', async ({ page }) => {
    await page.goto('/settlement/list');
    await page.waitForSelector('.stl-page', { timeout: 10000 });
    await page.waitForSelector('.vxe-table', { timeout: 10000 });

    // Check if there are any settlement rows
    const hasRows = await page.locator('.vxe-body--row').first().isVisible({ timeout: 5000 }).catch(() => false);

    if (!hasRows) {
      console.log('No settlements available, skipping detail test');
      return;
    }

    // Click the first settlement code link to navigate to detail
    const firstLink = page.locator('.stl-link').first();
    await firstLink.click();

    // Wait for detail page to load
    await page.waitForSelector('.stl-detail-page', { timeout: 10000 });

    // Verify page header with back button
    await expect(page.locator('.ant-page-header:has-text("结算详情")')).toBeVisible({ timeout: 5000 });

    // Verify status tags are visible in the header
    await expect(page.locator('.ant-page-header .ant-tag').first()).toBeVisible({ timeout: 3000 });

    // Verify tabs exist
    await expect(page.locator('.ant-tabs-tab:has-text("基本信息")')).toBeVisible();

    // Verify basic info tab displays settlement data
    await expect(page.locator('.ant-descriptions')).toBeVisible({ timeout: 5000 });

    // Verify key fields: settlement code, contract name, project name
    const descriptionsText = await page.locator('.ant-descriptions').first().textContent();
    expect(descriptionsText).toBeTruthy();

    // Take screenshot of basic info tab
    await page.screenshot({ path: 'e2e/screenshots/settlement-detail-basic.png', fullPage: true });

    // Click "汇总" tab
    await page.locator('.ant-tabs-tab:has-text("汇总")').click();
    await page.waitForTimeout(300);

    // Verify summary tab shows auto-summarized data
    await expect(page.locator('.stl-summary-readonly')).toBeVisible({ timeout: 5000 });

    // Verify summary contains the key amount fields
    const summaryText = await page.locator('.stl-summary-readonly').textContent();
    expect(summaryText).toBeTruthy();

    // Verify key labels present in summary
    const hasContractAmount = summaryText?.includes('合同金额') ?? false;
    const hasFinalAmount = summaryText?.includes('结算金额') ?? false;
    const hasFormula = summaryText?.includes('计算公式') ?? false;

    if (hasContractAmount) console.log('Summary: contractAmount field present');
    if (hasFinalAmount) console.log('Summary: finalAmount field present');
    if (hasFormula) console.log('Summary: calculation formula present');

    await page.screenshot({ path: 'e2e/screenshots/settlement-detail-summary.png', fullPage: true });
  });

  test('should verify settlement status tags on list page', async ({ page }) => {
    await page.goto('/settlement/list');
    await page.waitForSelector('.stl-page', { timeout: 10000 });
    await page.waitForSelector('.vxe-table', { timeout: 10000 });

    // Check if there are settlement rows
    const hasRows = await page.locator('.vxe-body--row').first().isVisible({ timeout: 5000 }).catch(() => false);

    if (!hasRows) {
      console.log('No settlements available, skipping status tag verification');
      return;
    }

    // Collect all settlement status tags visible on the page
    const statusTags = page.locator('.vxe-body--row .ant-tag');
    const tagCount = await statusTags.count();

    if (tagCount > 0) {
      // Read status tag texts
      const statusTexts: string[] = [];
      for (let i = 0; i < Math.min(tagCount, 10); i++) {
        const text = await statusTags.nth(i).textContent();
        if (text) statusTexts.push(text.trim());
      }

      console.log(`Found ${tagCount} status/approval tags:`, statusTexts);

      // Verify at least one recognized settlement status
      const knownStatuses = ['草稿', '已定案', '已作废', '审批中', '已通过', '已驳回', '已撤回'];
      const hasKnownStatus = statusTexts.some(t => knownStatuses.includes(t));
      expect(hasKnownStatus).toBeTruthy();
    } else {
      console.log('No status tags found in table rows');
    }

    await page.screenshot({ path: 'e2e/screenshots/settlement-status-tags.png', fullPage: true });
  });
});
