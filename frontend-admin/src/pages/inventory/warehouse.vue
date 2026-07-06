<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { message, Modal } from 'ant-design-vue'
import {
  CheckCircleOutlined,
  ClockCircleOutlined,
  DatabaseOutlined,
  FolderOpenOutlined,
  MoreOutlined,
  PlusOutlined,
  ReloadOutlined,
  SearchOutlined,
  StopOutlined,
} from '@ant-design/icons-vue'
import {
  getWarehouseList,
  createWarehouse,
  updateWarehouse,
  deleteWarehouse,
} from '@/api/modules/inventory'
import { useReferenceStore } from '@/stores/reference'
import type { WarehouseVO } from '@/types/inventory'
import { useColumnSettings } from '@/composables/useColumnSettings'
import { ColumnSettingsButton } from '@/components/list-page'

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
  { field: 'warehouseCode', title: '仓库编号', minWidth: 140, ellipsis: true },
  { field: 'warehouseName', title: '仓库名称', minWidth: 160, ellipsis: true },
  { field: 'projectName', title: '所属项目', minWidth: 160, ellipsis: true },
  { field: 'status', title: '状态', width: 88, slots: { default: 'status' } },
  { field: 'createdAt', title: '创建时间', width: 140 },
  { title: '操作', width: 76, slots: { default: 'ops' } },
])

const {
  visibleColumns: visibleGridColumns,
  columnSettings,
  colVisible,
  toggleCol,
} = useColumnSettings('warehouse_list_cols', gridColumns)

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
    total.value = Number(res.total ?? 0)
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
const kpiWhDisabled = computed(() => tableData.value.filter((r) => r.status === 'DISABLE').length)
const kpiProjectCount = computed(
  () => new Set(tableData.value.map((r) => r.projectId).filter(Boolean)).size,
)
const recentWarehouses = computed(() => tableData.value.slice(0, 4))

const warehouseStatusSummary = computed(() => [
  { key: 'ENABLE', label: '启用仓库', count: kpiWhEnabled.value, color: '#31c48d' },
  { key: 'DISABLE', label: '停用仓库', count: kpiWhDisabled.value, color: '#ef4444' },
])

const projectSummary = computed(() => {
  const counts = tableData.value.reduce<Record<string, number>>((acc, item) => {
    const key = item.projectName || '未关联项目'
    acc[key] = (acc[key] || 0) + 1
    return acc
  }, {})
  return Object.entries(counts)
    .slice(0, 4)
    .map(([name, count]) => ({ name, count }))
})

function summaryPct(value: number): number {
  const base = tableData.value.length || 1
  return Math.round((value / base) * 100)
}

onMounted(() => {
  referenceStore.fetchProjects()
  fetchData()
})
</script>

<template>
  <div class="lg-list-page lg-page app-page warehouse-page">
    <!-- Page head -->
    <div class="lg-page-head warehouse-page-head">
      <div class="warehouse-page-meta-row">
        <a-breadcrumb class="warehouse-breadcrumb">
          <a-breadcrumb-item>库存管理</a-breadcrumb-item>
          <a-breadcrumb-item>仓库</a-breadcrumb-item>
        </a-breadcrumb>
        <span class="warehouse-page-subtitle"
          >维护项目仓库基础信息，控制启停状态并支撑库存台账筛选</span
        >
      </div>
    </div>

    <div class="lg-grid">
      <div class="lg-left">
        <!-- KPI strip -->
        <div class="warehouse-kpi-summary" aria-label="仓库关键指标">
          <div class="warehouse-kpi-item">
            <span class="warehouse-kpi-icon is-blue"><DatabaseOutlined /></span>
            <span class="warehouse-kpi-label">仓库总数</span>
            <strong>{{ kpiWhTotal }} <small>个</small></strong>
          </div>
          <div class="warehouse-kpi-item">
            <span class="warehouse-kpi-icon is-green"><CheckCircleOutlined /></span>
            <span class="warehouse-kpi-label">启用仓库</span>
            <strong>{{ kpiWhEnabled }} <small>个</small></strong>
          </div>
          <div class="warehouse-kpi-item">
            <span class="warehouse-kpi-icon is-red"><StopOutlined /></span>
            <span class="warehouse-kpi-label">停用仓库</span>
            <strong>{{ kpiWhDisabled }} <small>个</small></strong>
          </div>
          <div class="warehouse-kpi-item">
            <span class="warehouse-kpi-icon is-cyan"><FolderOpenOutlined /></span>
            <span class="warehouse-kpi-label">关联项目</span>
            <strong>{{ kpiProjectCount }} <small>个</small></strong>
          </div>
          <div class="warehouse-kpi-item">
            <span class="warehouse-kpi-icon is-purple"><ClockCircleOutlined /></span>
            <span class="warehouse-kpi-label">本页记录</span>
            <strong>{{ tableData.length }} <small>条</small></strong>
          </div>
        </div>

        <div class="lg-search-bar warehouse-search-bar">
          <div class="warehouse-search-fields">
            <a-select
              v-model:value="filter.projectId"
              placeholder="全部项目"
              allow-clear
              size="large"
              @change="handleSearch"
            >
              <a-select-option v-for="p in projectList" :key="p.id" :value="p.id">
                {{ p.projectName }}
              </a-select-option>
            </a-select>
            <a-select
              v-model:value="filter.status"
              placeholder="全部状态"
              allow-clear
              size="large"
              @change="handleSearch"
            >
              <a-select-option value="ENABLE">启用</a-select-option>
              <a-select-option value="DISABLE">停用</a-select-option>
            </a-select>
          </div>
          <div class="warehouse-search-keyword-row">
            <a-input
              v-model:value="filter.keyword"
              placeholder="搜索仓库编号、名称"
              allow-clear
              size="large"
              @press-enter="handleSearch"
            >
              <template #prefix><SearchOutlined style="color: var(--text-secondary)" /></template>
            </a-input>
            <div class="warehouse-search-actions">
              <a-button type="primary" size="large" @click="handleSearch">查询</a-button>
              <a-button size="large" @click="handleReset">
                <template #icon><ReloadOutlined /></template>
                重置
              </a-button>
            </div>
          </div>
        </div>

        <main class="lg-list-table-panel">
          <!-- 工具栏 -->
          <div class="lg-toolbar">
            <div class="lg-toolbar-left">
              <span class="warehouse-table-title">仓库列表</span>
              <span class="warehouse-table-count">共 {{ total }} 条</span>
              <ColumnSettingsButton
                :columns="columnSettings"
                :visible="colVisible"
                @toggle="toggleCol"
              />
              <a-button type="primary" @click="handleAdd">
                <template #icon><PlusOutlined /></template>
                新建仓库
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
                      <a-menu-item danger @click="handleDelete(row)">删除</a-menu-item>
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

      <aside class="lg-analysis-rail warehouse-analysis-rail" aria-label="仓库辅助分析">
        <div class="warehouse-analysis-panel">
          <header class="warehouse-analysis-head">
            <div>
              <div class="warehouse-analysis-title">仓库分析</div>
              <div class="warehouse-analysis-subtitle">状态、项目与近期维护</div>
            </div>
            <a-button type="link" size="small" @click="fetchData">刷新</a-button>
          </header>
          <section class="warehouse-analysis-section">
            <div class="warehouse-section-title">仓库状态分布</div>
            <div v-for="item in warehouseStatusSummary" :key="item.key" class="lg-type-row">
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
          <section class="warehouse-analysis-section">
            <div class="warehouse-section-title">项目仓库分布</div>
            <div v-for="item in projectSummary" :key="item.name" class="lg-type-row">
              <span class="lg-type-dot" style="background: #2563eb"></span>
              <span class="lg-type-label">{{ item.name }}</span>
              <span class="lg-type-bar-wrap">
                <span
                  class="lg-type-bar"
                  :style="{ width: summaryPct(item.count) + '%', background: '#2563eb' }"
                ></span>
              </span>
              <span class="lg-type-num">{{ item.count }}</span>
              <span class="lg-type-pct">{{ summaryPct(item.count) }}%</span>
            </div>
            <div v-if="!projectSummary.length" class="lg-warning-empty">暂无项目仓库</div>
          </section>
          <section class="warehouse-analysis-section">
            <div class="warehouse-section-title">近期仓库</div>
            <div>
              <div v-for="item in recentWarehouses" :key="item.id" class="lg-type-row">
                <span class="lg-type-dot" style="background: #1890ff"></span>
                <span class="lg-type-label">{{ item.warehouseName }}</span>
                <span class="lg-type-bar-wrap"></span>
                <span class="lg-type-num"><ClockCircleOutlined /></span>
                <span class="lg-type-pct"></span>
              </div>
              <div v-if="!recentWarehouses.length" class="lg-warning-empty">暂无仓库</div>
            </div>
          </section>
        </div>
      </aside>
    </div>

    <!-- Add/Edit Modal -->
    <a-modal
      v-model:open="modalVisible"
      :title="modalTitle"
      :width="800"
      wrap-class-name="compact-warehouse-modal"
      @ok="handleModalOk"
      @cancel="handleModalCancel"
    >
      <a-form size="small" :label-col="{ span: 6 }" :wrapper-col="{ span: 17 }">
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

<style scoped>
.warehouse-page-head {
  align-items: center;
  justify-content: space-between;
  min-height: 0;
  margin-bottom: 0;
  padding: 18px 20px;
  background: var(--surface);
  border: 1px solid var(--border-subtle);
  border-left: 4px solid var(--primary);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-soft);
}

.warehouse-page-meta-row {
  display: flex;
  align-items: center;
  gap: 5em;
  min-width: 0;
}

.warehouse-breadcrumb {
  font-size: 13px;
  line-height: 20px;
}

.warehouse-page-subtitle {
  color: var(--text-secondary);
  font-size: 13px;
  line-height: 20px;
  white-space: nowrap;
}

.warehouse-search-bar {
  display: flex;
  flex-direction: column;
  align-items: stretch;
  gap: 12px;
  min-height: 74px;
  padding: 16px;
  border-left: 4px solid var(--primary-soft);
}

.warehouse-search-fields,
.warehouse-search-keyword-row,
.warehouse-search-actions {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
}

.warehouse-search-keyword-row > :deep(.ant-input-affix-wrapper) {
  min-width: 320px;
  flex: 1 1 auto;
}

.warehouse-search-fields > :deep(.ant-select) {
  min-width: 160px;
  flex: 1 1 190px;
}

.warehouse-search-actions {
  flex: 0 0 auto;
}

.warehouse-page .lg-grid {
  margin-top: 14px;
}

.warehouse-kpi-summary {
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

.warehouse-page .lg-list-table-panel {
  border-top: 3px solid var(--primary);
}

.warehouse-page .lg-list-table-panel > .lg-toolbar {
  min-height: 58px;
  background: linear-gradient(180deg, var(--surface), var(--surface-subtle));
}

.warehouse-kpi-item {
  display: grid;
  grid-template-columns: 38px minmax(0, 1fr);
  grid-template-rows: 20px 30px;
  column-gap: 10px;
  align-content: center;
  min-width: 0;
  padding: 16px 18px;
  border-right: 1px solid var(--border-subtle);
}

.warehouse-kpi-item:last-child {
  border-right: 0;
}

.warehouse-kpi-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  border-radius: var(--radius-sm);
  grid-row: 1 / span 2;
}

.warehouse-kpi-icon.is-blue {
  color: var(--primary);
  background: var(--primary-soft);
}
.warehouse-kpi-icon.is-green {
  color: var(--success);
  background: var(--success-soft);
}
.warehouse-kpi-icon.is-red {
  color: var(--error);
  background: var(--error-soft);
}
.warehouse-kpi-icon.is-cyan {
  color: #0891b2;
  background: #ecfeff;
}
.warehouse-kpi-icon.is-purple {
  color: #7c3aed;
  background: #f3e8ff;
}

.warehouse-kpi-label,
.warehouse-table-count,
.warehouse-analysis-subtitle {
  color: var(--text-secondary);
  font-size: 13px;
}

.warehouse-kpi-item strong {
  overflow: hidden;
  color: var(--text);
  font-size: 24px;
  font-weight: 800;
  line-height: 28px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.warehouse-kpi-item small {
  margin-left: 4px;
  color: var(--text-secondary);
  font-size: 13px;
  font-weight: 600;
}

.warehouse-table-title {
  color: var(--text);
  font-size: 15px;
  font-weight: 800;
}

.warehouse-analysis-rail {
  width: 336px;
}

.warehouse-analysis-panel {
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

.warehouse-analysis-title {
  color: var(--text);
  font-size: 16px;
  font-weight: 800;
  line-height: 22px;
}

.warehouse-analysis-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.warehouse-analysis-section {
  display: flex;
  flex-direction: column;
  gap: 10px;
  min-width: 0;
  padding-top: 16px;
  border-top: 1px solid var(--border-subtle);
}

@media (max-width: 900px) {
  .warehouse-search-bar,
  .warehouse-search-fields,
  .warehouse-search-actions {
    display: flex;
    align-items: stretch;
    flex-direction: column;
  }
}

.warehouse-section-title {
  color: var(--text);
  font-size: 14px;
  font-weight: 700;
  line-height: 20px;
}

.warehouse-analysis-section :deep(.lg-type-row),
.warehouse-analysis-section .lg-type-row {
  grid-template-columns: 9px minmax(54px, 72px) minmax(72px, 1fr) 20px 38px;
}

:global(.compact-warehouse-modal .ant-modal-body) {
  max-height: calc(100vh - 220px);
  overflow-y: auto;
  padding-top: 14px;
  padding-bottom: 8px;
}

:global(.compact-warehouse-modal .ant-form-item) {
  margin-bottom: 10px;
}
</style>
