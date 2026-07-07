<script setup lang="ts">
import { onMounted, computed, reactive, ref } from 'vue'
import { MoreOutlined, PlusOutlined, ReloadOutlined, SearchOutlined, SettingOutlined } from '@ant-design/icons-vue'
import { useReferenceStore } from '@/stores/reference'
import { getUserList } from '@/api/modules/user'
import type { SysUserVO } from '@/types/user'
import type { SelectOption } from '@/types/ui'
import { fetchDictData, getDictLabelSync, getDictTagColorSync } from '@/utils/dict'

import { useRequisitionList, fmtAmount } from './composables/useRequisitionList'
import { useRequisitionForm } from './composables/useRequisitionForm'
import RequisitionKpiStrip from './components/RequisitionKpiStrip.vue'
import RequisitionFormModal from './components/RequisitionFormModal.vue'
import { ColumnSettingsButton } from '@/components/list-page'
import { useColumnSettings } from '@/composables/useColumnSettings'

// 字典常量 - 审批状态
const APPROVAL_DRAFT = 'DRAFT'
const APPROVAL_APPROVING = 'APPROVING'
const APPROVAL_APPROVED = 'APPROVED'
const APPROVAL_REJECTED = 'REJECTED'
const APPROVAL_STATUS_DICT = 'approval_status'
const APPROVAL_STATUS_LABEL: Record<string, string> = {
  [APPROVAL_DRAFT]: '草稿',
  [APPROVAL_APPROVING]: '审批中',
  [APPROVAL_APPROVED]: '已通过',
  [APPROVAL_REJECTED]: '已驳回',
}
const APPROVAL_STATUS_COLOR: Record<string, string> = {
  [APPROVAL_DRAFT]: '#94a3b8',
  [APPROVAL_APPROVING]: '#1677ff',
  [APPROVAL_APPROVED]: '#52c41a',
  [APPROVAL_REJECTED]: '#ff4d4f',
}

const referenceStore = useReferenceStore()
const projectList = computed(() => referenceStore.projects ?? [])
const contractList = computed(() => referenceStore.contracts ?? [])

const userList = ref<SysUserVO[]>([])
const filterVisibility = reactive({
  projectId: true,
  warehouseId: true,
  approvalStatus: true,
})
const filterSettingItems = [
  { key: 'projectId', label: '项目' },
  { key: 'warehouseId', label: '仓库' },
  { key: 'approvalStatus', label: '审批状态' },
] as const

const {
  filter,
  loading,
  tableData,
  total,
  pageNo,
  pageSize,
  warehouseList,
  kpiTotalCount,
  kpiTotalAmount,
  gridColumns,
  fetchData,
  handleSearch,
  handleReset,
  handlePageChange,
  handlePageSizeChange,
  handleDelete,
  handleSubmitApproval,
  init,
} = useRequisitionList()

const {
  visibleColumns: visibleGridColumns,
  columnSettings,
  colVisible,
  toggleCol,
} = useColumnSettings('requisition_list_cols_v2', gridColumns, {
  requisitionDate: false,
  approvalStatus: false,
})

const {
  modalVisible,
  modalTitle,
  formData,
  itemList,
  handleAdd,
  handleEdit,
  handleAddItem,
  handleRemoveItem,
  handleItemQtyChange,
  handleItemPriceChange,
  itemsTotalAmount,
  handleModalOk,
  handleModalCancel,
} = useRequisitionForm(fetchData)

async function handleView(row: Parameters<typeof handleEdit>[0]) {
  await handleEdit(row)
  modalTitle.value = '查看领料申请'
}

function approvalStatusLabel(status: string) {
  return getDictLabelSync(APPROVAL_STATUS_DICT, status, APPROVAL_STATUS_LABEL)
}

function approvalStatusColor(status: string) {
  return getDictTagColorSync(APPROVAL_STATUS_DICT, status, APPROVAL_STATUS_COLOR)
}

const requisitionStatusSummary = computed(() => [
  {
    label: '已出库',
    count: stockedCount.value,
    color: '#52c41a',
    pct: statusPct(stockedCount.value),
  },
  {
    label: '未出库',
    count: unstockedCount.value,
    color: '#faad14',
    pct: statusPct(unstockedCount.value),
  },
])

const stockedCount = computed(
  () => tableData.value.filter((item) => item.stockOutFlag === 1).length,
)
const unstockedCount = computed(
  () => tableData.value.filter((item) => item.stockOutFlag !== 1).length,
)
const pendingApprovalCount = computed(
  () =>
    tableData.value.filter((item) => [APPROVAL_DRAFT, APPROVAL_APPROVING].includes(item.approvalStatus ?? ''))
      .length,
)
const approvalSummary = computed(() => [
  {
    label: approvalStatusLabel(APPROVAL_DRAFT),
    count: tableData.value.filter((item) => item.approvalStatus === APPROVAL_DRAFT).length,
    color: approvalStatusColor(APPROVAL_DRAFT),
  },
  {
    label: approvalStatusLabel(APPROVAL_APPROVING),
    count: tableData.value.filter((item) => item.approvalStatus === APPROVAL_APPROVING).length,
    color: approvalStatusColor(APPROVAL_APPROVING),
  },
  {
    label: approvalStatusLabel(APPROVAL_APPROVED),
    count: tableData.value.filter((item) => item.approvalStatus === APPROVAL_APPROVED).length,
    color: approvalStatusColor(APPROVAL_APPROVED),
  },
  {
    label: approvalStatusLabel(APPROVAL_REJECTED),
    count: tableData.value.filter((item) => item.approvalStatus === APPROVAL_REJECTED).length,
    color: approvalStatusColor(APPROVAL_REJECTED),
  },
])
const recentRequisitions = computed(() => tableData.value.slice(0, 4))

function statusPct(count: number) {
  const base = tableData.value.length || 0
  if (!base) return 0
  return Math.round((count / base) * 100)
}

function toggleFilterVisibility(key: (typeof filterSettingItems)[number]['key']) {
  filterVisibility[key] = !filterVisibility[key]
}

async function fetchUsers() {
  try {
    const res = await getUserList({ pageNo: 1, pageSize: 200 })
    userList.value = res.records
  } catch (e: unknown) {
    console.error(e)
    userList.value = []
  }
}

onMounted(() => {
  fetchDictData(APPROVAL_STATUS_DICT)
  referenceStore.fetchProjects()
  referenceStore.fetchContracts({ contractType: 'PURCHASE' })
  fetchUsers()
  init()
})
</script>

<template>
  <div class="lg-list-page lg-page app-page requisition-page">
    <div class="lg-page-head requisition-page-head">
      <div class="requisition-page-meta-row">
        <a-breadcrumb class="lg-breadcrumb">
          <a-breadcrumb-item>库存管理</a-breadcrumb-item>
          <a-breadcrumb-item>领料申请</a-breadcrumb-item>
        </a-breadcrumb>
      </div>
    </div>

    <div class="lg-grid">
      <div class="lg-left requisition-main-column">
        <RequisitionKpiStrip
          :total-count="kpiTotalCount"
          :total-amount="kpiTotalAmount"
          :stocked-count="stockedCount"
          :unstocked-count="unstockedCount"
          :pending-count="pendingApprovalCount"
          :fmt-amount="fmtAmount"
        />

        <div class="lg-search-bar requisition-search-bar">
          <div class="requisition-search-fields">
            <a-select
              v-if="filterVisibility.projectId"
              v-model:value="filter.projectId"
              placeholder="全部项目"
              allow-clear
              size="large"
              show-search
              :filter-option="
                (input: string, option: SelectOption) =>
                  option.label?.toLowerCase().includes(input.toLowerCase())
              "
              @change="
                (v: string | undefined) => {
                  filter.contractId = undefined
                  if (v) referenceStore.fetchContracts({ projectId: v })
                  handleSearch()
                }
              "
            >
              <a-select-option v-for="p in projectList" :key="p.id" :value="p.id">
                {{ p.projectName }}
              </a-select-option>
            </a-select>
            <a-select
              v-if="filterVisibility.warehouseId"
              v-model:value="filter.warehouseId"
              placeholder="全部仓库"
              allow-clear
              size="large"
              @change="handleSearch"
            >
              <a-select-option v-for="w in warehouseList" :key="w.id" :value="w.id">
                {{ w.warehouseName }}
              </a-select-option>
            </a-select>
            <a-select
              v-if="filterVisibility.approvalStatus"
              v-model:value="filter.approvalStatus"
              placeholder="全部审批状态"
              allow-clear
              size="large"
              @change="handleSearch"
            >
              <a-select-option value="DRAFT">草稿</a-select-option>
              <a-select-option value="APPROVING">审批中</a-select-option>
              <a-select-option value="APPROVED">已通过</a-select-option>
              <a-select-option value="REJECTED">已驳回</a-select-option>
            </a-select>
          </div>
          <div class="requisition-search-keyword-row">
            <a-input
              v-model:value="filter.requisitionCode"
              placeholder="搜索领料单号"
              allow-clear
              size="large"
              @press-enter="handleSearch"
            >
              <template #prefix><SearchOutlined style="color: var(--text-secondary)" /></template>
            </a-input>
            <div class="requisition-search-actions">
              <a-button type="primary" size="large" @click="handleSearch">查询</a-button>
              <a-button size="large" @click="handleReset">
                <template #icon><ReloadOutlined /></template>
                重置
              </a-button>
              <a-dropdown trigger="click">
                <a-button size="large">
                  <template #icon><SettingOutlined /></template>
                  筛选栏设置
                </a-button>
                <template #overlay>
                  <a-menu>
                    <a-menu-item
                      v-for="item in filterSettingItems"
                      :key="item.key"
                      @click="toggleFilterVisibility(item.key)"
                    >
                      <a-checkbox :checked="filterVisibility[item.key]">
                        {{ item.label }}
                      </a-checkbox>
                    </a-menu-item>
                  </a-menu>
                </template>
              </a-dropdown>
            </div>
          </div>
        </div>

        <main class="lg-list-table-panel requisition-table-panel">
          <div class="lg-toolbar">
            <div class="lg-toolbar-left">
              <div class="requisition-table-title">
                <strong>领料明细</strong>
                <span>共 {{ total }} 条</span>
              </div>
            </div>
            <div class="lg-toolbar-right">
              <ColumnSettingsButton
                :columns="columnSettings"
                :visible="colVisible"
                @toggle="toggleCol"
              />
              <a-button type="primary" @click="handleAdd">
                <template #icon><PlusOutlined /></template>
                新建领料
              </a-button>
              <a-button @click="fetchData">
                <template #icon><ReloadOutlined /></template>
                刷新
              </a-button>
            </div>
          </div>

          <div class="lg-table-wrap">
            <vxe-grid
              :data="tableData"
              :columns="visibleGridColumns"
              :loading="loading"
              :column-config="{ resizable: true }"
              stripe
              border="inner"
              size="small"
            >
              <template #requisitionCode="{ row }">
                <a-button class="requisition-code-link" type="link" @click="handleView(row)">
                  {{ row.requisitionCode || '-' }}
                </a-button>
              </template>
              <template #totalAmount="{ row }">
                <span v-if="row.totalAmount" class="lg-money">
                  {{
                    Number(row.totalAmount).toLocaleString('zh-CN', { minimumFractionDigits: 2 })
                  }}
                </span>
                <span v-else class="lg-none">-</span>
              </template>
              <template #stockOutFlag="{ row }">
                <a-tag :color="row.stockOutFlag === 1 ? 'success' : 'default'">
                  {{ row.stockOutFlag === 1 ? '已出库' : '未出库' }}
                </a-tag>
              </template>
              <template #approvalStatus="{ row }">
                <ApprovalStatusTag :status="row.approvalStatus" />
              </template>
              <template #action="{ row }">
                <a-dropdown :trigger="['click']">
                  <a-button class="lg-row-action-trigger" size="small" type="text">
                    <MoreOutlined />
                  </a-button>
                  <template #overlay>
                    <a-menu>
                      <a-menu-item @click="handleEdit(row)">编辑</a-menu-item>
                      <a-menu-item danger @click="handleDelete(row)">删除</a-menu-item>
                      <a-menu-item
                        v-if="row.approvalStatus === APPROVAL_DRAFT"
                        @click="handleSubmitApproval(row)"
                      >
                        提交审批
                      </a-menu-item>
                    </a-menu>
                  </template>
                </a-dropdown>
              </template>
            </vxe-grid>
          </div>

          <!-- 分页 -->
          <div class="lg-pagination">
            <span class="lg-total">共 {{ total }} 条</span>
            <a-pagination
              v-model:current="pageNo"
              v-model:page-size="pageSize"
              :total="total"
              :page-size-options="['10', '20', '50', '100']"
              show-size-changer
              show-quick-jumper
              @change="handlePageChange"
              @show-size-change="handlePageSizeChange"
            />
          </div>
        </main>
      </div>

      <aside class="lg-analysis-rail requisition-analysis-rail" aria-label="领料申请辅助分析">
        <div class="lg-analysis-panel lg-fill-card requisition-analysis-panel">
          <header class="requisition-analysis-head">
            <div>
              <div class="requisition-analysis-title">辅助分析</div>
              <div class="requisition-analysis-subtitle">出库、审批与近期单据</div>
            </div>
            <a-button type="link" size="small" @click="fetchData">刷新</a-button>
          </header>
          <section class="requisition-analysis-focus">
            <span>本页重点</span>
            <strong>{{ unstockedCount }} 单</strong>
            <em>已申请但未完成出库的领料单，优先跟踪仓库处理。</em>
          </section>
          <section class="requisition-analysis-section">
            <div class="requisition-section-head">
              <strong>出库状态分布</strong>
              <span>{{ tableData.length }} 单</span>
            </div>
            <div class="requisition-bar-list">
              <div
                v-for="item in requisitionStatusSummary"
                :key="item.label"
                class="requisition-bar-row"
              >
                <div class="requisition-bar-meta">
                  <span><i :style="{ background: item.color }"></i>{{ item.label }}</span>
                  <strong>{{ item.count }} 单</strong>
                </div>
                <div class="requisition-bar-track">
                  <span :style="{ width: item.pct + '%', background: item.color }"></span>
                </div>
              </div>
            </div>
          </section>
          <section class="requisition-analysis-section">
            <div class="requisition-section-head">
              <strong>审批状态</strong>
              <span>{{ pendingApprovalCount }} 待处理</span>
            </div>
            <div class="requisition-chip-list">
              <span
                v-for="item in approvalSummary"
                :key="item.label"
                class="requisition-chip"
                :style="{ borderColor: item.color, color: item.color }"
              >
                {{ item.label }} {{ item.count }}
              </span>
            </div>
          </section>
          <section class="requisition-analysis-section">
            <div class="requisition-section-head">
              <strong>近期领料</strong>
              <span>最新 4 单</span>
            </div>
            <div class="requisition-recent-list">
              <div
                v-for="item in recentRequisitions"
                :key="item.id"
                class="requisition-recent-item"
              >
                <span>{{ item.requisitionCode }}</span>
                <strong>{{ item.stockOutFlag === 1 ? '已出库' : '未出库' }}</strong>
              </div>
              <div v-if="!recentRequisitions.length" class="requisition-empty">暂无领料申请</div>
            </div>
          </section>
        </div>
      </aside>
    </div>

    <!-- Add/Edit Modal -->
    <RequisitionFormModal
      :visible="modalVisible"
      :title="modalTitle"
      :form-data="formData"
      :project-list="projectList"
      :contract-list="contractList"
      :warehouse-list="warehouseList"
      :user-list="userList"
      :item-list="itemList"
      :items-total-amount="itemsTotalAmount"
      @ok="handleModalOk"
      @cancel="handleModalCancel"
      @item-qty-change="handleItemQtyChange"
      @item-price-change="handleItemPriceChange"
      @add-item="handleAddItem"
      @remove-item="handleRemoveItem"
    />
  </div>
</template>

<style scoped>
.requisition-page {
  color: var(--text);
}

.requisition-page-head {
  padding: 0;
  background: transparent;
  border: 0;
  border-radius: 0;
  box-shadow: none;
}

.requisition-page-meta-row {
  display: flex;
  align-items: center;
  width: 100%;
  min-width: 0;
}

.lg-breadcrumb {
  margin-bottom: 0;
  font-size: 13px;
}

.requisition-search-bar {
  display: flex;
  flex: 0 0 auto;
  flex-direction: column;
  justify-content: flex-start;
  gap: 12px;
  height: auto;
  align-items: stretch;
  margin: 0;
}

.requisition-search-fields,
.requisition-search-keyword-row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
  min-width: 0;
  width: 100%;
}

.requisition-search-actions {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  margin-left: auto;
  min-width: 0;
}

.requisition-search-fields > :deep(.ant-select) {
  flex: 1 1 180px;
  min-width: 160px;
}

.requisition-search-keyword-row > :deep(.ant-input-affix-wrapper) {
  flex: 1 1 auto;
  min-width: 240px;
}

.requisition-main-column {
  min-width: 0;
  display: flex;
  flex: 1;
  flex-direction: column;
  min-height: 0;
}

.requisition-main-column > .requisition-search-bar {
  flex: 0 0 auto;
  align-self: auto;
}

.requisition-table-panel {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-height: 0;
  margin: 0;
  padding: 0;
  overflow: hidden;
  background: var(--surface);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-soft);
}

.requisition-table-title {
  display: inline-flex;
  align-items: baseline;
  gap: 8px;
  margin-right: 4px;
}

.requisition-table-title strong {
  font-size: 15px;
  color: var(--text);
}

.requisition-table-title span {
  font-size: 12px;
  color: var(--text-secondary);
}

.requisition-table-panel > .lg-toolbar {
  border-bottom: 1px solid var(--border-subtle);
}

.requisition-table-panel .lg-table-wrap {
  flex: 1;
  min-height: 0;
}

.requisition-analysis-rail {
  display: flex;
  min-height: 0;
}

.requisition-analysis-panel {
  display: flex;
  flex: 1;
  flex-direction: column;
  box-sizing: border-box;
  overflow: auto;
}

.requisition-analysis-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  padding: 18px 18px 14px;
  border-bottom: 1px solid var(--border-subtle);
}

.requisition-analysis-title {
  color: var(--text);
  font-size: 16px;
  font-weight: 800;
  line-height: 22px;
}

.requisition-analysis-subtitle {
  margin-top: 2px;
  color: var(--text-secondary);
  font-size: 12px;
  line-height: 18px;
}

.requisition-analysis-focus {
  display: grid;
  gap: 4px;
  padding: 14px 18px;
  background: var(--warning-soft);
  border-bottom: 1px solid rgba(245, 158, 11, 0.18);
}

.requisition-analysis-focus span,
.requisition-analysis-focus em {
  color: var(--text-secondary);
  font-size: 12px;
  font-style: normal;
}

.requisition-analysis-focus strong {
  color: var(--warning);
  font-size: 24px;
  font-weight: 800;
  line-height: 30px;
}

.requisition-analysis-section {
  padding: 18px;
  border-bottom: 1px solid var(--border-subtle);
}

.requisition-analysis-section:last-child {
  border-bottom: 0;
}

.requisition-section-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  margin-bottom: 14px;
}

.requisition-section-head strong {
  font-size: 15px;
  color: var(--text);
}

.requisition-section-head span {
  font-size: 12px;
  color: var(--text-secondary);
}

.requisition-bar-list,
.requisition-recent-list,
.requisition-chip-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.requisition-bar-meta,
.requisition-recent-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 10px;
  font-size: 13px;
}

.requisition-bar-meta span {
  display: inline-flex;
  align-items: center;
  min-width: 0;
  gap: 8px;
  color: var(--text);
}

.requisition-bar-meta i {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex: 0 0 auto;
}

.requisition-bar-meta strong,
.requisition-recent-item strong {
  color: var(--text);
  font-weight: 600;
  white-space: nowrap;
}

.requisition-bar-track {
  margin-top: 7px;
  height: 6px;
  border-radius: 999px;
  background: var(--surface-subtle);
  overflow: hidden;
}

.requisition-bar-track span {
  display: block;
  height: 100%;
  border-radius: inherit;
}

.requisition-chip-list {
  flex-direction: row;
  flex-wrap: wrap;
  gap: 8px;
}

.requisition-chip {
  padding: 5px 9px;
  border: 1px solid;
  border-radius: 999px;
  font-size: 12px;
  line-height: 18px;
  background: #fff;
}

.requisition-recent-item {
  padding: 10px 0;
  border-bottom: 1px solid var(--border-subtle);
}

.requisition-recent-item:last-child {
  border-bottom: 0;
}

.requisition-recent-item span {
  min-width: 0;
  overflow: hidden;
  color: var(--text);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.requisition-empty {
  padding: 18px 0;
  color: var(--text-secondary);
  text-align: center;
  font-size: 13px;
}

@media (max-width: 1200px) {
  .requisition-analysis-rail {
    width: 100%;
  }
}

@media (max-width: 768px) {
  .requisition-search-bar {
    align-items: stretch;
  }

  .requisition-search-fields,
  .requisition-search-keyword-row {
    flex-direction: column;
    align-items: stretch;
  }

  .requisition-search-actions {
    width: 100%;
    margin-left: 0;
  }

  .requisition-search-actions :deep(.ant-btn) {
    flex: 1;
  }
}

@media (min-width: 769px) {
  .requisition-search-keyword-row {
    flex-wrap: nowrap;
  }

  .requisition-search-keyword-row > :deep(.ant-input-affix-wrapper) {
    flex: 1 1 0;
    min-width: 0;
  }

  .requisition-search-actions {
    flex-wrap: nowrap;
  }
}
</style>
