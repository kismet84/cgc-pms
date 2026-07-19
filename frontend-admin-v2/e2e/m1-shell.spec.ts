import AxeBuilder from '@axe-core/playwright'
import { expect, test, type Page } from '@playwright/test'

type Identity = 'admin' | 'ordinary' | 'denied' | 'anonymous'

const users = {
  admin: {
    userId: '1',
    username: 'admin',
    realName: '平台管理员',
    roles: ['SUPER_ADMIN'],
    permissions: ['*'],
  },
  ordinary: {
    userId: '2',
    username: 'project.viewer',
    realName: '项目查看人',
    roles: ['USER'],
    permissions: ['project:query'],
  },
  denied: {
    userId: '3',
    username: 'report.viewer',
    realName: '报表查看人',
    roles: ['USER'],
    permissions: [],
  },
} as const

const anonymousEnvelope = {
  code: 'AUTH_TOKEN_INVALID',
  message: 'unauthorized',
  traceId: 'm1-e2e',
  data: null,
}

async function installIdentity(page: Page, readIdentity: () => Identity): Promise<void> {
  await page.route('**/api/auth/userinfo', (route) => {
    const identity = readIdentity()
    if (identity === 'anonymous') {
      return route.fulfill({
        status: 401,
        contentType: 'application/json',
        body: JSON.stringify(anonymousEnvelope),
      })
    }
    return route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        code: '0',
        message: 'success',
        traceId: 'm1-e2e',
        data: users[identity],
      }),
    })
  })
  await page.route('**/api/auth/refresh', (route) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(anonymousEnvelope),
    }),
  )
}

async function expectNoHorizontalOverflow(page: Page): Promise<void> {
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(
    true,
  )
}

async function expectNoSeriousAxeViolations(page: Page): Promise<void> {
  const results = await new AxeBuilder({ page }).analyze()
  expect(
    results.violations.filter((violation) =>
      ['serious', 'critical'].includes(violation.impact ?? ''),
    ),
  ).toEqual([])
}

test('keeps login and authenticated shell accessible at 1440, 1024 and 390', async ({ page }) => {
  let identity: Identity = 'anonymous'
  await installIdentity(page, () => identity)
  const runtimeProblems: string[] = []
  const businessRequests: string[] = []
  page.on('console', (message) => {
    if (message.type() === 'warning' || message.type() === 'error') {
      const location = message.location().url
      const expectedAnonymous401 =
        message.type() === 'error' &&
        message.text().includes('401 (Unauthorized)') &&
        (location.includes('/api/auth/userinfo') || identity === 'anonymous')
      if (!expectedAnonymous401) {
        runtimeProblems.push(`${message.type()} ${location || '<unknown>'}: ${message.text()}`)
      }
    }
  })
  page.on('pageerror', (error) => runtimeProblems.push(`pageerror: ${error.message}`))
  page.on('request', (request) => {
    const path = new URL(request.url()).pathname
    if (path.startsWith('/api/') && !path.startsWith('/api/auth/')) businessRequests.push(path)
  })

  for (const viewport of [
    { name: 'desktop', width: 1440, height: 900 },
    { name: 'compact', width: 1024, height: 768 },
    { name: 'mobile', width: 390, height: 844 },
  ]) {
    await page.setViewportSize(viewport)
    identity = 'anonymous'
    await page.goto('/v2/dashboard')
    await expect(page).toHaveURL(/\/v2\/login\?redirect=/)
    await expect(page.getByRole('heading', { name: '登录新版工作台' })).toBeVisible()
    await expectNoHorizontalOverflow(page)
    await expectNoSeriousAxeViolations(page)

    identity = 'admin'
    await page.goto('/v2/dashboard')
    await expect(page.getByRole('heading', { level: 1, name: '经营驾驶舱' })).toBeVisible()
    await expect(page.getByRole('main')).toBeVisible()
    await expectNoHorizontalOverflow(page)
    await expectNoSeriousAxeViolations(page)

    await page.getByRole('button', { name: '打开通知中心占位' }).click()
    await expect(page.getByRole('dialog', { name: '通知中心' })).toBeVisible()
    await expect(page.getByRole('heading', { name: '暂无壳级通知' })).toBeVisible()
    await expect(page.getByText('此处不显示模拟数量或业务消息')).toBeVisible()
    await page.getByRole('button', { name: '关闭对话框' }).click()

    if (viewport.name === 'desktop') {
      await page.evaluate(() => {
        document.body.tabIndex = -1
        document.body.focus()
        document.body.removeAttribute('tabindex')
      })
      await page.keyboard.press('Tab')
      await expect(page.getByRole('link', { name: '跳到主要内容' })).toBeFocused()
    }
    if (viewport.name === 'compact') {
      await expect(page.locator('.app-shell__workspaces').first()).toBeHidden()
      await expect(page.getByRole('link', { name: '供应链与物资' })).toBeVisible()
    }
    if (viewport.name === 'mobile') {
      const menu = page.getByRole('button', { name: '打开导航' })
      await menu.click()
      await expect(page.getByRole('button', { name: '关闭导航' }).last()).toBeFocused()
      await expect(page.getByRole('button', { name: '退出登录' })).toBeVisible()
      await page.keyboard.press('Escape')
      await expect(menu).toBeFocused()
      await menu.click()
      await page.getByRole('link', { name: '供应链与物资' }).click()
      await expect(page).toHaveURL(/\/v2\/supplier-sourcing$/)
      await expect(page.getByRole('main')).toBeFocused()
      await expect(page.getByRole('main')).not.toHaveCSS('outline-style', 'none')
    }
  }

  expect(businessRequests).toEqual([])
  expect(runtimeProblems).toEqual([])
})

test('honors reduced motion and distinguishes identity, 403 and 404 states', async ({ page }) => {
  let identity: Identity = 'admin'
  await installIdentity(page, () => identity)
  await page.emulateMedia({ reducedMotion: 'reduce' })
  await page.setViewportSize({ width: 390, height: 844 })
  await page.goto('/v2/dashboard')
  await expect(page.locator('.app-shell__sidebar')).toHaveCSS('transition-duration', '0.001s')

  identity = 'ordinary'
  await page.goto('/v2/project/list')
  await expect(page.getByRole('heading', { level: 1, name: '项目管理' })).toBeVisible()
  await expect(page.locator('[data-domain]')).toHaveCount(2)

  identity = 'denied'
  await page.goto('/v2/contract/ledger')
  await expect(page).toHaveURL(/\/v2\/forbidden\?from=/)
  await expect(page.getByRole('heading', { level: 1, name: '无权访问此页面' })).toBeVisible()
  await expectNoSeriousAxeViolations(page)

  identity = 'admin'
  await page.goto('/v2/not-a-real-route')
  await expect(page).toHaveURL(/\/v2\/not-a-real-route$/)
  await expect(page.getByRole('heading', { level: 1, name: '页面不存在' })).toBeVisible()
  await expectNoSeriousAxeViolations(page)

  identity = 'anonymous'
  await page.goto('/v2/project/list')
  await expect(page).toHaveURL(/\/v2\/login\?redirect=/)
})
