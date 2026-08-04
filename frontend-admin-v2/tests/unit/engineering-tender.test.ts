import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'

const root = resolve(import.meta.dirname, '../..')
const read = (path: string) => readFileSync(resolve(root, path), 'utf8')

describe('engineering tender and construction mainline contracts', () => {
  it('publishes exactly two engineering tender menu tabs plus one context detail route', () => {
    const catalog = read('src/navigation/catalog.ts')
    const router = read('src/router.ts')
    expect(catalog).toContain("label: '工程投标'")
    expect(catalog).toContain("label: '投标记录'")
    expect(catalog).toContain("label: '投标成本'")
    expect(router).toContain("path: '/engineering-tender/records/:id'")
  })

  it('renders four detail tabs and immutable document actions', () => {
    const page = read('src/pages/commercial/BidTenderDetailPage.vue')
    for (const label of ['基本信息', '招标文件', '投标文件', '中标文件'])
      expect(page).toContain(label)
    for (const action of ['appendBidDocument', 'finalizeBidDocument', 'voidBidDocument'])
      expect(page).toContain(action)
    expect(page).not.toContain('contentSha256')
    expect(page).not.toContain('SHA-256')
    expect(page).not.toContain('状态变更')
    expect(page).not.toContain('下一状态')
    expect(page).not.toContain('原因/说明')
    expect(page).not.toContain('外部回执号')
    expect(page).toContain('logicalName: file.value.name.slice(0, 200)')
    expect(page).toContain("loadEnabledDictDataByCode('bid_document_type', controller.signal)")
    expect(page).toContain('documentTypeLabel(item.documentType)')
  })

  it('uses service-owned status automation without manual controls', () => {
    const detail = read('src/pages/commercial/BidTenderDetailPage.vue')
    const service = read('src/services/commercial.ts')
    expect(detail).not.toContain('changeBidStatus')
    expect(detail).toContain('await finalizeBidDocument(bidId.value, version.id)')
    expect(detail).toContain(
      "session.hasPermission('bid:file:manage') && session.hasPermission('bid:status')",
    )
    expect(detail).toContain("PREPARING: '注册'")
    expect(detail).toContain("SUBMITTED: '投标'")
    expect(detail).toContain("EVALUATING: '评标'")
    expect(detail).toContain("WON: '中标'")
    expect(detail).toContain("['PREPARING', 'SUBMITTED', 'EVALUATING'].includes")
    expect(service).toContain('expectedStatus, targetStatus, reason')
    expect(service).not.toContain('中标关联项目')
  })

  it('exposes the planned date fields required before award', () => {
    const detail = read('src/pages/commercial/BidTenderDetailPage.vue')
    expect(detail).toContain("['documentReceivedDate', '获取文件日期', 'date']")
    expect(detail).toContain("['bidDeadlineAt', '投标截止时间', 'datetime-local']")
    expect(detail).toContain("['openingAt', '开标时间', 'datetime-local']")
    expect(detail).toContain("['bidValidUntil', '投标有效期', 'date']")
    expect(detail).toContain("'plannedStartDate'")
    expect(detail).toContain("'plannedEndDate'")
    expect(detail).toContain("['plannedStartDate', '计划开始日期', 'date']")
    expect(detail).toContain("['plannedEndDate', '计划结束日期', 'date']")
  })

  it('hides the bid section name but preserves it in detail updates', () => {
    const detail = read('src/pages/commercial/BidTenderDetailPage.vue')
    expect(detail).not.toContain("['bidSectionName', '标段名称']")
    expect(detail).toContain("'bidSectionName'")
    expect(detail).toContain('Object.entries(edit)')
  })

  it('uses shell project context for bid cash facts without local selectors', () => {
    const page = read('src/pages/commercial/BidTenderCostPage.vue')
    expect(page).toContain("costSubjectRootCode: '5401.01'")
    expect(page).toContain('useWorkspaceStore')
    expect(page).toContain('projectId: workspace.selectedProjectId || undefined')
    expect(page).toContain('projectId: null')
    expect(page).toContain('label="关联投标"')
    expect(page).toContain('<V2Card v-if="canMaintain" title="登记现金流水">')
    expect(page).toContain('createCashJournal')
    expect(page).toContain('archiveCashJournal')
    expect(page).toContain('reverseCashJournal')
    expect(page).not.toContain("router.push('/cash-journal')")
    expect(page).not.toContain('新建资金账户')
    expect(page).not.toContain('label="对方单位"')
    expect(page).not.toContain('label="投标记录"')
    expect(page).not.toContain('投标现金概览')
    expect(page).not.toContain('loadCashJournalSummary')
  })

  it('places the generic back button inside the detail title card with a 50px gap', () => {
    const page = read('src/pages/commercial/BidTenderDetailPage.vue')
    expect(page).toContain('leading-action-label="返回"')
    expect(page).toContain('@leading-action="router.push(\'/engineering-tender/records\')"')
    expect(page).toContain('class="bid-detail__heading"')
    expect(page).toContain('gap: 50px')
    expect(page).not.toContain('返回投标记录')
  })

  it('shows service-owned construction blockers and removes no-schedule submit fallback', () => {
    const closeout = read('src/pages/delivery/ProjectCloseoutPage.vue')
    const daily = read('src/pages/delivery/DailyLogPage.vue')
    expect(closeout).toContain('stageGates')
    expect(closeout).toContain('constructionCompletion')
    expect(closeout).toContain('warrantyEntry')
    expect(closeout).toContain('finalClose')
    expect(daily).toContain('日报提交已阻断')
    expect(daily).toContain('activeRecord.value.scheduleManaged &&')
    expect(daily).not.toContain('!activeRecord.value.scheduleManaged || canReportProgress.value')
  })
})
