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

  it('keeps the desktop horizontal scrollbar above the bottom pagination', () => {
    expect(source).toContain('class="site-daily-desktop-table"')
    expect(source).toMatch(
      /\.site-daily-table-wrap\s+:deep\(\.site-daily-desktop-table \.ant-table-content\)\s*\{[\s\S]*height:\s*100%\s*!important;/,
    )
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

  it('renders planned tasks covering the daily report date without write actions', () => {
    expect(source).toContain('当日计划任务')
    expect(source).toContain('activeRecord.plannedTasks')
    expect(source).toContain('planned.taskCode')
    expect(source).toContain('planned.taskName')
    expect(source).toContain('planned.workArea')
    expect(source).toContain('planned.progressPercent')
    expect(source).toContain('当日暂无计划任务')
    expect(source).not.toContain('updatePlannedTask')
  })

  it('renders approved stock-out requisitions as read-only daily facts', () => {
    expect(source).toContain('当日已审批领料')
    expect(source).toContain('activeRecord.requisitions')
    expect(source).toContain('requisition.requisitionCode')
    expect(source).toContain('requisition.materialName')
    expect(source).toContain('requisition.quantity')
    expect(source).toContain('requisition.materialUnit')
    expect(source).toContain('requisition.useLocation')
    expect(source).toContain('当日暂无已审批且已出库领料')
    expect(source).not.toContain('createRequisition')
    expect(source).not.toContain('已安装')
  })

  it('renders a minimal audit trail without sensitive audit fields', () => {
    expect(source).toContain('变更历史')
    expect(source).toContain('activeRecord.auditTrail')
    expect(source).toContain('audit.operationType')
    expect(source).toContain('audit.userId')
    expect(source).toContain('audit.success')
    expect(source).toContain('audit.createdAt')
    expect(source).toContain('暂无变更记录')
    expect(source).not.toContain('sourceIp')
    expect(source).not.toContain('requestPath')
    expect(source).not.toContain('errorCode')
  })

  it('loads submitted quality safety summaries only for authorized users', () => {
    expect(source).toContain("userStore.hasPermission('quality:safety:query')")
    expect(source).toContain('getSiteDailyQualitySafetyFacts')
    expect(source).toContain('if (!canViewQualitySafety.value) {')
    expect(source).toContain('当日质量安全检查')
    expect(source).toContain('qualitySafetyFacts')
    expect(source).toContain('inspectionCode')
    expect(source).toContain('highSeverityIssueCount')
    expect(source).toContain('openIssueCount')
    expect(source).toContain('当日暂无已提交质量安全检查')
    expect(source).toContain('不影响日报正文查看')
    expect(source).not.toContain('createQualitySafety')
    expect(source).not.toContain('responsiblePartnerId')
    expect(source).not.toContain('actionDescription')
    expect(source).not.toContain('fineAmount')
  })
})
