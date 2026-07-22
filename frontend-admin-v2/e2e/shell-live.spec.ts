import { expect, test } from '@playwright/test'

const runLiveShell = process.env.V2_LIVE_SHELL === '1'

test.describe('V2 live application shell', () => {
  test.skip(!runLiveShell, 'Set V2_LIVE_SHELL=1 only against the local test/demo runtime')

  test('keeps the admin dashboard responsive with selected-role business data', async ({
    page,
  }) => {
    const visualOutput = process.env.V2_VISUAL_OUTPUT
    const runtimeErrors: string[] = []
    const businessRequests: string[] = []

    page.on('console', (message) => {
      if (message.type() === 'error' || message.type() === 'warning') {
        runtimeErrors.push(
          `${message.type()} ${message.location().url || '<unknown>'}: ${message.text()}`,
        )
      }
    })
    page.on('pageerror', (error) => runtimeErrors.push(`pageerror: ${error.message}`))
    page.on('request', (request) => {
      const path = new URL(request.url()).pathname
      if (path.startsWith('/api/') && !path.startsWith('/api/auth/')) businessRequests.push(path)
    })

    const login = await page.goto('/api/auth/dev-login?username=admin')
    expect(login?.ok()).toBe(true)

    for (const viewport of [
      { name: 'desktop', width: 1440, height: 900 },
      { name: 'compact', width: 1024, height: 768 },
      { name: 'mobile', width: 390, height: 844 },
    ]) {
      await page.setViewportSize({ width: viewport.width, height: viewport.height })
      await page.goto('/v2/dashboard?role=mgmt')
      await expect(page.getByRole('heading', { level: 1, name: '经营驾驶舱' })).toBeVisible()
      await expect(page.getByText('经营健康为辅助判断')).toBeVisible()
      expect(
        await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth),
      ).toBe(true)

      if (viewport.name === 'mobile') {
        const contextControlHeights = await page
          .locator('.app-shell__context-controls .v2-field__control')
          .evaluateAll((controls) =>
            controls.map((control) => control.getBoundingClientRect().height),
          )
        expect(contextControlHeights).toHaveLength(2)
        expect(contextControlHeights.every((height) => height >= 44)).toBe(true)
        await page.getByRole('button', { name: '打开导航' }).click()
        await expect(page.locator('.app-shell__sidebar')).toHaveCSS(
          'transform',
          'matrix(1, 0, 0, 1, 0, 0)',
        )
        await expect(page.locator('[data-domain="workbench"]')).toBeVisible()
        await page.getByRole('button', { name: '关闭导航' }).last().click()
        await expect(page.locator('.app-shell__sidebar')).toHaveCSS(
          'transform',
          'matrix(1, 0, 0, 1, -304, 0)',
        )
      } else if (viewport.name === 'compact') {
        await expect(
          page.locator('.app-shell__domain--active .app-shell__workspaces'),
        ).toBeVisible()
      }

      if (visualOutput) {
        await page.screenshot({
          path: `${visualOutput}/shell-${viewport.name}.png`,
          fullPage: viewport.name !== 'mobile',
        })
      }
    }

    expect(businessRequests).toContain('/api/project-context/options')
    expect(businessRequests).toContain('/api/dashboard/management')
    expect(
      runtimeErrors.filter(
        (message) =>
          message !==
          'error http://127.0.0.1:5174/favicon.ico: Failed to load resource: the server responded with a status of 404 (Not Found)',
      ),
    ).toEqual([])
  })
})
