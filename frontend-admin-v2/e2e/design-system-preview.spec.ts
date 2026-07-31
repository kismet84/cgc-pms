import { expect, test, type Page } from '@playwright/test'

const previewUrl = '/src/components/preview/index.html'

async function openGallery(page: Page) {
  await page.goto(previewUrl)
  await expect(page.getByRole('heading', { level: 1, name: '设计系统单一预览基线' })).toBeVisible()
  await page.getByRole('button', { name: '关闭对话框' }).click()
}

test('keeps one desktop gallery and the V3 dialog closing contract', async ({ page }) => {
  const consoleProblems: string[] = []
  page.on('console', (message) => {
    if (['error', 'warning'].includes(message.type())) consoleProblems.push(message.text())
  })
  await page.setViewportSize({ width: 1440, height: 900 })
  await openGallery(page)

  await expect(
    page.getByRole('navigation', { name: '设计系统章节' }).getByRole('link'),
  ).toHaveCount(5)
  await expect(page.getByRole('table', { name: '项目台账共享表格示例' })).toBeVisible()

  await page.getByRole('button', { name: 'V3审批详情' }).click()
  const detail = page.getByRole('dialog', { name: /审批详情/ })
  await expect(detail).toHaveClass(/v2-detail-dialog/)
  await page.locator('.v2-dialog__backdrop').click({ position: { x: 4, y: 4 } })
  await expect(detail).toHaveCount(0)

  await page.getByRole('button', { name: '标准表单' }).click()
  const form = page.getByRole('dialog', { name: '新建演示记录' })
  await page.locator('.v2-dialog__backdrop').click({ position: { x: 4, y: 4 } })
  await expect(form).toBeVisible()
  await form.getByRole('button', { name: '取消' }).click()
  await expect(form).toHaveCount(0)

  expect(consoleProblems).toEqual([])
})

test('keeps the gallery and standard dialog inside a 390px viewport', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 })
  await openGallery(page)

  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= document.documentElement.clientWidth,
    ),
  ).toBe(true)

  await page.getByRole('link', { name: '弹窗规格' }).click()
  await page.getByRole('button', { name: '标准表单' }).click()
  const form = page.getByRole('dialog', { name: '新建演示记录' })
  const box = await form.boundingBox()

  expect(box).not.toBeNull()
  expect(box!.x).toBeGreaterThanOrEqual(0)
  expect(box!.x + box!.width).toBeLessThanOrEqual(390)
  await form.getByRole('button', { name: '取消' }).click()
})
