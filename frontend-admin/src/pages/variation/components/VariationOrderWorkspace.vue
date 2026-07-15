<script setup lang="ts">
import { ref, type PropType } from 'vue'
import {
  CheckCircleOutlined,
  ExclamationCircleOutlined,
  FileTextOutlined,
  FilterOutlined,
  MoreOutlined,
  PlusOutlined,
  ReloadOutlined,
  SearchOutlined,
  WalletOutlined,
} from '@ant-design/icons-vue'
import { ColumnSettingsButton } from '@/components/list-page'
import type { VarOrderVO } from '@/types/variation'

const mobileFiltersOpen = ref(false)

type FilterState = {
  projectId?: string
  contractId?: string
  partnerId?: string
  varType?: string
  direction?: string
  varCode: string
}

type FilterVisibilityState = {
  projectId: boolean
  varType: boolean
  direction: boolean
}

type FilterSettingItem = {
  key: keyof FilterVisibilityState
  label: string
}

type ProjectOption = {
  id: string
  projectName?: string
}

type SummaryItem = {
  key?: string
  label: string
  count: number
  percent: number
}

type ColumnSetting = {
  field?: string
  title?: string
  visible?: boolean
}

defineProps({
  filter: {
    type: Object as PropType<FilterState>,
    required: true,
  },
  filterVisibility: {
    type: Object as PropType<FilterVisibilityState>,
    required: true,
  },
  filterSettingItems: {
    type: Array as PropType<FilterSettingItem[]>,
    required: true,
  },
  projectList: {
    type: Array as PropType<ProjectOption[]>,
    required: true,
  },
  varTypeOptions: {
    type: Array as PropType<Array<{ value: string; label: string }>>,
    required: true,
  },
  directionOptions: {
    type: Array as PropType<Array<{ value: string; label: string }>>,
    required: true,
  },
  total: {
    type: Number,
    required: true,
  },
  isMobile: {
    type: Boolean,
    required: true,
  },
  loading: {
    type: Boolean,
    required: true,
  },
  tableData: {
    type: Array as PropType<VarOrderVO[]>,
    required: true,
  },
  visibleGridColumns: {
    type: Array as PropType<Record<string, unknown>[]>,
    required: true,
  },
  columnSettings: {
    type: Array as PropType<ColumnSetting[]>,
    required: true,
  },
  colVisible: {
    type: Object as PropType<Record<string, boolean>>,
    required: true,
  },
  variationStats: {
    type: Object as PropType<{ total: number; approved: number; cost: number; draft: number }>,
    required: true,
  },
  variationTypeSummary: {
    type: Array as PropType<SummaryItem[]>,
    required: true,
  },
  approvalStatusSummary: {
    type: Array as PropType<SummaryItem[]>,
    required: true,
  },
  recentVariations: {
    type: Array as PropType<VarOrderVO[]>,
    required: true,
  },
  approvalDraft: {
    type: String,
    required: true,
  },
  varTypeLabel: {
    type: Object as PropType<Record<string, string>>,
    required: true,
  },
  varTypeColor: {
    type: Object as PropType<Record<string, string>>,
    required: true,
  },
  approvalStatusLabel: {
    type: Function as PropType<(status: string | undefined) => string>,
    required: true,
  },
  approvalStatusColor: {
    type: Function as PropType<(status: string | undefined) => string>,
    required: true,
  },
  fmtWan: {
    type: Function as PropType<(val: string | undefined) => string>,
    required: true,
  },
  handleProjectChange: {
    type: Function as PropType<(value: string | undefined) => void>,
    required: true,
  },
  handleSearch: {
    type: Function as PropType<() => void>,
    required: true,
  },
  handleReset: {
    type: Function as PropType<() => void>,
    required: true,
  },
  toggleFilterVisibility: {
    type: Function as PropType<(key: FilterSettingItem['key']) => void>,
    required: true,
  },
  toggleCol: {
    type: Function as PropType<(field: string) => void>,
    required: true,
  },
  fetchData: {
    type: Function as PropType<() => void>,
    required: true,
  },
  handleAdd: {
    type: Function as PropType<() => void>,
    required: true,
  },
  handleView: {
    type: Function as PropType<(row: VarOrderVO) => void>,
    required: true,
  },
  handleEdit: {
    type: Function as PropType<(row: VarOrderVO) => void>,
    required: true,
  },
  handleSubmitApproval: {
    type: Function as PropType<(row: VarOrderVO) => void>,
    required: true,
  },
  handleDelete: {
    type: Function as PropType<(row: VarOrderVO) => void>,
    required: true,
  },
  handlePageChange: {
    type: Function as PropType<(page: number) => void>,
    required: true,
  },
  handlePageSizeChange: {
    type: Function as PropType<(page: number, pageSize: number) => void>,
    required: true,
  },
  pageNo: {
    type: Number,
    required: true,
  },
  pageSize: {
    type: Number,
    required: true,
  },
})
</script>

<template>
  <div class="lg-grid vo-workspace project-operation-workspace">
    <div class="lg-left vo-main-column project-operation-main-column">
      <div class="lg-kpi-strip vo-kpi-summary project-operation-kpi" aria-label="变更签证关键指标">
        <div class="vo-kpi-item">
          <span class="vo-kpi-icon is-total"><FileTextOutlined /></span>
          <span class="vo-kpi-label">签证总数</span>
          <span class="vo-kpi-value">{{ variationStats.total }} <small>单</small></span>
        </div>
        <div class="vo-kpi-item">
          <span class="vo-kpi-icon is-approved"><CheckCircleOutlined /></span>
          <span class="vo-kpi-label">已通过</span>
          <span class="vo-kpi-value">{{ variationStats.approved }} <small>单</small></span>
        </div>
        <div class="vo-kpi-item">
          <span class="vo-kpi-icon is-cost"><WalletOutlined /></span>
          <span class="vo-kpi-label">成本方向</span>
          <span class="vo-kpi-value">{{ variationStats.cost }} <small>单</small></span>
        </div>
        <div class="vo-kpi-item is-warn">
          <span class="vo-kpi-icon is-draft"><ExclamationCircleOutlined /></span>
          <span class="vo-kpi-label">草稿待提</span>
          <span class="vo-kpi-value">{{ variationStats.draft }} <small>单</small></span>
        </div>
      </div>

      <section
        class="lg-search-bar vo-query-panel project-operation-query-panel"
        aria-label="变更签证查询条件"
      >
        <div
          id="variation-filter-panel"
          class="vo-query-primary project-operation-filter-panel"
          :class="{ 'is-open': mobileFiltersOpen }"
        >
          <a-select
            v-if="filterVisibility.projectId"
            v-model:value="filter.projectId"
            class="vo-query-select"
            placeholder="全部项目"
            allow-clear
            size="large"
            @change="handleProjectChange"
          >
            <a-select-option v-for="p in projectList" :key="p.id" :value="p.id">
              {{ p.projectName }}
            </a-select-option>
          </a-select>
          <a-select
            v-if="filterVisibility.varType"
            v-model:value="filter.varType"
            class="vo-query-select"
            placeholder="变更类型"
            allow-clear
            size="large"
            @change="handleSearch"
          >
            <a-select-option v-for="o in varTypeOptions" :key="o.value" :value="o.value">
              {{ o.label }}
            </a-select-option>
          </a-select>
          <a-select
            v-if="filterVisibility.direction"
            v-model:value="filter.direction"
            class="vo-query-select"
            placeholder="方向"
            allow-clear
            size="large"
            @change="handleSearch"
          >
            <a-select-option v-for="o in directionOptions" :key="o.value" :value="o.value">
              {{ o.label }}
            </a-select-option>
          </a-select>
        </div>
        <div class="vo-query-keyword-row">
          <a-input
            v-model:value="filter.varCode"
            class="vo-keyword-search"
            placeholder="搜索变更编号、名称"
            allow-clear
            size="large"
            @press-enter="handleSearch"
          >
            <template #prefix><SearchOutlined class="vo-search-prefix-icon" /></template>
          </a-input>
          <div class="vo-query-actions">
            <a-button
              class="project-operation-desktop-query-action"
              type="primary"
              size="large"
              @click="handleSearch"
              >搜索</a-button
            >
            <a-button
              class="project-operation-desktop-query-action"
              size="large"
              @click="handleReset"
            >
              <template #icon><ReloadOutlined /></template>
              重置
            </a-button>
            <a-button
              class="project-operation-filter-toggle"
              size="large"
              :aria-expanded="mobileFiltersOpen"
              aria-controls="variation-filter-panel"
              @click="mobileFiltersOpen = !mobileFiltersOpen"
            >
              <template #icon><FilterOutlined /></template>筛选
            </a-button>
          </div>
        </div>
      </section>

      <main class="lg-list-table-panel vo-table-panel project-operation-table-panel">
        <div class="lg-toolbar vo-table-toolbar">
          <div class="lg-toolbar-left">
            <div class="vo-table-heading">
              <span class="vo-table-title">签证列表</span>
              <span class="vo-table-count">共 {{ total }} 条</span>
            </div>
          </div>
          <div class="lg-toolbar-right vo-table-toolbar-right">
            <ColumnSettingsButton
              v-if="!isMobile"
              :columns="columnSettings"
              :visible="colVisible"
              @toggle="toggleCol"
            />
            <a-button aria-label="刷新变更签证列表" title="刷新" @click="fetchData">
              <template #icon><ReloadOutlined /></template>
              刷新
            </a-button>
            <a-button type="primary" @click="handleAdd">
              <template #icon><PlusOutlined /></template>
              新建签证
            </a-button>
          </div>
        </div>

        <div v-if="isMobile" class="vo-mobile-list">
          <div v-if="loading" class="vo-mobile-state">
            <a-spin />
          </div>
          <a-empty v-else-if="!tableData.length" description="暂无变更签证" />
          <template v-else>
            <article v-for="row in tableData" :key="row.id" class="vo-mobile-card">
              <div class="vo-mobile-card-head">
                <a-button class="vo-var-link" type="link" @click="handleView(row)">
                  {{ row.varCode || '-' }}
                </a-button>
                <a-tag :color="approvalStatusColor(row.approvalStatus)" size="small">
                  {{ approvalStatusLabel(row.approvalStatus) }}
                </a-tag>
              </div>
              <div class="vo-mobile-card-title">{{ row.varName || '-' }}</div>
              <div class="vo-mobile-card-project">{{ row.projectName || '-' }}</div>
              <div class="vo-mobile-card-meta">
                <span>{{ varTypeLabel[row.varType] ?? row.varType ?? '-' }}</span>
                <span>{{ row.direction === 'COST' ? '成本' : row.direction || '-' }}</span>
              </div>
              <div class="vo-mobile-card-amount">
                上报金额：{{ fmtWan(row.reportedAmount) }} 万元
              </div>
              <div class="vo-mobile-card-actions">
                <a-button type="link" size="small" @click="handleView(row)">查看</a-button>
                <a-button type="link" size="small" @click="handleEdit(row)">编辑</a-button>
                <a-button
                  v-if="row.approvalStatus === approvalDraft"
                  type="link"
                  size="small"
                  @click="handleSubmitApproval(row)"
                >
                  提交
                </a-button>
                <a-button danger type="link" size="small" @click="handleDelete(row)">删除</a-button>
              </div>
            </article>
          </template>
        </div>

        <div v-else class="lg-table-wrap vo-table-wrap">
          <vxe-grid
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
            <template #varCode="{ row }">
              <a-button class="vo-var-link" type="link" @click="handleView(row)">
                {{ row.varCode }}
              </a-button>
            </template>
            <template #varType="{ row }">
              <a-tag :color="varTypeColor[row.varType]" size="small">
                {{ varTypeLabel[row.varType] ?? row.varType }}
              </a-tag>
            </template>
            <template #direction="{ row }">
              <a-tag :color="row.direction === 'COST' ? 'red' : 'green'" size="small">{{
                row.direction === 'COST' ? '成本' : row.direction
              }}</a-tag>
            </template>
            <template #reportedAmount="{ row }">
              <span>{{ fmtWan(row.reportedAmount) }} 万</span>
            </template>
            <template #approvedAmount="{ row }">
              <span>{{ fmtWan(row.approvedAmount) }} 万</span>
            </template>
            <template #confirmedAmount="{ row }">
              <span>{{ fmtWan(row.confirmedAmount) }} 万</span>
            </template>
            <template #approvalStatus="{ row }">
              <a-tag :color="approvalStatusColor(row.approvalStatus)" size="small">
                {{ approvalStatusLabel(row.approvalStatus) }}
              </a-tag>
            </template>
            <template #ops="{ row }">
              <a-dropdown :trigger="['click']">
                <a-button class="lg-row-action-trigger" size="small" type="text">
                  <MoreOutlined />
                </a-button>
                <template #overlay>
                  <a-menu>
                    <a-menu-item
                      v-if="row.approvalStatus === approvalDraft"
                      @click="handleSubmitApproval(row)"
                    >
                      提交审批
                    </a-menu-item>
                    <a-menu-item @click="handleEdit(row)">编辑</a-menu-item>
                    <a-menu-item danger @click="handleDelete(row)">删除</a-menu-item>
                  </a-menu>
                </template>
              </a-dropdown>
            </template>
          </vxe-grid>
        </div>

        <div class="lg-pagination vo-pagination">
          <span class="lg-total">共 {{ total }} 条</span>
          <a-pagination
            :current="pageNo"
            :page-size="pageSize"
            :total="total"
            :page-size-options="['10', '20', '50']"
            show-size-changer
            show-quick-jumper
            @change="handlePageChange"
            @show-size-change="handlePageSizeChange"
          />
        </div>
      </main>
    </div>

    <aside
      class="lg-analysis-rail vo-analysis-rail project-operation-analysis-rail"
      aria-label="变更签证辅助分析"
    >
      <div class="lg-analysis-panel lg-fill-card vo-analysis-panel">
        <header class="vo-analysis-head">
          <div>
            <div class="vo-analysis-title">签证分析</div>
          </div>
        </header>

        <section class="vo-analysis-section">
          <div class="vo-section-title">变更类型分布</div>
          <div v-for="item in variationTypeSummary" :key="item.label" class="lg-type-row">
            <span class="lg-type-dot vo-dot-primary"></span>
            <span class="lg-type-label">{{ item.label }}</span>
            <span class="lg-type-num">{{ item.count }}</span>
            <span class="lg-type-pct">{{ item.percent }}%</span>
          </div>
        </section>

        <section class="vo-analysis-section">
          <div class="vo-section-title">审批状态</div>
          <div v-for="item in approvalStatusSummary" :key="item.key" class="lg-type-row">
            <span class="lg-type-dot vo-dot-success"></span>
            <span class="lg-type-label">{{ item.label }}</span>
            <span class="lg-type-num">{{ item.count }}</span>
            <span class="lg-type-pct">{{ item.percent }}%</span>
          </div>
        </section>

        <section class="vo-analysis-section">
          <div class="vo-warning-head">
            <div class="vo-section-title">近期签证</div>
            <span class="vo-warning-count">{{ recentVariations.length }} 项</span>
          </div>
          <div v-for="item in recentVariations" :key="item.id" class="lg-type-row">
            <span class="lg-type-dot vo-dot-warning"></span>
            <span class="lg-type-label">{{ item.varName }}</span>
          </div>
          <div v-if="!recentVariations.length" class="lg-warning-empty">暂无变更签证</div>
        </section>
      </div>
    </aside>
  </div>
</template>

<style scoped>
.vo-main-column {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-height: 0;
}

.vo-kpi-summary {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 0;
  margin-bottom: 0;
  overflow: hidden;
  background: var(--surface);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-soft);
}

.vo-kpi-item {
  position: relative;
  display: grid;
  grid-template-columns: 38px minmax(0, 1fr);
  grid-template-rows: 19px 27px;
  column-gap: 10px;
  align-items: center;
  min-width: 0;
  padding: 16px 18px;
  border-right: 1px solid var(--border-subtle);
}

.vo-kpi-item:last-child {
  border-right: 0;
}

.vo-kpi-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  color: var(--primary);
  background: var(--primary-soft);
  border-radius: var(--radius-sm);
  grid-row: 1 / span 2;
}

.vo-kpi-icon.is-approved {
  color: var(--success);
  background: var(--success-soft);
}

.vo-kpi-icon.is-cost {
  color: var(--warning);
  background: var(--warning-soft);
}

.vo-kpi-icon.is-draft {
  color: var(--error);
  background: var(--error-soft);
}

.vo-kpi-label {
  overflow: hidden;
  color: var(--text-secondary);
  font-size: 13px;
  font-weight: 600;
  line-height: 18px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.vo-kpi-value {
  overflow: hidden;
  color: var(--text);
  font-size: 24px;
  font-weight: 800;
  line-height: 28px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.vo-kpi-value small {
  margin-left: 4px;
  color: var(--text-secondary);
  font-size: 13px;
  font-weight: 600;
}

.vo-query-panel {
  align-items: stretch;
  flex-direction: column;
  gap: 12px;
  margin: 0;
}

.vo-query-primary {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.vo-query-keyword-row {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.vo-keyword-search {
  flex: 1 1 auto;
  width: auto;
  min-width: 0;
}

.vo-search-prefix-icon {
  color: var(--text-secondary);
}

.vo-query-select {
  flex: 0 0 160px;
  width: 160px;
}

.vo-query-actions {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  gap: 8px;
}

.vo-table-panel {
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

.vo-table-toolbar {
  border-bottom: 1px solid var(--border-subtle);
}

.vo-table-title {
  color: var(--text);
  font-size: 15px;
  font-weight: 700;
}

.vo-table-heading,
.vo-table-toolbar-right {
  display: flex;
  align-items: center;
  gap: 10px;
}

.vo-table-count,
.vo-warning-count {
  color: var(--text-secondary);
  font-size: 13px;
}

.vo-table-wrap {
  flex: 1;
  min-height: 0;
}

.vo-mobile-list {
  display: grid;
  gap: 12px;
  padding: 12px;
}

.vo-mobile-state {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 180px;
}

.vo-mobile-card {
  display: grid;
  gap: 10px;
  min-width: 0;
  padding: 14px;
  background: var(--surface);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-md);
}

.vo-mobile-card-head,
.vo-mobile-card-meta,
.vo-mobile-card-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  min-width: 0;
  flex-wrap: wrap;
}

.vo-mobile-card-title {
  color: var(--text);
  font-size: 15px;
  font-weight: 700;
  line-height: 22px;
  word-break: break-word;
}

.vo-mobile-card-project,
.vo-mobile-card-meta,
.vo-mobile-card-amount {
  color: var(--text-secondary);
  font-size: 13px;
  line-height: 20px;
  word-break: break-word;
}

.vo-table-wrap :deep(.vxe-header--column .vxe-cell) {
  justify-content: center;
  text-align: center;
}

.vo-table-wrap :deep(.vxe-grid) {
  height: 100%;
}

.vo-var-link {
  height: auto;
  padding: 0;
  font-weight: 700;
}

.vo-var-link,
.vo-var-link:hover,
.vo-var-link:focus {
  background: transparent;
}

.vo-pagination {
  border-top: 1px solid var(--border-subtle);
}

.vo-analysis-panel {
  display: flex;
  flex-direction: column;
  gap: 0;
  padding: 0 0 12px;
}

.vo-analysis-head,
.vo-warning-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.vo-analysis-head {
  padding: 12px 16px 10px;
  border-bottom: 1px solid var(--border-subtle);
}

.vo-analysis-title {
  color: var(--text);
  font-size: 15px;
  font-weight: 700;
  line-height: 20px;
}

.vo-analysis-section {
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-width: 0;
  padding: 10px 16px 0;
}

.vo-analysis-section + .vo-analysis-section {
  margin-top: 10px;
  padding-top: 12px;
  border-top: 1px solid var(--border-subtle);
}

.vo-section-title {
  color: var(--text);
  font-size: 14px;
  font-weight: 700;
  line-height: 20px;
}

.vo-analysis-section :deep(.lg-type-row),
.vo-analysis-section .lg-type-row {
  grid-template-columns: 9px minmax(60px, 1fr) 28px 38px;
}

.vo-dot-primary {
  background: var(--primary);
}

.vo-dot-success {
  background: var(--success);
}

.vo-dot-warning {
  background: var(--warning);
}

@media (max-width: 1200px) {
  .vo-query-panel,
  .vo-query-keyword-row,
  .vo-query-primary {
    align-items: stretch;
    flex-direction: column;
  }

  .vo-query-actions {
    justify-content: flex-start;
  }

  .vo-table-toolbar-right {
    flex-wrap: wrap;
  }

  .vo-keyword-search,
  .vo-query-select,
  .vo-analysis-rail {
    width: 100%;
    min-width: 0;
  }
}

@media (max-width: 768px) {
  .vo-kpi-summary {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .vo-kpi-item {
    border-right: 0;
    border-bottom: 1px solid var(--border-subtle);
  }

  .vo-kpi-item:nth-last-child(-n + 2) {
    border-bottom: 0;
  }

  .vo-table-toolbar,
  .vo-table-toolbar .lg-toolbar-left,
  .vo-table-toolbar .lg-toolbar-right {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
