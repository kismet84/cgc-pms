import { test, type Page } from '@playwright/test'
import AxeBuilder from '@axe-core/playwright'

async function loginAsAdmin(page: Page) {
  await page.goto('/login')
  await page.fill('input[placeholder="请输入用户名"]', 'admin')
  await page.fill('input[placeholder="请输入密码"]', 'admin123')
  await page.click('button[type="submit"]')
  await page.waitForURL((url) => !url.pathname.includes('/login'), { timeout: 15000 })
}

const PAGES = [
  { name: '项目管理', path: '/project/list' },
  { name: '仪表盘', path: '/dashboard' },
  { name: '合同台账', path: '/contract' },
  { name: '成本汇总', path: '/cost/summary' },
  { name: '付款申请', path: '/payment' },
  { name: '发票管理', path: '/invoice' },
  { name: '审批待办', path: '/approval' },
  { name: '预警中心', path: '/alert' },
  { name: '组织架构', path: '/org' },
]

test.describe('Axe Accessibility Audit', () => {
  test.beforeEach(async ({ page }) => {
    await loginAsAdmin(page)
    await page.waitForSelector('.topbar', { timeout: 10000 })
  })

  for (const { name, path } of PAGES) {
    test(`${name} (${path})`, async ({ page }) => {
      await page.goto(path)
      await page.waitForLoadState('load')
      await page.waitForTimeout(2000)

      const results = await new AxeBuilder({ page })
        .withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'])
        .analyze()

      const violations = results.violations.filter(
        (v) => v.impact === 'critical' || v.impact === 'serious',
      )

      if (violations.length > 0) {
        console.log(`\n=== ${name} (${path}) ===`)
        for (const v of violations) {
          console.log(`\n[${v.impact}] ${v.id}: ${v.help}`)
          console.log(`  Description: ${v.description}`)
          for (const node of v.nodes.slice(0, 3)) {
            console.log(`  Element: ${node.html?.slice(0, 120)}`)
            console.log(`  Fix: ${node.failureSummary?.slice(0, 200)}`)
          }
        }
      } else {
        console.log(`\n✓ ${name} (${path}) — clean`)
      }
    })
  }
})
