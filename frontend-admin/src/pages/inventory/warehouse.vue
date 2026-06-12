<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { message, Modal } from 'ant-design-vue'
import {
  getWarehouseList,
  createWarehouse,
  updateWarehouse,
  deleteWarehouse,
} from '@/api/modules/inventory'
import { getProjectList } from '@/api/modules/project'
import type { WarehouseVO } from '@/types/inventory'
import type { ProjectVO } from '@/types/project'

const filter = reactive({
  projectId: undefined as string | undefined,
  warehouseCode: '',
  warehouseName: '',
  status: undefined as string | undefined,
})

const loading = ref(false)
const tableData = ref<WarehouseVO[]>([])
const total = ref(0)
const pageNo = ref(1)
const pageSize = ref(20)

const projectList = ref<ProjectVO[]>([])

const modalVisible = ref(false)
const modalTitle = ref('新建仓库')
const editingId = ref<string | null>(null)
const formData = reactive<Partial<WarehouseVO>>({
  projectId: undefined,
  warehouseCode: '',
  warehouseName: '',
  status: 'ENABLE',
  remark: '',
})

const STATUS_LABEL: Record<string, string> = {
  ENABLE: '启用',
  DISABLE: '停用',
}
const STATUS_COLOR: Record<string, string> = {
  ENABLE: 'success',
  DISABLE: 'error',
}

const columns = [
  { title: '仓库编号', dataIndex: 'warehouseCode', width: 130 },
  { title: '仓库名称', dataIndex: 'warehouseName', width: 160 },
  { title: '所属项目', dataIndex: 'projectName', width: 160 },
  { title: '状态', dataIndex: 'status', width: 80, key: 'status' },
  { title: '创建时间', dataIndex: 'createdAt', width: 150 },
  { title: '操作', key: 'action', width: 160, fixed: 'right' },
]

async function fetchData() {
  loading.value = true
  try {
    const res = await getWarehouseList({
      pageNo: pageNo.value,
      pageSize: pageSize.value,
      projectId: filter.projectId,
      warehouseCode: filter.warehouseCode || undefined,
      warehouseName: filter.warehouseName || undefined,
      status: filter.status,
    })
    tableData.value = res.records
    total.value = res.total
  } catch {
    tableData.value = []
    total.value = 0
    message.error('加载仓库列表失败，请稍后重试')
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

function handleSearch() {
  pageNo.value = 1
  fetchData()
}

function handleReset() {
  filter.projectId = undefined
  filter.warehouseCode = ''
  filter.warehouseName = ''
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

function handleAdd() {
  modalTitle.value = '新建仓库'
  editingId.value = null
  Object.assign(formData, {
    projectId: undefined,
    warehouseCode: '',
    warehouseName: '',
    status: 'ENABLE',
    remark: '',
  })
  modalVisible.value = true
}

function handleEdit(record: WarehouseVO) {
  modalTitle.value = '编辑仓库'
  editingId.value = record.id
  Object.assign(formData, {
    projectId: record.projectId,
    warehouseCode: record.warehouseCode,
    warehouseName: record.warehouseName,
    status: record.status,
    remark: record.remark,
  })
  modalVisible.value = true
}

function handleDelete(record: WarehouseVO) {
  Modal.confirm({
    title: '确认删除',
    content: `确定要删除仓库"${record.warehouseName}"吗？`,
    okText: '确定',
    cancelText: '取消',
    onOk: async () => {
      try {
        await deleteWarehouse(record.id)
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
    message.warning('请选择所属项目')
    return
  }
  if (!formData.warehouseName) {
    message.warning('请输入仓库名称')
    return
  }

  try {
    if (editingId.value) {
      await updateWarehouse(editingId.value, formData)
      message.success('更新成功')
    } else {
      await createWarehouse(formData)
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
  fetchData()
})
</script>

<template>
  <div class="pm-page">
    <a-page-header title="仓库管理" class="pm-header" />

    <!-- Filter -->
    <div class="pm-card pm-filter">
      <div class="pm-filter-row">
        <div class="pm-field">
          <label>所属项目：</label>
          <a-select v-model:value="filter.projectId" placeholder="全部" allow-clear style="width:180px">
            <a-select-option v-for="p in projectList" :key="p.id" :value="p.id">
              {{ p.projectName }}
            </a-select-option>
          </a-select>
        </div>
        <div class="pm-field">
          <label>仓库编号：</label>
          <a-input v-model:value="filter.warehouseCode" placeholder="请输入编号" style="width:140px" allow-clear />
        </div>
        <div class="pm-field">
          <label>仓库名称：</label>
          <a-input v-model:value="filter.warehouseName" placeholder="请输入名称" style="width:140px" allow-clear />
        </div>
        <div class="pm-field">
          <label>状态：</label>
          <a-select v-model:value="filter.status" placeholder="全部" allow-clear style="width:100px">
            <a-select-option value="ENABLE">启用</a-select-option>
            <a-select-option value="DISABLE">停用</a-select-option>
          </a-select>
        </div>
        <div class="pm-filter-actions">
          <a-button type="primary" @click="handleSearch">查询</a-button>
          <a-button @click="handleReset">重置</a-button>
          <a-button type="primary" @click="handleAdd">新建仓库</a-button>
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
        :scroll="{ x: 1000 }"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'status'">
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
      :width="560"
      @ok="handleModalOk"
      @cancel="handleModalCancel"
    >
      <a-form :label-col="{ span: 6 }" :wrapper-col="{ span: 16 }">
        <a-form-item label="所属项目" required>
          <a-select v-model:value="formData.projectId" placeholder="请选择项目">
            <a-select-option v-for="p in projectList" :key="p.id" :value="p.id">
              {{ p.projectName }}
            </a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="仓库编号">
          <a-input v-model:value="formData.warehouseCode" placeholder="留空自动生成" :disabled="!!editingId" />
        </a-form-item>
        <a-form-item label="仓库名称" required>
          <a-input v-model:value="formData.warehouseName" placeholder="请输入仓库名称" />
        </a-form-item>
        <a-form-item label="状态">
          <a-radio-group v-model:value="formData.status">
            <a-radio value="ENABLE">启用</a-radio>
            <a-radio value="DISABLE">停用</a-radio>
          </a-radio-group>
        </a-form-item>
        <a-form-item label="备注">
          <a-textarea v-model:value="formData.remark" :rows="2" placeholder="请输入备注" />
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
