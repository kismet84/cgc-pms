import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'

const currentDir = dirname(fileURLToPath(import.meta.url))
const source = readFileSync(resolve(currentDir, '../daily-log.vue'), 'utf-8')

describe('site daily log page', () => {
  it('keeps the first release to project daily facts and one-way submission', () => {
    expect(source).toContain('constructionContent')
    expect(source).toContain('issuesDelays')
    expect(source).toContain('nextDayPlan')
    expect(source).toContain('weatherSummary')
    expect(source).toContain('onSiteHeadcount')
    expect(source).toContain('人工天气摘要')
    expect(source).toContain('在场人数')
    expect(source).toContain("form.onSiteHeadcount == null ? '未填写' : form.onSiteHeadcount")
    expect(source).toContain("record.status === 'DRAFT'")
    expect(source).toContain('submitSiteDailyLog')
    expect(source).not.toMatch(/equipment|offline|geolocation/i)
  })

  it('reuses the file API and renders loading empty and error states', () => {
    expect(source).toContain("const SITE_DAILY_LOG = 'SITE_DAILY_LOG'")
    expect(source).toContain('uploadFile(file, SITE_DAILY_LOG')
    expect(source).toContain('listFiles(SITE_DAILY_LOG')
    expect(source).toContain('deleteFile(file.id)')
    expect(source).toContain('listError')
    expect(source).toContain('暂无现场日报')
  })

  it('renders approved material deliveries as read-only daily facts', () => {
    expect(source).toContain('当日材料到货')
    expect(source).toContain('getSiteDailyLog')
    expect(source).toContain('activeRecord.deliveries')
    expect(source).toContain('delivery.receiptCode')
    expect(source).toContain('delivery.partnerName')
    expect(source).toContain('delivery.materialName')
    expect(source).toContain('delivery.actualQuantity')
    expect(source).toContain('delivery.qualifiedQuantity')
    expect(source).toContain('当日暂无已审批材料到货')
    expect(source).not.toContain('createDelivery')
  })
})
