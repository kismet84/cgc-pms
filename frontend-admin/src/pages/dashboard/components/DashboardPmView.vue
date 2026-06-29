<script setup lang="ts">
import {
  AuditOutlined,
  WarningOutlined,
  ClockCircleOutlined,
  FileTextOutlined,
} from '@ant-design/icons-vue'
import type {
  DashboardContractItemVO,
  DashboardProjectSummaryVO,
  DashboardTaskItemVO,
  ProjectManagerDashboardVO,
} from '@/types/dashboard'
import { fmtNum } from '../utils/formatUtils'

const props = defineProps<{
  data: ProjectManagerDashboardVO
  loading: boolean
}>()

const pmTaskCols = [
  { title: '任务摘要', dataIndex: 'itemSummary', width: 220, ellipsis: true },
  { title: '业务类型', dataIndex: 'businessType', width: 96 },
  { title: '责任人', dataIndex: 'ownerName', width: 92, ellipsis: true },
  { title: '金额', dataIndex: 'amount', width: 100, align: 'right' as const },
  { title: '待处理', key: 'pendingDays', width: 84 },
  { title: '接收时间', dataIndex: 'receivedAt', width: 136 },
]

const pmProjectCols = [
  { title: '项目名称', dataIndex: 'projectName', width: 220, ellipsis: true },
  { title: '项目编号', dataIndex: 'projectCode', width: 140 },
  { title: '状态', dataIndex: 'status', width: 88 },
]

const pmContractCols = [
  { title: '合同名称', dataIndex: 'contractName', width: 240, ellipsis: true },
  { title: '到期日', dataIndex: 'endDate', width: 120 },
  { title: '金额(万元)', dataIndex: 'contractAmount', width: 120, align: 'right' as const },
]

const BUSINESS_TYPE_LABEL: Record<string, string> = {
  CONTRACT: '合同',
  CONTRACT_CHANGE: '合同变更',
  VARIATION_ORDER: '变更签证',
  PURCHASE_REQUEST: '采购申请',
  MATERIAL_REQUISITION: '领料申请',
  MATERIAL_RECEIPT: '验收入库',
  SUB_MEASURE: '分包计量',
  TECH_ITEM: '技术事项',
}

const PROJECT_STATUS_LABEL: Record<string, string> = {
  ACTIVE: '进行中',
  IN_PROGRESS: '进行中',
  PAUSED: '暂停',
  COMPLETED: '已完成',
  ARCHIVED: '已归档',
}

function displayText(value?: string | number) {
  return value === undefined || value === null || value === '' ? '-' : String(value)
}

function taskSummary(record: DashboardTaskItemVO) {
  return displayText(record.itemSummary || record.title)
}

function businessTypeLabel(value?: string | number) {
  const key = displayText(value)
  return key === '-' ? '-' : (BUSINESS_TYPE_LABEL[key] ?? '-')
}

function projectStatusLabel(value?: string | number) {
  const key = displayText(value)
  return key === '-' ? '-' : (PROJECT_STATUS_LABEL[key] ?? '-')
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
        <div class="role-reference-kpi-title">待办任务</div>
        <div class="role-reference-kpi-value">
          {{ fmtNum(data.pendingTaskCount) }} <small>项</small>
        </div>
        <div class="role-reference-kpi-meta">
          <span><AuditOutlined /> 工作待处理</span>
        </div>
      </article>
      <article class="role-reference-kpi" style="--kpi-accent: #f97316">
        <div class="role-reference-kpi-title">滞后项目</div>
        <div class="role-reference-kpi-value is-warning">
          {{ fmtNum(data.laggingProjectCount) }} <small>个</small>
        </div>
        <div class="role-reference-kpi-meta">
          <span><WarningOutlined /> 进度异常</span>
        </div>
      </article>
      <article class="role-reference-kpi" style="--kpi-accent: #14b8a6">
        <div class="role-reference-kpi-title">待审批</div>
        <div class="role-reference-kpi-value is-cyan">
          {{ fmtNum(data.pendingApprovalCount) }} <small>项</small>
        </div>
        <div class="role-reference-kpi-meta">
          <span><ClockCircleOutlined /> 流程处理中</span>
        </div>
      </article>
      <article class="role-reference-kpi" style="--kpi-accent: #ef4444">
        <div class="role-reference-kpi-title">临期合同</div>
        <div class="role-reference-kpi-value is-danger">
          {{ fmtNum(data.expiringContractCount) }} <small>份</small>
        </div>
        <div class="role-reference-kpi-meta">
          <span><FileTextOutlined /> 30天内到期</span>
        </div>
      </article>
    </section>

    <section class="role-reference-main-grid">
      <div class="role-reference-panel role-reference-analysis">
        <div class="role-reference-panel-head">
          <div>
            <strong>执行协同总览</strong>
          </div>
        </div>
        <div class="role-reference-summary-grid role-reference-summary-grid--hero">
          <div class="role-reference-summary-item">
            <span>待办任务</span>
            <b>{{ fmtNum(data.pendingTaskCount) }} 项</b>
          </div>
          <div class="role-reference-summary-item">
            <span>滞后项目</span>
            <b>{{ fmtNum(data.laggingProjectCount) }} 个</b>
          </div>
          <div class="role-reference-summary-item">
            <span>待审批</span>
            <b>{{ fmtNum(data.pendingApprovalCount) }} 项</b>
          </div>
          <div class="role-reference-summary-item">
            <span>临期合同</span>
            <b>{{ fmtNum(data.expiringContractCount) }} 份</b>
          </div>
        </div>
        <div class="role-reference-focus-list">
          <div>
            <strong>今日优先处理</strong>
            <span>先清理本人待办与项目审批，避免流程阻塞施工协同。</span>
          </div>
          <div>
            <strong>进度异常跟踪</strong>
            <span>优先查看滞后项目，确认责任人、计划节点和纠偏动作。</span>
          </div>
          <div>
            <strong>履约临期提醒</strong>
            <span>关注 30 天内到期合同，提前组织续签、验收或结算准备。</span>
          </div>
        </div>
      </div>

      <aside class="role-reference-side-stack">
        <div class="role-reference-panel role-mini-panel is-blue">
          <div class="role-reference-panel-head mini">
            <strong
              >待办任务 <b>（{{ data.pendingTasks.length }}）</b></strong
            >
          </div>
          <a-table
            :columns="pmTaskCols"
            :data-source="data.pendingTasks"
            :loading="loading"
            :pagination="false"
            :scroll="{ x: 728, y: 216 }"
            size="small"
            row-key="taskId"
            class="role-reference-table pm-reference-table"
          >
            <template #bodyCell="{ column, text, record }">
              <a-tooltip v-if="column.dataIndex === 'itemSummary'" :title="taskSummary(record as DashboardTaskItemVO)">
                <span class="pm-ellipsis">{{ taskSummary(record as DashboardTaskItemVO) }}</span>
              </a-tooltip>
              <span v-else-if="column.dataIndex === 'businessType'" class="pm-status">
                {{ businessTypeLabel(text) }}
              </span>
              <span v-else-if="column.dataIndex === 'ownerName'" class="pm-muted">
                {{ displayText(text) }}
              </span>
              <span v-else-if="column.dataIndex === 'amount'" class="pm-number">
                {{ displayText(text) }}
              </span>
              <span v-else-if="column.key === 'pendingDays'" class="pm-number">
                {{ pendingText((record as DashboardTaskItemVO).pendingDays) }}
              </span>
              <span v-else-if="column.dataIndex === 'receivedAt'" class="pm-date">
                {{ displayText(text) }}
              </span>
            </template>
          </a-table>
        </div>
        <div class="role-reference-panel role-mini-panel is-orange">
          <div class="role-reference-panel-head mini">
            <strong
              >待审批 <b>（{{ data.pendingApprovals.length }}）</b></strong
            >
          </div>
          <a-table
            :columns="pmTaskCols"
            :data-source="data.pendingApprovals"
            :loading="loading"
            :pagination="false"
            :scroll="{ x: 728, y: 216 }"
            size="small"
            row-key="taskId"
            class="role-reference-table pm-reference-table"
          >
            <template #bodyCell="{ column, text, record }">
              <a-tooltip v-if="column.dataIndex === 'itemSummary'" :title="taskSummary(record as DashboardTaskItemVO)">
                <span class="pm-ellipsis">{{ taskSummary(record as DashboardTaskItemVO) }}</span>
              </a-tooltip>
              <span v-else-if="column.dataIndex === 'businessType'" class="pm-status">
                {{ businessTypeLabel(text) }}
              </span>
              <span v-else-if="column.dataIndex === 'ownerName'" class="pm-muted">
                {{ displayText(text) }}
              </span>
              <span v-else-if="column.dataIndex === 'amount'" class="pm-number">
                {{ displayText(text) }}
              </span>
              <span v-else-if="column.key === 'pendingDays'" class="pm-number">
                {{ pendingText((record as DashboardTaskItemVO).pendingDays) }}
              </span>
              <span v-else-if="column.dataIndex === 'receivedAt'" class="pm-date">
                {{ displayText(text) }}
              </span>
            </template>
          </a-table>
        </div>
      </aside>
    </section>

    <div class="pm-bottom-card-grid">
      <section class="role-reference-panel role-reference-bottom-panel">
        <div class="role-reference-panel-head">
          <div>
            <strong>滞后项目</strong>
          </div>
        </div>
        <a-table
          :columns="pmProjectCols"
          :data-source="data.laggingProjects"
          :loading="loading"
          :pagination="false"
          :scroll="{ x: 'max-content', y: 248 }"
          size="small"
          row-key="projectId"
          class="role-reference-table pm-reference-table pm-bottom-table"
        >
          <template #bodyCell="{ column, text, record }">
            <a-tooltip v-if="column.dataIndex === 'projectName'" :title="displayText((record as DashboardProjectSummaryVO).projectName)">
              <span class="pm-ellipsis">{{ displayText(text) }}</span>
            </a-tooltip>
            <span v-else-if="column.dataIndex === 'status'" class="pm-status">
              {{ projectStatusLabel(text) }}
            </span>
          </template>
        </a-table>
      </section>
      <section class="role-reference-panel role-reference-bottom-panel">
        <div class="role-reference-panel-head">
          <div>
            <strong>临期合同（30天内到期）</strong>
          </div>
        </div>
        <a-table
          :columns="pmContractCols"
          :data-source="data.expiringContracts"
          :loading="loading"
          :pagination="false"
          :scroll="{ x: 'max-content', y: 248 }"
          size="small"
          row-key="contractId"
          class="role-reference-table pm-reference-table pm-bottom-table"
        >
          <template #bodyCell="{ column, text, record }">
            <a-tooltip v-if="column.dataIndex === 'contractName'" :title="displayText((record as DashboardContractItemVO).contractName)">
              <span class="pm-ellipsis">{{ displayText(text) }}</span>
            </a-tooltip>
            <span v-else-if="column.dataIndex === 'endDate'" class="pm-date">
              {{ displayText(text) }}
            </span>
            <span v-else-if="column.dataIndex === 'contractAmount'" class="pm-number">
              {{ displayText(text) }}
            </span>
          </template>
        </a-table>
      </section>
    </div>
  </div>
</template>

<style scoped>
.pm-reference-table :deep(.ant-table) {
  color: #243044;
  font-size: 12px;
  table-layout: fixed;
}

.pm-reference-table :deep(.ant-table-thead > tr > th) {
  height: 30px;
  padding: 0 8px;
  color: #536176;
  background: #fbfcff;
  font-size: 12px;
  font-weight: 700;
  line-height: 30px;
}

.pm-reference-table :deep(.ant-table-tbody > tr > td) {
  min-height: 30px;
  padding: 5px 8px;
  color: #243044;
  font-size: 12px;
  line-height: 16px;
  vertical-align: middle;
}

.pm-reference-table :deep(.ant-table-cell) {
  white-space: nowrap;
}

.pm-bottom-table :deep(.ant-table-placeholder .ant-table-cell) {
  height: 40px;
  padding: 6px 8px;
}

.pm-bottom-table :deep(.ant-empty-normal) {
  margin: 4px 0;
}

.pm-bottom-table :deep(.ant-empty-image) {
  display: none;
}

.pm-bottom-card-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

.pm-ellipsis {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.pm-date,
.pm-number {
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}

.pm-date {
  color: #64748b;
}

.pm-status {
  color: #243044;
  font-weight: 700;
}

.pm-muted {
  color: #64748b;
}

@media (max-width: 1200px) {
  .pm-bottom-card-grid {
    grid-template-columns: 1fr;
  }
}
</style>
