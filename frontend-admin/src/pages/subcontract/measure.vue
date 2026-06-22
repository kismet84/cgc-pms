<script setup lang="ts">
import { ref, reactive, onMounted, computed } from 'vue'
import { storeToRefs } from 'pinia'
import { message, Modal } from 'ant-design-vue'
import { PlusOutlined, ReloadOutlined, SearchOutlined } from '@ant-design/icons-vue'
import {
  getMeasureList,
  createMeasure,
  updateMeasure,
  deleteMeasure,
  getMeasureItems,
  saveMeasureItems,
  submitMeasureForApproval,
} from '@/api/modules/subcontract'
import { getContractItems } from '@/api/modules/contract'
import { useReferenceStore } from '@/stores/reference'
import type { SubMeasureVO, SubMeasureItemVO } from '@/types/subcontract'
import type { SelectOption } from '@/types/ui'
import type { ContractItem } from '@/types/contract'

const filter = reactive({
  projectId: undefined as string | undefined,
  contractId: undefined as string | undefined,
  partnerId: undefined as string | undefined,
  status: undefined as string | undefined,
  measureCode: '',
  keyword: '',
})

const loading = ref(false)
const tableData = ref<SubMeasureVO[]>([])
const total = ref(0)
const pageNo = ref(1)
const pageSize = ref(20)

const referenceStore = useReferenceStore()
const { projects: projectList, contracts: contractList } = storeToRefs(referenceStore)
const contractItemList = ref<ContractItem[]>([])

const modalVisible = ref(false)
const modalTitle = ref('鏂板缓鍒嗗寘璁￠噺')
const editingId = ref<string | null>(null)
const formData = reactive<Partial<SubMeasureVO>>({
  projectId: undefined,
  contractId: undefined,
  partnerId: undefined,
  measurePeriod: '',
  measureDate: undefined,
  remark: '',
})
const formPartnerName = computed(
  () => contractList.value?.find((c) => c.id === formData.contractId)?.partyBName ?? '',
)

// Line items
const itemList = ref<(Partial<SubMeasureItemVO> & { key: number })[]>([])
let itemKeyCounter = 0

const STATUS_LABEL: Record<string, string> = {
  DRAFT: '鑽夌',
  APPROVING: '瀹℃壒涓?,
  CONFIRMED: '宸茬‘璁?,
  COMPLETED: '宸插畬鎴?,
}
const STATUS_COLOR: Record<string, string> = {
  DRAFT: 'default',
  APPROVING: 'processing',
  CONFIRMED: 'blue',
  COMPLETED: 'success',
}

// ---- vxe-grid columns ----
const gridColumns = computed(() => [
  { field: 'measureCode', title: '璁￠噺缂栧彿', width: 140, ellipsis: true },
  { field: 'measurePeriod', title: '璁￠噺鏈熸', width: 100 },
  { field: 'projectName', title: '椤圭洰鍚嶇О', width: 120, ellipsis: true },
  { field: 'contractName', title: '鍚堝悓鍚嶇О', width: 120, ellipsis: true },
  { field: 'partnerName', title: '鍒嗗寘鍟?, width: 120, ellipsis: true },
  {
    field: 'reportedAmount',
    title: '鐢虫姤閲戦',
    width: 100,
    align: 'right' as const,
    slots: { default: 'reportedAmount' },
  },
  {
    field: 'approvedAmount',
    title: '瀹℃牳閲戦',
    width: 100,
    align: 'right' as const,
    slots: { default: 'approvedAmount' },
  },
  {
    field: 'netAmount',
    title: '鍑€棰?,
    width: 100,
    align: 'right' as const,
    slots: { default: 'netAmount' },
  },
  { field: 'measureDate', title: '璁￠噺鏃ユ湡', width: 100 },
  { field: 'status', title: '鐘舵€?, width: 80, slots: { default: 'status' } },
  { field: 'approvalStatus', title: '瀹℃壒鐘舵€?, width: 90, slots: { default: 'approvalStatus' } },
  { title: '鎿嶄綔', width: 110, slots: { default: 'action' } },
])

async function fetchData() {
  loading.value = true
  try {
    const res = await getMeasureList({
      pageNum: pageNo.value,
      pageSize: pageSize.value,
      projectId: filter.projectId,
      contractId: filter.contractId,
      partnerId: filter.partnerId,
      status: filter.status,
      measureCode: filter.keyword || filter.measureCode || undefined,
    })
    tableData.value = res.records
    total.value = res.total
  } catch (e: unknown) {
    console.error(e)
    tableData.value = []
    total.value = 0
    message.error('鍔犺浇鍒嗗寘璁￠噺鍒楄〃澶辫触锛岃绋嶅悗閲嶈瘯')
  } finally {
    loading.value = false
  }
}

async function loadContractItems(contractId: string) {
  try {
    const items = await getContractItems(contractId)
    contractItemList.value = items
  } catch (e: unknown) {
    console.error(e)
    contractItemList.value = []
    message.error('鍔犺浇鍚堝悓娓呭崟澶辫触')
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
  filter.status = undefined
  filter.measureCode = ''
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
  modalTitle.value = '鏂板缓鍒嗗寘璁￠噺'
  editingId.value = null
  Object.assign(formData, {
    projectId: undefined,
    contractId: undefined,
    partnerId: undefined,
    measurePeriod: '',
    measureDate: undefined,
    remark: '',
  })
  itemList.value = []
  contractItemList.value = []
  itemKeyCounter = 0
  modalVisible.value = true
}

async function handleEdit(record: SubMeasureVO) {
  modalTitle.value = '缂栬緫鍒嗗寘璁￠噺'
  editingId.value = record.id
  Object.assign(formData, {
    projectId: record.projectId,
    contractId: record.contractId,
    partnerId: record.partnerId,
    measurePeriod: record.measurePeriod,
    measureDate: record.measureDate,
    remark: record.remark,
  })
  itemList.value = []
  itemKeyCounter = 0
  // Load contract items if contract is set
  if (record.contractId) {
    await loadContractItems(record.contractId)
  }
  // Load existing measure items
  try {
    const items = await getMeasureItems(record.id)
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

function handleDelete(record: SubMeasureVO) {
  Modal.confirm({
    title: '纭鍒犻櫎',
    content: `纭畾瑕佸垹闄ゅ垎鍖呰閲?${record.measureCode}"鍚楋紵`,
    okText: '纭畾',
    cancelText: '鍙栨秷',
    onOk: async () => {
      try {
        await deleteMeasure(record.id)
        message.success('鍒犻櫎鎴愬姛')
        fetchData()
      } catch (e: unknown) {
        console.error(e)
        message.error('鍒犻櫎澶辫触锛岃绋嶅悗閲嶈瘯')
      }
    },
  })
}

function handleSubmitApproval(record: SubMeasureVO) {
  Modal.confirm({
    title: '纭鎻愪氦',
    content: `纭畾瑕佹彁浜ゅ垎鍖呰閲?${record.measureCode}"鍚楋紵鎻愪氦鍚庡皢杩涘叆瀹℃壒娴佺▼`,
    okText: '纭畾',
    cancelText: '鍙栨秷',
    onOk: async () => {
      try {
        await submitMeasureForApproval(record.id)
        message.success('鎻愪氦瀹℃壒鎴愬姛')
        fetchData()
      } catch (e: unknown) {
        console.error(e)
        message.error('鎻愪氦瀹℃壒澶辫触')
      }
    },
  })
}

// --- Line items ---
function handleAddItem() {
  itemList.value.push({
    key: itemKeyCounter++,
    contractItemId: undefined,
    itemName: '',
    unit: '',
    contractQuantity: '0',
    currentQuantity: '0',
    unitPrice: '0',
    amount: '0',
  })
}

function handleRemoveItem(index: number) {
  itemList.value.splice(index, 1)
}

function handleContractItemChange(index: number, itemId: string | undefined) {
  if (!itemId) {
    const row = itemList.value[index]
    row.itemName = ''
    row.unit = ''
    row.contractQuantity = '0'
    return
  }
  const ci = contractItemList.value.find((c) => c.id === itemId)
  if (ci) {
    const row = itemList.value[index]
    row.contractItemId = ci.id
    row.itemName = ci.itemName
    row.unit = ci.unit
    row.contractQuantity = String(ci.quantity ?? 0)
  }
}

function handleItemQtyChange(index: number) {
  const item = itemList.value[index]
  const qty = parseFloat(item.currentQuantity || '0')
  const price = parseFloat(item.unitPrice || '0')
  item.amount = (qty * price).toFixed(2)
}

function handleItemPriceChange(index: number) {
  const item = itemList.value[index]
  const qty = parseFloat(item.currentQuantity || '0')
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

async function onContractSelect(contractId: string | undefined) {
  if (contractId) {
    const c = contractList.value?.find((ct) => ct.id === contractId)
    formData.partnerId = c?.partyBId
    await loadContractItems(contractId)
  } else {
    formData.partnerId = undefined
    contractItemList.value = []
  }
}

async function handleModalOk() {
  if (!formData.projectId) {
    message.warning('璇烽€夋嫨椤圭洰')
    return
  }

  try {
    let measureId: string
    if (editingId.value) {
      await updateMeasure(editingId.value, formData)
      measureId = editingId.value
      message.success('鏇存柊鎴愬姛')
    } else {
      const result = await createMeasure(formData)
      measureId = result
      message.success('鍒涘缓鎴愬姛')
    }

    // Save line items
    if (itemList.value.length > 0) {
      const items = itemList.value.map((item) => ({
        ...item,
        measureId: measureId,
      }))
      await saveMeasureItems(measureId, items)
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

const kpiTotalCount = computed(() => total.value)
const kpiMeasureTotal = computed(() =>
  tableData.value.reduce((s, r) => s + (parseFloat(r.reportedAmount) || 0), 0),
)
const kpiApproved = computed(() =>
  tableData.value
    .filter((r) => r.status === 'CONFIRMED' || r.status === 'COMPLETED')
    .reduce((s, r) => s + (parseFloat(r.approvedAmount) || 0), 0),
)
const kpiMeasurePending = computed(
  () => tableData.value.filter((r) => r.status === 'DRAFT' || r.status === 'APPROVING').length,
)
function fmtAmount(val: number): string {
  return val.toLocaleString('zh-CN', { minimumFractionDigits: 0, maximumFractionDigits: 0 })
}

onMounted(() => {
  referenceStore.fetchProjects()
  referenceStore.fetchContracts({ contractType: 'SUB' })
  referenceStore.fetchPartners({ partnerType: 'SUB' })
  fetchData()
})
</script>

<template>
  <div class="lg-page app-page">
    <div class="lg-page-head">
      <div>
        <a-breadcrumb class="lg-breadcrumb">
          <a-breadcrumb-item>鍒嗗寘绠＄悊</a-breadcrumb-item>
          <a-breadcrumb-item>鍒嗗寘璁￠噺</a-breadcrumb-item>
        </a-breadcrumb>
      </div>
    </div>

    <!-- 鎼滅储鏍?-->
    <div class="lg-search-bar">
      <a-input
        v-model:value="filter.keyword"
        placeholder="鎼滅储璁￠噺缂栧彿鈥?
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
        <span class="lg-kpi-card-label">璁￠噺鎬绘暟</span>
        <span class="lg-kpi-card-value">{{ kpiTotalCount }} <small>鏉?/small></span>
        <span class="lg-kpi-card-bar"
          ><span style="width: 100%; background: var(--kpi-total)"></span
        ></span>
      </div>
      <div class="lg-kpi-card">
        <span class="lg-kpi-card-label">鐢虫姤鎬婚</span>
        <span class="lg-kpi-card-value">{{ fmtAmount(kpiMeasureTotal) }} <small>鍏?/small></span>
        <span class="lg-kpi-card-bar"
          ><span style="width: 100%; background: var(--kpi-amount)"></span
        ></span>
      </div>
      <div class="lg-kpi-card">
        <span class="lg-kpi-card-label">宸插鏍搁噾棰?/span>
        <span class="lg-kpi-card-value">{{ fmtAmount(kpiApproved) }} <small>鍏?/small></span>
        <span class="lg-kpi-card-bar"
          ><span
            :style="{
              width:
                (kpiMeasureTotal ? Math.round((kpiApproved / kpiMeasureTotal) * 100) : 0) + '%',
              background: 'var(--kpi-paid)',
            }"
          ></span
        ></span>
        <span class="lg-kpi-card-hint" v-if="kpiMeasureTotal"
          >{{ kpiMeasureTotal ? Math.round((kpiApproved / kpiMeasureTotal) * 100) : 0 }}%</span
        >
      </div>
      <div class="lg-kpi-card is-warn" v-if="kpiMeasurePending > 0">
        <span class="lg-kpi-card-label">寰呭鏍?/span>
        <span class="lg-kpi-card-value">{{ kpiMeasurePending }} <small>鏉?/small></span>
        <span class="lg-kpi-card-bar"
          ><span
            :style="{
              width:
                (kpiTotalCount ? Math.round((kpiMeasurePending / kpiTotalCount) * 100) : 0) + '%',
              background: 'var(--kpi-overdue)',
            }"
          ></span
        ></span>
        <span class="lg-kpi-card-hint" v-if="kpiTotalCount"
          >{{ kpiTotalCount ? Math.round((kpiMeasurePending / kpiTotalCount) * 100) : 0 }}%</span
        >
      </div>
    </div>

    <!-- 宸ュ叿鏍?-->
    <div class="lg-toolbar">
      <div class="lg-toolbar-left">
        <a-button type="primary" @click="handleAdd">
          <template #icon><PlusOutlined /></template>
          鏂板缓璁￠噺
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
        <template #reportedAmount="{ row }">
          <span v-if="row.reportedAmount" class="lg-money">
            {{ Number(row.reportedAmount).toLocaleString('zh-CN', { minimumFractionDigits: 2 }) }}
          </span>
          <span v-else class="lg-none">-</span>
        </template>
        <template #approvedAmount="{ row }">
          <span v-if="row.approvedAmount" class="lg-money">
            {{ Number(row.approvedAmount).toLocaleString('zh-CN', { minimumFractionDigits: 2 }) }}
          </span>
          <span v-else class="lg-none">-</span>
        </template>
        <template #netAmount="{ row }">
          <span v-if="row.netAmount !== undefined && row.netAmount !== null" class="lg-money">
            {{ Number(row.netAmount).toLocaleString('zh-CN', { minimumFractionDigits: 2 }) }}
          </span>
          <span v-else class="lg-none">-</span>
        </template>
        <template #status="{ row }">
          <a-tag :color="STATUS_COLOR[row.status]">
            {{ STATUS_LABEL[row.status] ?? row.status }}
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
        <a-form-item label="鍒嗗寘鍚堝悓">
          <a-select
            v-model:value="formData.contractId"
            placeholder="璇烽€夋嫨鍚堝悓"
            allow-clear
            show-search
            :filter-option="
              (input: string, option: SelectOption) =>
                option.label?.toLowerCase().includes(input.toLowerCase())
            "
            @change="(val: string) => onContractSelect(val)"
          >
            <a-select-option v-for="c in contractList" :key="c.id" :value="c.id">
              {{ c.contractName }}
            </a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="鍒嗗寘鍟?>
          <a-input :value="formPartnerName" disabled placeholder="閫夋嫨鍚堝悓鍚庤嚜鍔ㄥ～鍏呬箼鏂? />
        </a-form-item>
        <a-form-item label="璁￠噺鏈熸">
          <a-input
            v-model:value="formData.measurePeriod"
            placeholder="璇疯緭鍏ヨ閲忔湡娆★紙濡傦細绗?鏈燂級"
          />
        </a-form-item>
        <a-form-item label="璁￠噺鏃ユ湡">
          <a-date-picker
            v-model:value="formData.measureDate"
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
          <span style="font-weight: 600; font-size: 14px">璁￠噺鏄庣粏</span>
          <a-button type="dashed" size="small" @click="handleAddItem">+ 娣诲姞鏄庣粏</a-button>
        </div>

        <a-table
          :data-source="itemList"
          :pagination="false"
          row-key="key"
          size="small"
          :scroll="{ y: 250 }"
        >
          <a-table-column title="鍚堝悓娓呭崟椤? width="200">
            <template #default="{ record: item, index }">
              <a-select
                :value="item.contractItemId"
                placeholder="璇烽€夋嫨娓呭崟椤?
                allow-clear
                style="width: 100%"
                @change="(val: string) => handleContractItemChange(index, val)"
              >
                <a-select-option v-for="ci in contractItemList" :key="ci.id" :value="ci.id">
                  {{ ci.itemName }}
                </a-select-option>
              </a-select>
            </template>
          </a-table-column>
          <a-table-column title="鍗曚綅" width="70">
            <template #default="{ record: item }">
              <span>{{ item.unit || '-' }}</span>
            </template>
          </a-table-column>
          <a-table-column title="鍚堝悓閲? width="100">
            <template #default="{ record: item }">
              <span>{{ item.contractQuantity || '-' }}</span>
            </template>
          </a-table-column>
          <a-table-column title="鏈湡閲? width="120">
            <template #default="{ record: item, index }">
              <a-input-number
                v-model:value="item.currentQuantity"
                :min="0"
                :precision="4"
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
                :precision="4"
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
.lg-none {
  color: var(--muted);
}
</style>
