<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { message, Modal } from 'ant-design-vue'
import {
  getMaterialList,
  createMaterial,
  updateMaterial,
  updateMaterialStatus,
} from '@/api/modules/material'
import type { MaterialVO } from '@/types/material'

const filter = reactive({
  materialCode: '',
  materialName: '',
  categoryId: undefined as string | undefined,
  status: undefined as string | undefined,
})

const loading = ref(false)
const tableData = ref<MaterialVO[]>([])
const total = ref(0)
const pageNo = ref(1)
const pageSize = ref(20)

const modalVisible = ref(false)
const modalTitle = ref('新增材料')
const formLoading = ref(false)
const isEdit = ref(false)
const formData = reactive<Partial<MaterialVO>>({
  materialCode: '',
  materialName: '',
  categoryId: undefined,
  specification: '',
  unit: '',
  brand: '',
  defaultTaxRate: '',
  status: 'ENABLE',
  remark: '',
})

const STATUS_COLOR: Record<string, string> = {
  ENABLE: 'success',
  DISABLE: 'default',
}

const STATUS_LABEL: Record<string, string> = {
  ENABLE: '启用',
  DISABLE: '禁用',
}

const columns = [
  { title: '材料编码', dataIndex: 'materialCode', width: 130, ellipsis: true },
  { title: '材料名称', dataIndex: 'materialName', minWidth: 150, ellipsis: true },
  { title: '规格型号', dataIndex: 'specification', width: 120, ellipsis: true },
  { title: '单位', dataIndex: 'unit', width: 70 },
  { title: '品牌', dataIndex: 'brand', width: 100, ellipsis: true },
  {
    title: '默认税率(%)',
    dataIndex: 'defaultTaxRate',
    width: 100,
    align: 'right' as const,
    key: 'defaultTaxRate',
  },
  { title: '状态', dataIndex: 'status', width: 80, key: 'status' },
  { title: '创建时间', dataIndex: 'createdAt', width: 150 },
  { title: '操作', dataIndex: 'ops', width: 130, key: 'ops' },
]

async function fetchData() {
  loading.value = true
  try {
    const res = await getMaterialList({
      pageNum: pageNo.value,
      pageSize: pageSize.value,
      materialCode: filter.materialCode || undefined,
      materialName: filter.materialName || undefined,
      categoryId: filter.categoryId,
      status: filter.status,
    })
    tableData.value = res.records
    total.value = res.total
  } catch (e: unknown) {
    console.error(e)
    tableData.value = []
    total.value = 0
    message.error('加载材料列表失败，请稍后重试')
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  pageNo.value = 1
  fetchData()
}

function handleReset() {
  filter.materialCode = ''
  filter.materialName = ''
  filter.categoryId = undefined
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
  isEdit.value = false
  modalTitle.value = '新增材料'
  Object.assign(formData, {
    materialCode: '',
    materialName: '',
    categoryId: undefined,
    specification: '',
    unit: '',
    brand: '',
    defaultTaxRate: '',
    status: 'ENABLE',
    remark: '',
  })
  modalVisible.value = true
}

function handleEdit(record: MaterialVO) {
  isEdit.value = true
  modalTitle.value = '编辑材料'
  Object.assign(formData, {
    id: record.id,
    materialCode: record.materialCode,
    materialName: record.materialName,
    categoryId: record.categoryId,
    specification: record.specification,
    unit: record.unit,
    brand: record.brand,
    defaultTaxRate: record.defaultTaxRate,
    status: record.status,
    remark: record.remark,
  })
  modalVisible.value = true
}

function handleToggleStatus(record: MaterialVO) {
  const newStatus = record.status === 'ENABLE' ? 'DISABLE' : 'ENABLE'
  const actionText = newStatus === 'ENABLE' ? '启用' : '禁用'

  Modal.confirm({
    title: `确认${actionText}`,
    content: `确定要${actionText}材料"${record.materialName}"吗？`,
    okText: '确定',
    cancelText: '取消',
    onOk: async () => {
      try {
        await updateMaterialStatus(record.id, newStatus)
        message.success(`${actionText}成功`)
        fetchData()
      } catch (e: unknown) {
        console.error(e)
        message.error(`${actionText}失败`)
      }
    },
  })
}

async function handleSubmit() {
  if (!formData.materialCode || !formData.materialName) {
    message.error('请填写必填项')
    return
  }

  formLoading.value = true
  try {
    if (isEdit.value && formData.id) {
      await updateMaterial(formData.id, formData)
      message.success('编辑成功')
    } else {
      await createMaterial(formData)
      message.success('新增成功')
    }
    modalVisible.value = false
    fetchData()
  } catch (e: unknown) {
    console.error(e)
    message.error(isEdit.value ? '编辑失败' : '新增失败')
  } finally {
    formLoading.value = false
  }
}

function handleCancel() {
  modalVisible.value = false
}

onMounted(fetchData)
</script>

<template>
  <div class="project-target-redesign app-page">
    <div class="pt-page-head">
      <a-breadcrumb class="pt-breadcrumb"
        ><a-breadcrumb-item>基础数据</a-breadcrumb-item
        ><a-breadcrumb-item>材料字典</a-breadcrumb-item></a-breadcrumb
      >
      <div class="pt-head-actions">
        <a-button type="primary" @click="handleAdd">新增材料</a-button>
      </div>
    </div>

    <!-- Filter -->
    <div class="pt-filter-surface">
      <div class="pt-filter-row">
        <div class="pt-field">
          <label>材料编码：</label>
          <a-input
            v-model:value="filter.materialCode"
            placeholder="请输入材料编码"
            style="width: 160px"
            allow-clear
          />
        </div>
        <div class="pt-field">
          <label>材料名称：</label>
          <a-input
            v-model:value="filter.materialName"
            placeholder="请输入材料名称"
            style="width: 160px"
            allow-clear
          />
        </div>
        <div class="pt-field">
          <label>状态：</label>
          <a-select
            v-model:value="filter.status"
            placeholder="全部"
            allow-clear
            style="width: 110px"
          >
            <a-select-option value="ENABLE">启用</a-select-option>
            <a-select-option value="DISABLE">禁用</a-select-option>
          </a-select>
        </div>
        <div class="pt-filter-surface-actions">
          <a-button type="primary" @click="handleSearch">查询</a-button>
          <a-button @click="handleReset">重置</a-button>
        </div>
      </div>
    </div>

    <!-- Table -->
    <div class="pt-table-panel">
      <a-table
        :columns="columns"
        :data-source="tableData"
        :loading="loading"
        :pagination="false"
        row-key="id"
        size="small"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'defaultTaxRate'">
            <span>{{ record.defaultTaxRate || '-' }}</span>
          </template>
          <template v-else-if="column.key === 'status'">
            <a-tag :color="STATUS_COLOR[record.status]">
              {{ STATUS_LABEL[record.status] ?? record.status }}
            </a-tag>
          </template>
          <template v-else-if="column.key === 'ops'">
            <div class="pt-link">
              <a class="pt-link" @click="handleEdit(record)">编辑</a>
              <a class="pt-link" @click="handleToggleStatus(record)">
                {{ record.status === 'ENABLE' ? '禁用' : '启用' }}
              </a>
            </div>
          </template>
        </template>
      </a-table>
    </div>

    <!-- Pagination -->
    <div class="pt-pagination">
      <span class="pt-total">共 {{ total }} 条</span>
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

    <!-- Modal -->
    <a-modal
      v-model:open="modalVisible"
      :title="modalTitle"
      :confirm-loading="formLoading"
      width="600px"
      @ok="handleSubmit"
      @cancel="handleCancel"
    >
      <a-form :label-col="{ span: 6 }" :wrapper-col="{ span: 16 }">
        <a-form-item label="材料编码" required>
          <a-input
            v-model:value="formData.materialCode"
            placeholder="请输入材料编码"
            :disabled="isEdit"
          />
        </a-form-item>
        <a-form-item label="材料名称" required>
          <a-input v-model:value="formData.materialName" placeholder="请输入材料名称" />
        </a-form-item>
        <a-form-item label="规格型号">
          <a-input v-model:value="formData.specification" placeholder="请输入规格型号" />
        </a-form-item>
        <a-form-item label="计量单位">
          <a-input v-model:value="formData.unit" placeholder="如：吨、立方米、平方米" />
        </a-form-item>
        <a-form-item label="品牌">
          <a-input v-model:value="formData.brand" placeholder="请输入品牌" />
        </a-form-item>
        <a-form-item label="默认税率(%)">
          <a-input v-model:value="formData.defaultTaxRate" placeholder="如：13.00" />
        </a-form-item>
        <a-form-item label="状态">
          <a-select v-model:value="formData.status" style="width: 120px">
            <a-select-option value="ENABLE">启用</a-select-option>
            <a-select-option value="DISABLE">禁用</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="备注">
          <a-textarea v-model:value="formData.remark" placeholder="请输入备注" :rows="3" />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<style scoped></style>
