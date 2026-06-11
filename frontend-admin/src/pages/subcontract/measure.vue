<script setup lang="ts">
import { ref, reactive, onMounted, computed } from 'vue'
import { message, Modal } from 'ant-design-vue'
import {
  getMeasureList,
  createMeasure,
  updateMeasure,
  deleteMeasure,
  getMeasureItems,
  saveMeasureItems,
} from '@/api/modules/subcontract'
import { getProjectList } from '@/api/modules/project'
import { getContractLedger, getContractItems } from '@/api/modules/contract'
import { getPartnerList } from '@/api/modules/partner'
import type { SubMeasureVO, SubMeasureItemVO } from '@/types/subcontract'
import type { ProjectVO } from '@/types/project'
import type { ContractVO, ContractItem } from '@/types/contract'
import type { PartnerVO } from '@/types/partner'

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

const projectList = ref<ProjectVO[]>([])
const contractList = ref<ContractVO[]>([])
const partnerList = ref<PartnerVO[]>([])
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

const columns = [
  { title: '计量编号', dataIndex: 'measureCode', width: 160 },
  { title: '计量期次', dataIndex: 'measurePeriod', width: 120, key: 'measurePeriod' },
  { title: '项目名称', dataIndex: 'projectName', width: 140 },
  { title: '合同名称', dataIndex: 'contractName', width: 140 },
  { title: '分包商', dataIndex: 'partnerName', width: 140 },
  { title: '申报金额', dataIndex: 'reportedAmount', width: 120, key: 'reportedAmount' },
  { title: '审核金额', dataIndex: 'approvedAmount', width: 120, key: 'approvedAmount' },
  { title: '净额', dataIndex: 'netAmount', width: 120, key: 'netAmount' },
  { title: '计量日期', dataIndex: 'measureDate', width: 110 },
  { title: '状态', dataIndex: 'status', width: 100, key: 'status' },
  { title: '审批状态', dataIndex: 'approvalStatus', width: 100, key: 'approvalStatus' },
  { title: '操作', key: 'action', width: 140, fixed: 'right' },
]

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
  } catch {
    tableData.value = []
    total.value = 0
    message.error('加载分包计量列表失败，请稍后重试')
  } finally {
    loading.value = false
  }
}

async function fetchProjects() {
  try {
    const res = await getProjectList({ pageNum: 1, pageSize: 500 })
    projectList.value = res.records
  } catch {
    projectList.value = []
  }
}

async function fetchContracts() {
  try {
    const res = await getContractLedger({ pageNo: 1, pageSize: 500, contractType: 'SUB' })
    contractList.value = res.records
  } catch {
    contractList.value = []
  }
}

async function fetchPartners() {
  try {
    const res = await getPartnerList({ pageNum: 1, pageSize: 500, partnerType: 'SUB' })
    partnerList.value = res.records
  } catch {
    partnerList.value = []
  }
}

async function loadContractItems(contractId: string) {
  try {
    const items = await getContractItems(contractId)
    contractItemList.value = items
  } catch {
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
  } catch {
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
      } catch {
        message.error('删除失败，请稍后重试')
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
    await loadContractItems(contractId)
  } else {
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
  } catch {
    message.error('操作失败，请稍后重试')
  }
}

function handleModalCancel() {
  modalVisible.value = false
}

onMounted(() => {
  fetchProjects()
  fetchContracts()
  fetchPartners()
  fetchData()
})
</script>

<template>
  <div class="pm-page">
    <a-page-header title="分包计量" class="pm-header" />

    <!-- Filter -->
    <div class="pm-card pm-filter">
      <div class="pm-filter-row">
        <div class="pm-field">
          <label>项目：</label>
          <a-select v-model:value="filter.projectId" placeholder="全部" allow-clear style="width:180px">
            <a-select-option v-for="p in projectList" :key="p.id" :value="p.id">
              {{ p.projectName }}
            </a-select-option>
          </a-select>
        </div>
        <div class="pm-field">
          <label>分包合同：</label>
          <a-select v-model:value="filter.contractId" placeholder="全部" allow-clear style="width:180px">
            <a-select-option v-for="c in contractList" :key="c.id" :value="c.id">
              {{ c.contractName }}
            </a-select-option>
          </a-select>
        </div>
        <div class="pm-field">
          <label>分包商：</label>
          <a-select v-model:value="filter.partnerId" placeholder="全部" allow-clear style="width:160px">
            <a-select-option v-for="p in partnerList" :key="p.id" :value="p.id">
              {{ p.partnerName }}
            </a-select-option>
          </a-select>
        </div>
        <div class="pm-field">
          <label>状态：</label>
          <a-select v-model:value="filter.status" placeholder="全部" allow-clear style="width:110px">
            <a-select-option value="DRAFT">草稿</a-select-option>
            <a-select-option value="APPROVING">审批中</a-select-option>
            <a-select-option value="CONFIRMED">已确认</a-select-option>
            <a-select-option value="COMPLETED">已完成</a-select-option>
          </a-select>
        </div>
        <div class="pm-field">
          <label>计量编号：</label>
          <a-input v-model:value="filter.measureCode" placeholder="请输入编号" style="width:150px" allow-clear />
        </div>
        <div class="pm-filter-actions">
          <a-button type="primary" @click="handleSearch">查询</a-button>
          <a-button @click="handleReset">重置</a-button>
          <a-button type="primary" @click="handleAdd">新建计量</a-button>
        </div>
      </div>
    </div>

    <!-- Table -->
    <div class="pm-card pm-table-wrap">
      <a-table
        :columns="columns"
        :data-source="tableData"
        :loading="loading"
        :pagination="false"
        row-key="id"
        size="small"
        :scroll="{ x: 1500 }"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'measurePeriod'">
            <a class="pm-link">{{ record.measurePeriod }}</a>
          </template>
          <template v-else-if="column.key === 'reportedAmount'">
            <span v-if="record.reportedAmount">¥{{ Number(record.reportedAmount).toLocaleString('zh-CN', { minimumFractionDigits: 2 }) }}</span>
            <span v-else class="pm-none">-</span>
          </template>
          <template v-else-if="column.key === 'approvedAmount'">
            <span v-if="record.approvedAmount">¥{{ Number(record.approvedAmount).toLocaleString('zh-CN', { minimumFractionDigits: 2 }) }}</span>
            <span v-else class="pm-none">-</span>
          </template>
          <template v-else-if="column.key === 'netAmount'">
            <span v-if="record.netAmount !== undefined && record.netAmount !== null">¥{{ Number(record.netAmount).toLocaleString('zh-CN', { minimumFractionDigits: 2 }) }}</span>
            <span v-else class="pm-none">-</span>
          </template>
          <template v-else-if="column.key === 'status'">
            <a-tag :color="STATUS_COLOR[record.status]">
              {{ STATUS_LABEL[record.status] ?? record.status }}
            </a-tag>
          </template>
          <template v-else-if="column.key === 'approvalStatus'">
            <ApprovalStatusTag :status="(record.approvalStatus as any)" />
          </template>
          <template v-else-if="column.key === 'action'">
            <a-button type="link" size="small" @click="handleEdit(record)">编辑</a-button>
            <a-button type="link" size="small" danger @click="handleDelete(record)">删除</a-button>
          </template>
        </template>
      </a-table>
    </div>

    <!-- Pagination -->
    <div class="pm-pagination">
      <span class="pm-total">共 {{ total }} 条</span>
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
      <a-form :label-col="{ span: 6 }" :wrapper-col="{ span: 16 }" style="margin-bottom:8px">
        <a-form-item label="项目" required>
          <a-select v-model:value="formData.projectId" placeholder="请选择项目">
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
            @change="(val: string) => onContractSelect(val)"
          >
            <a-select-option v-for="c in contractList" :key="c.id" :value="c.id">
              {{ c.contractName }}
            </a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="分包商">
          <a-select v-model:value="formData.partnerId" placeholder="请选择分包商" allow-clear>
            <a-select-option v-for="p in partnerList" :key="p.id" :value="p.id">
              {{ p.partnerName }}
            </a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="计量期次">
          <a-input v-model:value="formData.measurePeriod" placeholder="请输入计量期次（如：第1期）" />
        </a-form-item>
        <a-form-item label="计量日期">
          <a-date-picker v-model:value="formData.measureDate" style="width: 100%" />
        </a-form-item>
        <a-form-item label="备注">
          <a-textarea v-model:value="formData.remark" :rows="2" placeholder="请输入备注" />
        </a-form-item>
      </a-form>

      <!-- Line Items Section -->
      <div style="border-top: 1px solid #f0f0f0; padding-top: 12px; margin-top: 4px">
        <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 10px">
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
              <span>{{ Number(item.amount || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2 }) }}</span>
            </template>
          </a-table-column>
          <a-table-column title="操作" width="60">
            <template #default="{ record: _item, index }">
              <a-button type="link" size="small" danger @click="handleRemoveItem(index)">删除</a-button>
            </template>
          </a-table-column>
        </a-table>

        <div style="text-align: right; margin-top: 8px; font-size: 14px">
          合计：<span style="font-weight: 600; color: #1677ff">¥{{ Number(itemsTotalAmount).toLocaleString('zh-CN', { minimumFractionDigits: 2 }) }}</span>
        </div>
      </div>
    </a-modal>
  </div>
</template>

<style scoped>
.pm-page {
  background: #f6f8fc;
  min-height: 100%;
  padding: 4px 0;
}
.pm-header {
  background: transparent;
  padding-bottom: 12px;
}
.pm-card {
  background: #fff;
  border: 1px solid #e5eaf3;
  border-radius: 10px;
  box-shadow: 0 10px 30px rgba(17, 24, 39, 0.05);
}
.pm-filter {
  padding: 20px 22px;
  margin-bottom: 14px;
}
.pm-filter-row {
  display: flex;
  flex-wrap: wrap;
  gap: 12px 24px;
  align-items: center;
}
.pm-field {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  white-space: nowrap;
}
.pm-field label {
  color: #374151;
}
.pm-filter-actions {
  display: flex;
  gap: 10px;
  margin-left: auto;
}
.pm-table-wrap {
  overflow: hidden;
  margin-bottom: 0;
}
.pm-link {
  color: #1677ff;
  font-weight: 500;
  cursor: pointer;
  text-decoration: none;
}
.pm-none {
  color: #9ca3af;
}
.pm-pagination {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 12px;
  padding: 12px 0 0;
}
.pm-total {
  font-size: 13px;
  color: #4b5563;
}
</style>
