import { expect, test } from '@playwright/test'

test('reports the current V2 app and API proxy health', async ({ page }) => {
  await page.route('**/api/actuator/health', (route) =>
    route.fulfill({ status: 200, contentType: 'application/json', body: '{"status":"UP"}' }),
  )
  await page.goto('/health')
  await expect(page.getByRole('heading', { name: '系统健康检查' })).toBeVisible()
  await expect(page.getByText('V2 管理端', { exact: true })).toBeVisible()
  await expect(page.getByText('后端 API 可达')).toBeVisible()
})
