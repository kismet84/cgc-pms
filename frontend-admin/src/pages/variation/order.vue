<script setup lang="ts">
import { ref, reactive, onMounted, computed, watch } from 'vue'
import { storeToRefs } from 'pinia'
import { message, Modal } from 'ant-design-vue'
import {
  PlusOutlined,
  ReloadOutlined,
  SettingOutlined,
  SearchOutlined,
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
import { useReferenceStore } from '@/stores/reference'
import type { VarOrderVO, VarOrderItemVO } from '@/types/variation'

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
const {
  projects: projectList,
  contracts: contractList,
  partners: partnerList,
} = storeToRefs(referenceStore)

const modalVisible = ref(false)
const modalTitle = ref('新建变更签证')
const editingId = ref<string | null>(null)
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

const itemList = ref<(Partial<VarOrderItemVO> & { key: number })[]>([])
let itemKeyCounter = 0

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

// ---- Column visibility ----
const COLS_KEY = 'var_order_cols'
const defaultCols: Record<string, boolean> = {
  varCode: true,
  varName: true,
  varType: true,
  direction: true,
  projectName: true,
  contractName: true,
  partnerName: true,
  reportedAmount: true,
  approvedAmount: true,
  confirmedAmount: true,
  approvalStatus: true,
  ops: true,
}
let saved: Record<string, boolean> = defaultCols
try {
  const raw = localStorage.getItem(COLS_KEY)
  if (raw) saved = JSON.parse(raw)
} catch (e: unknown) {
  console.error(e)
  localStorage.removeItem(COLS_KEY)
}
const colVisible = reactive<Record<string, boolean>>({ ...defaultCols, ...saved })
function toggleCol(key: string) {
  colVisible[key] = !colVisible[key]
  localStorage.setItem(COLS_KEY, JSON.stringify(colVisible))
}

const COL_LABELS: Record<string, string> = {
  varCode: '变更编号',
  varName: '变更名称',
  varType: '变更类型',
  direction: '方向',
  projectName: '项目名称',
  contractName: '合同名称',
  partnerName: '合作方',
  reportedAmount: '上报金额',
  approvedAmount: '审定金额',
  confirmedAmount: '确认金额',
  approvalStatus: '审批状态',
  ops: '操作',
}

// ---- VxeGrid columns ----
const gridColumns = computed(() => [
  { type: 'seq' as const, width: 50, fixed: 'left' as const },
  ...(colVisible.varCode
    ? [{ field: 'varCode', title: '变更编号', width: 140, ellipsis: true }]
    : []),
  ...(colVisible.varName
    ? [{ field: 'varName', title: '变更名称', width: 140, ellipsis: true }]
    : []),
  ...(colVisible.varType
    ? [{ field: 'varType', title: '变更类型', width: 90, slots: { default: 'varType' } }]
    : []),
  ...(colVisible.direction
    ? [{ field: 'direction', title: '方向', width: 70, slots: { default: 'direction' } }]
    : []),
  ...(colVisible.projectName
    ? [{ field: 'projectName', title: '项目名称', width: 120, ellipsis: true }]
    : []),
  ...(colVisible.contractName
    ? [{ field: 'contractName', title: '合同名称', width: 120, ellipsis: true }]
    : []),
  ...(colVisible.partnerName
    ? [{ field: 'partnerName', title: '合作方', width: 120, ellipsis: true }]
    : []),
  ...(colVisible.reportedAmount
    ? [
        {
          field: 'reportedAmount',
          title: '上报金额',
          width: 100,
          align: 'right' as const,
          slots: { default: 'reportedAmount' },
        },
      ]
    : []),
  ...(colVisible.approvedAmount
    ? [
        {
          field: 'approvedAmount',
          title: '审定金额',
          width: 100,
          align: 'right' as const,
          slots: { default: 'approvedAmount' },
        },
      ]
    : []),
  ...(colVisible.confirmedAmount
    ? [
        {
          field: 'confirmedAmount',
          title: '确认金额',
          width: 100,
          align: 'right' as const,
          slots: { default: 'confirmedAmount' },
        },
      ]
    : []),
  ...(colVisible.approvalStatus
    ? [
        {
          field: 'approvalStatus',
          title: '审批状态',
          width: 90,
          slots: { default: 'approvalStatus' },
        },
      ]
    : []),
  ...(colVisible.ops
    ? [{ title: '操作', width: 110, fixed: 'right' as const, slots: { default: 'ops' } }]
    : []),
])

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
    total.value = res.total
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
  modalTitle.value = '新建变更签证'
  editingId.value = null
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
  modalVisible.value = true
}

async function handleEdit(record: VarOrderVO) {
  modalTitle.value = '编辑变更签证'
  editingId.value = record.id
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
  const id = editingId.value
  try {
    if (id) {
      await updateVarOrder(id, formData)
      await saveVarOrderItems(id, itemList.value)
      message.success('更新成功')
    } else {
      const id = await createVarOrder(formData)
      await saveVarOrderItems(id, itemList.value)
      message.success('创建成功')
    }
    modalVisible.value = false
    fetchData()
  } catch (e: unknown) {
    console.error(e)
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
  item.amount = (item.quantity ?? 0) * (item.unitPrice ?? 0)
}
function handleItemPriceChange(idx: number) {
  const item = itemList.value[idx]
  item.amount = (item.quantity ?? 0) * (item.unitPrice ?? 0)
}

const itemsTotalAmount = computed(() => itemList.value.reduce((sum, i) => sum + (i.amount ?? 0), 0))

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
  <div class="lg-page app-page">
    <div class="lg-page-head">
      <div>
        <a-breadcrumb style="margin-bottom: 5px; font-size: 13px">
          <a-breadcrumb-item>变更签证</a-breadcrumb-item>
        </a-breadcrumb>
      </div>
    </div>

    <!-- 搜索栏 -->
    <div class="lg-search-bar">
      <a-input
        v-model:value="filter.varCode"
        placeholder="搜索变更编号…"
        allow-clear
        size="large"
        @press-enter="handleSearch"
      >
        <template #prefix><SearchOutlined style="color: #697380" /></template>
      </a-input>
      <a-button type="primary" size="large" @click="handleSearch">查询</a-button>
      <a-button size="large" @click="handleReset">
        <template #icon><ReloadOutlined /></template>
        重置
      </a-button>
    </div>

    <!-- 工具栏 -->
    <div class="lg-toolbar">
      <div class="lg-toolbar-left">
        <a-button type="primary" @click="handleAdd">
          <template #icon><PlusOutlined /></template>
          新建
        </a-button>
        <a-dropdown>
          <a-button>
            <template #icon><SettingOutlined /></template>
            列设置
          </a-button>
          <template #overlay>
            <a-menu>
              <a-menu-item v-for="(_, key) in defaultCols" :key="key" @click="toggleCol(key)">
                <a-checkbox :checked="colVisible[key]">
                  {{ COL_LABELS[key] }}
                </a-checkbox>
              </a-menu-item>
            </a-menu>
          </template>
        </a-dropdown>
        <a-button @click="fetchData">
          <template #icon><ReloadOutlined /></template>
        </a-button>
      </div>
      <div class="lg-toolbar-right">
        <a-select
          v-model:value="filter.projectId"
          placeholder="全部项目"
          allow-clear
          style="width: 140px"
          size="small"
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

    <!-- 表格 -->
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
        <template #varType="{ row }">
          <a-tag size="small">{{ VAR_TYPE_LABEL[row.varType] ?? row.varType }}</a-tag>
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
          <a-tag
            :color="
              row.approvalStatus === 'APPROVED'
                ? 'success'
                : row.approvalStatus === 'REJECTED'
                  ? 'error'
                  : 'processing'
            "
            size="small"
          >{{ row.approvalStatus }}</a-tag>
        </template>
        <template #ops="{ row }">
          <div class="lg-ops">
            <a
              v-if="row.approvalStatus === 'DRAFT'"
              class="lg-link"
              @click="handleSubmitApproval(row)"
            >提交审批</a>
            <a class="lg-link" @click="handleEdit(row)">编辑</a>
            <a class="lg-link lg-del" @click="handleDelete(row)">删除</a>
          </div>
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
        :page-size-options="['10', '20', '50']"
        show-size-changer
        show-quick-jumper
        @change="handlePageChange"
        @show-size-change="handlePageSizeChange"
      />
    </div>

    <!-- Modal unchanged -->
    <a-modal v-model:open="modalVisible" :title="modalTitle" :width="860" @ok="handleSubmit">
      <a-form layout="vertical" :model="formData">
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
                    formData.contractId = undefined
                    formData.partnerId = undefined
                    referenceStore.fetchContracts({ projectId: v })
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
          ><a-button type="dashed" size="small" @click="handleAddItem">+ 添加明细</a-button>
        </div>
        <a-table
          :data-source="itemList"
          :pagination="false"
          row-key="key"
          size="small"
          :scroll="{ y: 250 }"
        >
          <a-table-column title="清单项名称" width="160"
            ><template #default="{ record: item }"
              ><a-input
                v-model:value="item.itemName"
                placeholder="名称"
                style="width: 100%" /></template
          ></a-table-column>
          <a-table-column title="单位" width="70"
            ><template #default="{ record: item }"
              ><a-input
                v-model:value="item.unit"
                placeholder="单位"
                style="width: 100%" /></template
          ></a-table-column>
          <a-table-column title="数量" width="120"
            ><template #default="{ record: item, index }"
              ><a-input-number
                v-model:value="item.quantity"
                :min="0"
                :precision="4"
                style="width: 100%"
                @change="handleItemQtyChange(index)" /></template
          ></a-table-column>
          <a-table-column title="单价(元)" width="130"
            ><template #default="{ record: item, index }"
              ><a-input-number
                v-model:value="item.unitPrice"
                :min="0"
                :precision="4"
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
          <a-table-column title="操作" width="60"
            ><template #default="{ index }"
              ><a-button type="link" size="small" danger @click="handleRemoveItem(index)"
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
