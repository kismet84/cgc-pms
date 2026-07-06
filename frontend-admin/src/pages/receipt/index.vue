<script setup lang="ts">
import { onMounted, computed } from 'vue'
import { MoreOutlined, PlusOutlined, ReloadOutlined, SearchOutlined } from '@ant-design/icons-vue'
import { useReferenceStore } from '@/stores/reference'
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
} = useColumnSettings('receipt_list_cols_v2', gridColumns, {
  receiptDate: false,
  approvalStatus: false,
})

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

async function handleView(row: Parameters<typeof handleEdit>[0]) {
  await handleEdit(row)
  modalTitle.value = '查看材料验收'
}

const receiptStatusSummary = computed(() => [
  {
    label: '合格',
    count: tableData.value.filter((item) => item.qualityStatus === 'QUALIFIED').length,
    color: '#52c41a',
    pct: statusPct('QUALIFIED'),
  },
  {
    label: '部分合格',
    count: tableData.value.filter((item) => item.qualityStatus === 'PARTIAL').length,
    color: '#faad14',
    pct: statusPct('PARTIAL'),
  },
  {
    label: '不合格',
    count: tableData.value.filter((item) => item.qualityStatus === 'UNQUALIFIED').length,
    color: '#ff4d4f',
    pct: statusPct('UNQUALIFIED'),
  },
  {
    label: '待检验',
    count: tableData.value.filter((item) => item.qualityStatus === 'PENDING').length,
    color: '#1677ff',
    pct: statusPct('PENDING'),
  },
])

const qualityRiskCount = computed(() => kpiUnqualifiedCount.value)
const recentReceipts = computed(() => tableData.value.slice(0, 4))

function statusPct(status: string) {
  const base = tableData.value.length || 0
  if (!base) return 0
  const count = tableData.value.filter((item) => item.qualityStatus === status).length
  return Math.round((count / base) * 100)
}

onMounted(() => {
  referenceStore.fetchProjects()
  referenceStore.fetchContracts({ contractType: 'PURCHASE' })
  referenceStore.fetchPartners({ partnerType: 'SUPPLIER' })
  init()
})
</script>

<template>
  <div class="lg-list-page lg-page app-page receipt-page">
    <div class="lg-page-head receipt-page-head">
      <div class="receipt-title-block">
        <a-breadcrumb class="lg-breadcrumb">
          <a-breadcrumb-item>采购管理</a-breadcrumb-item>
          <a-breadcrumb-item>材料验收</a-breadcrumb-item>
        </a-breadcrumb>
        <div class="receipt-title-row">
          <h1>材料验收</h1>
          <span>核对采购到货、质量状态、验收金额与审批进度。</span>
        </div>
      </div>
    </div>

    <div class="lg-search-bar receipt-search-bar">
      <a-input
        v-model:value="filter.receiptCode"
        placeholder="搜索验收单号…"
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
        v-model:value="filter.orderId"
        placeholder="全部订单"
        allow-clear
        size="large"
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
        size="large"
        @change="handleSearch"
      >
        <a-select-option value="QUALIFIED">合格</a-select-option>
        <a-select-option value="PARTIAL">部分合格</a-select-option>
        <a-select-option value="UNQUALIFIED">不合格</a-select-option>
        <a-select-option value="PENDING">待检验</a-select-option>
      </a-select>
      <a-button type="primary" size="large" @click="handleSearch">搜索</a-button>
      <a-button size="large" @click="handleReset">
        <template #icon><ReloadOutlined /></template>
        重置
      </a-button>
    </div>

    <div class="lg-grid">
      <div class="lg-left receipt-main-column">
        <ReceiptKpiStrip
          :total-count="kpiTotalCount"
          :total-amount="kpiTotalAmount"
          :qualified-count="kpiQualifiedCount"
          :unqualified-count="kpiUnqualifiedCount"
          :fmt-amount="fmtAmount"
        />

        <main class="lg-list-table-panel receipt-table-panel">
          <div class="lg-toolbar">
            <div class="lg-toolbar-left">
              <div class="receipt-table-title">
                <strong>材料验收</strong>
                <span>共 {{ total }} 条</span>
              </div>
              <ColumnSettingsButton
                :columns="columnSettings"
                :visible="colVisible"
                @toggle="toggleCol"
              />
              <a-button type="primary" @click="handleAdd">
                <template #icon><PlusOutlined /></template>
                新建验收
              </a-button>
              <a-button @click="fetchData">
                <template #icon><ReloadOutlined /></template>
                刷新
              </a-button>
            </div>
            <div class="lg-toolbar-right">
              <span class="receipt-toolbar-hint">验收单号进入单据，行末查看更多操作</span>
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
              <template #receiptCode="{ row }">
                <a-button class="receipt-code-link" type="link" @click="handleView(row)">
                  {{ row.receiptCode || '-' }}
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
      </div>

      <aside class="lg-analysis-rail receipt-analysis-rail" aria-label="材料验收辅助分析">
        <div class="receipt-analysis-panel">
          <header class="receipt-analysis-head">
            <div>
              <div class="receipt-analysis-title">辅助分析</div>
              <div class="receipt-analysis-subtitle">质量状态、验收风险与近期单据</div>
            </div>
            <a-button type="link" size="small" @click="fetchData">刷新</a-button>
          </header>
          <section class="receipt-analysis-section">
            <div class="receipt-section-head">
              <strong>质量状态分布</strong>
              <span>{{ tableData.length }} 单</span>
            </div>
            <div class="receipt-bar-list">
              <div v-for="item in receiptStatusSummary" :key="item.label" class="receipt-bar-row">
                <div class="receipt-bar-meta">
                  <span><i :style="{ background: item.color }"></i>{{ item.label }}</span>
                  <strong>{{ item.count }} 单</strong>
                </div>
                <div class="receipt-bar-track">
                  <span :style="{ width: item.pct + '%', background: item.color }"></span>
                </div>
              </div>
            </div>
          </section>
          <section class="receipt-analysis-section">
            <div class="receipt-section-head">
              <strong>验收风险</strong>
              <span>{{ qualityRiskCount }} 单</span>
            </div>
            <div class="receipt-risk-box">
              <span>不合格批次</span>
              <strong>{{ qualityRiskCount }}</strong>
              <em>需跟踪退换货、让步接收或整改闭环。</em>
            </div>
          </section>
          <section class="receipt-analysis-section">
            <div class="receipt-section-head">
              <strong>近期验收</strong>
              <span>最新 4 单</span>
            </div>
            <div class="receipt-recent-list">
              <div v-for="item in recentReceipts" :key="item.id" class="receipt-recent-item">
                <span>{{ item.receiptCode }}</span>
                <strong>{{
                  QUALITY_STATUS_LABEL[item.qualityStatus] ?? item.qualityStatus ?? '-'
                }}</strong>
              </div>
              <div v-if="!recentReceipts.length" class="receipt-empty">暂无验收单</div>
            </div>
          </section>
        </div>
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
.receipt-page {
  color: #0f172a;
}

.receipt-page-head {
  margin-bottom: 7px;
}

.lg-breadcrumb {
  margin-bottom: 5px;
  font-size: 13px;
}

.receipt-title-row {
  display: flex;
  align-items: baseline;
  gap: 12px;
}

.receipt-title-row h1 {
  margin: 0;
  font-size: 22px;
  line-height: 30px;
  font-weight: 700;
  color: #0f172a;
}

.receipt-title-row span {
  font-size: 13px;
  color: #64748b;
}

.receipt-search-bar {
  min-height: 74px;
  display: grid;
  grid-template-columns: minmax(240px, 1.7fr) minmax(180px, 1fr) minmax(170px, 1fr) 150px auto auto;
  gap: 12px;
  align-items: center;
  margin-bottom: 16px;
}

.receipt-main-column {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.receipt-table-panel {
  min-height: 754px;
}

.receipt-table-title {
  display: inline-flex;
  align-items: baseline;
  gap: 8px;
  margin-right: 4px;
}

.receipt-table-title strong {
  font-size: 15px;
  color: #0f172a;
}

.receipt-table-title span {
  font-size: 12px;
  color: #64748b;
}

.receipt-toolbar-hint {
  color: #64748b;
  font-size: 12px;
}

.receipt-analysis-rail {
  width: 336px;
}

.receipt-analysis-panel {
  height: 856px;
  min-height: 856px;
  box-sizing: border-box;
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  box-shadow: 0 6px 18px rgba(15, 23, 42, 0.04);
  overflow: hidden;
}

.receipt-analysis-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  padding: 18px 18px 14px;
  border-bottom: 1px solid #edf1f5;
}

.receipt-analysis-title {
  color: #0f172a;
  font-size: 16px;
  font-weight: 700;
  line-height: 22px;
}

.receipt-analysis-subtitle {
  margin-top: 2px;
  color: #64748b;
  font-size: 12px;
  line-height: 18px;
}

.receipt-analysis-section {
  padding: 18px;
  border-bottom: 1px solid #edf1f5;
}

.receipt-analysis-section:last-child {
  border-bottom: 0;
}

.receipt-section-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  margin-bottom: 14px;
}

.receipt-section-head strong {
  font-size: 15px;
  color: #0f172a;
}

.receipt-section-head span {
  font-size: 12px;
  color: #64748b;
}

.receipt-bar-list,
.receipt-recent-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.receipt-bar-meta,
.receipt-recent-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 10px;
  font-size: 13px;
}

.receipt-bar-meta span {
  display: inline-flex;
  align-items: center;
  min-width: 0;
  gap: 8px;
  color: #334155;
}

.receipt-bar-meta i {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex: 0 0 auto;
}

.receipt-bar-meta strong,
.receipt-recent-item strong {
  color: #0f172a;
  font-weight: 600;
  white-space: nowrap;
}

.receipt-bar-track {
  margin-top: 7px;
  height: 6px;
  border-radius: 999px;
  background: #f1f5f9;
  overflow: hidden;
}

.receipt-bar-track span {
  display: block;
  height: 100%;
  border-radius: inherit;
}

.receipt-risk-box {
  display: grid;
  gap: 4px;
  padding: 14px;
  border-radius: 8px;
  background: #f8fafc;
  border: 1px solid #e5e7eb;
}

.receipt-risk-box span,
.receipt-risk-box em {
  font-size: 12px;
  color: #64748b;
  font-style: normal;
}

.receipt-risk-box strong {
  font-size: 24px;
  line-height: 30px;
  color: #dc2626;
}

.receipt-recent-item {
  padding: 10px 0;
  border-bottom: 1px solid #f1f5f9;
}

.receipt-recent-item:last-child {
  border-bottom: 0;
}

.receipt-recent-item span {
  min-width: 0;
  overflow: hidden;
  color: #334155;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.receipt-empty {
  padding: 18px 0;
  color: #94a3b8;
  text-align: center;
  font-size: 13px;
}

@media (max-width: 1280px) {
  .receipt-search-bar {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .receipt-analysis-rail {
    width: 100%;
  }
}
</style>
