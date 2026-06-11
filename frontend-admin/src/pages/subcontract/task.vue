<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { message, Modal } from 'ant-design-vue'
import { getSubTaskList, createSubTask, updateSubTask, deleteSubTask } from '@/api/modules/subcontract'
import { getProjectList } from '@/api/modules/project'
import { getContractLedger } from '@/api/modules/contract'
import { getPartnerList } from '@/api/modules/partner'
import type { SubTaskVO } from '@/types/subcontract'
import type { ProjectVO } from '@/types/project'
import type { ContractVO } from '@/types/contract'
import type { PartnerVO } from '@/types/partner'

const filter = reactive({
  projectId: undefined as string | undefined,
  contractId: undefined as string | undefined,
  partnerId: undefined as string | undefined,
  status: undefined as string | undefined,
  taskCode: '',
  taskName: '',
})

const loading = ref(false)
const tableData = ref<SubTaskVO[]>([])
const total = ref(0)
const pageNo = ref(1)
const pageSize = ref(20)

const projectList = ref<ProjectVO[]>([])
const contractList = ref<ContractVO[]>([])
const partnerList = ref<PartnerVO[]>([])

const modalVisible = ref(false)
const modalTitle = ref('新建分包任务')
const editingId = ref<string | null>(null)
const formData = reactive<Partial<SubTaskVO>>({
  projectId: undefined,
  contractId: undefined,
  partnerId: undefined,
  taskName: '',
  workArea: '',
  plannedStartDate: undefined,
  plannedEndDate: undefined,
  actualStartDate: undefined,
  actualEndDate: undefined,
  progressPercent: '0',
  status: 'NOT_STARTED',
  remark: '',
})

const STATUS_LABEL: Record<string, string> = {
  NOT_STARTED: '未开始',
  IN_PROGRESS: '进行中',
  COMPLETED: '已完成',
  SUSPENDED: '已暂停',
}
const STATUS_COLOR: Record<string, string> = {
  NOT_STARTED: 'default',
  IN_PROGRESS: 'processing',
  COMPLETED: 'success',
  SUSPENDED: 'warning',
}

const columns = [
  { title: '任务编号', dataIndex: 'taskCode', width: 150 },
  { title: '任务名称', dataIndex: 'taskName', minWidth: 160, key: 'taskName' },
  { title: '项目名称', dataIndex: 'projectName', width: 140 },
  { title: '合同名称', dataIndex: 'contractName', width: 140 },
  { title: '分包商', dataIndex: 'partnerName', width: 140 },
  { title: '施工区域', dataIndex: 'workArea', width: 120 },
  { title: '进度', dataIndex: 'progressPercent', width: 100, key: 'progressPercent' },
  { title: '状态', dataIndex: 'status', width: 100, key: 'status' },
  { title: '计划开始', dataIndex: 'plannedStartDate', width: 110 },
  { title: '计划结束', dataIndex: 'plannedEndDate', width: 110 },
  { title: '操作', key: 'action', width: 140, fixed: 'right' },
]

async function fetchData() {
  loading.value = true
  try {
    const res = await getSubTaskList({
      pageNum: pageNo.value,
      pageSize: pageSize.value,
      projectId: filter.projectId,
      contractId: filter.contractId,
      partnerId: filter.partnerId,
      status: filter.status,
      taskCode: filter.taskCode || undefined,
      taskName: filter.taskName || undefined,
    })
    tableData.value = res.records
    total.value = res.total
  } catch {
    tableData.value = []
    total.value = 0
    message.error('加载分包任务列表失败，请稍后重试')
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

function handleSearch() {
  pageNo.value = 1
  fetchData()
}

function handleReset() {
  filter.projectId = undefined
  filter.contractId = undefined
  filter.partnerId = undefined
  filter.status = undefined
  filter.taskCode = ''
  filter.taskName = ''
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
  modalTitle.value = '新建分包任务'
  editingId.value = null
  Object.assign(formData, {
    projectId: undefined,
    contractId: undefined,
    partnerId: undefined,
    taskName: '',
    workArea: '',
    plannedStartDate: undefined,
    plannedEndDate: undefined,
    actualStartDate: undefined,
    actualEndDate: undefined,
    progressPercent: '0',
    status: 'NOT_STARTED',
    remark: '',
  })
  modalVisible.value = true
}

function handleEdit(record: SubTaskVO) {
  modalTitle.value = '编辑分包任务'
  editingId.value = record.id
  Object.assign(formData, {
    projectId: record.projectId,
    contractId: record.contractId,
    partnerId: record.partnerId,
    taskName: record.taskName,
    workArea: record.workArea,
    plannedStartDate: record.plannedStartDate,
    plannedEndDate: record.plannedEndDate,
    actualStartDate: record.actualStartDate,
    actualEndDate: record.actualEndDate,
    progressPercent: record.progressPercent,
    status: record.status,
    remark: record.remark,
  })
  modalVisible.value = true
}

function handleDelete(record: SubTaskVO) {
  Modal.confirm({
    title: '确认删除',
    content: `确定要删除分包任务"${record.taskName}"吗？`,
    okText: '确定',
    cancelText: '取消',
    onOk: async () => {
      try {
        await deleteSubTask(record.id)
        message.success('删除成功')
        fetchData()
      } catch {
        message.error('删除失败，请稍后重试')
      }
    },
  })
}

async function handleModalOk() {
  if (!formData.projectId) {
    message.warning('请选择项目')
    return
  }
  if (!formData.taskName) {
    message.warning('请输入任务名称')
    return
  }

  try {
    if (editingId.value) {
      await updateSubTask(editingId.value, formData)
      message.success('更新成功')
    } else {
      await createSubTask(formData)
      message.success('创建成功')
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
    <a-page-header title="分包任务管理" class="pm-header" />

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
            <a-select-option value="NOT_STARTED">未开始</a-select-option>
            <a-select-option value="IN_PROGRESS">进行中</a-select-option>
            <a-select-option value="COMPLETED">已完成</a-select-option>
            <a-select-option value="SUSPENDED">已暂停</a-select-option>
          </a-select>
        </div>
        <div class="pm-field">
          <label>任务编号：</label>
          <a-input v-model:value="filter.taskCode" placeholder="请输入编号" style="width:140px" allow-clear />
        </div>
        <div class="pm-field">
          <label>任务名称：</label>
          <a-input v-model:value="filter.taskName" placeholder="请输入名称" style="width:140px" allow-clear />
        </div>
        <div class="pm-filter-actions">
          <a-button type="primary" @click="handleSearch">查询</a-button>
          <a-button @click="handleReset">重置</a-button>
          <a-button type="primary" @click="handleAdd">新建任务</a-button>
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
        :scroll="{ x: 1400 }"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'taskName'">
            <a class="pm-link">{{ record.taskName }}</a>
          </template>
          <template v-else-if="column.key === 'progressPercent'">
            <a-progress
              v-if="record.progressPercent"
              :percent="parseFloat(record.progressPercent)"
              :stroke-width="8"
              size="small"
              :show-info="true"
            />
            <span v-else class="pm-none">-</span>
          </template>
          <template v-else-if="column.key === 'status'">
            <a-tag :color="STATUS_COLOR[record.status]">
              {{ STATUS_LABEL[record.status] ?? record.status }}
            </a-tag>
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
      :width="720"
      @ok="handleModalOk"
      @cancel="handleModalCancel"
    >
      <a-form :label-col="{ span: 6 }" :wrapper-col="{ span: 16 }">
        <a-form-item label="项目" required>
          <a-select v-model:value="formData.projectId" placeholder="请选择项目">
            <a-select-option v-for="p in projectList" :key="p.id" :value="p.id">
              {{ p.projectName }}
            </a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="分包合同">
          <a-select v-model:value="formData.contractId" placeholder="请选择合同" allow-clear>
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
        <a-form-item label="任务名称" required>
          <a-input v-model:value="formData.taskName" placeholder="请输入任务名称" />
        </a-form-item>
        <a-form-item label="施工区域">
          <a-input v-model:value="formData.workArea" placeholder="请输入施工区域" />
        </a-form-item>
        <a-form-item label="计划开始日期">
          <a-date-picker v-model:value="formData.plannedStartDate" style="width: 100%" />
        </a-form-item>
        <a-form-item label="计划结束日期">
          <a-date-picker v-model:value="formData.plannedEndDate" style="width: 100%" />
        </a-form-item>
        <a-form-item label="实际开始日期">
          <a-date-picker v-model:value="formData.actualStartDate" style="width: 100%" />
        </a-form-item>
        <a-form-item label="实际结束日期">
          <a-date-picker v-model:value="formData.actualEndDate" style="width: 100%" />
        </a-form-item>
        <a-form-item label="进度百分比">
          <a-input-number
            v-model:value="formData.progressPercent"
            :min="0"
            :max="100"
            :precision="2"
            style="width: 100%"
          />
        </a-form-item>
        <a-form-item label="状态">
          <a-select v-model:value="formData.status" placeholder="请选择状态">
            <a-select-option value="NOT_STARTED">未开始</a-select-option>
            <a-select-option value="IN_PROGRESS">进行中</a-select-option>
            <a-select-option value="COMPLETED">已完成</a-select-option>
            <a-select-option value="SUSPENDED">已暂停</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="备注">
          <a-textarea v-model:value="formData.remark" :rows="3" placeholder="请输入备注" />
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
