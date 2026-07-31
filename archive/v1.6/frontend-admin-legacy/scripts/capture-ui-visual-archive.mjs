import { chromium } from '@playwright/test'
import { mkdir, writeFile } from 'node:fs/promises'
import { resolve } from 'node:path'

const outDir = resolve('../docs/quality/ui-visual-archive')
const routes = [
  ['dashboard', '/dashboard', '工作台'],
  ['contract-ledger', '/contract/ledger', '合同台账'],
  ['project-list', '/project/list', '项目列表'],
  ['inventory-stock', '/inventory/stock', '库存台账'],
  ['system-roles', '/system/roles', '角色管理'],
]

async function login(page) {
  await page.goto('http://localhost:5173/login', { waitUntil: 'domcontentloaded' })
  await page.fill('input[placeholder="请输入用户名"]', 'admin')
  await page.fill('input[placeholder="请输入密码"]', 'admin123')
  await Promise.all([
    page.waitForURL(/\/dashboard/, { timeout: 60000 }),
    page.press('input[placeholder="请输入密码"]', 'Enter'),
  ])
  await page.waitForFunction(() => Boolean(localStorage.getItem('cgc_pms_userinfo')), null, {
    timeout: 10000,
  })
}

await mkdir(outDir, { recursive: true })

const browser = await chromium.launch({ headless: true })
const page = await browser.newPage({
  viewport: { width: 2560, height: 1600 },
  deviceScaleFactor: 1,
})
page.setDefaultTimeout(30000)
await login(page)

const evidence = []
for (const [name, route, label] of routes) {
  await page.goto(`http://localhost:5173${route}`, {
    waitUntil: 'domcontentloaded',
    timeout: 60000,
  })
  await page.locator('body').filter({ hasText: label }).waitFor({ timeout: 30000 })
  await page.waitForTimeout(500)

  const screenshot = `${name}-2560x1600.png`
  await page.screenshot({ path: `${outDir}/${screenshot}`, fullPage: false })

  const metrics = await page.evaluate(() => ({
    url: location.href,
    title: document.title,
    innerWidth,
    innerHeight,
    devicePixelRatio,
    pageOverflowX: document.documentElement.scrollWidth > document.documentElement.clientWidth,
  }))

  evidence.push({ name, route, screenshot, metrics })
}

await writeFile(
  `${outDir}/visual-archive-evidence.json`,
  JSON.stringify({ evidence }, null, 2),
  'utf8',
)
await browser.close()

console.log(`Captured ${evidence.length} visual archive screenshots to ${outDir}`)
