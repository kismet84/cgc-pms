<script setup lang="ts">
import {
  AuditOutlined,
  ClockCircleOutlined,
  ToolOutlined,
  WarningOutlined,
} from '@ant-design/icons-vue'
import type { ChiefEngineerDashboardVO, DashboardBusinessItemVO } from '@/types/dashboard'
import { fmtNum } from '../utils/formatUtils'

defineProps<{
  data: ChiefEngineerDashboardVO
  loading: boolean
}>()

const techItemCols = [
  { title: '单号', dataIndex: 'code', width: 148 },
  { title: '技术事项摘要', dataIndex: 'itemSummary', width: 210, ellipsis: true },
  { title: '责任人', dataIndex: 'ownerName', width: 80, ellipsis: true },
  { title: '状态', dataIndex: 'status', width: 74 },
  { title: '日期', dataIndex: 'date', width: 100 },
  { title: '时效', key: 'overdueDays', width: 78 },
]

const TECH_STATUS_LABEL: Record<string, string> = {
  DRAFT: '草稿',
  SUBMITTED: '已提交',
  REVIEWING: '审核中',
  APPROVING: '审批中',
  APPROVED: '已审批',
  REJECTED: '已驳回',
  OPEN: '待处理',
  PENDING: '待处理',
  PROCESSING: '处理中',
  COORDINATING: '协调中',
  RESOLVED: '已解决',
  CLOSED: '已关闭',
  OVERDUE: '已逾期',
}

function displayText(value?: string | number) {
  return value === undefined || value === null || value === '' ? '-' : String(value)
}

function formatDate(value?: string | number) {
  const text = displayText(value)
  return text === '-' ? '-' : text.slice(0, 10)
}

function itemSummary(record: DashboardBusinessItemVO) {
  return displayText(record.itemSummary || record.title)
}

function techStatusLabel(value?: string | number) {
  const key = displayText(value)
  return key === '-' ? '-' : (TECH_STATUS_LABEL[key] ?? '-')
}

function daysUntil(value?: string | number) {
  const text = formatDate(value)
  if (text === '-') return null
  const target = new Date(`${text}T00:00:00`)
  if (Number.isNaN(target.getTime())) return null
  const today = new Date()
  today.setHours(0, 0, 0, 0)
  return Math.round((target.getTime() - today.getTime()) / 86400000)
}

function timelinessText(record: DashboardBusinessItemVO) {
  const days = daysUntil(record.date)
  if (days === null) return '-'
  if (days > 0) return `剩余${days}天`
  if (days === 0) return '今日到期'
  return `逾期${Math.abs(days)}天`
}
</script>

<template>
  <div class="role-reference-shell">
    <section class="role-reference-kpis role-reference-kpis--4">
      <article class="role-reference-kpi" style="--kpi-accent: #2f7cf6">
        <div class="role-reference-kpi-title">技术审核</div>
        <div class="role-reference-kpi-value">
          {{ fmtNum(data.pendingReviewCount) }} <small>项</small>
        </div>
        <div class="role-reference-kpi-meta">
          <span><AuditOutlined /> 待审核事项</span>
        </div>
      </article>
      <article class="role-reference-kpi" style="--kpi-accent: #14b8a6">
        <div class="role-reference-kpi-title">设计协调</div>
        <div class="role-reference-kpi-value is-cyan">
          {{ fmtNum(data.pendingCoordinationCount) }} <small>项</small>
        </div>
        <div class="role-reference-kpi-meta">
          <span><ClockCircleOutlined /> 待协调事项</span>
        </div>
      </article>
      <article class="role-reference-kpi" style="--kpi-accent: #f97316">
        <div class="role-reference-kpi-title">重大技术问题</div>
        <div class="role-reference-kpi-value is-warning">
          {{ fmtNum(data.openIssueCount) }} <small>项</small>
        </div>
        <div class="role-reference-kpi-meta">
          <span><ToolOutlined /> 持续跟踪</span>
        </div>
      </article>
      <article class="role-reference-kpi" style="--kpi-accent: #ef4444">
        <div class="role-reference-kpi-title">逾期技术事项</div>
        <div class="role-reference-kpi-value is-danger">
          {{ fmtNum(data.overdueCount) }} <small>项</small>
        </div>
        <div class="role-reference-kpi-meta">
          <span><WarningOutlined /> 需尽快处理</span>
        </div>
      </article>
    </section>

    <section class="role-reference-main-grid">
      <div class="role-reference-panel role-reference-analysis">
        <div class="role-reference-panel-head">
          <div>
            <strong>技术闭环总览</strong>
          </div>
        </div>
        <div class="role-reference-summary-grid role-reference-summary-grid--hero">
          <div class="role-reference-summary-item">
            <span>技术审核</span>
            <b>{{ fmtNum(data.pendingReviewCount) }} 项</b>
          </div>
          <div class="role-reference-summary-item">
            <span>设计协调</span>
            <b>{{ fmtNum(data.pendingCoordinationCount) }} 项</b>
          </div>
          <div class="role-reference-summary-item">
            <span>重大技术问题</span>
            <b>{{ fmtNum(data.openIssueCount) }} 项</b>
          </div>
          <div class="role-reference-summary-item">
            <span>逾期技术事项</span>
            <b>{{ fmtNum(data.overdueCount) }} 项</b>
          </div>
        </div>
        <div class="role-reference-focus-list">
          <div>
            <strong>审核优先级</strong>
            <span>优先清理待技术审核事项，避免技术结论滞后影响现场执行。</span>
          </div>
          <div>
            <strong>设计协同闭环</strong>
            <span>聚焦待协调事项，确认设计接口、责任人和计划完成时间。</span>
          </div>
          <div>
            <strong>问题压降</strong>
            <span>持续跟踪重大技术问题与逾期事项，优先处理超时未关闭问题。</span>
          </div>
        </div>
      </div>

      <aside class="role-reference-side-stack">
        <div class="role-reference-panel role-mini-panel is-blue">
          <div class="role-reference-panel-head mini">
            <strong
              >待技术审核 <b>（{{ data.pendingReviews.length }}）</b></strong
            >
          </div>
          <a-table
            :columns="techItemCols"
            :data-source="data.pendingReviews"
            :loading="loading"
            :pagination="false"
            :scroll="{ x: 690, y: 216 }"
            size="small"
            row-key="sourceId"
            class="role-reference-table chief-reference-table"
          >
            <template #bodyCell="{ column, text, record }">
              <a-tooltip
                v-if="column.dataIndex === 'itemSummary'"
                :title="itemSummary(record as DashboardBusinessItemVO)"
              >
                <span class="chief-ellipsis">{{
                  itemSummary(record as DashboardBusinessItemVO)
                }}</span>
              </a-tooltip>
              <span v-else-if="column.dataIndex === 'ownerName'" class="chief-muted">
                {{ displayText(text) }}
              </span>
              <span v-else-if="column.dataIndex === 'status'" class="chief-status">
                {{ techStatusLabel(text) }}
              </span>
              <span v-else-if="column.key === 'overdueDays'" class="chief-days">
                {{ timelinessText(record as DashboardBusinessItemVO) }}
              </span>
              <span v-else-if="column.dataIndex === 'date'" class="chief-date">
                {{ formatDate(text) }}
              </span>
            </template>
          </a-table>
        </div>
        <div class="role-reference-panel role-mini-panel is-orange">
          <div class="role-reference-panel-head mini">
            <strong
              >重大技术问题 <b>（{{ data.openIssues.length }}）</b></strong
            >
          </div>
          <a-table
            :columns="techItemCols"
            :data-source="data.openIssues"
            :loading="loading"
            :pagination="false"
            :scroll="{ x: 690, y: 216 }"
            size="small"
            row-key="sourceId"
            class="role-reference-table chief-reference-table"
          >
            <template #bodyCell="{ column, text, record }">
              <a-tooltip
                v-if="column.dataIndex === 'itemSummary'"
                :title="itemSummary(record as DashboardBusinessItemVO)"
              >
                <span class="chief-ellipsis">{{
                  itemSummary(record as DashboardBusinessItemVO)
                }}</span>
              </a-tooltip>
              <span v-else-if="column.dataIndex === 'ownerName'" class="chief-muted">
                {{ displayText(text) }}
              </span>
              <span v-else-if="column.dataIndex === 'status'" class="chief-status">
                {{ techStatusLabel(text) }}
              </span>
              <span v-else-if="column.key === 'overdueDays'" class="chief-days">
                {{ timelinessText(record as DashboardBusinessItemVO) }}
              </span>
              <span v-else-if="column.dataIndex === 'date'" class="chief-date">
                {{ formatDate(text) }}
              </span>
            </template>
          </a-table>
        </div>
      </aside>
    </section>

    <div class="chief-bottom-card-grid">
      <section class="role-reference-panel role-reference-bottom-panel">
        <div class="role-reference-panel-head">
          <div>
            <strong>设计协调事项</strong>
          </div>
        </div>
        <a-table
          :columns="techItemCols"
          :data-source="data.pendingCoordinations"
          :loading="loading"
          :pagination="false"
          :scroll="{ x: 690, y: 248 }"
          size="small"
          row-key="sourceId"
          class="role-reference-table chief-reference-table"
        >
          <template #bodyCell="{ column, text, record }">
            <a-tooltip
              v-if="column.dataIndex === 'itemSummary'"
              :title="itemSummary(record as DashboardBusinessItemVO)"
            >
              <span class="chief-ellipsis">{{
                itemSummary(record as DashboardBusinessItemVO)
              }}</span>
            </a-tooltip>
            <span v-else-if="column.dataIndex === 'ownerName'" class="chief-muted">
              {{ displayText(text) }}
            </span>
            <span v-else-if="column.dataIndex === 'status'" class="chief-status">
              {{ techStatusLabel(text) }}
            </span>
            <span v-else-if="column.key === 'overdueDays'" class="chief-days">
              {{ timelinessText(record as DashboardBusinessItemVO) }}
            </span>
            <span v-else-if="column.dataIndex === 'date'" class="chief-date">
              {{ formatDate(text) }}
            </span>
          </template>
        </a-table>
      </section>

      <section class="role-reference-panel role-reference-bottom-panel">
        <div class="role-reference-panel-head">
          <div>
            <strong>逾期技术事项</strong>
          </div>
        </div>
        <a-table
          :columns="techItemCols"
          :data-source="data.overdueItems"
          :loading="loading"
          :pagination="false"
          :scroll="{ x: 690, y: 248 }"
          size="small"
          row-key="sourceId"
          class="role-reference-table chief-reference-table"
        >
          <template #bodyCell="{ column, text, record }">
            <a-tooltip
              v-if="column.dataIndex === 'itemSummary'"
              :title="itemSummary(record as DashboardBusinessItemVO)"
            >
              <span class="chief-ellipsis">{{
                itemSummary(record as DashboardBusinessItemVO)
              }}</span>
            </a-tooltip>
            <span v-else-if="column.dataIndex === 'ownerName'" class="chief-muted">
              {{ displayText(text) }}
            </span>
            <span v-else-if="column.dataIndex === 'status'" class="chief-status">
              {{ techStatusLabel(text) }}
            </span>
            <span v-else-if="column.key === 'overdueDays'" class="chief-days">
              {{ timelinessText(record as DashboardBusinessItemVO) }}
            </span>
            <span v-else-if="column.dataIndex === 'date'" class="chief-date">
              {{ formatDate(text) }}
            </span>
          </template>
        </a-table>
      </section>
    </div>
  </div>
</template>

<style scoped>
.chief-reference-table :deep(.ant-table) {
  color: #243044;
  font-size: 12px;
  table-layout: fixed;
}

.chief-reference-table :deep(.ant-table-thead > tr > th) {
  height: 30px;
  padding: 0 8px;
  color: #536176;
  background: #fbfcff;
  font-size: 12px;
  font-weight: 700;
  line-height: 30px;
}

.chief-reference-table :deep(.ant-table-tbody > tr > td) {
  min-height: 30px;
  padding: 5px 8px;
  color: #243044;
  font-size: 12px;
  line-height: 16px;
  vertical-align: middle;
}

.chief-reference-table :deep(.ant-table-cell) {
  white-space: nowrap;
}

.chief-bottom-card-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

.chief-ellipsis {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.chief-date,
.chief-days {
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}

.chief-date {
  color: #64748b;
}

.chief-days {
  color: #ef4444;
  font-weight: 700;
}

.chief-status {
  color: #243044;
  font-weight: 700;
}

.chief-muted {
  color: #64748b;
}

@media (max-width: 1200px) {
  .chief-bottom-card-grid {
    grid-template-columns: 1fr;
  }
}
</style>
