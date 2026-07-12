<script setup lang="ts">
import { ref, reactive, onMounted, computed, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message, Modal } from 'ant-design-vue'
import {
  ClockCircleOutlined,
  DollarOutlined,
  FileDoneOutlined,
  MoreOutlined,
  PlusOutlined,
  ReloadOutlined,
  ShoppingCartOutlined,
  WalletOutlined,
} from '@ant-design/icons-vue'
import { useReferenceStore } from '@/stores/reference'
import {
  getOrderList,
  getOrderDetail,
  createOrder,
  updateOrder,
  deleteOrder,
  getOrderItems,
  saveOrderItems,
  submitOrderForApproval,
} from '@/api/modules/purchase'
import type { MatPurchaseOrderVO, MatPurchaseOrderItemVO } from '@/types/purchase'
import {
  readPositiveIntQuery,
  readStringQuery,
  replaceListQuery,
} from '@/composables/listPageQuery'
import { useColumnSettings } from '@/composables/useColumnSettings'
import { ColumnSettingsButton, LgEmptyState } from '@/components/list-page'
import { fetchDictData, getDictLabelSync, getDictTagColorSync } from '@/utils/dict'
import PurchaseOrderAnalysisRail from './components/PurchaseOrderAnalysisRail.vue'
import PurchaseOrderModal from './components/PurchaseOrderModal.vue'
import PurchaseOrderSearchBar from './components/PurchaseOrderSearchBar.vue'

// 字典常量 - 审批状态
const APPROVAL_DRAFT = 'DRAFT'

// 字典常量 - 订单状态
const ORDER_STATUS_DRAFT = 'DRAFT'
const ORDER_STATUS_APPROVING = 'APPROVING'
const ORDER_STATUS_COMPLETED = 'COMPLETED'
const ORDER_STATUS_CANCELLED = 'CANCELLED'
const route = useRoute()
const router = useRouter()

const filter = reactive({
  projectId: undefined as string | undefined,
  contractId: undefined as string | undefined,
  partnerId: undefined as string | undefined,
  orderStatus: undefined as string | undefined,
  orderType: undefined as string | undefined,
  keyword: '',
  orderCode: '',
})
const filterVisibility = reactive({
  projectId: true,
  contractId: true,
  partnerId: true,
  orderType: true,
  orderStatus: true,
})
const filterSettingItems = [
  { key: 'projectId', label: '项目' },
  { key: 'contractId', label: '合同' },
  { key: 'partnerId', label: '供应商' },
  { key: 'orderType', label: '订单类型' },
  { key: 'orderStatus', label: '订单状态' },
] as const

const loading = ref(false)
const hasLoaded = ref(false)
const listError = ref<string | null>(null)
const tableData = ref<MatPurchaseOrderVO[]>([])
const total = ref(0)
const pageNo = ref(1)
const pageSize = ref(20)
const queryReady = ref(false)

const referenceStore = useReferenceStore()
const projectList = computed(() => referenceStore.projects ?? [])
const contractList = computed(() => referenceStore.contracts ?? [])
const materialList = computed(() => referenceStore.materials ?? [])
const supplierList = ref<{ id: string; partnerName?: string }[]>([])
const hasActiveFilters = computed(() =>
  Boolean(
    filter.projectId ||
    filter.contractId ||
    filter.partnerId ||
    filter.orderStatus ||
    filter.orderType ||
    filter.keyword,
  ),
)

const modalVisible = ref(false)
const modalTitle = ref('新建采购订单')
const editingId = ref<string | null>(null)
type ModalMode = 'create' | 'edit' | 'view'
const modalMode = ref<ModalMode>('create')
const isViewMode = computed(() => modalMode.value === 'view')
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
  PURCHASE: '采购订单',
  CONTRACT: '合同采购',
  MATERIAL: '材料采购',
  EQUIPMENT: '设备采购',
  SERVICE: '服务采购',
  OTHER: '其他',
}
const ORDER_TYPE_COLOR: Record<string, string> = {
  PURCHASE: 'blue',
  CONTRACT: 'blue',
  MATERIAL: 'blue',
  EQUIPMENT: 'cyan',
  SERVICE: 'purple',
  OTHER: 'default',
}
const ORDER_STATUS_LABEL: Record<string, string> = {
  DRAFT: '草稿',
  APPROVING: '审批中',
  APPROVED: '已审批',
  PARTIAL: '部分入库',
  IN_TRANSIT: '运输中',
  PERFORMING: '履行中',
  COMPLETED: '已完成',
  CANCELLED: '已取消',
}
const ORDER_STATUS_COLOR: Record<string, string> = {
  DRAFT: 'default',
  APPROVING: 'processing',
  APPROVED: 'success',
  PARTIAL: 'warning',
  IN_TRANSIT: 'blue',
  PERFORMING: 'blue',
  COMPLETED: 'success',
  CANCELLED: 'error',
}
const ORDER_STATUS_DICT = 'purchase_order_status'

function orderStatusLabel(status: string | undefined): string {
  return getDictLabelSync(ORDER_STATUS_DICT, status ?? '', ORDER_STATUS_LABEL)
}

function orderStatusColor(status: string | undefined): string {
  return getDictTagColorSync(ORDER_STATUS_DICT, status ?? '', ORDER_STATUS_COLOR)
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
  listError.value = null
  try {
    await syncRouteQuery()
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
    listError.value = '请检查筛选条件或网络状态后重试。'
    message.error('加载采购订单列表失败')
  } finally {
    hasLoaded.value = true
    loading.value = false
  }
}

function hydrateFromRouteQuery() {
  filter.projectId = readStringQuery(route.query.projectId)
  filter.contractId = readStringQuery(route.query.contractId)
  filter.partnerId = readStringQuery(route.query.partnerId)
  filter.orderStatus = readStringQuery(route.query.orderStatus)
  filter.orderType = readStringQuery(route.query.orderType)
  filter.keyword = readStringQuery(route.query.keyword) ?? ''
  filter.orderCode = filter.keyword
  pageNo.value = readPositiveIntQuery(route.query.pageNo, 1)
  pageSize.value = readPositiveIntQuery(route.query.pageSize, 20)
  queryReady.value = true
}

async function syncRouteQuery() {
  if (!queryReady.value) return
  const nextQuery = replaceListQuery(
    route.query,
    {
      projectId: filter.projectId,
      contractId: filter.contractId,
      partnerId: filter.partnerId,
      orderStatus: filter.orderStatus,
      orderType: filter.orderType,
      keyword: filter.keyword || undefined,
      pageNo: pageNo.value,
      pageSize: pageSize.value,
    },
    [
      'projectId',
      'contractId',
      'partnerId',
      'orderStatus',
      'orderType',
      'keyword',
      'pageNo',
      'pageSize',
    ],
  )
  await router.replace({ path: route.path, query: nextQuery })
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
  referenceStore.fetchContracts({
    contractType: 'PURCHASE',
    contractStatus: 'PERFORMING',
    approvalStatus: 'APPROVED',
  })
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

function toggleFilterVisibility(key: (typeof filterSettingItems)[number]['key']) {
  filterVisibility[key] = !filterVisibility[key]
}

function handleAdd() {
  modalMode.value = 'create'
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
  modalMode.value = 'edit'
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
    return
  }
  modalVisible.value = true
}

async function handleView(record: MatPurchaseOrderVO) {
  await handleEdit(record)
  modalMode.value = 'view'
  modalTitle.value = '查看采购订单'
}

async function openBusinessIdFromQuery() {
  const value = route.query.businessId
  const businessId = Array.isArray(value) ? value[0] : value
  if (!businessId) return

  try {
    const record = await getOrderDetail(String(businessId))
    await handleView(record)
  } catch (e: unknown) {
    console.error(e)
    message.error('业务单据加载失败，请稍后重试')
  } finally {
    const nextQuery = { ...route.query }
    delete nextQuery.businessId
    await router.replace({ path: route.path, query: nextQuery })
  }
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
        message.error('提交审批失败，请稍后重试')
      }
    },
  })
}

// --- Line items management ---
function handleAddItem() {
  if (isViewMode.value) return
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
  if (isViewMode.value) return
  itemList.value.splice(index, 1)
}

function handleMaterialChange(index: number, materialId: string | undefined) {
  if (isViewMode.value) return
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
  if (isViewMode.value) return
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
    .filter(
      (r) => r.orderStatus !== ORDER_STATUS_COMPLETED && r.orderStatus !== ORDER_STATUS_CANCELLED,
    )
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
    label: orderStatusLabel(key),
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
    .filter(
      (row) =>
        row.orderStatus !== ORDER_STATUS_COMPLETED && row.orderStatus !== ORDER_STATUS_CANCELLED,
    )
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
  filter.partnerId = undefined
  if (v) {
    referenceStore.fetchContracts({
      projectId: v,
      contractType: 'PURCHASE',
      contractStatus: 'PERFORMING',
      approvalStatus: 'APPROVED',
    })
  } else {
    referenceStore.fetchContracts({
      contractType: 'PURCHASE',
      contractStatus: 'PERFORMING',
      approvalStatus: 'APPROVED',
    })
  }
  handleSearch()
}

function handleModalProjectChange(v: string) {
  formData.contractId = undefined
  formData.partnerId = undefined
  referenceStore.fetchContracts({
    projectId: v,
    contractType: 'PURCHASE',
    contractStatus: 'PERFORMING',
    approvalStatus: 'APPROVED',
  })
}

async function loadSuppliers() {
  try {
    supplierList.value = await referenceStore.fetchPartners({ partnerType: 'SUPPLIER' })
  } catch (e: unknown) {
    console.error(e)
    supplierList.value = []
  }
}

const showEmptyState = computed(() => hasLoaded.value && !loading.value && !tableData.value.length)

onMounted(() => {
  hydrateFromRouteQuery()
  fetchDictData(ORDER_STATUS_DICT)
  referenceStore.fetchProjects()
  referenceStore.fetchContracts({
    ...(filter.projectId ? { projectId: filter.projectId } : {}),
    contractType: 'PURCHASE',
    contractStatus: 'PERFORMING',
    approvalStatus: 'APPROVED',
  })
  loadSuppliers()
  referenceStore.fetchMaterials({ status: 'ENABLE' })
  fetchData()
  openBusinessIdFromQuery()
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
      </div>
    </div>

    <div class="lg-grid purchase-order-workspace">
      <div class="lg-left">
        <!-- KPI 横条 -->
        <div class="lg-kpi-strip purchase-order-kpi-summary" aria-label="采购订单关键指标">
          <div class="purchase-order-kpi-item">
            <span class="purchase-order-kpi-icon is-total"><ShoppingCartOutlined /></span>
            <span class="purchase-order-kpi-content">
              <span class="purchase-order-kpi-label">采购订单数</span>
              <span class="purchase-order-kpi-value">{{ kpiOrderTotal }} <small>单</small></span>
            </span>
          </div>
          <div class="purchase-order-kpi-item is-wide">
            <span class="purchase-order-kpi-icon is-amount"><DollarOutlined /></span>
            <span class="purchase-order-kpi-content">
              <span class="purchase-order-kpi-label">已下单金额</span>
              <span class="purchase-order-kpi-value"
                >{{ fmtWan(kpiOrderedAmount) }} <small>万元</small></span
              >
            </span>
          </div>
          <div class="purchase-order-kpi-item is-progress">
            <span class="purchase-order-kpi-icon is-pending"><ClockCircleOutlined /></span>
            <span class="purchase-order-kpi-content">
              <span class="purchase-order-kpi-label">待审批</span>
              <span class="purchase-order-kpi-value">{{ kpiOrderPending }} <small>单</small></span>
              <span class="purchase-order-kpi-progress">
                <span :style="{ width: kpiPct(kpiOrderPending, kpiMax.totalCount) + '%' }"></span>
              </span>
            </span>
          </div>
          <div class="purchase-order-kpi-item is-progress is-unreceived">
            <span class="purchase-order-kpi-icon is-unreceived"><WalletOutlined /></span>
            <span class="purchase-order-kpi-content">
              <span class="purchase-order-kpi-label">未入库金额</span>
              <span class="purchase-order-kpi-value"
                >{{ fmtWan(kpiUnreceived) }} <small>万元</small></span
              >
              <span class="purchase-order-kpi-progress">
                <span :style="{ width: kpiPct(kpiUnreceived, kpiMax.totalAmount) + '%' }"></span>
              </span>
            </span>
          </div>
          <div class="purchase-order-kpi-item">
            <span class="purchase-order-kpi-icon is-done"><FileDoneOutlined /></span>
            <span class="purchase-order-kpi-content">
              <span class="purchase-order-kpi-label">已完成订单</span>
              <span class="purchase-order-kpi-value">
                {{ tableData.filter((r) => r.orderStatus === ORDER_STATUS_COMPLETED).length }}
                <small>单</small>
              </span>
            </span>
          </div>
        </div>

        <PurchaseOrderSearchBar
          class="purchase-order-search-bar"
          :filter="filter"
          :filter-visibility="filterVisibility"
          :filter-setting-items="filterSettingItems"
          :project-list="projectList"
          :contract-list="contractList"
          :supplier-list="supplierList"
          :order-type-label="ORDER_TYPE_LABEL"
          :order-status-label-map="ORDER_STATUS_LABEL"
          :order-status-label="orderStatusLabel"
          :on-project-change="onFilterProjectChange"
          :on-search="handleSearch"
          :on-reset="handleReset"
          :on-toggle-filter-visibility="toggleFilterVisibility"
        />

        <main class="lg-list-table-panel purchase-order-table-panel">
          <!-- 工具栏 -->
          <div class="lg-toolbar purchase-order-toolbar">
            <div class="lg-toolbar-left">
              <div class="purchase-order-table-heading">
                <span class="purchase-order-table-title">采购订单明细</span>
                <span class="purchase-order-table-count">共 {{ total }} 条</span>
              </div>
            </div>
            <div class="lg-toolbar-right">
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
          </div>

          <!-- 表格 -->
          <div class="lg-table-wrap">
            <div v-if="listError" class="purchase-order-list-feedback">
              <a-result status="error" title="采购订单列表加载失败" :sub-title="listError">
                <template #extra>
                  <a-button type="primary" @click="fetchData">重试</a-button>
                </template>
              </a-result>
            </div>
            <div v-else-if="showEmptyState" class="purchase-order-list-feedback">
              <LgEmptyState description="暂无符合条件的采购订单">
                <a-button v-if="hasActiveFilters" @click="handleReset">清空筛选</a-button>
                <a-button v-else type="primary" @click="handleAdd">新建订单</a-button>
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
                <a-tag :color="orderStatusColor(row.orderStatus)">
                  {{ orderStatusLabel(row.orderStatus) }}
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
      <PurchaseOrderAnalysisRail
        class="purchase-order-analysis-rail"
        :focus-amount="fmtWan(kpiUnreceived)"
        :order-status-breakdown="orderStatusBreakdown"
        :order-type-breakdown="orderTypeBreakdown"
        :pending-orders="pendingOrders"
        :on-refresh="fetchData"
      />
    </div>

    <PurchaseOrderModal
      v-model:open="modalVisible"
      :title="modalTitle"
      :is-view-mode="isViewMode"
      :form-data="formData"
      :form-partner-name="formPartnerName"
      :project-list="projectList"
      :contract-list="contractList"
      :material-list="materialList"
      :item-list="itemList"
      :items-total-amount="itemsTotalAmount"
      :on-project-change="handleModalProjectChange"
      :on-contract-change="onContractChange"
      :on-add-item="handleAddItem"
      :on-remove-item="handleRemoveItem"
      :on-material-change="handleMaterialChange"
      :on-item-qty-change="handleItemQtyChange"
      :on-item-price-change="handleItemPriceChange"
      @ok="handleModalOk"
      @cancel="handleModalCancel"
    />
  </div>
</template>

<style scoped>
.purchase-order-page {
}

.purchase-order-page-head {
  min-height: 0;
  padding: 0;
  background: transparent;
  border: 0;
  border-radius: 0;
  box-shadow: none;
}

.purchase-order-page-meta-row {
  display: flex;
  align-items: center;
  width: 100%;
  min-width: 0;
}

.purchase-order-breadcrumb {
  margin-bottom: 0;
  font-size: 13px;
  line-height: 20px;
}

.purchase-order-workspace {
}

.purchase-order-page .lg-left {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-height: 0;
}

.purchase-order-page .purchase-order-kpi-summary {
  display: grid;
  flex: 0 0 auto;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 0;
  margin: 0;
  overflow: hidden;
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  box-shadow: 0 6px 18px rgba(15, 23, 42, 0.04);
}

.purchase-order-page .lg-left > .purchase-order-search-bar,
.purchase-order-page .lg-left > .purchase-order-kpi-summary {
  flex: 0 0 auto;
  align-self: auto;
}

.purchase-order-page .purchase-order-kpi-item {
  display: grid;
  grid-template-columns: 30px minmax(0, 1fr);
  column-gap: 6px;
  align-items: center;
  min-width: 0;
  padding: 16px 12px;
  border-right: 1px solid #edf1f5;
}

.purchase-order-page .purchase-order-kpi-item:last-child {
  border-right: 0;
}

.purchase-order-kpi-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  flex: 0 0 auto;
  color: var(--primary);
  background: var(--primary-soft);
  border-radius: var(--radius-sm);
}

.purchase-order-kpi-content {
  display: grid;
  grid-template-rows: 18px 28px 4px;
  align-content: center;
  row-gap: 4px;
  min-width: 0;
}

.purchase-order-kpi-item:not(.is-progress) .purchase-order-kpi-content::after {
  content: '';
  display: block;
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
  display: block;
  min-width: 0;
  color: var(--text-secondary);
  font-size: 13px;
  font-weight: 600;
  line-height: 18px;
}

.purchase-order-kpi-value {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: 4px;
  min-width: 0;
  color: var(--text);
  font-size: 24px;
  font-variant-numeric: tabular-nums;
  font-weight: 800;
  line-height: 28px;
}

.purchase-order-page .purchase-order-kpi-item.is-wide .purchase-order-kpi-value,
.purchase-order-page .purchase-order-kpi-item.is-unreceived .purchase-order-kpi-value {
  font-size: 22px;
}

.purchase-order-kpi-value small {
  color: var(--text-secondary);
  font-size: 13px;
  font-weight: 600;
}

.purchase-order-kpi-progress {
  display: block;
  overflow: hidden;
  width: 100%;
  height: 4px;
  background: var(--surface-subtle);
  border-radius: var(--radius-sm);
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

.purchase-order-toolbar {
  align-items: center;
  border-bottom: 1px solid var(--border-subtle);
}

.purchase-order-table-heading {
  display: grid;
  gap: 2px;
  margin-right: 4px;
}

.purchase-order-table-title {
  color: var(--text);
  font-size: 15px;
  font-weight: 800;
}

.purchase-order-table-count {
  color: var(--text-secondary);
  font-size: 12px;
}

.purchase-order-table-panel .lg-table-wrap {
  flex: 1;
  min-height: 0;
}

.purchase-order-list-feedback {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 420px;
  padding: 24px;
}

@media (max-width: 1200px) {
  .purchase-order-page .purchase-order-kpi-summary {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  .purchase-order-page .purchase-order-kpi-item {
    border-bottom: 1px solid #edf1f5;
  }

  .purchase-order-page .purchase-order-kpi-summary > .purchase-order-kpi-item:nth-child(3n) {
    border-right: 0;
  }

  .purchase-order-page
    .purchase-order-kpi-summary
    > .purchase-order-kpi-item:nth-last-child(-n + 2) {
    border-bottom: 0;
  }
}

@media (max-width: 768px) {
  .purchase-order-page .purchase-order-kpi-summary {
    grid-template-columns: 1fr;
  }

  .purchase-order-page .purchase-order-kpi-item {
    min-height: 88px;
    border-right: 0;
    border-bottom: 1px solid #edf1f5;
  }

  .purchase-order-page .purchase-order-kpi-summary > .purchase-order-kpi-item:last-child {
    border-bottom: 0;
  }
}
</style>
