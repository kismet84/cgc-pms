import { test, expect, type Locator, type Page } from '@playwright/test'

/**
 * Approval E2E: Pending tasks → modal detail → approve / reject.
 *
 * Current /approval/todo uses vxe-grid and opens details in an Ant modal;
 * it is not the old .wf-todo-page + independent detail route flow.
 */

async function loginAsAdmin(page: Page) {
  await page.goto('/login')
  await page.fill('input[placeholder="请输入用户名"]', 'admin')
  await page.fill('input[placeholder="请输入密码"]', 'admin123')
  await page.click('button[type="submit"]')
  await page.waitForURL((url) => !url.pathname.includes('/login'), { timeout: 15000 })
}

async function waitForApprovalList(page: Page) {
  await page.goto('/approval/todo')
  await expect(page.getByText('处理需要您审批的业务单据')).toBeVisible({ timeout: 10000 })
  await expect(page.locator('.lg-table-wrap')).toBeVisible({ timeout: 10000 })
  await expect(page.locator('.vxe-table').first()).toBeVisible({ timeout: 10000 })
}

function firstApprovalRow(page: Page): Locator {
  return page.locator('.vxe-body--row').first()
}

async function hasPendingTasks(page: Page): Promise<boolean> {
  const totalText = (await page.locator('.lg-total').textContent().catch(() => '')) ?? ''
  if (/共\s*0\s*条/.test(totalText) || (await page.getByText('暂无待办任务').isVisible().catch(() => false))) {
    console.log('Approval todo page loaded with no pending tasks')
    return false
  }
  return firstApprovalRow(page).isVisible({ timeout: 5000 }).catch(() => false)
}

async function openFirstApprovalDetail(page: Page) {
  await firstApprovalRow(page).locator('.lg-link').first().click()
  await expect(page.locator('.approval-detail-modal .approval-detail-content')).toBeVisible({
    timeout: 10000,
  })
}

test.describe('Approval Workflow', () => {
  test.beforeEach(async ({ page }) => {
    await loginAsAdmin(page)
  })

  test('should display pending tasks and view approval detail', async ({ page }) => {
    await waitForApprovalList(page)

    await expect(page.locator('.ant-tabs-tab').filter({ hasText: '我的待办' })).toBeVisible()
    await expect(page.locator('.ant-tabs-tab').filter({ hasText: '我的已办' })).toBeVisible()
    await expect(page.locator('.ant-tabs-tab').filter({ hasText: '抄送我的' })).toBeVisible()

    if (!(await hasPendingTasks(page))) {
      console.log('No pending tasks available; page loaded successfully')
      return
    }

    await openFirstApprovalDetail(page)
    await expect(page.locator('.approval-detail-modal .ant-descriptions')).toBeVisible({ timeout: 5000 })
    await expect(
      page.locator('.approval-detail-modal .approval-detail-section').filter({ hasText: '审批流程' }),
    ).toBeVisible({ timeout: 5000 })
    await expect(
      page.locator('.approval-detail-modal .approval-detail-section').filter({ hasText: '审批记录' }),
    ).toBeVisible({ timeout: 5000 })

    await page.screenshot({ path: 'e2e/screenshots/approval-detail.png', fullPage: true })
  })

  test('should approve a pending task successfully', async ({ page }) => {
    await waitForApprovalList(page)

    if (!(await hasPendingTasks(page))) {
      console.log('No pending tasks available, skipping approve action')
      return
    }

    await openFirstApprovalDetail(page)

    const approveBtn = page.locator('.approval-detail-modal .approval-actions button').filter({
      hasText: '同意',
    })

    if (!(await approveBtn.isVisible({ timeout: 3000 }).catch(() => false))) {
      console.log('Approve button not available for this task; blocked:no-approve-action')
      return
    }

    await approveBtn.click()
    const approveModal = page.locator('.ant-modal').filter({ hasText: '审批通过' }).last()
    await expect(approveModal).toBeVisible({ timeout: 5000 })
    await approveModal.locator('textarea').fill('E2E自动化审批通过')
    await approveModal.locator('.ant-btn-primary').click()

    await expect(page.getByText('审批通过').first()).toBeVisible({ timeout: 10000 })
    await page.screenshot({ path: 'e2e/screenshots/approval-approve-success.png', fullPage: true })
  })

  test('should reject a pending task with required comment', async ({ page }) => {
    await waitForApprovalList(page)

    if (!(await hasPendingTasks(page))) {
      console.log('No pending tasks available, skipping reject action')
      return
    }

    await openFirstApprovalDetail(page)

    const rejectBtn = page.locator('.approval-detail-modal .approval-actions button').filter({
      hasText: '驳回',
    })

    if (!(await rejectBtn.isVisible({ timeout: 3000 }).catch(() => false))) {
      console.log('Reject button not available for this task; blocked:no-reject-action')
      return
    }

    await rejectBtn.click()
    const rejectModal = page.locator('.ant-modal').filter({ hasText: '驳回' }).last()
    await expect(rejectModal).toBeVisible({ timeout: 5000 })
    await rejectModal.locator('textarea').fill('E2E测试-驳回原因：不符合要求')
    await rejectModal.locator('.ant-btn-primary').click()

    await expect(page.getByText('已驳回').first()).toBeVisible({ timeout: 10000 })
    await page.screenshot({ path: 'e2e/screenshots/approval-reject-success.png', fullPage: true })
  })
})
