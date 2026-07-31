<script setup lang="ts">
import { onMounted, computed, reactive, ref } from 'vue'
import { message } from 'ant-design-vue'
import { useRoute, useRouter } from 'vue-router'
import {
  MoreOutlined,
  FilterOutlined,
  PlusOutlined,
  ReloadOutlined,
  SearchOutlined,
} from '@ant-design/icons-vue'
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
import { ColumnSettingsButton, LgEmptyState } from '@/components/list-page'
import { useColumnSettings } from '@/composables/useColumnSettings'
import { getReceiptItems } from '@/api/modules/receipt'
import {
  confirmSupplierReturn,
  createSupplierReturn,
  getProcurementTrace,
  type ProcurementTrace,
} from '@/api/modules/procurement'
import { uploadFile } from '@/api/modules/file'
import type { MatReceiptItemVO, MatReceiptVO } from '@/types/receipt'
import ProcurementTraceDrawer from '@/components/ProcurementTraceDrawer.vue'

const route = useRoute()
const router = useRouter()
const referenceStore = useReferenceStore()
const filterPanelOpen = ref(false)
const projectList = computed(() => referenceStore.projects ?? [])
const contractList = computed(() => referenceStore.contracts ?? [])
const partnerList = computed(() => referenceStore.partners ?? [])

const {
  filter,
  loading,
  hasLoaded,
  listError,
  hasActiveFilters,
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
  handleSearch,
  handleReset,
  handlePageChange,
  handlePageSizeChange,
  handleDelete,
  handleSubmitApproval,
  init,
} = useReceiptList({ route, router })

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
  proofFile,
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

const showEmptyState = computed(() => hasLoaded.value && !loading.value && !tableData.value.length)

async function handleView(row: Parameters<typeof handleEdit>[0]) {
  await handleEdit(row)
  modalTitle.value = '查看材料验收'
}

const traceOpen = ref(false)
const traceLoading = ref(false)
const traceData = ref<ProcurementTrace>()

async function openTrace(row: MatReceiptVO) {
  traceOpen.value = true
  traceLoading.value = true
  traceData.value = undefined
  try {
    traceData.value = await getProcurementTrace('receipts', row.id)
  } catch (e: unknown) {
    console.error(e)
    message.error('加载采购闭环追溯失败')
  } finally {
    traceLoading.value = false
  }
}

const supplierReturnVisible = ref(false)
const supplierReturnLoading = ref(false)
const supplierReturnItems = ref<MatReceiptItemVO[]>([])
const supplierReturnFile = ref<File | null>(null)
const supplierReturnForm = reactive({
  receiptItemId: '',
  returnKind: 'UNQUALIFIED' as 'UNQUALIFIED' | 'ACCEPTED',
  quantity: '',
  returnDate: '',
  reason: '',
})

function handleSupplierReturnFile(event: Event) {
  const input = event.target as HTMLInputElement
  supplierReturnFile.value = input.files?.[0] ?? null
}

async function openSupplierReturn(row: MatReceiptVO) {
  try {
    const items = await getReceiptItems(row.id)
    supplierReturnItems.value = items.filter(
      (item) =>
        Number(item.qualifiedQuantity || 0) > 0 || Number(item.unqualifiedQuantity || 0) > 0,
    )
    if (!supplierReturnItems.value.length) {
      message.warning('该验收单没有可退供数量')
      return
    }
    const firstUnqualified = supplierReturnItems.value.find(
      (item) => Number(item.unqualifiedQuantity || 0) > 0,
    )
    Object.assign(supplierReturnForm, {
      receiptItemId: (firstUnqualified ?? supplierReturnItems.value[0]).id,
      returnKind: firstUnqualified ? 'UNQUALIFIED' : 'ACCEPTED',
      quantity: '',
      returnDate: new Date().toISOString().slice(0, 10),
      reason: '',
    })
    supplierReturnFile.value = null
    supplierReturnVisible.value = true
  } catch (e: unknown) {
    console.error(e)
    message.error('加载验收明细失败')
  }
}

async function submitSupplierReturn() {
  if (!supplierReturnForm.receiptItemId || Number(supplierReturnForm.quantity) <= 0) {
    message.warning('请选择验收明细并填写大于 0 的退供数量')
    return
  }
  if (!supplierReturnForm.returnDate || !supplierReturnForm.reason.trim()) {
    message.warning('请填写退供日期和原因')
    return
  }
  if (!supplierReturnFile.value) {
    message.warning('请上传退供单或供应商签收凭证')
    return
  }
  supplierReturnLoading.value = true
  try {
    const id = await createSupplierReturn({
      ...supplierReturnForm,
      idempotencyKey: `${Date.now()}-${Math.random().toString(16).slice(2)}`,
    })
    await uploadFile(supplierReturnFile.value, 'SUPPLIER_RETURN', id, 'RETURN_PROOF')
    await confirmSupplierReturn(id)
    message.success('退供已确认，库存、成本和预算已同步冲销')
    supplierReturnVisible.value = false
    await fetchData()
  } catch (e: unknown) {
    console.error(e)
    message.error('退供确认失败，请核对可退数量、处置方式和附件状态')
  } finally {
    supplierReturnLoading.value = false
  }
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
  referenceStore.fetchContracts(
    filter.projectId
      ? { projectId: filter.projectId, contractType: 'PURCHASE' }
      : { contractType: 'PURCHASE' },
  )
  referenceStore.fetchPartners({ partnerType: 'SUPPLIER' })
  init()
})
</script>

<template>
  <div class="lg-list-page lg-page app-page receipt-page procurement-subcontract-list-page">
    <div class="lg-page-head receipt-page-head">
      <div class="receipt-page-meta-row">
        <a-breadcrumb class="receipt-breadcrumb">
          <a-breadcrumb-item>采购管理</a-breadcrumb-item>
          <a-breadcrumb-item>材料验收</a-breadcrumb-item>
        </a-breadcrumb>
      </div>
    </div>

    <div class="lg-grid receipt-workspace">
      <div class="lg-left receipt-main-column">
        <ReceiptKpiStrip
          :total-count="kpiTotalCount"
          :total-amount="kpiTotalAmount"
          :qualified-count="kpiQualifiedCount"
          :unqualified-count="kpiUnqualifiedCount"
          :fmt-amount="fmtAmount"
        />

        <div class="lg-search-bar receipt-search-bar procurement-subcontract-query-panel">
          <div
            class="receipt-search-fields procurement-subcontract-filter-panel"
            :class="{ 'is-open': filterPanelOpen }"
          >
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
          </div>
          <div class="receipt-search-keyword-row procurement-subcontract-query-row">
            <a-input
              v-model:value="filter.receiptCode"
              placeholder="搜索验收单号"
              allow-clear
              size="large"
              @press-enter="handleSearch"
            >
              <template #prefix><SearchOutlined style="color: var(--text-secondary)" /></template>
            </a-input>
            <div class="receipt-search-actions procurement-subcontract-query-actions">
              <a-button
                class="procurement-subcontract-desktop-action"
                type="primary"
                size="large"
                @click="handleSearch"
                >搜索</a-button
              >
              <a-button
                class="procurement-subcontract-desktop-action"
                size="large"
                @click="handleReset"
              >
                <template #icon><ReloadOutlined /></template>
                重置
              </a-button>
              <a-button
                class="procurement-subcontract-filter-toggle"
                size="large"
                :aria-expanded="filterPanelOpen"
                @click="filterPanelOpen = !filterPanelOpen"
              >
                <template #icon><FilterOutlined /></template>
                筛选
              </a-button>
            </div>
          </div>
        </div>

        <main class="lg-list-table-panel receipt-table-panel procurement-subcontract-table-panel">
          <div class="lg-toolbar">
            <div class="lg-toolbar-left">
              <div class="receipt-table-title">
                <strong>材料验收明细</strong>
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
          </div>

          <div class="lg-table-wrap">
            <div v-if="listError" class="receipt-list-feedback">
              <a-result status="error" title="验收列表加载失败" :sub-title="listError">
                <template #extra>
                  <a-button type="primary" @click="fetchData">重试</a-button>
                </template>
              </a-result>
            </div>
            <div v-else-if="showEmptyState" class="receipt-list-feedback">
              <LgEmptyState description="暂无符合条件的材料验收记录">
                <a-button v-if="hasActiveFilters" @click="handleReset">清空筛选</a-button>
                <a-button v-else type="primary" @click="handleAdd">新建验收</a-button>
              </LgEmptyState>
            </div>
            <vxe-grid
              v-else
              class="procurement-subcontract-desktop-table"
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
                      <a-menu-item @click="openTrace(row)">全链追溯</a-menu-item>
                      <a-menu-item
                        v-if="row.approvalStatus === 'APPROVED'"
                        @click="openSupplierReturn(row)"
                      >
                        发起退供
                      </a-menu-item>
                    </a-menu>
                  </template>
                </a-dropdown>
              </template>
            </vxe-grid>
            <div v-if="!listError && !showEmptyState" class="procurement-subcontract-mobile-list">
              <button
                v-for="row in tableData"
                :key="row.id"
                class="procurement-subcontract-mobile-card"
                type="button"
                @click="handleView(row)"
              >
                <span class="procurement-subcontract-mobile-card-title">{{
                  row.receiptCode || '-'
                }}</span>
                <span class="procurement-subcontract-mobile-card-status">
                  <a-tag :color="QUALITY_STATUS_COLOR[row.qualityStatus || ''] || 'default'">{{
                    QUALITY_STATUS_LABEL[row.qualityStatus || ''] || row.qualityStatus || '-'
                  }}</a-tag>
                </span>
                <span class="procurement-subcontract-mobile-card-subtitle">{{
                  row.projectName || '未关联项目'
                }}</span>
                <span class="procurement-subcontract-mobile-card-meta"
                  >{{ row.orderCode || '订单待维护' }} · {{ row.receiptDate || '日期待维护' }}</span
                >
              </button>
            </div>
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

      <aside
        class="lg-analysis-rail receipt-analysis-rail procurement-subcontract-analysis-rail"
        aria-label="材料验收辅助分析"
      >
        <div class="lg-analysis-panel lg-fill-card receipt-analysis-panel">
          <header class="receipt-analysis-head lg-analysis-header">
            <div>
              <div class="receipt-analysis-title lg-analysis-heading">辅助分析</div>
              <div class="receipt-analysis-subtitle lg-analysis-description">
                质量状态、验收风险与近期单据
              </div>
            </div>
          </header>
          <section class="receipt-analysis-focus">
            <span>本页重点</span>
            <strong>{{ qualityRiskCount }} 单</strong>
            <em>不合格或待闭环批次，优先核对订单关联与整改状态。</em>
          </section>
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
            <div class="lg-analysis-overview-list">
              <div class="lg-analysis-overview-row">
                <span>不合格批次</span>
                <strong>{{ qualityRiskCount }} 单</strong>
              </div>
              <div class="lg-analysis-note">需跟踪退换货、让步接收或整改闭环。</div>
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
      :proof-file-name="proofFile?.name"
      @ok="handleModalOk"
      @cancel="handleModalCancel"
      @order-change="handleOrderChange"
      @item-qty-change="handleItemQtyChange"
      @item-price-change="handleItemPriceChange"
      @item-qualified-qty-change="handleItemQualifiedQtyChange"
      @proof-file-change="proofFile = $event"
    />
    <ProcurementTraceDrawer
      :open="traceOpen"
      :loading="traceLoading"
      :trace="traceData"
      @close="traceOpen = false"
    />
    <a-modal
      :open="supplierReturnVisible"
      title="验收退供确认"
      :confirm-loading="supplierReturnLoading"
      @ok="submitSupplierReturn"
      @cancel="supplierReturnVisible = false"
    >
      <a-form :label-col="{ span: 6 }" :wrapper-col="{ span: 17 }">
        <a-form-item label="验收明细" required>
          <a-select v-model:value="supplierReturnForm.receiptItemId">
            <a-select-option v-for="item in supplierReturnItems" :key="item.id" :value="item.id">
              {{ item.materialName }}（合格 {{ item.qualifiedQuantity || 0 }} / 不合格
              {{ item.unqualifiedQuantity || 0 }}）
            </a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="退供类型" required>
          <a-radio-group v-model:value="supplierReturnForm.returnKind">
            <a-radio value="UNQUALIFIED">不合格退供</a-radio>
            <a-radio value="ACCEPTED">已接收材料退供</a-radio>
          </a-radio-group>
        </a-form-item>
        <a-form-item label="退供数量" required>
          <a-input-number
            v-model:value="supplierReturnForm.quantity"
            :min="0"
            :precision="2"
            style="width: 100%"
          />
        </a-form-item>
        <a-form-item label="退供日期" required>
          <a-date-picker
            v-model:value="supplierReturnForm.returnDate"
            value-format="YYYY-MM-DD"
            style="width: 100%"
          />
        </a-form-item>
        <a-form-item label="退供原因" required>
          <a-textarea v-model:value="supplierReturnForm.reason" :rows="3" />
        </a-form-item>
        <a-form-item label="退供凭证" required>
          <input type="file" @change="handleSupplierReturnFile" />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<style scoped>
.receipt-page {
  color: #0f172a;
  background: var(--surface-subtle);
}

.receipt-page-head {
  align-items: center;
  justify-content: space-between;
  min-height: 0;
  padding: 0;
  background: transparent;
  border: 0;
  border-radius: 0;
  box-shadow: none;
}

.receipt-workspace {
}

.receipt-page .lg-left {
  flex: 1;
}

.receipt-main-column {
  min-width: 0;
  display: flex;
  flex: 1;
  flex-direction: column;
  min-height: 0;
}

.receipt-page-meta-row {
  display: flex;
  align-items: center;
  gap: 5em;
  width: 100%;
  min-width: 0;
}

.receipt-breadcrumb {
  margin-bottom: 0;
  font-size: 13px;
  line-height: 20px;
}

.receipt-search-bar {
  display: flex;
  flex: 0 0 auto;
  flex-direction: column;
  gap: 12px;
  align-items: stretch;
  margin: 0;
}

.receipt-search-fields,
.receipt-search-keyword-row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
  min-width: 0;
  width: 100%;
}

.receipt-search-actions {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  margin-left: auto;
  min-width: 0;
}

.receipt-search-fields > :deep(.ant-select) {
  flex: 1 1 180px;
  min-width: 180px;
}

.receipt-search-keyword-row > :deep(.ant-input-affix-wrapper) {
  flex: 1 1 320px;
  min-width: 320px;
}

.receipt-table-panel {
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

.receipt-table-panel > .lg-toolbar {
  min-height: 58px;
  border-bottom: 1px solid var(--border-subtle);
}

.receipt-table-panel > .lg-table-wrap {
  flex: 1;
  min-height: 0;
}

.receipt-list-feedback {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 420px;
  padding: 24px;
}

.receipt-table-panel > .lg-pagination {
  border-top: 1px solid var(--border-subtle);
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

.receipt-analysis-rail {
  display: flex;
  min-height: 0;
}

.receipt-analysis-panel {
  display: flex;
  flex: 1;
  flex-direction: column;
  height: auto;
  box-sizing: border-box;
  overflow: auto;
  position: sticky;
  top: 0;
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

.receipt-analysis-focus {
  display: grid;
  gap: 4px;
  padding: 14px 18px;
  background: var(--error-soft);
  border-bottom: 1px solid rgba(239, 68, 68, 0.18);
}

.receipt-analysis-focus span,
.receipt-analysis-focus em {
  color: var(--text-secondary);
  font-size: 12px;
  font-style: normal;
}

.receipt-analysis-focus strong {
  color: var(--error);
  font-size: 24px;
  font-weight: 800;
  line-height: 30px;
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
  .receipt-workspace {
    height: auto;
  }

  .receipt-search-fields,
  .receipt-search-keyword-row,
  .receipt-search-actions {
    flex-direction: column;
    align-items: stretch;
  }

  .receipt-search-actions {
    width: 100%;
    margin-left: 0;
  }

  .receipt-analysis-rail {
    width: 100%;
  }

  .receipt-analysis-panel {
    position: static;
  }
}
</style>
