<script setup lang="ts">
import { ref, reactive, onMounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { storeToRefs } from 'pinia'
import { message, Modal } from 'ant-design-vue'
import {
  CheckCircleOutlined,
  ClockCircleOutlined,
  DollarOutlined,
  FileDoneOutlined,
  MoreOutlined,
  PlusOutlined,
  ReloadOutlined,
  SafetyCertificateOutlined,
  SearchOutlined,
} from '@ant-design/icons-vue'
import {
  getMeasureList,
  getMeasureDetail,
  createMeasure,
  updateMeasure,
  deleteMeasure,
  getMeasureItems,
  saveMeasureItems,
  submitMeasureForApproval,
  getSubTaskList,
} from '@/api/modules/subcontract'
import { getContractItems } from '@/api/modules/contract'
import { useReferenceStore } from '@/stores/reference'
import type { SubMeasureVO, SubMeasureItemVO, SubTaskVO } from '@/types/subcontract'

// 字典常量 - 审批状态
const APPROVAL_DRAFT = 'DRAFT'
const APPROVAL_APPROVING = 'APPROVING'

// 字典常量 - 业务状态
const STATUS_DRAFT = 'DRAFT'
const STATUS_CONFIRMED = 'CONFIRMED'
const STATUS_COMPLETED = 'COMPLETED'
import type { SelectOption } from '@/types/ui'
import type { ContractItem } from '@/types/contract'
import { useColumnSettings } from '@/composables/useColumnSettings'
import { ColumnSettingsButton } from '@/components/list-page'
import SubcontractMeasureAnalysisRail from './components/SubcontractMeasureAnalysisRail.vue'
import SubcontractMeasureModal from './components/SubcontractMeasureModal.vue'
import { fetchDictData, getDictLabelSync, getDictTagColorSync } from '@/utils/dict'

const route = useRoute()
const router = useRouter()

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
const modalTitle = ref('新建分包计量')
const editingId = ref<string | null>(null)
const formData = reactive<Partial<SubMeasureVO>>({
  projectId: undefined,
  contractId: undefined,
  partnerId: undefined,
  subTaskId: undefined,
  measurePeriod: '',
  measureDate: undefined,
  remark: '',
})
const subTaskOptions = ref<SubTaskVO[]>([])
const formPartnerName = computed(
  () => contractList.value?.find((c) => c.id === formData.contractId)?.partyBName ?? '',
)

// Line items
const itemList = ref<(Partial<SubMeasureItemVO> & { key: number })[]>([])
let itemKeyCounter = 0

const STATUS_LABEL: Record<string, string> = {
  DRAFT: '草稿',
  APPROVING: '审批中',
  CONFIRMED: '已确认',
  COMPLETED: '已完成',
}
const STATUS_COLOR: Record<string, string> = {
  DRAFT: 'default',
  APPROVING: 'processing',
  CONFIRMED: 'blue',
  COMPLETED: 'success',
}
const STATUS_DICT = 'sub_measure_status'

function measureStatusLabel(status: string | undefined): string {
  return getDictLabelSync(STATUS_DICT, status ?? '', STATUS_LABEL)
}

function measureStatusColor(status: string | undefined): string {
  return getDictTagColorSync(STATUS_DICT, status ?? '', STATUS_COLOR)
}

function filterOptionByLabel(input: string, option: SelectOption) {
  return option.label?.toLowerCase().includes(input.toLowerCase())
}

// ---- vxe-grid columns ----
const gridColumns = computed(() => [
  { field: 'measureCode', title: '计量编号', minWidth: 150, slots: { default: 'measureCode' } },
  { field: 'measurePeriod', title: '计量期次', width: 112 },
  { field: 'projectName', title: '项目名称', minWidth: 150, ellipsis: true },
  { field: 'contractName', title: '合同名称', minWidth: 150, ellipsis: true },
  { field: 'partnerName', title: '分包商', minWidth: 140, ellipsis: true },
  {
    field: 'subTaskName',
    title: '关联任务',
    minWidth: 150,
    ellipsis: true,
    slots: { default: 'subTaskName' },
  },
  {
    field: 'reportedAmount',
    title: '申报金额',
    width: 118,
    align: 'right' as const,
    slots: { default: 'reportedAmount' },
  },
  {
    field: 'approvedAmount',
    title: '审核金额',
    width: 118,
    align: 'right' as const,
    slots: { default: 'approvedAmount' },
  },
  {
    field: 'netAmount',
    title: '净额',
    width: 100,
    align: 'right' as const,
    slots: { default: 'netAmount' },
  },
  { field: 'measureDate', title: '计量日期', width: 112 },
  { field: 'status', title: '状态', width: 88, slots: { default: 'status' } },
  { field: 'approvalStatus', title: '审批状态', width: 108, slots: { default: 'approvalStatus' } },
  { title: '操作', width: 76, slots: { default: 'action' } },
])

const {
  visibleColumns: visibleGridColumns,
  columnSettings,
  colVisible,
  toggleCol,
} = useColumnSettings('subcontract_measure_cols_v2', gridColumns, {
  measureDate: false,
  approvalStatus: false,
})

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
    total.value = Number(res.total ?? 0)
  } catch (e: unknown) {
    console.error(e)
    tableData.value = []
    total.value = 0
    message.error('加载分包计量列表失败，请稍后重试')
  } finally {
    loading.value = false
  }
}

async function loadSubTaskOptions() {
  if (!formData.projectId && !formData.contractId && !formData.partnerId) {
    subTaskOptions.value = []
    return
  }
  try {
    const params: Record<string, unknown> = { pageSize: 999 }
    if (formData.projectId) params.projectId = formData.projectId
    if (formData.contractId) params.contractId = formData.contractId
    if (formData.partnerId) params.partnerId = formData.partnerId
    const res = await getSubTaskList(params)
    subTaskOptions.value = res.records || []
  } catch (e: unknown) {
    console.error(e)
    subTaskOptions.value = []
  }
}

async function loadContractItems(contractId: string) {
  try {
    const items = await getContractItems(contractId)
    contractItemList.value = items
  } catch (e: unknown) {
    console.error(e)
    contractItemList.value = []
    message.error('加载合同清单失败')
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
  modalTitle.value = '新建分包计量'
  editingId.value = null
  Object.assign(formData, {
    projectId: undefined,
    contractId: undefined,
    partnerId: undefined,
    subTaskId: undefined,
    measurePeriod: '',
    measureDate: undefined,
    remark: '',
  })
  itemList.value = []
  contractItemList.value = []
  subTaskOptions.value = []
  itemKeyCounter = 0
  modalVisible.value = true
}

async function handleEdit(record: SubMeasureVO) {
  modalTitle.value = '编辑分包计量'
  editingId.value = record.id
  Object.assign(formData, {
    projectId: record.projectId,
    contractId: record.contractId,
    partnerId: record.partnerId,
    subTaskId: record.subTaskId,
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
  // Load subtask options for the current context
  await loadSubTaskOptions()
  // Load existing measure items
  try {
    const items = await getMeasureItems(record.id)
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

async function handleView(record: SubMeasureVO) {
  await handleEdit(record)
  modalTitle.value = '查看分包计量'
}

async function openBusinessIdFromQuery() {
  const value = route.query.businessId
  const businessId = Array.isArray(value) ? value[0] : value
  if (!businessId) return

  try {
    const record = await getMeasureDetail(String(businessId))
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

function handleDelete(record: SubMeasureVO) {
  Modal.confirm({
    title: '确认删除',
    content: `确定要删除分包计量"${record.measureCode}"吗？`,
    okText: '确定',
    cancelText: '取消',
    onOk: async () => {
      try {
        await deleteMeasure(record.id)
        message.success('删除成功')
        fetchData()
      } catch (e: unknown) {
        console.error(e)
        message.error('删除失败，请稍后重试')
      }
    },
  })
}

function handleSubmitApproval(record: SubMeasureVO) {
  Modal.confirm({
    title: '确认提交',
    content: `确定要提交分包计量"${record.measureCode}"吗？提交后将进入审批流程`,
    okText: '确定',
    cancelText: '取消',
    onOk: async () => {
      try {
        await submitMeasureForApproval(record.id)
        message.success('提交审批成功')
        fetchData()
      } catch (e: unknown) {
        console.error(e)
        message.error('提交审批失败')
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
  // When contract changes, subtask filter context also changes
  formData.subTaskId = undefined
  await loadSubTaskOptions()
}

function handleModalProjectChange(projectId: string) {
  formData.projectId = projectId
  formData.contractId = undefined
  formData.partnerId = undefined
  formData.subTaskId = undefined
  subTaskOptions.value = []
  referenceStore.fetchContracts({ projectId })
}

async function handleModalOk() {
  if (!formData.projectId) {
    message.warning('请选择项目')
    return
  }

  try {
    let measureId: string
    if (editingId.value) {
      await updateMeasure(editingId.value, formData)
      measureId = editingId.value
      message.success('更新成功')
    } else {
      const result = await createMeasure(formData)
      measureId = result
      message.success('创建成功')
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
    message.error('操作失败，请稍后重试')
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
    .filter((r) => r.status === STATUS_CONFIRMED || r.status === STATUS_COMPLETED)
    .reduce((s, r) => s + (parseFloat(r.approvedAmount) || 0), 0),
)
const kpiMeasurePending = computed(
  () =>
    tableData.value.filter((r) => r.status === STATUS_DRAFT || r.status === APPROVAL_APPROVING)
      .length,
)
const approvedRate = computed(() =>
  kpiMeasureTotal.value ? Math.round((kpiApproved.value / kpiMeasureTotal.value) * 100) : 0,
)
const measureStatusSummary = computed(() => [
  {
    label: '待审核',
    count: kpiMeasurePending.value,
    color: '#faad14',
    pct: statusPct(kpiMeasurePending.value),
  },
  {
    label: measureStatusLabel(STATUS_CONFIRMED),
    count: tableData.value.filter((r) => r.status === STATUS_CONFIRMED).length,
    color: '#1890ff',
    pct: statusPct(tableData.value.filter((r) => r.status === STATUS_CONFIRMED).length),
  },
  {
    label: measureStatusLabel(STATUS_COMPLETED),
    count: tableData.value.filter((r) => r.status === STATUS_COMPLETED).length,
    color: '#52c41a',
    pct: statusPct(tableData.value.filter((r) => r.status === STATUS_COMPLETED).length),
  },
])
const recentMeasures = computed(() => tableData.value.slice(0, 4))
function fmtWan(val: number): string {
  return (val / 10000).toLocaleString('zh-CN', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  })
}
function statusPct(count: number) {
  const base = tableData.value.length || 0
  if (!base) return 0
  return Math.round((count / base) * 100)
}

onMounted(() => {
  fetchDictData(STATUS_DICT)
  referenceStore.fetchProjects()
  referenceStore.fetchContracts({ contractType: 'SUB' })
  referenceStore.fetchPartners({ partnerType: 'SUB' })
  fetchData()
  openBusinessIdFromQuery()
})
</script>

<template>
  <div class="lg-list-page lg-page app-page subcontract-measure-page">
    <div class="lg-page-head subcontract-measure-page-head">
      <div class="subcontract-measure-title-block">
        <a-breadcrumb class="lg-breadcrumb">
          <a-breadcrumb-item>分包管理</a-breadcrumb-item>
          <a-breadcrumb-item>分包计量</a-breadcrumb-item>
        </a-breadcrumb>
        <div class="subcontract-measure-title-row">
          <h1>分包计量</h1>
          <span>核对分包计量申报、审核金额、净额与审批状态。</span>
        </div>
      </div>
    </div>

    <div class="lg-search-bar subcontract-measure-search-bar">
      <a-input
        v-model:value="filter.keyword"
        placeholder="搜索计量编号…"
        allow-clear
        size="large"
        @press-enter="handleSearch"
      >
        <template #prefix><SearchOutlined style="color: var(--text-secondary)" /></template>
      </a-input>
      <a-select
        v-model:value="filter.projectId"
        placeholder="全部项目"
        allow-clear
        size="large"
        show-search
        :filter-option="filterOptionByLabel"
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
        v-model:value="filter.status"
        placeholder="全部计量状态"
        allow-clear
        size="large"
        @change="handleSearch"
      >
        <a-select-option value="DRAFT">草稿</a-select-option>
        <a-select-option value="APPROVING">审批中</a-select-option>
        <a-select-option value="CONFIRMED">已确认</a-select-option>
        <a-select-option value="COMPLETED">已完成</a-select-option>
      </a-select>
      <a-button type="primary" size="large" @click="handleSearch">查询</a-button>
      <a-button size="large" @click="handleReset">
        <template #icon><ReloadOutlined /></template>
        重置
      </a-button>
    </div>

    <div class="lg-grid">
      <div class="subcontract-measure-main-column">
        <div class="subcontract-measure-kpi-summary" aria-label="分包计量关键指标">
          <div class="subcontract-measure-kpi-item">
            <span class="subcontract-measure-kpi-icon is-blue"><FileDoneOutlined /></span>
            <div>
              <span class="subcontract-measure-kpi-label">计量总数</span>
              <strong>{{ kpiTotalCount }}</strong>
              <span class="subcontract-measure-kpi-hint">全部计量</span>
            </div>
          </div>
          <div class="subcontract-measure-kpi-item">
            <span class="subcontract-measure-kpi-icon is-cyan"><DollarOutlined /></span>
            <div>
              <span class="subcontract-measure-kpi-label">申报总额</span>
              <strong>{{ fmtWan(kpiMeasureTotal) }}</strong>
              <span class="subcontract-measure-kpi-hint">万元</span>
            </div>
          </div>
          <div class="subcontract-measure-kpi-item">
            <span class="subcontract-measure-kpi-icon is-green"><CheckCircleOutlined /></span>
            <div>
              <span class="subcontract-measure-kpi-label">审核金额</span>
              <strong>{{ fmtWan(kpiApproved) }}</strong>
              <span class="subcontract-measure-kpi-hint">{{ approvedRate }}%</span>
            </div>
          </div>
          <div class="subcontract-measure-kpi-item">
            <span class="subcontract-measure-kpi-icon is-amber"><ClockCircleOutlined /></span>
            <div>
              <span class="subcontract-measure-kpi-label">待审核</span>
              <strong>{{ kpiMeasurePending }}</strong>
              <span class="subcontract-measure-kpi-hint">{{ statusPct(kpiMeasurePending) }}%</span>
            </div>
          </div>
          <div class="subcontract-measure-kpi-item">
            <span class="subcontract-measure-kpi-icon is-purple"
              ><SafetyCertificateOutlined
            /></span>
            <div>
              <span class="subcontract-measure-kpi-label">审核比例</span>
              <strong>{{ approvedRate }}%</strong>
              <span class="subcontract-measure-kpi-hint">申报金额口径</span>
            </div>
          </div>
        </div>

        <main class="lg-list-table-panel subcontract-measure-table-panel">
          <div class="lg-toolbar">
            <div class="lg-toolbar-left">
              <div class="subcontract-measure-table-title">
                <strong>计量明细</strong>
                <span>共 {{ total }} 条</span>
              </div>
              <ColumnSettingsButton
                :columns="columnSettings"
                :visible="colVisible"
                @toggle="toggleCol"
              />
              <a-button type="primary" @click="handleAdd">
                <template #icon><PlusOutlined /></template>
                新建计量
              </a-button>
              <a-button @click="fetchData">
                <template #icon><ReloadOutlined /></template>
                刷新
              </a-button>
            </div>
          </div>

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
              <template #measureCode="{ row }">
                <a-button
                  class="subcontract-measure-code-link"
                  type="link"
                  @click="handleView(row)"
                >
                  {{ row.measureCode || '-' }}
                </a-button>
              </template>
              <template #subTaskName="{ row }">
                <span v-if="row.subTaskName">{{ row.subTaskName }}</span>
                <span v-else class="lg-none">-</span>
              </template>
              <template #reportedAmount="{ row }">
                <span v-if="row.reportedAmount" class="lg-money">
                  {{
                    Number(row.reportedAmount).toLocaleString('zh-CN', {
                      minimumFractionDigits: 2,
                    })
                  }}
                </span>
                <span v-else class="lg-none">-</span>
              </template>
              <template #approvedAmount="{ row }">
                <span v-if="row.approvedAmount" class="lg-money">
                  {{
                    Number(row.approvedAmount).toLocaleString('zh-CN', {
                      minimumFractionDigits: 2,
                    })
                  }}
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
                <a-tag :color="measureStatusColor(row.status)">
                  {{ measureStatusLabel(row.status) }}
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

      <SubcontractMeasureAnalysisRail
        :status-count="tableData.length"
        :kpi-measure-total="kpiMeasureTotal"
        :kpi-approved="kpiApproved"
        :approved-rate="approvedRate"
        :measure-status-summary="measureStatusSummary"
        :recent-measures="recentMeasures"
        :fmt-wan="fmtWan"
        :measure-status-label="measureStatusLabel"
      />
    </div>

    <SubcontractMeasureModal
      :open="modalVisible"
      :title="modalTitle"
      :form-data="formData"
      :project-list="projectList"
      :contract-list="contractList"
      :sub-task-options="subTaskOptions"
      :form-partner-name="formPartnerName"
      :item-list="itemList"
      :contract-item-list="contractItemList"
      :items-total-amount="itemsTotalAmount"
      :on-ok="handleModalOk"
      :on-cancel="handleModalCancel"
      :on-project-change="handleModalProjectChange"
      :on-contract-select="onContractSelect"
      :on-add-item="handleAddItem"
      :on-remove-item="handleRemoveItem"
      :on-contract-item-change="handleContractItemChange"
      :on-item-qty-change="handleItemQtyChange"
      :on-item-price-change="handleItemPriceChange"
    />
  </div>
</template>

<style scoped>
.subcontract-measure-page {
  color: #0f172a;
}

.subcontract-measure-page-head {
  margin-bottom: 7px;
}

.lg-breadcrumb {
  margin-bottom: 5px;
  font-size: 13px;
}

.subcontract-measure-title-row {
  display: flex;
  align-items: baseline;
  gap: 12px;
}

.subcontract-measure-title-row h1 {
  margin: 0;
  font-size: 22px;
  line-height: 30px;
  font-weight: 700;
  color: #0f172a;
}

.subcontract-measure-title-row span {
  font-size: 13px;
  color: #64748b;
}

.subcontract-measure-search-bar {
  min-height: 74px;
  display: grid;
  grid-template-columns: minmax(260px, 1.7fr) minmax(180px, 1fr) 160px auto auto;
  gap: 12px;
  align-items: center;
  margin-bottom: 16px;
}

.subcontract-measure-main-column {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.subcontract-measure-kpi-summary {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  height: 88px;
  min-height: 88px;
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  overflow: hidden;
}

.subcontract-measure-kpi-item {
  display: flex;
  gap: 12px;
  align-items: center;
  min-width: 0;
  padding: 12px 18px;
  border-right: 1px solid #edf1f5;
}

.subcontract-measure-kpi-item:last-child {
  border-right: 0;
}

.subcontract-measure-kpi-icon {
  width: 36px;
  height: 36px;
  display: inline-grid;
  place-items: center;
  flex: 0 0 auto;
  border-radius: 8px;
  font-size: 18px;
}

.subcontract-measure-kpi-icon.is-blue {
  color: #2563eb;
  background: #eff6ff;
}

.subcontract-measure-kpi-icon.is-cyan {
  color: #0891b2;
  background: #ecfeff;
}

.subcontract-measure-kpi-icon.is-green {
  color: #16a34a;
  background: #f0fdf4;
}

.subcontract-measure-kpi-icon.is-amber {
  color: #d97706;
  background: #fffbeb;
}

.subcontract-measure-kpi-icon.is-purple {
  color: #7c3aed;
  background: #f5f3ff;
}

.subcontract-measure-kpi-label,
.subcontract-measure-kpi-hint {
  display: block;
  font-size: 12px;
  color: #64748b;
  line-height: 18px;
}

.subcontract-measure-kpi-item strong {
  display: block;
  margin: 1px 0;
  color: #0f172a;
  font-size: 20px;
  line-height: 24px;
  font-weight: 700;
}

.subcontract-measure-table-panel {
  min-height: 754px;
}

.subcontract-measure-table-title {
  display: inline-flex;
  align-items: baseline;
  gap: 8px;
  margin-right: 4px;
}

.subcontract-measure-table-title strong {
  font-size: 15px;
  color: #0f172a;
}

.subcontract-measure-table-title span {
  font-size: 12px;
  color: #64748b;
}

.lg-none {
  color: var(--muted);
}

@media (max-width: 1280px) {
  .subcontract-measure-search-bar {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
