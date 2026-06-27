<script setup lang="ts">
import { ref, reactive, onMounted, computed } from 'vue'
import {
  CheckCircleOutlined,
  ClockCircleOutlined,
  FileTextOutlined,
  MoreOutlined,
  PercentageOutlined,
  PlusOutlined,
  ReloadOutlined,
  SearchOutlined,
  StopOutlined,
  TagsOutlined,
} from '@ant-design/icons-vue'
import { message, Modal } from 'ant-design-vue'
import {
  getMaterialList,
  getMaterialDetail,
  createMaterial,
  updateMaterial,
  updateMaterialStatus,
} from '@/api/modules/material'
import type { MaterialVO } from '@/types/material'
import { useColumnSettings } from '@/composables/useColumnSettings'
import { ColumnSettingsButton } from '@/components/list-page'

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
const detailVisible = ref(false)
const detailLoading = ref(false)
const detailMaterial = ref<MaterialVO | null>(null)
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
  {
    field: 'materialCode',
    title: '材料编码',
    minWidth: 150,
    ellipsis: true,
    slots: { default: 'materialCode' },
  },
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

const {
  visibleColumns: visibleGridColumns,
  columnSettings,
  colVisible,
  toggleCol,
} = useColumnSettings('material_dict_cols_v2', gridColumns)

if (!localStorage.getItem('material_dict_cols_v2')) {
  colVisible.brand = false
  colVisible.defaultTaxRate = false
  colVisible.createdAt = false
}

const materialStats = computed(() => ({
  total: total.value,
  enabled: tableData.value.filter((item) => item.status === 'ENABLE').length,
  disabled: tableData.value.filter((item) => item.status === 'DISABLE').length,
  taxRated: tableData.value.filter((item) => item.defaultTaxRate).length,
  unitCount: new Set(tableData.value.map((item) => item.unit).filter(Boolean)).size,
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

const materialStatusSummary = computed(() => [
  {
    key: 'ENABLE',
    label: '启用',
    count: tableData.value.filter((item) => item.status === 'ENABLE').length,
    color: '#31c48d',
  },
  {
    key: 'DISABLE',
    label: '禁用',
    count: tableData.value.filter((item) => item.status === 'DISABLE').length,
    color: '#ef4444',
  },
])

function summaryPct(value: number): number {
  const base = tableData.value.length || 1
  return Math.round((value / base) * 100)
}

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
    total.value = Number(res.total ?? 0)
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

async function handleView(record: MaterialVO) {
  detailMaterial.value = record
  detailVisible.value = true
  detailLoading.value = true
  try {
    detailMaterial.value = await getMaterialDetail(record.id)
  } catch (e: unknown) {
    console.error(e)
    message.error('加载材料详情失败')
  } finally {
    detailLoading.value = false
  }
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
  <div class="lg-list-page lg-page app-page material-page">
    <div class="lg-page-head material-page-head">
      <div class="material-page-meta-row">
        <a-breadcrumb class="material-breadcrumb">
          <a-breadcrumb-item>基础数据</a-breadcrumb-item>
          <a-breadcrumb-item>材料字典</a-breadcrumb-item>
        </a-breadcrumb>
        <span class="material-page-subtitle"
          >统一维护材料编码、名称、规格、单位、品牌与默认税率</span
        >
      </div>
    </div>

    <!-- 搜索栏 -->
    <div class="lg-search-bar material-search-bar">
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
        <div class="material-kpi-summary" aria-label="材料关键指标">
          <div class="material-kpi-item">
            <span class="material-kpi-icon is-blue"><FileTextOutlined /></span>
            <span class="material-kpi-label">材料总数</span>
            <strong>{{ materialStats.total }} <small>项</small></strong>
          </div>
          <div class="material-kpi-item">
            <span class="material-kpi-icon is-green"><CheckCircleOutlined /></span>
            <span class="material-kpi-label">启用材料</span>
            <strong>{{ materialStats.enabled }} <small>项</small></strong>
          </div>
          <div class="material-kpi-item">
            <span class="material-kpi-icon is-cyan"><TagsOutlined /></span>
            <span class="material-kpi-label">计量单位</span>
            <strong>{{ materialStats.unitCount }} <small>类</small></strong>
          </div>
          <div class="material-kpi-item">
            <span class="material-kpi-icon is-purple"><PercentageOutlined /></span>
            <span class="material-kpi-label">已维护税率</span>
            <strong>{{ materialStats.taxRated }} <small>项</small></strong>
          </div>
          <div class="material-kpi-item">
            <span class="material-kpi-icon is-red"><StopOutlined /></span>
            <span class="material-kpi-label">禁用材料</span>
            <strong>{{ materialStats.disabled }} <small>项</small></strong>
          </div>
        </div>

        <main class="lg-list-table-panel">
          <div class="lg-toolbar">
            <div class="lg-toolbar-left">
              <span class="material-table-title">材料列表</span>
              <span class="material-table-count">共 {{ total }} 条</span>
              <ColumnSettingsButton
                :columns="columnSettings"
                :visible="colVisible"
                @toggle="toggleCol"
              />
              <a-button type="primary" @click="handleAdd">
                <template #icon><PlusOutlined /></template>
                新增材料
              </a-button>
              <a-button @click="fetchData">
                <template #icon><ReloadOutlined /></template>
                刷新
              </a-button>
            </div>
          </div>

          <!-- 表格 -->
          <div class="lg-table-wrap">
            <vxe-grid
              :data="tableData"
              :columns="visibleGridColumns"
              :loading="loading"
              :column-config="{ resizable: true }"
              stripe
              border="inner"
              size="small"
            >
              <template #materialCode="{ row }">
                <a-button class="material-code-link" type="link" @click="handleView(row)">
                  {{ row.materialCode || '-' }}
                </a-button>
              </template>
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

      <aside class="lg-analysis-rail material-analysis-rail" aria-label="材料辅助分析">
        <div class="material-analysis-panel">
          <header class="material-analysis-head">
            <div>
              <div class="material-analysis-title">材料分析</div>
              <div class="material-analysis-subtitle">单位、状态与近期维护</div>
            </div>
          </header>
          <section class="material-analysis-section">
            <div class="material-section-title">计量单位分布</div>
            <div>
              <div v-for="item in materialUnitSummary" :key="item.unit" class="lg-type-row">
                <span class="lg-type-dot" style="background: #2563eb"></span>
                <span class="lg-type-label">{{ item.unit }}</span>
                <span class="lg-type-bar-wrap">
                  <span
                    class="lg-type-bar"
                    :style="{ width: summaryPct(item.count) + '%', background: '#2563eb' }"
                  ></span>
                </span>
                <span class="lg-type-num">{{ item.count }}</span>
                <span class="lg-type-pct">{{ summaryPct(item.count) }}%</span>
              </div>
              <div v-if="!materialUnitSummary.length" class="lg-warning-empty">暂无材料</div>
            </div>
          </section>
          <section class="material-analysis-section">
            <div class="material-section-title">材料状态</div>
            <div v-for="item in materialStatusSummary" :key="item.key" class="lg-type-row">
              <span class="lg-type-dot" :style="{ background: item.color }"></span>
              <span class="lg-type-label">{{ item.label }}</span>
              <span class="lg-type-bar-wrap">
                <span
                  class="lg-type-bar"
                  :style="{ width: summaryPct(item.count) + '%', background: item.color }"
                ></span>
              </span>
              <span class="lg-type-num">{{ item.count }}</span>
              <span class="lg-type-pct">{{ summaryPct(item.count) }}%</span>
            </div>
          </section>
          <section class="material-analysis-section">
            <div class="material-section-title">近期材料</div>
            <div>
              <div v-for="item in recentMaterials" :key="item.id" class="lg-type-row">
                <span class="lg-type-dot" style="background: #52c41a"></span>
                <span class="lg-type-label">{{ item.materialName }}</span>
                <span class="lg-type-bar-wrap"></span>
                <span class="lg-type-num"><ClockCircleOutlined /></span>
                <span class="lg-type-pct"></span>
              </div>
              <div v-if="!recentMaterials.length" class="lg-warning-empty">暂无材料</div>
            </div>
          </section>
        </div>
      </aside>
    </div>

    <!-- Modal -->
    <a-modal
      v-model:open="modalVisible"
      :title="modalTitle"
      :confirm-loading="formLoading"
      width="800px"
      class="lg-modal-form"
      wrap-class-name="compact-material-modal"
      ok-text="保存"
      cancel-text="取消"
      @ok="handleSubmit"
      @cancel="handleCancel"
    >
      <a-form size="small" :label-col="{ span: 6 }" :wrapper-col="{ span: 17 }">
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
          <a-textarea v-model:value="formData.remark" placeholder="请输入备注" :rows="2" />
        </a-form-item>
      </a-form>
    </a-modal>

    <a-modal
      v-model:open="detailVisible"
      title="材料详情"
      :footer="null"
      :width="800"
      wrap-class-name="compact-material-detail-modal"
    >
      <a-spin :spinning="detailLoading">
        <a-descriptions
          v-if="detailMaterial"
          bordered
          size="small"
          :column="2"
          class="material-detail-descriptions"
        >
          <a-descriptions-item label="材料编码">
            {{ detailMaterial.materialCode || '-' }}
          </a-descriptions-item>
          <a-descriptions-item label="材料名称">
            {{ detailMaterial.materialName || '-' }}
          </a-descriptions-item>
          <a-descriptions-item label="规格型号">
            {{ detailMaterial.specification || '-' }}
          </a-descriptions-item>
          <a-descriptions-item label="计量单位">
            {{ detailMaterial.unit || '-' }}
          </a-descriptions-item>
          <a-descriptions-item label="品牌">
            {{ detailMaterial.brand || '-' }}
          </a-descriptions-item>
          <a-descriptions-item label="默认税率">
            {{ detailMaterial.defaultTaxRate || '-' }}
          </a-descriptions-item>
          <a-descriptions-item label="状态">
            <a-tag :color="STATUS_COLOR[detailMaterial.status]">
              {{ STATUS_LABEL[detailMaterial.status] ?? detailMaterial.status }}
            </a-tag>
          </a-descriptions-item>
          <a-descriptions-item label="创建时间">
            {{ detailMaterial.createdAt || '-' }}
          </a-descriptions-item>
          <a-descriptions-item label="更新时间" :span="2">
            {{ detailMaterial.updatedAt || '-' }}
          </a-descriptions-item>
          <a-descriptions-item label="备注" :span="2">
            {{ detailMaterial.remark || '-' }}
          </a-descriptions-item>
        </a-descriptions>
      </a-spin>
    </a-modal>
  </div>
</template>

<style scoped>
.material-page-head {
  align-items: center;
  justify-content: space-between;
  min-height: 0;
  margin-bottom: 7px;
  padding: 0;
}

.material-page-meta-row {
  display: flex;
  align-items: center;
  gap: 5em;
  min-width: 0;
}

.material-breadcrumb {
  font-size: 13px;
  line-height: 20px;
}

.material-page-subtitle {
  color: var(--text-secondary);
  font-size: 13px;
  line-height: 20px;
  white-space: nowrap;
}

.material-search-bar {
  margin-top: 21px;
  min-height: 74px;
}

.material-page .lg-grid {
  margin-top: 14px;
}

.material-kpi-summary {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 0;
  overflow: hidden;
  height: 88px;
  min-height: 88px;
  background: var(--surface);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-soft);
}

.material-kpi-item {
  display: grid;
  grid-template-columns: 38px minmax(0, 1fr);
  grid-template-rows: 20px 30px;
  column-gap: 10px;
  align-content: center;
  min-width: 0;
  padding: 16px 18px;
  border-right: 1px solid var(--border-subtle);
}

.material-kpi-item:last-child {
  border-right: 0;
}

.material-kpi-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  border-radius: var(--radius-sm);
  grid-row: 1 / span 2;
}

.material-kpi-icon.is-blue {
  color: var(--primary);
  background: var(--primary-soft);
}
.material-kpi-icon.is-green {
  color: var(--success);
  background: var(--success-soft);
}
.material-kpi-icon.is-cyan {
  color: #0891b2;
  background: #ecfeff;
}
.material-kpi-icon.is-purple {
  color: #7c3aed;
  background: #f3e8ff;
}
.material-kpi-icon.is-red {
  color: var(--error);
  background: var(--error-soft);
}

.material-kpi-label,
.material-table-count,
.material-analysis-subtitle {
  color: var(--text-secondary);
  font-size: 13px;
}

.material-kpi-item strong {
  overflow: hidden;
  color: var(--text);
  font-size: 24px;
  font-weight: 800;
  line-height: 28px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.material-kpi-item small {
  margin-left: 4px;
  color: var(--text-secondary);
  font-size: 13px;
  font-weight: 600;
}

.material-table-title {
  color: var(--text);
  font-size: 15px;
  font-weight: 800;
}

.material-code-link {
  height: auto;
  padding: 0;
  font-weight: 700;
}

.material-analysis-rail {
  width: 336px;
}

.material-analysis-panel {
  display: flex;
  flex-direction: column;
  gap: 18px;
  height: 856px;
  min-height: 856px;
  box-sizing: border-box;
  padding: 18px;
  background: var(--surface);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-soft);
}

.material-analysis-title {
  color: var(--text);
  font-size: 16px;
  font-weight: 800;
  line-height: 22px;
}

.material-analysis-section {
  display: flex;
  flex-direction: column;
  gap: 10px;
  min-width: 0;
  padding-top: 16px;
  border-top: 1px solid var(--border-subtle);
}

.material-section-title {
  color: var(--text);
  font-size: 14px;
  font-weight: 700;
  line-height: 20px;
}

.material-analysis-section :deep(.lg-type-row),
.material-analysis-section .lg-type-row {
  grid-template-columns: 9px minmax(54px, 72px) minmax(72px, 1fr) 20px 38px;
}

:global(.compact-material-modal .ant-modal-body) {
  max-height: calc(100vh - 220px);
  overflow-y: auto;
  padding-top: 14px;
  padding-bottom: 8px;
}

:global(.compact-material-modal .ant-form-item) {
  margin-bottom: 10px;
}

:global(.compact-material-detail-modal .ant-modal-body) {
  max-height: calc(100vh - 220px);
  overflow-y: auto;
}

.material-detail-descriptions :deep(.ant-descriptions-item-label) {
  width: 116px;
  color: var(--text-secondary);
  font-weight: 600;
}
</style>
