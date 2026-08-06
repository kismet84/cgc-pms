import { expect, test } from '@playwright/test'

test('ordinary user completes profile, password, preferences and help self-service', async ({
  page,
}) => {
  let currentUser = {
    tenantId: '0',
    userId: '7',
    username: 'demo.user',
    realName: '普通用户',
    phone: '13800000000',
    email: 'user@example.com',
    avatar: '',
    roles: ['USER'],
    permissions: [],
  }
  let preferences = {
    sidebarCollapsed: false,
    notificationEnabled: true,
    theme: 'light',
    tableDensity: 'middle',
  }
  const preferenceCalls: string[] = []

  await page
    .context()
    .addCookies([{ name: 'XSRF-TOKEN', value: 'e2e-csrf', url: 'http://127.0.0.1:5173' }])
  await page.route('**/api/**', async (route) => {
    const request = route.request()
    const url = new URL(request.url())
    const path = url.pathname
    const success = (data: unknown) =>
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ code: '0', message: 'success', traceId: 'e2e', data }),
      })

    if (path.endsWith('/api/auth/userinfo')) return success(currentUser)
    if (path.endsWith('/api/profile/password')) {
      const body = request.postDataJSON() as Record<string, unknown>
      expect(request.headers()['x-xsrf-token']).toBe('e2e-csrf')
      expect(body).toEqual({ oldPassword: 'old-password', newPassword: 'NewSecure1!' })
      return success(null)
    }
    if (path.endsWith('/api/profile/preferences')) {
      if (request.method() === 'GET') {
        preferenceCalls.push('GET')
        return success(preferences)
      }
      expect(request.headers()['x-xsrf-token']).toBe('e2e-csrf')
      preferenceCalls.push('PUT')
      preferences = request.postDataJSON() as typeof preferences
      return success(preferences)
    }
    if (path.endsWith('/api/profile')) {
      expect(request.headers()['x-xsrf-token']).toBe('e2e-csrf')
      const body = request.postDataJSON() as Record<string, unknown>
      expect(body).not.toHaveProperty('username')
      expect(body).not.toHaveProperty('roles')
      currentUser = { ...currentUser, ...body }
      return success(currentUser)
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

  await page.goto('/profile?source=deep-link#details')
  await expect(page).toHaveURL(/\/profile\?source=deep-link#details$/)
  await expect(page.getByRole('heading', { level: 1, name: '个人资料' })).toBeVisible()
  await page.getByLabel('姓名').fill('资料已回读')
  await page.getByRole('button', { name: '保存资料' }).click()
  await expect(page.getByText('当前账号信息已刷新。')).toBeVisible()

  await page.getByLabel('旧密码').fill('old-password')
  await page.getByLabel('新密码', { exact: true }).fill('NewSecure1!')
  await page.getByLabel('确认新密码').fill('NewSecure1!')
  await page.getByRole('button', { name: '修改密码' }).click()
  await expect(page.getByLabel('旧密码')).toHaveValue('')
  await expect(page.getByLabel('新密码', { exact: true })).toHaveValue('')
  await expect(page.getByLabel('确认新密码')).toHaveValue('')

  await page.getByRole('link', { name: '偏好设置' }).click()
  await expect(page).toHaveURL(/\/settings$/)
  await page.getByLabel('默认收起侧栏').check()
  await page.getByRole('button', { name: '保存偏好' }).click()
  await expect(page.getByText('偏好设置已刷新。')).toBeVisible()
  expect(preferenceCalls.filter((method) => method === 'PUT')).toHaveLength(1)
  expect(preferenceCalls.slice(-2)).toEqual(['PUT', 'GET'])

  await page.getByRole('link', { name: '使用帮助' }).click()
  await expect(page.getByRole('heading', { level: 1, name: '使用帮助' })).toBeVisible()
  await expect(page.getByText('HttpOnly Cookie')).toBeVisible()
  await expect(page.getByText(/400-|每日凌晨|保留30天|v1\.0\.0|Ctrl\+S|Ctrl\+Enter/)).toHaveCount(0)
})
