<script setup lang="ts">
import { computed, ref, onMounted } from 'vue'
import { MoreOutlined, SearchOutlined, PlusOutlined, ReloadOutlined } from '@ant-design/icons-vue'
import { useRoute, useRouter } from 'vue-router'
import type { InvoiceVO, InvoiceRecognizeResultVO } from '@/types/invoice'
import {
  INVOICE_TYPE_LABEL,
  INVOICE_TYPE_COLOR,
  VERIFY_STATUS_LABEL,
  VERIFY_STATUS_COLOR,
} from '@/types/invoice'
import { useInvoiceList, fmtAmount } from './composables/useInvoiceList'
import InvoiceFormModal from './components/InvoiceFormModal.vue'
import InvoiceKpiStrip from './components/InvoiceKpiStrip.vue'
import InvoiceVerifyPanel from './components/InvoiceVerifyPanel.vue'
import { ColumnSettingsButton, LgEmptyState } from '@/components/list-page'
import { useColumnSettings } from '@/composables/useColumnSettings'

const route = useRoute()
const router = useRouter()

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
  payRecordList,
  gridColumns,
  fetchData,
  handleSearch,
  handleReset,
  handlePageChange,
  handlePageSizeChange,
  handleDelete,
  handleVerify,
  init,
} = useInvoiceList({ route, router })

const {
  visibleColumns: visibleGridColumns,
  columnSettings,
  colVisible,
  toggleCol,
} = useColumnSettings('invoice_list_cols', gridColumns)

const modalVisible = ref(false)
const modalMode = ref<'create' | 'edit'>('create')
const editingRecord = ref<InvoiceVO | null>(null)
const modalRef = ref<InstanceType<typeof InvoiceFormModal> | null>(null)
const showEmptyState = computed(() => hasLoaded.value && !loading.value && !tableData.value.length)

function handleAdd() {
  modalMode.value = 'create'
  editingRecord.value = null
  modalVisible.value = true
  // Clear modal child state for tests that access via defineExpose proxy
  if (modalRef.value) {
    modalRef.value.uploadFileList = []
    modalRef.value.recognizeResult = null
  }
}

function handleEdit(record: InvoiceVO) {
  modalMode.value = 'edit'
  editingRecord.value = record
  modalVisible.value = true
}

function onModalClose() {
  modalVisible.value = false
}

function onModalSaved() {
  fetchData()
}

onMounted(() => {
  init()
})

defineExpose({
  get formData() {
    return modalRef.value?.formData
  },
  get uploadFileList() {
    return modalRef.value?.uploadFileList
  },
  set uploadFileList(val) {
    if (modalRef.value) modalRef.value.uploadFileList = val
  },
  get recognizeResult() {
    return modalRef.value?.recognizeResult
  },
  set recognizeResult(val) {
    if (modalRef.value) modalRef.value.recognizeResult = val
  },
  handleAdd() {
    handleAdd()
  },
  handleBeforeUpload(file: File) {
    return modalRef.value?.handleBeforeUpload(file)
  },
  applyRecognitionResult(result: InvoiceRecognizeResultVO) {
    modalRef.value?.applyRecognitionResult(result)
  },
})
</script>

<template>
  <div class="lg-list-page lg-page app-page invoice-page">
    <!-- 页面头部 -->
    <div class="lg-page-head invoice-page-head">
      <div class="invoice-page-meta-row">
        <a-breadcrumb class="invoice-breadcrumb">
          <a-breadcrumb-item>发票管理</a-breadcrumb-item>
          <a-breadcrumb-item>发票列表</a-breadcrumb-item>
        </a-breadcrumb>
        <span class="invoice-page-subtitle">按付款记录核验发票金额、认证状态与异常风险。</span>
      </div>
    </div>

    <!-- 搜索栏 -->
    <div class="lg-search-bar invoice-search-bar">
      <div class="invoice-search-fields">
        <a-input
          v-model:value="filter.keyword"
          class="invoice-search-input"
          placeholder="搜索发票号码"
          allow-clear
          size="large"
          @press-enter="handleSearch"
        >
          <template #prefix><SearchOutlined class="invoice-search-prefix-icon" /></template>
        </a-input>
        <a-select
          v-model:value="filter.payRecordId"
          class="invoice-search-select"
          placeholder="全部付款记录"
          allow-clear
          size="large"
          @change="handleSearch"
        >
          <a-select-option v-for="pr in payRecordList" :key="pr.id" :value="pr.id">
            {{ pr.voucherNo ? `#${pr.voucherNo}` : `付款记录#${pr.id}` }}
          </a-select-option>
        </a-select>
        <a-select
          v-model:value="filter.verifyStatus"
          class="invoice-search-select is-compact"
          placeholder="核验状态"
          allow-clear
          size="large"
          @change="handleSearch"
        >
          <a-select-option value="PENDING">待核验</a-select-option>
          <a-select-option value="VERIFIED">已认证</a-select-option>
          <a-select-option value="ABNORMAL">异常</a-select-option>
        </a-select>
      </div>
      <div class="invoice-search-actions">
        <a-button type="primary" size="large" @click="handleSearch">查询</a-button>
        <a-button size="large" @click="handleReset">
          <template #icon><ReloadOutlined /></template>
          重置
        </a-button>
      </div>
    </div>

    <div class="lg-grid invoice-workspace">
      <div class="lg-left">
        <!-- KPI 横条 -->
        <InvoiceKpiStrip :data="tableData" />

        <main class="lg-list-table-panel invoice-table-panel">
          <!-- 工具栏 -->
          <div class="lg-toolbar invoice-toolbar">
            <div class="lg-toolbar-left">
              <span class="invoice-table-title">发票记录</span>
              <span class="invoice-table-count">共 {{ total }} 条</span>
              <ColumnSettingsButton
                :columns="columnSettings"
                :visible="colVisible"
                @toggle="toggleCol"
              />
              <a-button type="primary" @click="handleAdd">
                <template #icon><PlusOutlined /></template>
                新增发票
              </a-button>
              <a-button @click="fetchData">
                <template #icon><ReloadOutlined /></template>
                刷新
              </a-button>
            </div>
            <div class="lg-toolbar-right">
              <span class="invoice-toolbar-hint">固定表头 / 核验状态 / 行操作展开</span>
            </div>
          </div>

          <!-- 表格 -->
          <div class="lg-table-wrap">
            <div v-if="listError" class="invoice-list-feedback">
              <a-result status="error" title="发票列表加载失败" :sub-title="listError">
                <template #extra>
                  <a-button type="primary" @click="fetchData">重试</a-button>
                </template>
              </a-result>
            </div>
            <div v-else-if="showEmptyState" class="invoice-list-feedback">
              <LgEmptyState description="暂无符合条件的发票记录">
                <a-button v-if="hasActiveFilters" @click="handleReset">清空筛选</a-button>
                <a-button v-else type="primary" @click="handleAdd">新增发票</a-button>
              </LgEmptyState>
            </div>
            <vxe-grid
              v-else
              :data="tableData"
              :columns="visibleGridColumns"
              :loading="loading"
              :column-config="{ resizable: true }"
              stripe
              border="inner"
              size="small"
            >
              <template #invoiceType="{ row }">
                <a-tag
                  :color="
                    INVOICE_TYPE_COLOR[row.invoiceType as keyof typeof INVOICE_TYPE_COLOR] ||
                    'default'
                  "
                >
                  {{
                    INVOICE_TYPE_LABEL[row.invoiceType as keyof typeof INVOICE_TYPE_LABEL] ||
                    row.invoiceType
                  }}
                </a-tag>
              </template>
              <template #invoiceAmount="{ row }">
                <span class="lg-money">{{ fmtAmount(row.invoiceAmount) }}</span>
              </template>
              <template #taxRate="{ row }">
                <span>{{ row.taxRate ? row.taxRate + '%' : '-' }}</span>
              </template>
              <template #taxAmount="{ row }">
                <span class="lg-money">{{ fmtAmount(row.taxAmount) }}</span>
              </template>
              <template #verifyStatus="{ row }">
                <a-tag
                  :color="
                    VERIFY_STATUS_COLOR[row.verifyStatus as keyof typeof VERIFY_STATUS_COLOR] ||
                    'default'
                  "
                >
                  {{
                    VERIFY_STATUS_LABEL[row.verifyStatus as keyof typeof VERIFY_STATUS_LABEL] ||
                    row.verifyStatus
                  }}
                </a-tag>
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
                      <a-menu-item v-if="row.verifyStatus === 'PENDING'" @click="handleVerify(row)">
                        核验
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

      <!-- 右侧分析面板 -->
      <InvoiceVerifyPanel :data="tableData" />
    </div>

    <!-- Add/Edit Modal -->
    <InvoiceFormModal
      ref="modalRef"
      :visible="modalVisible"
      :mode="modalMode"
      :edit-record="editingRecord"
      :pay-record-list="payRecordList"
      @close="onModalClose"
      @saved="onModalSaved"
    />
  </div>
</template>

<style scoped>
.invoice-page {
  gap: 14px;
}

.invoice-page-head {
  align-items: center;
  justify-content: space-between;
  min-height: 0;
  padding: 0;
}

.invoice-page-meta-row {
  display: flex;
  align-items: center;
  gap: 5em;
  min-width: 0;
}

.invoice-breadcrumb {
  font-size: 13px;
  line-height: 20px;
}

.invoice-page-subtitle {
  color: var(--text-secondary);
  font-size: 13px;
  line-height: 20px;
  white-space: nowrap;
}

.invoice-search-bar {
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  min-height: 74px;
}

.invoice-search-fields {
  display: flex;
  flex: 1 1 auto;
  gap: 12px;
  align-items: center;
  min-width: 0;
}

.invoice-search-input {
  width: min(520px, 31vw);
  min-width: 320px;
  flex: 1 1 auto;
}

.invoice-search-prefix-icon {
  color: var(--text-secondary);
}

.invoice-search-select {
  width: 220px;
  flex: 0 0 220px;
}

.invoice-search-select.is-compact {
  width: 150px;
  flex-basis: 150px;
}

.invoice-search-actions {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  gap: 8px;
}

.invoice-workspace {
  align-items: stretch;
  min-height: 0;
}

.invoice-table-panel {
  min-height: 754px;
}

.invoice-toolbar {
  align-items: center;
}

.invoice-list-feedback {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 420px;
  padding: 24px;
}

.invoice-table-title {
  color: var(--text);
  font-size: 15px;
  font-weight: 800;
}

.invoice-table-count,
.invoice-toolbar-hint {
  color: var(--text-secondary);
  font-size: 12px;
}

@media (max-width: 768px) {
  .invoice-page-meta-row,
  .invoice-search-bar,
  .invoice-search-fields {
    align-items: stretch;
    flex-direction: column;
  }

  .invoice-page-subtitle {
    white-space: normal;
  }

  .invoice-search-input,
  .invoice-search-select,
  .invoice-search-select.is-compact {
    width: 100%;
    min-width: 0;
    flex-basis: auto;
  }
}
</style>
