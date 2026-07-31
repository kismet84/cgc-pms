import { describe, expect, it } from 'vitest'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const currentDir = dirname(fileURLToPath(import.meta.url))
const read = (p: string) => readFileSync(resolve(currentDir, '..', p), 'utf-8')
const readRoot = (p: string) => readFileSync(resolve(currentDir, '..', '..', '..', p), 'utf-8')

// Read all split files to form the full source picture
const indexSource = read('index.vue')
const composableSource = read('composables/useDashboardData.ts')
const chartOptsSource = read('utils/chartOptions.ts')
const pmViewSource = read('components/DashboardPmView.vue')
const bmViewSource = read('components/DashboardBmView.vue')
const costViewSource = read('components/DashboardCostView.vue')
const purchaseViewSource = read('components/DashboardPurchaseView.vue')
const productionViewSource = read('components/DashboardProductionView.vue')
const chiefViewSource = read('components/DashboardChiefEngineerView.vue')
const financeViewSource = read('components/DashboardFinanceView.vue')
const mgmtViewSource = read('components/DashboardMgmtView.vue')
const downloadUtilSource = readRoot('utils/download.ts')

const allViews = [
  pmViewSource,
  bmViewSource,
  costViewSource,
  purchaseViewSource,
  productionViewSource,
  chiefViewSource,
  financeViewSource,
  mgmtViewSource,
].join('\n')
const allSource = [indexSource, composableSource, chartOptsSource, ...allViews.split('\n')].join(
  '\n',
)

describe('Dashboard reference fidelity', () => {
  it('keeps the approved dashboard copy and adds the reference chart/table structure', () => {
    for (const label of [
      '执行协同总览',
      '商务成本执行情况',
      '临期合同（30天内到期）',
      '项目经理',
      '商务经理',
      '采购经理',
      '生产经理',
      '总工程师',
      '财务',
      '管理层',
    ]) {
      expect(allSource).toContain(label)
    }

    expect(indexSource).toContain(
      "const roleTabOrder: DashboardRole[] = ['cost', 'pm', 'purchase', 'production', 'chiefEngineer']",
    )
    expect(pmViewSource).toContain('pendingApprovals')
    expect(pmViewSource).toContain('pmTaskCols')
    expect(pmViewSource).toContain('itemSummary')
    expect(pmViewSource).toContain('ownerName')
    expect(pmViewSource).toContain('pendingDays')
    expect(pmViewSource).toContain('pm-reference-table')
    expect(purchaseViewSource).toContain('采购执行总览')
    expect(purchaseViewSource).toContain('逾期交货')
    expect(purchaseViewSource).toContain('库存预警')
    expect(purchaseViewSource).toContain("title: '单号'")
    expect(purchaseViewSource).toContain("title: '事项摘要'")
    expect(purchaseViewSource).toContain('statusLabel(text)')
    expect(purchaseViewSource).toContain("APPROVED: '已审批'")
    expect(purchaseViewSource).toContain("APPROVING: '审批中'")
    expect(purchaseViewSource).toContain('purchase-reference-table')
    expect(purchaseViewSource).toContain('supplierScores')
    expect(purchaseViewSource).toContain('partnerId')
    expect(purchaseViewSource).toContain('orderCount')
    expect(purchaseViewSource).toContain('lateCompletedCount')
    expect(purchaseViewSource).toContain('overdueIncompleteCount')
    expect(purchaseViewSource).toContain('onTimeDeliveryRate')
    expect(purchaseViewSource).toContain('performanceScore')
    expect(purchaseViewSource).toContain('供应商采购订单交期表现')
    expect(purchaseViewSource).toContain('仅展示采购订单交期表现，不代表供应商综合评级')
    expect(purchaseViewSource).toContain('暂无供应商采购订单交期表现数据')
    expect(purchaseViewSource).not.toContain('综合供应商评级')
    expect(purchaseViewSource).not.toContain('综合评分')
    expect(purchaseViewSource).not.toContain('评级配置器')
    expect(purchaseViewSource).not.toContain('订单、交货、入库与库存关注')
    expect(purchaseViewSource).not.toContain('只展示后端采购经理接口返回事项')
    expect(purchaseViewSource).toContain('overdueOrderCols')
    expect(purchaseViewSource).toContain('pendingReceiptCols')
    expect(purchaseViewSource).toContain('const overdueOrders = computed(() =>')
    expect(purchaseViewSource).toContain('const pendingReceipts = computed(() =>')
    expect(purchaseViewSource).toContain('const recentRequests = computed(() =>')
    expect(purchaseViewSource).toContain("title: '供应商'")
    expect(purchaseViewSource).toContain("dataIndex: 'partnerName'")
    expect(purchaseViewSource).toContain("title: '应交日期'")
    expect(purchaseViewSource).toContain("title: '应验日期'")
    expect(purchaseViewSource).toContain("title: '交期状态'")
    expect(purchaseViewSource).toContain("title: '验收状态'")
    expect(purchaseViewSource).toContain("title: '申请人'")
    expect(purchaseViewSource).toContain("dataIndex: 'ownerName'")
    expect(purchaseViewSource).toContain("title: '申请部门/项目'")
    expect(purchaseViewSource).toContain("dataIndex: 'projectName'")
    expect(purchaseViewSource).toContain("title: '申请单号'")
    expect(purchaseViewSource).toContain("title: '申请事项/物资'")
    expect(purchaseViewSource).toContain("title: '申请日期'")
    expect(purchaseViewSource).toContain("title: '当前状态'")
    expect(purchaseViewSource).toContain("title: '紧急程度'")
    expect(purchaseViewSource).toContain("key: 'overdueInfo'")
    expect(purchaseViewSource).toContain("key: 'pendingInfo'")
    expect(purchaseViewSource).toContain('overdueInfo(record)')
    expect(purchaseViewSource).toContain('receiptTimeliness(record)')
    expect(purchaseViewSource).toContain('durationDays(record.overdueDays)')
    expect(purchaseViewSource).toContain('receiptTimeliness(record)')
    expect(purchaseViewSource).toContain('return `逾期${days}天`')
    expect(purchaseViewSource).toContain('return `剩余${days}天`')
    expect(purchaseViewSource).toContain('purchase-ellipsis')
    expect(purchaseViewSource).toContain('purchase-date-cell')
    expect(purchaseViewSource).toContain(':scroll="{ x: 756, y: 216 }"')
    expect(purchaseViewSource).toContain(':scroll="{ x: 1136, y: 248 }"')
    expect(productionViewSource).toContain('现场执行协同')
    expect(productionViewSource).toContain('验收记录')
    expect(productionViewSource).toContain('分包计量')
    expect(productionViewSource).toContain('productionItemCols')
    expect(productionViewSource).toContain('productionAmountCols')
    expect(productionViewSource).toContain('itemSummary')
    expect(productionViewSource).toContain('partnerName')
    expect(productionViewSource).toContain('ownerName')
    expect(productionViewSource).toContain('pendingDays')
    expect(chiefViewSource).toContain('技术闭环总览')
    expect(chiefViewSource).toContain('技术审核')
    expect(chiefViewSource).toContain('设计协调')
    expect(chiefViewSource).toContain('重大技术问题')
    expect(chiefViewSource).toContain('逾期技术事项')
    expect(chiefViewSource).toContain('techItemCols')
    expect(chiefViewSource).toContain('技术事项摘要')
    expect(chiefViewSource).toContain('ownerName')
    expect(chiefViewSource).toContain('overdueDays')
    expect(pmViewSource).toContain('pm-bottom-card-grid')
    expect(chiefViewSource).toContain('chief-bottom-card-grid')
    expect(indexSource).toContain("activeRole === 'chiefEngineer' && chiefEngineerData")
    expect(pmViewSource).not.toContain('pmCostCompositionOption')
    expect(pmViewSource).not.toContain('pmFundingOverviewOption')
    expect(pmViewSource).not.toContain('成本构成分析')
    expect(pmViewSource).not.toContain('资金收支概览')
    expect(productionViewSource).not.toContain('劳务')
    expect(productionViewSource).not.toContain('完整施工进度')
    expect(productionViewSource).not.toContain('产值')
    expect(productionViewSource).not.toContain('机械台班')
    expect(productionViewSource).not.toContain('质量巡检闭环')
    expect(chiefViewSource).not.toContain('经营')
    expect(chiefViewSource).not.toContain('付款')
    expect(chiefViewSource).not.toContain('成本总览')
    expect(chiefViewSource).not.toContain('风险总览')

    expect(allSource).not.toContain('成本目标管理')
  })

  it('does not keep local mock data arrays in the cost manager dashboard', () => {
    for (const forbidden of [
      'fallbackSubjects',
      'budgetAlerts = [',
      'overdueItems = [',
      'pendingPayments = [',
      'fallbackLedgerRows',
      '74,510.00',
      '13,680.25',
    ]) {
      expect(costViewSource).not.toContain(forbidden)
    }
  })

  it('wires cost manager contract fields from the API type into the component', () => {
    const typesSource = readFileSync(resolve(currentDir, '../../../types/dashboard.ts'), 'utf-8')
    for (const field of [
      'trendPoints',
      'subjectRankings',
      'overdueItems',
      'pendingPayments',
      'ledgerRows',
      'ledgerTotal',
    ]) {
      expect(typesSource).toContain(field)
      expect(costViewSource).toContain(field)
    }
  })

  it('wires purchase manager lightweight business item fields from the API type into the component', () => {
    const typesSource = readFileSync(resolve(currentDir, '../../../types/dashboard.ts'), 'utf-8')
    for (const field of [
      'partnerName',
      'ownerName',
      'projectName',
      'date',
      'overdueDays',
      'pendingDays',
    ]) {
      expect(typesSource).toContain(field)
      expect(purchaseViewSource).toContain(field)
    }
  })

  it('uses receipt date instead of pendingDays zero to label future pending receipts', () => {
    expect(purchaseViewSource).toContain('function receiptTimeliness')
    expect(purchaseViewSource).toContain('Date.UTC')
    expect(purchaseViewSource).toContain('return `剩余${days}天`')
    expect(purchaseViewSource).toContain("return '今日'")
    expect(purchaseViewSource).toContain('return `逾期${Math.abs(days)}天`')
    expect(purchaseViewSource).toContain('receiptTimeliness(record)')
    expect(purchaseViewSource).not.toContain(
      "if (days === 0) return '今日'\n  return `剩余${days}天`",
    )
  })

  it('uses receipt date for production recent receipt timing without touching requisitions', () => {
    expect(productionViewSource).toContain('function receiptTimingText')
    expect(productionViewSource).toContain('Date.UTC')
    expect(productionViewSource).toContain('return `剩余${days}天`')
    expect(productionViewSource).toContain("return '今日'")
    expect(productionViewSource).toContain('return `已过${Math.abs(days)}天`')
    expect(productionViewSource).toContain('receiptTimingText(record as DashboardBusinessItemVO)')
    expect(productionViewSource).toContain(
      'pendingText((record as DashboardBusinessItemVO).pendingDays)',
    )
  })

  it('wires role-specific summary fields without changing legacy dashboard pages', () => {
    const typesSource = readFileSync(resolve(currentDir, '../../../types/dashboard.ts'), 'utf-8')
    for (const field of ['itemSummary', 'ownerName', 'amount', 'pendingDays']) {
      expect(typesSource).toContain(field)
      expect(pmViewSource).toContain(field)
    }
    for (const field of ['itemSummary', 'partnerName', 'ownerName', 'pendingDays']) {
      expect(typesSource).toContain(field)
      expect(productionViewSource).toContain(field)
    }
    for (const field of ['itemSummary', 'ownerName', 'overdueDays']) {
      expect(typesSource).toContain(field)
      expect(chiefViewSource).toContain(field)
    }

    expect(financeViewSource).not.toContain('itemSummary')
    expect(mgmtViewSource).not.toContain('itemSummary')
    expect(bmViewSource).not.toContain('itemSummary')
  })

  it('wires dashboard header controls instead of leaving static selectors and buttons', () => {
    expect(indexSource).toContain('v-model:value="selectedMonth"')
    expect(indexSource).toContain('monthOptions')
    expect(indexSource).toContain('@click="toggleFullscreen"')
    expect(indexSource).toContain('requestFullscreen')
    expect(indexSource).not.toContain('<a-select value="2024-05"')
    expect(indexSource).not.toContain(
      '<a-button type="text">\n          <template #icon><FullscreenOutlined',
    )
  })

  it('wires cost ledger tabs, filters, export, and pagination controls', () => {
    for (const expected of [
      'activeLedgerTab',
      'subjectFilter',
      'statusFilter',
      'ledgerKeyword',
      'pagedLedgerRows',
      'resetLedgerFilters',
      'exportLedgerCsv',
      'viewLedgerRow',
      'drillLedgerRow',
      'v-model:current="currentPage"',
      'v-model:value="pageSize"',
    ]) {
      expect(costViewSource).toContain(expected)
    }

    expect(costViewSource).toContain('@click="viewLedgerRow')
    expect(costViewSource).toContain('@click="drillLedgerRow')
    expect(costViewSource).not.toContain('<a-select value="all" size="small"')
    expect(costViewSource).not.toContain('<a-button size="small">重置</a-button>')
    expect(costViewSource).not.toContain(
      '<a-button size="small" type="primary" ghost>导出</a-button>',
    )
    expect(costViewSource).not.toContain('<a-pagination :current="1"')
  })

  it('uses the shared blob download helper so export clicks become real downloads in the browser', () => {
    expect(downloadUtilSource).toContain('document.body.appendChild(link)')
    expect(downloadUtilSource).toContain('link.click()')
    expect(downloadUtilSource).toContain('setTimeout(() => URL.revokeObjectURL(url), 1000)')
    expect(costViewSource).toContain("import { downloadBlobFile } from '@/utils/download'")
    expect(costViewSource).toContain("downloadBlobFile(blob, '成本列表.csv')")
  })

  it('wires the cost trend cumulative/monthly segmented control', () => {
    expect(costViewSource).toContain('trendMode')
    expect(costViewSource).toContain('displayedTrendPoints')
    expect(costViewSource).toContain('v-model:value="trendMode"')
    expect(costViewSource).not.toContain(':value="\'累计\'"')
  })
})
