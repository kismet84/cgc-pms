import { expect, test } from '@playwright/test'
import type { Route } from '@playwright/test'

function success(route: Route, data: unknown) {
  return route.fulfill({
    status: 200,
    contentType: 'application/json',
    body: JSON.stringify({ code: '0', message: 'success', traceId: 'e2e', data }),
  })
}

test('ordinary role stays forbidden even with workflow process permission', async ({ page }) => {
  let templateRequests = 0
  await page.route('**/api/**', (route) => {
    const path = new URL(route.request().url()).pathname
    if (path.endsWith('/api/auth/userinfo')) {
      return success(route, {
        userId: '7',
        username: 'ordinary.user',
        roles: ['USER'],
        permissions: ['workflow:process:query'],
      })
    }
    if (path.endsWith('/api/workflow/templates')) templateRequests += 1
    return success(route, null)
  })

  await page.goto('/v2/approval/process?source=e2e#nodes')

  await expect(page).toHaveURL(/\/v2\/forbidden\?from=/)
  await expect(page.getByRole('heading', { name: '无权访问此页面' })).toBeVisible()
  expect(templateRequests).toBe(0)
})

test('administrator uses the documented admin permission override', async ({ page }) => {
  let templateRequests = 0
  await page.route('**/api/**', (route) => {
    const path = new URL(route.request().url()).pathname
    if (path.endsWith('/api/auth/userinfo')) {
      return success(route, {
        userId: '1',
        username: 'admin',
        roles: ['ADMIN'],
        permissions: [],
      })
    }
    if (path.endsWith('/api/workflow/templates')) templateRequests += 1
    return success(route, null)
  })

  await page.goto('/v2/approval/process')

  await expect(page).toHaveURL(/\/v2\/approval\/process$/)
  await expect.poll(() => templateRequests).toBe(1)
})

test('administrator with exact permission reads server workflow facts', async ({ page }) => {
  await page.route('**/api/**', (route) => {
    const path = new URL(route.request().url()).pathname
    if (path.endsWith('/api/auth/userinfo')) {
      return success(route, {
        userId: '1',
        username: 'admin',
        roles: ['ADMIN'],
        permissions: ['workflow:process:query'],
      })
    }
    if (path.endsWith('/api/workflow/templates')) {
      return success(route, {
        pageNo: 1,
        pageSize: 20,
        total: 1,
        records: [
          {
            id: '10',
            templateCode: 'FLOW-CONTRACT',
            templateName: '服务端合同审批',
            businessType: 'CONTRACT_APPROVAL',
            enabled: 1,
            amountMin: '100.2300',
            amountMax: '900.4500',
            nodeCount: 2,
          },
        ],
      })
    }
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

  await page.goto('/v2/approval/process?source=e2e#nodes')

  await expect(page).toHaveURL(/\/v2\/approval\/process\?source=e2e#nodes$/)
  await expect(page.getByRole('heading', { level: 1, name: '审批流程配置' })).toBeVisible()
  await expect(page.getByText('服务端合同审批')).toBeVisible()
})
