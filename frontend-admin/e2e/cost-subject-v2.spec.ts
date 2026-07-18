import { expect, test } from '@playwright/test'

test('成本科目中心四工作区支持深链接、刷新与Tab切换', async ({ page }) => {
  const runtimeErrors: string[] = []
  page.on('console', (entry) => {
    if (entry.type() === 'error') runtimeErrors.push(entry.text())
  })
  page.on('pageerror', (error) => runtimeErrors.push(error.message))

  await page.goto('/cost/subject')
  await expect(page).toHaveURL(/\/cost\/subject\/taxonomy$/)
  await expect(page.locator('.cost-subject-center')).toBeVisible()
  await expect(
    page.getByText('统一科目、显式归集、项目范围、转入与分摊追踪；旧科目引用已迁移并留存审计。'),
  ).toBeVisible()
  const treeResponse = await page.request.get('/api/cost-subjects/tree?category=COST')
  expect(treeResponse.ok()).toBeTruthy()
  const treePayload = JSON.stringify(await treeResponse.json())
  for (const legacyCode of [
    'COST_ROOT',
    'COST_MATERIAL',
    'COST_SUBCONTRACT',
    'COST_LABOR',
    'COST_MACHINERY',
    'COST_OTHER',
    '5001.01',
    '5001.02',
    '5001.03',
    '5001.04',
  ]) {
    expect(treePayload).not.toContain(legacyCode)
  }
  const workspaceTabs = page.locator('.workspace-tabs')
  await expect(workspaceTabs.getByRole('tab')).toHaveCount(4)
  await expect(page.locator('.cost-subject-center').getByRole('tab')).toHaveCount(0)

  await workspaceTabs.getByRole('tab', { name: '归集规则' }).click()
  await expect(page).toHaveURL(/\/cost\/subject\/rules$/)
  await expect(page.getByText('映射版本', { exact: true })).toBeVisible()

  await workspaceTabs.getByRole('tab', { name: '项目适用与目标成本' }).click()
  await expect(page).toHaveURL(/\/cost\/subject\/scope$/)
  await expect(page.getByText('项目适用范围', { exact: true })).toBeVisible()

  await workspaceTabs.getByRole('tab', { name: '影响与转入追踪' }).click()
  await expect(page).toHaveURL(/\/cost\/subject\/trace$/)
  await expect(page.getByText('投标成本转入记录', { exact: true })).toBeVisible()
  await expect(page.getByText('项目财务费用分摊记录', { exact: true })).toBeVisible()

  await page.reload()
  await expect(page).toHaveURL(/\/cost\/subject\/trace$/)
  await expect(page.locator('.cost-subject-center')).toBeVisible()
  await page.screenshot({
    path: test.info().outputPath('cost-subject-v2-desktop.png'),
    fullPage: false,
  })

  await page.setViewportSize({ width: 390, height: 844 })
  await page.goto('/cost/subject/taxonomy')
  await expect(page.locator('.cost-subject-center')).toBeVisible()
  await page.screenshot({
    path: test.info().outputPath('cost-subject-v2-mobile.png'),
    fullPage: false,
  })

  expect(runtimeErrors).toEqual([])
})
