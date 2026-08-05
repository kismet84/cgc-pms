import AxeBuilder from '@axe-core/playwright'
import { expect, test, type Page, type Route } from '@playwright/test'

const conversation = {
  id: '9007199254740993',
  type: 'DIRECT',
  name: '张三',
  ownerUserId: null,
  lastMessageSeq: '9007199254740995',
  lastMessageAt: '2026-08-05T09:00:00',
  status: 'ACTIVE',
  role: 'MEMBER',
  unreadCount: 1,
}

async function json(route: Route, data: unknown) {
  await route.fulfill({
    contentType: 'application/json',
    body: JSON.stringify({ code: '0', message: 'success', data }),
  })
}

async function installApi(page: Page) {
  await page.route('**/api/**', async (route) => {
    const { pathname } = new URL(route.request().url())
    if (pathname === '/api/communications/stream') {
      await route.fulfill({
        contentType: 'text/event-stream',
        body: 'event: connected\ndata: {"action":"REFRESH"}\n\n',
      })
      return
    }
    if (pathname === '/api/auth/userinfo') {
      await json(route, {
        userId: '1',
        username: 'tester',
        realName: '测试用户',
        roles: ['USER'],
        permissions: ['communication:view', 'communication:send'],
      })
      return
    }
    if (pathname === '/api/communications/conversations') {
      await json(route, [conversation])
      return
    }
    if (pathname.endsWith('/messages')) {
      await json(route, [
        {
          id: '9007199254740994',
          conversationId: conversation.id,
          senderId: '2',
          seq: '9007199254740995',
          body: '<script>window.__communicationXss = true</script>',
          senderName: '张三',
          createdAt: '2026-08-05T09:00:00',
          attachments: [],
        },
      ])
      return
    }
    if (pathname === '/api/communications/users') {
      await json(route, [{ id: '2', username: 'zhangsan', realName: '张三', avatar: null }])
      return
    }
    if (pathname === '/api/communications/unread-count') {
      await json(route, { count: 1 })
      return
    }
    if (pathname.endsWith('/read')) {
      await json(route, null)
      return
    }
    if (pathname === '/api/profile/preferences') {
      await json(route, {
        sidebarCollapsed: false,
        notificationEnabled: true,
        theme: 'light',
        tableDensity: 'middle',
      })
      return
    }
    if (pathname === '/api/project-context/options') {
      await json(route, [])
      return
    }
    await json(route, [])
  })
}

test('renders communication history as plain text with independent unread entry', async ({
  page,
}) => {
  await installApi(page)
  await page.goto('/communication')

  await expect(page.getByRole('heading', { name: '站内通讯' })).toBeVisible()
  await expect(page.getByRole('link', { name: /打开站内通讯/ })).toBeVisible()
  await expect(page.getByRole('log')).toContainText(
    '<script>window.__communicationXss = true</script>',
  )
  expect(
    await page.evaluate(
      () => (window as Window & { __communicationXss?: boolean }).__communicationXss,
    ),
  ).toBeUndefined()
  const accessibility = await new AxeBuilder({ page }).include('.communication-page').analyze()
  expect(accessibility.violations).toEqual([])
})

test('keeps conversation and composer usable on a narrow viewport', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 })
  await installApi(page)
  await page.goto('/communication')

  await expect(page.getByRole('button', { name: /张三/ }).first()).toBeVisible()
  await expect(page.getByLabel('消息')).toBeVisible()
  await page.getByLabel('消息').fill('移动端消息')
  await expect(page.getByRole('button', { name: '发送' })).toBeEnabled()
})
