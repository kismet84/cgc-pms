import { expect, test } from '@playwright/test'

const anonymous = {
  code: 'AUTH_TOKEN_INVALID',
  message: 'unauthorized',
  traceId: 'e2e',
  data: null,
}
const userInfo = {
  tenantId: '1001',
  userId: '1',
  username: 'admin',
  realName: '平台管理员',
  roles: ['SUPER_ADMIN'],
  permissions: ['*'],
}

test.beforeEach(async ({ page }) => {
  await page.route('**/api/**', (route) => {
    if (route.request().url().includes('/api/auth/')) return route.fallback()
    return route.fulfill({
      status: 500,
      contentType: 'application/json',
      body: JSON.stringify({
        code: 'E2E_API_UNSTUBBED',
        message: 'auth spec does not load business data',
        traceId: 'e2e',
        data: null,
      }),
    })
  })
})

test('redirects an anonymous user to the V2 login page', async ({ page }) => {
  await page.route('**/api/auth/userinfo', (route) =>
    route.fulfill({
      status: 401,
      contentType: 'application/json',
      body: JSON.stringify(anonymous),
    }),
  )
  await page.route('**/api/auth/refresh', (route) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(anonymous),
    }),
  )

  await page.goto('/session')

  await expect(page).toHaveURL(/\/login\?redirect=/)
  await expect(page.getByRole('heading', { name: '登录新版工作台' })).toBeVisible()
})

test('logs in through the existing contract and clears the password field', async ({ page }) => {
  await page.route('**/api/auth/userinfo', (route) =>
    route.fulfill({
      status: 401,
      contentType: 'application/json',
      body: JSON.stringify(anonymous),
    }),
  )
  await page.route('**/api/auth/refresh', (route) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(anonymous),
    }),
  )
  await page.route('**/api/auth/login', async (route) => {
    const payload = route.request().postDataJSON() as { tenantId?: number; username?: string }
    expect(payload.tenantId).toBe(1001)
    expect(payload.username).toBe('admin')
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ code: '0', message: 'success', traceId: 'e2e', data: { userInfo } }),
    })
  })

  await page.goto('/login')
  await page.getByLabel('租户ID').fill('1001')
  await page.getByLabel('用户名').fill('admin')
  await page.getByLabel('密码').fill('local-password')
  await page.getByRole('button', { name: '登录' }).click()

  await expect(page).toHaveURL(/\/dashboard$/)
  await expect(page.getByRole('heading', { level: 1, name: '经营驾驶舱' })).toBeVisible()
  await expect(page.getByRole('banner').getByText('平台管理员')).toBeVisible()
  await expect(page.getByText('local-password')).toHaveCount(0)
})

test('filters navigation and blocks deep links for an ordinary permission sample', async ({
  page,
}) => {
  await page.route('**/api/auth/userinfo', (route) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        code: '0',
        message: 'success',
        traceId: 'e2e',
        data: { ...userInfo, roles: ['USER'], permissions: ['project:query'] },
      }),
    }),
  )

  await page.goto('/project/list')
  await expect(page.locator('[data-domain]')).toHaveCount(2)
  await expect(page.locator('[data-domain="workbench"]')).toBeVisible()
  await expect(page.locator('[data-domain="delivery"]')).toBeVisible()
  await expect(page.getByText('商务合约', { exact: true })).toHaveCount(0)

  await page.goto('/project/42/overview?projectId=unknown&period=2026-07')
  await expect(page).toHaveURL(/\/project\/42\/overview\?projectId=unknown&period=2026-07$/)
  await page.reload()
  await expect(page).toHaveURL(/\/project\/42\/overview\?projectId=unknown&period=2026-07$/)

  await page.goto('/contract/ledger')
  await expect(page).toHaveURL(/\/forbidden\?from=/)
  await expect(page.getByRole('heading', { name: '无权访问此页面' })).toBeVisible()
})

test('keeps a no-permission user in the non-business workbench shell only', async ({ page }) => {
  await page.route('**/api/auth/userinfo', (route) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        code: '0',
        message: 'success',
        traceId: 'e2e',
        data: { ...userInfo, roles: ['USER'], permissions: [] },
      }),
    }),
  )

  await page.goto('/session')
  await expect(page).toHaveURL(/\/approval\/todo$/)
  await expect(page.locator('[data-domain]')).toHaveCount(1)
  await expect(page.getByText('我的工作', { exact: true }).first()).toBeVisible()
})

test('keeps Legacy 403 and unknown deep links distinct', async ({ page }) => {
  await page.route('**/api/auth/userinfo', (route) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ code: '0', message: 'success', traceId: 'e2e', data: userInfo }),
    }),
  )

  await page.goto('/403?from=%2Fsystem%2Fusers#denied')
  await expect(page).toHaveURL(/\/forbidden\?from=\/system\/users#denied$/)
  await expect(page.getByRole('heading', { name: '无权访问此页面' })).toBeVisible()
  await expect(page.getByText('已阻断路径：/system/users')).toBeVisible()

  await page.goto('/definitely-not-a-v2-route')
  await expect(page).toHaveURL(/\/definitely-not-a-v2-route$/)
  await expect(page.getByRole('heading', { name: '页面不存在' })).toBeVisible()
})
