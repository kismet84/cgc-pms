<script setup lang="ts">
import { ref, reactive, onMounted, computed } from 'vue'
import { storeToRefs } from 'pinia'
import { message, Modal } from 'ant-design-vue'
import {
  PlusOutlined,
  ReloadOutlined,
  SearchOutlined,
} from '@ant-design/icons-vue'
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
import type { ContractItem } from '@/types/contract'

const filter = reactive({
  projectId: undefined as string | undefined,
  contractId: undefined as string | undefined,
  partnerId: undefined as string | undefined,
  status: undefined as string | undefined,
  measureCode: '',
})

const loading = ref(false)
const tableData = ref<SubMeasureVO[]>([])
const total = ref(0)
const pageNo = ref(1)
const pageSize = ref(20)

const referenceStore = useReferenceStore()
const {
  projects: projectList,
  contracts: contractList,
  partners: partnerList,
} = storeToRefs(referenceStore)
const contractItemList = ref<ContractItem[]>([])

const modalVisible = ref(false)
const modalTitle = ref('新建分包计量')
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

// ---- vxe-grid columns ----
const gridColumns = computed(() => [
  { field: 'measureCode', title: '计量编号', width: 140, ellipsis: true },
  { field: 'measurePeriod', title: '计量期次', width: 100 },
  { field: 'projectName', title: '项目名称', width: 120, ellipsis: true },
  { field: 'contractName', title: '合同名称', width: 120, ellipsis: true },
  { field: 'partnerName', title: '分包商', width: 120, ellipsis: true },
  {
    field: 'reportedAmount',
    title: '申报金额',
    width: 100,
    align: 'right' as const,
    slots: { default: 'reportedAmount' },
  },
  {
    field: 'approvedAmount',
    title: '审核金额',
    width: 100,
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
  { field: 'measureDate', title: '计量日期', width: 100 },
  { field: 'status', title: '状态', width: 80, slots: { default: 'status' } },
  { field: 'approvalStatus', title: '审批状态', width: 90, slots: { default: 'approvalStatus' } },
  { title: '操作', width: 110, slots: { default: 'action' } },
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
      measureCode: filter.measureCode || undefined,
    })
    tableData.value = res.records
    total.value = res.total
  } catch (e: unknown) {
    console.error(e)
    tableData.value = []
    total.value = 0
    message.error('加载分包计量列表失败，请稍后重试')
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
  modalTitle.value = '编辑分包计量'
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
    message.error('加载明细失败')
    itemList.value = []
  }
  modalVisible.value = true
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
          <a-breadcrumb-item>分包管理</a-breadcrumb-item>
          <a-breadcrumb-item>分包计量</a-breadcrumb-item>
        </a-breadcrumb>
      </div>
    </div>

    <!-- 搜索栏 -->
    <div class="lg-search-bar">
      <a-input
        v-model:value="filter.measureCode"
        placeholder="搜索计量编号…"
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

    <!-- KPI 横条 -->
    <div class="lg-kpi-strip">
      <div class="lg-kpi-card">
        <span class="lg-kpi-card-label">计量总数</span>
        <span class="lg-kpi-card-value">{{ kpiTotalCount }} <small>条</small></span>
        <span class="lg-kpi-card-bar"><span style="width:100%;background:var(--kpi-total)"></span></span>
      </div>
      <div class="lg-kpi-card">
        <span class="lg-kpi-card-label">申报总额</span>
        <span class="lg-kpi-card-value">{{ fmtAmount(kpiMeasureTotal) }} <small>元</small></span>
        <span class="lg-kpi-card-bar"><span style="width:100%;background:var(--kpi-amount)"></span></span>
      </div>
      <div class="lg-kpi-card">
        <span class="lg-kpi-card-label">已审核金额</span>
        <span class="lg-kpi-card-value">{{ fmtAmount(kpiApproved) }} <small>元</small></span>
        <span class="lg-kpi-card-bar"><span :style="{ width: (kpiMeasureTotal ? Math.round((kpiApproved / kpiMeasureTotal) * 100) : 0) + '%', background: 'var(--kpi-paid)' }"></span></span>
        <span class="lg-kpi-card-hint" v-if="kpiMeasureTotal">{{ kpiMeasureTotal ? Math.round((kpiApproved / kpiMeasureTotal) * 100) : 0 }}%</span>
      </div>
      <div class="lg-kpi-card is-warn" v-if="kpiMeasurePending > 0">
        <span class="lg-kpi-card-label">待审核</span>
        <span class="lg-kpi-card-value">{{ kpiMeasurePending }} <small>条</small></span>
        <span class="lg-kpi-card-bar"><span :style="{ width: (kpiTotalCount ? Math.round((kpiMeasurePending / kpiTotalCount) * 100) : 0) + '%', background: 'var(--kpi-overdue)' }"></span></span>
        <span class="lg-kpi-card-hint" v-if="kpiTotalCount">{{ kpiTotalCount ? Math.round((kpiMeasurePending / kpiTotalCount) * 100) : 0 }}%</span>
      </div>
    </div>

    <!-- 工具栏 -->
    <div class="lg-toolbar">
      <div class="lg-toolbar-left">
        <a-button type="primary" @click="handleAdd">
          <template #icon><PlusOutlined /></template>
          新建计量
        </a-button>
        <a-button @click="fetchData">
          <template #icon><ReloadOutlined /></template>
        </a-button>
      </div>
      <div class="lg-toolbar-right">
        <a-select
          v-model:value="filter.projectId"
          placeholder="全部项目"
          allow-clear
          style="width: 160px"
          size="small"
          show-search
          :filter-option="(input: string, option: any) => option.label?.toLowerCase().includes(input.toLowerCase())"
          @change="(v: string | undefined) => { filter.contractId = undefined; if (v) referenceStore.fetchContracts({ projectId: v }); handleSearch() }"
        >
          <a-select-option v-for="p in projectList" :key="p.id" :value="p.id">
            {{ p.projectName }}
          </a-select-option>
        </a-select>
        <a-select
          v-model:value="filter.contractId"
          placeholder="全部分包合同"
          allow-clear
          style="width: 160px"
          size="small"
          show-search
          :filter-option="(input: string, option: any) => option.label?.toLowerCase().includes(input.toLowerCase())"
          @change="handleSearch"
        >
          <a-select-option v-for="c in contractList" :key="c.id" :value="c.id">
            {{ c.contractName }}
          </a-select-option>
        </a-select>
        <a-select
          v-model:value="filter.partnerId"
          placeholder="全部分包商"
          allow-clear
          style="width: 140px"
          size="small"
          show-search
          :filter-option="(input: string, option: any) => option.label?.toLowerCase().includes(input.toLowerCase())"
          @change="handleSearch"
        >
          <a-select-option v-for="p in partnerList" :key="p.id" :value="p.id">
            {{ p.partnerName }}
          </a-select-option>
        </a-select>
        <a-select
          v-model:value="filter.status"
          placeholder="全部状态"
          allow-clear
          style="width: 110px"
          size="small"
          @change="handleSearch"
        >
          <a-select-option value="DRAFT">草稿</a-select-option>
          <a-select-option value="APPROVING">审批中</a-select-option>
          <a-select-option value="CONFIRMED">已确认</a-select-option>
          <a-select-option value="COMPLETED">已完成</a-select-option>
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
            <a class="lg-link" @click="handleEdit(row)">编辑</a>
            <a class="lg-link lg-del" @click="handleDelete(row)">删除</a>
            <a
              v-if="row.approvalStatus === 'DRAFT'"
              class="lg-link"
              @click="handleSubmitApproval(row)"
            >提交审批</a>
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
              (input: string, option: any) =>
                option.label?.toLowerCase().includes(input.toLowerCase())
            "
          >
            <a-select-option v-for="p in projectList" :key="p.id" :value="p.id">
              {{ p.projectName }}
            </a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="分包合同">
          <a-select
            v-model:value="formData.contractId"
            placeholder="请选择合同"
            allow-clear
            show-search
            :filter-option="
              (input: string, option: any) =>
                option.label?.toLowerCase().includes(input.toLowerCase())
            "
            @change="(val: string) => onContractSelect(val)"
          >
            <a-select-option v-for="c in contractList" :key="c.id" :value="c.id">
              {{ c.contractName }}
            </a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="分包商">
          <a-input :value="formPartnerName" disabled placeholder="选择合同后自动填充乙方" />
        </a-form-item>
        <a-form-item label="计量期次">
          <a-input
            v-model:value="formData.measurePeriod"
            placeholder="请输入计量期次（如：第1期）"
          />
        </a-form-item>
        <a-form-item label="计量日期">
          <a-date-picker
            v-model:value="formData.measureDate"
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
          <span style="font-weight: 600; font-size: 14px">计量明细</span>
          <a-button type="dashed" size="small" @click="handleAddItem">+ 添加明细</a-button>
        </div>

        <a-table
          :data-source="itemList"
          :pagination="false"
          row-key="key"
          size="small"
          :scroll="{ y: 250 }"
        >
          <a-table-column title="合同清单项" width="200">
            <template #default="{ record: item, index }">
              <a-select
                :value="item.contractItemId"
                placeholder="请选择清单项"
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
          <a-table-column title="单位" width="70">
            <template #default="{ record: item }">
              <span>{{ item.unit || '-' }}</span>
            </template>
          </a-table-column>
          <a-table-column title="合同量" width="100">
            <template #default="{ record: item }">
              <span>{{ item.contractQuantity || '-' }}</span>
            </template>
          </a-table-column>
          <a-table-column title="本期量" width="120">
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
          <a-table-column title="单价(元)" width="130">
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
          <a-table-column title="金额(元)" width="130">
            <template #default="{ record: item }">
              <span>{{
                Number(item.amount || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2 })
              }}</span>
            </template>
          </a-table-column>
          <a-table-column title="操作" width="60">
            <template #default="{ record: _item, index }">
              <a-button type="link" size="small" danger @click="handleRemoveItem(index)"
                >删除</a-button
              >
            </template>
          </a-table-column>
        </a-table>

        <div style="text-align: right; margin-top: 8px; font-size: 14px">
          合计：<span style="font-weight: 600; color: #1677ff"
            >{{ Number(itemsTotalAmount).toLocaleString('zh-CN', { minimumFractionDigits: 2 }) }}</span
          >
        </div>
      </div>
    </a-modal>
  </div>
</template>

<style scoped>
/* 页面专属样式 — 其余已由 lg-* 全局类覆盖 */
.lg-breadcrumb {
  margin-bottom: 5px;
  font-size: 13px;
}
.lg-none {
  color: var(--muted);
}
</style>
