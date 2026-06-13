<script setup lang="ts">
import { ref, reactive, onMounted, computed } from 'vue'
import { message, Modal } from 'ant-design-vue'
import {
  getVarOrderList,
  createVarOrder,
  updateVarOrder,
  deleteVarOrder,
  getVarOrderDetail,
  saveVarOrderItems,
} from '@/api/modules/variation'
import { getProjectList } from '@/api/modules/project'
import { getContractLedger } from '@/api/modules/contract'
import { getPartnerList } from '@/api/modules/partner'
import type { VarOrderVO, VarOrderItemVO } from '@/types/variation'
import type { ProjectVO } from '@/types/project'
import type { ContractVO } from '@/types/contract'
import type { PartnerVO } from '@/types/partner'

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

const projectList = ref<ProjectVO[]>([])
const contractList = ref<ContractVO[]>([])
const partnerList = ref<PartnerVO[]>([])

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

// Line items
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

const columns = [
  { title: '变更编号', dataIndex: 'varCode', width: 160 },
  { title: '变更名称', dataIndex: 'varName', width: 160 },
  { title: '变更类型', dataIndex: 'varType', width: 100, key: 'varType' },
  { title: '方向', dataIndex: 'direction', width: 80, key: 'direction' },
  { title: '项目名称', dataIndex: 'projectName', width: 140 },
  { title: '合同名称', dataIndex: 'contractName', width: 140 },
  { title: '合作方', dataIndex: 'partnerName', width: 140 },
  { title: '上报金额', dataIndex: 'reportedAmount', width: 120, key: 'reportedAmount' },
  { title: '审定金额', dataIndex: 'approvedAmount', width: 120, key: 'approvedAmount' },
  { title: '确认金额', dataIndex: 'confirmedAmount', width: 120, key: 'confirmedAmount' },
  { title: '审批状态', dataIndex: 'approvalStatus', width: 100, key: 'approvalStatus' },
  { title: '操作', key: 'action', width: 140, fixed: 'right' },
]

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
  } catch {
    tableData.value = []
    total.value = 0
    message.error('加载变更签证列表失败，请稍后重试')
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
    const res = await getContractLedger({ pageNo: 1, pageSize: 500 })
    contractList.value = res.records
  } catch {
    contractList.value = []
  }
}

async function fetchPartners() {
  try {
    const res = await getPartnerList({ pageNum: 1, pageSize: 500 })
    partnerList.value = res.records
  } catch {
    partnerList.value = []
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
    direction: record.direction || 'COST',
    impactDays: record.impactDays ?? 0,
    ownerConfirmFlag: record.ownerConfirmFlag ?? 0,
    remark: record.remark,
  })
  itemList.value = []
  itemKeyCounter = 0
  // Load existing items
  try {
    const detail = await getVarOrderDetail(record.id)
    if (detail.items) {
      itemList.value = detail.items.map((item) => ({
        ...item,
        key: itemKeyCounter++,
      }))
    }
  } catch {
    message.error('加载明细失败')
    itemList.value = []
  }
  modalVisible.value = true
}

function handleDelete(record: VarOrderVO) {
  Modal.confirm({
    title: '确认删除',
    content: `确定要删除变更签证"${record.varCode}"吗？`,
    okText: '确定',
    cancelText: '取消',
    onOk: async () => {
      try {
        await deleteVarOrder(record.id)
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
    itemName: '',
    unit: '',
    quantity: '0',
    unitPrice: '0',
    amount: '0',
    costSubjectId: undefined,
  })
}

function handleRemoveItem(index: number) {
  itemList.value.splice(index, 1)
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
      await updateVarOrder(editingId.value, formData)
      orderId = editingId.value
      message.success('更新成功')
    } else {
      const result = await createVarOrder(formData)
      orderId = result
      message.success('创建成功')
    }

    // Save line items
    if (itemList.value.length > 0) {
      const items = itemList.value.map((item) => ({
        ...item,
        varOrderId: orderId,
      }))
      await saveVarOrderItems(orderId, items)
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
    <a-page-header title="变更签证" class="pm-header" />

    <!-- Filter -->
    <div class="pm-card pm-filter">
      <div class="pm-filter-row">
        <div class="pm-field">
          <label>项目：</label>
          <a-select
            v-model:value="filter.projectId"
            placeholder="全部"
            allow-clear
            style="width: 180px"
          >
            <a-select-option v-for="p in projectList" :key="p.id" :value="p.id">
              {{ p.projectName }}
            </a-select-option>
          </a-select>
        </div>
        <div class="pm-field">
          <label>合同：</label>
          <a-select
            v-model:value="filter.contractId"
            placeholder="全部"
            allow-clear
            style="width: 180px"
          >
            <a-select-option v-for="c in contractList" :key="c.id" :value="c.id">
              {{ c.contractName }}
            </a-select-option>
          </a-select>
        </div>
        <div class="pm-field">
          <label>合作方：</label>
          <a-select
            v-model:value="filter.partnerId"
            placeholder="全部"
            allow-clear
            style="width: 160px"
          >
            <a-select-option v-for="p in partnerList" :key="p.id" :value="p.id">
              {{ p.partnerName }}
            </a-select-option>
          </a-select>
        </div>
        <div class="pm-field">
          <label>变更类型：</label>
          <a-select
            v-model:value="filter.varType"
            placeholder="全部"
            allow-clear
            style="width: 120px"
          >
            <a-select-option v-for="opt in VAR_TYPE_OPTIONS" :key="opt.value" :value="opt.value">
              {{ opt.label }}
            </a-select-option>
          </a-select>
        </div>
        <div class="pm-field">
          <label>方向：</label>
          <a-select
            v-model:value="filter.direction"
            placeholder="全部"
            allow-clear
            style="width: 90px"
          >
            <a-select-option
              v-for="opt in DIRECTION_OPTIONS"
              :key="opt.value"
              :value="opt.value"
              :disabled="opt.disabled"
            >
              {{ opt.label }}
            </a-select-option>
          </a-select>
        </div>
        <div class="pm-field">
          <label>变更编号：</label>
          <a-input
            v-model:value="filter.varCode"
            placeholder="请输入编号"
            style="width: 150px"
            allow-clear
          />
        </div>
        <div class="pm-filter-actions">
          <a-button type="primary" @click="handleSearch">查询</a-button>
          <a-button @click="handleReset">重置</a-button>
          <a-button type="primary" @click="handleAdd">新建签证</a-button>
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
          <template v-if="column.key === 'varType'">
            <a-tag>{{ VAR_TYPE_LABEL[record.varType] ?? record.varType ?? '-' }}</a-tag>
          </template>
          <template v-else-if="column.key === 'direction'">
            <a-tag :color="record.direction === 'COST' ? 'blue' : 'green'">
              {{
                record.direction === 'COST'
                  ? '成本'
                  : record.direction === 'REVENUE'
                    ? '收入'
                    : record.direction || '-'
              }}
            </a-tag>
          </template>
          <template v-else-if="column.key === 'reportedAmount'">
            <span v-if="record.reportedAmount"
              >¥{{
                Number(record.reportedAmount).toLocaleString('zh-CN', { minimumFractionDigits: 2 })
              }}</span
            >
            <span v-else class="pm-none">-</span>
          </template>
          <template v-else-if="column.key === 'approvedAmount'">
            <span v-if="record.approvedAmount"
              >¥{{
                Number(record.approvedAmount).toLocaleString('zh-CN', { minimumFractionDigits: 2 })
              }}</span
            >
            <span v-else class="pm-none">-</span>
          </template>
          <template v-else-if="column.key === 'confirmedAmount'">
            <span v-if="record.confirmedAmount"
              >¥{{
                Number(record.confirmedAmount).toLocaleString('zh-CN', { minimumFractionDigits: 2 })
              }}</span
            >
            <span v-else class="pm-none">-</span>
          </template>
          <template v-else-if="column.key === 'approvalStatus'">
            <ApprovalStatusTag :status="record.approvalStatus" />
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
      <a-form :label-col="{ span: 6 }" :wrapper-col="{ span: 16 }" style="margin-bottom: 8px">
        <a-form-item label="项目" required>
          <a-select v-model:value="formData.projectId" placeholder="请选择项目">
            <a-select-option v-for="p in projectList" :key="p.id" :value="p.id">
              {{ p.projectName }}
            </a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="合同">
          <a-select v-model:value="formData.contractId" placeholder="请选择合同" allow-clear>
            <a-select-option v-for="c in contractList" :key="c.id" :value="c.id">
              {{ c.contractName }}
            </a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="合作方">
          <a-select v-model:value="formData.partnerId" placeholder="请选择合作方" allow-clear>
            <a-select-option v-for="p in partnerList" :key="p.id" :value="p.id">
              {{ p.partnerName }}
            </a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="变更类型" required>
          <a-select v-model:value="formData.varType" placeholder="请选择变更类型">
            <a-select-option v-for="opt in VAR_TYPE_OPTIONS" :key="opt.value" :value="opt.value">
              {{ opt.label }}
            </a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="变更名称">
          <a-input v-model:value="formData.varName" placeholder="请输入变更名称" />
        </a-form-item>
        <a-form-item label="变更方向">
          <a-select v-model:value="formData.direction" placeholder="请选择方向">
            <a-select-option
              v-for="opt in DIRECTION_OPTIONS"
              :key="opt.value"
              :value="opt.value"
              :disabled="opt.disabled"
            >
              {{ opt.label }}
            </a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="影响工期(天)">
          <a-input-number v-model:value="formData.impactDays" :min="0" style="width: 100%" />
        </a-form-item>
        <a-form-item label="业主确认">
          <a-switch
            :checked="formData.ownerConfirmFlag === 1"
            @change="(val: boolean) => (formData.ownerConfirmFlag = val ? 1 : 0)"
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
          <span style="font-weight: 600; font-size: 14px">变更明细</span>
          <a-button type="dashed" size="small" @click="handleAddItem">+ 添加明细</a-button>
        </div>

        <a-table
          :data-source="itemList"
          :pagination="false"
          row-key="key"
          size="small"
          :scroll="{ y: 250 }"
        >
          <a-table-column title="清单项名称" width="160">
            <template #default="{ record: item }">
              <a-input v-model:value="item.itemName" placeholder="名称" style="width: 100%" />
            </template>
          </a-table-column>
          <a-table-column title="单位" width="70">
            <template #default="{ record: item }">
              <a-input v-model:value="item.unit" placeholder="单位" style="width: 100%" />
            </template>
          </a-table-column>
          <a-table-column title="数量" width="120">
            <template #default="{ record: item, index }">
              <a-input-number
                v-model:value="item.quantity"
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
