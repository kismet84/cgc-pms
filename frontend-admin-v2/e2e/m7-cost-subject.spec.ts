import { expect, test } from '@playwright/test'
import type { Route } from '@playwright/test'

test('cost-subject deep links render server facts and preserve root redirect state', async ({
  page,
}) => {
  const success = (route: Route, data: unknown) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ code: '0', message: 'success', traceId: 'e2e', data }),
    })

  await page.route('**/api/**', async (route) => {
    const path = new URL(route.request().url()).pathname
    if (path.endsWith('/api/auth/userinfo')) {
      return success(route, {
        userId: '7',
        username: 'cost.user',
        roles: ['USER'],
        permissions: [
          'cost:query',
          'cost:subject:mapping:query',
          'cost:subject:rule:query',
          'cost:subject:scope:query',
          'cost:subject:audit:query',
        ],
      })
    }
    if (path.endsWith('/api/cost-subjects/tree')) {
      return success(route, [
        {
          id: '1',
          parentId: '0',
          subjectCode: 'COST',
          subjectName: '服务端成本域',
          subjectType: 'ROOT',
          accountCategory: 'COST',
          level: 1,
          sortOrder: 1,
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
    if (path.endsWith('/api/cost-subject-v2/rules')) {
      return success(route, [
        {
          id: '3',
          ruleCode: 'RULE-001',
          versionCode: 'MAP-2026',
          sourceType: 'CONTRACT',
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
    if (path.endsWith('/api/cost-subject-v2/finance-allocations')) return success(route, [])
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

  await page.goto('/v2/cost/subject?source=e2e#mapping')
  await expect(page).toHaveURL(/\/v2\/cost\/subject\/taxonomy\?source=e2e#mapping$/)
  await expect(page.getByRole('heading', { level: 1, name: '成本科目体系' })).toBeVisible()
  await expect(page.getByText('服务端成本域')).toBeVisible()

  await page.goto('/v2/cost/subject/rules')
  await expect(page.getByRole('heading', { level: 1, name: '归集规则与映射版本' })).toBeVisible()
  await expect(page.getByText('服务端映射版本')).toBeVisible()
  await expect(page.getByText('RULE-001')).toBeVisible()

  await page.goto('/v2/cost/subject/scope')
  await expect(page.getByRole('heading', { level: 1, name: '项目适用与目标成本' })).toBeVisible()

  await page.goto('/v2/cost/subject/trace')
  await expect(page.getByRole('heading', { level: 1, name: '影响与转入追踪' })).toBeVisible()
  await expect(page.getByText('125.2300')).toBeVisible()
  await expect(page.getByText('投标成本转入', { exact: true })).toHaveCount(0)
})
