<script setup lang="ts">
import { ref, reactive, onMounted, computed } from 'vue'
import { PlusOutlined, ReloadOutlined, SearchOutlined } from '@ant-design/icons-vue'
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
import { getWarehouseList } from '@/api/modules/inventory'
import { useReferenceStore } from '@/stores/reference'
import type { MatReceiptVO, MatReceiptItemVO } from '@/types/receipt'
import type { MatPurchaseOrderVO } from '@/types/purchase'
import type { WarehouseVO } from '@/types/inventory'
import type { SelectOption } from '@/types/ui'

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
const warehouseList = ref<WarehouseVO[]>([])

const modalVisible = ref(false)
const modalTitle = ref('鏂板缓鏉愭枡楠屾敹')
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
  QUALIFIED: '鍚堟牸',
  PARTIAL: '閮ㄥ垎鍚堟牸',
  UNQUALIFIED: '涓嶅悎鏍?,
  PENDING: '寰呮楠?,
}
const QUALITY_STATUS_COLOR: Record<string, string> = {
  QUALIFIED: 'success',
  PARTIAL: 'warning',
  UNQUALIFIED: 'error',
  PENDING: 'processing',
}

// ---- KPI computeds ----
const kpiTotalCount = computed(() => total.value)
const kpiTotalAmount = computed(() => {
  return tableData.value.reduce((sum, r) => sum + parseFloat(r.totalAmount || '0'), 0).toFixed(2)
})
const kpiQualifiedCount = computed(() => {
  return tableData.value.filter((r) => r.qualityStatus === 'QUALIFIED').length
})
const kpiUnqualifiedCount = computed(() => {
  return tableData.value.filter((r) => r.qualityStatus === 'UNQUALIFIED').length
})

function fmtAmount(val: string): string {
  const n = parseFloat(val)
  if (isNaN(n)) return '0.00'
  return (n / 10000).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}
// ---- End KPI ----

const gridColumns = computed(() => [
  { field: 'receiptCode', title: '楠屾敹鍗曞彿', width: 140, ellipsis: true },
  { field: 'orderCode', title: '閲囪喘璁㈠崟', width: 130, ellipsis: true },
  { field: 'projectName', title: '椤圭洰', width: 120, ellipsis: true },
  { field: 'partnerName', title: '渚涘簲鍟?, width: 120, ellipsis: true },
  { field: 'receiptDate', title: '楠屾敹鏃ユ湡', width: 100 },
  {
    field: 'totalAmount',
    title: '鎬婚噾棰?,
    width: 110,
    align: 'right' as const,
    slots: { default: 'totalAmount' },
  },
  { field: 'qualityStatus', title: '璐ㄩ噺鐘舵€?, width: 100, slots: { default: 'qualityStatus' } },
  { field: 'approvalStatus', title: '瀹℃壒鐘舵€?, width: 100, slots: { default: 'approvalStatus' } },
  { title: '鎿嶄綔', width: 180, slots: { default: 'action' } },
])

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
    message.error('鍔犺浇楠屾敹鍒楄〃澶辫触锛岃绋嶅悗閲嶈瘯')
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

async function fetchWarehouses() {
  try {
    const res = await getWarehouseList({ pageNo: 1, pageSize: 200, status: 'ENABLE' })
    warehouseList.value = res.records
  } catch (e: unknown) {
    console.error(e)
    warehouseList.value = []
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
  modalTitle.value = '鏂板缓鏉愭枡楠屾敹'
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
  modalTitle.value = '缂栬緫鏉愭枡楠屾敹'
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
    message.error('鍔犺浇楠屾敹鏄庣粏澶辫触')
    itemList.value = []
  }
  modalVisible.value = true
}

function handleDelete(record: MatReceiptVO) {
  Modal.confirm({
    title: '纭鍒犻櫎',
    content: `纭畾瑕佸垹闄ら獙鏀跺崟"${record.receiptCode}"鍚楋紵`,
    okText: '纭畾',
    cancelText: '鍙栨秷',
    onOk: async () => {
      try {
        await deleteReceipt(record.id)
        message.success('鍒犻櫎鎴愬姛')
        fetchData()
      } catch (e: unknown) {
        console.error(e)
        message.error('鍒犻櫎澶辫触锛岃绋嶅悗閲嶈瘯')
      }
    },
  })
}

function handleSubmitApproval(record: MatReceiptVO) {
  Modal.confirm({
    title: '纭鎻愪氦',
    content: `纭畾瑕佹彁浜ら獙鏀跺崟"${record.receiptCode}"鍚楋紵鎻愪氦鍚庡皢杩涘叆瀹℃壒娴佺▼`,
    okText: '纭畾',
    cancelText: '鍙栨秷',
    onOk: async () => {
      try {
        await submitReceiptForApproval(record.id)
        message.success('鎻愪氦瀹℃壒鎴愬姛')
        fetchData()
      } catch (e: unknown) {
        console.error(e)
        message.error('鎻愪氦瀹℃壒澶辫触')
      }
    },
  })
}

// --- Order selection 鈫?load order items for receipt ---
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
    message.error('鍔犺浇閲囪喘璁㈠崟鏄庣粏澶辫触')
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
    message.warning('鍚堟牸鏁伴噺涓嶈兘瓒呰繃瀹為檯鍒拌揣鏁伴噺')
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
    message.warning('璇烽€夋嫨椤圭洰')
    return
  }

  // Show warning but don't block (W0 Decision 3)
  if (hasWarning.value) {
    message.warning('閮ㄥ垎楠屾敹鏁伴噺瓒呰繃閲囪喘璁㈠崟鍓╀綑鏁伴噺锛岃娉ㄦ剰鏍稿')
  }

  try {
    let receiptId: string
    if (editingId.value) {
      await updateReceipt(editingId.value, formData)
      receiptId = editingId.value
      message.success('鏇存柊鎴愬姛')
    } else {
      const result = await createReceipt(formData)
      receiptId = result
      message.success('鍒涘缓鎴愬姛')
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
    message.error('鎿嶄綔澶辫触锛岃绋嶅悗閲嶈瘯')
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
  fetchWarehouses()
  fetchData()
})
</script>

<template>
  <div class="lg-page app-page">
    <div class="lg-page-head">
      <div>
        <a-breadcrumb class="lg-breadcrumb">
          <a-breadcrumb-item>閲囪喘绠＄悊</a-breadcrumb-item>
          <a-breadcrumb-item>鏉愭枡楠屾敹</a-breadcrumb-item>
        </a-breadcrumb>
      </div>
    </div>

    <!-- 鎼滅储鏍?-->
    <div class="lg-search-bar">
      <a-input
        v-model:value="filter.receiptCode"
        placeholder="鎼滅储楠屾敹鍗曞彿鈥?
        allow-clear
        size="large"
        @press-enter="handleSearch"
      >
        <template #prefix><SearchOutlined style="color: #697380" /></template>
      </a-input>
      <a-button type="primary" size="large" @click="handleSearch">鏌ヨ</a-button>
      <a-button size="large" @click="handleReset">
        <template #icon><ReloadOutlined /></template>
        閲嶇疆
      </a-button>
    </div>

    <!-- KPI 妯潯 -->
    <div class="lg-kpi-strip">
      <div class="lg-kpi-card">
        <span class="lg-kpi-card-label">楠屾敹鎬绘暟</span>
        <span class="lg-kpi-card-value">{{ kpiTotalCount }} <small>鍗?/small></span>
        <span class="lg-kpi-card-bar"
          ><span style="width: 100%; background: var(--kpi-total)"></span
        ></span>
      </div>
      <div class="lg-kpi-card">
        <span class="lg-kpi-card-label">楠屾敹鎬婚噾棰?鍚◣)</span>
        <span class="lg-kpi-card-value">{{ fmtAmount(kpiTotalAmount) }} <small>涓囧厓</small></span>
        <span class="lg-kpi-card-bar"
          ><span style="width: 100%; background: var(--kpi-amount)"></span
        ></span>
      </div>
      <div class="lg-kpi-card">
        <span class="lg-kpi-card-label">鍚堟牸鎵规</span>
        <span class="lg-kpi-card-value">{{ kpiQualifiedCount }} <small>鍗?/small></span>
        <span class="lg-kpi-card-bar"
          ><span
            :style="{
              width:
                (kpiTotalCount ? Math.round((kpiQualifiedCount / kpiTotalCount) * 100) : 0) + '%',
              background: 'var(--kpi-paid)',
            }"
          ></span
        ></span>
        <span class="lg-kpi-card-hint" v-if="kpiTotalCount"
          >{{ kpiTotalCount ? Math.round((kpiQualifiedCount / kpiTotalCount) * 100) : 0 }}%</span
        >
      </div>
      <div class="lg-kpi-card is-warn" v-if="kpiUnqualifiedCount > 0">
        <span class="lg-kpi-card-label">涓嶅悎鏍兼壒娆?/span>
        <span class="lg-kpi-card-value">{{ kpiUnqualifiedCount }} <small>鍗?/small></span>
        <span class="lg-kpi-card-bar"
          ><span
            :style="{
              width:
                (kpiTotalCount ? Math.round((kpiUnqualifiedCount / kpiTotalCount) * 100) : 0) + '%',
              background: 'var(--kpi-overdue)',
            }"
          ></span
        ></span>
        <span class="lg-kpi-card-hint" v-if="kpiTotalCount"
          >{{ kpiTotalCount ? Math.round((kpiUnqualifiedCount / kpiTotalCount) * 100) : 0 }}%</span
        >
      </div>
    </div>

    <!-- 宸ュ叿鏍?-->
    <div class="lg-toolbar">
      <div class="lg-toolbar-left">
        <a-button type="primary" @click="handleAdd">
          <template #icon><PlusOutlined /></template>
          鏂板缓楠屾敹
        </a-button>
        <a-button @click="fetchData">
          <template #icon><ReloadOutlined /></template>
        </a-button>
      </div>
      <div class="lg-toolbar-right">
        <a-select
          v-model:value="filter.projectId"
          placeholder="鍏ㄩ儴椤圭洰"
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
          placeholder="鍏ㄩ儴璁㈠崟"
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
          placeholder="鍏ㄩ儴璐ㄩ噺鐘舵€?
          allow-clear
          style="width: 140px"
          size="small"
          @change="handleSearch"
        >
          <a-select-option value="QUALIFIED">鍚堟牸</a-select-option>
          <a-select-option value="PARTIAL">閮ㄥ垎鍚堟牸</a-select-option>
          <a-select-option value="UNQUALIFIED">涓嶅悎鏍?/a-select-option>
          <a-select-option value="PENDING">寰呮楠?/a-select-option>
        </a-select>
      </div>
    </div>

    <!-- 琛ㄦ牸 -->
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
          <div class="lg-ops">
            <a class="lg-link" @click="handleEdit(row)">缂栬緫</a>
            <a class="lg-link lg-del" @click="handleDelete(row)">鍒犻櫎</a>
            <a
              v-if="row.approvalStatus === 'DRAFT'"
              class="lg-link"
              @click="handleSubmitApproval(row)"
              >鎻愪氦瀹℃壒</a
            >
          </div>
        </template>
      </vxe-grid>
    </div>

    <!-- 鍒嗛〉 -->
    <div class="lg-pagination">
      <span class="lg-total">鍏?{{ total }} 鏉?/span>
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
        <a-form-item label="椤圭洰" required>
          <a-select v-model:value="formData.projectId" placeholder="璇烽€夋嫨椤圭洰">
            <a-select-option v-for="p in projectList" :key="p.id" :value="p.id">
              {{ p.projectName }}
            </a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="閲囪喘璁㈠崟">
          <a-select
            v-model:value="formData.orderId"
            placeholder="璇烽€夋嫨閲囪喘璁㈠崟"
            allow-clear
            @change="(val: string) => handleOrderChange(val)"
          >
            <a-select-option v-for="o in orderList" :key="o.id" :value="o.id">
              {{ o.orderCode }} - {{ o.projectName }}
            </a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="閲囪喘鍚堝悓">
          <a-select v-model:value="formData.contractId" placeholder="鑷姩濉厖" disabled>
            <a-select-option v-for="c in contractList" :key="c.id" :value="c.id">
              {{ c.contractName }}
            </a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="渚涘簲鍟?>
          <a-select v-model:value="formData.partnerId" placeholder="鑷姩濉厖" disabled>
            <a-select-option v-for="p in partnerList" :key="p.id" :value="p.id">
              {{ p.partnerName }}
            </a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="鍏ュ簱浠撳簱">
          <a-select
            v-model:value="formData.warehouseId"
            placeholder="璇烽€夋嫨鍏ュ簱浠撳簱"
            allow-clear
          >
            <a-select-option v-for="w in warehouseList" :key="w.id" :value="w.id">
              {{ w.warehouseName }}
            </a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="楠屾敹鏃ユ湡">
          <a-date-picker
            v-model:value="formData.receiptDate"
            value-format="YYYY-MM-DD"
            style="width: 100%"
          />
        </a-form-item>
        <a-form-item label="璐ㄩ噺鐘舵€?>
          <a-select v-model:value="formData.qualityStatus" placeholder="璇烽€夋嫨璐ㄩ噺鐘舵€? allow-clear>
            <a-select-option value="QUALIFIED">鍚堟牸</a-select-option>
            <a-select-option value="PARTIAL">閮ㄥ垎鍚堟牸</a-select-option>
            <a-select-option value="UNQUALIFIED">涓嶅悎鏍?/a-select-option>
            <a-select-option value="PENDING">寰呮楠?/a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="澶囨敞">
          <a-textarea v-model:value="formData.remark" :rows="2" placeholder="璇疯緭鍏ュ娉? />
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
          <span style="font-weight: 600; font-size: 14px">楠屾敹鏄庣粏</span>
          <span style="font-size: 12px; color: #9ca3af">锛堥€夋嫨閲囪喘璁㈠崟鍚庤嚜鍔ㄥ姞杞借鍗曟槑缁嗭級</span>
        </div>

        <!-- Quantity warning -->
        <a-alert
          v-if="hasWarning"
          message="閮ㄥ垎楠屾敹鏁伴噺瓒呰繃閲囪喘璁㈠崟鍓╀綑鏁伴噺锛屾牳瀹炲悗鍙户缁繚瀛?
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
          <a-table-column title="鏉愭枡" width="150">
            <template #default="{ record: item }">
              <span>{{ item.materialName || '-' }}</span>
            </template>
          </a-table-column>
          <a-table-column title="瑙勬牸" width="90">
            <template #default="{ record: item }">
              <span>{{ item.specification || '-' }}</span>
            </template>
          </a-table-column>
          <a-table-column title="鍗曚綅" width="60">
            <template #default="{ record: item }">
              <span>{{ item.unit || '-' }}</span>
            </template>
          </a-table-column>
          <a-table-column title="璁㈠崟鏁伴噺" width="90">
            <template #default="{ record: item }">
              <span>{{ item.orderedQuantity || '0' }}</span>
            </template>
          </a-table-column>
          <a-table-column title="宸查獙鏀? width="80">
            <template #default="{ record: item }">
              <span>{{ item.receivedQuantity || '0' }}</span>
            </template>
          </a-table-column>
          <a-table-column title="鍓╀綑" width="80">
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
          <a-table-column title="鏈鍒拌揣" width="110">
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
          <a-table-column title="鍚堟牸鏁伴噺" width="110">
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
          <a-table-column title="鍗曚环(鍏?" width="110">
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
          <a-table-column title="閲戦(鍏?" width="120">
            <template #default="{ record: item }">
              <span>{{
                Number(item.amount || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2 })
              }}</span>
            </template>
          </a-table-column>
          <a-table-column title="浣跨敤閮ㄤ綅" width="120">
            <template #default="{ record: item }">
              <a-input v-model:value="item.useLocation" size="small" placeholder="閮ㄤ綅" />
            </template>
          </a-table-column>
          <a-table-column title="鎵瑰彿" width="100">
            <template #default="{ record: item }">
              <a-input v-model:value="item.batchNo" size="small" placeholder="鎵瑰彿" />
            </template>
          </a-table-column>
        </a-table>

        <div style="text-align: right; margin-top: 8px; font-size: 14px">
          鍚堣锛?span style="font-weight: 600; color: #1677ff">{{
            Number(itemsTotalAmount).toLocaleString('zh-CN', { minimumFractionDigits: 2 })
          }}</span>
        </div>
      </div>
    </a-modal>
  </div>
</template>

<style scoped>
/* 椤甸潰涓撳睘鏍峰紡 鈥?鍏朵綑宸茬敱 lg-* 鍏ㄥ眬绫昏鐩?*/
.lg-breadcrumb {
  margin-bottom: 5px;
  font-size: 13px;
}
</style>
