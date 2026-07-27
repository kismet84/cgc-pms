import { expect, test } from '@playwright/test'

test.skip(
  process.env.V2_M8_ROOT_REHEARSAL !== '1',
  'Run only against the isolated local M8 root-path edge',
)

test('root-path artifact restores authenticated representative deep links', async ({ page }) => {
  const errors: string[] = []
  page.on('console', (message) => {
    if (message.type() === 'error') errors.push(message.text())
  })
  page.on('pageerror', (error) => errors.push(error.message))

  expect((await page.goto('/api/auth/dev-login?username=admin'))?.ok()).toBe(true)

  for (const [path, heading] of [
    ['/dashboard', '经营驾驶舱'],
    ['/project/list', '项目台账'],
    ['/approval/todo', '审批工作台'],
    ['/system/users', '用户管理'],
  ] as const) {
    await page.goto(path)
    await expect(page).toHaveURL(new RegExp(`${path.replaceAll('/', '\\/')}$`))
    await expect(page.getByRole('heading', { level: 1, name: heading })).toBeVisible()
    await page.reload()
    await expect(page.getByRole('heading', { level: 1, name: heading })).toBeVisible()
  }

  expect(errors).toEqual([])
})
