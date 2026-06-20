<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { message, Modal } from 'ant-design-vue'
import {
  SearchOutlined,
  ReloadOutlined,
  PlusOutlined,
} from '@ant-design/icons-vue'
import {
  getWarehouseList,
  createWarehouse,
  updateWarehouse,
  deleteWarehouse,
} from '@/api/modules/inventory'
import { useReferenceStore } from '@/stores/reference'
import type { WarehouseVO } from '@/types/inventory'

const filter = reactive({
  projectId: undefined as string | undefined,
  keyword: '',
  warehouseCode: '',
  warehouseName: '',
  status: undefined as string | undefined,
})

const loading = ref(false)
const tableData = ref<WarehouseVO[]>([])
const total = ref(0)
const pageNo = ref(1)
const pageSize = ref(20)

const referenceStore = useReferenceStore()
const projectList = computed(() => referenceStore.projects ?? [])

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

const gridColumns = computed(() => [
  { field: 'warehouseCode', title: '仓库编号', width: 120, ellipsis: true },
  { field: 'warehouseName', title: '仓库名称', width: 140, ellipsis: true },
  { field: 'projectName', title: '所属项目', width: 140, ellipsis: true },
  { field: 'status', title: '状态', width: 80, slots: { default: 'status' } },
  { field: 'createdAt', title: '创建时间', width: 140 },
  { title: '操作', width: 130, slots: { default: 'ops' } },
])

async function fetchData() {
  loading.value = true
  try {
    const res = await getWarehouseList({
      pageNo: pageNo.value,
      pageSize: pageSize.value,
      projectId: filter.projectId,
      warehouseCode: filter.keyword || filter.warehouseCode || undefined,
      warehouseName: filter.keyword || filter.warehouseName || undefined,
      status: filter.status,
    })
    tableData.value = res.records
    total.value = res.total
  } catch (e: unknown) {
    console.error(e)
    tableData.value = []
    total.value = 0
    message.error('加载仓库列表失败，请稍后重试')
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
  filter.keyword = ''
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
      } catch (e: unknown) {
        console.error(e)
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
  } catch (e: unknown) {
    console.error(e)
    message.error('操作失败，请稍后重试')
  }
}

function handleModalCancel() {
  modalVisible.value = false
}

const kpiWhTotal = computed(() => total.value)
const kpiWhEnabled = computed(() => tableData.value.filter((r) => r.status === 'ENABLE').length)

function filterOption(input: string, option: any) {
  return option.label?.toLowerCase().includes(input.toLowerCase())
}

onMounted(() => {
  referenceStore.fetchProjects()
  fetchData()
})
</script>

<template>
  <div class="lg-page app-page">
    <!-- Page head -->
    <div class="lg-page-head">
      <div>
        <a-breadcrumb style="margin-bottom:5px;font-size:13px">
          <a-breadcrumb-item>库存管理</a-breadcrumb-item>
          <a-breadcrumb-item>仓库</a-breadcrumb-item>
        </a-breadcrumb>
      </div>
    </div>

    <!-- KPI strip -->
    <div class="lg-kpi-strip" style="grid-template-columns: repeat(2, minmax(136px, 1fr))">
      <div class="lg-kpi-card">
        <span class="lg-kpi-card-label">仓库总数</span>
        <span class="lg-kpi-card-value">{{ kpiWhTotal }} <small>个</small></span>
        <span class="lg-kpi-card-bar"><span style="width:100%;background:var(--kpi-total)"></span></span>
      </div>
      <div class="lg-kpi-card">
        <span class="lg-kpi-card-label">启用仓库</span>
        <span class="lg-kpi-card-value" style="color: #22c55e">{{ kpiWhEnabled }} <small>个</small></span>
        <span class="lg-kpi-card-bar"><span style="width:100%;background:var(--kpi-paid)"></span></span>
      </div>
    </div>

    <!-- 搜索栏 -->
    <div class="lg-search-bar">
      <a-input
        v-model:value="filter.keyword"
        placeholder="搜索仓库编号、名称…"
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
          新建仓库
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
          @change="handleSearch"
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
        <template #status="{ row }">
          <a-tag :color="STATUS_COLOR[row.status]">
            {{ STATUS_LABEL[row.status] ?? row.status }}
          </a-tag>
        </template>
        <template #ops="{ row }">
          <div class="lg-ops">
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
          <a-input
            v-model:value="formData.warehouseCode"
            placeholder="留空自动生成"
            :disabled="!!editingId"
          />
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

<style scoped></style>
