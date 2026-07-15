<script setup lang="ts">
import { MoreOutlined, PlusOutlined, ReloadOutlined, RightOutlined } from '@ant-design/icons-vue'
import { ColumnSettingsButton } from '@/components/list-page'
import type { ProjectVO } from '@/types/project'

defineProps<{
  total: number
  loading: boolean
  tableData: ProjectVO[]
  isMobile: boolean
  pageNo: number
  pageSize: number
  visibleGridColumns: unknown[]
  columnSettings: unknown[]
  colVisible: Record<string, boolean>
  statusLabel: Record<string, string>
  statusColor: Record<string, string>
  approvalStatusLabel: Record<string, string>
  approvalStatusColor: Record<string, string>
  projectTypeLabel: (value?: string) => string
  projectTypeColor: (value?: string) => string
  fmtAmount: (val: string) => string
  canArchive: boolean
}>()

const emit = defineEmits<{
  toggleCol: [key: string]
  refresh: []
  create: []
  overview: [row: ProjectVO]
  edit: [row: ProjectVO]
  archive: [row: ProjectVO]
  delete: [row: ProjectVO]
  pageChange: [page: number]
  pageSizeChange: [current: number, size: number]
}>()
</script>

<template>
  <main class="lg-list-table-panel project-table-panel">
    <div v-if="!isMobile" class="lg-toolbar project-table-toolbar">
      <div class="lg-toolbar-left">
        <div class="project-table-heading">
          <span class="project-table-title">项目列表</span>
          <span class="project-table-count">共 {{ total }} 条</span>
        </div>
      </div>
      <div class="lg-toolbar-right project-table-toolbar-right">
        <ColumnSettingsButton
          :columns="columnSettings"
          :visible="colVisible"
          @toggle="emit('toggleCol', $event)"
        />
        <a-button aria-label="刷新项目列表" title="刷新" @click="emit('refresh')">
          <template #icon><ReloadOutlined /></template>
          刷新
        </a-button>
        <a-button type="primary" @click="emit('create')">
          <template #icon><PlusOutlined /></template>
          新建项目
        </a-button>
      </div>
    </div>

    <div class="lg-table-wrap project-table-wrap">
      <div v-if="isMobile" class="project-mobile-list">
        <a-spin :spinning="loading">
          <div v-if="tableData.length" class="project-mobile-cards">
            <article
              v-for="row in tableData"
              :key="row.id"
              class="project-mobile-card"
              role="link"
              tabindex="0"
              @click="emit('overview', row)"
              @keydown.enter="emit('overview', row)"
              @keydown.space.prevent="emit('overview', row)"
            >
              <div class="project-mobile-card-head">
                <div class="project-mobile-card-title">{{ row.projectName }}</div>
                <a-tag :color="statusColor[row.status]" size="small">
                  {{ statusLabel[row.status] ?? row.status }}
                </a-tag>
              </div>
              <div class="project-mobile-card-meta">
                <span class="project-mobile-card-code">{{ row.projectCode || '-' }}</span>
                <RightOutlined class="project-mobile-card-chevron" aria-hidden="true" />
              </div>
              <div class="project-mobile-card-summary">
                <span>{{ fmtAmount(row.contractAmount) }}</span>
                <span aria-hidden="true">·</span>
                <span>{{
                  row.plannedEndDate ? `计划至 ${row.plannedEndDate}` : '工期待维护'
                }}</span>
              </div>
            </article>
          </div>
          <a-empty v-else description="暂无项目数据" />
        </a-spin>
      </div>
      <vxe-grid
        v-else
        :data="tableData"
        :columns="visibleGridColumns"
        :loading="loading"
        :column-config="{ resizable: true, useKey: true }"
        show-overflow="title"
        show-header-overflow="title"
        stripe
        border="inner"
        size="small"
      >
        <template #projectCode="{ row }">
          <a-button class="project-code-link" type="link" @click="emit('overview', row)">
            {{ row.projectCode }}
          </a-button>
        </template>
        <template #projectName="{ row }">
          <span class="project-name-text">{{ row.projectName }}</span>
        </template>
        <template #projectType="{ row }">
          <a-tag :color="projectTypeColor(row.projectType)" size="small">
            {{ projectTypeLabel(row.projectType) }}
          </a-tag>
        </template>
        <template #contractAmount="{ row }">
          <span class="lg-money project-contract-amount">{{ fmtAmount(row.contractAmount) }}</span>
        </template>
        <template #plannedDuration="{ row }">
          <span>{{
            row.plannedStartDate || row.plannedEndDate
              ? `${row.plannedStartDate || '-'} ~ ${row.plannedEndDate || '-'}`
              : '-'
          }}</span>
        </template>
        <template #status="{ row }">
          <a-tag :color="statusColor[row.status]" size="small">
            {{ statusLabel[row.status] ?? row.status }}
          </a-tag>
        </template>
        <template #approvalStatus="{ row }">
          <a-tag
            v-if="row.approvalStatus"
            :color="approvalStatusColor[row.approvalStatus]"
            size="small"
          >
            {{ approvalStatusLabel[row.approvalStatus] ?? row.approvalStatus }}
          </a-tag>
          <span v-else class="project-empty-text">-</span>
        </template>
        <template #ops="{ row }">
          <a-dropdown :trigger="['click']">
            <a-button class="lg-row-action-trigger" size="small" type="text">
              <MoreOutlined />
            </a-button>
            <template #overlay>
              <a-menu>
                <a-menu-item @click="emit('overview', row)">查看</a-menu-item>
                <a-menu-item @click="emit('edit', row)">编辑</a-menu-item>
                <a-menu-item
                  v-if="canArchive && row.status !== 'ARCHIVED'"
                  @click="emit('archive', row)"
                >
                  归档
                </a-menu-item>
                <a-menu-item danger @click="emit('delete', row)">删除</a-menu-item>
              </a-menu>
            </template>
          </a-dropdown>
        </template>
      </vxe-grid>
    </div>

    <div class="lg-pagination project-pagination">
      <span v-if="!isMobile" class="lg-total">共 {{ total }} 条</span>
      <a-pagination
        :current="pageNo"
        :page-size="pageSize"
        :total="total"
        :page-size-options="['10', '20', '50', '100']"
        :show-size-changer="!isMobile"
        :simple="isMobile"
        @change="(page) => emit('pageChange', page)"
        @show-size-change="(current, size) => emit('pageSizeChange', current, size)"
      />
    </div>
  </main>
</template>

<style scoped>
.project-table-panel {
  display: flex;
  flex: 1;
  flex-direction: column;
  margin: 0;
  padding: 0;
  overflow: hidden;
  background: var(--surface);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-soft);
  min-height: 0;
}

.project-table-toolbar {
  padding: 10px 14px;
  border-bottom: 1px solid var(--border-subtle);
}

.project-table-heading,
.project-table-toolbar-right {
  display: flex;
  align-items: center;
  gap: 10px;
}

.project-table-title {
  color: var(--text);
  font-size: 15px;
  font-weight: 700;
}

.project-table-count {
  color: var(--text-secondary);
  font-size: 13px;
}

.project-table-wrap {
  flex: 1;
  min-height: 0;
}

.project-mobile-list {
  padding: 0;
}

.project-mobile-cards {
  display: grid;
  gap: 6px;
}

.project-mobile-card {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-height: 82px;
  padding: 10px 12px;
  background: var(--surface);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-md);
  cursor: pointer;
  transition:
    background 0.16s ease,
    border-color 0.16s ease;
}

.project-mobile-card:hover,
.project-mobile-card:focus-visible {
  background: var(--surface-tint);
  border-color: var(--primary);
  outline: none;
}

.project-mobile-card-head,
.project-mobile-card-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.project-mobile-card-summary {
  display: flex;
  align-items: center;
  gap: 5px;
  min-width: 0;
  overflow: hidden;
  color: var(--text-secondary);
  font-size: 12px;
  line-height: 18px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.project-mobile-card-title,
.project-mobile-card-code {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.project-mobile-card-title {
  font-size: 14px;
  font-weight: 700;
  line-height: 20px;
}

.project-mobile-card-code {
  color: var(--text-secondary);
  font-size: 12px;
  line-height: 18px;
}

.project-mobile-card-chevron {
  flex: 0 0 auto;
  color: var(--muted);
  font-size: 12px;
}

.project-table-wrap :deep(.vxe-header--column .vxe-cell) {
  justify-content: center;
  text-align: center;
}

.project-table-wrap :deep(.vxe-grid) {
  height: 100%;
}

.project-code-link,
.project-name-text,
.project-contract-amount {
  font-size: 14px;
  line-height: 22px;
}

.project-code-link {
  height: auto;
  padding: 0;
  font-weight: 700;
}

.project-code-link,
.project-code-link:hover,
.project-code-link:focus {
  background: transparent;
}

.project-pagination {
  padding: 8px 18px;
  border-top: 1px solid var(--border-subtle);
}

@media (width < 500px) {
  .project-table-panel.lg-list-table-panel {
    flex: 0 0 auto;
    min-height: 0;
    overflow: visible;
    background: transparent;
    border: 0;
    border-radius: 0;
    box-shadow: none;
  }

  .project-table-panel .project-table-wrap {
    flex: 0 0 auto;
    height: auto;
    min-height: 0;
    overflow: visible;
  }

  .project-pagination {
    justify-content: center;
    min-height: 40px;
    padding: 2px 0;
    border-top: 0;
  }
}

@media (max-width: 1200px) {
  .project-table-toolbar-right {
    flex-wrap: wrap;
  }
}
</style>
