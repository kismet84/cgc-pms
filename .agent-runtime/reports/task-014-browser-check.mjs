import { createRequire } from 'node:module'
import { mkdir, writeFile } from 'node:fs/promises'

const require = createRequire('D:/projects-test/cgc-pms/frontend-admin/package.json')
const { chromium } = require('@playwright/test')

const baseUrl = 'http://127.0.0.1:4176'
const screenshotDir = 'D:/projects-test/cgc-pms/.agent-runtime/reports/task-014-screenshots'

const userInfo = {
  id: 'visual-admin',
  username: 'admin',
  realName: '视觉验收用户',
  roles: ['ADMIN'],
  permissions: ['*'],
}

const projects = [
  {
    id: 'p-001',
    projectCode: 'XM-2026-001',
    projectName: '城北安置房总承包项目',
    projectType: '施工总承包',
    projectAddress: '城北新区',
    ownerUnit: '城北建设集团',
    supervisorUnit: '华建监理',
    designUnit: '市政设计院',
    contractAmount: '268000000',
    targetCost: '210000000',
    plannedStartDate: '2026-01-08',
    plannedEndDate: '2026-12-30',
    projectManagerId: 'u-001',
    status: 'ONGOING',
    approvalStatus: '已批准',
    createdBy: 'admin',
    createdAt: '2026-01-01',
    updatedBy: 'admin',
    updatedAt: '2026-06-01',
  },
  {
    id: 'p-002',
    projectCode: 'XM-2026-002',
    projectName: '国际会展中心幕墙工程',
    projectType: '专业分包',
    projectAddress: '会展片区',
    ownerUnit: '会展集团',
    supervisorUnit: '中城监理',
    designUnit: '建筑设计总院',
    contractAmount: '96000000',
    targetCost: '76000000',
    plannedStartDate: '2026-02-10',
    plannedEndDate: '2026-10-20',
    projectManagerId: 'u-002',
    status: 'SUSPENDED',
    approvalStatus: '审批中',
    createdBy: 'admin',
    createdAt: '2026-02-01',
    updatedBy: 'admin',
    updatedAt: '2026-06-01',
  },
  {
    id: 'p-003',
    projectCode: 'XM-2025-018',
    projectName: '轨交配套道路工程',
    projectType: '施工总承包',
    projectAddress: '轨交新区',
    ownerUnit: '轨交集团',
    supervisorUnit: '中咨监理',
    designUnit: '交通设计院',
    contractAmount: '186000000',
    targetCost: '150000000',
    plannedStartDate: '2025-06-01',
    plannedEndDate: '2026-05-30',
    projectManagerId: 'u-003',
    status: 'COMPLETED',
    approvalStatus: '已批准',
    createdBy: 'admin',
    createdAt: '2025-05-01',
    updatedBy: 'admin',
    updatedAt: '2026-05-30',
  },
]

const members = [
  { id: 'm-001', projectId: 'p-001', userId: 'u-001', userName: '张工', roleCode: 'PM', positionName: '项目经理', startDate: '2026-01-08', status: 'ACTIVE' },
  { id: 'm-002', projectId: 'p-001', userId: 'u-002', userName: '李工', roleCode: 'CM', positionName: '商务经理', startDate: '2026-01-08', status: 'ACTIVE' },
  { id: 'm-003', projectId: 'p-001', userId: 'u-003', userName: '王工', roleCode: 'CSTM', positionName: '成本经理', startDate: '2026-01-08', status: 'ACTIVE' },
]

const users = [
  { id: 'u-001', username: 'zhang', realName: '张工' },
  { id: 'u-002', username: 'li', realName: '李工' },
  { id: 'u-003', username: 'wang', realName: '王工' },
]

const targets = [
  {
    id: 'ct-001',
    projectId: 'p-001',
    projectName: '城北安置房总承包项目',
    versionNo: 'V1.0',
    versionName: '首版目标成本',
    totalTargetAmount: '210000000',
    isActive: 1,
    approvalStatus: 'APPROVED',
    status: 'ACTIVE',
    effectiveDate: '2026-01-10',
    remark: '当前执行版本',
  },
  {
    id: 'ct-002',
    projectId: 'p-002',
    projectName: '国际会展中心幕墙工程',
    versionNo: 'V0.9',
    versionName: '调整版目标成本',
    totalTargetAmount: '76000000',
    isActive: 0,
    approvalStatus: 'REJECTED',
    status: 'DRAFT',
    effectiveDate: '2026-02-18',
    remark: '偏差待调整',
  },
]

const targetItems = [
  { id: 'i-001', costSubjectId: 'cs-001', costSubjectCode: '01', costSubjectName: '人工费', targetAmount: '86000000', sortOrder: 1 },
  { id: 'i-002', costSubjectId: 'cs-002', costSubjectCode: '02', costSubjectName: '材料费', targetAmount: '124000000', sortOrder: 2 },
]

const subjects = [
  { id: 'cs-001', subjectCode: '01', subjectName: '人工费', parentId: null, level: 1 },
  { id: 'cs-002', subjectCode: '02', subjectName: '材料费', parentId: null, level: 1 },
]

function api(data) {
  return { code: '0', message: 'success', data }
}

function pageResult(records) {
  return { records, total: records.length, pageNo: 1, pageSize: 20 }
}

async function routeApi(page) {
  await page.route('**/api/**', async (route) => {
    const url = new URL(route.request().url())
    const path = url.pathname.replace(/^\/api/, '')
    if (path === '/notifications/stream') {
      return route.fulfill({
        status: 200,
        headers: {
          'content-type': 'text/event-stream; charset=utf-8',
          'cache-control': 'no-cache',
          connection: 'keep-alive',
        },
        body: ': connected\n\n',
      })
    }
    if (path === '/projects') return route.fulfill({ json: api(pageResult(projects)) })
    if (path === '/projects/p-001') return route.fulfill({ json: api(projects[0]) })
    if (path === '/projects/p-001/overview') {
      return route.fulfill({
        json: api({
          projectId: 'p-001',
          contractCount: '8',
          totalContractAmount: '268000000',
          dynamicCost: '218000000',
          paidAmount: '96000000',
          warningCount: '3',
          memberCount: '3',
          members: members.map(({ userId, userName, roleCode }) => ({ userId, userName, roleCode })),
        }),
      })
    }
    if (path === '/projects/p-001/members') return route.fulfill({ json: api(pageResult(members)) })
    if (path === '/system/users' || path === '/users') return route.fulfill({ json: api(pageResult(users)) })
    if (path === '/cost-targets') return route.fulfill({ json: api(pageResult(targets)) })
    if (path === '/cost-targets/ct-001') return route.fulfill({ json: api(targets[0]) })
    if (path === '/cost-targets/ct-001/items') return route.fulfill({ json: api(targetItems) })
    if (path === '/cost-subjects/tree') return route.fulfill({ json: api(subjects) })
    if (path === '/notifications/unread-count') return route.fulfill({ json: api({ count: 0 }) })
    return route.fulfill({ json: api({}) })
  })
}

async function checkRoute(page, routePath, viewport, name) {
  await page.setViewportSize(viewport)
  await page.goto(`${baseUrl}${routePath}`, { waitUntil: 'domcontentloaded' })
  await page.waitForTimeout(900)
  const result = await page.evaluate(() => {
    const root = document.documentElement
    const bodyText = document.body.innerText
    return {
      url: location.pathname,
      title: document.title,
      bodyText,
      overflowX: Math.max(0, root.scrollWidth - root.clientWidth),
      hasOverlay: Boolean(document.querySelector('[plugin\\:vite\\:client-overlay], vite-error-overlay')),
      blank: document.body.innerText.trim().length < 20,
    }
  })
  const screenshot = `${screenshotDir}/${name}.png`
  await page.screenshot({ path: screenshot, fullPage: false })
  return { routePath, viewport, screenshot, ...result }
}

await mkdir(screenshotDir, { recursive: true })

const browser = await chromium.launch({ headless: true })
const context = await browser.newContext()
await context.addInitScript((info) => {
  localStorage.setItem('cgc_pms_userinfo', JSON.stringify(info))
}, userInfo)
const page = await context.newPage()
const consoleMessages = []
const pageErrors = []
page.on('console', (msg) => {
  if (['error', 'warning'].includes(msg.type())) consoleMessages.push(`${msg.type()}: ${msg.text()}`)
})
page.on('pageerror', (error) => pageErrors.push(error.message))
await routeApi(page)

const desktop = { width: 1440, height: 900 }
const mid = { width: 937, height: 900 }
const mobile = { width: 390, height: 844 }

const checks = []
checks.push(await checkRoute(page, '/project/list', desktop, 'project-list-1440x900'))
checks.push(await checkRoute(page, '/project/p-001/overview', desktop, 'project-overview-1440x900'))
checks.push(await checkRoute(page, '/project/p-001/members', desktop, 'project-members-1440x900'))
checks.push(await checkRoute(page, '/project/p-001/edit', desktop, 'project-edit-1440x900'))
checks.push(await checkRoute(page, '/cost-target/index', desktop, 'target-list-1440x900'))
checks.push(await checkRoute(page, '/cost-target/create', desktop, 'target-create-1440x900'))
checks.push(await checkRoute(page, '/project/list', mid, 'project-list-937x900'))
checks.push(await checkRoute(page, '/cost-target/index', mid, 'target-list-937x900'))
checks.push(await checkRoute(page, '/project/list', mobile, 'project-list-390x844'))
checks.push(await checkRoute(page, '/cost-target/index', mobile, 'target-list-390x844'))

await browser.close()

const summary = {
  baseUrl,
  consoleMessages,
  pageErrors,
  checks: checks.map((check) => ({
    routePath: check.routePath,
    viewport: check.viewport,
    url: check.url,
    title: check.title,
    overflowX: check.overflowX,
    hasOverlay: check.hasOverlay,
    blank: check.blank,
    screenshot: check.screenshot,
    requiredTextPresent: {
      projectList: check.routePath === '/project/list' ? check.bodyText.includes('项目列表') && check.bodyText.includes('项目状态分布') : undefined,
      projectOverview: check.routePath.includes('/overview') ? check.bodyText.includes('项目经营概览') && check.bodyText.includes('关键风险') : undefined,
      projectMembers: check.routePath.includes('/members') ? check.bodyText.includes('项目成员') && check.bodyText.includes('角色分布') : undefined,
      projectEdit: check.routePath.includes('/edit') && check.routePath.includes('/project/') ? check.bodyText.includes('基础信息') && check.bodyText.includes('项目周期') : undefined,
      targetList: check.routePath === '/cost-target/index' ? check.bodyText.includes('目标管理') && check.bodyText.includes('偏差预警') : undefined,
      targetCreate: check.routePath === '/cost-target/create' ? check.bodyText.includes('新建目标成本') && check.bodyText.includes('审批与备注') : undefined,
    },
  })),
}

await writeFile(
  'D:/projects-test/cgc-pms/.agent-runtime/reports/task-014-browser-check-result.json',
  JSON.stringify(summary, null, 2),
)
console.log(JSON.stringify(summary, null, 2))
