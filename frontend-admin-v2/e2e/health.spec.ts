import { expect, test } from '@playwright/test'

test('serves the isolated V2 health surface', async ({ page }) => {
  await page.goto('/v2/health')
  await expect(page.getByRole('heading', { name: '隔离底座已启动' })).toBeVisible()
  await expect(page.getByText('Legacy UI 依赖')).toBeVisible()
  await expect(page.getByText('0', { exact: true })).toBeVisible()
})
