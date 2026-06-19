<script setup lang="ts">
import { ref, reactive, onMounted, computed, watch } from 'vue'
import { storeToRefs } from 'pinia'
import { message, Modal } from 'ant-design-vue'
import {
  getSubTaskList,
  createSubTask,
  updateSubTask,
  deleteSubTask,
} from '@/api/modules/subcontract'
import { useReferenceStore } from '@/stores/reference'
import type { SubTaskVO } from '@/types/subcontract'

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

const referenceStore = useReferenceStore()
const {
  projects: projectList,
  contracts: contractList,
  partners: partnerList,
} = storeToRefs(referenceStore)

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
  { title: '任务编号', dataIndex: 'taskCode', width: 130, ellipsis: true },
  { title: '任务名称', dataIndex: 'taskName', minWidth: 140, key: 'taskName', ellipsis: true },
  { title: '项目名称', dataIndex: 'projectName', width: 120, ellipsis: true },
  { title: '合同名称', dataIndex: 'contractName', width: 120, ellipsis: true },
  { title: '分包商', dataIndex: 'partnerName', width: 120, ellipsis: true },
  { title: '施工区域', dataIndex: 'workArea', width: 100, ellipsis: true },
  { title: '进度', dataIndex: 'progressPercent', width: 90, key: 'progressPercent' },
  { title: '状态', dataIndex: 'status', width: 80, key: 'status' },
  { title: '计划开始', dataIndex: 'plannedStartDate', width: 100 },
  { title: '计划结束', dataIndex: 'plannedEndDate', width: 100 },
  { title: '操作', key: 'action', width: 110 },
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
  } catch (e: unknown) {
    console.error(e)
    tableData.value = []
    total.value = 0
    message.error('加载分包任务列表失败，请稍后重试')
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
      } catch (e: unknown) {
        console.error(e)
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
  } catch (e: unknown) {
    console.error(e)
    message.error('操作失败，请稍后重试')
  }
}

function handleModalCancel() {
  modalVisible.value = false
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
      <a-breadcrumb style="margin-bottom:5px;font-size:13px">
        <a-breadcrumb-item>分包管理</a-breadcrumb-item>
        <a-breadcrumb-item>分包任务</a-breadcrumb-item>
      </a-breadcrumb>
    </div>

    <!-- 搜索栏 -->
    <div class="lg-search-bar">
      <a-select
        v-model:value="filter.projectId"
        placeholder="全部"
        allow-clear
        style="width: 180px"
        show-search
        @change="
          (v: string | undefined) => {
            filter.contractId = undefined
            if (v) referenceStore.fetchContracts({ projectId: v })
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
      <a-select
        v-model:value="filter.contractId"
        placeholder="全部"
        allow-clear
        style="width: 180px"
        show-search
        :filter-option="
          (input: string, option: any) =>
            option.label?.toLowerCase().includes(input.toLowerCase())
        "
      >
        <a-select-option v-for="c in contractList" :key="c.id" :value="c.id">
          {{ c.contractName }}
        </a-select-option>
      </a-select>
      <a-select
        v-model:value="filter.partnerId"
        placeholder="全部"
        allow-clear
        style="width: 160px"
        show-search
        :filter-option="
          (input: string, option: any) =>
            option.label?.toLowerCase().includes(input.toLowerCase())
        "
      >
        <a-select-option v-for="p in partnerList" :key="p.id" :value="p.id">
          {{ p.partnerName }}
        </a-select-option>
      </a-select>
      <a-select
        v-model:value="filter.status"
        placeholder="全部"
        allow-clear
        style="width: 110px"
      >
        <a-select-option value="NOT_STARTED">未开始</a-select-option>
        <a-select-option value="IN_PROGRESS">进行中</a-select-option>
        <a-select-option value="COMPLETED">已完成</a-select-option>
        <a-select-option value="SUSPENDED">已暂停</a-select-option>
      </a-select>
      <a-input
        v-model:value="filter.taskCode"
        placeholder="请输入编号"
        style="width: 140px"
        allow-clear
      />
      <a-input
        v-model:value="filter.taskName"
        placeholder="请输入名称"
        style="width: 140px"
        allow-clear
      />
      <a-button type="primary" @click="handleSearch">查询</a-button>
      <a-button @click="handleReset">重置</a-button>
    </div>

    <!-- 工具栏 -->
    <div class="lg-toolbar">
      <div class="lg-toolbar-left">
        <a-button type="primary" @click="handleAdd">新建任务</a-button>
      </div>
      <div class="lg-toolbar-right" />
    </div>

    <!-- 表格 -->
    <div class="lg-table-wrap">
      <a-table
        :columns="columns"
        :data-source="tableData"
        :loading="loading"
        :pagination="false"
        row-key="id"
        size="small"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'taskName'">
            <a class="lg-link">{{ record.taskName }}</a>
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
            <a-button type="link" size="small" danger @click="handleDelete(record)"
              >删除</a-button
            >
          </template>
        </template>
      </a-table>
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
      :width="720"
      @ok="handleModalOk"
      @cancel="handleModalCancel"
    >
      <a-form :label-col="{ span: 6 }" :wrapper-col="{ span: 16 }">
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
            @change="onContractChange"
          >
            <a-select-option v-for="c in contractList" :key="c.id" :value="c.id">
              {{ c.contractName }}
            </a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="分包商">
          <a-input :value="formPartnerName" disabled placeholder="选择合同后自动填充乙方" />
        </a-form-item>
        <a-form-item label="任务名称" required>
          <a-input v-model:value="formData.taskName" placeholder="请输入任务名称" />
        </a-form-item>
        <a-form-item label="施工区域">
          <a-input v-model:value="formData.workArea" placeholder="请输入施工区域" />
        </a-form-item>
        <a-form-item label="计划开始日期">
          <a-date-picker
            v-model:value="formData.plannedStartDate"
            value-format="YYYY-MM-DD"
            style="width: 100%"
          />
        </a-form-item>
        <a-form-item label="计划结束日期">
          <a-date-picker
            v-model:value="formData.plannedEndDate"
            value-format="YYYY-MM-DD"
            style="width: 100%"
          />
        </a-form-item>
        <a-form-item label="实际开始日期">
          <a-date-picker
            v-model:value="formData.actualStartDate"
            value-format="YYYY-MM-DD"
            style="width: 100%"
          />
        </a-form-item>
        <a-form-item label="实际结束日期">
          <a-date-picker
            v-model:value="formData.actualEndDate"
            value-format="YYYY-MM-DD"
            style="width: 100%"
          />
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

<style scoped></style>
