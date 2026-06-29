<script setup lang="ts">
import { computed, reactive } from 'vue'
import {
  AuditOutlined,
  ClockCircleOutlined,
  InboxOutlined,
  ShoppingCartOutlined,
  WarningOutlined,
} from '@ant-design/icons-vue'
import type { DashboardBusinessItemVO, PurchaseManagerDashboardVO } from '@/types/dashboard'
import { fmtNum, fmtWan } from '../utils/formatUtils'

const props = defineProps<{
  data: PurchaseManagerDashboardVO
  loading: boolean
}>()

const STATUS_LABEL: Record<string, string> = {
  DRAFT: '草稿',
  PENDING: '待审批',
  APPROVING: '审批中',
  APPROVED: '已审批',
  REJECTED: '已驳回',
  CANCELLED: '已取消',
  COMPLETED: '已完成',
  PARTIAL_RECEIVED: '部分入库',
  RECEIVED: '已入库',
}

const overdueOrderCols = [
  { title: '单号', dataIndex: 'code', width: 156 },
  { title: '事项摘要', dataIndex: 'title', width: 188, ellipsis: true },
  { title: '供应商', dataIndex: 'partnerName', width: 104, ellipsis: true },
  { title: '状态', dataIndex: 'status', width: 84 },
  { title: '应交日期', dataIndex: 'date', width: 112 },
  { title: '交期状态', key: 'overdueInfo', width: 96 },
]

const pendingReceiptCols = [
  { title: '单号', dataIndex: 'code', width: 156 },
  { title: '事项摘要', dataIndex: 'title', width: 188, ellipsis: true },
  { title: '供应商', dataIndex: 'partnerName', width: 104, ellipsis: true },
  { title: '状态', dataIndex: 'status', width: 84 },
  { title: '应验日期', dataIndex: 'date', width: 112 },
  { title: '验收状态', key: 'pendingInfo', width: 96 },
]

const purchaseRequestCols = [
  { title: '申请单号', dataIndex: 'code', width: 164 },
  { title: '申请事项/物资', dataIndex: 'title', width: 240, ellipsis: true },
  { title: '申请部门/项目', dataIndex: 'projectName', width: 132, ellipsis: true },
  { title: '申请人', dataIndex: 'ownerName', width: 92, ellipsis: true },
  { title: '金额', dataIndex: 'amount', width: 112, align: 'right' as const },
  { title: '申请日期', dataIndex: 'date', width: 136 },
  { title: '当前状态', dataIndex: 'status', width: 92 },
  { title: '紧急程度', key: 'urgency', width: 88 },
]

function statusLabel(status?: string) {
  return status ? (STATUS_LABEL[status] ?? status) : '-'
}

function statusTone(status?: string) {
  if (status === 'APPROVED' || status === 'COMPLETED' || status === 'RECEIVED') return 'success'
  if (status === 'APPROVING' || status === 'PENDING' || status === 'PARTIAL_RECEIVED') {
    return 'warning'
  }
  if (status === 'REJECTED' || status === 'CANCELLED') return 'danger'
  return 'default'
}

function displayText(value?: string | number) {
  return value === undefined || value === null || value === '' ? '-' : String(value)
}

const summaryOverflow = reactive<Record<string, boolean>>({})

function summaryKey(prefix: string, record: DashboardBusinessItemVO) {
  return `${prefix}-${displayText(record.sourceId || record.code || record.title)}`
}

function summaryTooltipTitle(key: string, value?: string | number) {
  return summaryOverflow[key] ? displayText(value) : undefined
}

function syncSummaryOverflow(el: HTMLElement, key?: string) {
  if (!key) return
  window.requestAnimationFrame(() => {
    summaryOverflow[key] = el.scrollWidth > el.clientWidth
  })
}

const vSummaryOverflow = {
  mounted(el: HTMLElement, binding: { value?: string }) {
    syncSummaryOverflow(el, binding.value)
  },
  updated(el: HTMLElement, binding: { value?: string }) {
    syncSummaryOverflow(el, binding.value)
  },
}

function durationDays(value?: number | string) {
  if (value === undefined || value === null || value === '') return null
  const days = Number(value)
  return Number.isFinite(days) ? Math.max(Math.round(days), 0) : null
}

function sortNumber(value?: number | string, desc = false) {
  const days = durationDays(value)
  if (days === null) return desc ? -1 : Number.MAX_SAFE_INTEGER
  return desc ? -days : days
}

function sortDate(value?: string, desc = false) {
  if (!value) return desc ? Number.MIN_SAFE_INTEGER : Number.MAX_SAFE_INTEGER
  const timestamp = new Date(`${value}T00:00:00`).getTime()
  if (Number.isNaN(timestamp)) return desc ? Number.MIN_SAFE_INTEGER : Number.MAX_SAFE_INTEGER
  return desc ? -timestamp : timestamp
}

function sortCode(value?: string) {
  return value ?? ''
}

const overdueOrders = computed(() =>
  [...(props.data.overdueOrders ?? [])].sort(
    (a, b) =>
      sortNumber(a.overdueDays, true) - sortNumber(b.overdueDays, true) ||
      sortDate(a.date) - sortDate(b.date) ||
      sortCode(a.code).localeCompare(sortCode(b.code), 'zh-CN'),
  ),
)

const pendingReceipts = computed(() =>
  [...(props.data.pendingReceipts ?? [])].sort(
    (a, b) =>
      sortNumber(a.pendingDays) - sortNumber(b.pendingDays) ||
      sortDate(a.date) - sortDate(b.date) ||
      sortCode(a.code).localeCompare(sortCode(b.code), 'zh-CN'),
  ),
)

const recentRequests = computed(() =>
  [...(props.data.recentRequests ?? [])].sort(
    (a, b) =>
      sortDate(a.date, true) - sortDate(b.date, true) ||
      sortCode(a.code).localeCompare(sortCode(b.code), 'zh-CN'),
  ),
)

function overdueInfo(record: DashboardBusinessItemVO) {
  const days = durationDays(record.overdueDays)
  if (days === null) return '-'
  if (days === 0) return '今日'
  return `逾期${days}天`
}

function pendingInfo(record: DashboardBusinessItemVO) {
  const days = durationDays(record.pendingDays)
  if (days === null) return '-'
  if (days === 0) return '今日'
  return `剩余${days}天`
}
</script>

<template>
  <div class="role-reference-shell">
    <section class="role-reference-kpis role-reference-kpis--4">
      <article class="role-reference-kpi" style="--kpi-accent: #2f7cf6">
        <div class="role-reference-kpi-title">待审批采购</div>
        <div class="role-reference-kpi-value">
          {{ fmtNum(data.pendingRequestCount) }} <small>项</small>
        </div>
        <div class="role-reference-kpi-meta">
          <span><AuditOutlined /> 采购申请</span>
        </div>
      </article>
      <article class="role-reference-kpi" style="--kpi-accent: #14b8a6">
        <div class="role-reference-kpi-title">执行中订单</div>
        <div class="role-reference-kpi-value is-cyan">
          {{ fmtNum(data.activeOrderCount) }} <small>单</small>
        </div>
        <div class="role-reference-kpi-meta">
          <span><ShoppingCartOutlined /> 交货跟踪</span>
        </div>
      </article>
      <article class="role-reference-kpi" style="--kpi-accent: #ef4444">
        <div class="role-reference-kpi-title">逾期交货</div>
        <div class="role-reference-kpi-value is-danger">
          {{ fmtNum(data.overdueDeliveryCount) }} <small>单</small>
        </div>
        <div class="role-reference-kpi-meta">
          <span><WarningOutlined /> 需催办</span>
        </div>
      </article>
      <article class="role-reference-kpi" style="--kpi-accent: #f97316">
        <div class="role-reference-kpi-title">库存预警</div>
        <div class="role-reference-kpi-value is-warning">
          {{ fmtNum(data.lowStockItemCount) }} <small>项</small>
        </div>
        <div class="role-reference-kpi-meta">
          <span><InboxOutlined /> 低库存</span>
        </div>
      </article>
    </section>

    <section class="role-reference-main-grid">
      <div class="role-reference-panel role-reference-analysis">
        <div class="role-reference-panel-head">
          <div class="purchase-panel-title">
            <strong>采购执行总览</strong>
          </div>
        </div>
        <div class="role-reference-summary-grid role-reference-summary-grid--hero">
          <div class="role-reference-summary-item">
            <span>执行中订单</span>
            <b>{{ fmtNum(data.activeOrderCount) }} 单</b>
          </div>
          <div class="role-reference-summary-item">
            <span>采购订单金额</span>
            <b>{{ fmtWan(data.totalOrderAmount) }} 万元</b>
          </div>
          <div class="role-reference-summary-item">
            <span>待验收入库</span>
            <b>{{ fmtNum(data.pendingReceiptCount) }} 项</b>
          </div>
          <div class="role-reference-summary-item">
            <span>库存预警</span>
            <b>{{ fmtNum(data.lowStockItemCount) }} 项</b>
          </div>
        </div>
        <div class="role-reference-focus-list">
          <div>
            <strong>采购审批清理</strong>
            <span>优先处理待审批采购申请，避免影响后续下单与供应商排产。</span>
          </div>
          <div>
            <strong>交货催办</strong>
            <span>聚焦逾期交货订单，确认供应商、交货日期和入库计划。</span>
          </div>
          <div>
            <strong>库存补给</strong>
            <span>结合低库存预警和待验收入库事项，安排补采或到货确认。</span>
          </div>
        </div>
      </div>

      <aside class="role-reference-side-stack">
        <div class="role-reference-panel role-mini-panel is-red">
          <div class="role-reference-panel-head mini">
            <strong
              >逾期交货 <b>（{{ data.overdueOrders.length }}）</b></strong
            >
          </div>
          <a-table
            :columns="overdueOrderCols"
            :data-source="overdueOrders"
            :loading="loading"
            :pagination="false"
            :scroll="{ x: 756, y: 216 }"
            size="small"
            row-key="sourceId"
            class="role-reference-table purchase-reference-table"
          >
            <template #bodyCell="{ column, text, record }">
              <span v-if="column.dataIndex === 'status'" :class="['purchase-status', statusTone(text)]">
                {{ statusLabel(text) }}
              </span>
              <span v-else-if="column.dataIndex === 'code'" class="purchase-code">
                {{ displayText(text) }}
              </span>
              <span v-else-if="column.dataIndex === 'partnerName'" class="purchase-muted">
                <a-tooltip :title="displayText(text)">
                  <span class="purchase-ellipsis purchase-muted">{{ displayText(text) }}</span>
                </a-tooltip>
              </span>
              <span v-else-if="column.dataIndex === 'date'" class="purchase-muted">
                {{ displayText(text) }}
              </span>
              <a-tooltip
                v-else-if="column.dataIndex === 'title'"
                :title="summaryTooltipTitle(summaryKey('overdue', record as DashboardBusinessItemVO), text)"
              >
                <span
                  class="purchase-ellipsis"
                  v-summary-overflow="summaryKey('overdue', record as DashboardBusinessItemVO)"
                >
                  {{ displayText(text) }}
                </span>
              </a-tooltip>
              <span v-else-if="column.key === 'overdueInfo'" class="purchase-days danger">
                {{ overdueInfo(record) }}
              </span>
            </template>
          </a-table>
        </div>
        <div class="role-reference-panel role-mini-panel is-orange">
          <div class="role-reference-panel-head mini">
            <strong
              >待验收入库 <b>（{{ data.pendingReceipts.length }}）</b></strong
            >
          </div>
          <a-table
            :columns="pendingReceiptCols"
            :data-source="pendingReceipts"
            :loading="loading"
            :pagination="false"
            :scroll="{ x: 756, y: 216 }"
            size="small"
            row-key="sourceId"
            class="role-reference-table purchase-reference-table"
          >
            <template #bodyCell="{ column, text, record }">
              <span v-if="column.dataIndex === 'status'" :class="['purchase-status', statusTone(text)]">
                {{ statusLabel(text) }}
              </span>
              <span v-else-if="column.dataIndex === 'code'" class="purchase-code">
                {{ displayText(text) }}
              </span>
              <span v-else-if="column.dataIndex === 'partnerName'" class="purchase-muted">
                <a-tooltip :title="displayText(text)">
                  <span class="purchase-ellipsis purchase-muted">{{ displayText(text) }}</span>
                </a-tooltip>
              </span>
              <span v-else-if="column.dataIndex === 'date'" class="purchase-muted">
                {{ displayText(text) }}
              </span>
              <a-tooltip
                v-else-if="column.dataIndex === 'title'"
                :title="summaryTooltipTitle(summaryKey('receipt', record as DashboardBusinessItemVO), text)"
              >
                <span
                  class="purchase-ellipsis"
                  v-summary-overflow="summaryKey('receipt', record as DashboardBusinessItemVO)"
                >
                  {{ displayText(text) }}
                </span>
              </a-tooltip>
              <span v-else-if="column.key === 'pendingInfo'" class="purchase-days warning">
                {{ pendingInfo(record) }}
              </span>
            </template>
          </a-table>
        </div>
      </aside>
    </section>

    <section class="role-reference-panel role-reference-bottom-panel">
      <div class="role-reference-panel-head">
        <div>
          <strong>近期采购申请</strong>
        </div>
      </div>
      <a-table
        :columns="purchaseRequestCols"
        :data-source="recentRequests"
        :loading="loading"
        :pagination="false"
        :scroll="{ x: 1136, y: 248 }"
        size="small"
        row-key="sourceId"
        class="role-reference-table purchase-reference-table"
      >
        <template #bodyCell="{ column, text, record }">
          <span v-if="column.dataIndex === 'status'" :class="['purchase-status', statusTone(text)]">
            {{ statusLabel(text) }}
          </span>
          <span v-else-if="column.dataIndex === 'code'" class="purchase-code">
            {{ displayText(text) }}
          </span>
          <span v-else-if="column.dataIndex === 'ownerName'" class="purchase-muted">
            {{ displayText(text) }}
          </span>
          <span v-else-if="column.dataIndex === 'projectName'" class="purchase-muted">
            <a-tooltip :title="displayText(text)">
              <span class="purchase-ellipsis purchase-muted">{{ displayText(text) }}</span>
            </a-tooltip>
          </span>
          <span v-else-if="column.dataIndex === 'amount'" class="purchase-amount">
            {{ displayText(text) }}
          </span>
          <a-tooltip v-else-if="column.dataIndex === 'date'" :title="displayText(text)">
            <span class="purchase-date-cell">{{ displayText(text) }}</span>
          </a-tooltip>
          <a-tooltip
            v-else-if="column.dataIndex === 'title'"
            :title="summaryTooltipTitle(summaryKey('request', record as DashboardBusinessItemVO), text)"
          >
            <span
              class="purchase-ellipsis"
              v-summary-overflow="summaryKey('request', record as DashboardBusinessItemVO)"
            >
              {{ displayText(text) }}
            </span>
          </a-tooltip>
          <span v-else-if="column.key === 'urgency'" class="purchase-muted">-</span>
        </template>
      </a-table>
    </section>
  </div>
</template>

<style scoped>
.purchase-panel-title {
  display: grid;
  gap: 2px;
}

.purchase-panel-title span {
  display: block;
  font-size: 12px;
  line-height: 18px;
}

.purchase-reference-table :deep(.ant-table) {
  color: #243044;
  font-size: 12px;
  table-layout: fixed;
}

.purchase-reference-table :deep(.ant-table-thead > tr > th) {
  height: 30px;
  padding: 0 8px;
  color: #536176;
  background: #fbfcff;
  font-size: 12px;
  font-weight: 700;
  line-height: 30px;
}

.purchase-reference-table :deep(.ant-table-tbody > tr > td) {
  min-height: 30px;
  padding: 5px 8px;
  color: #243044;
  font-size: 12px;
  line-height: 16px;
  vertical-align: middle;
}

.purchase-reference-table :deep(.ant-table-cell) {
  white-space: nowrap;
}

.purchase-code {
  color: #64748b;
  font-variant-numeric: tabular-nums;
}

.purchase-ellipsis {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.purchase-date-cell {
  display: block;
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}

.purchase-muted {
  color: #64748b;
}

.purchase-amount,
.purchase-days {
  font-variant-numeric: tabular-nums;
}

.purchase-days {
  font-weight: 700;
}

.purchase-days.danger {
  color: #ef4444;
}

.purchase-days.warning {
  color: #d97706;
}

.purchase-status {
  font-weight: 700;
}

.purchase-status.success {
  color: #16a34a;
}

.purchase-status.warning {
  color: #d97706;
}

.purchase-status.danger {
  color: #ef4444;
}

.purchase-status.default {
  color: #64748b;
}
</style>
