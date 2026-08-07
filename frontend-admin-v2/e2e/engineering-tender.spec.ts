import AxeBuilder from '@axe-core/playwright'
import { expect, test, type Page } from '@playwright/test'
import { captureRuntimeErrors } from './runtime-errors'
import { installShellPreferencesMock } from './shell-session'

type Identity = 'management' | 'ordinary'

const users = {
  management: {
    tenantId: '0',
    userId: '269',
    username: 'management',
    realName: '管理层',
    roles: ['MANAGEMENT'],
    permissions: [
      'bid:query',
      'bid:add',
      'bid:edit',
      'bid:delete',
      'bid:status',
      'bid:file:manage',
      'bid:cost:query',
      'bid:cost:maintain',
      'bid:cost:export',
    ],
  },
  ordinary: {
    tenantId: '0',
    userId: '300',
    username: 'ordinary',
    realName: '普通用户',
    roles: ['COMMON_USER'],
    permissions: [],
  },
} as const

const bid = {
  id: '71',
  bidCode: 'BID-20260803-001',
  bidProjectName: '市民中心工程',
  bidSectionName: '施工总承包一标段',
  tendereeName: '市建设中心',
  agencyName: '公共资源代理公司',
  ownerId: '269',
  ownerName: '管理层',
  bidDeadlineAt: '2026-08-20 09:00:00',
  bidStatus: 'PREPARING',
  finalBidPrice: '12000000.00',
  bidExpense: '2600.00',
  updatedAt: '2026-08-03 18:00:00',
}

const envelope = (data: unknown) => ({ code: '0', message: 'success', data })

async function installMocks(page: Page, identity: () => Identity) {
  await installShellPreferencesMock(page)
  await page.route('**/api/auth/userinfo', (route) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(envelope(users[identity()])),
    }),
  )
  await page.route('**/api/auth/refresh', (route) =>
    route.fulfill({
      status: 401,
      contentType: 'application/json',
      body: JSON.stringify({ code: 'AUTH_TOKEN_INVALID', message: 'unauthorized', data: null }),
    }),
  )
  await page.route('**/api/project-context/options', (route) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(envelope([])),
    }),
  )
  await page.route('**/api/system/dict/data/by-code/*', (route) => {
    const code = new URL(route.request().url()).pathname.split('/').at(-1)
    const rows =
      {
        bid_status: [
          {
            id: '1',
            dictTypeId: '11',
            dictLabel: '注册',
            dictValue: 'PREPARING',
            status: 'ENABLE',
          },
          {
            id: '2',
            dictTypeId: '11',
            dictLabel: '投标',
            dictValue: 'SUBMITTED',
            status: 'ENABLE',
          },
        ],
        bid_document_type: [
          {
            id: '3',
            dictTypeId: '12',
            dictLabel: '招标文件',
            dictValue: 'TENDER_DOCUMENT',
            status: 'ENABLE',
          },
        ],
        cash_direction: [
          { id: '4', dictTypeId: '13', dictLabel: '收入', dictValue: 'IN', status: 'ENABLE' },
          { id: '5', dictTypeId: '13', dictLabel: '支出', dictValue: 'OUT', status: 'ENABLE' },
        ],
      }[code ?? ''] ?? []
    return route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(envelope(rows)),
    })
  })
  await page.route('**/api/bid-cost/owners', (route) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(envelope([{ ownerId: '269', ownerName: '管理层' }])),
    }),
  )
  await page.route(/\/api\/bid-cost(?:\?.*)?$/, (route) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(envelope({ records: [bid], total: 1, pageNo: 1, pageSize: 10 })),
    }),
  )
  await page.route('**/api/bid-cost/cost-options', (route) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(
        envelope([{ id: bid.id, bidCode: bid.bidCode, bidProjectName: bid.bidProjectName }]),
      ),
    }),
  )
  await page.route('**/api/bid-cost/71/documents', (route) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(
        envelope([
          {
            id: '711',
            bidCostId: '71',
            documentGroup: 'TENDER',
            documentType: 'TENDER_DOCUMENT',
            logicalName: '招标文件',
            versionNo: 1,
            sysFileId: '9001',
            status: 'FINAL',
            contentSha256: 'a'.repeat(64),
            sourceName: '公共资源交易平台',
            receivedAt: '2026-08-01 10:00:00',
            createdBy: '269',
            createdAt: '2026-08-01 10:05:00',
          },
        ]),
      ),
    }),
  )
  await page.route('**/api/bid-cost/71', (route) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(envelope(bid)),
    }),
  )
  await page.route('**/api/cost-subjects/bid-options', (route) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(
        envelope([
          {
            id: '54010103',
            subjectCode: '5401.01.03',
            subjectName: '标书制作费',
            status: 'ENABLE',
          },
        ]),
      ),
    }),
  )
  await page.route('**/api/fund-accounts/bid-options', (route) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(
        envelope([{ id: '501', accountName: '基本户', accountType: 'BANK', enabledFlag: 1 }]),
      ),
    }),
  )
  await page.route(/\/api\/cash-journal-entries\/summary(?:\?.*)?$/, (route) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(
        envelope({
          pendingCount: 0,
          cumulativeCashOut: '3000.00',
          cumulativeCashIn: '400.00',
          outstandingDeposit: '0.00',
          actualBidExpense: '2600.00',
          cashNetOutflow: '2600.00',
        }),
      ),
    }),
  )
  await page.route(/\/api\/cash-journal-entries(?:\?.*)?$/, (route) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(
        envelope({
          records: [
            {
              id: '801',
              entryNo: 'CJ-20260803-001',
              direction: 'OUT',
              amount: '2600.00',
              runningBalance: null,
              businessDate: '2026-08-03',
              bidCostId: '71',
              costSubjectId: '54010103',
              costSubjectCode: '5401.01.03',
              costSubjectName: '标书制作费',
              counterpartyName: '图文公司',
              summary: '标书制作',
              sourceType: 'MANUAL',
              status: 'ARCHIVED',
              createdBy: '269',
              attachmentCount: 1,
            },
          ],
          total: 1,
          pageNo: 1,
          pageSize: 50,
        }),
      ),
    }),
  )
}

test('管理层完成投标记录、四页签与投标成本主链，普通角色直达拒绝', async ({ page, browser }) => {
  test.setTimeout(60_000)
  let identity: Identity = 'management'
  await installMocks(page, () => identity)
  const runtimeErrors = captureRuntimeErrors(page)

  await page.goto('/bid-cost')
  await expect(page).toHaveURL(/\/engineering-tender\/records$/)
  await expect(page.getByRole('heading', { name: '投标记录', exact: true })).toBeVisible()
  await expect(page.locator('.bid-cost-page table thead th')).toHaveCount(12)
  await expect(page.locator('.bid-cost-page table thead')).not.toContainText('标段名称')
  await page.locator('.bid-cost-page table tbody tr').first().press('Enter')
  await expect(page).toHaveURL(/\/engineering-tender\/records\/71\?tab=basic$/)
  await expect(page.getByText('状态变更', { exact: true })).toHaveCount(0)
  await expect(page.getByLabel('外部平台')).toHaveCount(0)
  await expect(page.getByLabel('外部编号')).toHaveCount(0)
  await expect(page.getByLabel('外部链接')).toHaveCount(0)
  await expect(page.getByLabel('投标负责人ID')).toHaveCount(0)
  for (const tab of ['基本信息', '招标文件', '投标文件', '中标文件']) {
    await expect(page.getByRole('tab', { name: new RegExp(tab) })).toBeVisible()
  }
  await page.getByRole('tab', { name: /招标文件/ }).click()
  await expect(page.getByLabel('逻辑文件名')).toHaveCount(0)
  await expect(page.getByLabel('来源名称')).toHaveCount(0)
  await expect(page.getByLabel('来源链接')).toHaveCount(0)
  await expect(page.getByText('公共资源交易平台')).toBeVisible()
  await expect(page.locator('table')).not.toContainText('SHA-256')
  await expect(page.locator('table')).not.toContainText(`a`.repeat(64))
  await expect(page.locator('table')).not.toContainText('TENDER_DOCUMENT')

  await page.goto('/engineering-tender/costs')
  await expect(page.getByRole('heading', { name: '投标成本', exact: true })).toBeVisible()
  await expect(page.getByRole('heading', { name: '登记现金流水', exact: true })).toBeVisible()
  await expect(page.getByLabel('关联投标')).toBeVisible()
  await expect(page.getByRole('heading', { name: '投标现金概览' })).toHaveCount(0)
  await expect(page.getByText('累计现金支出', { exact: true })).toHaveCount(0)
  await expect(page.getByLabel('投标记录')).toHaveCount(0)
  await expect(page.getByRole('button', { name: '新建资金账户' })).toHaveCount(0)
  await expect(page.getByLabel('对方单位')).toHaveCount(0)
  await expect(page.getByText('CJ-20260803-001')).toBeVisible()
  await expect(page.getByRole('cell', { name: '支出', exact: true })).toBeVisible()
  await expect(page.locator('.bid-cost-ledger__table')).not.toContainText('5401.01.03')
  await expect(page.getByRole('button', { name: '红冲' })).toBeVisible()
  expect(
    (await new AxeBuilder({ page }).include('.bid-cost-ledger').analyze()).violations.filter(
      (item) => ['serious', 'critical'].includes(item.impact ?? ''),
    ),
  ).toEqual([])
  expect(runtimeErrors).toEqual([])

  const narrow = await browser.newPage({ viewport: { width: 390, height: 844 } })
  await installMocks(narrow, () => identity)
  await narrow.goto('/engineering-tender/costs')
  await expect(narrow.getByRole('heading', { name: '投标成本', exact: true })).toBeVisible()
  await expect(narrow.getByText('CJ-20260803-001')).toBeVisible()
  await narrow.close()

  const denied = await browser.newPage()
  identity = 'ordinary'
  await installMocks(denied, () => identity)
  await denied.goto('/engineering-tender/records')
  await expect(denied).toHaveURL(/\/forbidden\?from=/)
  await denied.goto('/engineering-tender/costs')
  await expect(denied).toHaveURL(/\/forbidden\?from=/)
  await denied.close()
})
