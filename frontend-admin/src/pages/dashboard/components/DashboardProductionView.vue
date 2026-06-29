<script setup lang="ts">
import {
  AuditOutlined,
  FileDoneOutlined,
  InboxOutlined,
  ToolOutlined,
  WarningOutlined,
} from '@ant-design/icons-vue'
import type { DashboardBusinessItemVO, ProductionManagerDashboardVO } from '@/types/dashboard'
import { fmtNum, fmtWan } from '../utils/formatUtils'

defineProps<{
  data: ProductionManagerDashboardVO
  loading: boolean
}>()

const productionItemCols = [
  { title: '单号', dataIndex: 'code', width: 156 },
  { title: '事项摘要', dataIndex: 'itemSummary', width: 220, ellipsis: true },
  { title: '协作方', dataIndex: 'partnerName', width: 104, ellipsis: true },
  { title: '责任人', dataIndex: 'ownerName', width: 92, ellipsis: true },
  { title: '状态', dataIndex: 'status', width: 88 },
  { title: '日期', dataIndex: 'date', width: 112 },
  { title: '时效', key: 'pendingDays', width: 84 },
]

const productionAmountCols = [
  { title: '单号', dataIndex: 'code', width: 156 },
  { title: '事项摘要', dataIndex: 'itemSummary', width: 260, ellipsis: true },
  { title: '协作方', dataIndex: 'partnerName', width: 120, ellipsis: true },
  { title: '责任人', dataIndex: 'ownerName', width: 92, ellipsis: true },
  { title: '金额', dataIndex: 'amount', width: 120, align: 'right' as const },
  { title: '状态', dataIndex: 'status', width: 92 },
  { title: '日期', dataIndex: 'date', width: 112 },
]

const STATUS_LABEL: Record<string, string> = {
  DRAFT: '草稿',
  SUBMITTED: '已提交',
  APPROVING: '审批中',
  APPROVED: '已审批',
  REJECTED: '已驳回',
  CANCELLED: '已取消',
  PENDING: '待处理',
  PROCESSING: '处理中',
  CONFIRMED: '已确认',
  COMPLETED: '已完成',
  CLOSED: '已关闭',
  PENDING_STOCK_OUT: '待出库',
  STOCKED_OUT: '已出库',
  PARTIAL_RECEIVED: '部分验收',
  RECEIVED: '已验收',
}

function displayText(value?: string | number) {
  return value === undefined || value === null || value === '' ? '-' : String(value)
}

function itemSummary(record: DashboardBusinessItemVO) {
  return displayText(record.itemSummary)
}

function statusLabel(value?: string | number) {
  const key = displayText(value)
  return key === '-' ? '-' : (STATUS_LABEL[key] ?? '-')
}

function pendingText(value?: number) {
  if (value === undefined || value === null) return '-'
  if (value === 0) return '今日'
  return `${Math.max(Math.round(value), 0)}天`
}
</script>

<template>
  <div class="role-reference-shell">
    <section class="role-reference-kpis role-reference-kpis--4">
      <article class="role-reference-kpi" style="--kpi-accent: #2f7cf6">
        <div class="role-reference-kpi-title">验收记录</div>
        <div class="role-reference-kpi-value">
          {{ fmtNum(data.receiptCount) }} <small>项</small>
        </div>
        <div class="role-reference-kpi-meta">
          <span><FileDoneOutlined /> 到货确认</span>
        </div>
      </article>
      <article class="role-reference-kpi" style="--kpi-accent: #14b8a6">
        <div class="role-reference-kpi-title">领料申请</div>
        <div class="role-reference-kpi-value is-cyan">
          {{ fmtNum(data.requisitionCount) }} <small>项</small>
        </div>
        <div class="role-reference-kpi-meta">
          <span><InboxOutlined /> 现场用料</span>
        </div>
      </article>
      <article class="role-reference-kpi" style="--kpi-accent: #f97316">
        <div class="role-reference-kpi-title">待出库</div>
        <div class="role-reference-kpi-value is-warning">
          {{ fmtNum(data.pendingStockOutCount) }} <small>项</small>
        </div>
        <div class="role-reference-kpi-meta">
          <span><WarningOutlined /> 仓库协同</span>
        </div>
      </article>
      <article class="role-reference-kpi" style="--kpi-accent: #8b5cf6">
        <div class="role-reference-kpi-title">分包计量</div>
        <div class="role-reference-kpi-value">
          {{ fmtNum(data.subMeasureCount) }} <small>项</small>
        </div>
        <div class="role-reference-kpi-meta">
          <span><ToolOutlined /> 计量确认</span>
        </div>
      </article>
    </section>

    <section class="role-reference-main-grid">
      <div class="role-reference-panel role-reference-analysis">
        <div class="role-reference-panel-head">
          <div>
            <strong>现场执行协同</strong>
          </div>
        </div>
        <div class="role-reference-summary-grid role-reference-summary-grid--hero">
          <div class="role-reference-summary-item">
            <span>验收记录</span>
            <b>{{ fmtNum(data.receiptCount) }} 项</b>
          </div>
          <div class="role-reference-summary-item">
            <span>领料申请</span>
            <b>{{ fmtNum(data.requisitionCount) }} 项</b>
          </div>
          <div class="role-reference-summary-item">
            <span>库存预警</span>
            <b>{{ fmtNum(data.lowStockItemCount) }} 项</b>
          </div>
          <div class="role-reference-summary-item">
            <span>已确认计量</span>
            <b>{{ fmtWan(data.confirmedMeasureAmount) }} 万元</b>
          </div>
        </div>
        <div class="role-reference-focus-list">
          <div>
            <strong>验收与领料衔接</strong>
            <span>核对近期验收记录与领料申请，减少材料到场后等待。</span>
          </div>
          <div>
            <strong>待出库处理</strong>
            <span>优先处理待出库事项，确保现场用料及时流转。</span>
          </div>
          <div>
            <strong>分包计量跟踪</strong>
            <span>关注近期分包计量，配合商务侧完成计量确认。</span>
          </div>
        </div>
      </div>

      <aside class="role-reference-side-stack">
        <div class="role-reference-panel role-mini-panel is-blue">
          <div class="role-reference-panel-head mini">
            <strong
              >近期验收 <b>（{{ data.recentReceipts.length }}）</b></strong
            >
          </div>
          <a-table
            :columns="productionItemCols"
            :data-source="data.recentReceipts"
            :loading="loading"
            :pagination="false"
            :scroll="{ x: 856, y: 216 }"
            size="small"
            row-key="sourceId"
            class="role-reference-table production-reference-table"
          >
            <template #bodyCell="{ column, text, record }">
              <a-tooltip v-if="column.dataIndex === 'itemSummary'" :title="itemSummary(record as DashboardBusinessItemVO)">
                <span class="production-ellipsis">{{ itemSummary(record as DashboardBusinessItemVO) }}</span>
              </a-tooltip>
              <a-tooltip v-else-if="column.dataIndex === 'partnerName'" :title="displayText(text)">
                <span class="production-ellipsis production-muted">{{ displayText(text) }}</span>
              </a-tooltip>
              <span v-else-if="column.dataIndex === 'ownerName'" class="production-muted">
                {{ displayText(text) }}
              </span>
              <span v-else-if="column.dataIndex === 'status'" class="production-status">
                {{ statusLabel(text) }}
              </span>
              <span v-else-if="column.dataIndex === 'date'" class="production-date">
                {{ displayText(text) }}
              </span>
              <span v-else-if="column.key === 'pendingDays'" class="production-days">
                {{ pendingText((record as DashboardBusinessItemVO).pendingDays) }}
              </span>
            </template>
          </a-table>
        </div>
        <div class="role-reference-panel role-mini-panel is-orange">
          <div class="role-reference-panel-head mini">
            <strong
              >近期领料 <b>（{{ data.recentRequisitions.length }}）</b></strong
            >
          </div>
          <a-table
            :columns="productionItemCols"
            :data-source="data.recentRequisitions"
            :loading="loading"
            :pagination="false"
            :scroll="{ x: 856, y: 216 }"
            size="small"
            row-key="sourceId"
            class="role-reference-table production-reference-table"
          >
            <template #bodyCell="{ column, text, record }">
              <a-tooltip v-if="column.dataIndex === 'itemSummary'" :title="itemSummary(record as DashboardBusinessItemVO)">
                <span class="production-ellipsis">{{ itemSummary(record as DashboardBusinessItemVO) }}</span>
              </a-tooltip>
              <a-tooltip v-else-if="column.dataIndex === 'partnerName'" :title="displayText(text)">
                <span class="production-ellipsis production-muted">{{ displayText(text) }}</span>
              </a-tooltip>
              <span v-else-if="column.dataIndex === 'ownerName'" class="production-muted">
                {{ displayText(text) }}
              </span>
              <span v-else-if="column.dataIndex === 'status'" class="production-status">
                {{ statusLabel(text) }}
              </span>
              <span v-else-if="column.dataIndex === 'date'" class="production-date">
                {{ displayText(text) }}
              </span>
              <span v-else-if="column.key === 'pendingDays'" class="production-days">
                {{ pendingText((record as DashboardBusinessItemVO).pendingDays) }}
              </span>
            </template>
          </a-table>
        </div>
      </aside>
    </section>

    <section class="role-reference-panel role-reference-bottom-panel">
      <div class="role-reference-panel-head">
        <div>
          <strong>近期分包计量</strong>
        </div>
      </div>
      <a-table
        :columns="productionAmountCols"
        :data-source="data.recentSubMeasures"
        :loading="loading"
        :pagination="false"
        :scroll="{ x: 952, y: 248 }"
        size="small"
        row-key="sourceId"
        class="role-reference-table production-reference-table"
      >
        <template #bodyCell="{ column, text, record }">
          <a-tooltip v-if="column.dataIndex === 'itemSummary'" :title="itemSummary(record as DashboardBusinessItemVO)">
            <span class="production-ellipsis">{{ itemSummary(record as DashboardBusinessItemVO) }}</span>
          </a-tooltip>
          <a-tooltip v-else-if="column.dataIndex === 'partnerName'" :title="displayText(text)">
            <span class="production-ellipsis production-muted">{{ displayText(text) }}</span>
          </a-tooltip>
          <span v-else-if="column.dataIndex === 'ownerName'" class="production-muted">
            {{ displayText(text) }}
          </span>
          <span v-else-if="column.dataIndex === 'amount'" class="production-number">
            {{ displayText(text) }}
          </span>
          <span v-else-if="column.dataIndex === 'status'" class="production-status">
            {{ statusLabel(text) }}
          </span>
          <span v-else-if="column.dataIndex === 'date'" class="production-date">
            {{ displayText(text) }}
          </span>
        </template>
      </a-table>
    </section>
  </div>
</template>

<style scoped>
.production-reference-table :deep(.ant-table) {
  color: #243044;
  font-size: 12px;
  table-layout: fixed;
}

.production-reference-table :deep(.ant-table-thead > tr > th) {
  height: 30px;
  padding: 0 8px;
  color: #536176;
  background: #fbfcff;
  font-size: 12px;
  font-weight: 700;
  line-height: 30px;
}

.production-reference-table :deep(.ant-table-tbody > tr > td) {
  min-height: 30px;
  padding: 5px 8px;
  color: #243044;
  font-size: 12px;
  line-height: 16px;
  vertical-align: middle;
}

.production-reference-table :deep(.ant-table-cell) {
  white-space: nowrap;
}

.production-ellipsis {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.production-date,
.production-days,
.production-number {
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}

.production-date {
  color: #64748b;
}

.production-days {
  color: #d97706;
  font-weight: 700;
}

.production-status {
  color: #243044;
  font-weight: 700;
}

.production-muted {
  color: #64748b;
}
</style>
