<script setup lang="ts">
import { ref, reactive, onMounted, computed } from 'vue'
import { message, Modal } from 'ant-design-vue'
import {
  getReceiptList,
  createReceipt,
  updateReceipt,
  deleteReceipt,
  getReceiptItems,
  saveReceiptItems,
  getOrderItemsForReceipt,
  submitReceiptForApproval,
} from '@/api/modules/receipt'
import { getOrderList } from '@/api/modules/purchase'
import { useReferenceStore } from '@/stores/reference'
import type { MatReceiptVO, MatReceiptItemVO } from '@/types/receipt'
import type { MatPurchaseOrderVO } from '@/types/purchase'

const filter = reactive({
  projectId: undefined as string | undefined,
  orderId: undefined as string | undefined,
  contractId: undefined as string | undefined,
  partnerId: undefined as string | undefined,
  receiptCode: '',
  qualityStatus: undefined as string | undefined,
})

const loading = ref(false)
const tableData = ref<MatReceiptVO[]>([])
const total = ref(0)
const pageNo = ref(1)
const pageSize = ref(20)

const referenceStore = useReferenceStore()
const projectList = computed(() => referenceStore.projects ?? [])
const contractList = computed(() => referenceStore.contracts ?? [])
const partnerList = computed(() => referenceStore.partners ?? [])
const orderList = ref<MatPurchaseOrderVO[]>([])

const modalVisible = ref(false)
const modalTitle = ref('新建材料验收')
const editingId = ref<string | null>(null)
const formData = reactive<Partial<MatReceiptVO>>({
  projectId: undefined,
  orderId: undefined,
  contractId: undefined,
  partnerId: undefined,
  receiptDate: undefined,
  qualityStatus: undefined,
  warehouseId: undefined,
  receiverId: undefined,
  remark: '',
})

// Line items for the modal
const itemList = ref<(Partial<MatReceiptItemVO> & { key: number; warning?: boolean })[]>([])
let itemKeyCounter = 0

const QUALITY_STATUS_LABEL: Record<string, string> = {
  QUALIFIED: '合格',
  PARTIAL: '部分合格',
  UNQUALIFIED: '不合格',
  PENDING: '待检验',
}
const QUALITY_STATUS_COLOR: Record<string, string> = {
  QUALIFIED: 'success',
  PARTIAL: 'warning',
  UNQUALIFIED: 'error',
  PENDING: 'processing',
}

const columns = [
  { title: '验收单号', dataIndex: 'receiptCode', width: 140, ellipsis: true },
  { title: '采购订单', dataIndex: 'orderCode', width: 130, ellipsis: true },
  { title: '项目', dataIndex: 'projectName', width: 120, ellipsis: true },
  { title: '供应商', dataIndex: 'partnerName', width: 120, ellipsis: true },
  { title: '验收日期', dataIndex: 'receiptDate', width: 100 },
  {
    title: '总金额',
    dataIndex: 'totalAmount',
    width: 110,
    key: 'totalAmount',
    align: 'right' as const,
  },
  { title: '质量状态', dataIndex: 'qualityStatus', width: 90, key: 'qualityStatus' },
  { title: '审批状态', dataIndex: 'approvalStatus', width: 90, key: 'approvalStatus' },
  { title: '操作', key: 'action', width: 140 },
]

async function fetchData() {
  loading.value = true
  try {
    const res = await getReceiptList({
      pageNum: pageNo.value,
      pageSize: pageSize.value,
      projectId: filter.projectId,
      orderId: filter.orderId,
      contractId: filter.contractId,
      partnerId: filter.partnerId,
      receiptCode: filter.receiptCode || undefined,
      qualityStatus: filter.qualityStatus,
    })
    tableData.value = res.records
    total.value = res.total
  } catch (e: unknown) {
    console.error(e)
    tableData.value = []
    total.value = 0
    message.error('加载验收列表失败，请稍后重试')
  } finally {
    loading.value = false
  }
}

async function fetchOrders() {
  try {
    const res = await getOrderList({ pageNum: 1, pageSize: 50 })
    orderList.value = res.records
  } catch (e: unknown) {
    console.error(e)
    orderList.value = []
  }
}

function handleSearch() {
  pageNo.value = 1
  fetchData()
}

function handleReset() {
  filter.projectId = undefined
  filter.orderId = undefined
  filter.contractId = undefined
  filter.partnerId = undefined
  filter.receiptCode = ''
  filter.qualityStatus = undefined
  pageNo.value = 1
  fetchData()
}

function handlePageChange(page: number) {
  pageNo.value = page
  fetchData()
}

function handlePageSizeChange(_cur: number, size: number) {
  pageSize.value = size
  pageNo.value = 1
  fetchData()
}

function handleAdd() {
  modalTitle.value = '新建材料验收'
  editingId.value = null
  Object.assign(formData, {
    projectId: undefined,
    orderId: undefined,
    contractId: undefined,
    partnerId: undefined,
    receiptDate: undefined,
    qualityStatus: undefined,
    warehouseId: undefined,
    receiverId: undefined,
    remark: '',
  })
  itemList.value = []
  itemKeyCounter = 0
  modalVisible.value = true
}

async function handleEdit(record: MatReceiptVO) {
  modalTitle.value = '编辑材料验收'
  editingId.value = record.id
  Object.assign(formData, {
    projectId: record.projectId,
    orderId: record.orderId,
    contractId: record.contractId,
    partnerId: record.partnerId,
    receiptDate: record.receiptDate,
    qualityStatus: record.qualityStatus,
    warehouseId: record.warehouseId,
    receiverId: record.receiverId,
    remark: record.remark,
  })
  itemList.value = []
  itemKeyCounter = 0
  // Load existing items
  try {
    const items = await getReceiptItems(record.id)
    itemList.value = items.map((item) => ({
      ...item,
      key: itemKeyCounter++,
    }))
  } catch (e: unknown) {
    console.error(e)
    message.error('加载验收明细失败')
    itemList.value = []
  }
  modalVisible.value = true
}

function handleDelete(record: MatReceiptVO) {
  Modal.confirm({
    title: '确认删除',
    content: `确定要删除验收单"${record.receiptCode}"吗？`,
    okText: '确定',
    cancelText: '取消',
    onOk: async () => {
      try {
        await deleteReceipt(record.id)
        message.success('删除成功')
        fetchData()
      } catch (e: unknown) {
        console.error(e)
        message.error('删除失败，请稍后重试')
      }
    },
  })
}

function handleSubmitApproval(record: MatReceiptVO) {
  Modal.confirm({
    title: '确认提交',
    content: `确定要提交验收单"${record.receiptCode}"吗？提交后将进入审批流程`,
    okText: '确定',
    cancelText: '取消',
    onOk: async () => {
      try {
        await submitReceiptForApproval(record.id)
        message.success('提交审批成功')
        fetchData()
      } catch (e: unknown) {
        console.error(e)
        message.error('提交审批失败')
      }
    },
  })
}

// --- Order selection → load order items for receipt ---
async function handleOrderChange(orderId: string | undefined) {
  itemList.value = []
  itemKeyCounter = 0
  if (!orderId) {
    formData.contractId = undefined
    formData.partnerId = undefined
    return
  }
  // Auto-fill contract and partner from selected order
  const order = orderList.value.find((o) => o.id === orderId)
  if (order) {
    formData.contractId = order.contractId
    formData.partnerId = order.partnerId
  }
  // Load order items for receipt selection
  try {
    const items = await getOrderItemsForReceipt(orderId)
    itemList.value = items.map((item) => ({
      ...item,
      key: itemKeyCounter++,
    }))
  } catch (e: unknown) {
    console.error(e)
    message.error('加载采购订单明细失败')
    itemList.value = []
  }
}

// --- Line items management ---
function handleItemQtyChange(index: number) {
  const item = itemList.value[index]
  const qty = parseFloat(item.actualQuantity || '0')
  const price = parseFloat(item.unitPrice || '0')
  item.amount = (qty * price).toFixed(2)

  // Quantity validation (W0 Decision 3: warn only)
  const remaining = parseFloat(item.remainingQuantity || '0')
  if (qty > remaining) {
    item.warning = true
  } else {
    item.warning = false
  }
}

function handleItemPriceChange(index: number) {
  const item = itemList.value[index]
  const qty = parseFloat(item.actualQuantity || '0')
  const price = parseFloat(item.unitPrice || '0')
  item.amount = (qty * price).toFixed(2)
}

function handleItemQualifiedQtyChange(index: number) {
  const item = itemList.value[index]
  const qualified = parseFloat(item.qualifiedQuantity || '0')
  const actual = parseFloat(item.actualQuantity || '0')
  if (qualified > actual) {
    message.warning('合格数量不能超过实际到货数量')
    item.qualifiedQuantity = item.actualQuantity
  }
}

const itemsTotalAmount = computed(() => {
  let total = 0
  for (const item of itemList.value) {
    total += parseFloat(item.amount || '0')
  }
  return total.toFixed(2)
})

// Check if any item has warning
const hasWarning = computed(() => {
  return itemList.value.some((item) => item.warning)
})

async function handleModalOk() {
  if (!formData.projectId) {
    message.warning('请选择项目')
    return
  }

  // Show warning but don't block (W0 Decision 3)
  if (hasWarning.value) {
    message.warning('部分验收数量超过采购订单剩余数量，请注意核对')
  }

  try {
    let receiptId: string
    if (editingId.value) {
      await updateReceipt(editingId.value, formData)
      receiptId = editingId.value
      message.success('更新成功')
    } else {
      const result = await createReceipt(formData)
      receiptId = result
      message.success('创建成功')
    }

    // Save line items
    if (itemList.value.length > 0) {
      const items = itemList.value.map((item) => ({
        ...item,
        receiptId: receiptId,
        warning: undefined,
      }))
      await saveReceiptItems(receiptId, items)
    }

    modalVisible.value = false
    fetchData()
  } catch (e: unknown) {
    console.error(e)
    message.error('操作失败，请稍后重试')
  }
}

function handleModalCancel() {
  modalVisible.value = false
}

onMounted(() => {
  referenceStore.fetchProjects()
  referenceStore.fetchContracts({ contractType: 'PURCHASE' })
  referenceStore.fetchPartners({ partnerType: 'SUPPLIER' })
  fetchOrders()
  fetchData()
})
</script>

<template>
  <div class="pm-page">
    <a-page-header title="材料验收" class="pm-header" />

    <!-- Filter -->
    <div class="pm-card pm-filter">
      <div class="pm-filter-row">
        <div class="pm-field">
          <label>项目：</label>
          <a-select
            v-model:value="filter.projectId"
            placeholder="全部"
            allow-clear
            style="width: 180px"
            show-search
            @change="
              (v: string | undefined) => {
                filter.contractId = undefined
                if (v) referenceStore.fetchContracts({ projectId: v })
              }
            "
            :filter-option="
              (input: string, option: any) =>
                option.label?.toLowerCase().includes(input.toLowerCase())
            "
          >
            <a-select-option v-for="p in projectList" :key="p.id" :value="p.id">
              {{ p.projectName }}
            </a-select-option>
          </a-select>
        </div>
        <div class="pm-field">
          <label>采购订单：</label>
          <a-select
            v-model:value="filter.orderId"
            placeholder="全部"
            allow-clear
            style="width: 180px"
            show-search
            :filter-option="
              (input: string, option: any) =>
                option.label?.toLowerCase().includes(input.toLowerCase())
            "
          >
            <a-select-option v-for="o in orderList" :key="o.id" :value="o.id">
              {{ o.orderCode }}
            </a-select-option>
          </a-select>
        </div>
        <div class="pm-field">
          <label>采购合同：</label>
          <a-select
            v-model:value="filter.contractId"
            placeholder="全部"
            allow-clear
            style="width: 180px"
            show-search
            :filter-option="
              (input: string, option: any) =>
                option.label?.toLowerCase().includes(input.toLowerCase())
            "
          >
            <a-select-option v-for="c in contractList" :key="c.id" :value="c.id">
              {{ c.contractName }}
            </a-select-option>
          </a-select>
        </div>
        <div class="pm-field">
          <label>供应商：</label>
          <a-select
            v-model:value="filter.partnerId"
            placeholder="全部"
            allow-clear
            style="width: 160px"
            show-search
            :filter-option="
              (input: string, option: any) =>
                option.label?.toLowerCase().includes(input.toLowerCase())
            "
          >
            <a-select-option v-for="p in partnerList" :key="p.id" :value="p.id">
              {{ p.partnerName }}
            </a-select-option>
          </a-select>
        </div>
        <div class="pm-field">
          <label>质量状态：</label>
          <a-select
            v-model:value="filter.qualityStatus"
            placeholder="全部"
            allow-clear
            style="width: 130px"
          >
            <a-select-option value="QUALIFIED">合格</a-select-option>
            <a-select-option value="PARTIAL">部分合格</a-select-option>
            <a-select-option value="UNQUALIFIED">不合格</a-select-option>
            <a-select-option value="PENDING">待检验</a-select-option>
          </a-select>
        </div>
        <div class="pm-field">
          <label>验收单号：</label>
          <a-input
            v-model:value="filter.receiptCode"
            placeholder="请输入单号"
            style="width: 150px"
            allow-clear
          />
        </div>
        <div class="pm-filter-actions">
          <a-button type="primary" @click="handleSearch">查询</a-button>
          <a-button @click="handleReset">重置</a-button>
          <a-button type="primary" @click="handleAdd">新建验收</a-button>
        </div>
      </div>
    </div>

    <!-- Table -->
    <div class="pm-card pm-table-wrap">
      <a-table
        :columns="columns"
        :data-source="tableData"
        :loading="loading"
        :pagination="false"
        row-key="id"
        size="small"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'totalAmount'">
            <span v-if="record.totalAmount"
              >¥{{
                Number(record.totalAmount).toLocaleString('zh-CN', { minimumFractionDigits: 2 })
              }}</span
            >
            <span v-else class="pm-none">-</span>
          </template>
          <template v-else-if="column.key === 'qualityStatus'">
            <a-tag :color="QUALITY_STATUS_COLOR[record.qualityStatus] || 'default'">
              {{ (QUALITY_STATUS_LABEL[record.qualityStatus] ?? record.qualityStatus) || '-' }}
            </a-tag>
          </template>
          <template v-else-if="column.key === 'approvalStatus'">
            <ApprovalStatusTag :status="record.approvalStatus" />
          </template>
          <template v-else-if="column.key === 'action'">
            <a-button type="link" size="small" @click="handleEdit(record)">编辑</a-button>
            <a-button type="link" size="small" danger @click="handleDelete(record)">删除</a-button>
            <a-button
              v-if="record.approvalStatus === 'DRAFT'"
              type="link"
              size="small"
              @click="handleSubmitApproval(record)"
              >提交审批</a-button
            >
          </template>
        </template>
      </a-table>
    </div>

    <!-- Pagination -->
    <div class="pm-pagination">
      <span class="pm-total">共 {{ total }} 条</span>
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

    <!-- Add/Edit Modal -->
    <a-modal
      v-model:open="modalVisible"
      :title="modalTitle"
      :width="1000"
      @ok="handleModalOk"
      @cancel="handleModalCancel"
    >
      <!-- Header Form -->
      <a-form :label-col="{ span: 6 }" :wrapper-col="{ span: 16 }" style="margin-bottom: 8px">
        <a-form-item label="项目" required>
          <a-select v-model:value="formData.projectId" placeholder="请选择项目">
            <a-select-option v-for="p in projectList" :key="p.id" :value="p.id">
              {{ p.projectName }}
            </a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="采购订单">
          <a-select
            v-model:value="formData.orderId"
            placeholder="请选择采购订单"
            allow-clear
            @change="(val: string) => handleOrderChange(val)"
          >
            <a-select-option v-for="o in orderList" :key="o.id" :value="o.id">
              {{ o.orderCode }} - {{ o.projectName }}
            </a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="采购合同">
          <a-select v-model:value="formData.contractId" placeholder="自动填充" disabled>
            <a-select-option v-for="c in contractList" :key="c.id" :value="c.id">
              {{ c.contractName }}
            </a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="供应商">
          <a-select v-model:value="formData.partnerId" placeholder="自动填充" disabled>
            <a-select-option v-for="p in partnerList" :key="p.id" :value="p.id">
              {{ p.partnerName }}
            </a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="验收日期">
          <a-date-picker
            v-model:value="formData.receiptDate"
            value-format="YYYY-MM-DD"
            style="width: 100%"
          />
        </a-form-item>
        <a-form-item label="质量状态">
          <a-select v-model:value="formData.qualityStatus" placeholder="请选择质量状态" allow-clear>
            <a-select-option value="QUALIFIED">合格</a-select-option>
            <a-select-option value="PARTIAL">部分合格</a-select-option>
            <a-select-option value="UNQUALIFIED">不合格</a-select-option>
            <a-select-option value="PENDING">待检验</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="备注">
          <a-textarea v-model:value="formData.remark" :rows="2" placeholder="请输入备注" />
        </a-form-item>
      </a-form>

      <!-- Line Items Section -->
      <div style="border-top: 1px solid #f0f0f0; padding-top: 12px; margin-top: 4px">
        <div
          style="
            display: flex;
            justify-content: space-between;
            align-items: center;
            margin-bottom: 10px;
          "
        >
          <span style="font-weight: 600; font-size: 14px">验收明细</span>
          <span style="font-size: 12px; color: #9ca3af">（选择采购订单后自动加载订单明细）</span>
        </div>

        <!-- Quantity warning -->
        <a-alert
          v-if="hasWarning"
          message="部分验收数量超过采购订单剩余数量，核实后可继续保存"
          type="warning"
          show-icon
          :closable="false"
          style="margin-bottom: 10px"
        />

        <a-table
          :data-source="itemList"
          :pagination="false"
          row-key="key"
          size="small"
          :scroll="{ y: 280 }"
        >
          <a-table-column title="材料" width="150">
            <template #default="{ record: item }">
              <span>{{ item.materialName || '-' }}</span>
            </template>
          </a-table-column>
          <a-table-column title="规格" width="90">
            <template #default="{ record: item }">
              <span>{{ item.specification || '-' }}</span>
            </template>
          </a-table-column>
          <a-table-column title="单位" width="60">
            <template #default="{ record: item }">
              <span>{{ item.unit || '-' }}</span>
            </template>
          </a-table-column>
          <a-table-column title="订单数量" width="90">
            <template #default="{ record: item }">
              <span>{{ item.orderedQuantity || '0' }}</span>
            </template>
          </a-table-column>
          <a-table-column title="已验收" width="80">
            <template #default="{ record: item }">
              <span>{{ item.receivedQuantity || '0' }}</span>
            </template>
          </a-table-column>
          <a-table-column title="剩余" width="80">
            <template #default="{ record: item }">
              <span
                :style="{
                  color: parseFloat(item.remainingQuantity || '0') < 0 ? '#ff4d4f' : undefined,
                }"
              >
                {{ item.remainingQuantity || '0' }}
              </span>
            </template>
          </a-table-column>
          <a-table-column title="本次到货" width="110">
            <template #default="{ record: item, index }">
              <a-input-number
                v-model:value="item.actualQuantity"
                :min="0"
                :precision="2"
                style="width: 100%"
                :status="item.warning ? 'warning' : undefined"
                @change="handleItemQtyChange(index)"
              />
            </template>
          </a-table-column>
          <a-table-column title="合格数量" width="110">
            <template #default="{ record: item, index }">
              <a-input-number
                v-model:value="item.qualifiedQuantity"
                :min="0"
                :precision="2"
                style="width: 100%"
                @change="handleItemQualifiedQtyChange(index)"
              />
            </template>
          </a-table-column>
          <a-table-column title="单价(元)" width="110">
            <template #default="{ record: item, index }">
              <a-input-number
                v-model:value="item.unitPrice"
                :min="0"
                :precision="2"
                style="width: 100%"
                @change="handleItemPriceChange(index)"
              />
            </template>
          </a-table-column>
          <a-table-column title="金额(元)" width="120">
            <template #default="{ record: item }">
              <span>{{
                Number(item.amount || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2 })
              }}</span>
            </template>
          </a-table-column>
          <a-table-column title="使用部位" width="120">
            <template #default="{ record: item }">
              <a-input v-model:value="item.useLocation" size="small" placeholder="部位" />
            </template>
          </a-table-column>
          <a-table-column title="批号" width="100">
            <template #default="{ record: item }">
              <a-input v-model:value="item.batchNo" size="small" placeholder="批号" />
            </template>
          </a-table-column>
        </a-table>

        <div style="text-align: right; margin-top: 8px; font-size: 14px">
          合计：<span style="font-weight: 600; color: #1677ff"
            >¥{{
              Number(itemsTotalAmount).toLocaleString('zh-CN', { minimumFractionDigits: 2 })
            }}</span
          >
        </div>
      </div>
    </a-modal>
  </div>
</template>

<style scoped>
.pm-page {
  background: #f6f8fc;
  min-height: 100%;
  padding: 4px 0;
}
.pm-header {
  background: transparent;
  padding-bottom: 12px;
}
.pm-card {
  background: #fff;
  border: 1px solid #e5eaf3;
  border-radius: 10px;
  box-shadow: 0 10px 30px rgba(17, 24, 39, 0.05);
}
.pm-filter {
  padding: 20px 22px;
  margin-bottom: 14px;
}
.pm-filter-row {
  display: flex;
  flex-wrap: wrap;
  gap: 12px 24px;
  align-items: center;
}
.pm-field {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  white-space: nowrap;
}
.pm-field label {
  color: #374151;
}
.pm-filter-actions {
  display: flex;
  gap: 10px;
  margin-left: auto;
}
.pm-table-wrap {
  overflow: hidden;
  margin-bottom: 0;
}
.pm-none {
  color: #9ca3af;
}
.pm-pagination {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 12px;
  padding: 12px 0 0;
}
.pm-total {
  font-size: 13px;
  color: #4b5563;
}
</style>
