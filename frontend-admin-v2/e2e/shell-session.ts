import type { Page } from '@playwright/test'

export const installShellPreferencesMock = (page: Page) =>
  page.route('**/api/profile/preferences', (route) =>
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
