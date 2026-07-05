<script setup lang="ts">
import { ref, reactive, onMounted, computed, watch } from 'vue'
import { message, Modal } from 'ant-design-vue'
import {
  ClockCircleOutlined,
  DollarOutlined,
  FileDoneOutlined,
  MoreOutlined,
  PlusOutlined,
  ReloadOutlined,
  SearchOutlined,
  ShoppingCartOutlined,
  WalletOutlined,
} from '@ant-design/icons-vue'
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
import { useColumnSettings } from '@/composables/useColumnSettings'
import { ColumnSettingsButton } from '@/components/list-page'

// 字典常量 - 审批状态
const APPROVAL_DRAFT = 'DRAFT'
const APPROVAL_APPROVING = 'APPROVING'

// 字典常量 - 订单状态
const ORDER_STATUS_DRAFT = 'DRAFT'
const ORDER_STATUS_APPROVING = 'APPROVING'
const ORDER_STATUS_COMPLETED = 'COMPLETED'
const ORDER_STATUS_CANCELLED = 'CANCELLED'

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
const modalTitle = ref('新建采购订单')
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
  CONTRACT: '合同采购',
  MATERIAL: '材料采购',
  EQUIPMENT: '设备采购',
  SERVICE: '服务采购',
  OTHER: '其他',
}
const ORDER_TYPE_COLOR: Record<string, string> = {
  CONTRACT: 'blue',
  MATERIAL: 'blue',
  EQUIPMENT: 'cyan',
  SERVICE: 'purple',
  OTHER: 'default',
}
const ORDER_STATUS_LABEL: Record<string, string> = {
  DRAFT: '草稿',
  APPROVING: '审批中',
  PERFORMING: '履行中',
  COMPLETED: '已完成',
  CANCELLED: '已取消',
}
const ORDER_STATUS_COLOR: Record<string, string> = {
  DRAFT: 'default',
  APPROVING: 'processing',
  PERFORMING: 'blue',
  COMPLETED: 'success',
  CANCELLED: 'error',
}

const gridColumns = computed(() => [
  { field: 'orderCode', title: '订单编号', minWidth: 150, slots: { default: 'orderCode' } },
  { field: 'orderType', title: '订单类型', width: 108, slots: { default: 'orderType' } },
  { field: 'projectName', title: '项目名称', minWidth: 150, ellipsis: true },
  { field: 'contractName', title: '合同名称', minWidth: 150, ellipsis: true },
  { field: 'partnerName', title: '供应商', minWidth: 140, ellipsis: true },
  {
    field: 'totalAmount',
    title: '总金额',
    width: 128,
    align: 'right' as const,
    slots: { default: 'totalAmount' },
  },
  { field: 'deliveryDate', title: '交货日期', width: 112 },
  { field: 'orderStatus', title: '订单状态', width: 108, slots: { default: 'orderStatus' } },
  { field: 'approvalStatus', title: '审批状态', width: 108, slots: { default: 'approvalStatus' } },
  { title: '操作', width: 76, slots: { default: 'action' } },
])

const {
  visibleColumns: visibleGridColumns,
  columnSettings,
  colVisible,
  toggleCol,
} = useColumnSettings('purchase_order_cols_v2', gridColumns, {
  deliveryDate: false,
  approvalStatus: false,
})

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
    total.value = Number(res.total ?? 0)
  } catch (e: unknown) {
    console.error(e)
    tableData.value = []
    total.value = 0
    message.error('加载采购订单列表失败，请稍后重试')
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
  modalTitle.value = '新建采购订单'
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
  modalTitle.value = '编辑采购订单'
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
    message.error('加载明细失败')
    itemList.value = []
  }
  modalVisible.value = true
}

async function handleView(record: MatPurchaseOrderVO) {
  await handleEdit(record)
  modalTitle.value = '查看采购订单'
}

function handleDelete(record: MatPurchaseOrderVO) {
  Modal.confirm({
    title: '确认删除',
    content: `确定要删除采购订单"${record.orderCode}"吗？`,
    okText: '确定',
    cancelText: '取消',
    onOk: async () => {
      try {
        await deleteOrder(record.id)
        message.success('删除成功')
        fetchData()
      } catch (e: unknown) {
        console.error(e)
        message.error('删除失败，请稍后重试')
      }
    },
  })
}

function handleSubmitApproval(record: MatPurchaseOrderVO) {
  Modal.confirm({
    title: '确认提交',
    content: `确定要提交采购订单"${record.orderCode}"吗？提交后将进入审批流程`,
    okText: '确定',
    cancelText: '取消',
    onOk: async () => {
      try {
        await submitOrderForApproval(record.id)
        message.success('提交审批成功')
        fetchData()
      } catch (e: unknown) {
        console.error(e)
        message.error('提交审批失败')
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
    message.warning('请选择项目')
    return
  }

  try {
    let orderId: string
    if (editingId.value) {
      await updateOrder(editingId.value, formData)
      orderId = editingId.value
      message.success('更新成功')
    } else {
      const result = await createOrder(formData)
      orderId = result
      message.success('创建成功')
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
    message.error('操作失败，请稍后重试')
  }
}

function handleModalCancel() {
  modalVisible.value = false
}

const kpiOrderTotal = computed(() => tableData.value.length)
const kpiOrderPending = computed(
  () => tableData.value.filter((r) => r.orderStatus === ORDER_STATUS_DRAFT).length,
)
const kpiOrderedAmount = computed(() =>
  tableData.value.reduce((s, r) => s + (parseFloat(r.totalAmount) || 0), 0),
)
const kpiUnreceived = computed(() =>
  tableData.value
    .filter((r) => r.orderStatus !== ORDER_STATUS_COMPLETED && r.orderStatus !== ORDER_STATUS_CANCELLED)
    .reduce((s, r) => s + (parseFloat(r.totalAmount) || 0), 0),
)
function fmtWan(value: number): string {
  return (value / 10000).toLocaleString('zh-CN', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  })
}
function kpiPct(value: number, max: number): number {
  if (max === 0) return 0
  return Math.min(Math.round((value / max) * 100), 100)
}
const kpiMax = computed(() => ({
  totalAmount: Math.max(kpiOrderedAmount.value, kpiUnreceived.value, 1),
  totalCount: Math.max(total.value, tableData.value.length, 1),
}))
const orderStatusBreakdown = computed(() => {
  const m: Record<string, number> = {}
  tableData.value.forEach((r) => {
    const key = r.orderStatus || ORDER_STATUS_DRAFT
    m[key] = (m[key] || 0) + 1
  })
  return Object.entries(m).map(([key, count]) => ({
    key,
    label: ORDER_STATUS_LABEL[key] ?? key,
    count,
    pct: kpiPct(count, kpiMax.value.totalCount),
    color:
      key === ORDER_STATUS_COMPLETED
        ? '#31c48d'
        : key === ORDER_STATUS_CANCELLED
          ? '#ef4444'
          : key === ORDER_STATUS_APPROVING
            ? '#2563eb'
            : '#f59e0b',
  }))
})
const orderTypeBreakdown = computed(() => {
  const m: Record<string, number> = {}
  tableData.value.forEach((r) => {
    const key = r.orderType || 'OTHER'
    m[key] = (m[key] || 0) + 1
  })
  return Object.entries(m).map(([key, count]) => ({
    key,
    label: ORDER_TYPE_LABEL[key] ?? key,
    count,
    pct: kpiPct(count, kpiMax.value.totalCount),
    color:
      key === 'MATERIAL'
        ? '#2563eb'
        : key === 'EQUIPMENT'
          ? '#0891b2'
          : key === 'SERVICE'
            ? '#8b5cf6'
            : '#94a3b8',
  }))
})
const pendingOrders = computed(() =>
  tableData.value
    .filter((row) => row.orderStatus !== ORDER_STATUS_COMPLETED && row.orderStatus !== ORDER_STATUS_CANCELLED)
    .map((row) => ({
      id: row.id,
      project: row.projectName || '-',
      title: row.orderCode || row.contractName || '-',
      amount: fmtWan(parseFloat(row.totalAmount || '0') || 0),
    }))
    .slice(0, 4),
)

function onFilterProjectChange(v: string | undefined) {
  filter.contractId = undefined
  if (v) referenceStore.fetchContracts({ projectId: v, contractType: 'PURCHASE' })
  handleSearch()
}

onMounted(() => {
  referenceStore.fetchProjects()
  referenceStore.fetchContracts({ contractType: 'PURCHASE' })
  referenceStore.fetchPartners({ partnerType: 'SUPPLIER' })
  referenceStore.fetchMaterials({ status: 'ENABLE' })
  fetchData()
})
</script>

<template>
  <div class="lg-list-page lg-page app-page purchase-order-page">
    <div class="lg-page-head purchase-order-page-head">
      <div class="purchase-order-page-meta-row">
        <a-breadcrumb class="purchase-order-breadcrumb">
          <a-breadcrumb-item>采购管理</a-breadcrumb-item>
          <a-breadcrumb-item>采购订单</a-breadcrumb-item>
        </a-breadcrumb>
        <span class="purchase-order-page-subtitle">按项目跟踪采购订单、履约状态与未入库金额。</span>
      </div>
    </div>

    <!-- 搜索栏 -->
    <div class="lg-search-bar purchase-order-search-bar">
      <div class="purchase-order-search-fields">
        <a-input
          v-model:value="filter.keyword"
          class="purchase-order-search-input"
          placeholder="搜索订单编号、名称"
          allow-clear
          size="large"
          @press-enter="handleSearch"
        >
          <template #prefix><SearchOutlined class="purchase-order-search-prefix-icon" /></template>
        </a-input>
        <a-select
          v-model:value="filter.projectId"
          class="purchase-order-search-select"
          placeholder="全部项目"
          allow-clear
          size="large"
          @change="onFilterProjectChange"
        >
          <a-select-option v-for="p in projectList" :key="p.id" :value="p.id">
            {{ p.projectName }}
          </a-select-option>
        </a-select>
        <a-select
          v-model:value="filter.orderType"
          class="purchase-order-search-select is-compact"
          placeholder="类型"
          allow-clear
          size="large"
          @change="handleSearch"
        >
          <a-select-option v-for="(label, key) in ORDER_TYPE_LABEL" :key="key" :value="key">
            {{ label }}
          </a-select-option>
        </a-select>
        <a-select
          v-model:value="filter.orderStatus"
          class="purchase-order-search-select is-compact"
          placeholder="状态"
          allow-clear
          size="large"
          @change="handleSearch"
        >
          <a-select-option v-for="(label, key) in ORDER_STATUS_LABEL" :key="key" :value="key">
            {{ label }}
          </a-select-option>
        </a-select>
      </div>
      <div class="purchase-order-search-actions">
        <a-button type="primary" size="large" @click="handleSearch">查询</a-button>
        <a-button size="large" @click="handleReset">
          <template #icon><ReloadOutlined /></template>
          重置
        </a-button>
      </div>
    </div>

    <div class="lg-grid purchase-order-workspace">
      <div class="lg-left">
        <!-- KPI 横条 -->
        <div class="purchase-order-kpi-summary" aria-label="采购订单关键指标">
          <div class="purchase-order-kpi-item">
            <span class="purchase-order-kpi-icon is-total"><ShoppingCartOutlined /></span>
            <span class="purchase-order-kpi-label">采购订单数</span>
            <span class="purchase-order-kpi-value">{{ kpiOrderTotal }} <small>单</small></span>
          </div>
          <div class="purchase-order-kpi-item is-wide">
            <span class="purchase-order-kpi-icon is-amount"><DollarOutlined /></span>
            <span class="purchase-order-kpi-label">已下单金额</span>
            <span class="purchase-order-kpi-value"
              >{{ fmtWan(kpiOrderedAmount) }} <small>万元</small></span
            >
          </div>
          <div class="purchase-order-kpi-item is-progress">
            <span class="purchase-order-kpi-icon is-pending"><ClockCircleOutlined /></span>
            <span class="purchase-order-kpi-label">待审批</span>
            <span class="purchase-order-kpi-value">{{ kpiOrderPending }} <small>单</small></span>
            <span class="purchase-order-kpi-progress">
              <span :style="{ width: kpiPct(kpiOrderPending, kpiMax.totalCount) + '%' }"></span>
            </span>
          </div>
          <div class="purchase-order-kpi-item is-progress is-unreceived">
            <span class="purchase-order-kpi-icon is-unreceived"><WalletOutlined /></span>
            <span class="purchase-order-kpi-label">未入库金额</span>
            <span class="purchase-order-kpi-value"
              >{{ fmtWan(kpiUnreceived) }} <small>万元</small></span
            >
            <span class="purchase-order-kpi-progress">
              <span :style="{ width: kpiPct(kpiUnreceived, kpiMax.totalAmount) + '%' }"></span>
            </span>
          </div>
          <div class="purchase-order-kpi-item">
            <span class="purchase-order-kpi-icon is-done"><FileDoneOutlined /></span>
            <span class="purchase-order-kpi-label">已完成订单</span>
            <span class="purchase-order-kpi-value">
              {{ tableData.filter((r) => r.orderStatus === ORDER_STATUS_COMPLETED).length }} <small>单</small>
            </span>
          </div>
        </div>

        <main class="lg-list-table-panel purchase-order-table-panel">
          <!-- 工具栏 -->
          <div class="lg-toolbar purchase-order-toolbar">
            <div class="lg-toolbar-left">
              <span class="purchase-order-table-title">采购订单</span>
              <span class="purchase-order-table-count">共 {{ total }} 条</span>
              <ColumnSettingsButton
                :columns="columnSettings"
                :visible="colVisible"
                @toggle="toggleCol"
              />
              <a-button type="primary" @click="handleAdd">
                <template #icon><PlusOutlined /></template>
                新建订单
              </a-button>
              <a-button @click="fetchData">
                <template #icon><ReloadOutlined /></template>
                刷新
              </a-button>
            </div>
            <div class="lg-toolbar-right">
              <span class="purchase-order-toolbar-hint">固定表头 / 审批状态 / 行操作展开</span>
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
              <template #orderCode="{ row }">
                <a-button class="purchase-order-code-link" type="link" @click="handleView(row)">
                  {{ row.orderCode || '-' }}
                </a-button>
              </template>
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

      <!-- 右侧分析面板 -->
      <aside class="lg-analysis-rail purchase-order-analysis-rail" aria-label="采购订单辅助分析">
        <div class="purchase-order-analysis-panel">
          <header class="purchase-order-analysis-head">
            <div>
              <div class="purchase-order-analysis-title">订单分析</div>
              <div class="purchase-order-analysis-subtitle">状态、类型与待履约金额</div>
            </div>
            <a-button type="link" size="small" @click="fetchData">刷新</a-button>
          </header>

          <section class="purchase-order-analysis-section">
            <div class="purchase-order-section-title">订单状态分布</div>
            <div v-for="it in orderStatusBreakdown" :key="it.key" class="lg-type-row">
              <span class="lg-type-dot" :style="{ background: it.color }"></span>
              <span class="lg-type-label">{{ it.label }}</span>
              <span class="lg-type-bar-wrap">
                <span
                  class="lg-type-bar"
                  :style="{ width: it.pct + '%', background: it.color }"
                ></span>
              </span>
              <span class="lg-type-num">{{ it.count }}</span>
              <span class="lg-type-pct">{{ it.pct }}%</span>
            </div>
            <div v-if="!orderStatusBreakdown.length" class="purchase-order-analysis-empty">
              暂无订单状态数据
            </div>
          </section>

          <section class="purchase-order-analysis-section">
            <div class="purchase-order-section-title">订单类型分布</div>
            <div v-for="it in orderTypeBreakdown" :key="it.key" class="lg-type-row">
              <span class="lg-type-dot" :style="{ background: it.color }"></span>
              <span class="lg-type-label">{{ it.label }}</span>
              <span class="lg-type-bar-wrap">
                <span
                  class="lg-type-bar"
                  :style="{ width: it.pct + '%', background: it.color }"
                ></span>
              </span>
              <span class="lg-type-num">{{ it.count }}</span>
              <span class="lg-type-pct">{{ it.pct }}%</span>
            </div>
          </section>

          <section class="purchase-order-analysis-section">
            <div class="purchase-order-warning-head">
              <div class="purchase-order-section-title">待履约订单</div>
              <span class="purchase-order-warning-count">{{ pendingOrders.length }} 项</span>
            </div>
            <div v-for="item in pendingOrders" :key="item.id" class="lg-warning-item">
              <span class="lg-warning-project">{{ item.project }}</span>
              <span class="lg-warning-title">{{ item.title }}</span>
              <span class="purchase-order-warning-amount">{{ item.amount }}万</span>
            </div>
            <div v-if="!pendingOrders.length" class="lg-warning-empty">暂无待履约订单</div>
          </section>
        </div>
      </aside>
    </div>

    <!-- Add/Edit Modal -->
    <a-modal
      v-model:open="modalVisible"
      :title="modalTitle"
      :width="800"
      @ok="handleModalOk"
      @cancel="handleModalCancel"
    >
      <!-- Header Form -->
      <a-form :label-col="{ span: 6 }" :wrapper-col="{ span: 16 }" style="margin-bottom: 8px">
        <a-form-item label="项目" required>
          <a-select
            v-model:value="formData.projectId"
            placeholder="请选择项目"
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
        <a-form-item label="采购合同">
          <a-select
            v-model:value="formData.contractId"
            placeholder="请选择合同"
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
        <a-form-item label="供应商">
          <a-input :value="formPartnerName" disabled placeholder="选择合同后自动填充乙方" />
        </a-form-item>
        <a-form-item label="订单类型">
          <a-select v-model:value="formData.orderType" placeholder="请选择类型" allow-clear>
            <a-select-option value="MATERIAL">材料采购</a-select-option>
            <a-select-option value="EQUIPMENT">设备采购</a-select-option>
            <a-select-option value="SERVICE">服务采购</a-select-option>
            <a-select-option value="OTHER">其他</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="订单日期">
          <a-date-picker
            v-model:value="formData.orderDate"
            value-format="YYYY-MM-DD"
            style="width: 100%"
          />
        </a-form-item>
        <a-form-item label="交货日期">
          <a-date-picker
            v-model:value="formData.deliveryDate"
            value-format="YYYY-MM-DD"
            style="width: 100%"
          />
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
          <span style="font-weight: 600; font-size: 14px">订单明细</span>
          <a-button type="dashed" size="small" @click="handleAddItem">+ 添加明细</a-button>
        </div>

        <a-table
          :data-source="itemList"
          :pagination="false"
          row-key="key"
          size="small"
          :scroll="{ y: 250 }"
        >
          <a-table-column title="材料" width="200">
            <template #default="{ record: item, index }">
              <a-select
                :value="item.materialId"
                placeholder="请选择材料"
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
          <a-table-column title="规格" width="100">
            <template #default="{ record: item }">
              <span>{{ item.specification || '-' }}</span>
            </template>
          </a-table-column>
          <a-table-column title="单位" width="70">
            <template #default="{ record: item }">
              <span>{{ item.unit || '-' }}</span>
            </template>
          </a-table-column>
          <a-table-column title="数量" width="120">
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
          <a-table-column title="单价(元)" width="130">
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
          <a-table-column title="金额(元)" width="130">
            <template #default="{ record: item }">
              <span>{{
                Number(item.amount || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2 })
              }}</span>
            </template>
          </a-table-column>
          <a-table-column title="操作" width="76">
            <template #default="{ index }">
              <a-button type="link" size="small" danger @click="handleRemoveItem(index)"
                >删除</a-button
              >
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
.purchase-order-page {
  gap: 14px;
}

.purchase-order-page-head {
  align-items: center;
  justify-content: space-between;
  min-height: 0;
  padding: 0;
}

.purchase-order-page-meta-row {
  display: flex;
  align-items: center;
  gap: 5em;
  min-width: 0;
}

.purchase-order-breadcrumb {
  font-size: 13px;
  line-height: 20px;
}

.purchase-order-page-subtitle {
  color: var(--text-secondary);
  font-size: 13px;
  line-height: 20px;
  white-space: nowrap;
}

.purchase-order-search-bar {
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  min-height: 74px;
}

.purchase-order-search-fields {
  display: flex;
  flex: 1 1 auto;
  gap: 12px;
  align-items: center;
  min-width: 0;
}

.purchase-order-search-input {
  width: min(520px, 31vw);
  min-width: 320px;
  flex: 1 1 auto;
}

.purchase-order-search-prefix-icon {
  color: var(--text-secondary);
}

.purchase-order-search-select {
  width: 180px;
  flex: 0 0 180px;
}

.purchase-order-search-select.is-compact {
  width: 150px;
  flex-basis: 150px;
}

.purchase-order-search-actions {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  gap: 8px;
}

.purchase-order-workspace {
  align-items: stretch;
  min-height: 0;
}

.purchase-order-kpi-summary {
  display: grid;
  grid-template-columns: 1fr 1.25fr 1.15fr 1.15fr 1fr;
  gap: 0;
  overflow: hidden;
  min-height: 84px;
  background: var(--surface);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-soft);
}

.purchase-order-kpi-item {
  position: relative;
  display: grid;
  grid-template-columns: 38px minmax(0, 1fr);
  grid-template-rows: 19px 27px 8px;
  column-gap: 10px;
  align-items: center;
  min-width: 0;
  padding: 16px 18px;
  border-right: 1px solid var(--border-subtle);
}

.purchase-order-kpi-item:last-child {
  border-right: 0;
}

.purchase-order-kpi-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  color: var(--primary);
  background: var(--primary-soft);
  border-radius: var(--radius-sm);
  grid-row: 1 / span 2;
}

.purchase-order-kpi-icon.is-amount {
  color: var(--warning);
  background: var(--warning-soft);
}

.purchase-order-kpi-icon.is-pending {
  color: var(--primary);
  background: var(--surface-tint);
}

.purchase-order-kpi-icon.is-unreceived {
  color: var(--error);
  background: var(--error-soft);
}

.purchase-order-kpi-icon.is-done {
  color: var(--success);
  background: var(--success-soft);
}

.purchase-order-kpi-label {
  overflow: hidden;
  color: var(--text-secondary);
  font-size: 13px;
  font-weight: 600;
  line-height: 18px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.purchase-order-kpi-value {
  overflow: hidden;
  color: var(--text);
  font-size: 24px;
  font-weight: 800;
  line-height: 28px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.purchase-order-kpi-value small {
  margin-left: 4px;
  color: var(--text-secondary);
  font-size: 13px;
  font-weight: 600;
}

.purchase-order-kpi-progress {
  display: block;
  overflow: hidden;
  height: 4px;
  background: var(--surface-subtle);
  border-radius: var(--radius-sm);
  grid-column: 2;
}

.purchase-order-kpi-progress > span {
  display: block;
  height: 100%;
  background: var(--kpi-paid);
  border-radius: var(--radius-sm);
}

.purchase-order-kpi-item.is-unreceived .purchase-order-kpi-progress > span {
  background: var(--kpi-unpaid);
}

.purchase-order-table-panel {
  min-height: 754px;
}

.purchase-order-toolbar {
  align-items: center;
}

.purchase-order-table-title {
  color: var(--text);
  font-size: 15px;
  font-weight: 800;
}

.purchase-order-table-count,
.purchase-order-toolbar-hint {
  color: var(--text-secondary);
  font-size: 12px;
}

.purchase-order-analysis-rail {
  width: 336px;
}

.purchase-order-analysis-panel {
  display: flex;
  flex-direction: column;
  gap: 18px;
  height: 100%;
  padding: 18px;
  background: var(--surface);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-soft);
}

.purchase-order-analysis-head,
.purchase-order-warning-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.purchase-order-analysis-title {
  color: var(--text);
  font-size: 16px;
  font-weight: 800;
  line-height: 22px;
}

.purchase-order-analysis-subtitle,
.purchase-order-warning-count {
  color: var(--text-secondary);
  font-size: 12px;
}

.purchase-order-analysis-section {
  display: flex;
  flex-direction: column;
  gap: 10px;
  min-width: 0;
  padding-top: 16px;
  border-top: 1px solid var(--border-subtle);
}

.purchase-order-section-title {
  color: var(--text);
  font-size: 14px;
  font-weight: 700;
  line-height: 20px;
}

.purchase-order-analysis-empty {
  padding: 10px 0;
  color: var(--text-secondary);
  font-size: 13px;
  text-align: center;
}

.purchase-order-analysis-section :deep(.lg-type-row),
.lg-type-row {
  grid-template-columns: 9px minmax(54px, 72px) minmax(72px, 1fr) 20px 38px;
}

.purchase-order-warning-amount {
  color: var(--error);
  font-size: 12px;
  font-weight: 700;
  white-space: nowrap;
}

@media (max-width: 1200px) {
  .purchase-order-kpi-summary {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .purchase-order-kpi-item {
    border-bottom: 1px solid var(--border-subtle);
  }

  .purchase-order-analysis-rail {
    width: 100%;
  }
}

@media (max-width: 768px) {
  .purchase-order-page-meta-row,
  .purchase-order-search-bar,
  .purchase-order-search-fields {
    align-items: stretch;
    flex-direction: column;
  }

  .purchase-order-page-subtitle {
    white-space: normal;
  }

  .purchase-order-search-input,
  .purchase-order-search-select,
  .purchase-order-search-select.is-compact {
    width: 100%;
    min-width: 0;
    flex-basis: auto;
  }
}
</style>
