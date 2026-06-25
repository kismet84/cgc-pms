<script setup lang="ts">
import { ref, reactive, onMounted, computed, watch } from 'vue'
import { message, Modal } from 'ant-design-vue'
import { SearchOutlined, PlusOutlined, ReloadOutlined, MoreOutlined } from '@ant-design/icons-vue'
import { useReferenceStore } from '@/stores/reference'
import {
  getOrderList,
  createOrder,
  updateOrder,
  deleteOrder,
  getOrderItems,
  saveOrderItems,
  submitOrderForApproval,
} from '@/api/modules/purchase'
import type { MatPurchaseOrderVO, MatPurchaseOrderItemVO } from '@/types/purchase'
import type { SelectOption } from '@/types/ui'
import { useColumnSettings } from '@/composables/useColumnSettings'
import { ColumnSettingsButton } from '@/components/list-page'

const filter = reactive({
  projectId: undefined as string | undefined,
  contractId: undefined as string | undefined,
  partnerId: undefined as string | undefined,
  orderStatus: undefined as string | undefined,
  orderType: undefined as string | undefined,
  keyword: '',
  orderCode: '',
})

const loading = ref(false)
const tableData = ref<MatPurchaseOrderVO[]>([])
const total = ref(0)
const pageNo = ref(1)
const pageSize = ref(20)

const referenceStore = useReferenceStore()
const projectList = computed(() => referenceStore.projects ?? [])
const contractList = computed(() => referenceStore.contracts ?? [])
const materialList = computed(() => referenceStore.materials ?? [])

const modalVisible = ref(false)
const modalTitle = ref('新建采购订单')
const editingId = ref<string | null>(null)
const formData = reactive<Partial<MatPurchaseOrderVO>>({
  projectId: undefined,
  contractId: undefined,
  partnerId: undefined,
  orderType: undefined,
  orderDate: undefined,
  deliveryDate: undefined,
  remark: '',
})
const formPartnerName = computed(
  () => contractList.value?.find((c) => c.id === formData.contractId)?.partyBName ?? '',
)
function onContractChange(contractId: string) {
  const c = contractList.value?.find((ct) => ct.id === contractId)
  formData.partnerId = c?.partyBId
}
watch(
  () => formData.contractId,
  (val) => {
    if (!val) formData.partnerId = undefined
  },
)

// Line items for the modal
const itemList = ref<(Partial<MatPurchaseOrderItemVO> & { key: number })[]>([])
let itemKeyCounter = 0

const ORDER_TYPE_LABEL: Record<string, string> = {
  MATERIAL: '材料采购',
  EQUIPMENT: '设备采购',
  SERVICE: '服务采购',
  OTHER: '其他',
}
const ORDER_TYPE_COLOR: Record<string, string> = {
  MATERIAL: 'blue',
  EQUIPMENT: 'cyan',
  SERVICE: 'purple',
  OTHER: 'default',
}
const ORDER_STATUS_LABEL: Record<string, string> = {
  DRAFT: '草稿',
  APPROVING: '审批中',
  PERFORMING: '履行中',
  COMPLETED: '已完成',
  CANCELLED: '已取消',
}
const ORDER_STATUS_COLOR: Record<string, string> = {
  DRAFT: 'default',
  APPROVING: 'processing',
  PERFORMING: 'blue',
  COMPLETED: 'success',
  CANCELLED: 'error',
}

const gridColumns = computed(() => [
  { field: 'orderCode', title: '订单编号', minWidth: 150, ellipsis: true },
  { field: 'orderType', title: '订单类型', width: 108, slots: { default: 'orderType' } },
  { field: 'projectName', title: '项目名称', minWidth: 150, ellipsis: true },
  { field: 'contractName', title: '合同名称', minWidth: 150, ellipsis: true },
  { field: 'partnerName', title: '供应商', minWidth: 140, ellipsis: true },
  {
    field: 'totalAmount',
    title: '总金额',
    width: 128,
    align: 'right' as const,
    slots: { default: 'totalAmount' },
  },
  { field: 'deliveryDate', title: '交货日期', width: 112 },
  { field: 'orderStatus', title: '订单状态', width: 108, slots: { default: 'orderStatus' } },
  { field: 'approvalStatus', title: '审批状态', width: 108, slots: { default: 'approvalStatus' } },
  { title: '操作', width: 76, slots: { default: 'action' } },
])

const {
  visibleColumns: visibleGridColumns,
  columnSettings,
  colVisible,
  toggleCol,
} = useColumnSettings('purchase_order_cols', gridColumns)

async function fetchData() {
  loading.value = true
  try {
    const res = await getOrderList({
      pageNum: pageNo.value,
      pageSize: pageSize.value,
      projectId: filter.projectId,
      contractId: filter.contractId,
      partnerId: filter.partnerId,
      orderStatus: filter.orderStatus,
      orderType: filter.orderType,
      orderCode: filter.keyword || filter.orderCode || undefined,
    })
    tableData.value = res.records
    total.value = res.total
  } catch (e: unknown) {
    console.error(e)
    tableData.value = []
    total.value = 0
    message.error('加载采购订单列表失败，请稍后重试')
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
  filter.contractId = undefined
  filter.partnerId = undefined
  filter.orderStatus = undefined
  filter.orderType = undefined
  filter.orderCode = ''
  filter.keyword = ''
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
  modalTitle.value = '新建采购订单'
  editingId.value = null
  Object.assign(formData, {
    projectId: undefined,
    contractId: undefined,
    partnerId: undefined,
    orderType: undefined,
    orderDate: undefined,
    deliveryDate: undefined,
    remark: '',
  })
  itemList.value = []
  itemKeyCounter = 0
  modalVisible.value = true
}

async function handleEdit(record: MatPurchaseOrderVO) {
  modalTitle.value = '编辑采购订单'
  editingId.value = record.id
  Object.assign(formData, {
    projectId: record.projectId,
    contractId: record.contractId,
    partnerId: record.partnerId,
    orderType: record.orderType,
    orderDate: record.orderDate,
    deliveryDate: record.deliveryDate,
    remark: record.remark,
  })
  itemList.value = []
  itemKeyCounter = 0
  // Load existing items
  try {
    const items = await getOrderItems(record.id)
    itemList.value = items.map((item) => ({
      ...item,
      key: itemKeyCounter++,
    }))
  } catch (e: unknown) {
    console.error(e)
    message.error('加载明细失败')
    itemList.value = []
  }
  modalVisible.value = true
}

function handleDelete(record: MatPurchaseOrderVO) {
  Modal.confirm({
    title: '确认删除',
    content: `确定要删除采购订单"${record.orderCode}"吗？`,
    okText: '确定',
    cancelText: '取消',
    onOk: async () => {
      try {
        await deleteOrder(record.id)
        message.success('删除成功')
        fetchData()
      } catch (e: unknown) {
        console.error(e)
        message.error('删除失败，请稍后重试')
      }
    },
  })
}

function handleSubmitApproval(record: MatPurchaseOrderVO) {
  Modal.confirm({
    title: '确认提交',
    content: `确定要提交采购订单"${record.orderCode}"吗？提交后将进入审批流程`,
    okText: '确定',
    cancelText: '取消',
    onOk: async () => {
      try {
        await submitOrderForApproval(record.id)
        message.success('提交审批成功')
        fetchData()
      } catch (e: unknown) {
        console.error(e)
        message.error('提交审批失败')
      }
    },
  })
}

// --- Line items management ---
function handleAddItem() {
  itemList.value.push({
    key: itemKeyCounter++,
    materialId: undefined,
    materialName: '',
    specification: '',
    unit: '',
    quantity: '0',
    unitPrice: '0',
    amount: '0',
  })
}

function handleRemoveItem(index: number) {
  itemList.value.splice(index, 1)
}

function handleMaterialChange(index: number, materialId: string | undefined) {
  if (!materialId) {
    const item = itemList.value[index]
    item.materialName = ''
    item.specification = ''
    item.unit = ''
    return
  }
  const material = materialList.value.find((m) => m.id === materialId)
  if (material) {
    const item = itemList.value[index]
    item.materialId = material.id
    item.materialName = material.materialName
    item.specification = material.specification || ''
    item.unit = material.unit || ''
  }
}

function handleItemQtyChange(index: number) {
  const item = itemList.value[index]
  const qty = parseFloat(item.quantity || '0')
  const price = parseFloat(item.unitPrice || '0')
  item.amount = (qty * price).toFixed(2)
}

function handleItemPriceChange(index: number) {
  const item = itemList.value[index]
  const qty = parseFloat(item.quantity || '0')
  const price = parseFloat(item.unitPrice || '0')
  item.amount = (qty * price).toFixed(2)
}

const itemsTotalAmount = computed(() => {
  let total = 0
  for (const item of itemList.value) {
    total += parseFloat(item.amount || '0')
  }
  return total.toFixed(2)
})

async function handleModalOk() {
  if (!formData.projectId) {
    message.warning('请选择项目')
    return
  }

  try {
    let orderId: string
    if (editingId.value) {
      await updateOrder(editingId.value, formData)
      orderId = editingId.value
      message.success('更新成功')
    } else {
      const result = await createOrder(formData)
      orderId = result
      message.success('创建成功')
    }

    // Save line items
    if (itemList.value.length > 0) {
      const items = itemList.value.map((item) => ({
        ...item,
        orderId: orderId,
      }))
      await saveOrderItems(orderId, items)
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

const kpiOrderTotal = computed(() => tableData.value.length)
const kpiOrderPending = computed(
  () => tableData.value.filter((r) => r.orderStatus === 'DRAFT').length,
)
const kpiOrderedAmount = computed(() =>
  tableData.value.reduce((s, r) => s + (parseFloat(r.totalAmount) || 0), 0),
)
const kpiUnreceived = computed(() =>
  tableData.value
    .filter((r) => r.orderStatus !== 'COMPLETED' && r.orderStatus !== 'CANCELLED')
    .reduce((s, r) => s + (parseFloat(r.totalAmount) || 0), 0),
)
const orderStatusBreakdown = computed(() => {
  const m: Record<string, number> = {}
  tableData.value.forEach((r) => {
    m[ORDER_STATUS_LABEL[r.orderStatus] ?? r.orderStatus] =
      (m[ORDER_STATUS_LABEL[r.orderStatus] ?? r.orderStatus] || 0) + 1
  })
  return Object.entries(m).map(([k, v]) => ({ label: k, count: v }))
})

onMounted(() => {
  referenceStore.fetchProjects()
  referenceStore.fetchContracts({ contractType: 'PURCHASE' })
  referenceStore.fetchPartners({ partnerType: 'SUPPLIER' })
  referenceStore.fetchMaterials({ status: 'ENABLE' })
  fetchData()
})
</script>

<template>
  <div class="lg-list-page lg-page app-page">
    <div class="lg-page-head">
      <div>
        <a-breadcrumb style="margin-bottom: 5px; font-size: 13px">
          <a-breadcrumb-item>采购管理</a-breadcrumb-item>
          <a-breadcrumb-item>采购订单</a-breadcrumb-item>
        </a-breadcrumb>
      </div>
    </div>

    <!-- 搜索栏 -->
    <div class="lg-search-bar">
      <a-input
        v-model:value="filter.keyword"
        placeholder="搜索订单编号、名称…"
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
        <!-- KPI 横条 -->
        <div class="lg-kpi-strip">
          <div class="lg-kpi-card">
            <span class="lg-kpi-card-label">采购订单数</span>
            <span class="lg-kpi-card-value">{{ kpiOrderTotal }} <small>条</small></span>
            <span class="lg-kpi-card-bar"
              ><span style="width: 100%; background: var(--kpi-total)"></span
            ></span>
          </div>
          <div class="lg-kpi-card">
            <span class="lg-kpi-card-label">待审批</span>
            <span class="lg-kpi-card-value">{{ kpiOrderPending }} <small>条</small></span>
            <span class="lg-kpi-card-bar"
              ><span style="width: 100%; background: #f59e0b"></span
            ></span>
          </div>
          <div class="lg-kpi-card">
            <span class="lg-kpi-card-label">已下单金额</span>
            <span class="lg-kpi-card-value"
              >{{ kpiOrderedAmount.toLocaleString() }} <small>元</small></span
            >
            <span class="lg-kpi-card-bar"
              ><span style="width: 100%; background: var(--kpi-amount)"></span
            ></span>
          </div>
          <div class="lg-kpi-card is-warn">
            <span class="lg-kpi-card-label">未入库金额</span>
            <span class="lg-kpi-card-value" style="color: #f59e0b"
              >{{ kpiUnreceived.toLocaleString() }} <small>元</small></span
            >
            <span class="lg-kpi-card-bar"
              ><span style="width: 100%; background: #f59e0b"></span
            ></span>
          </div>
        </div>

        <main class="lg-list-table-panel">
          <!-- 工具栏 -->
          <div class="lg-toolbar">
            <div class="lg-toolbar-left">
              <a-button type="primary" @click="handleAdd">
                <template #icon><PlusOutlined /></template>
                新建订单
              </a-button>
              <a-button @click="fetchData">
                <template #icon><ReloadOutlined /></template>
              </a-button>
            </div>
            <div class="lg-toolbar-right">
              <ColumnSettingsButton
                :columns="columnSettings"
                :visible="colVisible"
                @toggle="toggleCol"
              />
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
              :columns="visibleGridColumns"
              :loading="loading"
              :column-config="{ resizable: true }"
              stripe
              border="inner"
              size="small"
            >
              <template #orderType="{ row }">
                <a-tag :color="ORDER_TYPE_COLOR[row.orderType]">
                  {{ ORDER_TYPE_LABEL[row.orderType] ?? row.orderType }}
                </a-tag>
              </template>
              <template #totalAmount="{ row }">
                <span v-if="row.totalAmount" class="lg-money">{{
                  Number(row.totalAmount).toLocaleString('zh-CN', { minimumFractionDigits: 2 })
                }}</span>
                <span v-else :style="{ color: 'var(--muted)' }">-</span>
              </template>
              <template #orderStatus="{ row }">
                <a-tag :color="ORDER_STATUS_COLOR[row.orderStatus]">
                  {{ ORDER_STATUS_LABEL[row.orderStatus] ?? row.orderStatus }}
                </a-tag>
              </template>
              <template #approvalStatus="{ row }">
                <ApprovalStatusTag :status="row.approvalStatus" />
              </template>
              <template #action="{ row }">
                <a-dropdown :trigger="['click']">
                  <a-button class="lg-row-action-trigger" size="small" type="text">
                    <MoreOutlined />
                  </a-button>
                  <template #overlay>
                    <a-menu>
                      <a-menu-item @click="handleEdit(row)">编辑</a-menu-item>
                      <a-menu-item danger @click="handleDelete(row)">删除</a-menu-item>
                      <a-menu-item
                        v-if="row.approvalStatus === 'DRAFT'"
                        @click="handleSubmitApproval(row)"
                      >
                        提交审批
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

      <!-- 右侧分析面板 -->
      <aside class="lg-analysis-rail">
        <section class="lg-panel">
          <div class="lg-panel-title">订单状态分布</div>
          <div class="lg-type-list">
            <div v-for="it in orderStatusBreakdown" :key="it.label" class="lg-type-row">
              <span class="lg-type-label">{{ it.label }}</span>
              <span class="lg-type-num">{{ it.count }}</span>
              <span class="lg-type-pct">条</span>
            </div>
          </div>
        </section>
      </aside>
    </div>

    <!-- Add/Edit Modal -->
    <a-modal
      v-model:open="modalVisible"
      :title="modalTitle"
      :width="900"
      @ok="handleModalOk"
      @cancel="handleModalCancel"
    >
      <!-- Header Form -->
      <a-form :label-col="{ span: 6 }" :wrapper-col="{ span: 16 }" style="margin-bottom: 8px">
        <a-form-item label="项目" required>
          <a-select
            v-model:value="formData.projectId"
            placeholder="请选择项目"
            show-search
            @change="
              (v: string) => {
                formData.contractId = undefined
                formData.partnerId = undefined
                referenceStore.fetchContracts({ projectId: v })
              }
            "
            :filter-option="
              (input: string, option: SelectOption) =>
                option.label?.toLowerCase().includes(input.toLowerCase())
            "
          >
            <a-select-option v-for="p in projectList" :key="p.id" :value="p.id">
              {{ p.projectName }}
            </a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="采购合同">
          <a-select
            v-model:value="formData.contractId"
            placeholder="请选择合同"
            allow-clear
            show-search
            :filter-option="
              (input: string, option: SelectOption) =>
                option.label?.toLowerCase().includes(input.toLowerCase())
            "
            @change="onContractChange"
          >
            <a-select-option v-for="c in contractList" :key="c.id" :value="c.id">
              {{ c.contractName }}
            </a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="供应商">
          <a-input :value="formPartnerName" disabled placeholder="选择合同后自动填充乙方" />
        </a-form-item>
        <a-form-item label="订单类型">
          <a-select v-model:value="formData.orderType" placeholder="请选择类型" allow-clear>
            <a-select-option value="MATERIAL">材料采购</a-select-option>
            <a-select-option value="EQUIPMENT">设备采购</a-select-option>
            <a-select-option value="SERVICE">服务采购</a-select-option>
            <a-select-option value="OTHER">其他</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="订单日期">
          <a-date-picker
            v-model:value="formData.orderDate"
            value-format="YYYY-MM-DD"
            style="width: 100%"
          />
        </a-form-item>
        <a-form-item label="交货日期">
          <a-date-picker
            v-model:value="formData.deliveryDate"
            value-format="YYYY-MM-DD"
            style="width: 100%"
          />
        </a-form-item>
        <a-form-item label="备注">
          <a-textarea v-model:value="formData.remark" :rows="2" placeholder="请输入备注" />
        </a-form-item>
      </a-form>

      <!-- Line Items Section -->
      <div style="border-top: 1px solid #f0f0f0; padding-top: 12px; margin-top: 4px">
        <div
          style="
            display: flex;
            justify-content: space-between;
            align-items: center;
            margin-bottom: 10px;
          "
        >
          <span style="font-weight: 600; font-size: 14px">订单明细</span>
          <a-button type="dashed" size="small" @click="handleAddItem">+ 添加明细</a-button>
        </div>

        <a-table
          :data-source="itemList"
          :pagination="false"
          row-key="key"
          size="small"
          :scroll="{ y: 250 }"
        >
          <a-table-column title="材料" width="200">
            <template #default="{ record: item, index }">
              <a-select
                :value="item.materialId"
                placeholder="请选择材料"
                allow-clear
                style="width: 100%"
                show-search
                :filter-option="
                  (input: string, option: SelectOption) =>
                    option.label?.toLowerCase().includes(input.toLowerCase())
                "
                @change="(val: string) => handleMaterialChange(index, val)"
              >
                <a-select-option v-for="m in materialList" :key="m.id" :value="m.id">
                  {{ m.materialName }}
                </a-select-option>
              </a-select>
            </template>
          </a-table-column>
          <a-table-column title="规格" width="100">
            <template #default="{ record: item }">
              <span>{{ item.specification || '-' }}</span>
            </template>
          </a-table-column>
          <a-table-column title="单位" width="70">
            <template #default="{ record: item }">
              <span>{{ item.unit || '-' }}</span>
            </template>
          </a-table-column>
          <a-table-column title="数量" width="120">
            <template #default="{ record: item, index }">
              <a-input-number
                v-model:value="item.quantity"
                :min="0"
                :precision="2"
                style="width: 100%"
                @change="handleItemQtyChange(index)"
              />
            </template>
          </a-table-column>
          <a-table-column title="单价(元)" width="130">
            <template #default="{ record: item, index }">
              <a-input-number
                v-model:value="item.unitPrice"
                :min="0"
                :precision="2"
                style="width: 100%"
                @change="handleItemPriceChange(index)"
              />
            </template>
          </a-table-column>
          <a-table-column title="金额(元)" width="130">
            <template #default="{ record: item }">
              <span>{{
                Number(item.amount || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2 })
              }}</span>
            </template>
          </a-table-column>
          <a-table-column title="操作" width="76">
            <template #default="{ index }">
              <a-button type="link" size="small" danger @click="handleRemoveItem(index)"
                >删除</a-button
              >
            </template>
          </a-table-column>
        </a-table>

        <div style="text-align: right; margin-top: 8px; font-size: 14px">
          合计：<span style="font-weight: 600; color: #1677ff"
            >¥{{
              Number(itemsTotalAmount).toLocaleString('zh-CN', { minimumFractionDigits: 2 })
            }}</span
          >
        </div>
      </div>
    </a-modal>
  </div>
</template>
