<script setup lang="ts">
import { ref, reactive, onMounted, computed } from 'vue'
import { MoreOutlined, PlusOutlined, ReloadOutlined, SearchOutlined } from '@ant-design/icons-vue'
import { message, Modal } from 'ant-design-vue'
import {
  getMaterialList,
  createMaterial,
  updateMaterial,
  updateMaterialStatus,
} from '@/api/modules/material'
import type { MaterialVO } from '@/types/material'

const filter = reactive({
  keyword: '',
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

const gridColumns = computed(() => [
  { field: 'materialCode', title: '材料编码', minWidth: 140, ellipsis: true },
  { field: 'materialName', title: '材料名称', minWidth: 180, ellipsis: true },
  { field: 'specification', title: '规格型号', minWidth: 140, ellipsis: true },
  { field: 'unit', title: '单位', width: 70 },
  { field: 'brand', title: '品牌', minWidth: 110, ellipsis: true },
  {
    field: 'defaultTaxRate',
    title: '默认税率(%)',
    width: 120,
    align: 'right' as const,
    slots: { default: 'defaultTaxRate' },
  },
  { field: 'status', title: '状态', width: 88, slots: { default: 'status' } },
  { field: 'createdAt', title: '创建时间', width: 150 },
  { title: '操作', width: 76, slots: { default: 'ops' } },
])

const materialStats = computed(() => ({
  total: total.value,
  enabled: tableData.value.filter((item) => item.status === 'ENABLE').length,
  disabled: tableData.value.filter((item) => item.status === 'DISABLE').length,
  taxRated: tableData.value.filter((item) => item.defaultTaxRate).length,
}))

const materialUnitSummary = computed(() => {
  const counts = tableData.value.reduce<Record<string, number>>((acc, item) => {
    const key = item.unit || '未维护'
    acc[key] = (acc[key] || 0) + 1
    return acc
  }, {})
  return Object.entries(counts)
    .slice(0, 5)
    .map(([unit, count]) => ({ unit, count }))
})

const recentMaterials = computed(() => tableData.value.slice(0, 4))

async function fetchData() {
  loading.value = true
  try {
    const res = await getMaterialList({
      pageNum: pageNo.value,
      pageSize: pageSize.value,
      keyword: filter.keyword || undefined,
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
  filter.keyword = ''
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
  <div class="lg-list-page lg-page app-page">
    <div class="lg-page-head">
      <div>
        <a-breadcrumb class="lg-breadcrumb">
          <a-breadcrumb-item>基础数据</a-breadcrumb-item>
          <a-breadcrumb-item>材料字典</a-breadcrumb-item>
        </a-breadcrumb>
      </div>
    </div>

    <!-- 搜索栏 -->
    <div class="lg-search-bar">
      <a-input
        v-model:value="filter.keyword"
        placeholder="搜索材料编码、材料名称…"
        allow-clear
        size="large"
        @press-enter="handleSearch"
      >
        <template #prefix><SearchOutlined style="color: var(--text-secondary)" /></template>
      </a-input>
      <a-button type="primary" size="large" @click="handleSearch">查询</a-button>
      <a-button size="large" @click="handleReset">
        <template #icon><ReloadOutlined /></template>
        重置
      </a-button>
    </div>

    <div class="lg-grid">
      <div class="lg-left">
        <div class="lg-kpi-strip">
          <div class="lg-kpi-card">
            <span class="lg-kpi-card-label">材料总数</span>
            <span class="lg-kpi-card-value">{{ materialStats.total }} <small>项</small></span>
          </div>
          <div class="lg-kpi-card">
            <span class="lg-kpi-card-label">启用材料</span>
            <span class="lg-kpi-card-value">{{ materialStats.enabled }} <small>项</small></span>
          </div>
          <div class="lg-kpi-card">
            <span class="lg-kpi-card-label">已维护税率</span>
            <span class="lg-kpi-card-value">{{ materialStats.taxRated }} <small>项</small></span>
          </div>
          <div class="lg-kpi-card is-warn">
            <span class="lg-kpi-card-label">禁用材料</span>
            <span class="lg-kpi-card-value">{{ materialStats.disabled }} <small>项</small></span>
          </div>
        </div>

        <main class="lg-list-table-panel">
          <div class="lg-toolbar">
            <div class="lg-toolbar-left">
              <a-button type="primary" @click="handleAdd">
                <template #icon><PlusOutlined /></template>
                新增材料
              </a-button>
              <a-button @click="fetchData">
                <template #icon><ReloadOutlined /></template>
              </a-button>
            </div>
            <div class="lg-toolbar-right" />
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
            >
              <template #defaultTaxRate="{ row }">
                <span>{{ row.defaultTaxRate || '-' }}</span>
              </template>
              <template #status="{ row }">
                <a-tag :color="STATUS_COLOR[row.status]">
                  {{ STATUS_LABEL[row.status] ?? row.status }}
                </a-tag>
              </template>
              <template #ops="{ row }">
                <a-dropdown :trigger="['click']">
                  <a-button class="lg-row-action-trigger" size="small" type="text">
                    <MoreOutlined />
                  </a-button>
                  <template #overlay>
                    <a-menu>
                      <a-menu-item @click="handleEdit(row)">编辑</a-menu-item>
                      <a-menu-item danger @click="handleToggleStatus(row)">
                        {{ row.status === 'ENABLE' ? '禁用' : '启用' }}
                      </a-menu-item>
                    </a-menu>
                  </template>
                </a-dropdown>
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
        </main>
      </div>

      <aside class="lg-analysis-rail">
        <section class="lg-panel">
          <div class="lg-panel-title">计量单位分布</div>
          <div class="lg-type-list">
            <div v-for="item in materialUnitSummary" :key="item.unit" class="lg-type-row">
              <span class="lg-type-dot" style="background: #1890ff"></span>
              <span class="lg-type-label">{{ item.unit }}</span>
              <span style="margin-left: auto">{{ item.count }} 项</span>
            </div>
            <div v-if="!materialUnitSummary.length" class="lg-warning-empty">暂无材料</div>
          </div>
        </section>
        <section class="lg-panel">
          <div class="lg-panel-title">近期材料</div>
          <div class="lg-type-list">
            <div v-for="item in recentMaterials" :key="item.id" class="lg-type-row">
              <span class="lg-type-dot" style="background: #52c41a"></span>
              <span class="lg-type-label">{{ item.materialName }}</span>
            </div>
            <div v-if="!recentMaterials.length" class="lg-warning-empty">暂无材料</div>
          </div>
        </section>
      </aside>
    </div>

    <!-- Modal -->
    <a-modal
      v-model:open="modalVisible"
      :title="modalTitle"
      :confirm-loading="formLoading"
      width="600px"
      class="lg-modal-form"
      ok-text="保存"
      cancel-text="取消"
      @ok="handleSubmit"
      @cancel="handleCancel"
    >
      <a-form>
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

<style scoped>
/* 页面专属样式 — 其余已由 lg-* 全局类覆盖 */
.lg-breadcrumb {
  margin-bottom: 5px;
  font-size: 13px;
}
</style>
