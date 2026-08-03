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
    expect(page).toContain('contentSha256')
    expect(page).not.toContain('contentSha256:')
  })

  it('uses automatic award status transition without a manual project selector', () => {
    const detail = read('src/pages/commercial/BidTenderDetailPage.vue')
    const service = read('src/services/commercial.ts')
    expect(detail).toContain('changeBidStatus')
    expect(detail).toContain("EVALUATING: ['WON', 'LOST']")
    expect(service).toContain('expectedStatus, targetStatus, reason')
    expect(service).not.toContain('中标关联项目')
  })

  it('reads bid cash facts and summaries from the shared cash journal', () => {
    const page = read('src/pages/commercial/BidTenderCostPage.vue')
    expect(page).toContain("costSubjectRootCode: '5401.01'")
    expect(page).toContain('loadCashJournalSummary')
    expect(page).toContain('createCashJournal')
    expect(page).toContain('archiveCashJournal')
    expect(page).toContain('reverseCashJournal')
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
