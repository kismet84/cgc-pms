<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { PlusOutlined } from '@ant-design/icons-vue'
import { getProjectList, createProject, deleteProject } from '@/api/modules/project'
import type { ProjectVO } from '@/types/project'
import type { PageResult } from '@/types/api'

const filter = reactive({
  projectCode: '',
  projectName: '',
  projectType: undefined as string | undefined,
  status: undefined as string | undefined,
})

const loading = ref(false)
const tableData = ref<ProjectVO[]>([])
const total = ref(0)
const pageNo = ref(1)
const pageSize = ref(20)

const createVisible = ref(false)
const createLoading = ref(false)
const router = useRouter()
const createFormRef = ref()
const createForm = reactive({
  projectName: '',
  projectType: undefined as string | undefined,
  projectAddress: '',
  ownerUnit: '',
  supervisorUnit: '',
  designUnit: '',
  contractAmount: undefined as number | undefined,
  plannedStartDate: undefined as string | undefined,
  plannedEndDate: undefined as string | undefined,
})

function handleCreateModalOpen() {
  Object.assign(createForm, {
    projectName: '',
    projectType: undefined,
    projectAddress: '',
    ownerUnit: '',
    supervisorUnit: '',
    designUnit: '',
    contractAmount: undefined,
    plannedStartDate: undefined,
    plannedEndDate: undefined,
  })
  createVisible.value = true
}

function handleCreateModalClose() {
  createVisible.value = false
  createFormRef.value?.resetFields()
}

async function handleDelete(id: string) {
  try {
    await deleteProject(id)
    message.success('删除成功')
    fetchData()
  } catch (e: unknown) {
    console.error(e)
    message.error('删除失败，请稍后重试')
  }
}

async function handleCreateSubmit() {
  try {
    await createFormRef.value?.validate()
  } catch (e: unknown) {
    console.error(e)
    return
  }
  createLoading.value = true
  try {
    await createProject({
      projectName: createForm.projectName,
      projectType: createForm.projectType,
      projectAddress: createForm.projectAddress || undefined,
      ownerUnit: createForm.ownerUnit || undefined,
      supervisorUnit: createForm.supervisorUnit || undefined,
      designUnit: createForm.designUnit || undefined,
      contractAmount:
        createForm.contractAmount != null ? String(createForm.contractAmount) : undefined,
      plannedStartDate: createForm.plannedStartDate,
      plannedEndDate: createForm.plannedEndDate,
    })
    message.success('项目创建成功')
    handleCreateModalClose()
    fetchData()
  } catch (e: unknown) {
    console.error(e)
    message.error('创建项目失败，请稍后重试')
  } finally {
    createLoading.value = false
  }
}

async function fetchData() {
  loading.value = true
  try {
    const res: PageResult<ProjectVO> = await getProjectList({
      pageNum: pageNo.value,
      pageSize: pageSize.value,
      projectCode: filter.projectCode || undefined,
      projectName: filter.projectName || undefined,
      projectType: filter.projectType,
      status: filter.status,
    })
    tableData.value = res.records
    total.value = res.total
  } catch (e: unknown) {
    console.error(e)
    tableData.value = []
    total.value = 0
    message.error('加载项目列表失败，请稍后重试')
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  pageNo.value = 1
  fetchData()
}
function handleReset() {
  filter.projectCode = ''
  filter.projectName = ''
  filter.projectType = undefined
  filter.status = undefined
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

onMounted(fetchData)

function fmtAmount(val: string): string {
  const n = parseFloat(val)
  if (isNaN(n)) return '-'
  return (
    (n / 10000).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 }) +
    ' 万元'
  )
}

const TYPE_COLOR: Record<string, string> = {
  施工总承包: 'blue',
  专业分包: 'green',
  劳务分包: 'cyan',
  材料采购: 'orange',
}

const STATUS_COLOR: Record<string, string> = {
  DRAFT: 'processing',
  ONGOING: 'success',
  COMPLETED: 'default',
  SUSPENDED: 'warning',
  CLOSED: 'error',
}

const STATUS_LABEL: Record<string, string> = {
  DRAFT: '前期',
  ONGOING: '在建',
  COMPLETED: '已竣工',
  SUSPENDED: '已暂停',
  CLOSED: '已关闭',
}

const APPROVAL_COLOR: Record<string, string> = {
  已批准: 'success',
  审批中: 'processing',
  待审批: 'default',
  已拒绝: 'error',
}

const columns = [
  { title: '项目编号', dataIndex: 'projectCode', width: 150 },
  {
    title: '项目名称',
    dataIndex: 'projectName',
    minWidth: 180,
  },
  {
    title: '项目类型',
    dataIndex: 'projectType',
    width: 120,
  },
  {
    title: '合同金额',
    dataIndex: 'contractAmount',
    width: 140,
    align: 'right',
  },
  {
    title: '计划工期',
    dataIndex: 'plannedStartDate',
    width: 200,
  },
  { title: '状态', dataIndex: 'status', width: 90 },
  {
    title: '审批状态',
    dataIndex: 'approvalStatus',
    width: 100,
  },
  { title: '操作', dataIndex: 'ops', width: 120, fixed: 'right' },
]
</script>

<template>
  <div class="pj-page">
    <a-page-header title="项目管理" class="pj-header" />

    <!-- Filter -->
    <div class="pj-card pj-filter">
      <div class="pj-filter-row">
        <div class="pj-field">
          <label for="filter-project-code">项目编号：</label>
          <a-input
            id="filter-project-code"
            v-model:value="filter.projectCode"
            placeholder="请输入项目编号"
            style="width: 160px"
            allow-clear
          />
        </div>
        <div class="pj-field">
          <label for="filter-project-name">项目名称：</label>
          <a-input
            id="filter-project-name"
            v-model:value="filter.projectName"
            placeholder="请输入项目名称"
            style="width: 180px"
            allow-clear
          />
        </div>
        <div class="pj-field">
          <label for="filter-project-type">项目类型：</label>
          <a-select
            id="filter-project-type"
            v-model:value="filter.projectType"
            placeholder="全部"
            allow-clear
            style="width: 140px"
          >
            <a-select-option value="施工总承包">施工总承包</a-select-option>
            <a-select-option value="专业分包">专业分包</a-select-option>
            <a-select-option value="劳务分包">劳务分包</a-select-option>
            <a-select-option value="材料采购">材料采购</a-select-option>
          </a-select>
        </div>
        <div class="pj-field">
          <label for="filter-status">状态：</label>
          <a-select
            id="filter-status"
            v-model:value="filter.status"
            placeholder="全部"
            allow-clear
            style="width: 120px"
          >
            <a-select-option value="DRAFT">前期</a-select-option>
            <a-select-option value="ONGOING">在建</a-select-option>
            <a-select-option value="COMPLETED">已竣工</a-select-option>
            <a-select-option value="SUSPENDED">已暂停</a-select-option>
            <a-select-option value="CLOSED">已关闭</a-select-option>
          </a-select>
        </div>
        <div class="pj-filter-actions">
          <a-button type="primary" @click="handleCreateModalOpen">
            <PlusOutlined />
            新建项目
          </a-button>
          <a-button type="primary" @click="handleSearch">查询</a-button>
          <a-button @click="handleReset">重置</a-button>
        </div>
      </div>
    </div>

    <!-- Create Modal -->
    <a-modal
      v-model:open="createVisible"
      title="新建项目"
      :confirm-loading="createLoading"
      :mask-closable="false"
      ok-text="创建"
      cancel-text="取消"
      @ok="handleCreateSubmit"
      @cancel="handleCreateModalClose"
    >
      <a-form
        ref="createFormRef"
        :model="createForm"
        :label-col="{ span: 6 }"
        :wrapper-col="{ span: 16 }"
        class="pj-create-form"
      >
        <a-form-item
          label="项目名称"
          name="projectName"
          :rules="[{ required: true, message: '请输入项目名称' }]"
        >
          <a-input v-model:value="createForm.projectName" placeholder="请输入项目名称" />
        </a-form-item>
        <a-form-item
          label="项目类型"
          name="projectType"
          :rules="[{ required: true, message: '请选择项目类型' }]"
        >
          <a-select v-model:value="createForm.projectType" placeholder="请选择项目类型">
            <a-select-option value="施工总承包">施工总承包</a-select-option>
            <a-select-option value="专业分包">专业分包</a-select-option>
            <a-select-option value="劳务分包">劳务分包</a-select-option>
            <a-select-option value="材料采购">材料采购</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="项目地址" name="projectAddress">
          <a-input v-model:value="createForm.projectAddress" placeholder="请输入项目地址" />
        </a-form-item>
        <a-form-item label="建设单位" name="ownerUnit">
          <a-input v-model:value="createForm.ownerUnit" placeholder="请输入建设单位" />
        </a-form-item>
        <a-form-item label="监理单位" name="supervisorUnit">
          <a-input v-model:value="createForm.supervisorUnit" placeholder="请输入监理单位" />
        </a-form-item>
        <a-form-item label="设计单位" name="designUnit">
          <a-input v-model:value="createForm.designUnit" placeholder="请输入设计单位" />
        </a-form-item>
        <a-form-item label="合同金额(万元)" name="contractAmount">
          <a-input-number
            v-model:value="createForm.contractAmount"
            :min="0"
            :precision="2"
            placeholder="请输入合同金额"
            style="width: 100%"
          />
        </a-form-item>
        <a-form-item label="计划开工日期" name="plannedStartDate">
          <a-date-picker
            v-model:value="createForm.plannedStartDate"
            placeholder="请选择计划开工日期"
            style="width: 100%"
            value-format="YYYY-MM-DD"
          />
        </a-form-item>
        <a-form-item label="计划竣工日期" name="plannedEndDate">
          <a-date-picker
            v-model:value="createForm.plannedEndDate"
            placeholder="请选择计划竣工日期"
            style="width: 100%"
            value-format="YYYY-MM-DD"
          />
        </a-form-item>
      </a-form>
    </a-modal>

    <!-- Table -->
    <div class="pj-card pj-table-wrap">
      <a-table
        :data-source="tableData"
        :columns="columns"
        :loading="loading"
        :pagination="false"
        row-key="id"
        size="middle"
        :scroll="{ x: 1100 }"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.dataIndex === 'projectName'">
            <a class="pj-link" @click="router.push(`/project/${record.id}/overview`)">{{ record.projectName }}</a>
          </template>
          <template v-else-if="column.dataIndex === 'projectType'">
            <a-tag :color="TYPE_COLOR[record.projectType] ?? 'default'">{{
              record.projectType
            }}</a-tag>
          </template>
          <template v-else-if="column.dataIndex === 'contractAmount'">
            <span class="pj-money">{{ fmtAmount(record.contractAmount) }}</span>
          </template>
          <template v-else-if="column.dataIndex === 'plannedStartDate'">
            <span>{{ record.plannedStartDate }} ~ {{ record.plannedEndDate }}</span>
          </template>
          <template v-else-if="column.dataIndex === 'status'">
            <a-tag :color="STATUS_COLOR[record.status] ?? 'default'">{{
              STATUS_LABEL[record.status] ?? record.status
            }}</a-tag>
          </template>
          <template v-else-if="column.dataIndex === 'approvalStatus'">
            <a-tag :color="APPROVAL_COLOR[record.approvalStatus] ?? 'default'">{{
              record.approvalStatus
            }}</a-tag>
          </template>
          <template v-else-if="column.dataIndex === 'ops'">
            <div class="pj-ops">
              <a class="pj-link" @click="router.push(`/project/${record.id}/overview`)">查看</a>
              <a class="pj-link" @click="router.push(`/project/${record.id}/edit`)">编辑</a>
              <a-popconfirm
                title="确认删除该项目？"
                ok-text="确认"
                cancel-text="取消"
                @confirm="handleDelete(record.id)"
              >
                <a class="pj-link" style="color: #ff4d4f">删除</a>
              </a-popconfirm>
            </div>
          </template>
        </template>
      </a-table>
    </div>

    <!-- Pagination -->
    <div class="pj-pagination">
      <span class="pj-total">共 {{ total }} 条</span>
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
  </div>
</template>

<style scoped>
.pj-page {
  background: #f6f8fc;
  min-height: 100%;
  padding: 4px 0;
}
.pj-header {
  background: transparent;
  padding-bottom: 8px;
}
.pj-card {
  background: #fff;
  border: 1px solid #e5eaf3;
  border-radius: 10px;
  box-shadow: 0 10px 30px rgba(17, 24, 39, 0.05);
}
.pj-filter {
  padding: 20px 22px;
  margin-bottom: 14px;
}
.pj-filter-row {
  display: flex;
  flex-wrap: wrap;
  gap: 12px 24px;
  align-items: center;
}
.pj-field {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  white-space: nowrap;
}
.pj-field label {
  color: #374151;
  min-width: 56px;
}
.pj-filter-actions {
  display: flex;
  gap: 10px;
  margin-left: auto;
  align-items: center;
}
.pj-table-wrap {
  overflow: hidden;
  margin-bottom: 0;
}
.pj-link {
  color: #1677ff;
  font-weight: 500;
  text-decoration: none;
  cursor: pointer;
}
.pj-money {
  font-variant-numeric: tabular-nums;
}
.pj-ops {
  display: flex;
  gap: 10px;
}
.pj-pagination {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 12px;
  padding: 12px 0 0;
}
.pj-total {
  font-size: 13px;
  color: #4b5563;
}
.pj-create-form {
  padding-top: 16px;
}
</style>
