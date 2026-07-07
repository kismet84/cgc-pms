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
import PurchaseRequestAnalysisPanel from './components/PurchaseRequestAnalysisPanel.vue'
import PurchaseRequestModal from './components/PurchaseRequestModal.vue'
import PurchaseRequestSearchBar from './components/PurchaseRequestSearchBar.vue'
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
type ModalMode = 'create' | 'edit' | 'view'
const modalMode = ref<ModalMode>('create')
const isViewMode = computed(() => modalMode.value === 'view')
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
  APPROVED: '已通过',
  CONVERTED: '已转PO',
}
const STATUS_COLOR: Record<string, string> = {
  DRAFT: 'default',
  APPROVED: 'success',
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
  { field: 'requestCode', title: '申请编号', minWidth: 120, slots: { default: 'requestCode' } },
  { field: 'projectName', title: '所属项目', minWidth: 112, ellipsis: true },
  { field: 'contractName', title: '关联合同', minWidth: 124, ellipsis: true },
  { field: 'approvalStatus', title: '审批状态', width: 88, slots: { default: 'approvalStatus' } },
  { field: 'status', title: '业务状态', width: 88, slots: { default: 'status' } },
  { field: 'createdBy', title: '创建人', width: 76 },
  { field: 'createdTime', title: '创建时间', width: 116 },
  { title: '操作', width: 56, slots: { default: 'ops' } },
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
  modalMode.value = 'create'
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
  modalMode.value = 'edit'
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
  modalMode.value = 'view'
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
  if (isViewMode.value) return
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
  if (isViewMode.value) return
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
  if (isViewMode.value) return
  modalDirty.value = true
  const idx = itemList.value.findIndex((i) => i.key === key)
  if (idx !== -1) {
    itemList.value.splice(idx, 1)
  }
}

function handleMaterialClear(key: number) {
  if (isViewMode.value) return
  modalDirty.value = true
  const item = itemList.value.find((i) => i.key === key)
  if (!item) return
  item.materialId = ''
  item.materialName = ''
  item.unit = ''
}

function handleMaterialChange(key: number, materialId: string | undefined) {
  if (isViewMode.value) return
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
  if (isViewMode.value || submitting.value) return

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
      } catch (cleanupError: unknown) {
        console.error('采购申请创建回滚失败', cleanupError)
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
    tableData.value.filter(
      (r) => r.approvalStatus === APPROVAL_DRAFT || r.approvalStatus === APPROVAL_APPROVING,
    ).length,
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
    color: key === 'CONVERTED' ? '#31c48d' : key === 'APPROVED' ? '#16a34a' : '#f59e0b',
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
      </div>
    </div>

    <div class="lg-grid purchase-request-workspace">
      <div class="lg-left">
        <!-- KPI 横条 -->
        <div class="lg-kpi-strip purchase-request-kpi-summary" aria-label="采购申请关键指标">
          <div class="purchase-request-kpi-item">
            <span class="purchase-request-kpi-icon is-total"><ShoppingCartOutlined /></span>
            <span class="purchase-request-kpi-content">
              <span class="purchase-request-kpi-label">申请总数</span>
              <span class="purchase-request-kpi-value">{{ kpiReqTotal }} <small>单</small></span>
            </span>
          </div>
          <div class="purchase-request-kpi-item is-wide">
            <span class="purchase-request-kpi-icon is-draft"><FileDoneOutlined /></span>
            <span class="purchase-request-kpi-content">
              <span class="purchase-request-kpi-label">草稿申请</span>
              <span class="purchase-request-kpi-value">{{ kpiReqDraft }} <small>单</small></span>
            </span>
          </div>
          <div class="purchase-request-kpi-item is-progress">
            <span class="purchase-request-kpi-icon is-pending"><ClockCircleOutlined /></span>
            <span class="purchase-request-kpi-content">
              <span class="purchase-request-kpi-label">待审批</span>
              <span class="purchase-request-kpi-value">{{ kpiReqPending }} <small>单</small></span>
              <span class="purchase-request-kpi-progress">
                <span :style="{ width: kpiPct(kpiReqPending, kpiMax.totalCount) + '%' }"></span>
              </span>
            </span>
          </div>
          <div class="purchase-request-kpi-item is-progress is-converted">
            <span class="purchase-request-kpi-icon is-converted"><CheckCircleOutlined /></span>
            <span class="purchase-request-kpi-content">
              <span class="purchase-request-kpi-label">已转PO</span>
              <span class="purchase-request-kpi-value"
                >{{ kpiReqConverted }} <small>单</small></span
              >
              <span class="purchase-request-kpi-progress">
                <span :style="{ width: kpiPct(kpiReqConverted, kpiMax.totalCount) + '%' }"></span>
              </span>
            </span>
          </div>
          <div class="purchase-request-kpi-item">
            <span class="purchase-request-kpi-icon is-recent"><WalletOutlined /></span>
            <span class="purchase-request-kpi-content">
              <span class="purchase-request-kpi-label">近期申请</span>
              <span class="purchase-request-kpi-value"
                >{{ recentRequests.length }} <small>单</small></span
              >
            </span>
          </div>
        </div>

        <PurchaseRequestSearchBar
          :project-id="filter.projectId"
          :approval-status="filter.approvalStatus"
          :status="filter.status"
          :keyword="filter.keyword"
          :project-list="projectList"
          @update:project-id="(value) => (filter.projectId = value)"
          @update:approval-status="(value) => (filter.approvalStatus = value)"
          @update:status="(value) => (filter.status = value)"
          @update:keyword="(value) => (filter.keyword = value)"
          @search="handleSearch"
          @reset="handleReset"
        />

        <main class="lg-list-table-panel purchase-request-table-panel">
          <!-- 工具栏 -->
          <div class="lg-toolbar purchase-request-toolbar">
            <div class="lg-toolbar-left">
              <span class="purchase-request-table-title">采购申请</span>
              <span class="purchase-request-table-count">共 {{ total }} 条</span>
            </div>
            <div class="lg-toolbar-right">
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
          </div>

          <!-- 表格 -->
          <div class="lg-table-wrap">
            <vxe-grid
              :data="tableData"
              :columns="visibleGridColumns"
              :loading="loading"
              :column-config="{ resizable: true, useKey: true }"
              show-overflow="title"
              show-header-overflow="title"
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
                      <a-menu-item
                        v-if="row.approvalStatus === APPROVAL_DRAFT"
                        @click="handleSubmit(row)"
                      >
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

      <PurchaseRequestAnalysisPanel
        :status-breakdown="statusBreakdown"
        :approval-breakdown="approvalBreakdown"
        :recent-requests="recentRequests"
        @refresh="fetchData"
      />
    </div>

    <PurchaseRequestModal
      :open="modalVisible"
      :title="modalTitle"
      :is-view-mode="isViewMode"
      :submitting="submitting"
      :form-data="formData"
      :project-list="projectList"
      :contract-list="contractList"
      :item-list="itemList"
      :items-count="itemsCount"
      :item-columns="itemColumns"
      :material-list="materialList"
      :filter-option="filterOption"
      :get-popup-container="getPopupContainer"
      @ok="handleModalOk"
      @cancel="handleModalCancel"
      @project-change="handleProjectChange"
      @mark-dirty="modalDirty = true"
      @add-item="handleAddItem"
      @remove-item="handleRemoveItem"
      @material-change="handleMaterialChange"
      @material-clear="handleMaterialClear"
    />
  </div>
</template>

<style scoped>
.purchase-request-page {
  background: var(--surface-subtle);
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

.purchase-request-workspace {
}

.purchase-request-page .lg-left {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-height: 0;
}

.purchase-request-kpi-summary {
  display: grid;
  flex: 0 0 auto;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 0;
  margin: 0;
  overflow: hidden;
  background: var(--surface);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-soft);
}

.purchase-request-kpi-item {
  position: relative;
  display: grid;
  grid-template-columns: 38px minmax(0, 1fr);
  column-gap: 10px;
  align-items: center;
  min-width: 0;
  padding: 16px;
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
}

.purchase-request-kpi-content {
  display: grid;
  grid-template-rows: auto auto;
  row-gap: 4px;
  min-width: 0;
}

.purchase-request-kpi-item.is-progress .purchase-request-kpi-content {
  grid-template-rows: auto auto auto;
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
  display: block;
  min-width: 0;
  color: var(--text-secondary);
  font-size: 13px;
  font-weight: 600;
  line-height: 17px;
}

.purchase-request-kpi-value {
  display: block;
  min-width: 0;
  color: var(--text);
  font-size: 22px;
  font-variant-numeric: tabular-nums;
  font-weight: 800;
  line-height: 26px;
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
  width: 100%;
  height: 4px;
  background: var(--surface-subtle);
  border-radius: var(--radius-sm);
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

.purchase-request-toolbar {
  border-bottom: 1px solid var(--border-subtle);
}

.purchase-request-table-title {
  color: var(--text);
  font-size: 15px;
  font-weight: 700;
}

.purchase-request-table-count {
  color: var(--text-secondary);
  font-size: 13px;
}

.purchase-request-table-panel .lg-table-wrap {
  flex: 1;
  min-height: 0;
}

.purchase-request-code-link {
  max-width: 100%;
  padding: 0;
}

.purchase-request-code-link :deep(span) {
  display: inline-block;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.purchase-request-table-panel .lg-pagination {
  border-top: 1px solid var(--border-subtle);
}

@media (max-width: 1200px) {
  .purchase-request-kpi-summary {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  .purchase-request-kpi-item {
    border-bottom: 1px solid var(--border-subtle);
  }
}

@media (max-width: 768px) {
  .purchase-request-kpi-summary {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
