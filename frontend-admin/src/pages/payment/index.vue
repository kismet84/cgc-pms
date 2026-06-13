<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { message, Modal } from 'ant-design-vue'
import {
  getApplicationList,
  createApplication,
  updateApplication,
  deleteApplication,
  getBasisList,
  saveBasis,
  submitForApproval,
  doWriteback,
} from '@/api/modules/payment'
import { getProjectList } from '@/api/modules/project'
import { getContractLedger } from '@/api/modules/contract'
import { getPartnerList } from '@/api/modules/partner'
import { getReceiptList } from '@/api/modules/receipt'
import { getMeasureList } from '@/api/modules/subcontract'
import type { PayApplicationVO, PayApplicationBasisVO } from '@/types/payment'
import { PAY_TYPE_LABEL, PAY_TYPE_COLOR, PAY_STATUS_LABEL, PAY_STATUS_COLOR } from '@/types/payment'
import type { ProjectVO } from '@/types/project'
import type { ContractVO } from '@/types/contract'
import type { PartnerVO } from '@/types/partner'
import type { MatReceiptVO } from '@/types/receipt'
import type { SubMeasureVO } from '@/types/subcontract'

const filter = reactive({
  projectId: undefined as string | undefined,
  contractId: undefined as string | undefined,
  partnerId: undefined as string | undefined,
  payType: undefined as string | undefined,
  payStatus: undefined as string | undefined,
  approvalStatus: undefined as string | undefined,
})

const loading = ref(false)
const tableData = ref<PayApplicationVO[]>([])
const total = ref(0)
const pageNo = ref(1)
const pageSize = ref(20)

const projectList = ref<ProjectVO[]>([])
const contractList = ref<ContractVO[]>([])
const partnerList = ref<PartnerVO[]>([])
const receiptList = ref<MatReceiptVO[]>([])
const measureList = ref<SubMeasureVO[]>([])

const modalVisible = ref(false)
const modalTitle = ref('新建付款申请')
const editingId = ref<string | null>(null)
const formData = reactive<Partial<PayApplicationVO>>({
  projectId: undefined,
  contractId: undefined,
  partnerId: undefined,
  payType: undefined,
  applyAmount: undefined,
  applyReason: '',
})

// Basis items editor
const basisList = ref<(Partial<PayApplicationBasisVO> & { key: number })[]>([])
let basisKeyCounter = 0

// Writeback modal
const writebackVisible = ref(false)
const writebackTargetId = ref('')
const writebackForm = reactive({
  payAmount: undefined as number | undefined,
  payDate: undefined as string | undefined,
  payMethod: 'BANK_TRANSFER',
  voucherNo: '',
})

const columns = [
  { title: '申请编号', dataIndex: 'applyCode', width: 160 },
  { title: '项目', dataIndex: 'projectName', width: 140 },
  { title: '合同', dataIndex: 'contractName', width: 140 },
  { title: '合作方', dataIndex: 'partnerName', width: 140 },
  { title: '申请金额', dataIndex: 'applyAmount', width: 130, key: 'applyAmount' },
  { title: '审批金额', dataIndex: 'approvedAmount', width: 130, key: 'approvedAmount' },
  { title: '实付金额', dataIndex: 'actualPayAmount', width: 130, key: 'actualPayAmount' },
  { title: '付款类型', dataIndex: 'payType', width: 100, key: 'payType' },
  { title: '支付状态', dataIndex: 'payStatus', width: 100, key: 'payStatus' },
  { title: '审批状态', dataIndex: 'approvalStatus', width: 100, key: 'approvalStatus' },
  { title: '操作', key: 'action', width: 220, fixed: 'right' },
]

async function fetchData() {
  loading.value = true
  try {
    const res = await getApplicationList({
      pageNum: pageNo.value,
      pageSize: pageSize.value,
      projectId: filter.projectId,
      contractId: filter.contractId,
      partnerId: filter.partnerId,
      payType: filter.payType,
      payStatus: filter.payStatus,
      approvalStatus: filter.approvalStatus,
    })
    tableData.value = res.records
    total.value = res.total
  } catch {
    tableData.value = []
    total.value = 0
    message.error('加载付款申请列表失败，请稍后重试')
  } finally {
    loading.value = false
  }
}

async function fetchProjects() {
  try {
    const res = await getProjectList({ pageNum: 1, pageSize: 500 })
    projectList.value = res.records
  } catch { projectList.value = [] }
}

async function fetchContracts() {
  try {
    const res = await getContractLedger({ pageNo: 1, pageSize: 500 })
    contractList.value = res.records
  } catch { contractList.value = [] }
}

async function fetchPartners() {
  try {
    const res = await getPartnerList({ pageNum: 1, pageSize: 500 })
    partnerList.value = res.records
  } catch { partnerList.value = [] }
}

async function fetchReceipts() {
  try {
    const res = await getReceiptList({ pageNum: 1, pageSize: 500 })
    receiptList.value = res.records
  } catch { receiptList.value = [] }
}

async function fetchMeasures() {
  try {
    const res = await getMeasureList({ pageNum: 1, pageSize: 500 })
    measureList.value = res.records
  } catch { measureList.value = [] }
}

function handleSearch() {
  pageNo.value = 1
  fetchData()
}

function handleReset() {
  filter.projectId = undefined
  filter.contractId = undefined
  filter.partnerId = undefined
  filter.payType = undefined
  filter.payStatus = undefined
  filter.approvalStatus = undefined
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
  modalTitle.value = '新建付款申请'
  editingId.value = null
  Object.assign(formData, {
    projectId: undefined,
    contractId: undefined,
    partnerId: undefined,
    payType: undefined,
    applyAmount: undefined,
    applyReason: '',
  })
  basisList.value = []
  basisKeyCounter = 0
  modalVisible.value = true
}

async function handleEdit(record: PayApplicationVO) {
  modalTitle.value = '编辑付款申请'
  editingId.value = record.id
  Object.assign(formData, {
    projectId: record.projectId,
    contractId: record.contractId,
    partnerId: record.partnerId,
    payType: record.payType,
    applyAmount: record.applyAmount,
    applyReason: record.applyReason,
  })
  basisList.value = []
  basisKeyCounter = 0
  try {
    const items = await getBasisList(record.id)
    basisList.value = items.map((item) => ({
      ...item,
      key: basisKeyCounter++,
    }))
  } catch {
    message.error('加载依据明细失败')
    basisList.value = []
  }
  modalVisible.value = true
}

function handleDelete(record: PayApplicationVO) {
  Modal.confirm({
    title: '确认删除',
    content: `确定要删除付款申请"${record.applyCode}"吗？`,
    okText: '确定',
    cancelText: '取消',
    onOk: async () => {
      try {
        await deleteApplication(record.id)
        message.success('删除成功')
        fetchData()
      } catch {
        message.error('删除失败，请稍后重试')
      }
    },
  })
}

// Basis items management
function handleAddBasis() {
  basisList.value.push({
    key: basisKeyCounter++,
    sourceType: 'RECEIPT',
    sourceId: undefined,
    sourceItemId: undefined,
    amount: '0',
  })
}

function handleRemoveBasis(index: number) {
  basisList.value.splice(index, 1)
}

function getSourceOptions(sourceType: string): { id: string; label: string }[] {
  if (sourceType === 'RECEIPT') {
    return receiptList.value.map((r) => ({
      id: r.id,
      label: `${r.receiptCode} - ${r.projectName || ''}`,
    }))
  }
  if (sourceType === 'MEASURE') {
    return measureList.value.map((m) => ({
      id: m.id,
      label: `${m.measureCode} - ${m.projectName || ''}`,
    }))
  }
  return []
}

function handleSourceChange(index: number) {
  const item = basisList.value[index]
  item.sourceId = undefined
  item.sourceItemId = undefined
}

async function handleModalOk() {
  if (!formData.projectId) {
    message.warning('请选择项目')
    return
  }
  if (!formData.payType) {
    message.warning('请选择付款类型')
    return
  }
  if (!formData.applyAmount || parseFloat(formData.applyAmount) <= 0) {
    message.warning('请输入有效的申请金额')
    return
  }

  try {
    let applicationId: string
    if (editingId.value) {
      await updateApplication(editingId.value, formData)
      applicationId = editingId.value
      message.success('更新成功')
    } else {
      const result = await createApplication(formData)
      applicationId = result
      message.success('创建成功')
    }

    // Save basis items
    if (basisList.value.length > 0) {
      const items = basisList.value.map((item) => ({
        applicationId,
        sourceType: item.sourceType,
        sourceId: item.sourceId || '',
        sourceItemId: item.sourceItemId,
        sourceName: item.sourceName,
        amount: item.amount || '0',
      }))
      await saveBasis(applicationId, items as PayApplicationBasisVO[])
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

function handleSubmitApproval(record: PayApplicationVO) {
  Modal.confirm({
    title: '提交审批',
    content: `确定要提交付款申请"${record.applyCode}"吗？`,
    okText: '确定',
    cancelText: '取消',
    onOk: async () => {
      try {
        await submitForApproval(record.id)
        message.success('提交审批成功')
        fetchData()
      } catch {
        message.error('提交审批失败')
      }
    },
  })
}

function handleOpenWriteback(record: PayApplicationVO) {
  writebackTargetId.value = record.id
  writebackForm.payAmount = undefined
  writebackForm.payDate = undefined
  writebackForm.payMethod = 'BANK_TRANSFER'
  writebackForm.voucherNo = ''
  writebackVisible.value = true
}

async function handleWritebackOk() {
  if (!writebackForm.payAmount || writebackForm.payAmount <= 0) {
    message.warning('请输入有效的支付金额')
    return
  }
  if (!writebackForm.payDate) {
    message.warning('请选择支付日期')
    return
  }
  try {
    await doWriteback({
      payApplicationId: writebackTargetId.value,
      payAmount: writebackForm.payAmount,
      payDate: writebackForm.payDate,
      payMethod: writebackForm.payMethod,
      voucherNo: writebackForm.voucherNo || undefined,
    })
    message.success('回写成功')
    writebackVisible.value = false
    fetchData()
  } catch {
    message.error('回写失败，请稍后重试')
  }
}

function handleWritebackCancel() {
  writebackVisible.value = false
}

function fmtAmount(val: string | undefined): string {
  if (!val) return '-'
  const n = parseFloat(val)
  if (isNaN(n)) return '-'
  return n.toLocaleString('zh-CN', { minimumFractionDigits: 2 })
}

onMounted(() => {
  fetchProjects()
  fetchContracts()
  fetchPartners()
  fetchReceipts()
  fetchMeasures()
  fetchData()
})
</script>

<template>
  <div class="pm-page">
    <a-page-header title="付款申请管理" class="pm-header" />

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
          <label>合同：</label>
          <a-select v-model:value="filter.contractId" placeholder="全部" allow-clear style="width:180px">
            <a-select-option v-for="c in contractList" :key="c.id" :value="c.id">
              {{ c.contractName }}
            </a-select-option>
          </a-select>
        </div>
        <div class="pm-field">
          <label>合作方：</label>
          <a-select v-model:value="filter.partnerId" placeholder="全部" allow-clear style="width:160px">
            <a-select-option v-for="p in partnerList" :key="p.id" :value="p.id">
              {{ p.partnerName }}
            </a-select-option>
          </a-select>
        </div>
        <div class="pm-field">
          <label>付款类型：</label>
          <a-select v-model:value="filter.payType" placeholder="全部" allow-clear style="width:110px">
            <a-select-option value="ADVANCE">预付款</a-select-option>
            <a-select-option value="PROGRESS">进度款</a-select-option>
            <a-select-option value="FINAL">结算款</a-select-option>
            <a-select-option value="OTHER">其他</a-select-option>
          </a-select>
        </div>
        <div class="pm-field">
          <label>支付状态：</label>
          <a-select v-model:value="filter.payStatus" placeholder="全部" allow-clear style="width:110px">
            <a-select-option value="UNPAID">未支付</a-select-option>
            <a-select-option value="PARTIAL">部分支付</a-select-option>
            <a-select-option value="PAID">已支付</a-select-option>
          </a-select>
        </div>
        <div class="pm-field">
          <label>审批状态：</label>
          <a-select v-model:value="filter.approvalStatus" placeholder="全部" allow-clear style="width:110px">
            <a-select-option value="DRAFT">草稿</a-select-option>
            <a-select-option value="APPROVING">审批中</a-select-option>
            <a-select-option value="APPROVED">已通过</a-select-option>
            <a-select-option value="REJECTED">已驳回</a-select-option>
            <a-select-option value="WITHDRAWN">已撤回</a-select-option>
          </a-select>
        </div>
        <div class="pm-filter-actions">
          <a-button type="primary" @click="handleSearch">查询</a-button>
          <a-button @click="handleReset">重置</a-button>
          <a-button type="primary" @click="handleAdd">新建申请</a-button>
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
          <template v-if="column.key === 'applyAmount'">
            <span>{{ fmtAmount(record.applyAmount) }}</span>
          </template>
          <template v-else-if="column.key === 'approvedAmount'">
            <span>{{ fmtAmount(record.approvedAmount) }}</span>
          </template>
          <template v-else-if="column.key === 'actualPayAmount'">
            <span>{{ fmtAmount(record.actualPayAmount) }}</span>
          </template>
          <template v-else-if="column.key === 'payType'">
            <a-tag :color="PAY_TYPE_COLOR[record.payType as keyof typeof PAY_TYPE_COLOR] || 'default'">
              {{ PAY_TYPE_LABEL[record.payType as keyof typeof PAY_TYPE_LABEL] || record.payType }}
            </a-tag>
          </template>
          <template v-else-if="column.key === 'payStatus'">
            <a-tag :color="PAY_STATUS_COLOR[record.payStatus as keyof typeof PAY_STATUS_COLOR] || 'default'">
              {{ PAY_STATUS_LABEL[record.payStatus as keyof typeof PAY_STATUS_LABEL] || record.payStatus }}
            </a-tag>
          </template>
          <template v-else-if="column.key === 'approvalStatus'">
            <ApprovalStatusTag :status="record.approvalStatus" />
          </template>
          <template v-else-if="column.key === 'action'">
            <a-button type="link" size="small" @click="handleEdit(record)">编辑</a-button>
            <a-button type="link" size="small" danger @click="handleDelete(record)">删除</a-button>
            <a-button
              v-if="record.approvalStatus === 'DRAFT'"
              type="link"
              size="small"
              @click="handleSubmitApproval(record)"
            >提交审批</a-button>
            <a-button
              v-if="record.approvalStatus === 'APPROVED'"
              type="link"
              size="small"
              @click="handleOpenWriteback(record)"
            >回写</a-button>
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
      <a-form :label-col="{ span: 6 }" :wrapper-col="{ span: 16 }" style="margin-bottom:8px">
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
        <a-form-item label="付款类型" required>
          <a-select v-model:value="formData.payType" placeholder="请选择付款类型">
            <a-select-option value="ADVANCE">预付款</a-select-option>
            <a-select-option value="PROGRESS">进度款</a-select-option>
            <a-select-option value="FINAL">结算款</a-select-option>
            <a-select-option value="OTHER">其他</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="申请金额" required>
          <a-input-number
            v-model:value="formData.applyAmount"
            :min="0"
            :precision="2"
            style="width: 100%"
            placeholder="请输入申请金额"
          />
        </a-form-item>
        <a-form-item label="申请原因">
          <a-textarea v-model:value="formData.applyReason" :rows="2" placeholder="请输入申请原因" />
        </a-form-item>
      </a-form>

      <!-- Basis Items Section -->
      <div style="border-top: 1px solid #f0f0f0; padding-top: 12px; margin-top: 4px">
        <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 10px">
          <span style="font-weight: 600; font-size: 14px">付款依据</span>
          <a-button size="small" @click="handleAddBasis">添加依据行</a-button>
        </div>

        <a-table
          :data-source="basisList"
          :pagination="false"
          row-key="key"
          size="small"
          :scroll="{ y: 240 }"
        >
          <a-table-column title="来源类型" width="100">
            <template #default="{ record: item, index }">
              <a-select
                v-model:value="item.sourceType"
                size="small"
                style="width: 100%"
                @change="handleSourceChange(index)"
              >
                <a-select-option value="RECEIPT">材料验收</a-select-option>
                <a-select-option value="MEASURE">分包计量</a-select-option>
              </a-select>
            </template>
          </a-table-column>
          <a-table-column title="来源单据" width="240">
            <template #default="{ record: item }">
              <a-select
                v-model:value="item.sourceId"
                size="small"
                placeholder="选择单据"
                allow-clear
                style="width: 100%"
              >
                <a-select-option
                  v-for="opt in getSourceOptions(item.sourceType || 'RECEIPT')"
                  :key="opt.id"
                  :value="opt.id"
                >
                  {{ opt.label }}
                </a-select-option>
              </a-select>
            </template>
          </a-table-column>
          <a-table-column title="金额" width="160">
            <template #default="{ record: item }">
              <a-input-number
                v-model:value="item.amount"
                :min="0"
                :precision="2"
                size="small"
                style="width: 100%"
                placeholder="金额"
              />
            </template>
          </a-table-column>
          <a-table-column title="操作" width="60">
            <template #default="{ index }">
              <a-button type="link" size="small" danger @click="handleRemoveBasis(index)">删除</a-button>
            </template>
          </a-table-column>
        </a-table>
      </div>
    </a-modal>

    <!-- Writeback Modal -->
    <a-modal
      v-model:open="writebackVisible"
      title="付款回写"
      :width="480"
      @ok="handleWritebackOk"
      @cancel="handleWritebackCancel"
    >
      <a-form :label-col="{ span: 6 }" :wrapper-col="{ span: 16 }">
        <a-form-item label="支付金额" required>
          <a-input-number
            v-model:value="writebackForm.payAmount"
            :min="0.01"
            :precision="2"
            style="width: 100%"
            placeholder="请输入支付金额"
          />
        </a-form-item>
        <a-form-item label="支付日期" required>
          <a-date-picker v-model:value="writebackForm.payDate" style="width: 100%" />
        </a-form-item>
        <a-form-item label="支付方式" required>
          <a-select v-model:value="writebackForm.payMethod" placeholder="请选择支付方式">
            <a-select-option value="BANK_TRANSFER">银行转账</a-select-option>
            <a-select-option value="CASH">现金</a-select-option>
            <a-select-option value="CHECK">支票</a-select-option>
            <a-select-option value="OTHER">其他</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="凭证号">
          <a-input v-model:value="writebackForm.voucherNo" placeholder="请输入凭证号" />
        </a-form-item>
      </a-form>
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
