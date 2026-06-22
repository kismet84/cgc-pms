<script setup lang="ts">
import { ref, reactive, onMounted, computed, watch } from 'vue'
import { message, Modal } from 'ant-design-vue'
import { SearchOutlined, PlusOutlined, ReloadOutlined } from '@ant-design/icons-vue'
import { useReferenceStore } from '@/stores/reference'
import {
  getOrderList,
  createOrder,
  updateOrder,
  deleteOrder,
  getOrderItems,
  saveOrderItems,
  submitOrderForApproval,
} from '@/api/modules/purchase'
import type { MatPurchaseOrderVO, MatPurchaseOrderItemVO } from '@/types/purchase'
import type { SelectOption } from '@/types/ui'

const filter = reactive({
  projectId: undefined as string | undefined,
  contractId: undefined as string | undefined,
  partnerId: undefined as string | undefined,
  orderStatus: undefined as string | undefined,
  orderType: undefined as string | undefined,
  keyword: '',
  orderCode: '',
})

const loading = ref(false)
const tableData = ref<MatPurchaseOrderVO[]>([])
const total = ref(0)
const pageNo = ref(1)
const pageSize = ref(20)

const referenceStore = useReferenceStore()
const projectList = computed(() => referenceStore.projects ?? [])
const contractList = computed(() => referenceStore.contracts ?? [])
const materialList = computed(() => referenceStore.materials ?? [])

const modalVisible = ref(false)
const modalTitle = ref('鏂板缓閲囪喘璁㈠崟')
const editingId = ref<string | null>(null)
const formData = reactive<Partial<MatPurchaseOrderVO>>({
  projectId: undefined,
  contractId: undefined,
  partnerId: undefined,
  orderType: undefined,
  orderDate: undefined,
  deliveryDate: undefined,
  remark: '',
})
const formPartnerName = computed(
  () => contractList.value?.find((c) => c.id === formData.contractId)?.partyBName ?? '',
)
function onContractChange(contractId: string) {
  const c = contractList.value?.find((ct) => ct.id === contractId)
  formData.partnerId = c?.partyBId
}
watch(
  () => formData.contractId,
  (val) => {
    if (!val) formData.partnerId = undefined
  },
)

// Line items for the modal
const itemList = ref<(Partial<MatPurchaseOrderItemVO> & { key: number })[]>([])
let itemKeyCounter = 0

const ORDER_TYPE_LABEL: Record<string, string> = {
  MATERIAL: '鏉愭枡閲囪喘',
  EQUIPMENT: '璁惧閲囪喘',
  SERVICE: '鏈嶅姟閲囪喘',
  OTHER: '鍏朵粬',
}
const ORDER_TYPE_COLOR: Record<string, string> = {
  MATERIAL: 'blue',
  EQUIPMENT: 'cyan',
  SERVICE: 'purple',
  OTHER: 'default',
}
const ORDER_STATUS_LABEL: Record<string, string> = {
  DRAFT: '鑽夌',
  APPROVING: '瀹℃壒涓?,
  PERFORMING: '灞ヨ涓?,
  COMPLETED: '宸插畬鎴?,
  CANCELLED: '宸插彇娑?,
}
const ORDER_STATUS_COLOR: Record<string, string> = {
  DRAFT: 'default',
  APPROVING: 'processing',
  PERFORMING: 'blue',
  COMPLETED: 'success',
  CANCELLED: 'error',
}

const gridColumns = computed(() => [
  { field: 'orderCode', title: '璁㈠崟缂栧彿', width: 140, ellipsis: true },
  { field: 'orderType', title: '璁㈠崟绫诲瀷', width: 90, slots: { default: 'orderType' } },
  { field: 'projectName', title: '椤圭洰鍚嶇О', width: 120, ellipsis: true },
  { field: 'contractName', title: '鍚堝悓鍚嶇О', width: 120, ellipsis: true },
  { field: 'partnerName', title: '渚涘簲鍟?, width: 120, ellipsis: true },
  {
    field: 'totalAmount',
    title: '鎬婚噾棰?,
    width: 110,
    align: 'right' as const,
    slots: { default: 'totalAmount' },
  },
  { field: 'deliveryDate', title: '浜よ揣鏃ユ湡', width: 100 },
  { field: 'orderStatus', title: '璁㈠崟鐘舵€?, width: 90, slots: { default: 'orderStatus' } },
  { field: 'approvalStatus', title: '瀹℃壒鐘舵€?, width: 90, slots: { default: 'approvalStatus' } },
  { title: '鎿嶄綔', width: 160, slots: { default: 'action' } },
])

async function fetchData() {
  loading.value = true
  try {
    const res = await getOrderList({
      pageNum: pageNo.value,
      pageSize: pageSize.value,
      projectId: filter.projectId,
      contractId: filter.contractId,
      partnerId: filter.partnerId,
      orderStatus: filter.orderStatus,
      orderType: filter.orderType,
      orderCode: filter.keyword || filter.orderCode || undefined,
    })
    tableData.value = res.records
    total.value = res.total
  } catch (e: unknown) {
    console.error(e)
    tableData.value = []
    total.value = 0
    message.error('鍔犺浇閲囪喘璁㈠崟鍒楄〃澶辫触锛岃绋嶅悗閲嶈瘯')
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  pageNo.value = 1
  fetchData()
}

function handleReset() {
  filter.projectId = undefined
  filter.contractId = undefined
  filter.partnerId = undefined
  filter.orderStatus = undefined
  filter.orderType = undefined
  filter.orderCode = ''
  filter.keyword = ''
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
  modalTitle.value = '鏂板缓閲囪喘璁㈠崟'
  editingId.value = null
  Object.assign(formData, {
    projectId: undefined,
    contractId: undefined,
    partnerId: undefined,
    orderType: undefined,
    orderDate: undefined,
    deliveryDate: undefined,
    remark: '',
  })
  itemList.value = []
  itemKeyCounter = 0
  modalVisible.value = true
}

async function handleEdit(record: MatPurchaseOrderVO) {
  modalTitle.value = '缂栬緫閲囪喘璁㈠崟'
  editingId.value = record.id
  Object.assign(formData, {
    projectId: record.projectId,
    contractId: record.contractId,
    partnerId: record.partnerId,
    orderType: record.orderType,
    orderDate: record.orderDate,
    deliveryDate: record.deliveryDate,
    remark: record.remark,
  })
  itemList.value = []
  itemKeyCounter = 0
  // Load existing items
  try {
    const items = await getOrderItems(record.id)
    itemList.value = items.map((item) => ({
      ...item,
      key: itemKeyCounter++,
    }))
  } catch (e: unknown) {
    console.error(e)
    message.error('鍔犺浇鏄庣粏澶辫触')
    itemList.value = []
  }
  modalVisible.value = true
}

function handleDelete(record: MatPurchaseOrderVO) {
  Modal.confirm({
    title: '纭鍒犻櫎',
    content: `纭畾瑕佸垹闄ら噰璐鍗?${record.orderCode}"鍚楋紵`,
    okText: '纭畾',
    cancelText: '鍙栨秷',
    onOk: async () => {
      try {
        await deleteOrder(record.id)
        message.success('鍒犻櫎鎴愬姛')
        fetchData()
      } catch (e: unknown) {
        console.error(e)
        message.error('鍒犻櫎澶辫触锛岃绋嶅悗閲嶈瘯')
      }
    },
  })
}

function handleSubmitApproval(record: MatPurchaseOrderVO) {
  Modal.confirm({
    title: '纭鎻愪氦',
    content: `纭畾瑕佹彁浜ら噰璐鍗?${record.orderCode}"鍚楋紵鎻愪氦鍚庡皢杩涘叆瀹℃壒娴佺▼`,
    okText: '纭畾',
    cancelText: '鍙栨秷',
    onOk: async () => {
      try {
        await submitOrderForApproval(record.id)
        message.success('鎻愪氦瀹℃壒鎴愬姛')
        fetchData()
      } catch (e: unknown) {
        console.error(e)
        message.error('鎻愪氦瀹℃壒澶辫触')
      }
    },
  })
}

// --- Line items management ---
function handleAddItem() {
  itemList.value.push({
    key: itemKeyCounter++,
    materialId: undefined,
    materialName: '',
    specification: '',
    unit: '',
    quantity: '0',
    unitPrice: '0',
    amount: '0',
  })
}

function handleRemoveItem(index: number) {
  itemList.value.splice(index, 1)
}

function handleMaterialChange(index: number, materialId: string | undefined) {
  if (!materialId) {
    const item = itemList.value[index]
    item.materialName = ''
    item.specification = ''
    item.unit = ''
    return
  }
  const material = materialList.value.find((m) => m.id === materialId)
  if (material) {
    const item = itemList.value[index]
    item.materialId = material.id
    item.materialName = material.materialName
    item.specification = material.specification || ''
    item.unit = material.unit || ''
  }
}

function handleItemQtyChange(index: number) {
  const item = itemList.value[index]
  const qty = parseFloat(item.quantity || '0')
  const price = parseFloat(item.unitPrice || '0')
  item.amount = (qty * price).toFixed(2)
}

function handleItemPriceChange(index: number) {
  const item = itemList.value[index]
  const qty = parseFloat(item.quantity || '0')
  const price = parseFloat(item.unitPrice || '0')
  item.amount = (qty * price).toFixed(2)
}

const itemsTotalAmount = computed(() => {
  let total = 0
  for (const item of itemList.value) {
    total += parseFloat(item.amount || '0')
  }
  return total.toFixed(2)
})

async function handleModalOk() {
  if (!formData.projectId) {
    message.warning('璇烽€夋嫨椤圭洰')
    return
  }

  try {
    let orderId: string
    if (editingId.value) {
      await updateOrder(editingId.value, formData)
      orderId = editingId.value
      message.success('鏇存柊鎴愬姛')
    } else {
      const result = await createOrder(formData)
      orderId = result
      message.success('鍒涘缓鎴愬姛')
    }

    // Save line items
    if (itemList.value.length > 0) {
      const items = itemList.value.map((item) => ({
        ...item,
        orderId: orderId,
      }))
      await saveOrderItems(orderId, items)
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

const kpiOrderTotal = computed(() => tableData.value.length)
const kpiOrderPending = computed(
  () => tableData.value.filter((r) => r.orderStatus === 'DRAFT').length,
)
const kpiOrderedAmount = computed(() =>
  tableData.value.reduce((s, r) => s + (parseFloat(r.totalAmount) || 0), 0),
)
const kpiUnreceived = computed(() =>
  tableData.value
    .filter((r) => r.orderStatus !== 'COMPLETED' && r.orderStatus !== 'CANCELLED')
    .reduce((s, r) => s + (parseFloat(r.totalAmount) || 0), 0),
)
const orderStatusBreakdown = computed(() => {
  const m: Record<string, number> = {}
  tableData.value.forEach((r) => {
    m[ORDER_STATUS_LABEL[r.orderStatus] ?? r.orderStatus] =
      (m[ORDER_STATUS_LABEL[r.orderStatus] ?? r.orderStatus] || 0) + 1
  })
  return Object.entries(m).map(([k, v]) => ({ label: k, count: v }))
})

onMounted(() => {
  referenceStore.fetchProjects()
  referenceStore.fetchContracts({ contractType: 'PURCHASE' })
  referenceStore.fetchPartners({ partnerType: 'SUPPLIER' })
  referenceStore.fetchMaterials({ status: 'ENABLE' })
  fetchData()
})
</script>

<template>
  <div class="lg-page app-page">
    <div class="lg-page-head">
      <div>
        <a-breadcrumb style="margin-bottom: 5px; font-size: 13px">
          <a-breadcrumb-item>閲囪喘绠＄悊</a-breadcrumb-item>
          <a-breadcrumb-item>閲囪喘璁㈠崟</a-breadcrumb-item>
        </a-breadcrumb>
      </div>
    </div>

    <!-- 鎼滅储鏍?-->
    <div class="lg-search-bar">
      <a-input
        v-model:value="filter.keyword"
        placeholder="鎼滅储璁㈠崟缂栧彿銆佸悕绉扳€?
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

    <div class="lg-grid">
      <div class="lg-left">
        <!-- KPI 妯潯 -->
        <div class="lg-kpi-strip">
          <div class="lg-kpi-card">
            <span class="lg-kpi-card-label">閲囪喘璁㈠崟鏁?/span>
            <span class="lg-kpi-card-value">{{ kpiOrderTotal }} <small>鏉?/small></span>
            <span class="lg-kpi-card-bar"
              ><span style="width: 100%; background: var(--kpi-total)"></span
            ></span>
          </div>
          <div class="lg-kpi-card">
            <span class="lg-kpi-card-label">寰呭鎵?/span>
            <span class="lg-kpi-card-value">{{ kpiOrderPending }} <small>鏉?/small></span>
            <span class="lg-kpi-card-bar"
              ><span style="width: 100%; background: #f59e0b"></span
            ></span>
          </div>
          <div class="lg-kpi-card">
            <span class="lg-kpi-card-label">宸蹭笅鍗曢噾棰?/span>
            <span class="lg-kpi-card-value"
              >{{ kpiOrderedAmount.toLocaleString() }} <small>鍏?/small></span
            >
            <span class="lg-kpi-card-bar"
              ><span style="width: 100%; background: var(--kpi-amount)"></span
            ></span>
          </div>
          <div class="lg-kpi-card is-warn">
            <span class="lg-kpi-card-label">鏈叆搴撻噾棰?/span>
            <span class="lg-kpi-card-value" style="color: #f59e0b"
              >{{ kpiUnreceived.toLocaleString() }} <small>鍏?/small></span
            >
            <span class="lg-kpi-card-bar"
              ><span style="width: 100%; background: #f59e0b"></span
            ></span>
          </div>
        </div>

        <!-- 宸ュ叿鏍?-->
        <div class="lg-toolbar">
          <div class="lg-toolbar-left">
            <a-button type="primary" @click="handleAdd">
              <template #icon><PlusOutlined /></template>
              鏂板缓璁㈠崟
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
              @change="handleSearch"
            >
              <a-select-option v-for="p in projectList" :key="p.id" :value="p.id">
                {{ p.projectName }}
              </a-select-option>
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
            <template #orderType="{ row }">
              <a-tag :color="ORDER_TYPE_COLOR[row.orderType]">
                {{ ORDER_TYPE_LABEL[row.orderType] ?? row.orderType }}
              </a-tag>
            </template>
            <template #totalAmount="{ row }">
              <span v-if="row.totalAmount" class="lg-money">{{
                Number(row.totalAmount).toLocaleString('zh-CN', { minimumFractionDigits: 2 })
              }}</span>
              <span v-else :style="{ color: 'var(--muted)' }">-</span>
            </template>
            <template #orderStatus="{ row }">
              <a-tag :color="ORDER_STATUS_COLOR[row.orderStatus]">
                {{ ORDER_STATUS_LABEL[row.orderStatus] ?? row.orderStatus }}
              </a-tag>
            </template>
            <template #approvalStatus="{ row }">
              <ApprovalStatusTag :status="row.approvalStatus" />
            </template>
            <template #action="{ row }">
              <a-button type="link" size="small" @click="handleEdit(row)">缂栬緫</a-button>
              <a-button type="link" size="small" danger @click="handleDelete(row)">鍒犻櫎</a-button>
              <a-button
                v-if="row.approvalStatus === 'DRAFT'"
                type="link"
                size="small"
                @click="handleSubmitApproval(row)"
                >鎻愪氦瀹℃壒</a-button
              >
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
      </div>

      <!-- 鍙充晶鍒嗘瀽闈㈡澘 -->
      <aside class="lg-analysis-rail">
        <section class="lg-panel">
          <div class="lg-panel-title">璁㈠崟鐘舵€佸垎甯?/div>
          <div class="lg-type-list">
            <div v-for="it in orderStatusBreakdown" :key="it.label" class="lg-type-row">
              <span class="lg-type-label">{{ it.label }}</span>
              <span class="lg-type-num">{{ it.count }}</span>
              <span class="lg-type-pct">鏉?/span>
            </div>
          </div>
        </section>
      </aside>
    </div>

    <!-- Add/Edit Modal -->
    <a-modal
      v-model:open="modalVisible"
      :title="modalTitle"
      :width="900"
      @ok="handleModalOk"
      @cancel="handleModalCancel"
    >
      <!-- Header Form -->
      <a-form :label-col="{ span: 6 }" :wrapper-col="{ span: 16 }" style="margin-bottom: 8px">
        <a-form-item label="椤圭洰" required>
          <a-select
            v-model:value="formData.projectId"
            placeholder="璇烽€夋嫨椤圭洰"
            show-search
            @change="
              (v: string) => {
                formData.contractId = undefined
                formData.partnerId = undefined
                referenceStore.fetchContracts({ projectId: v })
              }
            "
            :filter-option="
              (input: string, option: SelectOption) =>
                option.label?.toLowerCase().includes(input.toLowerCase())
            "
          >
            <a-select-option v-for="p in projectList" :key="p.id" :value="p.id">
              {{ p.projectName }}
            </a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="閲囪喘鍚堝悓">
          <a-select
            v-model:value="formData.contractId"
            placeholder="璇烽€夋嫨鍚堝悓"
            allow-clear
            show-search
            :filter-option="
              (input: string, option: SelectOption) =>
                option.label?.toLowerCase().includes(input.toLowerCase())
            "
            @change="onContractChange"
          >
            <a-select-option v-for="c in contractList" :key="c.id" :value="c.id">
              {{ c.contractName }}
            </a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="渚涘簲鍟?>
          <a-input :value="formPartnerName" disabled placeholder="閫夋嫨鍚堝悓鍚庤嚜鍔ㄥ～鍏呬箼鏂? />
        </a-form-item>
        <a-form-item label="璁㈠崟绫诲瀷">
          <a-select v-model:value="formData.orderType" placeholder="璇烽€夋嫨绫诲瀷" allow-clear>
            <a-select-option value="MATERIAL">鏉愭枡閲囪喘</a-select-option>
            <a-select-option value="EQUIPMENT">璁惧閲囪喘</a-select-option>
            <a-select-option value="SERVICE">鏈嶅姟閲囪喘</a-select-option>
            <a-select-option value="OTHER">鍏朵粬</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="璁㈠崟鏃ユ湡">
          <a-date-picker
            v-model:value="formData.orderDate"
            value-format="YYYY-MM-DD"
            style="width: 100%"
          />
        </a-form-item>
        <a-form-item label="浜よ揣鏃ユ湡">
          <a-date-picker
            v-model:value="formData.deliveryDate"
            value-format="YYYY-MM-DD"
            style="width: 100%"
          />
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
          <span style="font-weight: 600; font-size: 14px">璁㈠崟鏄庣粏</span>
          <a-button type="dashed" size="small" @click="handleAddItem">+ 娣诲姞鏄庣粏</a-button>
        </div>

        <a-table
          :data-source="itemList"
          :pagination="false"
          row-key="key"
          size="small"
          :scroll="{ y: 250 }"
        >
          <a-table-column title="鏉愭枡" width="200">
            <template #default="{ record: item, index }">
              <a-select
                :value="item.materialId"
                placeholder="璇烽€夋嫨鏉愭枡"
                allow-clear
                style="width: 100%"
                show-search
                :filter-option="
                  (input: string, option: SelectOption) =>
                    option.label?.toLowerCase().includes(input.toLowerCase())
                "
                @change="(val: string) => handleMaterialChange(index, val)"
              >
                <a-select-option v-for="m in materialList" :key="m.id" :value="m.id">
                  {{ m.materialName }}
                </a-select-option>
              </a-select>
            </template>
          </a-table-column>
          <a-table-column title="瑙勬牸" width="100">
            <template #default="{ record: item }">
              <span>{{ item.specification || '-' }}</span>
            </template>
          </a-table-column>
          <a-table-column title="鍗曚綅" width="70">
            <template #default="{ record: item }">
              <span>{{ item.unit || '-' }}</span>
            </template>
          </a-table-column>
          <a-table-column title="鏁伴噺" width="120">
            <template #default="{ record: item, index }">
              <a-input-number
                v-model:value="item.quantity"
                :min="0"
                :precision="2"
                style="width: 100%"
                @change="handleItemQtyChange(index)"
              />
            </template>
          </a-table-column>
          <a-table-column title="鍗曚环(鍏?" width="130">
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
          <a-table-column title="閲戦(鍏?" width="130">
            <template #default="{ record: item }">
              <span>{{
                Number(item.amount || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2 })
              }}</span>
            </template>
          </a-table-column>
          <a-table-column title="鎿嶄綔" width="60">
            <template #default="{ index }">
              <a-button type="link" size="small" danger @click="handleRemoveItem(index)"
                >鍒犻櫎</a-button
              >
            </template>
          </a-table-column>
        </a-table>

        <div style="text-align: right; margin-top: 8px; font-size: 14px">
          鍚堣锛?span style="font-weight: 600; color: #1677ff"
            >楼{{
              Number(itemsTotalAmount).toLocaleString('zh-CN', { minimumFractionDigits: 2 })
            }}</span
          >
        </div>
      </div>
    </a-modal>
  </div>
</template>
