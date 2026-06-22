<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { SearchOutlined, PlusOutlined, ReloadOutlined } from '@ant-design/icons-vue'
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

const {
  filter,
  loading,
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
} = useInvoiceList()

const modalVisible = ref(false)
const modalMode = ref<'create' | 'edit'>('create')
const editingRecord = ref<InvoiceVO | null>(null)
const modalRef = ref<InstanceType<typeof InvoiceFormModal> | null>(null)

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
  <div class="lg-page app-page">
    <!-- 页面头部 -->
    <div class="lg-page-head">
      <div>
        <a-breadcrumb style="margin-bottom: 5px; font-size: 13px">
          <a-breadcrumb-item>发票管理</a-breadcrumb-item>
          <a-breadcrumb-item>发票列表</a-breadcrumb-item>
        </a-breadcrumb>
      </div>
    </div>

    <!-- 搜索栏 -->
    <div class="lg-search-bar">
      <a-input
        v-model:value="filter.keyword"
        placeholder="搜索发票号码…"
        allow-clear
        size="large"
        @press-enter="handleSearch"
      >
        <template #prefix><SearchOutlined style="color: #697380" /></template>
      </a-input>
      <a-button type="primary" size="large" @click="handleSearch">查询</a-button>
      <a-button size="large" @click="handleReset">
        <template #icon><ReloadOutlined /></template>
        重置
      </a-button>
    </div>

    <div class="lg-grid">
      <div class="lg-left">
        <!-- KPI 横条 -->
        <InvoiceKpiStrip :data="tableData" />

        <!-- 工具栏 -->
        <div class="lg-toolbar">
          <div class="lg-toolbar-left">
            <a-button type="primary" @click="handleAdd">
              <template #icon><PlusOutlined /></template>
              新增发票
            </a-button>
            <a-button @click="fetchData">
              <template #icon><ReloadOutlined /></template>
            </a-button>
          </div>
          <div class="lg-toolbar-right">
            <a-select
              v-model:value="filter.payRecordId"
              placeholder="全部付款记录"
              allow-clear
              style="width: 180px"
              size="small"
              @change="handleSearch"
            >
              <a-select-option v-for="pr in payRecordList" :key="pr.id" :value="pr.id">
                {{ pr.voucherNo ? `#${pr.voucherNo}` : `付款记录#${pr.id}` }}
              </a-select-option>
            </a-select>
            <a-select
              v-model:value="filter.verifyStatus"
              placeholder="全部核验状态"
              allow-clear
              style="width: 130px"
              size="small"
              @change="handleSearch"
            >
              <a-select-option value="PENDING">待核验</a-select-option>
              <a-select-option value="VERIFIED">已认证</a-select-option>
              <a-select-option value="ABNORMAL">异常</a-select-option>
            </a-select>
          </div>
        </div>

        <!-- 表格 -->
        <div class="lg-table-wrap">
          <vxe-grid
            :data="tableData"
            :columns="gridColumns"
            :loading="loading"
            :column-config="{ resizable: true }"
            stripe
            border="inner"
            size="small"
            max-height="480"
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
              <div class="lg-ops">
                <a class="lg-link" @click="handleEdit(row)">编辑</a>
                <a class="lg-link lg-del" @click="handleDelete(row)">删除</a>
                <a v-if="row.verifyStatus === 'PENDING'" class="lg-link" @click="handleVerify(row)"
                  >核验</a
                >
              </div>
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

<style scoped></style>
