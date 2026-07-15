<script setup lang="ts">
import { RightOutlined } from '@ant-design/icons-vue'
import type { AlertLogVO } from '@/types/alert'
import { ColumnSettingsButton, LgEmptyState } from '@/components/list-page'
import { ALERT_PROCESS_STATUS_COLOR, RULE_TYPE_LABELS, SEVERITY_COLOR } from '@/types/alert'

type AlertProcessStatus = 'OPEN' | 'PROCESSED' | 'ARCHIVED' | 'INVALID'

defineProps<{
  alerts: AlertLogVO[]
  columnSettings: Array<{ key: string; label: string }>
  colVisible: Record<string, boolean>
  tableColumns: Array<Record<string, unknown>>
  loading: boolean
  isMobile: boolean
  tableHeight: string
  allPageSelected: boolean
  pageSelectionIndeterminate: boolean
  total: number
  pageNo: number
  pageSize: number
  selectedCount: number
  listError: string | null
  showEmptyState: boolean
  hasActiveFilters: boolean
  canManageAlerts: boolean
  canExportAlerts: boolean
  exportDisabled: boolean
  toggleCol: (key: string) => void
  togglePageSelection: (checked: boolean) => void
  isRowSelected: (id: string | number) => boolean
  toggleRowSelection: (record: AlertLogVO, checked: boolean) => void
  openDetail: (record: AlertLogVO) => void
  getProjectName: (projectId: string | number) => string
  getAlertDomainLabel: (record: AlertLogVO) => string
  getAlertTagLabel: (record: AlertLogVO) => string
  getProcessStatusLabel: (record: AlertLogVO) => string
  formatSeverityText: (value: string) => string
  formatDateTime: (value: unknown) => string
  getAlertMessageText: (value: unknown) => string
  handleMarkRead: (record: AlertLogVO) => void
  handleChangeStatus: (
    record: AlertLogVO,
    processStatus: AlertProcessStatus,
    statusRemark?: string,
  ) => void
  handleBatchStatus: (processStatus: AlertProcessStatus) => void
  handleBatchMarkRead: () => void
  handlePageChange: (page: number) => void
  handlePageSizeChange: (page: number, size: number) => void
  handleReset: () => void
  handleRetry: () => void
  exportCurrentView: () => void
}>()
</script>

<template>
  <main class="lg-list-table-panel alert-table-panel">
    <div class="alert-toolbar">
      <div class="alert-toolbar-heading">
        <strong>预警列表</strong>
        <span>共 {{ total }} 条</span>
      </div>
      <div class="alert-toolbar-actions">
        <a-button
          v-if="canManageAlerts"
          type="primary"
          :disabled="selectedCount === 0"
          @click="handleBatchStatus('PROCESSED')"
          >批量处理</a-button
        >
        <a-button
          v-if="canManageAlerts"
          :disabled="selectedCount === 0"
          @click="handleBatchMarkRead"
          >标记已读</a-button
        >
        <a-button
          v-if="canManageAlerts"
          :disabled="selectedCount === 0"
          @click="handleBatchStatus('ARCHIVED')"
          >归档</a-button
        >
        <a-button v-if="canExportAlerts" :disabled="exportDisabled" @click="exportCurrentView"
          >导出</a-button
        >
        <span v-if="canManageAlerts" class="alert-toolbar-meta">已选择 {{ selectedCount }} 条</span>
        <ColumnSettingsButton
          class="alert-column-settings"
          :columns="columnSettings"
          :visible="colVisible"
          @toggle="toggleCol"
        />
      </div>
    </div>

    <div class="lg-table-wrap alert-grid-wrap">
      <div v-if="listError" class="alert-list-feedback">
        <a-result status="error" title="预警列表加载失败" :sub-title="listError">
          <template #extra>
            <a-button type="primary" @click="handleRetry">重试</a-button>
          </template>
        </a-result>
      </div>
      <div v-else-if="showEmptyState" class="alert-list-feedback">
        <LgEmptyState description="暂无符合条件的预警记录">
          <a-button v-if="hasActiveFilters" @click="handleReset">清空筛选</a-button>
        </LgEmptyState>
      </div>
      <div v-else-if="isMobile" class="alert-mobile-list">
        <article
          v-for="record in alerts"
          :key="String(record.id)"
          class="alert-mobile-card"
          @click="openDetail(record)"
        >
          <div class="alert-mobile-card-head">
            <button type="button" class="alert-mobile-title" @click.stop="openDetail(record)">
              {{ getAlertMessageText(record.message) }}
            </button>
            <a-tag :color="SEVERITY_COLOR[record.severity] ?? 'default'">
              {{ formatSeverityText(record.severity) }}
            </a-tag>
          </div>
          <div class="alert-mobile-project">{{ getProjectName(record.projectId) }}</div>
          <div class="alert-mobile-meta">
            <span
              >{{ getProcessStatusLabel(record) }} · {{ formatDateTime(record.triggeredAt) }}</span
            >
            <RightOutlined />
          </div>
        </article>
      </div>
      <vxe-grid
        v-else
        :data="alerts"
        :columns="tableColumns"
        :loading="loading"
        :height="tableHeight"
        :column-config="{ resizable: true }"
        :row-config="{ isHover: true }"
        border="inner"
        size="mini"
        show-overflow="title"
      >
        <template #selectionHeader>
          <a-checkbox
            :checked="allPageSelected"
            :indeterminate="pageSelectionIndeterminate"
            @change="(event) => togglePageSelection(event.target.checked)"
          />
        </template>
        <template #selection="{ row }">
          <a-checkbox
            :checked="isRowSelected(row.id)"
            @change="(event) => toggleRowSelection(row, event.target.checked)"
          />
        </template>
        <template #id="{ row }">
          <button type="button" class="alert-link" @click="openDetail(row)">{{ row.id }}</button>
        </template>
        <template #projectId="{ row }">
          <button type="button" class="alert-link alert-project-link" @click="openDetail(row)">
            {{ getProjectName(row.projectId) }}
          </button>
        </template>
        <template #alertDomain="{ row }">
          <span class="alert-cell-text">{{ getAlertDomainLabel(row) }}</span>
        </template>
        <template #ruleType="{ row }">
          <span class="alert-cell-text">{{ RULE_TYPE_LABELS[row.ruleType] || row.ruleType }}</span>
        </template>
        <template #alertCategory="{ row }">
          <span class="alert-cell-text">{{ getAlertTagLabel(row) }}</span>
        </template>
        <template #severity="{ row }">
          <a-tag :color="SEVERITY_COLOR[row.severity] ?? 'default'" class="alert-tag">
            {{ formatSeverityText(row.severity) }}
          </a-tag>
        </template>
        <template #processStatus="{ row }">
          <a-tag
            :color="ALERT_PROCESS_STATUS_COLOR[String(row.processStatus ?? 'OPEN')] ?? 'default'"
            class="alert-tag"
          >
            {{ getProcessStatusLabel(row) }}
          </a-tag>
        </template>
        <template #isRead="{ row }">
          <span class="alert-read-state" :class="{ 'is-unread': row.isRead === 0 }">
            <i></i>
            {{ row.isRead === 0 ? '未读' : '已读' }}
          </span>
        </template>
        <template #triggeredAt="{ row }">
          <span class="alert-cell-text">{{ formatDateTime(row.triggeredAt) }}</span>
        </template>
        <template #message="{ row }">
          <button type="button" class="alert-message-button" @click="openDetail(row)">
            {{ getAlertMessageText(row.message) }}
          </button>
        </template>
        <template #action="{ row }">
          <div class="alert-row-actions">
            <a-button
              v-if="canManageAlerts && row.isRead === 0"
              type="link"
              size="small"
              @click="handleMarkRead(row)"
              >标记已读</a-button
            >
            <a-button
              v-if="canManageAlerts && String(row.processStatus ?? 'OPEN') !== 'PROCESSED'"
              type="link"
              size="small"
              @click="handleChangeStatus(row, 'PROCESSED')"
            >
              处理
            </a-button>
            <a-button
              v-if="canManageAlerts && String(row.processStatus ?? 'OPEN') !== 'ARCHIVED'"
              type="link"
              size="small"
              @click="handleChangeStatus(row, 'ARCHIVED')"
            >
              归档
            </a-button>
            <a-button type="link" size="small" @click="openDetail(row)">详情</a-button>
          </div>
        </template>
      </vxe-grid>
    </div>

    <div class="lg-pagination alert-pagination">
      <span v-if="!isMobile" class="lg-total">共 {{ total }} 条</span>
      <a-pagination
        :current="pageNo"
        :page-size="pageSize"
        :total="total"
        :page-size-options="['10', '20', '50', '100']"
        :show-size-changer="!isMobile"
        :simple="isMobile"
        @change="handlePageChange"
        @show-size-change="handlePageSizeChange"
      />
    </div>
  </main>
</template>

<style scoped>
.alert-table-panel {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-height: 0;
  overflow: hidden;
}

.alert-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 14px;
  border-bottom: 1px solid #eef2f7;
}

.alert-toolbar-heading,
.alert-toolbar-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}

.alert-toolbar-heading strong {
  color: var(--text);
  font-size: 15px;
}

.alert-toolbar-heading span,
.alert-toolbar-meta {
  color: var(--text-secondary);
  font-size: 13px;
}

.alert-toolbar-actions {
  justify-content: flex-end;
}

.alert-toolbar-meta {
  margin-left: 4px;
}

.alert-grid-wrap {
  flex: 1;
  min-height: 0;
  padding: 0 14px 6px;
}

.alert-list-feedback {
  padding: 12px 0;
}

.alert-pagination {
  padding: 8px 18px;
}

.alert-link,
.alert-message-button {
  padding: 0;
  background: transparent;
  border: 0;
}

.alert-link {
  display: block;
  width: 100%;
  overflow: hidden;
  color: #1677ff;
  line-height: 20px;
  text-align: left;
  text-overflow: ellipsis;
  white-space: nowrap;
  cursor: pointer;
}

.alert-project-link {
  text-align: left;
}

.alert-message-button {
  display: block;
  width: 100%;
  overflow: hidden;
  color: #1f2329;
  text-align: left;
  text-overflow: ellipsis;
  white-space: nowrap;
  cursor: pointer;
}

.alert-cell-text {
  display: block;
  overflow: hidden;
  color: #1f2329;
  line-height: 20px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.alert-tag {
  font-weight: 600;
}

.alert-read-state {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: #5f6b7a;
}

.alert-read-state i {
  width: 7px;
  height: 7px;
  background: #52c41a;
  border-radius: 50%;
}

.alert-read-state.is-unread {
  color: #ff4d4f;
}

.alert-read-state.is-unread i {
  background: #ff4d4f;
}

.alert-row-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  margin-left: -8px;
}

:deep(.alert-grid-wrap .vxe-grid) {
  height: 100%;
}

:deep(.alert-grid-wrap .vxe-table--header-wrapper),
:deep(.alert-grid-wrap .vxe-table--body-wrapper) {
  width: 100%;
}

:deep(.alert-grid-wrap .vxe-header--column),
:deep(.alert-grid-wrap .vxe-body--column) {
  padding-top: 0;
  padding-bottom: 0;
}

:deep(.alert-grid-wrap .vxe-cell) {
  padding: 0 6px;
  line-height: 16px;
  font-size: 12px;
}

:deep(.alert-grid-wrap .vxe-header--column .vxe-cell) {
  line-height: 24px;
  font-size: 12px;
}

:deep(.alert-grid-wrap .vxe-body--row) {
  height: 16px;
}

:deep(.alert-grid-wrap .ant-tag) {
  margin: 0;
  padding: 0 3px;
  line-height: 14px;
  font-size: 10px;
}

:deep(.alert-grid-wrap .ant-btn-sm) {
  height: 16px;
  padding: 0 2px;
  line-height: 14px;
  font-size: 12px;
}

:deep(.alert-grid-wrap .vxe-table--body-wrapper) {
  min-height: 0;
}

.alert-mobile-list {
  display: grid;
  gap: 6px;
}

.alert-mobile-card {
  min-height: 88px;
  padding: 10px 12px;
  background: var(--surface);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-soft);
}

.alert-mobile-card-head,
.alert-mobile-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.alert-mobile-title {
  min-width: 0;
  padding: 0;
  overflow: hidden;
  color: var(--text);
  font-size: 15px;
  font-weight: 700;
  text-align: left;
  text-overflow: ellipsis;
  white-space: nowrap;
  background: transparent;
  border: 0;
}

.alert-mobile-project,
.alert-mobile-meta {
  margin-top: 5px;
  overflow: hidden;
  color: var(--text-secondary);
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

@media (width < 500px) {
  .alert-table-panel {
    flex: 0 0 auto;
    min-height: 0;
    overflow: visible;
    background: transparent;
    border: 0;
    border-radius: 0;
    box-shadow: none;
  }

  .alert-toolbar {
    min-height: 40px;
    padding: 8px 4px;
    background: var(--surface);
    border: 1px solid var(--border-subtle);
    border-radius: var(--radius-md);
  }

  .alert-toolbar-actions {
    display: none;
  }

  .alert-grid-wrap {
    flex: 0 0 auto;
    height: auto;
    min-height: 0;
    padding: 0;
    overflow: visible;
  }

  .alert-pagination {
    justify-content: center;
    min-height: 40px;
    padding: 2px 0;
    border-top: 0;
  }
}
</style>
