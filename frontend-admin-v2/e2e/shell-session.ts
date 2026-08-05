import type { Page } from '@playwright/test'

export async function installShellPreferencesMock(page: Page): Promise<void> {
  await page.route('**/api/profile/preferences', (route) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        code: '0',
        message: 'success',
        data: {
          sidebarCollapsed: false,
          notificationEnabled: true,
          theme: 'light',
          tableDensity: 'middle',
        },
      }),
    }),
  )
  await page.route('**/api/communications/unread-count', (route) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ code: '0', message: 'success', data: { count: 0 } }),
    }),
  )
  await page.route('**/api/communications/stream', (route) => route.fulfill({ status: 204 }))
}
