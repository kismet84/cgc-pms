<script setup lang="ts">
import { ref, reactive, onMounted, computed } from 'vue'
import { message, Modal } from 'ant-design-vue'
import { SearchOutlined, ReloadOutlined } from '@ant-design/icons-vue'
import {
  getPurchaseRequestList,
  createPurchaseRequest,
  updatePurchaseRequest,
  deletePurchaseRequest,
  getPurchaseRequestItems,
  savePurchaseRequestItems,
  submitPurchaseRequest,
} from '@/api/modules/inventory'
import { useReferenceStore } from '@/stores/reference'
import type { PurchaseRequestVO, PurchaseRequestItemVO } from '@/types/inventory'
import { getContractLedger } from '@/api/modules/contract'
import type { ContractVO } from '@/types/contract'
import ApprovalStatusTag from '@/components/ApprovalStatusTag.vue'

const filter = reactive({
  projectId: undefined as string | undefined,
  approvalStatus: undefined as string | undefined,
  status: undefined as string | undefined,
  requestCode: '',
})

const loading = ref(false)
const tableData = ref<PurchaseRequestVO[]>([])
const total = ref(0)
const pageNo = ref(1)
const pageSize = ref(20)

const referenceStore = useReferenceStore()
const projectList = computed(() => referenceStore.projects ?? [])
const contractList = ref<ContractVO[]>([])
const materialList = computed(() => referenceStore.materials ?? [])

const modalVisible = ref(false)
const modalTitle = ref('新建采购申请')
const editingId = ref<string | null>(null)
const submitting = ref(false)
const modalDirty = ref(false)
const formData = reactive<Partial<PurchaseRequestVO>>({
  projectId: undefined,
  contractId: undefined,
  remark: '',
})

// Line items for the modal
const itemList = ref<(Partial<PurchaseRequestItemVO> & { key: number })[]>([])
const keySeq = ref(0)

const STATUS_LABEL: Record<string, string> = {
  DRAFT: '草稿',
  CONVERTED: '已转PO',
}
const STATUS_COLOR: Record<string, string> = {
  DRAFT: 'default',
  CONVERTED: 'cyan',
}

const itemColumns = [
  {
    title: '物料',
    dataIndex: 'material',
    key: 'material',
    width: 240,
    customHeaderCell: () => ({
      style: { width: '240px', minWidth: '240px', maxWidth: '240px' },
    }),
    customCell: () => ({
      style: { width: '240px', minWidth: '240px', maxWidth: '240px' },
    }),
  },
  { title: '单位', dataIndex: 'unit', key: 'unit', width: 120 },
  { title: '数量', dataIndex: 'quantity', key: 'quantity', width: 120 },
  { title: '计划日期', dataIndex: 'plannedDate', key: 'plannedDate', width: 160 },
  { title: '备注', dataIndex: 'remark', key: 'remark', width: 160 },
  { title: '操作', key: 'action', width: 100 },
]

const filterOption = (input: string, option: any) =>
  option.label?.toLowerCase().includes(input.toLowerCase())

const gridColumns = computed(() => [
  { field: 'requestCode', title: '申请编号', width: 140, ellipsis: true },
  { field: 'projectName', title: '所属项目', width: 130, ellipsis: true },
  { field: 'contractName', title: '关联合同', width: 130, ellipsis: true },
  { field: 'approvalStatus', title: '审批状态', width: 90, slots: { default: 'approvalStatus' } },
  { field: 'status', title: '业务状态', width: 80, slots: { default: 'status' } },
  { field: 'createdBy', title: '创建人', width: 90 },
  { field: 'createdTime', title: '创建时间', width: 140 },
  { title: '操作', width: 190, slots: { default: 'ops' } },
])

async function fetchData() {
  loading.value = true
  try {
    const res = await getPurchaseRequestList({
      pageNum: pageNo.value,
      pageSize: pageSize.value,
      projectId: filter.projectId,
      approvalStatus: filter.approvalStatus,
      status: filter.status,
      requestCode: filter.requestCode || undefined,
    })
    tableData.value = res.records
    total.value = res.total
  } catch (e: unknown) {
    console.error(e)
    tableData.value = []
    total.value = 0
    message.error('加载采购申请列表失败，请稍后重试')
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
  filter.approvalStatus = undefined
  filter.status = undefined
  filter.requestCode = ''
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
  modalTitle.value = '新建采购申请'
  editingId.value = null
  Object.assign(formData, {
    projectId: undefined,
    contractId: undefined,
    remark: '',
  })
  itemList.value = []
  keySeq.value = 0
  contractList.value = []
  modalDirty.value = false
  modalVisible.value = true
}

async function handleEdit(record: PurchaseRequestVO) {
  modalTitle.value = '编辑采购申请'
  editingId.value = record.id
  Object.assign(formData, {
    projectId: record.projectId,
    contractId: record.contractId,
    remark: record.remark,
  })
  itemList.value = []
  keySeq.value = 0
  await loadContractsByProject(record.projectId)
  // Load existing items
  try {
    const items = await getPurchaseRequestItems(record.id)
    itemList.value = items.map((item) => ({
      ...item,
      key: keySeq.value++,
    }))
  } catch (e: unknown) {
    console.error(e)
    message.error('加载明细失败')
    itemList.value = []
  }
  modalDirty.value = false
  modalVisible.value = true
}

async function loadContractsByProject(projectId?: string) {
  if (!projectId) {
    contractList.value = []
    return
  }
  try {
    const res = await getContractLedger({
      projectId,
      contractType: 'PURCHASE',
      pageNo: 1,
      pageSize: 200,
    })
    contractList.value = res.records
  } catch (e: unknown) {
    console.error(e)
    contractList.value = []
  }
}

async function handleProjectChange(projectId?: string) {
  formData.projectId = projectId
  formData.contractId = undefined
  modalDirty.value = true
  await loadContractsByProject(projectId)
}

function handleDelete(record: PurchaseRequestVO) {
  Modal.confirm({
    title: '确认删除',
    content: `确定要删除采购申请"${record.requestCode}"吗？`,
    okText: '确定',
    cancelText: '取消',
    onOk: async () => {
      try {
        await deletePurchaseRequest(record.id)
        message.success('删除成功')
        fetchData()
      } catch (e: unknown) {
        console.error(e)
        message.error('删除失败，请稍后重试')
      }
    },
  })
}

function handleSubmit(record: PurchaseRequestVO) {
  Modal.confirm({
    title: '确认提交审批',
    content: `确定要提交采购申请"${record.requestCode}"进行审批吗？提交后不可编辑。`,
    okText: '确定提交',
    cancelText: '取消',
    onOk: async () => {
      try {
        await submitPurchaseRequest(record.id)
        message.success('已提交审批')
        fetchData()
      } catch (e: unknown) {
        console.error(e)
        message.error('提交失败，请稍后重试')
      }
    },
  })
}

// --- Line items management ---
function handleAddItem() {
  modalDirty.value = true
  itemList.value.push({
    key: keySeq.value++,
    materialId: '',
    materialName: '',
    quantity: '0',
    unit: '',
    plannedDate: undefined,
    remark: '',
  })
}

function handleRemoveItem(key: number) {
  modalDirty.value = true
  const idx = itemList.value.findIndex((i) => i.key === key)
  if (idx !== -1) {
    itemList.value.splice(idx, 1)
  }
}

function handleMaterialClear(key: number) {
  modalDirty.value = true
  const item = itemList.value.find((i) => i.key === key)
  if (!item) return
  item.materialId = ''
  item.materialName = ''
  item.unit = ''
}

function handleMaterialChange(key: number, materialId: string | undefined) {
  modalDirty.value = true
  const item = itemList.value.find((i) => i.key === key)
  if (!item) return
  if (!materialId) {
    item.materialId = ''
    item.materialName = ''
    item.unit = ''
    return
  }
  const material = materialList.value.find((m) => m.id === materialId)
  if (material) {
    item.materialId = material.id
    item.materialName = material.materialName
    item.unit = material.unit || ''
  }
}

const itemsCount = computed(() => itemList.value.length)

async function handleModalOk() {
  if (submitting.value) return

  // --- validation ---
  if (!formData.projectId) {
    message.warning('请选择项目')
    return
  }
  if (itemList.value.length < 1) {
    message.warning('请至少添加一个物料明细')
    return
  }
  for (const item of itemList.value) {
    if (!item.materialId && !item.materialName) {
      message.warning('请为所有明细选择物料或输入物料名称')
      return
    }
    if (!item.quantity || Number(item.quantity) <= 0) {
      message.warning('物料数量必须大于 0')
      return
    }
  }

  submitting.value = true
  let requestId = ''
  try {
    if (editingId.value) {
      await updatePurchaseRequest(editingId.value, formData)
      requestId = editingId.value
    } else {
      requestId = await createPurchaseRequest(formData)
    }

    // Save line items
    if (itemList.value.length > 0) {
      const items = itemList.value.map((item) => ({
        ...item,
        requestId: requestId,
      }))
      await savePurchaseRequestItems(requestId, items)
    }

    message.success(editingId.value ? '更新成功' : '创建成功')
    modalVisible.value = false
    fetchData()
  } catch (e: unknown) {
    console.error(e)
    // Clean up orphaned PR when creating new and header saved but items failed
    if (!editingId.value && requestId) {
      try {
        await deletePurchaseRequest(requestId)
      } catch {
        // best-effort cleanup
      }
    }
    message.error('操作失败，请稍后重试')
  } finally {
    submitting.value = false
  }
}

function handleModalCancel() {
  if (submitting.value) return
  if (modalDirty.value) {
    Modal.confirm({
      title: '放弃编辑？',
      content: '当前表单有未保存的修改，确定关闭吗？',
      okText: '确定关闭',
      okType: 'danger',
      cancelText: '继续编辑',
      onOk: () => {
        modalVisible.value = false
      },
    })
    return
  }
  modalVisible.value = false
}

function getPopupContainer() {
  return document.body
}

const kpiReqTotal = computed(() => tableData.value.length)
const kpiReqPending = computed(
  () =>
    tableData.value.filter((r) => r.approvalStatus === 'DRAFT' || r.approvalStatus === 'APPROVING')
      .length,
)

onMounted(() => {
  referenceStore.fetchProjects()
  referenceStore.fetchMaterials()
  fetchData()
})
</script>

<template>
  <div class="lg-page app-page">
    <div class="lg-page-head">
      <div>
        <a-breadcrumb class="cl-breadcrumb">
          <a-breadcrumb-item>库存管理</a-breadcrumb-item>
          <a-breadcrumb-item>采购申请</a-breadcrumb-item>
        </a-breadcrumb>
      </div>
    </div>

    <!-- 搜索栏 -->
    <div class="lg-search-bar">
      <a-select
        v-model:value="filter.projectId"
        placeholder="全部项目"
        allow-clear
        style="width: 180px"
        show-search
        :filter-option="filterOption"
      >
        <a-select-option v-for="p in projectList" :key="p.id" :value="p.id">
          {{ p.projectName }}
        </a-select-option>
      </a-select>
      <a-select
        v-model:value="filter.approvalStatus"
        placeholder="全部审批状态"
        allow-clear
        style="width: 130px"
      >
        <a-select-option value="DRAFT">草稿</a-select-option>
        <a-select-option value="APPROVING">审批中</a-select-option>
        <a-select-option value="APPROVED">已通过</a-select-option>
        <a-select-option value="REJECTED">已驳回</a-select-option>
        <a-select-option value="WITHDRAWN">已撤回</a-select-option>
      </a-select>
      <a-select
        v-model:value="filter.status"
        placeholder="全部业务状态"
        allow-clear
        style="width: 130px"
      >
        <a-select-option value="DRAFT">草稿</a-select-option>
        <a-select-option value="CONVERTED">已转PO</a-select-option>
      </a-select>
      <a-input
        v-model:value="filter.requestCode"
        placeholder="搜索申请编号"
        style="width: 170px"
        allow-clear
        @press-enter="handleSearch"
      />
      <a-button type="primary" @click="handleSearch">
        <template #icon><SearchOutlined /></template>
        查询
      </a-button>
      <a-button @click="handleReset">
        <template #icon><ReloadOutlined /></template>
        重置
      </a-button>
    </div>

    <!-- KPI 横条 -->
    <div class="lg-kpi-strip">
      <div class="lg-kpi-card">
        <span class="lg-kpi-card-label">申请数</span>
        <span class="lg-kpi-card-value">{{ kpiReqTotal }} <small>条</small></span>
        <span class="lg-kpi-card-bar"><span style="width:100%;background:var(--kpi-total)"></span></span>
      </div>
      <div class="lg-kpi-card is-warn">
        <span class="lg-kpi-card-label">待审批</span>
        <span class="lg-kpi-card-value">{{ kpiReqPending }} <small>条</small></span>
        <span class="lg-kpi-card-bar"><span style="width:100%;background:#f59e0b"></span></span>
      </div>
    </div>

    <!-- 工具栏 -->
    <div class="lg-toolbar">
      <div class="lg-toolbar-left">
        <a-button type="primary" @click="handleAdd">新建申请</a-button>
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
        max-height="480"
      >
        <template #approvalStatus="{ row }">
          <ApprovalStatusTag :status="row.approvalStatus" />
        </template>
        <template #status="{ row }">
          <a-tag :color="STATUS_COLOR[row.status]">
            {{ STATUS_LABEL[row.status] ?? row.status }}
          </a-tag>
        </template>
        <template #ops="{ row }">
          <div class="lg-ops">
            <a class="lg-link" @click="handleEdit(row)">编辑</a>
            <a
              v-if="row.approvalStatus === 'DRAFT'"
              class="lg-link"
              style="color: #1677ff"
              @click="handleSubmit(row)"
            >提交审批</a>
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
      :width="900"
      :confirm-loading="submitting"
      destroy-on-close
      @ok="handleModalOk"
      @cancel="handleModalCancel"
    >
      <!-- Header Form -->
      <a-form :label-col="{ span: 6 }" :wrapper-col="{ span: 16 }" style="margin-bottom: 8px">
        <a-form-item label="项目" required>
          <a-select
            v-model:value="formData.projectId"
            placeholder="请选择项目"
            @change="handleProjectChange"
          >
            <a-select-option v-for="p in projectList" :key="p.id" :value="p.id">
              {{ p.projectName }}
            </a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="关联合同">
          <a-select
            v-model:value="formData.contractId"
            placeholder="选择采购合同"
            allow-clear
            show-search
            :filter-option="filterOption"
            @change="modalDirty = true"
          >
            <a-select-option v-for="c in contractList" :key="c.id" :value="c.id">
              {{ c.contractName }}
            </a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="备注">
          <a-textarea
            v-model:value="formData.remark"
            :rows="2"
            placeholder="请输入备注"
            @change="modalDirty = true"
          />
        </a-form-item>
      </a-form>

      <!-- Line Items Section -->
      <div class="pr-items-section">
        <div class="pr-items-header">
          <span class="pr-items-title">
            申请明细
            <span class="pr-items-count"> {{ itemsCount }} 项 </span>
          </span>
          <a-button type="dashed" size="small" @click="handleAddItem">+ 添加物料</a-button>
        </div>

        <a-table
          :data-source="itemList"
          :pagination="false"
          table-layout="fixed"
          :columns="itemColumns"
          row-key="key"
          size="small"
          :scroll="{ y: 250 }"
        >
          <template #bodyCell="{ column, record: item }">
            <template v-if="column.key === 'material'">
              <div style="display: flex; gap: 4px">
                <a-select
                  :value="item.materialId"
                  placeholder="选择已有物料"
                  allow-clear
                  :style="{ width: item.materialId ? '100%' : '50%', flexShrink: 0 }"
                  show-search
                  :filter-option="filterOption"
                  @change="(val: string) => handleMaterialChange(item.key, val)"
                  @clear="handleMaterialClear(item.key)"
                >
                  <a-select-option v-for="m in materialList" :key="m.id" :value="m.id">
                    {{ m.materialName }}
                  </a-select-option>
                </a-select>
                <a-input
                  v-if="!item.materialId"
                  v-model:value="item.materialName"
                  placeholder="自定义物料"
                  size="small"
                  style="flex: 1"
                  @change="modalDirty = true"
                />
              </div>
            </template>
            <template v-else-if="column.key === 'unit'">
              <a-input
                v-model:value="item.unit"
                placeholder="单位"
                size="small"
                style="width: 100%"
                @change="modalDirty = true"
              />
            </template>
            <template v-else-if="column.key === 'quantity'">
              <a-input-number
                v-model:value="item.quantity"
                :min="0"
                :precision="4"
                style="width: 100%"
                @change="modalDirty = true"
              />
            </template>
            <template v-else-if="column.key === 'plannedDate'">
              <a-date-picker
                v-model:value="item.plannedDate"
                value-format="YYYY-MM-DD"
                style="width: 100%"
                size="small"
                :get-popup-container="getPopupContainer"
                @change="modalDirty = true"
              />
            </template>
            <template v-else-if="column.key === 'remark'">
              <a-input
                v-model:value="item.remark"
                placeholder="备注"
                size="small"
                @change="modalDirty = true"
              />
            </template>
            <template v-else-if="column.key === 'action'">
              <a-button type="link" size="small" danger @click="handleRemoveItem(item.key)">
                删除
              </a-button>
            </template>
          </template>
        </a-table>
      </div>
    </a-modal>
  </div>
</template>

<style scoped>
.pr-items-section {
  border-top: 1px solid #f0f0f0;
  padding-top: 12px;
  margin-top: 4px;
}

.pr-items-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
}

.pr-items-title {
  font-weight: 600;
  font-size: 14px;
}

.pr-items-count {
  color: #9ca3af;
  font-weight: 400;
  font-size: 12px;
  margin-left: 6px;
}

:deep(.pr-items-section .ant-table-thead > tr > th:first-child),
:deep(.pr-items-section .ant-table-tbody > tr > td:first-child) {
  width: 240px !important;
  min-width: 240px !important;
  max-width: 240px !important;
}

:deep(.pr-items-section .ant-table colgroup col:first-child) {
  width: 240px !important;
  min-width: 240px !important;
  max-width: 240px !important;
}
</style>
