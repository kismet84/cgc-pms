<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { message, Modal } from 'ant-design-vue'
import { MoreOutlined } from '@ant-design/icons-vue'
import {
  getContractChangeList,
  createContractChange,
  updateContractChange,
  deleteContractChange,
  submitContractChangeApproval,
} from '@/api/modules/contract-change'
import {
  CHANGE_TYPE_LABEL,
  CHANGE_TYPE_COLOR,
  CHANGE_APPROVAL_LABEL,
  type ContractChangeVO,
  type ChangeType,
} from '@/types/contract-change'

const props = defineProps<{
  contractId: string
}>()

const emit = defineEmits<{
  (e: 'refresh'): void
}>()

// ---- Table state ----
const loading = ref(false)
const tableData = ref<ContractChangeVO[]>([])
const total = ref(0)
const pageNo = ref(1)
const pageSize = ref(10)

// ---- Modal state ----
const modalVisible = ref(false)
const modalTitle = ref('新建合同变更')
const editingId = ref<string | null>(null)
const formSubmitting = ref(false)

const formData = reactive<{
  changeName: string
  changeType: ChangeType | undefined
  beforeAmount: string
  changeAmount: string
  reason: string
  remark: string
}>({
  changeName: '',
  changeType: undefined,
  beforeAmount: '0',
  changeAmount: '0',
  reason: '',
  remark: '',
})

// ---- Computed ----
function computedAfterAmount(): string {
  const before = parseFloat(formData.beforeAmount) || 0
  const change = parseFloat(formData.changeAmount) || 0
  return (before + change).toFixed(2)
}

// ---- Detail modal for approval timeline ----
const detailVisible = ref(false)
const detailRecord = ref<ContractChangeVO | null>(null)

// ---- Table columns ----
const columns = [
  { title: '变更编号', dataIndex: 'changeCode', key: 'changeCode', width: 160 },
  { title: '变更名称', dataIndex: 'changeName', key: 'changeName', width: 160 },
  { title: '变更类型', dataIndex: 'changeType', key: 'changeType', width: 100 },
  {
    title: '变更前金额',
    dataIndex: 'beforeAmount',
    key: 'beforeAmount',
    width: 130,
    align: 'right' as const,
  },
  {
    title: '变更金额',
    dataIndex: 'changeAmount',
    key: 'changeAmount',
    width: 130,
    align: 'right' as const,
  },
  {
    title: '变更后金额',
    dataIndex: 'afterAmount',
    key: 'afterAmount',
    width: 130,
    align: 'right' as const,
  },
  { title: '审批状态', dataIndex: 'approvalStatus', key: 'approvalStatus', width: 100 },
  {
    title: '是否生效',
    dataIndex: 'effectiveFlag',
    key: 'effectiveFlag',
    width: 80,
    align: 'center' as const,
  },
  { title: '操作', key: 'action', width: 76 },
]

// ---- Data fetching ----
async function fetchData() {
  loading.value = true
  try {
    const res = await getContractChangeList({
      pageNo: pageNo.value,
      pageSize: pageSize.value,
      contractId: props.contractId,
    })
    tableData.value = res.records
    total.value = res.total
  } catch (err) {
    if (import.meta.env.DEV) {
      console.error('ContractChangeList: 加载合同变更列表失败', err)
    }
    tableData.value = []
    total.value = 0
    message.error('加载合同变更列表失败')
  } finally {
    loading.value = false
  }
}

// ---- CRUD handlers ----
function handleCreate() {
  modalTitle.value = '新建合同变更'
  editingId.value = null
  formData.changeName = ''
  formData.changeType = undefined
  formData.beforeAmount = '0'
  formData.changeAmount = '0'
  formData.reason = ''
  formData.remark = ''
  modalVisible.value = true
}

function handleEdit(record: ContractChangeVO) {
  if (record.costGeneratedFlag === 1) {
    message.warning('该变更已生成成本，不可编辑')
    return
  }
  if (record.approvalStatus !== 'DRAFT') {
    message.warning('该变更已提交审批，不可编辑')
    return
  }
  modalTitle.value = '编辑合同变更'
  editingId.value = record.id
  formData.changeName = record.changeName
  formData.changeType = record.changeType
  formData.beforeAmount = record.beforeAmount || '0'
  formData.changeAmount = record.changeAmount || '0'
  formData.reason = record.reason || ''
  formData.remark = record.remark || ''
  modalVisible.value = true
}

function handleDelete(record: ContractChangeVO) {
  if (record.costGeneratedFlag === 1) {
    message.warning('该变更已生成成本，不可删除')
    return
  }
  if (record.approvalStatus !== 'DRAFT') {
    message.warning('该变更已提交审批，不可删除')
    return
  }
  Modal.confirm({
    title: '确认删除',
    content: `确定要删除变更"${record.changeCode}"吗？`,
    okText: '确定',
    cancelText: '取消',
    onOk: async () => {
      try {
        await deleteContractChange(record.id)
        message.success('删除成功')
        fetchData()
      } catch (err) {
        if (import.meta.env.DEV) {
          console.error('ContractChangeList: 删除失败', err)
        }
        message.error('删除失败')
      }
    },
  })
}

function handleSubmitApproval(record: ContractChangeVO) {
  if (record.approvalStatus !== 'DRAFT') {
    message.warning('该变更已提交审批')
    return
  }
  Modal.confirm({
    title: '确认提交审批',
    content: `确定要提交变更"${record.changeCode}"进行审批吗？`,
    okText: '确认提交',
    cancelText: '取消',
    onOk: async () => {
      try {
        await submitContractChangeApproval(record.id)
        message.success('已提交审批')
        fetchData()
      } catch (err) {
        if (import.meta.env.DEV) {
          console.error('ContractChangeList: 提交审批失败', err)
        }
        message.error('提交审批失败')
      }
    },
  })
}

function handleViewDetail(record: ContractChangeVO) {
  detailRecord.value = record
  detailVisible.value = true
}

// ---- Form submit ----
async function handleModalOk() {
  if (!formData.changeName?.trim()) {
    message.warning('请输入变更名称')
    return
  }
  if (!formData.changeType) {
    message.warning('请选择变更类型')
    return
  }

  formSubmitting.value = true
  try {
    const payload: Partial<ContractChangeVO> = {
      contractId: props.contractId,
      changeName: formData.changeName.trim(),
      changeType: formData.changeType,
      beforeAmount: formData.beforeAmount,
      changeAmount: formData.changeAmount,
      afterAmount: computedAfterAmount(),
      reason: formData.reason || '',
      remark: formData.remark || '',
    }

    if (editingId.value) {
      await updateContractChange(editingId.value, payload)
      message.success('更新成功')
    } else {
      await createContractChange(payload)
      message.success('创建成功')
    }
    modalVisible.value = false
    fetchData()
    emit('refresh')
  } catch (err) {
    if (import.meta.env.DEV) {
      console.error('ContractChangeList: 操作失败', err)
    }
    message.error('操作失败，请稍后重试')
  } finally {
    formSubmitting.value = false
  }
}

function handleModalCancel() {
  modalVisible.value = false
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

function formatAmount(val: string | number): string {
  const n = typeof val === 'string' ? parseFloat(val) : val
  if (isNaN(n)) return '0.00'
  return n.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

const CHANGE_TYPE_OPTIONS = [
  { value: 'AMOUNT', label: '金额变更' },
  { value: 'DURATION', label: '工期变更' },
  { value: 'CLAUSE', label: '条款变更' },
]

// ---- Approval timeline steps ----

onMounted(() => {
  fetchData()
})
</script>

<template>
  <div class="cc-list">
    <!-- Header -->
    <div class="cc-list-header">
      <a-button type="primary" @click="handleCreate">新建变更</a-button>
    </div>

    <!-- Table -->
    <a-spin :spinning="loading">
      <a-table
        :columns="columns"
        :data-source="tableData"
        :pagination="false"
        row-key="id"
        size="small"
        bordered
        :scroll="{ x: 1200 }"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'changeType'">
            <a-tag :color="CHANGE_TYPE_COLOR[record.changeType]">
              {{ CHANGE_TYPE_LABEL[record.changeType] || record.changeType }}
            </a-tag>
          </template>
          <template v-else-if="column.key === 'beforeAmount'">
            <span :style="{ color: 'var(--text-secondary)' }"
              >¥{{ formatAmount(record.beforeAmount) }}</span
            >
          </template>
          <template v-else-if="column.key === 'changeAmount'">
            <span
              :style="{
                color: parseFloat(record.changeAmount) >= 0 ? '#1677ff' : '#ef4444',
                fontWeight: 600,
              }"
            >
              {{ parseFloat(record.changeAmount) >= 0 ? '+' : ''
              }}{{ formatAmount(record.changeAmount) }}
            </span>
          </template>
          <template v-else-if="column.key === 'afterAmount'">
            <span style="font-weight: 600; color: #15803d"
              >¥{{ formatAmount(record.afterAmount) }}</span
            >
          </template>
          <template v-else-if="column.key === 'approvalStatus'">
            <a-tag
              :color="
                record.approvalStatus === 'APPROVED'
                  ? 'success'
                  : record.approvalStatus === 'REJECTED'
                    ? 'error'
                    : record.approvalStatus === 'APPROVING'
                      ? 'processing'
                      : record.approvalStatus === 'WITHDRAWN'
                        ? 'warning'
                        : 'default'
              "
            >
              {{ CHANGE_APPROVAL_LABEL[record.approvalStatus] || record.approvalStatus }}
            </a-tag>
          </template>
          <template v-else-if="column.key === 'effectiveFlag'">
            <a-tag :color="record.effectiveFlag === 1 ? 'green' : 'default'">
              {{ record.effectiveFlag === 1 ? '是' : '否' }}
            </a-tag>
          </template>
          <template v-else-if="column.key === 'action'">
            <a-dropdown :trigger="['click']">
              <a-button class="lg-row-action-trigger" size="small" type="text">
                <MoreOutlined />
              </a-button>
              <template #overlay>
                <a-menu>
                  <a-menu-item @click="handleViewDetail(record)">详情</a-menu-item>
                  <a-menu-item
                    v-if="record.approvalStatus === 'DRAFT' && record.costGeneratedFlag !== 1"
                    @click="handleEdit(record)"
                  >
                    编辑
                  </a-menu-item>
                  <a-menu-item
                    v-if="record.approvalStatus === 'DRAFT' && record.costGeneratedFlag !== 1"
                    danger
                    @click="handleDelete(record)"
                  >
                    删除
                  </a-menu-item>
                  <a-menu-item
                    v-if="record.approvalStatus === 'DRAFT'"
                    @click="handleSubmitApproval(record)"
                  >
                    提交审批
                  </a-menu-item>
                </a-menu>
              </template>
            </a-dropdown>
          </template>
        </template>
      </a-table>
    </a-spin>

    <!-- Pagination -->
    <div class="cc-pagination">
      <span class="cc-total">共 {{ total }} 条</span>
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

    <!-- Create/Edit Modal -->
    <a-modal
      v-model:open="modalVisible"
      :title="modalTitle"
      :width="600"
      :confirm-loading="formSubmitting"
      @ok="handleModalOk"
      @cancel="handleModalCancel"
    >
      <a-form :label-col="{ span: 6 }" :wrapper-col="{ span: 16 }">
        <a-form-item label="变更名称" required>
          <a-input v-model:value="formData.changeName" placeholder="请输入变更名称" />
        </a-form-item>
        <a-form-item label="变更类型" required>
          <a-select v-model:value="formData.changeType" placeholder="请选择变更类型">
            <a-select-option v-for="opt in CHANGE_TYPE_OPTIONS" :key="opt.value" :value="opt.value">
              {{ opt.label }}
            </a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="变更前金额">
          <a-input-number
            v-model:value="formData.beforeAmount"
            :precision="2"
            style="width: 100%"
            placeholder="请输入变更前金额"
          />
        </a-form-item>
        <a-form-item label="变更金额">
          <a-input-number
            v-model:value="formData.changeAmount"
            :precision="2"
            style="width: 100%"
            placeholder="请输入变更金额（正数增加，负数减少）"
          />
        </a-form-item>
        <a-form-item label="变更后金额">
          <div class="cc-amount-impact">
            <span class="cc-amount-before">¥{{ formatAmount(formData.beforeAmount) }}</span>
            <span class="cc-amount-arrow">→</span>
            <span
              class="cc-amount-change"
              :style="{ color: parseFloat(formData.changeAmount) >= 0 ? '#ef4444' : '#1677ff' }"
            >
              {{ parseFloat(formData.changeAmount) >= 0 ? '+' : ''
              }}{{ formatAmount(formData.changeAmount) }}
            </span>
            <span class="cc-amount-arrow">→</span>
            <span class="cc-amount-after">¥{{ formatAmount(computedAfterAmount()) }}</span>
          </div>
        </a-form-item>
        <a-form-item label="变更原因">
          <a-textarea v-model:value="formData.reason" :rows="3" placeholder="请输入变更原因" />
        </a-form-item>
        <a-form-item label="备注">
          <a-textarea v-model:value="formData.remark" :rows="2" placeholder="请输入备注" />
        </a-form-item>
      </a-form>
    </a-modal>

    <!-- Detail Modal with Approval Timeline -->
    <a-modal v-model:open="detailVisible" title="变更详情" :width="700" :footer="null">
      <template v-if="detailRecord">
        <a-descriptions :column="2" size="small" bordered>
          <a-descriptions-item label="变更编号">{{ detailRecord.changeCode }}</a-descriptions-item>
          <a-descriptions-item label="变更名称">{{ detailRecord.changeName }}</a-descriptions-item>
          <a-descriptions-item label="变更类型">
            <a-tag :color="CHANGE_TYPE_COLOR[detailRecord.changeType]">
              {{ CHANGE_TYPE_LABEL[detailRecord.changeType] || detailRecord.changeType }}
            </a-tag>
          </a-descriptions-item>
          <a-descriptions-item label="审批状态">
            <a-tag
              :color="
                detailRecord.approvalStatus === 'APPROVED'
                  ? 'success'
                  : detailRecord.approvalStatus === 'REJECTED'
                    ? 'error'
                    : detailRecord.approvalStatus === 'APPROVING'
                      ? 'processing'
                      : detailRecord.approvalStatus === 'WITHDRAWN'
                        ? 'warning'
                        : 'default'
              "
            >
              {{
                CHANGE_APPROVAL_LABEL[detailRecord.approvalStatus] || detailRecord.approvalStatus
              }}
            </a-tag>
          </a-descriptions-item>
          <a-descriptions-item label="变更前金额">
            ¥{{ formatAmount(detailRecord.beforeAmount) }}
          </a-descriptions-item>
          <a-descriptions-item label="变更金额">
            <span
              :style="{
                color: parseFloat(detailRecord.changeAmount) >= 0 ? '#ef4444' : '#1677ff',
                fontWeight: 600,
              }"
            >
              {{ parseFloat(detailRecord.changeAmount) >= 0 ? '+' : ''
              }}{{ formatAmount(detailRecord.changeAmount) }}
            </span>
          </a-descriptions-item>
          <a-descriptions-item label="变更后金额">
            <span style="font-weight: 600; color: #15803d"
              >¥{{ formatAmount(detailRecord.afterAmount) }}</span
            >
          </a-descriptions-item>
          <a-descriptions-item label="是否生效">
            <a-tag :color="detailRecord.effectiveFlag === 1 ? 'green' : 'default'">
              {{ detailRecord.effectiveFlag === 1 ? '已生效' : '未生效' }}
            </a-tag>
          </a-descriptions-item>
          <a-descriptions-item v-if="detailRecord.reason" label="变更原因" :span="2">
            {{ detailRecord.reason }}
          </a-descriptions-item>
          <a-descriptions-item v-if="detailRecord.remark" label="备注" :span="2">
            {{ detailRecord.remark }}
          </a-descriptions-item>
          <a-descriptions-item label="创建时间">{{
            detailRecord.createdTime || '-'
          }}</a-descriptions-item>
          <a-descriptions-item label="更新时间">{{
            detailRecord.updatedTime || '-'
          }}</a-descriptions-item>
        </a-descriptions>

        <!-- Amount Impact Visualization -->
        <div class="cc-detail-impact" style="margin-top: 16px">
          <div class="cc-impact-label">金额影响</div>
          <div class="cc-impact-flow">
            <div class="cc-impact-card">
              <div class="cc-impact-card-label">变更前</div>
              <div class="cc-impact-card-value">¥{{ formatAmount(detailRecord.beforeAmount) }}</div>
            </div>
            <div class="cc-impact-arrow">→</div>
            <div
              class="cc-impact-card"
              :style="{
                borderColor: parseFloat(detailRecord.changeAmount) >= 0 ? '#ef4444' : '#1677ff',
              }"
            >
              <div class="cc-impact-card-label">
                {{ parseFloat(detailRecord.changeAmount) >= 0 ? '增加' : '减少' }}
              </div>
              <div
                class="cc-impact-card-value"
                :style="{
                  color: parseFloat(detailRecord.changeAmount) >= 0 ? '#ef4444' : '#1677ff',
                }"
              >
                {{ parseFloat(detailRecord.changeAmount) >= 0 ? '+' : ''
                }}{{ formatAmount(detailRecord.changeAmount) }}
              </div>
            </div>
            <div class="cc-impact-arrow">→</div>
            <div class="cc-impact-card" style="border-color: #15803d">
              <div class="cc-impact-card-label">变更后</div>
              <div class="cc-impact-card-value" style="color: #15803d">
                ¥{{ formatAmount(detailRecord.afterAmount) }}
              </div>
            </div>
          </div>
        </div>

        <!-- Approval Timeline -->
        <div class="cc-timeline" style="margin-top: 20px">
          <div class="cc-timeline-title">审批进度</div>
          <a-steps
            :current="['DRAFT', 'APPROVING', 'APPROVED'].indexOf(detailRecord.approvalStatus)"
            size="small"
            direction="horizontal"
          >
            <a-step title="草稿" description="创建变更" />
            <a-step title="审批中" description="等待审批" />
            <a-step
              :title="detailRecord.approvalStatus === 'REJECTED' ? '已驳回' : '已通过'"
              :status="detailRecord.approvalStatus === 'REJECTED' ? 'error' : undefined"
              :description="detailRecord.approvalStatus === 'REJECTED' ? '审批未通过' : '审批完成'"
            />
          </a-steps>
          <div
            v-if="detailRecord.approvalStatus === 'WITHDRAWN'"
            style="margin-top: 8px; color: #f59e0b; font-size: 13px"
          >
            该变更已被撤回
          </div>
        </div>
      </template>
    </a-modal>
  </div>
</template>

<style scoped>
.cc-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.cc-list-header {
  display: flex;
  justify-content: flex-end;
  align-items: center;
}

.cc-pagination {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 12px;
  padding: 8px 0 0;
}

.cc-total {
  font-size: 13px;
  color: var(--text-secondary);
}

.cc-amount-impact {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  flex-wrap: wrap;
}

.cc-amount-before {
  color: var(--text-secondary);
  font-family: 'Consolas', 'Monaco', monospace;
}

.cc-amount-change {
  font-weight: 600;
  font-family: 'Consolas', 'Monaco', monospace;
}

.cc-amount-after {
  font-weight: 600;
  color: #15803d;
  font-family: 'Consolas', 'Monaco', monospace;
}

.cc-amount-arrow {
  color: var(--muted);
  font-size: 16px;
}

/* Detail Modal Styles */
.cc-detail-impact {
  background: #f8fafc;
  border: 1px solid #e5eaf3;
  border-radius: 8px;
  padding: 16px;
}

.cc-impact-label {
  font-size: 14px;
  font-weight: 600;
  color: var(--text);
  margin-bottom: 12px;
}

.cc-impact-flow {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.cc-impact-card {
  padding: 12px 16px;
  border: 2px solid #e5e7eb;
  border-radius: 8px;
  text-align: center;
  min-width: 140px;
  background: #fff;
}

.cc-impact-card-label {
  font-size: 12px;
  color: var(--text-secondary);
  margin-bottom: 4px;
}

.cc-impact-card-value {
  font-size: 18px;
  font-weight: 700;
  font-family: 'Consolas', 'Monaco', monospace;
}

.cc-impact-arrow {
  font-size: 20px;
  color: var(--muted);
  font-weight: 700;
}

.cc-timeline {
  background: #f8fafc;
  border: 1px solid #e5eaf3;
  border-radius: 8px;
  padding: 16px;
}

.cc-timeline-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--text);
  margin-bottom: 16px;
}
</style>
