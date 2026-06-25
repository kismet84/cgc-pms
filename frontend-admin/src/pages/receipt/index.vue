<script setup lang="ts">
import { onMounted, computed } from 'vue'
import { MoreOutlined, PlusOutlined, ReloadOutlined, SearchOutlined } from '@ant-design/icons-vue'
import { useReferenceStore } from '@/stores/reference'
import type { MatReceiptVO } from '@/types/receipt'
import type { SelectOption } from '@/types/ui'

import {
  useReceiptList,
  fmtAmount,
  QUALITY_STATUS_LABEL,
  QUALITY_STATUS_COLOR,
} from './composables/useReceiptList'
import { useReceiptForm } from './composables/useReceiptForm'
import ReceiptKpiStrip from './components/ReceiptKpiStrip.vue'
import ReceiptFormModal from './components/ReceiptFormModal.vue'
import { ColumnSettingsButton } from '@/components/list-page'
import { useColumnSettings } from '@/composables/useColumnSettings'

const referenceStore = useReferenceStore()
const projectList = computed(() => referenceStore.projects ?? [])
const contractList = computed(() => referenceStore.contracts ?? [])
const partnerList = computed(() => referenceStore.partners ?? [])

const {
  filter,
  loading,
  tableData,
  total,
  pageNo,
  pageSize,
  orderList,
  gridColumns,
  kpiTotalCount,
  kpiTotalAmount,
  kpiQualifiedCount,
  kpiUnqualifiedCount,
  fetchData,
  fetchWarehouses,
  handleSearch,
  handleReset,
  handlePageChange,
  handlePageSizeChange,
  handleDelete,
  handleSubmitApproval,
  init,
} = useReceiptList()

const {
  visibleColumns: visibleGridColumns,
  columnSettings,
  colVisible,
  toggleCol,
} = useColumnSettings('receipt_list_cols', gridColumns)

const {
  modalVisible,
  modalTitle,
  formData,
  itemList,
  itemsTotalAmount,
  hasWarning,
  handleAdd,
  handleEdit,
  handleOrderChange,
  handleItemQtyChange,
  handleItemPriceChange,
  handleItemQualifiedQtyChange,
  handleModalOk,
  handleModalCancel,
} = useReceiptForm(fetchData, orderList)

const receiptStatusSummary = computed(() => [
  {
    label: '合格',
    count: tableData.value.filter((item) => item.qualityStatus === 'QUALIFIED').length,
    color: '#52c41a',
  },
  {
    label: '部分合格',
    count: tableData.value.filter((item) => item.qualityStatus === 'PARTIAL').length,
    color: '#faad14',
  },
  {
    label: '不合格',
    count: tableData.value.filter((item) => item.qualityStatus === 'UNQUALIFIED').length,
    color: '#ff4d4f',
  },
])

const recentReceipts = computed(() => tableData.value.slice(0, 4))

onMounted(() => {
  referenceStore.fetchProjects()
  referenceStore.fetchContracts({ contractType: 'PURCHASE' })
  referenceStore.fetchPartners({ partnerType: 'SUPPLIER' })
  init()
})
</script>

<template>
  <div class="lg-list-page lg-page app-page">
    <div class="lg-page-head">
      <div>
        <a-breadcrumb class="lg-breadcrumb">
          <a-breadcrumb-item>采购管理</a-breadcrumb-item>
          <a-breadcrumb-item>材料验收</a-breadcrumb-item>
        </a-breadcrumb>
      </div>
    </div>

    <!-- 搜索栏 -->
    <div class="lg-search-bar">
      <a-input
        v-model:value="filter.receiptCode"
        placeholder="搜索验收单号…"
        allow-clear
        size="large"
        @press-enter="handleSearch"
      >
        <template #prefix><SearchOutlined style="color: var(--text-secondary)" /></template>
      </a-input>
      <a-button type="primary" size="large" @click="handleSearch">查询</a-button>
      <a-button size="large" @click="handleReset">
        <template #icon><ReloadOutlined /></template>
        重置
      </a-button>
    </div>

    <!-- KPI 横条 -->
    <ReceiptKpiStrip
      :total-count="kpiTotalCount"
      :total-amount="kpiTotalAmount"
      :qualified-count="kpiQualifiedCount"
      :unqualified-count="kpiUnqualifiedCount"
      :fmt-amount="fmtAmount"
    />

    <div class="lg-grid">
      <main class="lg-list-table-panel">
        <!-- 工具栏 -->
        <div class="lg-toolbar">
          <div class="lg-toolbar-left">
            <a-button type="primary" @click="handleAdd">
              <template #icon><PlusOutlined /></template>
              新建验收
            </a-button>
            <a-button @click="fetchData">
              <template #icon><ReloadOutlined /></template>
            </a-button>
          </div>
          <div class="lg-toolbar-right">
            <ColumnSettingsButton
              :columns="columnSettings"
              :visible="colVisible"
              @toggle="toggleCol"
            />
            <a-select
              v-model:value="filter.projectId"
              placeholder="全部项目"
              allow-clear
              style="width: 160px"
              size="small"
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
              v-model:value="filter.orderId"
              placeholder="全部订单"
              allow-clear
              style="width: 160px"
              size="small"
              show-search
              :filter-option="
                (input: string, option: SelectOption) =>
                  option.label?.toLowerCase().includes(input.toLowerCase())
              "
              @change="handleSearch"
            >
              <a-select-option v-for="o in orderList" :key="o.id" :value="o.id">
                {{ o.orderCode }}
              </a-select-option>
            </a-select>
            <a-select
              v-model:value="filter.qualityStatus"
              placeholder="全部质量状态"
              allow-clear
              style="width: 140px"
              size="small"
              @change="handleSearch"
            >
              <a-select-option value="QUALIFIED">合格</a-select-option>
              <a-select-option value="PARTIAL">部分合格</a-select-option>
              <a-select-option value="UNQUALIFIED">不合格</a-select-option>
              <a-select-option value="PENDING">待检验</a-select-option>
            </a-select>
          </div>
        </div>

        <!-- 表格 -->
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
            <template #totalAmount="{ row }">
              <span v-if="row.totalAmount" class="lg-money">
                {{ Number(row.totalAmount).toLocaleString('zh-CN', { minimumFractionDigits: 2 }) }}
              </span>
              <span v-else class="lg-none">-</span>
            </template>
            <template #qualityStatus="{ row }">
              <a-tag :color="QUALITY_STATUS_COLOR[row.qualityStatus] || 'default'">
                {{ (QUALITY_STATUS_LABEL[row.qualityStatus] ?? row.qualityStatus) || '-' }}
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
                      v-if="row.approvalStatus === 'DRAFT'"
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

      <aside class="lg-analysis-rail">
        <section class="lg-panel">
          <div class="lg-panel-title">质量状态分布</div>
          <div class="lg-type-list">
            <div v-for="item in receiptStatusSummary" :key="item.label" class="lg-type-row">
              <span class="lg-type-dot" :style="{ background: item.color }"></span>
              <span class="lg-type-label">{{ item.label }}</span>
              <span style="margin-left: auto">{{ item.count }} 单</span>
            </div>
          </div>
        </section>
        <section class="lg-panel">
          <div class="lg-panel-title">近期验收</div>
          <div class="lg-type-list">
            <div v-for="item in recentReceipts" :key="item.id" class="lg-type-row">
              <span class="lg-type-dot" style="background: #1890ff"></span>
              <span class="lg-type-label">{{ item.receiptCode }}</span>
            </div>
            <div v-if="!recentReceipts.length" class="lg-warning-empty">暂无验收单</div>
          </div>
        </section>
      </aside>
    </div>

    <!-- Add/Edit Modal -->
    <ReceiptFormModal
      :visible="modalVisible"
      :title="modalTitle"
      :form-data="formData"
      :project-list="projectList"
      :order-list="orderList"
      :contract-list="contractList"
      :partner-list="partnerList"
      :item-list="itemList"
      :has-warning="hasWarning"
      :items-total-amount="itemsTotalAmount"
      @ok="handleModalOk"
      @cancel="handleModalCancel"
      @order-change="handleOrderChange"
      @item-qty-change="handleItemQtyChange"
      @item-price-change="handleItemPriceChange"
      @item-qualified-qty-change="handleItemQualifiedQtyChange"
    />
  </div>
</template>

<style scoped>
/* 页面专属样式 — 其余已由 lg-* 全局类覆盖 */
.lg-breadcrumb {
  margin-bottom: 5px;
  font-size: 13px;
}
</style>
