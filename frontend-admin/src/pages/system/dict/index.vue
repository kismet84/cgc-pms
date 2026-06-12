<script setup lang="ts">
import { ref, reactive, onMounted, computed } from 'vue'
import { message, Modal } from 'ant-design-vue'
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
      pageSize: 500,
      dictName: typeFilter.value || undefined,
    })
    typeList.value = res.records
    // 如果当前没有选中项，默认选中第一个
    if (typeList.value.length > 0 && !selectedTypeId.value) {
      selectedTypeId.value = typeList.value[0].id
      fetchDataList()
    }
  } catch {
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
      } catch {
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
  } catch {
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

const STATUS_LABEL: Record<string, string> = {
  ENABLED: '启用',
  DISABLED: '禁用',
}

const dataColumns = [
  { title: '字典标签', dataIndex: 'dictLabel', minWidth: 140 },
  { title: '字典键值', dataIndex: 'dictValue', width: 140 },
  { title: '排序', dataIndex: 'orderNum', width: 80, align: 'right' as const },
  { title: '样式类名', dataIndex: 'cssClass', width: 120 },
  { title: '状态', dataIndex: 'status', width: 80, key: 'status' },
  { title: '创建时间', dataIndex: 'createdAt', width: 170 },
  { title: '操作', dataIndex: 'ops', width: 140, fixed: 'right' as const, key: 'ops' },
]

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
    dataTableData.value = res.records
    dataTotal.value = res.total
  } catch {
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
      } catch {
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
  } catch {
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
  <div class="dict-page">
    <a-layout class="dict-layout">
      <!-- 左侧：字典类型列表 -->
      <a-layout-sider
        class="dict-sider"
        width="280"
        theme="light"
      >
        <div class="dict-sider-header">
          <span class="dict-sider-title">字典类型</span>
          <a-button type="primary" size="small" @click="handleAddType">新增</a-button>
        </div>

        <div class="dict-sider-search">
          <a-input-search
            v-model:value="typeFilter"
            placeholder="搜索字典名称"
            size="small"
            @search="handleTypeSearch"
          />
        </div>

        <div class="dict-sider-list" v-loading="typeLoading">
          <div v-if="typeList.length === 0 && !typeLoading" class="dict-sider-empty">
            暂无字典类型
          </div>
          <div
            v-for="item in typeList"
            :key="item.id"
            class="dict-type-item"
            :class="{ active: selectedTypeId === item.id }"
            @click="handleSelectType(item.id)"
          >
            <div class="dict-type-info">
              <span class="dict-type-name">{{ item.dictName }}</span>
              <span class="dict-type-code">{{ item.dictCode }}</span>
            </div>
            <div class="dict-type-actions">
              <a-button type="link" size="small" @click.stop="handleEditType(item)">编辑</a-button>
              <a-button type="link" size="small" danger @click.stop="handleDeleteType(item)">删除</a-button>
            </div>
          </div>
        </div>
      </a-layout-sider>

      <!-- 右侧：字典数据表格 -->
      <a-layout-content class="dict-content">
        <template v-if="selectedTypeId">
          <div class="dict-content-header">
            <span class="dict-content-title">{{ selectedTypeName || '字典数据' }}</span>
            <a-button type="primary" size="small" @click="handleAddData">新增数据</a-button>
          </div>

          <!-- 数据筛选 -->
          <div class="dict-card dict-data-filter">
            <div class="dict-filter-row">
              <div class="dict-field">
                <label>字典标签：</label>
                <a-input v-model:value="dataFilter.dictLabel" placeholder="请输入标签" style="width:160px" allow-clear />
              </div>
              <div class="dict-field">
                <label>状态：</label>
                <a-select v-model:value="dataFilter.status" placeholder="全部" allow-clear style="width:110px">
                  <a-select-option value="ENABLED">启用</a-select-option>
                  <a-select-option value="DISABLED">禁用</a-select-option>
                </a-select>
              </div>
              <div class="dict-filter-actions">
                <a-button type="primary" size="small" @click="handleDataSearch">查询</a-button>
                <a-button size="small" @click="handleDataReset">重置</a-button>
              </div>
            </div>
          </div>

          <!-- 数据表格 -->
          <div class="dict-card dict-table-wrap">
            <a-table
              :columns="dataColumns"
              :data-source="dataTableData"
              :loading="dataLoading"
              :pagination="false"
              row-key="id"
              size="small"
              :scroll="{ x: 900 }"
            >
              <template #bodyCell="{ column, record }">
                <template v-if="column.key === 'status'">
                  <a-tag :color="record.status === 'ENABLED' ? 'success' : 'default'">
                    {{ STATUS_LABEL[record.status] ?? record.status }}
                  </a-tag>
                </template>
                <template v-else-if="column.key === 'ops'">
                  <div class="dict-ops">
                    <a class="dict-link" @click="handleEditData(record)">编辑</a>
                    <a class="dict-link dict-link-danger" @click="handleDeleteData(record)">删除</a>
                  </div>
                </template>
              </template>
            </a-table>
          </div>

          <!-- 分页 -->
          <div class="dict-pagination">
            <span class="dict-total">共 {{ dataTotal }} 条</span>
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
        <div v-else class="dict-content-placeholder">
          <div class="dict-placeholder-icon">📋</div>
          <div class="dict-placeholder-text">请在左侧选择一个字典类型</div>
        </div>
      </a-layout-content>
    </a-layout>

    <!-- 字典类型弹窗 -->
    <a-modal
      v-model:open="typeModalVisible"
      :title="typeModalTitle"
      :confirm-loading="typeFormLoading"
      width="480px"
      @ok="handleTypeSubmit"
      @cancel="handleTypeCancel"
    >
      <a-form :label-col="{ span: 6 }" :wrapper-col="{ span: 16 }">
        <a-form-item label="字典编码" required>
          <a-input v-model:value="typeForm.dictCode" placeholder="请输入字典编码" :disabled="typeIsEdit" />
        </a-form-item>
        <a-form-item label="字典名称" required>
          <a-input v-model:value="typeForm.dictName" placeholder="请输入字典名称" />
        </a-form-item>
        <a-form-item label="状态">
          <a-select v-model:value="typeForm.status" style="width:120px">
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
      width="560px"
      @ok="handleDataSubmit"
      @cancel="handleDataCancel"
    >
      <a-form :label-col="{ span: 6 }" :wrapper-col="{ span: 16 }">
        <a-form-item label="字典标签" required>
          <a-input v-model:value="dataForm.dictLabel" placeholder="请输入字典标签" />
        </a-form-item>
        <a-form-item label="字典键值" required>
          <a-input v-model:value="dataForm.dictValue" placeholder="请输入字典键值" />
        </a-form-item>
        <a-form-item label="排序号">
          <a-input-number v-model:value="dataForm.orderNum" :min="0" style="width:100%" />
        </a-form-item>
        <a-form-item label="CSS类名">
          <a-input v-model:value="dataForm.cssClass" placeholder="如：text-danger bg-warning" />
        </a-form-item>
        <a-form-item label="列表样式">
          <a-input v-model:value="dataForm.listClass" placeholder="如：default primary success" />
        </a-form-item>
        <a-form-item label="状态">
          <a-select v-model:value="dataForm.status" style="width:120px">
            <a-select-option value="ENABLED">启用</a-select-option>
            <a-select-option value="DISABLED">禁用</a-select-option>
          </a-select>
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<style scoped>
.dict-page {
  background: #f6f8fc;
  min-height: calc(100vh - 56px - 36px);
}

.dict-layout {
  background: transparent;
  gap: 14px;
  height: 100%;
}

/* ---- 左侧字典类型 ---- */
.dict-sider {
  background: #fff !important;
  border: 1px solid #e5eaf3;
  border-radius: 10px;
  box-shadow: 0 10px 30px rgba(17, 24, 39, 0.05);
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.dict-sider-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 16px 12px;
  border-bottom: 1px solid #f0f2f5;
}

.dict-sider-title {
  font-size: 15px;
  font-weight: 600;
  color: #111827;
}

.dict-sider-search {
  padding: 10px 16px;
}

.dict-sider-list {
  flex: 1;
  overflow-y: auto;
  padding: 4px 0;
}

.dict-sider-empty {
  padding: 32px 16px;
  text-align: center;
  color: #9ca3af;
  font-size: 13px;
}

.dict-type-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 16px;
  cursor: pointer;
  border-left: 3px solid transparent;
  transition: background 0.15s, border-color 0.15s;
  gap: 8px;
}

.dict-type-item:hover {
  background: #f6f8fc;
}

.dict-type-item.active {
  background: #e6f4ff;
  border-left-color: #1677ff;
}

.dict-type-info {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.dict-type-name {
  font-size: 14px;
  font-weight: 500;
  color: #111827;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.dict-type-code {
  font-size: 12px;
  color: #6b7280;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.dict-type-actions {
  display: none;
  gap: 2px;
  flex-shrink: 0;
}

.dict-type-item:hover .dict-type-actions {
  display: flex;
}

/* ---- 右侧字典数据 ---- */
.dict-content {
  background: transparent;
  min-height: 0;
}

.dict-content-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 0 14px;
}

.dict-content-title {
  font-size: 16px;
  font-weight: 600;
  color: #111827;
}

.dict-content-placeholder {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 80px 20px;
  background: #fff;
  border: 1px solid #e5eaf3;
  border-radius: 10px;
  box-shadow: 0 10px 30px rgba(17, 24, 39, 0.05);
}

.dict-placeholder-icon {
  font-size: 48px;
  margin-bottom: 16px;
  opacity: 0.6;
}

.dict-placeholder-text {
  font-size: 14px;
  color: #9ca3af;
}

.dict-card {
  background: #fff;
  border: 1px solid #e5eaf3;
  border-radius: 10px;
  box-shadow: 0 10px 30px rgba(17, 24, 39, 0.05);
}

.dict-data-filter {
  padding: 14px 18px;
  margin-bottom: 14px;
}

.dict-filter-row {
  display: flex;
  flex-wrap: wrap;
  gap: 10px 20px;
  align-items: center;
}

.dict-field {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  white-space: nowrap;
}

.dict-field label {
  color: #374151;
}

.dict-filter-actions {
  display: flex;
  gap: 8px;
  margin-left: auto;
}

.dict-table-wrap {
  overflow: hidden;
  margin-bottom: 0;
}

.dict-link {
  color: #1677ff;
  font-weight: 500;
  cursor: pointer;
  text-decoration: none;
}

.dict-link-danger {
  color: #ff4d4f;
}

.dict-ops {
  display: flex;
  gap: 10px;
}

.dict-pagination {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 12px;
  padding: 12px 0 0;
}

.dict-total {
  font-size: 13px;
  color: #4b5563;
}
</style>
