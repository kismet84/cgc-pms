import { describe, expect, it } from 'vitest'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const currentDir = dirname(fileURLToPath(import.meta.url))
const read = (p: string) => readFileSync(resolve(currentDir, '..', p), 'utf-8')

const indexSource = read('index.vue')
const composableSource = read('composables/useDashboardData.ts')
const costViewSource = read('components/DashboardCostView.vue')
const pmViewSource = read('components/DashboardPmView.vue')
const purchaseViewSource = read('components/DashboardPurchaseView.vue')
const productionViewSource = read('components/DashboardProductionView.vue')
const chiefViewSource = read('components/DashboardChiefEngineerView.vue')

const allViews = [
  costViewSource,
  pmViewSource,
  purchaseViewSource,
  productionViewSource,
  chiefViewSource,
].join('\n')
const fullSource = [indexSource, composableSource, allViews].join('\n')

describe('Dashboard role UI unification', () => {
  it('keeps approved role labels and layout language aligned', () => {
    for (const label of ['项目经理', '商务经理', '采购经理', '生产经理', '总工程师']) {
      expect(fullSource).toContain(label)
    }

    expect(indexSource).toContain(
      "const roleTabOrder: DashboardRole[] = ['cost', 'pm', 'purchase', 'production', 'chiefEngineer']",
    )
    expect(fullSource).not.toContain('项目总')
    expect(fullSource).not.toContain('成本经理')

    for (const className of ['cost-reference-kpis', 'role-reference-kpis']) {
      expect(fullSource).toContain(className)
    }

    for (const label of [
      '商务成本执行情况',
      '执行协同总览',
      '待办任务',
      '滞后项目',
      '待审批',
      '临期合同',
      '采购执行总览',
      '现场执行协同',
      '技术闭环总览',
      '技术审核',
      '设计协调',
      '重大技术问题',
      '逾期技术事项',
    ]) {
      expect(fullSource).toContain(label)
    }

    for (const role of ['cost', 'pm', 'purchase', 'production', 'chiefEngineer']) {
      expect(indexSource).toContain(`activeRole === '${role}' && `)
    }

    expect(pmViewSource).not.toContain('成本构成分析')
    expect(pmViewSource).not.toContain('资金收支概览')
    expect(pmViewSource).toContain('itemSummary')
    expect(pmViewSource).toContain('ownerName')
    expect(pmViewSource).toContain('pendingDays')
    expect(pmViewSource).toContain('pm-reference-table')
    expect(purchaseViewSource).toContain("APPROVED: '已审批'")
    expect(purchaseViewSource).toContain("APPROVING: '审批中'")
    expect(purchaseViewSource).toContain("IN_TRANSIT: '运输中'")
    expect(purchaseViewSource).toContain('STATUS_LABEL[status] ?? \'-\'')
    expect(purchaseViewSource).not.toContain('STATUS_LABEL[status] ?? status')
    expect(purchaseViewSource).toContain('purchase-status')
    expect(purchaseViewSource).toContain('purchase-code')
    expect(purchaseViewSource).toContain('overdueOrderCols')
    expect(purchaseViewSource).toContain('pendingReceiptCols')
    expect(purchaseViewSource).toContain('partnerName')
    expect(purchaseViewSource).toContain('ownerName')
    expect(purchaseViewSource).toContain('projectName')
    expect(purchaseViewSource).toContain('durationDays')
    expect(purchaseViewSource).toContain('overdueDays')
    expect(purchaseViewSource).toContain('pendingDays')
    expect(purchaseViewSource).toContain('逾期${days}天')
    expect(purchaseViewSource).toContain('剩余${days}天')
    expect(productionViewSource).not.toContain('劳务')
    expect(productionViewSource).not.toContain('完整施工进度')
    expect(productionViewSource).not.toContain('产值')
    expect(productionViewSource).not.toContain('机械台班')
    expect(productionViewSource).not.toContain('质量巡检闭环')
    expect(productionViewSource).toContain('productionItemCols')
    expect(productionViewSource).toContain('itemSummary')
    expect(productionViewSource).toContain('partnerName')
    expect(productionViewSource).toContain('ownerName')
    expect(chiefViewSource).not.toContain('经营')
    expect(chiefViewSource).not.toContain('付款')
    expect(chiefViewSource).not.toContain('成本总览')
    expect(chiefViewSource).not.toContain('风险总览')
    expect(chiefViewSource).toContain('techItemCols')
    expect(chiefViewSource).toContain('ownerName')
    expect(chiefViewSource).toContain('overdueDays')

    expect(fullSource).not.toContain('目标成本管理')
  })

  it('removes table header helper copy from the role pages that are being closed out', () => {
    for (const copy of [
      '任务、审批、进度与履约关注',
      '需要项目经理跟进的进度异常',
      '需要提前组织履约收口',
    ]) {
      expect(pmViewSource).not.toContain(copy)
    }

    for (const copy of [
      '验收、领料、库存与分包计量',
      '生产经理 MVP 仅展示执行协同事项',
    ]) {
      expect(productionViewSource).not.toContain(copy)
    }

    for (const copy of [
      '技术审核、设计协调、重大技术问题与逾期事项',
      '持续跟进设计接口与会签闭环',
      '仅展示后端返回的逾期技术事项',
    ]) {
      expect(chiefViewSource).not.toContain(copy)
    }
  })

  it('keeps chief engineer bottom tables as independent cards', () => {
    expect(chiefViewSource).not.toContain('role-reference-chart-grid role-reference-chart-grid--2')
    expect(chiefViewSource).toContain('chief-bottom-card-grid')
    expect(chiefViewSource.match(/role-reference-bottom-panel/g)?.length ?? 0).toBeGreaterThanOrEqual(2)
  })

  it('keeps chief engineer bottom tables compact and date-only', () => {
    expect(chiefViewSource).toContain('function formatDate')
    expect(chiefViewSource).toContain('formatDate(text)')
    expect(chiefViewSource).toContain(':scroll="{ x: 690, y: 248 }"')
  })

  it('keeps project manager bottom tables as two independent cards', () => {
    expect(pmViewSource).toContain('pm-bottom-card-grid')
    expect(pmViewSource.match(/role-reference-bottom-panel/g)?.length ?? 0).toBeGreaterThanOrEqual(2)
  })

  it('keeps project manager bottom tables compact in empty states', () => {
    expect(pmViewSource).toContain(":scroll=\"{ x: 'max-content', y: 248 }\"")
    expect(pmViewSource).toContain('pm-bottom-table')
    expect(pmViewSource).toContain('.pm-bottom-table :deep(.ant-empty-image)')
  })

  it('removes purchase manager table title helper copy', () => {
    expect(purchaseViewSource).not.toContain('订单、交货、入库与库存关注')
    expect(purchaseViewSource).not.toContain('只展示后端采购经理接口返回事项')
    expect(purchaseViewSource).not.toContain('近期采购申请')
  })

  it('keeps purchase manager bottom tables as simple tabs with shared columns', () => {
    expect(purchaseViewSource).toContain("label: '采购申请'")
    expect(purchaseViewSource).toContain("label: '采购订单'")
    expect(purchaseViewSource).toContain('activeBottomTab')
    expect(purchaseViewSource).toContain('purchaseBottomRows')
    expect(purchaseViewSource).toContain(':columns="purchaseRequestCols"')
    expect(purchaseViewSource).toContain('const purchaseOrders = computed(() => props.data.purchaseOrders ?? [])')
    expect(purchaseViewSource).not.toContain('const purchaseOrders = computed(() => overdueOrders.value)')
    expect(purchaseViewSource).not.toContain('...pendingReceipts.value')
    expect(purchaseViewSource).not.toContain('uniqueBusinessRows')
  })

  it('uses truncation-driven tooltips for purchase manager summary cells', () => {
    expect(purchaseViewSource).toContain('const vSummaryOverflow')
    expect(purchaseViewSource).toContain('summaryOverflow[key] = el.scrollWidth > el.clientWidth')
    expect(purchaseViewSource).toContain('summaryTooltipTitle(')
    expect(purchaseViewSource).toContain('v-summary-overflow=')
    expect(purchaseViewSource).toContain('<a-tooltip')
    expect(purchaseViewSource).not.toContain('v-summary-title="displayText(text)"')
    expect(purchaseViewSource).not.toContain('el.title =')
    expect(purchaseViewSource).not.toContain(':ellipsis="{ tooltip: displayText(text) }"')
    expect(purchaseViewSource).not.toContain('<a-tooltip v-else-if="column.dataIndex === \'title\'"')
  })

  it('keeps dashboard summary columns aligned with the local summary rules', () => {
    expect(costViewSource).toContain('cost-alert-summary')
    expect(costViewSource).not.toContain('overflow: visible;\n  text-overflow: clip;\n  white-space: normal;')

    expect(purchaseViewSource).toContain("dataIndex: 'itemSummary'")
    expect(purchaseViewSource).toContain('function purchaseSummary')
    expect(purchaseViewSource).toContain('isInvalidSummary')
    expect(purchaseViewSource).not.toContain("dataIndex: 'title'")

    expect(chiefViewSource).toContain("title: '时效'")
    expect(chiefViewSource).toContain('function timelinessText')
    expect(chiefViewSource).toContain('return `剩余${days}天`')
    expect(chiefViewSource).toContain("return '今日到期'")
    expect(chiefViewSource).not.toContain("title: '逾期'")
  })

  it('keeps dashboard date and date-time display rules aligned with product policy', () => {
    expect(costViewSource).toContain('function formatDateTime')
    expect(costViewSource).toContain('formatDateTime(item.triggeredAt)')
    expect(costViewSource).toContain('formatDateTime(item.plannedAt)')
    expect(costViewSource).toContain('formatDate(item.payDate)')

    expect(pmViewSource).toContain('function formatDateTime')
    expect(pmViewSource).toContain('formatDateTime(text)')
    expect(pmViewSource).toContain('formatDate(text)')

    expect(purchaseViewSource).toContain('function formatDateTime')
    expect(purchaseViewSource).toContain('formatDateTime(text)')
    expect(purchaseViewSource).toContain('formatDate(text)')

    expect(productionViewSource).toContain('function formatDate')
    expect(productionViewSource).toContain('formatDate(text)')

    expect(chiefViewSource).toContain('function formatDate')
    expect(chiefViewSource).toContain('formatDate(text)')
    expect(chiefViewSource).toContain("return '今日到期'")
  })

  it('formats first-screen dashboard amount columns as ten-thousand yuan text', () => {
    expect(pmViewSource).toContain("{ title: '金额（万元）', dataIndex: 'amount'")
    expect(pmViewSource).toContain("{ title: '金额（万元）', dataIndex: 'contractAmount'")
    expect(pmViewSource).toContain('function amountText')
    expect(pmViewSource).toContain('amountText(text)')
    expect(pmViewSource).not.toContain("column.dataIndex === 'amount'\" class=\"pm-number\">\n                {{ displayText(text) }}")
    expect(pmViewSource).not.toContain("column.dataIndex === 'contractAmount'\" class=\"pm-number\">\n              {{ displayText(text) }}")

    expect(purchaseViewSource).toContain("{ title: '金额（万元）', dataIndex: 'amount'")
    expect(purchaseViewSource).toContain('function amountText')
    expect(purchaseViewSource).toContain('amountText(text)')
    expect(purchaseViewSource).not.toContain("column.dataIndex === 'amount'\" class=\"purchase-amount\">\n            {{ displayText(text) }}")

    expect(productionViewSource).toContain("{ title: '金额（万元）', dataIndex: 'amount'")
    expect(productionViewSource).toContain('function amountText')
    expect(productionViewSource).toContain('amountText(text)')
    expect(productionViewSource).not.toContain("column.dataIndex === 'amount'\" class=\"production-number\">\n            {{ displayText(text) }}")

    expect(costViewSource).toContain('amountText(item.payAmount)')
    expect(chiefViewSource).not.toContain('fmtWan')
  })

  it('localizes visible dashboard type and status values without leaking unknown English codes', () => {
    expect(pmViewSource).toContain('BUSINESS_TYPE_LABEL')
    expect(pmViewSource).toContain('businessTypeLabel(text)')
    expect(pmViewSource).toContain('PROJECT_STATUS_LABEL')
    expect(pmViewSource).toContain('projectStatusLabel(text)')
    expect(pmViewSource).toContain('?? \'-\'')

    expect(productionViewSource).toContain('STATUS_LABEL')
    expect(productionViewSource).toContain('statusLabel(text)')
    expect(productionViewSource).toContain('?? \'-\'')

    expect(chiefViewSource).toContain('TECH_STATUS_LABEL')
    expect(chiefViewSource).toContain('techStatusLabel(text)')
    expect(chiefViewSource).toContain('?? \'-\'')
  })
})
