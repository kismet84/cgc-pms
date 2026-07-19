import { expect, test } from '@playwright/test'

const runLiveAuth = process.env.V2_LIVE_AUTH === '1'
const liveClientIpSeed = (process.pid + Date.now()) % 200
const liveIdentities = [
  {
    username: 'admin',
    visibleName: '平台管理员',
    clientIp: '',
  },
]

if (process.env.V2_LIVE_ORDINARY_USER && process.env.V2_LIVE_ORDINARY_NAME) {
  liveIdentities.push({
    username: process.env.V2_LIVE_ORDINARY_USER,
    visibleName: process.env.V2_LIVE_ORDINARY_NAME,
    clientIp: '',
  })
}

function liveClientIp(offset: number): string {
  return `192.0.2.${20 + ((liveClientIpSeed + offset) % 200)}`
}

test.describe('V2 live local authentication', () => {
  test.skip(!runLiveAuth, 'Set V2_LIVE_AUTH=1 only against the local test/demo runtime')

  test('redirects an unauthenticated browser to login', async ({ page }) => {
    await page.context().setExtraHTTPHeaders({ 'X-Forwarded-For': liveClientIp(0) })
    await page.goto('/v2/session')

    await expect(page).toHaveURL(/\/v2\/login\?redirect=/)
    await expect(page.getByRole('heading', { name: '登录新版工作台' })).toBeVisible()
  })

  for (const [index, configuredIdentity] of liveIdentities.entries()) {
    const identity = { ...configuredIdentity, clientIp: liveClientIp(index + 1) }
    test(`${identity.username} restores and terminates a cookie session`, async ({ page }) => {
      await page.context().setExtraHTTPHeaders({ 'X-Forwarded-For': identity.clientIp })
      let logoutHadCsrfHeader = false
      page.on('request', (request) => {
        if (request.url().endsWith('/api/auth/logout')) {
          logoutHadCsrfHeader = request.headers()['x-xsrf-token'] !== undefined
        }
      })
      const loginResponse = await page.goto(
        `/api/auth/dev-login?username=${encodeURIComponent(identity.username)}`,
      )
      expect(loginResponse?.ok()).toBe(true)

      await page.goto('/v2/session')
      await expect(page).not.toHaveURL(/\/v2\/(?:login|session)/)
      await expect(page.getByText('业务页面尚未迁移')).toBeVisible()
      await expect(page.getByText(identity.visibleName)).toBeVisible()

      await page.reload()
      await expect(page.getByText('业务页面尚未迁移')).toBeVisible()

      if (identity.username === 'admin') {
        await page.goto('/v2/system/users')
        await expect(page.getByRole('heading', { level: 1, name: '访问控制' })).toBeVisible()
      } else {
        await page.goto('/v2/system/users')
        await expect(page).toHaveURL(/\/v2\/no-access\?from=/)
        await expect(page.getByText('访问已阻断')).toBeVisible()
      }

      await page.getByRole('button', { name: '退出' }).click()
      await expect(page).toHaveURL(/\/v2\/login$/)
      expect(logoutHadCsrfHeader).toBe(true)

      await page.goto('/v2/session')
      await expect(page).toHaveURL(/\/v2\/login\?redirect=/)
    })
  }

  test('rejects an unknown identity without retaining the password', async ({ page }) => {
    await page.context().setExtraHTTPHeaders({ 'X-Forwarded-For': liveClientIp(3) })
    let loginHadCsrfHeader = false
    page.on('request', (request) => {
      if (request.url().endsWith('/api/auth/login')) {
        loginHadCsrfHeader = request.headers()['x-xsrf-token'] !== undefined
      }
    })
    await page.goto('/v2/login')
    await page.getByLabel('用户名').fill('__v2_denied_identity__')
    await page.getByLabel('密码').fill('invalid-credential-053002')
    await page.getByRole('button', { name: '登录' }).click()

    expect(loginHadCsrfHeader).toBe(true)
    await expect(page.getByText('用户名或密码错误')).toBeVisible()
    await expect(page.getByLabel('密码')).toHaveValue('')
    await expect(page).toHaveURL(/\/v2\/login$/)
  })

  test('keeps the login shell usable at desktop and mobile widths', async ({ page }) => {
    await page.context().setExtraHTTPHeaders({ 'X-Forwarded-For': liveClientIp(4) })
    const visualOutput = process.env.V2_VISUAL_OUTPUT
    const runtimeErrors: string[] = []
    const unexpectedResponses: string[] = []
    let observedAnonymousProbe = false
    page.on('response', (response) => {
      if (response.url().endsWith('/api/auth/userinfo') && response.status() === 401) {
        observedAnonymousProbe = true
      } else if (response.status() >= 400) {
        unexpectedResponses.push(`${response.status()} ${new URL(response.url()).pathname}`)
      }
    })
    page.on('console', (message) => {
      if (message.type() === 'error' || message.type() === 'warning') {
        runtimeErrors.push(
          `${message.type()} ${message.location().url || '<unknown>'}: ${message.text()}`,
        )
      }
    })
    page.on('pageerror', (error) => runtimeErrors.push(`pageerror: ${error.message}`))

    await page.setViewportSize({ width: 1440, height: 900 })
    await page.goto('/v2/login')
    await expect(page.getByRole('heading', { name: '登录新版工作台' })).toBeVisible()
    expect(
      await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth),
    ).toBe(true)
    if (visualOutput) {
      await page.screenshot({ path: `${visualOutput}/login-desktop.png`, fullPage: true })
    }

    await page.setViewportSize({ width: 390, height: 844 })
    await expect(page.getByText('认证方式', { exact: true })).toBeHidden()
    await expect(page.getByRole('button', { name: '登录' })).toBeVisible()
    expect(
      await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth),
    ).toBe(true)
    if (visualOutput) {
      await page.screenshot({ path: `${visualOutput}/login-mobile.png`, fullPage: true })
    }
    expect(observedAnonymousProbe).toBe(true)
    expect(unexpectedResponses).toEqual([])
    expect(
      runtimeErrors.filter(
        (message) =>
          !(
            observedAnonymousProbe &&
            message.endsWith(
              ': Failed to load resource: the server responded with a status of 401 (Unauthorized)',
            )
          ) &&
          message !==
            'error http://127.0.0.1:5174/favicon.ico: Failed to load resource: the server responded with a status of 404 (Not Found)',
      ),
    ).toEqual([])
  })
})
