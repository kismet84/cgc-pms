<script setup lang="ts">
import { onMounted, computed, ref } from 'vue'
import { MoreOutlined, PlusOutlined, ReloadOutlined, SearchOutlined } from '@ant-design/icons-vue'
import { useReferenceStore } from '@/stores/reference'
import { getUserList } from '@/api/modules/user'
import type { SysUserVO } from '@/types/user'
import type { SelectOption } from '@/types/ui'

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

const referenceStore = useReferenceStore()
const projectList = computed(() => referenceStore.projects ?? [])
const contractList = computed(() => referenceStore.contracts ?? [])

const userList = ref<SysUserVO[]>([])

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
    label: '草稿',
    count: tableData.value.filter((item) => item.approvalStatus === APPROVAL_DRAFT).length,
    color: '#94a3b8',
  },
  {
    label: '审批中',
    count: tableData.value.filter((item) => item.approvalStatus === APPROVAL_APPROVING).length,
    color: '#1677ff',
  },
  {
    label: '已通过',
    count: tableData.value.filter((item) => item.approvalStatus === APPROVAL_APPROVED).length,
    color: '#52c41a',
  },
  {
    label: '已驳回',
    count: tableData.value.filter((item) => item.approvalStatus === APPROVAL_REJECTED).length,
    color: '#ff4d4f',
  },
])
const recentRequisitions = computed(() => tableData.value.slice(0, 4))

function statusPct(count: number) {
  const base = tableData.value.length || 0
  if (!base) return 0
  return Math.round((count / base) * 100)
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
  referenceStore.fetchProjects()
  referenceStore.fetchContracts({ contractType: 'PURCHASE' })
  fetchUsers()
  init()
})
</script>

<template>
  <div class="lg-list-page lg-page app-page requisition-page">
    <div class="lg-page-head requisition-page-head">
      <div class="requisition-title-block">
        <a-breadcrumb class="lg-breadcrumb">
          <a-breadcrumb-item>库存管理</a-breadcrumb-item>
          <a-breadcrumb-item>领料申请</a-breadcrumb-item>
        </a-breadcrumb>
        <div class="requisition-title-row">
          <h1>领料申请</h1>
          <span>统一查看项目领料、出库状态、审批进度与金额汇总。</span>
        </div>
      </div>
    </div>

    <div class="lg-search-bar requisition-search-bar">
      <a-input
        v-model:value="filter.requisitionCode"
        placeholder="搜索领料单号…"
        allow-clear
        size="large"
        @press-enter="handleSearch"
      >
        <template #prefix><SearchOutlined style="color: var(--text-secondary)" /></template>
      </a-input>
      <a-select
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
      <a-button type="primary" size="large" @click="handleSearch">查询</a-button>
      <a-button size="large" @click="handleReset">
        <template #icon><ReloadOutlined /></template>
        重置
      </a-button>
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

        <main class="lg-list-table-panel requisition-table-panel">
          <div class="lg-toolbar">
            <div class="lg-toolbar-left">
              <div class="requisition-table-title">
                <strong>领料明细</strong>
                <span>共 {{ total }} 条</span>
              </div>
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
        <div class="requisition-analysis-panel">
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
  color: #0f172a;
}

.requisition-page-head {
  margin-bottom: 7px;
}

.lg-breadcrumb {
  margin-bottom: 5px;
  font-size: 13px;
}

.requisition-title-row {
  display: flex;
  align-items: baseline;
  gap: 12px;
}

.requisition-title-row h1 {
  margin: 0;
  font-size: 22px;
  line-height: 30px;
  font-weight: 700;
  color: #0f172a;
}

.requisition-title-row span {
  font-size: 13px;
  color: #64748b;
}

.requisition-search-bar {
  min-height: 74px;
  display: grid;
  grid-template-columns:
    minmax(240px, 1.7fr) minmax(180px, 1fr) minmax(160px, 0.9fr)
    150px auto auto;
  gap: 12px;
  align-items: center;
  margin-bottom: 16px;
}

.requisition-main-column {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.requisition-table-panel {
  min-height: 754px;
}

.requisition-table-title {
  display: inline-flex;
  align-items: baseline;
  gap: 8px;
  margin-right: 4px;
}

.requisition-table-title strong {
  font-size: 15px;
  color: #0f172a;
}

.requisition-table-title span {
  font-size: 12px;
  color: #64748b;
}

.requisition-analysis-rail {
  width: 336px;
}

.requisition-analysis-panel {
  height: 856px;
  min-height: 856px;
  box-sizing: border-box;
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  box-shadow: 0 6px 18px rgba(15, 23, 42, 0.04);
  overflow: hidden;
}

.requisition-analysis-section {
  padding: 18px;
  border-bottom: 1px solid #edf1f5;
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
  color: #0f172a;
}

.requisition-section-head span {
  font-size: 12px;
  color: #64748b;
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
  color: #334155;
}

.requisition-bar-meta i {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex: 0 0 auto;
}

.requisition-bar-meta strong,
.requisition-recent-item strong {
  color: #0f172a;
  font-weight: 600;
  white-space: nowrap;
}

.requisition-bar-track {
  margin-top: 7px;
  height: 6px;
  border-radius: 999px;
  background: #f1f5f9;
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
  border-bottom: 1px solid #f1f5f9;
}

.requisition-recent-item:last-child {
  border-bottom: 0;
}

.requisition-recent-item span {
  min-width: 0;
  overflow: hidden;
  color: #334155;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.requisition-empty {
  padding: 18px 0;
  color: #94a3b8;
  text-align: center;
  font-size: 13px;
}

@media (max-width: 1280px) {
  .requisition-search-bar {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .requisition-analysis-rail {
    width: 100%;
  }
}
</style>
