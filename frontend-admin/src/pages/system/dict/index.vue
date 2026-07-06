<script setup lang="ts">
import { ref, reactive, onMounted, computed } from 'vue'
import { message, Modal } from 'ant-design-vue'
import { MoreOutlined } from '@ant-design/icons-vue'
import {
  getDictTypeList,
  createDictType,
  updateDictType,
  deleteDictType,
  getDictDataList,
  createDictData,
  updateDictData,
  deleteDictData,
} from '@/api/modules/dict'
import type { DictTypeVO, DictDataVO } from '@/types/dict'
import { DICT_DATA_GRID_COLUMNS, DICT_STATUS_LABEL } from './dictPageConfig'

/* ========== 字典类型列表（左侧） ========== */

const typeFilter = ref('')
const typeLoading = ref(false)
const typeList = ref<DictTypeVO[]>([])
const selectedTypeId = ref<string>('')
const selectedTypeName = computed(() => {
  const t = typeList.value.find((x) => x.id === selectedTypeId.value)
  return t?.dictName ?? ''
})

const typeModalVisible = ref(false)
const typeModalTitle = ref('新增字典类型')
const typeFormLoading = ref(false)
const typeIsEdit = ref(false)
const typeForm = reactive({
  id: '' as string,
  dictCode: '',
  dictName: '',
  status: 'ENABLED',
})

async function fetchTypeList() {
  typeLoading.value = true
  try {
    const res = await getDictTypeList({
      pageNum: 1,
      pageSize: 50,
      dictName: typeFilter.value || undefined,
    })
    const records = Array.isArray(res?.records) ? res.records : Array.isArray(res) ? res : []
    typeList.value = records
    // 如果当前没有选中项，默认选中第一个
    if (typeList.value.length > 0 && !selectedTypeId.value) {
      selectedTypeId.value = typeList.value[0].id
      fetchDataList()
    }
  } catch (e: unknown) {
    console.error(e)
    typeList.value = []
    message.error('加载字典类型失败')
  } finally {
    typeLoading.value = false
  }
}

function handleSelectType(id: string) {
  if (selectedTypeId.value === id) return
  selectedTypeId.value = id
  dataPageNo.value = 1
  fetchDataList()
}

function handleTypeSearch() {
  fetchTypeList()
}

/* --- 字典类型弹窗 --- */

function handleAddType() {
  typeIsEdit.value = false
  typeModalTitle.value = '新增字典类型'
  typeForm.id = ''
  typeForm.dictCode = ''
  typeForm.dictName = ''
  typeForm.status = 'ENABLED'
  typeModalVisible.value = true
}

function handleEditType(record: DictTypeVO) {
  typeIsEdit.value = true
  typeModalTitle.value = '编辑字典类型'
  typeForm.id = record.id
  typeForm.dictCode = record.dictCode
  typeForm.dictName = record.dictName
  typeForm.status = record.status
  typeModalVisible.value = true
}

async function handleDeleteType(record: DictTypeVO) {
  Modal.confirm({
    title: '确认删除',
    content: `确定要删除字典类型"${record.dictName}"吗？删除后该类型下的字典数据也将不可用。`,
    okText: '确定',
    cancelText: '取消',
    okType: 'danger',
    onOk: async () => {
      try {
        await deleteDictType(record.id)
        message.success('删除成功')
        if (selectedTypeId.value === record.id) {
          selectedTypeId.value = ''
        }
        fetchTypeList()
      } catch (e: unknown) {
        console.error(e)
        message.error('删除失败')
      }
    },
  })
}

async function handleTypeSubmit() {
  if (!typeForm.dictCode || !typeForm.dictName) {
    message.error('请填写必填项')
    return
  }
  typeFormLoading.value = true
  try {
    if (typeIsEdit.value && typeForm.id) {
      await updateDictType(typeForm.id, {
        dictCode: typeForm.dictCode,
        dictName: typeForm.dictName,
        status: typeForm.status,
      })
      message.success('编辑成功')
    } else {
      await createDictType({
        dictCode: typeForm.dictCode,
        dictName: typeForm.dictName,
        status: typeForm.status,
      })
      message.success('新增成功')
    }
    typeModalVisible.value = false
    fetchTypeList()
  } catch (e: unknown) {
    console.error(e)
    message.error(typeIsEdit.value ? '编辑失败' : '新增失败')
  } finally {
    typeFormLoading.value = false
  }
}

function handleTypeCancel() {
  typeModalVisible.value = false
}

/* ========== 字典数据表格（右侧） ========== */

const dataFilter = reactive({
  dictLabel: '',
  status: undefined as string | undefined,
})

const dataLoading = ref(false)
const dataTableData = ref<DictDataVO[]>([])
const dataTotal = ref(0)
const dataPageNo = ref(1)
const dataPageSize = ref(20)

const dataModalVisible = ref(false)
const dataModalTitle = ref('新增字典数据')
const dataFormLoading = ref(false)
const dataIsEdit = ref(false)
const dataForm = reactive({
  id: '' as string,
  dictTypeId: '' as string,
  dictLabel: '',
  dictValue: '',
  cssClass: '',
  listClass: '',
  orderNum: 0,
  status: 'ENABLED',
})

const dataGridColumns = computed(() => [...DICT_DATA_GRID_COLUMNS])

async function fetchDataList() {
  if (!selectedTypeId.value) {
    dataTableData.value = []
    dataTotal.value = 0
    return
  }
  dataLoading.value = true
  try {
    const res = await getDictDataList({
      pageNum: dataPageNo.value,
      pageSize: dataPageSize.value,
      typeId: selectedTypeId.value,
      dictLabel: dataFilter.dictLabel || undefined,
      status: dataFilter.status,
    })
    const records = Array.isArray(res?.records) ? res.records : Array.isArray(res) ? res : []
    dataTableData.value = records.map((item) => ({
      ...item,
      statusLabel: DICT_STATUS_LABEL[item.status] ?? item.status,
    }))
    dataTotal.value = Number(res?.total ?? records.length)
  } catch (e: unknown) {
    console.error(e)
    dataTableData.value = []
    dataTotal.value = 0
    message.error('加载字典数据失败')
  } finally {
    dataLoading.value = false
  }
}

function handleDataSearch() {
  dataPageNo.value = 1
  fetchDataList()
}

function handleDataReset() {
  dataFilter.dictLabel = ''
  dataFilter.status = undefined
  dataPageNo.value = 1
  fetchDataList()
}

function handleDataPageChange(page: number) {
  dataPageNo.value = page
  fetchDataList()
}

function handleDataPageSizeChange(_cur: number, size: number) {
  dataPageSize.value = size
  dataPageNo.value = 1
  fetchDataList()
}

/* --- 字典数据弹窗 --- */

function handleAddData() {
  if (!selectedTypeId.value) {
    message.warning('请先选择左侧字典类型')
    return
  }
  dataIsEdit.value = false
  dataModalTitle.value = '新增字典数据'
  dataForm.id = ''
  dataForm.dictTypeId = selectedTypeId.value
  dataForm.dictLabel = ''
  dataForm.dictValue = ''
  dataForm.cssClass = ''
  dataForm.listClass = ''
  dataForm.orderNum = 0
  dataForm.status = 'ENABLED'
  dataModalVisible.value = true
}

function handleEditData(record: DictDataVO) {
  dataIsEdit.value = true
  dataModalTitle.value = '编辑字典数据'
  dataForm.id = record.id
  dataForm.dictTypeId = record.dictTypeId
  dataForm.dictLabel = record.dictLabel
  dataForm.dictValue = record.dictValue
  dataForm.cssClass = record.cssClass || ''
  dataForm.listClass = record.listClass || ''
  dataForm.orderNum = record.orderNum ?? 0
  dataForm.status = record.status
  dataModalVisible.value = true
}

async function handleDeleteData(record: DictDataVO) {
  Modal.confirm({
    title: '确认删除',
    content: `确定要删除字典数据"${record.dictLabel}"吗？`,
    okText: '确定',
    cancelText: '取消',
    okType: 'danger',
    onOk: async () => {
      try {
        await deleteDictData(record.id)
        message.success('删除成功')
        fetchDataList()
      } catch (e: unknown) {
        console.error(e)
        message.error('删除失败')
      }
    },
  })
}

async function handleDataSubmit() {
  if (!dataForm.dictLabel || !dataForm.dictValue) {
    message.error('请填写必填项')
    return
  }
  if (!dataForm.dictTypeId) {
    message.error('字典类型不能为空')
    return
  }
  dataFormLoading.value = true
  try {
    const payload = {
      dictTypeId: dataForm.dictTypeId,
      dictLabel: dataForm.dictLabel,
      dictValue: dataForm.dictValue,
      cssClass: dataForm.cssClass || undefined,
      listClass: dataForm.listClass || undefined,
      orderNum: dataForm.orderNum,
      status: dataForm.status,
    }
    if (dataIsEdit.value && dataForm.id) {
      await updateDictData(dataForm.id, payload)
      message.success('编辑成功')
    } else {
      await createDictData(payload)
      message.success('新增成功')
    }
    dataModalVisible.value = false
    fetchDataList()
  } catch (e: unknown) {
    console.error(e)
    message.error(dataIsEdit.value ? '编辑失败' : '新增失败')
  } finally {
    dataFormLoading.value = false
  }
}

function handleDataCancel() {
  dataModalVisible.value = false
}

/* ========== 初始化 ========== */

onMounted(() => {
  fetchTypeList()
})
</script>

<template>
  <div class="lg-list-page lg-page app-page dict-page">
    <div class="lg-page-head dict-page-head">
      <div class="dict-page-meta-row">
        <a-breadcrumb class="dict-breadcrumb">
          <a-breadcrumb-item>系统设置</a-breadcrumb-item>
          <a-breadcrumb-item>字典管理</a-breadcrumb-item>
        </a-breadcrumb>
        <span class="dict-page-subtitle">维护业务状态、类型与枚举字典，统一系统字段展示口径</span>
      </div>
    </div>

    <main class="dc-panel">
      <!-- 左侧：字典类型列表 -->
      <div class="dc-left">
        <div class="dc-left-header">
          <span class="dc-left-title">字典类型</span>
          <a-button type="primary" @click="handleAddType">新增</a-button>
        </div>

        <div class="dc-left-search">
          <a-input-search
            v-model:value="typeFilter"
            placeholder="搜索字典名称"
            @search="handleTypeSearch"
          />
        </div>

        <div class="dc-left-list" v-loading="typeLoading">
          <div v-if="typeList.length === 0 && !typeLoading" class="dc-left-empty">暂无字典类型</div>
          <div
            v-for="item in typeList"
            :key="item.id"
            class="dc-type-item"
            :class="{ 'dc-type-item--active': selectedTypeId === item.id }"
            @click="handleSelectType(item.id)"
          >
            <div class="dc-type-info">
              <span class="dc-type-name">{{ item.dictName }}</span>
              <span class="dc-type-code">{{ item.dictCode }}</span>
            </div>
            <div class="dc-type-actions">
              <a-button type="link" size="small" @click.stop="handleEditType(item)">编辑</a-button>
              <a-button type="link" size="small" danger @click.stop="handleDeleteType(item)"
                >删除</a-button
              >
            </div>
          </div>
        </div>

        <div class="dc-left-footer">共 {{ typeList.length }} 个类型</div>
      </div>

      <!-- 右侧：字典数据表格 -->
      <div class="dc-right">
        <template v-if="selectedTypeId">
          <div class="dc-right-header">
            <span class="dc-right-title">{{ selectedTypeName || '字典数据' }}</span>
            <a-button type="primary" @click="handleAddData">新增数据</a-button>
          </div>

          <!-- 数据筛选工具栏 -->
          <div class="lg-toolbar">
            <div class="lg-toolbar-left">
              <a-input
                v-model:value="dataFilter.dictLabel"
                placeholder="搜索字典标签"
                class="dict-filter-input"
                allow-clear
                @press-enter="handleDataSearch"
              />
              <a-select
                v-model:value="dataFilter.status"
                placeholder="全部状态"
                allow-clear
                class="dict-filter-status"
              >
                <a-select-option value="ENABLED">启用</a-select-option>
                <a-select-option value="DISABLED">禁用</a-select-option>
              </a-select>
            </div>
            <div class="lg-toolbar-right">
              <a-button type="primary" @click="handleDataSearch">查询</a-button>
              <a-button @click="handleDataReset">重置</a-button>
            </div>
          </div>

          <!-- 数据表格 -->
          <div class="lg-table-wrap">
            <vxe-grid
              :data="dataTableData"
              :columns="dataGridColumns"
              :loading="dataLoading"
              :column-config="{ resizable: true }"
              stripe
              border="inner"
              size="small"
            >
              <template #status="{ row }">
                <a-tag :color="row.status === 'ENABLED' ? 'success' : 'default'">
                  {{ STATUS_LABEL[row.status] ?? row.status }}
                </a-tag>
              </template>
              <template #ops="{ row }">
                <a-dropdown :trigger="['click']">
                  <a-button
                    class="lg-row-action-trigger"
                    size="small"
                    type="text"
                    title="字典数据操作"
                    aria-label="字典数据操作"
                  >
                    <MoreOutlined />
                  </a-button>
                  <template #overlay>
                    <a-menu>
                      <a-menu-item @click="handleEditData(row)">编辑</a-menu-item>
                      <a-menu-item danger @click="handleDeleteData(row)">删除</a-menu-item>
                    </a-menu>
                  </template>
                </a-dropdown>
              </template>
            </vxe-grid>
          </div>

          <!-- 分页 -->
          <div class="lg-pagination">
            <span class="lg-total">共 {{ dataTotal }} 条</span>
            <a-pagination
              v-model:current="dataPageNo"
              v-model:page-size="dataPageSize"
              :total="dataTotal"
              :page-size-options="['10', '20', '50', '100']"
              show-size-changer
              show-quick-jumper
              size="small"
              @change="handleDataPageChange"
              @show-size-change="handleDataPageSizeChange"
            />
          </div>
        </template>

        <!-- 未选择类型时的占位 -->
        <div v-else class="dc-right-empty">
          <a-empty description="请在左侧选择一个字典类型" />
        </div>
      </div>
    </main>

    <!-- 字典类型弹窗 -->
    <a-modal
      v-model:open="typeModalVisible"
      :title="typeModalTitle"
      :confirm-loading="typeFormLoading"
      :width="800"
      class="lg-modal-form"
      ok-text="保存"
      cancel-text="取消"
      @ok="handleTypeSubmit"
      @cancel="handleTypeCancel"
    >
      <a-form>
        <a-form-item label="字典编码" required>
          <a-input
            v-model:value="typeForm.dictCode"
            placeholder="请输入字典编码"
            :disabled="typeIsEdit"
          />
        </a-form-item>
        <a-form-item label="字典名称" required>
          <a-input v-model:value="typeForm.dictName" placeholder="请输入字典名称" />
        </a-form-item>
        <a-form-item label="状态">
          <a-select v-model:value="typeForm.status" class="dict-status-select">
            <a-select-option value="ENABLED">启用</a-select-option>
            <a-select-option value="DISABLED">禁用</a-select-option>
          </a-select>
        </a-form-item>
      </a-form>
    </a-modal>

    <!-- 字典数据弹窗 -->
    <a-modal
      v-model:open="dataModalVisible"
      :title="dataModalTitle"
      :confirm-loading="dataFormLoading"
      :width="800"
      class="lg-modal-form"
      ok-text="保存"
      cancel-text="取消"
      @ok="handleDataSubmit"
      @cancel="handleDataCancel"
    >
      <a-form>
        <a-form-item label="字典标签" required>
          <a-input v-model:value="dataForm.dictLabel" placeholder="请输入字典标签" />
        </a-form-item>
        <a-form-item label="字典键值" required>
          <a-input v-model:value="dataForm.dictValue" placeholder="请输入字典键值" />
        </a-form-item>
        <a-form-item label="排序号">
          <a-input-number v-model:value="dataForm.orderNum" :min="0" class="lg-full-control" />
        </a-form-item>
        <a-form-item label="CSS类名">
          <a-input v-model:value="dataForm.cssClass" placeholder="如：text-danger bg-warning" />
        </a-form-item>
        <a-form-item label="列表样式">
          <a-input v-model:value="dataForm.listClass" placeholder="如：default primary success" />
        </a-form-item>
        <a-form-item label="状态">
          <a-select v-model:value="dataForm.status" class="dict-status-select">
            <a-select-option value="ENABLED">启用</a-select-option>
            <a-select-option value="DISABLED">禁用</a-select-option>
          </a-select>
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<style scoped>
/* 页面专属样式 — 左右分栏 + 类型列表交互 */
.dict-page {
  gap: var(--space-16);
}

.dict-page-head {
  align-items: center;
  justify-content: space-between;
  min-height: 0;
  padding: 0;
  margin-bottom: 0;
}

.dict-page-meta-row {
  display: flex;
  align-items: center;
  gap: 5em;
  min-width: 0;
}

.dict-breadcrumb {
  flex: none;
  font-size: 13px;
  line-height: 20px;
}

.dict-page-subtitle {
  min-width: 0;
  overflow: hidden;
  font-size: 13px;
  line-height: 20px;
  color: var(--text-secondary);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.dc-panel {
  display: flex;
  min-height: calc(100vh - 180px);
  overflow: hidden;
  background: var(--surface);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-soft);
  padding: 0;
}

.dc-left {
  width: 280px;
  flex-shrink: 0;
  border-right: 1px solid var(--border);
  display: flex;
  flex-direction: column;
  background: var(--surface);
}

.dc-left-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 58px;
  padding: 12px 16px;
  border-bottom: 1px solid var(--border);
}

.dc-left-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--text);
}

.dc-left-search {
  padding: 12px 16px;
  border-bottom: 1px solid var(--border-subtle);
}

.dc-left-list {
  flex: 1;
  overflow: auto;
  padding: 8px;
}

.dc-left-empty {
  padding: 32px 16px;
  text-align: center;
  color: var(--muted);
  font-size: 13px;
}

.dc-left-footer {
  padding: 10px 16px;
  font-size: 12px;
  color: var(--muted);
  border-top: 1px solid var(--border);
}

.dc-type-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 58px;
  padding: 8px 12px;
  border-radius: var(--radius-sm);
  cursor: pointer;
  transition: background 0.15s;
}

.dc-type-item:hover {
  background: var(--surface-subtle);
}

.dc-type-item--active {
  background: var(--primary-soft);
}

.dc-type-item--active:hover {
  background: var(--primary-soft);
}

.dc-type-info {
  display: flex;
  flex-direction: column;
  gap: 2px;
  overflow: hidden;
}

.dc-type-name {
  font-size: 14px;
  color: var(--text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.dc-type-code {
  font-size: 12px;
  color: var(--muted);
  font-family: var(--font-family);
  font-variant-numeric: tabular-nums;
}

.dc-type-actions {
  flex-shrink: 0;
  opacity: 0;
  transition: opacity 0.15s;
}

.dc-type-item:hover .dc-type-actions {
  opacity: 1;
}

.dc-type-item--active .dc-type-actions {
  opacity: 1;
}

.dc-right {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  background: var(--surface);
}

.dc-right-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 58px;
  padding: 12px 18px;
  border-bottom: 1px solid var(--border);
}

.dc-right-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--text);
}

.dc-right-empty {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
}

.dict-filter-input {
  width: 220px;
}

.dict-filter-status,
.dict-status-select {
  width: 120px;
}

.dc-right .lg-toolbar {
  padding: 12px 18px;
  border-bottom: 1px solid var(--border-subtle);
}

.dc-right .lg-table-wrap {
  flex: 1;
  min-height: 0;
}

.dc-right .lg-pagination {
  padding: 12px 18px;
}
</style>
