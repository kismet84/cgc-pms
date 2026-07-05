<script setup lang="ts">
import { ref, reactive, onMounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message, Modal } from 'ant-design-vue'
import {
  CheckCircleOutlined,
  ClockCircleOutlined,
  FileDoneOutlined,
  MoreOutlined,
  PlusOutlined,
  ReloadOutlined,
  SearchOutlined,
  ShoppingCartOutlined,
  WalletOutlined,
} from '@ant-design/icons-vue'
import {
  getPurchaseRequestList,
  getPurchaseRequestDetail,
  createPurchaseRequest,
  updatePurchaseRequest,
  deletePurchaseRequest,
  getPurchaseRequestItems,
  savePurchaseRequestItems,
  submitPurchaseRequest,
} from '@/api/modules/inventory'
import { useReferenceStore } from '@/stores/reference'
import type { PurchaseRequestVO, PurchaseRequestItemVO } from '@/types/inventory'
import { getContractLedger } from '@/api/modules/contract'
import type { ContractVO } from '@/types/contract'
import ApprovalStatusTag from '@/components/ApprovalStatusTag.vue'
import { fetchDictData, getDictLabelSync, getDictTagColorSync } from '@/utils/dict'

// 字典常量 - 审批状态
const APPROVAL_DRAFT = 'DRAFT'
const APPROVAL_APPROVING = 'APPROVING'

// 字典常量 - 业务状态
const STATUS_DRAFT = 'DRAFT'
import { useColumnSettings } from '@/composables/useColumnSettings'
import { ColumnSettingsButton } from '@/components/list-page'

const route = useRoute()
const router = useRouter()

const filter = reactive({
  projectId: undefined as string | undefined,
  approvalStatus: undefined as string | undefined,
  status: undefined as string | undefined,
  requestCode: '',
  keyword: '',
})

const loading = ref(false)
const tableData = ref<PurchaseRequestVO[]>([])
const total = ref(0)
const pageNo = ref(1)
const pageSize = ref(20)

const referenceStore = useReferenceStore()
const projectList = computed(() => referenceStore.projects ?? [])
const contractList = ref<ContractVO[]>([])
const materialList = computed(() => referenceStore.materials ?? [])

const modalVisible = ref(false)
const modalTitle = ref('新建采购申请')
const editingId = ref<string | null>(null)
const submitting = ref(false)
const modalDirty = ref(false)
const formData = reactive<Partial<PurchaseRequestVO>>({
  projectId: undefined,
  contractId: undefined,
  remark: '',
})

// Line items for the modal
const itemList = ref<(Partial<PurchaseRequestItemVO> & { key: number })[]>([])
const keySeq = ref(0)

const STATUS_LABEL: Record<string, string> = {
  DRAFT: '草稿',
  CONVERTED: '已转PO',
}
const STATUS_COLOR: Record<string, string> = {
  DRAFT: 'default',
  CONVERTED: 'cyan',
}
const STATUS_DICT = 'purchase_request_status'
const APPROVAL_STATUS_DICT = 'approval_status'

function businessStatusLabel(status: string | undefined): string {
  return getDictLabelSync(STATUS_DICT, status ?? '', STATUS_LABEL)
}

function businessStatusColor(status: string | undefined): string {
  return getDictTagColorSync(STATUS_DICT, status ?? '', STATUS_COLOR)
}

const itemColumns = [
  {
    title: '物料',
    dataIndex: 'material',
    key: 'material',
    width: 240,
    customHeaderCell: () => ({
      style: { width: '240px', minWidth: '240px', maxWidth: '240px' },
    }),
    customCell: () => ({
      style: { width: '240px', minWidth: '240px', maxWidth: '240px' },
    }),
  },
  { title: '单位', dataIndex: 'unit', key: 'unit', width: 120 },
  { title: '数量', dataIndex: 'quantity', key: 'quantity', width: 120 },
  { title: '计划日期', dataIndex: 'plannedDate', key: 'plannedDate', width: 160 },
  { title: '备注', dataIndex: 'remark', key: 'remark', width: 160 },
  { title: '操作', key: 'action', width: 76 },
]

const filterOption = (input: string, option: { label?: string }) =>
  option.label?.toLowerCase().includes(input.toLowerCase())

const gridColumns = computed(() => [
  { field: 'requestCode', title: '申请编号', minWidth: 150, slots: { default: 'requestCode' } },
  { field: 'projectName', title: '所属项目', minWidth: 150, ellipsis: true },
  { field: 'contractName', title: '关联合同', minWidth: 150, ellipsis: true },
  { field: 'approvalStatus', title: '审批状态', width: 108, slots: { default: 'approvalStatus' } },
  { field: 'status', title: '业务状态', width: 108, slots: { default: 'status' } },
  { field: 'createdBy', title: '创建人', width: 90 },
  { field: 'createdTime', title: '创建时间', width: 140 },
  { title: '操作', width: 76, slots: { default: 'ops' } },
])

const {
  visibleColumns: visibleGridColumns,
  columnSettings,
  colVisible,
  toggleCol,
} = useColumnSettings('purchase_request_cols_v2', gridColumns, {
  createdBy: false,
  createdTime: false,
})

async function fetchData() {
  loading.value = true
  try {
    const res = await getPurchaseRequestList({
      pageNum: pageNo.value,
      pageSize: pageSize.value,
      projectId: filter.projectId,
      approvalStatus: filter.approvalStatus,
      status: filter.status,
      requestCode: filter.keyword || filter.requestCode || undefined,
    })
    tableData.value = res.records
    total.value = Number(res.total ?? 0)
  } catch (e: unknown) {
    console.error(e)
    tableData.value = []
    total.value = 0
    message.error('加载采购申请列表失败，请稍后重试')
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
  filter.approvalStatus = undefined
  filter.status = undefined
  filter.requestCode = ''
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
  modalTitle.value = '新建采购申请'
  editingId.value = null
  Object.assign(formData, {
    projectId: undefined,
    contractId: undefined,
    remark: '',
  })
  itemList.value = []
  keySeq.value = 0
  contractList.value = []
  modalDirty.value = false
  modalVisible.value = true
}

async function handleEdit(record: PurchaseRequestVO) {
  modalTitle.value = '编辑采购申请'
  editingId.value = record.id
  Object.assign(formData, {
    projectId: record.projectId,
    contractId: record.contractId,
    remark: record.remark,
  })
  itemList.value = []
  keySeq.value = 0
  await loadContractsByProject(record.projectId)
  // Load existing items
  try {
    const items = await getPurchaseRequestItems(record.id)
    itemList.value = items.map((item) => ({
      ...item,
      key: keySeq.value++,
    }))
  } catch (e: unknown) {
    console.error(e)
    message.error('加载明细失败')
    itemList.value = []
  }
  modalDirty.value = false
  modalVisible.value = true
}

async function handleView(record: PurchaseRequestVO) {
  await handleEdit(record)
  modalTitle.value = '查看采购申请'
}

async function openBusinessIdFromQuery() {
  const value = route.query.businessId
  const businessId = Array.isArray(value) ? value[0] : value
  if (!businessId) return

  try {
    const record = await getPurchaseRequestDetail(String(businessId))
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

async function loadContractsByProject(projectId?: string) {
  if (!projectId) {
    contractList.value = []
    return
  }
  try {
    const res = await getContractLedger({
      projectId,
      contractType: 'PURCHASE',
      pageNo: 1,
      pageSize: 200,
    })
    contractList.value = res.records
  } catch (e: unknown) {
    console.error(e)
    contractList.value = []
  }
}

async function handleProjectChange(projectId?: string) {
  formData.projectId = projectId
  formData.contractId = undefined
  modalDirty.value = true
  await loadContractsByProject(projectId)
}

function handleDelete(record: PurchaseRequestVO) {
  Modal.confirm({
    title: '确认删除',
    content: `确定要删除采购申请"${record.requestCode}"吗？`,
    okText: '确定',
    cancelText: '取消',
    onOk: async () => {
      try {
        await deletePurchaseRequest(record.id)
        message.success('删除成功')
        fetchData()
      } catch (e: unknown) {
        console.error(e)
        message.error('删除失败，请稍后重试')
      }
    },
  })
}

function handleSubmit(record: PurchaseRequestVO) {
  Modal.confirm({
    title: '确认提交审批',
    content: `确定要提交采购申请"${record.requestCode}"进行审批吗？提交后不可编辑。`,
    okText: '确定提交',
    cancelText: '取消',
    onOk: async () => {
      try {
        await submitPurchaseRequest(record.id)
        message.success('已提交审批')
        fetchData()
      } catch (e: unknown) {
        console.error(e)
        message.error('提交失败，请稍后重试')
      }
    },
  })
}

// --- Line items management ---
function handleAddItem() {
  modalDirty.value = true
  itemList.value.push({
    key: keySeq.value++,
    materialId: '',
    materialName: '',
    quantity: '0',
    unit: '',
    plannedDate: undefined,
    remark: '',
  })
}

function handleRemoveItem(key: number) {
  modalDirty.value = true
  const idx = itemList.value.findIndex((i) => i.key === key)
  if (idx !== -1) {
    itemList.value.splice(idx, 1)
  }
}

function handleMaterialClear(key: number) {
  modalDirty.value = true
  const item = itemList.value.find((i) => i.key === key)
  if (!item) return
  item.materialId = ''
  item.materialName = ''
  item.unit = ''
}

function handleMaterialChange(key: number, materialId: string | undefined) {
  modalDirty.value = true
  const item = itemList.value.find((i) => i.key === key)
  if (!item) return
  if (!materialId) {
    item.materialId = ''
    item.materialName = ''
    item.unit = ''
    return
  }
  const material = materialList.value.find((m) => m.id === materialId)
  if (material) {
    item.materialId = material.id
    item.materialName = material.materialName
    item.unit = material.unit || ''
  }
}

const itemsCount = computed(() => itemList.value.length)

async function handleModalOk() {
  if (submitting.value) return

  // --- validation ---
  if (!formData.projectId) {
    message.warning('请选择项目')
    return
  }
  if (itemList.value.length < 1) {
    message.warning('请至少添加一个物料明细')
    return
  }
  for (const item of itemList.value) {
    if (!item.materialId && !item.materialName) {
      message.warning('请为所有明细选择物料或输入物料名称')
      return
    }
    if (!item.quantity || Number(item.quantity) <= 0) {
      message.warning('物料数量必须大于 0')
      return
    }
  }

  submitting.value = true
  let requestId = ''
  try {
    if (editingId.value) {
      await updatePurchaseRequest(editingId.value, formData)
      requestId = editingId.value
    } else {
      requestId = await createPurchaseRequest(formData)
    }

    // Save line items
    if (itemList.value.length > 0) {
      const items = itemList.value.map((item) => ({
        ...item,
        requestId: requestId,
      }))
      await savePurchaseRequestItems(requestId, items)
    }

    message.success(editingId.value ? '更新成功' : '创建成功')
    modalVisible.value = false
    fetchData()
  } catch (e: unknown) {
    console.error(e)
    // Clean up orphaned PR when creating new and header saved but items failed
    if (!editingId.value && requestId) {
      try {
        await deletePurchaseRequest(requestId)
      } catch {
        // best-effort cleanup
      }
    }
    message.error('操作失败，请稍后重试')
  } finally {
    submitting.value = false
  }
}

function handleModalCancel() {
  if (submitting.value) return
  if (modalDirty.value) {
    Modal.confirm({
      title: '放弃编辑？',
      content: '当前表单有未保存的修改，确定关闭吗？',
      okText: '确定关闭',
      okType: 'danger',
      cancelText: '继续编辑',
      onOk: () => {
        modalVisible.value = false
      },
    })
    return
  }
  modalVisible.value = false
}

function getPopupContainer() {
  return document.body
}

const kpiReqTotal = computed(() => tableData.value.length)
const kpiReqPending = computed(
  () =>
    tableData.value.filter((r) => r.approvalStatus === APPROVAL_DRAFT || r.approvalStatus === APPROVAL_APPROVING)
      .length,
)
const kpiReqConverted = computed(
  () => tableData.value.filter((r) => r.status === 'CONVERTED').length,
)
const recentRequests = computed(() => tableData.value.slice(0, 4))
const kpiReqDraft = computed(() => tableData.value.filter((r) => r.status === STATUS_DRAFT).length)
const kpiMax = computed(() => ({
  totalCount: Math.max(total.value, tableData.value.length, 1),
}))
function kpiPct(value: number, max: number): number {
  if (max === 0) return 0
  return Math.min(Math.round((value / max) * 100), 100)
}
const statusBreakdown = computed(() => {
  const m: Record<string, number> = {}
  tableData.value.forEach((r) => {
    const key = r.status || STATUS_DRAFT
    m[key] = (m[key] || 0) + 1
  })
  return Object.entries(m).map(([key, count]) => ({
    key,
    label: businessStatusLabel(key),
    count,
    pct: kpiPct(count, kpiMax.value.totalCount),
    color: key === 'CONVERTED' ? '#31c48d' : '#f59e0b',
  }))
})
const approvalBreakdown = computed(() => {
  const m: Record<string, number> = {}
  tableData.value.forEach((r) => {
    const key = r.approvalStatus || APPROVAL_DRAFT
    m[key] = (m[key] || 0) + 1
  })
  const labels: Record<string, string> = {
    DRAFT: '草稿',
    APPROVING: '审批中',
    APPROVED: '已通过',
    REJECTED: '已驳回',
  }
  const colors: Record<string, string> = {
    DRAFT: '#94a3b8',
    APPROVING: '#2563eb',
    APPROVED: '#31c48d',
    REJECTED: '#ef4444',
  }
  return Object.entries(m).map(([key, count]) => ({
    key,
    label: getDictLabelSync(APPROVAL_STATUS_DICT, key, labels),
    count,
    pct: kpiPct(count, kpiMax.value.totalCount),
    color: colors[key] ?? '#94a3b8',
  }))
})

onMounted(() => {
  fetchDictData(STATUS_DICT)
  fetchDictData(APPROVAL_STATUS_DICT)
  referenceStore.fetchProjects()
  referenceStore.fetchMaterials()
  fetchData()
  openBusinessIdFromQuery()
})
</script>

<template>
  <div class="lg-list-page lg-page app-page purchase-request-page">
    <div class="lg-page-head purchase-request-page-head">
      <div class="purchase-request-page-meta-row">
        <a-breadcrumb class="purchase-request-breadcrumb">
          <a-breadcrumb-item>库存管理</a-breadcrumb-item>
          <a-breadcrumb-item>采购申请</a-breadcrumb-item>
        </a-breadcrumb>
        <span class="purchase-request-page-subtitle">按项目跟踪采购申请、审批状态与转单进度。</span>
      </div>
    </div>

    <!-- 搜索栏 -->
    <div class="lg-search-bar purchase-request-search-bar">
      <div class="purchase-request-search-fields">
        <a-input
          v-model:value="filter.keyword"
          class="purchase-request-search-input"
          placeholder="搜索申请编号"
          allow-clear
          size="large"
          @press-enter="handleSearch"
        >
          <template #prefix
            ><SearchOutlined class="purchase-request-search-prefix-icon"
          /></template>
        </a-input>
        <a-select
          v-model:value="filter.projectId"
          class="purchase-request-search-select"
          placeholder="全部项目"
          allow-clear
          size="large"
          @change="handleSearch"
        >
          <a-select-option v-for="p in projectList" :key="p.id" :value="p.id">
            {{ p.projectName }}
          </a-select-option>
        </a-select>
        <a-select
          v-model:value="filter.approvalStatus"
          class="purchase-request-search-select is-compact"
          placeholder="审批状态"
          allow-clear
          size="large"
          @change="handleSearch"
        >
          <a-select-option value="DRAFT">草稿</a-select-option>
          <a-select-option value="APPROVING">审批中</a-select-option>
          <a-select-option value="APPROVED">已通过</a-select-option>
          <a-select-option value="REJECTED">已驳回</a-select-option>
        </a-select>
        <a-select
          v-model:value="filter.status"
          class="purchase-request-search-select is-compact"
          placeholder="业务状态"
          allow-clear
          size="large"
          @change="handleSearch"
        >
          <a-select-option value="DRAFT">草稿</a-select-option>
          <a-select-option value="CONVERTED">已转PO</a-select-option>
        </a-select>
      </div>
      <div class="purchase-request-search-actions">
        <a-button type="primary" size="large" @click="handleSearch">查询</a-button>
        <a-button size="large" @click="handleReset">
          <template #icon><ReloadOutlined /></template>
          重置
        </a-button>
      </div>
    </div>

    <div class="lg-grid purchase-request-workspace">
      <div class="lg-left">
        <!-- KPI 横条 -->
        <div class="purchase-request-kpi-summary" aria-label="采购申请关键指标">
          <div class="purchase-request-kpi-item">
            <span class="purchase-request-kpi-icon is-total"><ShoppingCartOutlined /></span>
            <span class="purchase-request-kpi-label">申请总数</span>
            <span class="purchase-request-kpi-value">{{ kpiReqTotal }} <small>单</small></span>
          </div>
          <div class="purchase-request-kpi-item is-wide">
            <span class="purchase-request-kpi-icon is-draft"><FileDoneOutlined /></span>
            <span class="purchase-request-kpi-label">草稿申请</span>
            <span class="purchase-request-kpi-value">{{ kpiReqDraft }} <small>单</small></span>
          </div>
          <div class="purchase-request-kpi-item is-progress">
            <span class="purchase-request-kpi-icon is-pending"><ClockCircleOutlined /></span>
            <span class="purchase-request-kpi-label">待审批</span>
            <span class="purchase-request-kpi-value">{{ kpiReqPending }} <small>单</small></span>
            <span class="purchase-request-kpi-progress">
              <span :style="{ width: kpiPct(kpiReqPending, kpiMax.totalCount) + '%' }"></span>
            </span>
          </div>
          <div class="purchase-request-kpi-item is-progress is-converted">
            <span class="purchase-request-kpi-icon is-converted"><CheckCircleOutlined /></span>
            <span class="purchase-request-kpi-label">已转PO</span>
            <span class="purchase-request-kpi-value">{{ kpiReqConverted }} <small>单</small></span>
            <span class="purchase-request-kpi-progress">
              <span :style="{ width: kpiPct(kpiReqConverted, kpiMax.totalCount) + '%' }"></span>
            </span>
          </div>
          <div class="purchase-request-kpi-item">
            <span class="purchase-request-kpi-icon is-recent"><WalletOutlined /></span>
            <span class="purchase-request-kpi-label">近期申请</span>
            <span class="purchase-request-kpi-value"
              >{{ recentRequests.length }} <small>单</small></span
            >
          </div>
        </div>

        <main class="lg-list-table-panel purchase-request-table-panel">
          <!-- 工具栏 -->
          <div class="lg-toolbar purchase-request-toolbar">
            <div class="lg-toolbar-left">
              <span class="purchase-request-table-title">采购申请</span>
              <span class="purchase-request-table-count">共 {{ total }} 条</span>
              <ColumnSettingsButton
                :columns="columnSettings"
                :visible="colVisible"
                @toggle="toggleCol"
              />
              <a-button type="primary" @click="handleAdd">
                <template #icon><PlusOutlined /></template>
                新建申请
              </a-button>
              <a-button @click="fetchData">
                <template #icon><ReloadOutlined /></template>
                刷新
              </a-button>
            </div>
            <div class="lg-toolbar-right">
              <span class="purchase-request-toolbar-hint">固定表头 / 审批状态 / 行操作展开</span>
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
              <template #requestCode="{ row }">
                <a-button class="purchase-request-code-link" type="link" @click="handleView(row)">
                  {{ row.requestCode || '-' }}
                </a-button>
              </template>
              <template #approvalStatus="{ row }">
                <ApprovalStatusTag :status="row.approvalStatus" />
              </template>
              <template #status="{ row }">
                <a-tag :color="businessStatusColor(row.status)">
                  {{ businessStatusLabel(row.status) }}
                </a-tag>
              </template>
              <template #ops="{ row }">
                <a-dropdown :trigger="['click']">
                  <a-button class="lg-row-action-trigger" size="small" type="text">
                    <MoreOutlined />
                  </a-button>
                  <template #overlay>
                    <a-menu>
                      <a-menu-item @click="handleEdit(row)">编辑</a-menu-item>
                      <a-menu-item v-if="row.approvalStatus === APPROVAL_DRAFT" @click="handleSubmit(row)">
                        提交审批
                      </a-menu-item>
                      <a-menu-item danger @click="handleDelete(row)">删除</a-menu-item>
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

      <aside class="lg-analysis-rail purchase-request-analysis-rail" aria-label="采购申请辅助分析">
        <div class="purchase-request-analysis-panel">
          <header class="purchase-request-analysis-head">
            <div>
              <div class="purchase-request-analysis-title">申请分析</div>
              <div class="purchase-request-analysis-subtitle">业务状态、审批状态与近期申请</div>
            </div>
            <a-button type="link" size="small" @click="fetchData">刷新</a-button>
          </header>

          <section class="purchase-request-analysis-section">
            <div class="purchase-request-section-title">业务状态分布</div>
            <div v-for="item in statusBreakdown" :key="item.key" class="lg-type-row">
              <span class="lg-type-dot" :style="{ background: item.color }"></span>
              <span class="lg-type-label">{{ item.label }}</span>
              <span class="lg-type-bar-wrap">
                <span
                  class="lg-type-bar"
                  :style="{ width: item.pct + '%', background: item.color }"
                ></span>
              </span>
              <span class="lg-type-num">{{ item.count }}</span>
              <span class="lg-type-pct">{{ item.pct }}%</span>
            </div>
            <div v-if="!statusBreakdown.length" class="purchase-request-analysis-empty">
              暂无业务状态数据
            </div>
          </section>

          <section class="purchase-request-analysis-section">
            <div class="purchase-request-section-title">审批状态</div>
            <div v-for="item in approvalBreakdown" :key="item.key" class="lg-type-row">
              <span class="lg-type-dot" :style="{ background: item.color }"></span>
              <span class="lg-type-label">{{ item.label }}</span>
              <span class="lg-type-bar-wrap">
                <span
                  class="lg-type-bar"
                  :style="{ width: item.pct + '%', background: item.color }"
                ></span>
              </span>
              <span class="lg-type-num">{{ item.count }}</span>
              <span class="lg-type-pct">{{ item.pct }}%</span>
            </div>
          </section>

          <section class="purchase-request-analysis-section">
            <div class="purchase-request-warning-head">
              <div class="purchase-request-section-title">近期申请</div>
              <span class="purchase-request-warning-count">{{ recentRequests.length }} 项</span>
            </div>
            <div v-for="item in recentRequests" :key="item.id" class="lg-warning-item">
              <span class="lg-warning-project">{{ item.projectName || '-' }}</span>
              <span class="lg-warning-title">{{ item.requestCode }}</span>
            </div>
            <div v-if="!recentRequests.length" class="lg-warning-empty">暂无采购申请</div>
          </section>
        </div>
      </aside>
    </div>

    <!-- Add/Edit Modal -->
    <a-modal
      v-model:open="modalVisible"
      :title="modalTitle"
      :width="800"
      :confirm-loading="submitting"
      destroy-on-close
      @ok="handleModalOk"
      @cancel="handleModalCancel"
    >
      <!-- Header Form -->
      <a-form :label-col="{ span: 6 }" :wrapper-col="{ span: 16 }" style="margin-bottom: 8px">
        <a-form-item label="项目" required>
          <a-select
            v-model:value="formData.projectId"
            placeholder="请选择项目"
            @change="handleProjectChange"
          >
            <a-select-option v-for="p in projectList" :key="p.id" :value="p.id">
              {{ p.projectName }}
            </a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="关联合同">
          <a-select
            v-model:value="formData.contractId"
            placeholder="选择采购合同"
            allow-clear
            show-search
            :filter-option="filterOption"
            @change="modalDirty = true"
          >
            <a-select-option v-for="c in contractList" :key="c.id" :value="c.id">
              {{ c.contractName }}
            </a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="备注">
          <a-textarea
            v-model:value="formData.remark"
            :rows="2"
            placeholder="请输入备注"
            @change="modalDirty = true"
          />
        </a-form-item>
      </a-form>

      <!-- Line Items Section -->
      <div class="pr-items-section">
        <div class="pr-items-header">
          <span class="pr-items-title">
            申请明细
            <span class="pr-items-count"> {{ itemsCount }} 项 </span>
          </span>
          <a-button type="dashed" size="small" @click="handleAddItem">+ 添加物料</a-button>
        </div>

        <a-table
          :data-source="itemList"
          :pagination="false"
          table-layout="fixed"
          :columns="itemColumns"
          row-key="key"
          size="small"
          :scroll="{ y: 250 }"
        >
          <template #bodyCell="{ column, record: item }">
            <template v-if="column.key === 'material'">
              <div style="display: flex; gap: 4px">
                <a-select
                  :value="item.materialId"
                  placeholder="选择已有物料"
                  allow-clear
                  :style="{ width: item.materialId ? '100%' : '50%', flexShrink: 0 }"
                  show-search
                  :filter-option="filterOption"
                  @change="(val: string) => handleMaterialChange(item.key, val)"
                  @clear="handleMaterialClear(item.key)"
                >
                  <a-select-option v-for="m in materialList" :key="m.id" :value="m.id">
                    {{ m.materialName }}
                  </a-select-option>
                </a-select>
                <a-input
                  v-if="!item.materialId"
                  v-model:value="item.materialName"
                  placeholder="自定义物料"
                  size="small"
                  style="flex: 1"
                  @change="modalDirty = true"
                />
              </div>
            </template>
            <template v-else-if="column.key === 'unit'">
              <a-input
                v-model:value="item.unit"
                placeholder="单位"
                size="small"
                style="width: 100%"
                @change="modalDirty = true"
              />
            </template>
            <template v-else-if="column.key === 'quantity'">
              <a-input-number
                v-model:value="item.quantity"
                :min="0"
                :precision="4"
                style="width: 100%"
                @change="modalDirty = true"
              />
            </template>
            <template v-else-if="column.key === 'plannedDate'">
              <a-date-picker
                v-model:value="item.plannedDate"
                value-format="YYYY-MM-DD"
                style="width: 100%"
                size="small"
                :get-popup-container="getPopupContainer"
                @change="modalDirty = true"
              />
            </template>
            <template v-else-if="column.key === 'remark'">
              <a-input
                v-model:value="item.remark"
                placeholder="备注"
                size="small"
                @change="modalDirty = true"
              />
            </template>
            <template v-else-if="column.key === 'action'">
              <a-button type="link" size="small" danger @click="handleRemoveItem(item.key)">
                删除
              </a-button>
            </template>
          </template>
        </a-table>
      </div>
    </a-modal>
  </div>
</template>

<style scoped>
.purchase-request-page {
  gap: 14px;
}

.purchase-request-page-head {
  align-items: center;
  justify-content: space-between;
  min-height: 0;
  padding: 0;
}

.purchase-request-page-meta-row {
  display: flex;
  align-items: center;
  gap: 5em;
  min-width: 0;
}

.purchase-request-breadcrumb {
  font-size: 13px;
  line-height: 20px;
}

.purchase-request-page-subtitle {
  color: var(--text-secondary);
  font-size: 13px;
  line-height: 20px;
  white-space: nowrap;
}

.purchase-request-search-bar {
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  min-height: 74px;
}

.purchase-request-search-fields {
  display: flex;
  flex: 1 1 auto;
  gap: 12px;
  align-items: center;
  min-width: 0;
}

.purchase-request-search-input {
  width: min(520px, 31vw);
  min-width: 320px;
  flex: 1 1 auto;
}

.purchase-request-search-prefix-icon {
  color: var(--text-secondary);
}

.purchase-request-search-select {
  width: 180px;
  flex: 0 0 180px;
}

.purchase-request-search-select.is-compact {
  width: 150px;
  flex-basis: 150px;
}

.purchase-request-search-actions {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  gap: 8px;
}

.purchase-request-workspace {
  align-items: stretch;
  min-height: 0;
}

.purchase-request-kpi-summary {
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

.purchase-request-kpi-item {
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

.purchase-request-kpi-item:last-child {
  border-right: 0;
}

.purchase-request-kpi-icon {
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

.purchase-request-kpi-icon.is-draft {
  color: var(--warning);
  background: var(--warning-soft);
}

.purchase-request-kpi-icon.is-pending,
.purchase-request-kpi-icon.is-recent {
  color: var(--primary);
  background: var(--surface-tint);
}

.purchase-request-kpi-icon.is-converted {
  color: var(--success);
  background: var(--success-soft);
}

.purchase-request-kpi-label {
  overflow: hidden;
  color: var(--text-secondary);
  font-size: 13px;
  font-weight: 600;
  line-height: 18px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.purchase-request-kpi-value {
  overflow: hidden;
  color: var(--text);
  font-size: 24px;
  font-weight: 800;
  line-height: 28px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.purchase-request-kpi-value small {
  margin-left: 4px;
  color: var(--text-secondary);
  font-size: 13px;
  font-weight: 600;
}

.purchase-request-kpi-progress {
  display: block;
  overflow: hidden;
  height: 4px;
  background: var(--surface-subtle);
  border-radius: var(--radius-sm);
  grid-column: 2;
}

.purchase-request-kpi-progress > span {
  display: block;
  height: 100%;
  background: var(--kpi-paid);
  border-radius: var(--radius-sm);
}

.purchase-request-kpi-item.is-converted .purchase-request-kpi-progress > span {
  background: var(--kpi-unpaid);
}

.purchase-request-table-panel {
  min-height: 754px;
}

.purchase-request-toolbar {
  align-items: center;
}

.purchase-request-table-title {
  color: var(--text);
  font-size: 15px;
  font-weight: 800;
}

.purchase-request-table-count,
.purchase-request-toolbar-hint {
  color: var(--text-secondary);
  font-size: 12px;
}

.purchase-request-analysis-rail {
  width: 336px;
}

.purchase-request-analysis-panel {
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

.purchase-request-analysis-head,
.purchase-request-warning-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.purchase-request-analysis-title {
  color: var(--text);
  font-size: 16px;
  font-weight: 800;
  line-height: 22px;
}

.purchase-request-analysis-subtitle,
.purchase-request-warning-count {
  color: var(--text-secondary);
  font-size: 12px;
}

.purchase-request-analysis-section {
  display: flex;
  flex-direction: column;
  gap: 10px;
  min-width: 0;
  padding-top: 16px;
  border-top: 1px solid var(--border-subtle);
}

.purchase-request-section-title {
  color: var(--text);
  font-size: 14px;
  font-weight: 700;
  line-height: 20px;
}

.purchase-request-analysis-empty {
  padding: 10px 0;
  color: var(--text-secondary);
  font-size: 13px;
  text-align: center;
}

.purchase-request-analysis-section :deep(.lg-type-row),
.lg-type-row {
  grid-template-columns: 9px minmax(54px, 72px) minmax(72px, 1fr) 20px 38px;
}

.pr-items-section {
  border-top: 1px solid #f0f0f0;
  padding-top: 12px;
  margin-top: 4px;
}

.pr-items-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
}

.pr-items-title {
  font-weight: 600;
  font-size: 14px;
}

.pr-items-count {
  color: var(--muted);
  font-weight: 400;
  font-size: 12px;
  margin-left: 6px;
}

:deep(.pr-items-section .ant-table-thead > tr > th:first-child),
:deep(.pr-items-section .ant-table-tbody > tr > td:first-child) {
  width: 240px !important;
  min-width: 240px !important;
  max-width: 240px !important;
}

:deep(.pr-items-section .ant-table colgroup col:first-child) {
  width: 240px !important;
  min-width: 240px !important;
  max-width: 240px !important;
}

@media (max-width: 1200px) {
  .purchase-request-kpi-summary {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .purchase-request-kpi-item {
    border-bottom: 1px solid var(--border-subtle);
  }

  .purchase-request-analysis-rail {
    width: 100%;
  }
}

@media (max-width: 768px) {
  .purchase-request-page-meta-row,
  .purchase-request-search-bar,
  .purchase-request-search-fields {
    align-items: stretch;
    flex-direction: column;
  }

  .purchase-request-page-subtitle {
    white-space: normal;
  }

  .purchase-request-search-input,
  .purchase-request-search-select,
  .purchase-request-search-select.is-compact {
    width: 100%;
    min-width: 0;
    flex-basis: auto;
  }
}
</style>
