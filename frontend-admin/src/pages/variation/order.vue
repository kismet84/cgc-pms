<script setup lang="ts">
import { ref, reactive, onMounted, computed, watch } from 'vue'
import { storeToRefs } from 'pinia'
import { message, Modal } from 'ant-design-vue'
import {
  CheckCircleOutlined,
  ExclamationCircleOutlined,
  FileTextOutlined,
  MoreOutlined,
  PlusOutlined,
  ReloadOutlined,
  SearchOutlined,
  WalletOutlined,
} from '@ant-design/icons-vue'
import {
  getVarOrderList,
  createVarOrder,
  updateVarOrder,
  deleteVarOrder,
  getVarOrderDetail,
  saveVarOrderItems,
  submitVarOrderForApproval,
} from '@/api/modules/variation'
import { getContractItems } from '@/api/modules/contract'
import { getCostSubjectTree } from '@/api/modules/costSubject'
import { useReferenceStore } from '@/stores/reference'
import type { VarOrderVO, VarOrderItemVO } from '@/types/variation'
import type { ContractItem } from '@/types/contract'
import type { CostSubjectTreeNode } from '@/types/costSubject'
import { ColumnSettingsButton } from '@/components/list-page'
import { useColumnSettings } from '@/composables/useColumnSettings'

const filter = reactive({
  projectId: undefined as string | undefined,
  contractId: undefined as string | undefined,
  partnerId: undefined as string | undefined,
  varType: undefined as string | undefined,
  direction: undefined as string | undefined,
  varCode: '',
})

const loading = ref(false)
const tableData = ref<VarOrderVO[]>([])
const total = ref(0)
const pageNo = ref(1)
const pageSize = ref(20)

const referenceStore = useReferenceStore()
const { projects: projectList, contracts: contractList } = storeToRefs(referenceStore)

const modalVisible = ref(false)
const modalTitle = ref('新建变更签证')
const editingId = ref<string | null>(null)
const modalReadonly = ref(false)
const formData = reactive<Partial<VarOrderVO>>({
  projectId: undefined,
  contractId: undefined,
  partnerId: undefined,
  varType: undefined,
  varName: '',
  direction: 'COST',
  impactDays: 0,
  ownerConfirmFlag: 0,
  remark: '',
})
const formPartnerName = computed(
  () => contractList.value?.find((c) => c.id === formData.contractId)?.partyBName ?? '',
)
async function onContractChange(contractId: string) {
  const c = contractList.value?.find((ct) => ct.id === contractId)
  formData.partnerId = c?.partyBId
  await ensureCostSubjects()
  await loadContractItems(contractId)
}
function onFormProjectChange(projectId: string) {
  formData.contractId = undefined
  formData.partnerId = undefined
  itemList.value = []
  referenceStore.fetchContracts({ projectId })
}
watch(
  () => formData.contractId,
  (val) => {
    if (!val) {
      formData.partnerId = undefined
      itemList.value = []
    }
  },
)

const itemList = ref<(Partial<VarOrderItemVO> & { key: number })[]>([])
let itemKeyCounter = 0
const contractItemsLoading = ref(false)
const costSubjectOptions = ref<{ value: string; label: string }[]>([])

const VAR_TYPE_OPTIONS = [
  { label: '设计变更', value: '设计变更' },
  { label: '现场签证', value: '现场签证' },
  { label: '索赔', value: '索赔' },
  { label: '洽商', value: '洽商' },
]

const DIRECTION_OPTIONS = [
  { label: '成本', value: 'COST' },
  { label: '收入', value: 'REVENUE', disabled: true },
]

const VAR_TYPE_LABEL: Record<string, string> = {
  设计变更: '设计变更',
  现场签证: '现场签证',
  索赔: '索赔',
  洽商: '洽商',
}
const VAR_TYPE_COLOR: Record<string, string> = {
  设计变更: 'blue',
  现场签证: 'orange',
  索赔: 'purple',
  洽商: 'cyan',
}
const APPROVAL_STATUS_LABEL: Record<string, string> = {
  DRAFT: '草稿',
  PENDING: '审批中',
  APPROVED: '已通过',
  REJECTED: '已驳回',
}
const APPROVAL_STATUS_COLOR: Record<string, string> = {
  DRAFT: 'processing',
  PENDING: 'warning',
  APPROVED: 'success',
  REJECTED: 'error',
}

function calcCodeColumnWidth(values: Array<string | undefined>, title = '变更编号') {
  const longest = Math.max(title.length, ...values.map((value) => String(value ?? '').length))
  return Math.min(Math.max(longest * 9 + 42, 128), 240)
}

// ---- VxeGrid columns ----
const gridColumns = computed(() => [
  {
    field: 'varCode',
    title: '变更编号',
    width: calcCodeColumnWidth(tableData.value.map((item) => item.varCode)),
    minWidth: 128,
    showOverflow: false,
    slots: { default: 'varCode' },
  },
  { field: 'varName', title: '变更名称', minWidth: 150, ellipsis: true },
  { field: 'varType', title: '变更类型', width: 108, slots: { default: 'varType' } },
  { field: 'direction', title: '方向', width: 70, slots: { default: 'direction' } },
  { field: 'projectName', title: '项目名称', minWidth: 150, ellipsis: true },
  { field: 'contractName', title: '合同名称', minWidth: 150, ellipsis: true },
  { field: 'partnerName', title: '合作方', minWidth: 140, ellipsis: true },
  {
    field: 'reportedAmount',
    title: '上报金额',
    width: 118,
    align: 'right' as const,
    slots: { default: 'reportedAmount' },
  },
  {
    field: 'approvedAmount',
    title: '审定金额',
    width: 118,
    align: 'right' as const,
    slots: { default: 'approvedAmount' },
  },
  {
    field: 'confirmedAmount',
    title: '确认金额',
    width: 118,
    align: 'right' as const,
    slots: { default: 'confirmedAmount' },
  },
  {
    field: 'approvalStatus',
    title: '审批状态',
    width: 108,
    slots: { default: 'approvalStatus' },
  },
  { key: 'ops', title: '操作', width: 76, slots: { default: 'ops' } },
])
const {
  visibleColumns: visibleGridColumns,
  columnSettings,
  colVisible,
  toggleCol,
} = useColumnSettings('var_order_cols_v2', gridColumns, {
  contractName: false,
  partnerName: false,
  reportedAmount: false,
  approvedAmount: false,
})

async function fetchData() {
  loading.value = true
  try {
    const res = await getVarOrderList({
      pageNo: pageNo.value,
      pageSize: pageSize.value,
      projectId: filter.projectId,
      contractId: filter.contractId,
      partnerId: filter.partnerId,
      varType: filter.varType,
      direction: filter.direction,
      varCode: filter.varCode || undefined,
    })
    tableData.value = res.records
    total.value = Number(res.total) || 0
  } catch (e: unknown) {
    console.error(e)
    tableData.value = []
    total.value = 0
    message.error('加载变更签证列表失败')
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
  filter.varType = undefined
  filter.direction = undefined
  filter.varCode = ''
  pageNo.value = 1
  fetchData()
}
function handleProjectChange(val: string | undefined) {
  filter.contractId = undefined
  if (val) referenceStore.fetchContracts({ projectId: val })
  handleSearch()
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

async function handleAdd() {
  modalTitle.value = '新建变更签证'
  editingId.value = null
  modalReadonly.value = false
  Object.assign(formData, {
    projectId: undefined,
    contractId: undefined,
    partnerId: undefined,
    varType: undefined,
    varName: '',
    direction: 'COST',
    impactDays: 0,
    ownerConfirmFlag: 0,
    remark: '',
  })
  itemList.value = []
  itemKeyCounter = 0
  await ensureCostSubjects()
  modalVisible.value = true
}

async function openVarOrderModal(record: VarOrderVO, readonly: boolean) {
  modalTitle.value = readonly ? '查看变更签证' : '编辑变更签证'
  editingId.value = record.id
  modalReadonly.value = readonly
  Object.assign(formData, {
    projectId: record.projectId,
    contractId: record.contractId,
    partnerId: record.partnerId,
    varType: record.varType,
    varName: record.varName,
    direction: record.direction,
    impactDays: record.impactDays ?? 0,
    ownerConfirmFlag: record.ownerConfirmFlag ?? 0,
    remark: record.remark ?? '',
  })
  await ensureCostSubjects()
  try {
    const detail = await getVarOrderDetail(record.id)
    itemList.value = (detail.items ?? []).map((it, idx) => ({ ...it, key: idx }))
    itemKeyCounter = itemList.value.length
  } catch {
    message.error('加载变更明细失败，请稍后重试')
    return
  }
  modalVisible.value = true
}

async function handleView(record: VarOrderVO) {
  await openVarOrderModal(record, true)
}

async function handleEdit(record: VarOrderVO) {
  await openVarOrderModal(record, false)
}

async function handleSubmitApproval(record: VarOrderVO) {
  Modal.confirm({
    title: '提交审批',
    content: `确认提交变更签证 ${record.varCode}？`,
    onOk: async () => {
      await submitVarOrderForApproval(record.id)
      message.success('已提交审批')
      fetchData()
    },
  })
}
async function handleDelete(record: VarOrderVO) {
  Modal.confirm({
    title: '确认删除',
    content: `确定删除变更签证 ${record.varCode}？`,
    okType: 'danger',
    onOk: async () => {
      await deleteVarOrder(record.id)
      message.success('已删除')
      fetchData()
    },
  })
}

async function handleSubmit() {
  if (modalReadonly.value) return
  const id = editingId.value
  const effectiveItems = itemList.value.filter((item) => toNumber(item.quantity) > 0)
  try {
    if (id) {
      await updateVarOrder(id, formData)
      await saveVarOrderItems(id, effectiveItems)
      message.success('更新成功')
    } else {
      const id = await createVarOrder(formData)
      await saveVarOrderItems(id, effectiveItems)
      message.success('创建成功')
    }
    modalVisible.value = false
    fetchData()
  } catch (e: unknown) {
    console.error(e)
  }
}

function toNumber(value: unknown): number {
  const n = Number(value ?? 0)
  return Number.isFinite(n) ? n : 0
}

function flattenCostSubjects(
  nodes: CostSubjectTreeNode[] = [],
  depth = 0,
): { value: string; label: string }[] {
  return nodes.flatMap((node) => [
    { value: node.id, label: `${'　'.repeat(depth)}${node.subjectName}` },
    ...flattenCostSubjects(node.children ?? [], depth + 1),
  ])
}

async function ensureCostSubjects() {
  if (costSubjectOptions.value.length) return
  try {
    costSubjectOptions.value = flattenCostSubjects(await getCostSubjectTree('COST'))
  } catch (e: unknown) {
    console.error(e)
    message.error('加载成本科目失败')
  }
}

async function loadContractItems(contractId?: string) {
  if (!contractId) {
    itemList.value = []
    return
  }
  contractItemsLoading.value = true
  try {
    const rows = await getContractItems(contractId)
    itemKeyCounter = 0
    itemList.value = rows.map((row: ContractItem) => ({
      key: itemKeyCounter++,
      itemName: row.itemName,
      unit: row.unit,
      quantity: 0,
      unitPrice: toNumber(row.unitPrice),
      amount: 0,
    }))
  } catch (e: unknown) {
    console.error(e)
    itemList.value = []
    message.error('加载合同清单失败')
  } finally {
    contractItemsLoading.value = false
  }
}

function handleAddItem() {
  itemList.value.push({
    key: itemKeyCounter++,
    itemName: '',
    unit: '',
    quantity: 0,
    unitPrice: 0,
    amount: 0,
  })
}
function handleRemoveItem(idx: number) {
  itemList.value.splice(idx, 1)
}
function handleItemQtyChange(idx: number) {
  const item = itemList.value[idx]
  item.amount = toNumber(item.quantity) * toNumber(item.unitPrice)
}
function handleItemPriceChange(idx: number) {
  const item = itemList.value[idx]
  item.amount = toNumber(item.quantity) * toNumber(item.unitPrice)
}

const itemsTotalAmount = computed(() =>
  itemList.value
    .filter((item) => toNumber(item.quantity) > 0)
    .reduce((sum, i) => sum + toNumber(i.amount), 0),
)

const variationStats = computed(() => ({
  total: total.value,
  draft: tableData.value.filter((item) => item.approvalStatus === 'DRAFT').length,
  approved: tableData.value.filter((item) => item.approvalStatus === 'APPROVED').length,
  cost: tableData.value.filter((item) => item.direction === 'COST').length,
}))

function calcPercent(count: number): number {
  const denominator = Number(total.value)
  if (!Number.isFinite(denominator) || denominator <= 0) return 0
  return Math.round((count / denominator) * 100)
}

const variationTypeSummary = computed(() =>
  VAR_TYPE_OPTIONS.map((option) => {
    const count = tableData.value.filter((item) => item.varType === option.value).length
    return {
      label: option.label,
      count,
      percent: calcPercent(count),
    }
  }),
)

const approvalStatusSummary = computed(() => {
  const labels: Record<string, string> = {
    DRAFT: '草稿',
    PENDING: '审批中',
    APPROVED: '已通过',
    REJECTED: '已驳回',
  }
  return Object.entries(labels).map(([key, label]) => {
    const count = tableData.value.filter((item) => item.approvalStatus === key).length
    return {
      key,
      label,
      count,
      percent: calcPercent(count),
    }
  })
})

const recentVariations = computed(() => tableData.value.slice(0, 4))

function fmtWan(val: string | undefined): string {
  if (!val) return '0.00'
  const n = parseFloat(val)
  return isNaN(n) ? '0.00' : (n / 10000).toFixed(2)
}

onMounted(() => {
  referenceStore.fetchProjects()
  referenceStore.fetchContracts({})
  referenceStore.fetchPartners()
  fetchData()
})
</script>

<template>
  <div class="lg-list-page lg-page app-page variation-page">
    <div class="lg-page-head vo-page-head">
      <div class="vo-page-meta-row">
        <a-breadcrumb class="vo-breadcrumb">
          <a-breadcrumb-item>合同管理</a-breadcrumb-item>
          <a-breadcrumb-item>变更签证</a-breadcrumb-item>
        </a-breadcrumb>
        <span class="vo-page-subtitle">统一查看合同变更、现场签证、审批与金额影响</span>
      </div>
    </div>

    <section class="lg-search-bar vo-query-panel" aria-label="变更签证查询条件">
      <div class="vo-query-primary">
        <a-input
          v-model:value="filter.varCode"
          class="vo-keyword-search"
          placeholder="搜索变更编号、名称"
          allow-clear
          size="large"
          @press-enter="handleSearch"
        >
          <template #prefix><SearchOutlined class="vo-search-prefix-icon" /></template>
        </a-input>
        <a-select
          v-model:value="filter.projectId"
          class="vo-query-select"
          placeholder="全部项目"
          allow-clear
          size="large"
          @change="handleProjectChange"
        >
          <a-select-option v-for="p in projectList" :key="p.id" :value="p.id">
            {{ p.projectName }}
          </a-select-option>
        </a-select>
        <a-select
          v-model:value="filter.varType"
          class="vo-query-select"
          placeholder="变更类型"
          allow-clear
          size="large"
          @change="handleSearch"
        >
          <a-select-option v-for="o in VAR_TYPE_OPTIONS" :key="o.value" :value="o.value">
            {{ o.label }}
          </a-select-option>
        </a-select>
        <a-select
          v-model:value="filter.direction"
          class="vo-query-select"
          placeholder="方向"
          allow-clear
          size="large"
          @change="handleSearch"
        >
          <a-select-option v-for="o in DIRECTION_OPTIONS" :key="o.value" :value="o.value">
            {{ o.label }}
          </a-select-option>
        </a-select>
      </div>
      <div class="vo-query-actions">
        <a-button type="primary" size="large" @click="handleSearch">查询</a-button>
        <a-button size="large" @click="handleReset">
          <template #icon><ReloadOutlined /></template>
          重置
        </a-button>
      </div>
    </section>

    <div class="lg-grid vo-workspace">
      <div class="lg-left vo-main-column">
        <div class="vo-kpi-summary" aria-label="变更签证关键指标">
          <div class="vo-kpi-item">
            <span class="vo-kpi-icon is-total"><FileTextOutlined /></span>
            <span class="vo-kpi-label">签证总数</span>
            <span class="vo-kpi-value">{{ variationStats.total }} <small>单</small></span>
          </div>
          <div class="vo-kpi-item">
            <span class="vo-kpi-icon is-approved"><CheckCircleOutlined /></span>
            <span class="vo-kpi-label">已通过</span>
            <span class="vo-kpi-value">{{ variationStats.approved }} <small>单</small></span>
          </div>
          <div class="vo-kpi-item">
            <span class="vo-kpi-icon is-cost"><WalletOutlined /></span>
            <span class="vo-kpi-label">成本方向</span>
            <span class="vo-kpi-value">{{ variationStats.cost }} <small>单</small></span>
          </div>
          <div class="vo-kpi-item is-warn">
            <span class="vo-kpi-icon is-draft"><ExclamationCircleOutlined /></span>
            <span class="vo-kpi-label">草稿待提</span>
            <span class="vo-kpi-value">{{ variationStats.draft }} <small>单</small></span>
          </div>
        </div>

        <main class="lg-list-table-panel vo-table-panel">
          <div class="lg-toolbar vo-table-toolbar">
            <div class="lg-toolbar-left">
              <span class="vo-table-title">变更签证列表</span>
              <span class="vo-table-count">共 {{ total }} 条</span>
              <ColumnSettingsButton
                :columns="columnSettings"
                :visible="colVisible"
                @toggle="toggleCol"
              />
              <a-button aria-label="刷新变更签证列表" title="刷新" @click="fetchData">
                <template #icon><ReloadOutlined /></template>
                刷新
              </a-button>
              <a-button type="primary" @click="handleAdd">
                <template #icon><PlusOutlined /></template>
                新建签证
              </a-button>
            </div>
            <div class="lg-toolbar-right">
              <span class="vo-toolbar-hint">固定表头 / 金额右对齐 / 编号可查看详情</span>
            </div>
          </div>

          <div class="lg-table-wrap vo-table-wrap">
            <vxe-grid
              :data="tableData"
              :columns="visibleGridColumns"
              :loading="loading"
              :column-config="{ resizable: true }"
              stripe
              border="inner"
              size="small"
            >
              <template #varCode="{ row }">
                <a-button class="vo-var-link" type="link" @click="handleView(row)">
                  {{ row.varCode }}
                </a-button>
              </template>
              <template #varType="{ row }">
                <a-tag :color="VAR_TYPE_COLOR[row.varType]" size="small">
                  {{ VAR_TYPE_LABEL[row.varType] ?? row.varType }}
                </a-tag>
              </template>
              <template #direction="{ row }">
                <a-tag :color="row.direction === 'COST' ? 'red' : 'green'" size="small">{{
                  row.direction === 'COST' ? '成本' : row.direction
                }}</a-tag>
              </template>
              <template #reportedAmount="{ row }">
                <span>{{ fmtWan(row.reportedAmount) }} 万</span>
              </template>
              <template #approvedAmount="{ row }">
                <span>{{ fmtWan(row.approvedAmount) }} 万</span>
              </template>
              <template #confirmedAmount="{ row }">
                <span>{{ fmtWan(row.confirmedAmount) }} 万</span>
              </template>
              <template #approvalStatus="{ row }">
                <a-tag :color="APPROVAL_STATUS_COLOR[row.approvalStatus]" size="small">
                  {{ APPROVAL_STATUS_LABEL[row.approvalStatus] ?? row.approvalStatus }}
                </a-tag>
              </template>
              <template #ops="{ row }">
                <a-dropdown :trigger="['click']">
                  <a-button class="lg-row-action-trigger" size="small" type="text">
                    <MoreOutlined />
                  </a-button>
                  <template #overlay>
                    <a-menu>
                      <a-menu-item
                        v-if="row.approvalStatus === 'DRAFT'"
                        @click="handleSubmitApproval(row)"
                      >
                        提交审批
                      </a-menu-item>
                      <a-menu-item @click="handleEdit(row)">编辑</a-menu-item>
                      <a-menu-item danger @click="handleDelete(row)">删除</a-menu-item>
                    </a-menu>
                  </template>
                </a-dropdown>
              </template>
            </vxe-grid>
          </div>

          <div class="lg-pagination vo-pagination">
            <span class="lg-total">共 {{ total }} 条</span>
            <a-pagination
              v-model:current="pageNo"
              v-model:page-size="pageSize"
              :total="total"
              :page-size-options="['10', '20', '50']"
              show-size-changer
              show-quick-jumper
              @change="handlePageChange"
              @show-size-change="handlePageSizeChange"
            />
          </div>
        </main>
      </div>

      <aside class="lg-analysis-rail vo-analysis-rail" aria-label="变更签证辅助分析">
        <div class="vo-analysis-panel">
          <header class="vo-analysis-head">
            <div>
              <div class="vo-analysis-title">签证分析</div>
              <div class="vo-analysis-subtitle">类型、状态与近期记录</div>
            </div>
          </header>

          <section class="vo-analysis-section">
            <div class="vo-section-title">变更类型分布</div>
            <div v-for="item in variationTypeSummary" :key="item.label" class="lg-type-row">
              <span class="lg-type-dot vo-dot-primary"></span>
              <span class="lg-type-label">{{ item.label }}</span>
              <span class="lg-type-num">{{ item.count }}</span>
              <span class="lg-type-pct">{{ item.percent }}%</span>
            </div>
          </section>

          <section class="vo-analysis-section">
            <div class="vo-section-title">审批状态</div>
            <div v-for="item in approvalStatusSummary" :key="item.key" class="lg-type-row">
              <span class="lg-type-dot vo-dot-success"></span>
              <span class="lg-type-label">{{ item.label }}</span>
              <span class="lg-type-num">{{ item.count }}</span>
              <span class="lg-type-pct">{{ item.percent }}%</span>
            </div>
          </section>

          <section class="vo-analysis-section">
            <div class="vo-warning-head">
              <div class="vo-section-title">近期签证</div>
              <span class="vo-warning-count">{{ recentVariations.length }} 项</span>
            </div>
            <div v-for="item in recentVariations" :key="item.id" class="lg-type-row">
              <span class="lg-type-dot vo-dot-warning"></span>
              <span class="lg-type-label">{{ item.varName }}</span>
            </div>
            <div v-if="!recentVariations.length" class="lg-warning-empty">暂无变更签证</div>
          </section>
        </div>
      </aside>
    </div>

    <!-- Modal unchanged -->
    <a-modal
      v-model:open="modalVisible"
      :title="modalTitle"
      :width="800"
      :footer="modalReadonly ? null : undefined"
      @ok="handleSubmit"
    >
      <a-form layout="vertical" :model="formData" :disabled="modalReadonly">
        <a-row :gutter="16">
          <a-col :span="8"
            ><a-form-item label="项目"
              ><a-select
                v-model:value="formData.projectId"
                placeholder="请选择项目"
                style="width: 100%"
                :options="(projectList ?? []).map((p) => ({ value: p.id, label: p.projectName }))"
                @change="
                  (v: string) => {
                    onFormProjectChange(v)
                  }
                " /></a-form-item
          ></a-col>
          <a-col :span="8"
            ><a-form-item label="合同"
              ><a-select
                v-model:value="formData.contractId"
                placeholder="请选择合同"
                style="width: 100%"
                :options="
                  (contractList ?? [])
                    .filter((c) => !formData.projectId || c.projectId === formData.projectId)
                    .map((c) => ({ value: c.id, label: c.contractName }))
                "
                @change="onContractChange" /></a-form-item
          ></a-col>
          <a-col :span="8"
            ><a-form-item label="合作方"
              ><a-input
                :value="formPartnerName"
                disabled
                placeholder="选择合同后自动填充乙方" /></a-form-item
          ></a-col>
        </a-row>
        <a-row :gutter="16">
          <a-col :span="8"
            ><a-form-item label="变更类型"
              ><a-select v-model:value="formData.varType" placeholder="请选择" style="width: 100%"
                ><a-select-option v-for="o in VAR_TYPE_OPTIONS" :key="o.value" :value="o.value">{{
                  o.label
                }}</a-select-option></a-select
              ></a-form-item
            ></a-col
          >
          <a-col :span="8"
            ><a-form-item label="变更名称"
              ><a-input v-model:value="formData.varName" placeholder="变更名称" /></a-form-item
          ></a-col>
          <a-col :span="8"
            ><a-form-item label="方向"
              ><a-select v-model:value="formData.direction" placeholder="请选择"
                ><a-select-option
                  v-for="o in DIRECTION_OPTIONS"
                  :key="o.value"
                  :value="o.value"
                  :disabled="o.disabled"
                  >{{ o.label }}</a-select-option
                ></a-select
              ></a-form-item
            ></a-col
          >
        </a-row>
        <a-row :gutter="16">
          <a-col :span="8"
            ><a-form-item label="影响工期(天)"
              ><a-input-number
                v-model:value="formData.impactDays"
                :min="0"
                style="width: 100%" /></a-form-item
          ></a-col>
          <a-col :span="8"
            ><a-form-item label="业主确认"
              ><a-switch
                :checked="formData.ownerConfirmFlag === 1"
                @change="(v: boolean) => (formData.ownerConfirmFlag = v ? 1 : 0)" /></a-form-item
          ></a-col>
          <a-col :span="8"
            ><a-form-item label="备注"
              ><a-textarea v-model:value="formData.remark" :rows="2" /></a-form-item
          ></a-col>
        </a-row>
      </a-form>
      <div style="border-top: 1px solid #f0f0f0; padding-top: 12px; margin-top: 4px">
        <div
          style="
            display: flex;
            justify-content: space-between;
            align-items: center;
            margin-bottom: 10px;
          "
        >
          <span style="font-weight: 600; font-size: 14px">变更明细</span
          ><a-button type="dashed" size="small" :disabled="modalReadonly" @click="handleAddItem"
            >+ 添加明细</a-button
          >
        </div>
        <a-table
          :data-source="itemList"
          :loading="contractItemsLoading"
          :pagination="false"
          row-key="key"
          size="small"
          :scroll="{ x: 900, y: 250 }"
        >
          <a-table-column title="清单项名称" width="160"
            ><template #default="{ record: item }"
              ><a-input
                v-model:value="item.itemName"
                placeholder="名称"
                :disabled="modalReadonly"
                style="width: 100%" /></template
          ></a-table-column>
          <a-table-column title="单位" width="70"
            ><template #default="{ record: item }"
              ><a-input
                v-model:value="item.unit"
                placeholder="单位"
                :disabled="modalReadonly"
                style="width: 100%" /></template
          ></a-table-column>
          <a-table-column title="成本科目" width="180"
            ><template #default="{ record: item }"
              ><a-select
                v-model:value="item.costSubjectId"
                placeholder="选择成本科目"
                :options="costSubjectOptions"
                show-search
                option-filter-prop="label"
                popup-match-select-width="false"
                :dropdown-style="{ minWidth: '280px' }"
                :disabled="modalReadonly"
                style="width: 100%" /></template
          ></a-table-column>
          <a-table-column title="数量" width="120"
            ><template #default="{ record: item, index }"
              ><a-input-number
                v-model:value="item.quantity"
                :min="0"
                :precision="4"
                :disabled="modalReadonly"
                style="width: 100%"
                @change="handleItemQtyChange(index)" /></template
          ></a-table-column>
          <a-table-column title="单价(元)" width="130"
            ><template #default="{ record: item, index }"
              ><a-input-number
                v-model:value="item.unitPrice"
                :min="0"
                :precision="4"
                :disabled="modalReadonly"
                style="width: 100%"
                @change="handleItemPriceChange(index)" /></template
          ></a-table-column>
          <a-table-column title="金额(元)" width="130"
            ><template #default="{ record: item }"
              ><span>{{
                Number(item.amount || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2 })
              }}</span></template
            ></a-table-column
          >
          <a-table-column title="操作" width="76"
            ><template #default="{ index }"
              ><a-button
                type="link"
                size="small"
                danger
                :disabled="modalReadonly"
                @click="handleRemoveItem(index)"
                >删除</a-button
              ></template
            ></a-table-column
          >
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
.variation-page {
  gap: 14px;
}

.vo-page-head {
  align-items: center;
  justify-content: space-between;
  min-height: 0;
  padding: 0;
}

.vo-breadcrumb {
  font-size: 13px;
  line-height: 20px;
}

.vo-page-meta-row {
  display: flex;
  align-items: center;
  gap: 5em;
  min-width: 0;
}

.vo-page-subtitle {
  color: var(--text-secondary);
  font-size: 13px;
  line-height: 20px;
  white-space: nowrap;
}

.vo-query-panel {
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  min-height: 74px;
}

.vo-query-primary {
  display: flex;
  flex: 1 1 auto;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.vo-keyword-search {
  width: min(640px, 34vw);
  min-width: 420px;
}

.vo-search-prefix-icon {
  color: var(--text-secondary);
}

.vo-query-select {
  width: 160px;
}

.vo-query-actions {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  gap: 8px;
}

.vo-workspace {
  align-items: stretch;
  min-height: 0;
}

.vo-main-column {
  gap: 12px;
}

.vo-kpi-summary {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 0;
  overflow: hidden;
  min-height: 84px;
  background: var(--surface);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-soft);
}

.vo-kpi-item {
  position: relative;
  display: grid;
  grid-template-columns: 38px minmax(0, 1fr);
  grid-template-rows: 19px 27px;
  column-gap: 10px;
  align-items: center;
  min-width: 0;
  padding: 16px 18px;
  border-right: 1px solid var(--border-subtle);
}

.vo-kpi-item:last-child {
  border-right: 0;
}

.vo-kpi-icon {
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

.vo-kpi-icon.is-approved {
  color: var(--success);
  background: var(--success-soft);
}

.vo-kpi-icon.is-cost {
  color: var(--warning);
  background: var(--warning-soft);
}

.vo-kpi-icon.is-draft {
  color: var(--error);
  background: var(--error-soft);
}

.vo-kpi-label {
  overflow: hidden;
  color: var(--text-secondary);
  font-size: 13px;
  font-weight: 600;
  line-height: 18px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.vo-kpi-value {
  overflow: hidden;
  color: var(--text);
  font-size: 24px;
  font-weight: 800;
  line-height: 28px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.vo-kpi-value small {
  margin-left: 4px;
  color: var(--text-secondary);
  font-size: 13px;
  font-weight: 600;
}

.vo-table-panel {
  overflow: hidden;
  border: 1px solid var(--border-subtle);
}

.vo-table-toolbar {
  border-bottom: 1px solid var(--border-subtle);
}

.vo-table-title {
  color: var(--text);
  font-size: 15px;
  font-weight: 700;
}

.vo-table-count,
.vo-toolbar-hint,
.vo-analysis-subtitle,
.vo-warning-count {
  color: var(--text-secondary);
  font-size: 13px;
}

.vo-table-wrap {
  min-height: 520px;
}

.vo-table-wrap :deep(.vxe-header--column .vxe-cell) {
  justify-content: center;
  text-align: center;
}

.vo-var-link {
  height: auto;
  padding: 0;
  font-weight: 700;
}

.vo-var-link,
.vo-var-link:hover,
.vo-var-link:focus {
  background: transparent;
}

.vo-pagination {
  border-top: 1px solid var(--border-subtle);
}

.vo-analysis-rail {
  width: 336px;
}

.vo-analysis-panel {
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

.vo-analysis-head,
.vo-warning-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.vo-analysis-title {
  color: var(--text);
  font-size: 16px;
  font-weight: 800;
  line-height: 22px;
}

.vo-analysis-section {
  display: flex;
  flex-direction: column;
  gap: 10px;
  min-width: 0;
  padding-top: 16px;
  border-top: 1px solid var(--border-subtle);
}

.vo-section-title {
  color: var(--text);
  font-size: 14px;
  font-weight: 700;
  line-height: 20px;
}

.vo-analysis-section :deep(.lg-type-row),
.vo-analysis-section .lg-type-row {
  grid-template-columns: 9px minmax(60px, 1fr) 28px 38px;
}

.vo-dot-primary {
  background: var(--primary);
}

.vo-dot-success {
  background: var(--success);
}

.vo-dot-warning {
  background: var(--warning);
}

@media (max-width: 1200px) {
  .vo-page-head,
  .vo-query-panel,
  .vo-query-primary {
    align-items: stretch;
    flex-direction: column;
  }

  .vo-query-actions {
    justify-content: flex-start;
  }

  .vo-keyword-search,
  .vo-query-select,
  .vo-analysis-rail {
    width: 100%;
    min-width: 0;
  }
}
</style>
