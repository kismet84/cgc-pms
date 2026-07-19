import { expect, test } from '@playwright/test'

const anonymous = {
  code: 'AUTH_TOKEN_INVALID',
  message: 'unauthorized',
  traceId: 'e2e',
  data: null,
}
const userInfo = {
  userId: '1',
  username: 'admin',
  realName: '平台管理员',
  roles: ['SUPER_ADMIN'],
  permissions: ['*'],
}

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

  await page.goto('/v2/session')

  await expect(page).toHaveURL(/\/v2\/login\?redirect=/)
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
    const payload = route.request().postDataJSON() as { username?: string }
    expect(payload.username).toBe('admin')
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ code: '0', message: 'success', traceId: 'e2e', data: { userInfo } }),
    })
  })

  await page.goto('/v2/login')
  await page.getByLabel('用户名').fill('admin')
  await page.getByLabel('密码').fill('local-password')
  await page.getByRole('button', { name: '登录' }).click()

  await expect(page).toHaveURL(/\/v2\/dashboard$/)
  await expect(page.getByRole('heading', { level: 1, name: '经营驾驶舱' })).toBeVisible()
  await expect(page.getByText('平台管理员')).toBeVisible()
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

  await page.goto('/v2/project/list')
  await expect(page.locator('[data-domain]')).toHaveCount(2)
  await expect(page.locator('[data-domain="workbench"]')).toBeVisible()
  await expect(page.locator('[data-domain="delivery"]')).toBeVisible()
  await expect(page.getByText('商务合约', { exact: true })).toHaveCount(0)

  await page.goto('/v2/project/42/overview?projectId=unknown&period=2026-07')
  await expect(page.getByText('project / 42', { exact: true })).toBeVisible()
  await expect(page.getByText('暂无可用项目', { exact: true }).last()).toBeVisible()
  await page.reload()
  await expect(page.getByText('project / 42', { exact: true })).toBeVisible()

  await page.goto('/v2/contract/ledger')
  await expect(page).toHaveURL(/\/v2\/forbidden\?from=/)
  await expect(page.getByRole('heading', { name: '无权访问此页面' })).toBeVisible()
})

test('keeps a no-permission user in the non-business report shell only', async ({ page }) => {
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

  await page.goto('/v2/session')
  await expect(page).toHaveURL(/\/v2\/dashboard\/reports$/)
  await expect(page.locator('[data-domain]')).toHaveCount(1)
  await expect(page.getByText('报表中心', { exact: true }).first()).toBeVisible()
})
