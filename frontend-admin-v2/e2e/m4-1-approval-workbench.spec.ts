import AxeBuilder from '@axe-core/playwright'
import { expect, test, type Page, type Route } from '@playwright/test'
import { captureRuntimeErrors } from './runtime-errors'

const task = {
  id: '91',
  instanceId: '81',
  nodeInstanceId: '71',
  businessType: 'PAY_REQUEST',
  businessId: '9001',
  businessCode: 'PAY-2026-001',
  approverId: '1',
  approverName: '审批人',
  taskStatus: 'PENDING',
  roundNo: 1,
  taskVersion: 0,
  receivedAt: '2026-07-20T08:00:00',
  title: '付款申请审批',
  instanceStatus: 'RUNNING',
}

const records = {
  todo: task,
  done: {
    id: '92',
    instanceId: '81',
    actionType: 'APPROVE',
    actionName: '同意',
    operatorName: '审批人',
    createdAt: '2026-07-20T09:00:00',
    title: '付款申请审批',
    businessType: 'PAY_REQUEST',
    businessId: '9001',
    businessCode: 'PAY-2026-001',
  },
  cc: {
    id: '93',
    instanceId: '81',
    title: '付款申请审批',
    businessType: 'PAY_REQUEST',
    businessId: '9001',
    businessCode: 'PAY-2026-001',
    instanceStatus: 'RUNNING',
    ccUserName: '抄送人',
    createdTime: '2026-07-20T09:30:00',
    isRead: false,
  },
  mine: {
    instanceId: '81',
    title: '付款申请审批',
    businessType: 'PAY_REQUEST',
    businessId: '9001',
    businessCode: 'PAY-2026-001',
    instanceStatus: 'RUNNING',
    currentNodeName: '财务审批',
    createdAt: '2026-07-20T08:00:00',
    updatedAt: '2026-07-20T09:30:00',
  },
}

const detail = {
  id: '81',
  templateId: '1',
  templateName: '付款审批',
  businessType: 'PAY_REQUEST',
  businessId: '9001',
  businessCode: 'PAY-2026-001',
  title: '付款申请审批',
  businessSummary: '演示付款申请',
  amount: '120000.00',
  instanceStatus: 'RUNNING',
  currentRound: 1,
  resubmitCount: 0,
  initiatorId: '8',
  initiatorName: '发起人',
  startedAt: '2026-07-20T08:00:00',
  availableActions: ['approve', 'reject'],
  nodes: [
    {
      id: '71',
      templateNodeId: '61',
      nodeCode: 'FINANCE',
      nodeName: '财务审批',
      nodeOrder: 1,
      approveMode: 'OR',
      nodeStatus: 'ACTIVE',
      roundNo: 1,
      tasks: [task],
    },
  ],
  records: [],
}

function fulfill(route: Route, data: unknown, status = 200) {
  return route.fulfill({
    status,
    contentType: 'application/json',
    body: JSON.stringify({ code: status === 200 ? '0' : 'ERROR', message: 'success', data }),
  })
}

async function installApprovalMock(page: Page): Promise<void> {
  await page.route('**/api/auth/userinfo', (route) =>
    fulfill(route, {
      userId: '1',
      username: 'approver',
      realName: '审批人',
      roles: ['USER'],
      permissions: ['workflow:instance:query', 'workflow:approve', 'workflow:reject'],
    }),
  )
  await page.route('**/api/auth/refresh', (route) => fulfill(route, null, 401))
  await page.route('**/api/project-context/options', (route) => fulfill(route, []))
  await page.route(/\/api\/workflow\/business-types(?:\?.*)?$/, (route) =>
    fulfill(route, ['PAY_REQUEST']),
  )
  await page.route(
    /\/api\/workflow\/(?:tasks\/(?:todo|done|cc)|instances\/mine)(?:\?.*)?$/,
    (route) => {
      const path = new URL(route.request().url()).pathname
      const tab = path.endsWith('/done')
        ? 'done'
        : path.endsWith('/cc')
          ? 'cc'
          : path.endsWith('/mine')
            ? 'mine'
            : 'todo'
      return fulfill(route, { records: [records[tab]], total: 1, pageNo: 1, pageSize: 10 })
    },
  )
  await page.route('**/api/workflow/instances/81', (route) => fulfill(route, detail))
}

test.describe('M4-1 approval workbench', () => {
  test('four lists and detail dialog remain usable at three viewports', async ({ page }) => {
    await installApprovalMock(page)
    const runtimeErrors = captureRuntimeErrors(page)

    for (const viewport of [
      { width: 1440, height: 900 },
      { width: 1024, height: 768 },
      { width: 390, height: 844 },
    ]) {
      await page.setViewportSize(viewport)
      await page.goto('/v2/approval/todo')
      await expect(page.getByRole('heading', { level: 1, name: '审批工作台' })).toBeAttached()
      await expect(page.getByRole('heading', { level: 1, name: '审批工作台' })).toHaveClass(
        /v2-visually-hidden/,
      )
      const filterLabels = page.locator('.workflow-filter .v2-field__label')
      await expect(filterLabels).toHaveCount(3)
      expect(
        await filterLabels.evaluateAll((labels) =>
          labels.every((label) => label.classList.contains('v2-visually-hidden')),
        ),
      ).toBe(true)
      await expect(page.getByLabel('审批任务表格')).toBeVisible()
      await expect(page.getByRole('button', { name: '付款申请审批', exact: true })).toBeVisible()
      await expect(page.getByRole('cell', { name: 'PAY-2026-001', exact: true })).toBeVisible()

      for (const tab of ['我已处理', '抄送我的', '我发起', '待我处理']) {
        await page.getByRole('link', { name: tab, exact: true }).click()
        await expect(page.getByText('付款申请审批', { exact: true })).toBeVisible()
      }

      await page.getByRole('button', { name: '付款申请审批', exact: true }).click()
      await expect(page).toHaveURL(/\/v2\/approval\/instances\/81\?returnTab=todo$/)
      const detailDialog = page.getByRole('dialog', { name: '审批详情' })
      await expect(detailDialog).toBeVisible()
      await expect(detailDialog).toHaveClass(/v2-detail-dialog/)
      await expect(detailDialog.getByText('付款申请审批', { exact: true })).toBeVisible()
      await expect(detailDialog.getByText('PAY-2026-001', { exact: true })).toBeVisible()
      await expect(detailDialog.locator('.v2-glass-button')).not.toHaveCount(0)
      expect(
        await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth),
      ).toBe(true)

      const axe = await new AxeBuilder({ page }).include('.workflow-page').analyze()
      expect(
        axe.violations.filter((item) => ['serious', 'critical'].includes(item.impact ?? '')),
      ).toEqual([])
      await page.getByRole('button', { name: '关闭审批详情', exact: true }).click()
      await expect(page).toHaveURL(/\/v2\/approval\/todo$/)
    }

    expect(runtimeErrors).toEqual([])
  })

  test('reject validation remains inside action dialog and blocks mutation', async ({ page }) => {
    await installApprovalMock(page)
    let mutationCount = 0
    await page.route('**/api/workflow/tasks/91/reject', (route) => {
      mutationCount += 1
      return fulfill(route, null)
    })

    await page.goto('/v2/approval/instances/81?returnTab=todo')
    await page.getByRole('button', { name: '驳回', exact: true }).click()
    const dialog = page.getByRole('dialog', { name: '驳回' })
    await dialog.getByRole('button', { name: '确认提交', exact: true }).click()
    await expect(dialog.getByText('驳回必须填写原因', { exact: true })).toBeVisible()
    await expect(dialog.locator('textarea')).toHaveAttribute('aria-invalid', 'true')
    expect(mutationCount).toBe(0)
  })
})
