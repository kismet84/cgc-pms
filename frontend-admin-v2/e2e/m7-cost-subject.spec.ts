import { expect, test } from '@playwright/test'
import type { Route } from '@playwright/test'

test('cost-subject deep links render server facts and preserve root redirect state', async ({
  page,
}) => {
  const consoleErrors: string[] = []
  let overridePayload: Record<string, unknown> | null = null
  page.on('console', (message) => {
    if (message.type() === 'error') consoleErrors.push(message.text())
  })
  const success = (route: Route, data: unknown) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ code: '0', message: 'success', traceId: 'e2e', data }),
    })

  await page.route('**/api/**', async (route) => {
    const path = new URL(route.request().url()).pathname
    if (
      path.endsWith('/api/cost-subject-v2/classification-overrides') &&
      route.request().method() === 'POST'
    ) {
      overridePayload = route.request().postDataJSON() as Record<string, unknown>
      return success(route, '900')
    }
    if (path.endsWith('/api/auth/userinfo')) {
      return success(route, {
        tenantId: '0',
        userId: '7',
        username: 'cost.user',
        roles: ['USER'],
        permissions: [
          'cost:query',
          'cost:subject:mapping:query',
          'cost:subject:rule:query',
          'cost:subject:scope:query',
          'cost:subject:audit:query',
          'cost:subject:mapping:edit',
          'cost:subject:rule:edit',
          'cost:subject:scope:edit',
          'cost:project-config:edit',
          'cost:project-config:submit',
          'cost:rule-plan:submit',
          'cost:subject:bid-transfer',
          'cost:subject:transfer:submit',
          'cost:subject:finance-allocate',
          'cost:subject:allocation:submit',
          'cost:classification:override',
          'cost:recalculation:edit',
          'cost:recalculation:submit',
          'cost:reversal:edit',
          'cost:reversal:submit',
          'business:amount:view',
        ],
      })
    }
    if (path.endsWith('/api/profile/preferences')) {
      return success(route, {
        sidebarCollapsed: false,
        notificationEnabled: true,
        theme: 'light',
        tableDensity: 'middle',
      })
    }
    if (path.endsWith('/api/project-context/options')) {
      return success(route, [
        {
          id: '101',
          projectCode: 'P-2026-001',
          projectName: '服务端成本项目',
          status: 'ACTIVE',
        },
      ])
    }
    if (path.endsWith('/api/cost-subjects/tree')) {
      return success(route, [
        {
          id: '1',
          parentId: '0',
          subjectCode: '5401',
          subjectName: '工程成本',
          subjectType: 'ROOT',
          accountCategory: 'COST',
          level: 1,
          sortOrder: 1,
          status: 'ENABLE',
          children: [
            {
              id: '11',
              parentId: '1',
              subjectCode: '5401.01',
              subjectName: '直接工程费',
              subjectType: 'GROUP',
              accountCategory: 'COST',
              level: 2,
              sortOrder: 1,
              status: 'ENABLE',
              children: [
                {
                  id: '111',
                  parentId: '11',
                  subjectCode: '5401.01.01',
                  subjectName: '人工费',
                  subjectType: 'DETAIL',
                  accountCategory: 'COST',
                  level: 3,
                  sortOrder: 1,
                  status: 'ENABLE',
                  children: [],
                },
              ],
            },
          ],
        },
        {
          id: '2',
          parentId: '0',
          subjectCode: '1122-AR',
          subjectName: '应收账款',
          subjectType: 'GENERAL_LEDGER',
          accountCategory: 'ASSET',
          level: 1,
          sortOrder: 20,
          status: 'ENABLE',
          children: [],
        },
      ])
    }
    if (path.endsWith('/api/cost-subject-v2/mapping-versions')) {
      return success(route, [
        {
          id: '2',
          versionCode: 'MAP-2026',
          versionName: '服务端映射版本',
          status: 'DRAFT',
          itemCount: 3,
        },
      ])
    }
    if (path.endsWith('/api/cost-subject-v2/form-options')) {
      return success(route, {
        projects: [
          {
            id: '101',
            projectCode: 'P-2026-001',
            projectName: '服务端成本项目',
            projectStatus: 'ACTIVE',
          },
        ],
        costSubjects: [
          { id: '111', subjectCode: '5401.01.01', subjectName: '人工费', status: 'ENABLE' },
        ],
        rulePlans: [
          {
            id: '20',
            versionCode: 'MAP-ACTIVE',
            versionName: '当前启用方案',
            status: 'ACTIVE',
          },
        ],
        bidCosts: [
          {
            id: '30',
            bidCode: 'BID-WON-001',
            bidProjectName: '已中标工程',
            projectId: '101',
            projectCode: 'P-2026-001',
            projectName: '服务端成本项目',
          },
        ],
        targetVersions: [
          {
            id: '40',
            projectId: '101',
            versionNo: 'TC-V2',
            versionName: '待转入目标成本',
            approvalStatus: 'DRAFT',
            status: 'DRAFT',
            totalTargetAmount: '500000',
          },
        ],
        financeSources: [],
        pendingClassifications: [
          {
            caseId: '501',
            snapshotId: null,
            sourceType: 'MAT_RECEIPT',
            sourceId: '601',
            sourceItemId: '0',
            projectId: '101',
            matchedSubjectCode: '5401.01.01',
            matchedSubjectName: '人工费',
            errorCode: 'COST_SUBJECT_UNCLASSIFIED',
            errorMessage: '未命中成本规则',
          },
        ],
      })
    }
    if (path.endsWith('/api/cost-subject-v2/project-config')) {
      return success(route, {
        project: {
          id: '101',
          projectCode: 'P-2026-001',
          projectName: '服务端成本项目',
          projectStatus: 'ACTIVE',
          mainContractCode: 'CT-001',
          mainContractName: '施工总承包合同',
          targetVersionNo: 'TC-V1',
          targetVersionName: '当前目标成本',
          targetAmount: '500000',
        },
        subjects: [
          {
            id: '111',
            subjectCode: '5401.01.01',
            subjectName: '人工费',
            scopeState: 'INHERITED',
            status: 'ENABLE',
            costFactCount: 2,
          },
        ],
        requests: [],
      })
    }
    if (path.endsWith('/api/cost-subject-v2/rules')) {
      return success(route, [
        {
          id: '3',
          ruleCode: 'RULE-001',
          versionCode: 'MAP-2026',
          sourceType: 'CT_CONTRACT',
          businessCategory: '*',
          costSubjectId: '1',
          subjectCode: 'COST',
          subjectName: '服务端成本域',
          priority: 100,
          status: 'ENABLE',
          effectiveFrom: '2026-07-27',
        },
      ])
    }
    if (path.endsWith('/api/cost-subject-v2/bid-transfers')) {
      return success(route, [
        {
          id: '4',
          transferCode: 'BT-001',
          bidProjectName: '服务端投标项目',
          versionNo: 'V1',
          totalAmount: '125.2300',
          status: 'POSTED',
          approvalInstanceId: 'A-1',
        },
      ])
    }
    if (path.endsWith('/api/cost-subject-v2/bid-transfer-requests')) return success(route, [])
    if (path.endsWith('/api/cost-subject-v2/finance-allocation-requests')) return success(route, [])
    if (path.endsWith('/api/cost-subject-v2/finance-allocations')) return success(route, [])
    if (path.endsWith('/api/cost-subject-v2/recalculation-batches')) return success(route, [])
    if (path.endsWith('/api/cost-subject-v2/reversal-requests')) return success(route, [])
    return route.fulfill({
      status: 500,
      contentType: 'application/json',
      body: JSON.stringify({
        code: 'E2E_API_UNSTUBBED',
        message: path,
        traceId: 'e2e',
        data: null,
      }),
    })
  })

  await page.setViewportSize({ width: 1512, height: 1114 })
  await page.goto('/cost/subject?source=e2e#mapping')
  await expect(page).toHaveURL(/\/cost\/subject\/taxonomy\?source=e2e#mapping$/)
  await expect(page.getByRole('heading', { level: 1, name: '会计科目' })).toBeVisible()
  await expect(page.getByRole('region', { name: '1. 科目大类' })).toContainText('资产类')
  const catalog = page.getByRole('region', { name: '2. 科目目录' })
  await expect(catalog).toContainText('5401.01.01')
  await expect(catalog).toContainText('1122-AR')
  await catalog.getByText('应收账款', { exact: true }).click()
  await expect(page.getByRole('region', { name: '3. 科目详情' })).toContainText('1122-AR')

  await page.goto('/cost/subject/rules')
  await expect(page.getByRole('heading', { level: 1, name: '成本规则方案' })).toBeVisible()
  await expect(page.getByText('服务端映射版本')).toBeVisible()
  await expect(page.getByText('RULE-001')).toBeVisible()
  await page.getByRole('button', { name: '新建方案' }).click()
  const planDialog = page.getByRole('dialog', { name: '新建成本规则方案' })
  await expect(planDialog).toBeVisible()
  await expect
    .poll(() => planDialog.evaluate((element) => element.scrollWidth <= element.clientWidth))
    .toBe(true)
  await planDialog.getByRole('button', { name: '取消' }).click()

  await page.goto('/cost/subject/scope')
  await expect(page.getByRole('heading', { level: 1, name: '项目成本配置' })).toBeVisible()
  await expect(page.getByText('P-2026-001 · 服务端成本项目', { exact: true })).toBeVisible()
  await expect(page.getByText('企业继承')).toBeVisible()

  await page.goto('/cost/subject/trace')
  await expect(page.getByRole('heading', { level: 1, name: '成本追溯与转入' })).toBeVisible()
  for (const label of ['科目影响', '项目成本对账', '投标成本转入', '财务费用分摊']) {
    await expect(page.getByRole('tab', { name: label })).toBeVisible()
  }
  await expect(page.getByText('未命中成本规则')).toBeVisible()
  await page.getByRole('button', { name: '财务覆盖' }).click()
  const overrideDialog = page.getByRole('dialog', { name: '财务覆盖成本归类' })
  await expect(overrideDialog).toBeVisible()
  await expect
    .poll(() => overrideDialog.evaluate((element) => element.scrollWidth <= element.clientWidth))
    .toBe(true)
  await overrideDialog.getByLabel('目标末级成本科目').selectOption('111')
  await overrideDialog.getByLabel('覆盖原因').fill('财务复核来源后覆盖')
  await overrideDialog.getByRole('button', { name: '保存覆盖' }).click()
  await expect(overrideDialog).toBeHidden()
  expect(overridePayload).toEqual({
    caseId: '501',
    snapshotId: null,
    costSubjectId: '111',
    reason: '财务复核来源后覆盖',
  })
  await page.getByRole('tab', { name: '投标成本转入' }).click()
  await expect(page.getByText('¥125.23')).toBeVisible()
  await page.setViewportSize({ width: 985, height: 732 })
  await expect
    .poll(() =>
      page.locator('main').evaluate((element) => element.scrollWidth <= element.clientWidth),
    )
    .toBe(true)
  expect(consoleErrors).toEqual([])
})
