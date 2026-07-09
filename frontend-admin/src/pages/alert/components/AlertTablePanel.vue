<script setup lang="ts">
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
  <section class="alert-panel alert-table-panel">
    <div class="alert-toolbar">
      <div class="alert-toolbar-left">
        <a-button
          v-if="canManageAlerts"
          type="primary"
          :disabled="selectedCount === 0"
          @click="handleBatchStatus('PROCESSED')"
          >批量处理</a-button
        >
        <a-button v-if="canManageAlerts" :disabled="selectedCount === 0" @click="handleBatchMarkRead"
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
      </div>
      <ColumnSettingsButton :columns="columnSettings" :visible="colVisible" @toggle="toggleCol" />
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
      <span class="lg-total">共 {{ total }} 条</span>
      <a-pagination
        :current="pageNo"
        :page-size="pageSize"
        :total="total"
        :page-size-options="['10', '20', '50', '100']"
        show-size-changer
        show-quick-jumper
        @change="handlePageChange"
        @show-size-change="handlePageSizeChange"
      />
    </div>
  </section>
</template>

<style scoped>
.alert-panel {
  background: #fff;
  border: 1px solid #e8edf5;
  border-radius: 12px;
  box-shadow: 0 4px 14px rgba(31, 35, 41, 0.04);
}

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
  padding: 14px 18px;
  border-bottom: 1px solid #eef2f7;
}

.alert-toolbar-left {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}

.alert-toolbar-meta {
  margin-left: 4px;
  color: #8a94a6;
  font-size: 13px;
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
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 30px;
  padding: 0 18px;
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

@media (max-width: 768px) {
  .alert-toolbar {
    flex-direction: column;
    align-items: stretch;
  }
}
</style>
