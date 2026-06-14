<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useReferenceStore } from '@/stores/reference'
import { storeToRefs } from 'pinia'
import {
  FileTextOutlined,
  DollarOutlined,
  PayCircleOutlined,
  WalletOutlined,
  CheckCircleOutlined,
  PlusOutlined,
  ReloadOutlined,
  SearchOutlined,
} from '@ant-design/icons-vue'
import { message, Modal } from 'ant-design-vue'
import {
  getSettlementList,
  deleteSettlement,
  getSettlementKpi,
  computeSettlementAmount,
  createSettlement,
} from '@/api/modules/settlement'
import type {
  SettlementVO,
  SettlementQueryParams,
  SettlementKpiVO,
  SettlementStatus,
} from '@/types/settlement'
import { SETTLEMENT_STATUS_LABEL, SETTLEMENT_STATUS_COLOR } from '@/types/settlement'
import type { PageResult } from '@/types/api'
import type { ProjectVO } from '@/types/project'
import type { ContractVO, ContractQueryParams } from '@/types/contract'
import type { PartnerVO } from '@/types/partner'

const router = useRouter()

// ---- Dropdown data ----
const referenceStore = useReferenceStore()
const { projects, contracts, partners } = storeToRefs(referenceStore)

// ---- Filter state ----
const filter = reactive({
  projectId: undefined as string | undefined,
  contractId: undefined as string | undefined,
  partnerId: undefined as string | undefined,
  settlementStatus: undefined as SettlementStatus | undefined,
  settlementCode: '',
  settlementType: undefined as string | undefined,
})

// ---- Table state ----
const loading = ref(false)
const tableData = ref<SettlementVO[]>([])
const total = ref(0)
const pageNo = ref(1)
const pageSize = ref(20)

// ---- KPI state ----
const kpi = ref<SettlementKpiVO>({
  totalCount: 0,
  totalContractAmount: '0',
  totalFinalAmount: '0',
  totalChangeAmount: '0',
  totalPaidAmount: '0',
  totalUnpaidAmount: '0',
  draftCount: 0,
  finalizedCount: 0,
})

// ---- Create modal ----
const createModalVisible = ref(false)
const createLoading = ref(false)
const createForm = reactive({
  contractId: undefined as string | undefined,
  settlementType: undefined as string | undefined,
  remark: '',
})

function onProjectChange(val: string | undefined) {
  filter.contractId = undefined
  if (val) referenceStore.fetchContracts({ projectId: val })
}

// ---- Fetch data ----
async function fetchData() {
  loading.value = true
  const params: SettlementQueryParams = {
    projectId: filter.projectId,
    contractId: filter.contractId,
    partnerId: filter.partnerId,
    settlementStatus: filter.settlementStatus,
    settlementCode: filter.settlementCode || undefined,
    settlementType: filter.settlementType,
    pageNo: pageNo.value,
    pageSize: pageSize.value,
  }
  try {
    const res: PageResult<SettlementVO> = await getSettlementList(params)
    tableData.value = res.records
    total.value = res.total
  } catch {
    tableData.value = []
    total.value = 0
    message.error('加载结算列表失败，请稍后重试')
  } finally {
    loading.value = false
  }
}

async function fetchKpi() {
  try {
    kpi.value = await getSettlementKpi()
  } catch {
    kpi.value = {
      totalCount: 0,
      totalContractAmount: '0',
      totalFinalAmount: '0',
      totalChangeAmount: '0',
      totalPaidAmount: '0',
      totalUnpaidAmount: '0',
      draftCount: 0,
      finalizedCount: 0,
    }
  }
}

function handleSearch() {
  pageNo.value = 1
  fetchData()
  fetchKpi()
}

function handleReset() {
  filter.projectId = undefined
  filter.contractId = undefined
  filter.partnerId = undefined
  filter.settlementStatus = undefined
  filter.settlementCode = ''
  filter.settlementType = undefined
  pageNo.value = 1
  fetchData()
  fetchKpi()
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

function handleView(row: SettlementVO) {
  router.push(`/settlement/${row.id}`)
}

async function handleDelete(row: SettlementVO) {
  if (row.settlementStatus === 'FINALIZED') {
    message.warning('已定案的结算单不可删除')
    return
  }
  Modal.confirm({
    title: '确认删除',
    content: `确定要删除结算单 ${row.settlementCode} 吗？`,
    okText: '确认删除',
    cancelText: '取消',
    okType: 'danger',
    onOk: async () => {
      try {
        await deleteSettlement(row.id)
        message.success('已删除')
        fetchData()
        fetchKpi()
      } catch {
        message.error('删除失败，请稍后重试')
      }
    },
  })
}

// ---- Create handler ----
function openCreateModal() {
  createForm.contractId = undefined
  createForm.settlementType = undefined
  createForm.remark = ''
  createModalVisible.value = true
}

async function handleCreate() {
  if (!createForm.contractId) {
    message.warning('请选择关联合同')
    return
  }
  createLoading.value = true
  try {
    await createSettlement({
      contractId: createForm.contractId,
      settlementType: createForm.settlementType,
      remark: createForm.remark,
    })
    message.success('结算单创建成功')
    createModalVisible.value = false
    fetchData()
    fetchKpi()
  } catch {
    message.error('创建失败，请稍后重试')
  } finally {
    createLoading.value = false
  }
}

// ---- Helpers ----
function fmtAmount(val: string): string {
  const n = parseFloat(val)
  if (isNaN(n)) return '0.00'
  return (n / 10000).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

function fmtAmountYuan(val: string): string {
  const n = parseFloat(val)
  if (isNaN(n)) return '0.00'
  return n.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

// ---- VxeGrid columns ----
const columns = [
  { type: 'checkbox', width: 46, fixed: 'left' as const },
  { field: 'settlementCode', title: '结算编号', width: 170, slots: { default: 'settlementCode' } },
  { field: 'contractName', title: '关联合同', minWidth: 160 },
  { field: 'projectName', title: '所属项目', width: 150 },
  { field: 'partnerName', title: '合作方', width: 150 },
  {
    field: 'contractAmount',
    title: '合同金额(含税)',
    width: 140,
    align: 'right' as const,
    slots: { default: 'contractAmount' },
  },
  {
    field: 'changeAmount',
    title: '变更金额',
    width: 120,
    align: 'right' as const,
    slots: { default: 'changeAmount' },
  },
  {
    field: 'finalAmount',
    title: '结算金额',
    width: 140,
    align: 'right' as const,
    slots: { default: 'finalAmount' },
  },
  {
    field: 'paidAmount',
    title: '已付款',
    width: 120,
    align: 'right' as const,
    slots: { default: 'paidAmount' },
  },
  {
    field: 'unpaidAmount',
    title: '未付款',
    width: 120,
    align: 'right' as const,
    slots: { default: 'unpaidAmount' },
  },
  {
    field: 'settlementStatus',
    title: '结算状态',
    width: 100,
    slots: { default: 'settlementStatus' },
  },
  { field: 'approvalStatus', title: '审批状态', width: 100, slots: { default: 'approvalStatus' } },
  { field: 'createdAt', title: '创建时间', width: 160 },
  { title: '操作', width: 140, fixed: 'right' as const, slots: { default: 'ops' } },
]

const APPROVAL_STATUS_LABEL: Record<string, string> = {
  DRAFT: '草稿',
  APPROVING: '审批中',
  APPROVED: '已通过',
  REJECTED: '已驳回',
  WITHDRAWN: '已撤回',
}
const APPROVAL_STATUS_COLOR: Record<string, string> = {
  DRAFT: 'default',
  APPROVING: 'processing',
  APPROVED: 'success',
  REJECTED: 'error',
  WITHDRAWN: 'warning',
}

onMounted(() => {
  referenceStore.fetchProjects()
  referenceStore.fetchContracts()
  referenceStore.fetchPartners()
  fetchData()
  fetchKpi()
})
</script>

<template>
  <div class="stl-page">
    <a-breadcrumb class="stl-breadcrumb">
      <a-breadcrumb-item>结算管理</a-breadcrumb-item>
      <a-breadcrumb-item>结算列表</a-breadcrumb-item>
    </a-breadcrumb>

    <!-- KPI cards -->
    <div class="stl-kpis">
      <div class="stl-kpi">
        <div class="stl-kpi-icon" style="background: #3b82f6"><FileTextOutlined /></div>
        <div>
          <div class="stl-kpi-title">结算单总数</div>
          <div class="stl-kpi-value">{{ kpi.totalCount }} <small>份</small></div>
        </div>
      </div>
      <div class="stl-kpi">
        <div class="stl-kpi-icon" style="background: #22c55e"><DollarOutlined /></div>
        <div>
          <div class="stl-kpi-title">合同金额合计</div>
          <div class="stl-kpi-value">
            {{ fmtAmount(kpi.totalContractAmount) }} <small>万元</small>
          </div>
        </div>
      </div>
      <div class="stl-kpi">
        <div class="stl-kpi-icon" style="background: #f59e0b"><PayCircleOutlined /></div>
        <div>
          <div class="stl-kpi-title">结算金额合计</div>
          <div class="stl-kpi-value">{{ fmtAmount(kpi.totalFinalAmount) }} <small>万元</small></div>
        </div>
      </div>
      <div class="stl-kpi">
        <div class="stl-kpi-icon" style="background: #8b5cf6"><WalletOutlined /></div>
        <div>
          <div class="stl-kpi-title">已付款合计</div>
          <div class="stl-kpi-value">{{ fmtAmount(kpi.totalPaidAmount) }} <small>万元</small></div>
        </div>
      </div>
      <div class="stl-kpi">
        <div class="stl-kpi-icon" style="background: #ef4444"><CheckCircleOutlined /></div>
        <div>
          <div class="stl-kpi-title">已定案</div>
          <div class="stl-kpi-value">{{ kpi.finalizedCount }} <small>份</small></div>
        </div>
      </div>
    </div>

    <!-- Filter card -->
    <div class="stl-card stl-filter">
      <div class="stl-filter-row">
        <div class="stl-field">
          <label>所属项目：</label>
          <a-select
            v-model:value="filter.projectId"
            placeholder="请选择项目"
            allow-clear
            style="width: 160px"
            @change="onProjectChange"
          >
            <a-select-option v-for="p in projects" :key="p.id" :value="p.id">{{
              p.projectName
            }}</a-select-option>
          </a-select>
        </div>
        <div class="stl-field">
          <label>关联合同：</label>
          <a-select
            v-model:value="filter.contractId"
            placeholder="请选择合同"
            allow-clear
            style="width: 180px"
          >
            <a-select-option v-for="c in contracts" :key="c.id" :value="c.id">{{
              c.contractName
            }}</a-select-option>
          </a-select>
        </div>
        <div class="stl-field">
          <label>合作方：</label>
          <a-select
            v-model:value="filter.partnerId"
            placeholder="请选择合作方"
            allow-clear
            style="width: 160px"
          >
            <a-select-option v-for="p in partners" :key="p.id" :value="p.id">{{
              p.partnerName
            }}</a-select-option>
          </a-select>
        </div>
        <div class="stl-field">
          <label>结算状态：</label>
          <a-select
            v-model:value="filter.settlementStatus"
            placeholder="全部"
            allow-clear
            style="width: 130px"
          >
            <a-select-option value="DRAFT">草稿</a-select-option>
            <a-select-option value="FINALIZED">已定案</a-select-option>
            <a-select-option value="CANCELLED">已作废</a-select-option>
          </a-select>
        </div>
      </div>
      <div class="stl-filter-row stl-filter-row--last">
        <div class="stl-field">
          <label>结算编号：</label>
          <a-input
            v-model:value="filter.settlementCode"
            placeholder="请输入结算编号"
            style="width: 170px"
          />
        </div>
        <div class="stl-filter-actions">
          <a-button type="primary" @click="handleSearch"
            ><template #icon><SearchOutlined /></template>查询</a-button
          >
          <a-button @click="handleReset">重置</a-button>
        </div>
      </div>
    </div>

    <!-- Toolbar -->
    <div class="stl-toolbar">
      <a-button type="primary" @click="openCreateModal"
        ><template #icon><PlusOutlined /></template>新建结算</a-button
      >
      <a-button @click="fetchData"
        ><template #icon><ReloadOutlined /></template
      ></a-button>
    </div>

    <!-- Table -->
    <div class="stl-card stl-table-wrap">
      <vxe-grid
        :data="tableData"
        :columns="columns"
        :loading="loading"
        :column-config="{ resizable: true }"
        :checkbox-config="{ highlight: true }"
        stripe
        border="inner"
        size="small"
        max-height="480"
      >
        <template #settlementCode="{ row }">
          <a class="stl-link" @click="handleView(row)">{{ row.settlementCode }}</a>
        </template>
        <template #contractAmount="{ row }">
          <span class="stl-money">{{ fmtAmountYuan(row.contractAmount) }}</span>
        </template>
        <template #changeAmount="{ row }">
          <span class="stl-money">{{ fmtAmountYuan(row.changeAmount) }}</span>
        </template>
        <template #finalAmount="{ row }">
          <span class="stl-money" style="font-weight: 600; color: #3b82f6">{{
            fmtAmountYuan(row.finalAmount)
          }}</span>
        </template>
        <template #paidAmount="{ row }">
          <span class="stl-money">{{ fmtAmountYuan(row.paidAmount) }}</span>
        </template>
        <template #unpaidAmount="{ row }">
          <span class="stl-money">{{ fmtAmountYuan(row.unpaidAmount) }}</span>
        </template>
        <template #settlementStatus="{ row }">
          <a-tag
            :color="SETTLEMENT_STATUS_COLOR[row.settlementStatus as SettlementStatus] || 'default'"
          >
            {{
              SETTLEMENT_STATUS_LABEL[row.settlementStatus as SettlementStatus] ||
              row.settlementStatus
            }}
          </a-tag>
        </template>
        <template #approvalStatus="{ row }">
          <a-tag :color="APPROVAL_STATUS_COLOR[row.approvalStatus] || 'default'">
            {{ APPROVAL_STATUS_LABEL[row.approvalStatus] || row.approvalStatus }}
          </a-tag>
        </template>
        <template #ops="{ row }">
          <div class="stl-ops">
            <a class="stl-link" @click="handleView(row)">查看</a>
            <a
              v-if="row.settlementStatus !== 'FINALIZED'"
              class="stl-link stl-del"
              @click="handleDelete(row)"
              >删除</a
            >
          </div>
        </template>
      </vxe-grid>
    </div>

    <!-- Pagination -->
    <div class="stl-pagination">
      <span class="stl-total">共 {{ total }} 条</span>
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

    <!-- Create Modal -->
    <a-modal
      v-model:open="createModalVisible"
      title="新建结算单"
      :confirm-loading="createLoading"
      @ok="handleCreate"
    >
      <a-form layout="vertical">
        <a-form-item label="关联合同" required>
          <a-select
            v-model:value="createForm.contractId"
            placeholder="请选择合同"
            style="width: 100%"
            show-search
            option-filter-prop="label"
          >
            <a-select-option
              v-for="c in contracts"
              :key="c.id"
              :value="c.id"
              :label="c.contractName"
              >{{ c.contractName }}</a-select-option
            >
          </a-select>
        </a-form-item>
        <a-form-item label="结算类型">
          <a-select
            v-model:value="createForm.settlementType"
            placeholder="请选择结算类型"
            allow-clear
            style="width: 100%"
          >
            <a-select-option value="PROGRESS">进度结算</a-select-option>
            <a-select-option value="FINAL">竣工结算</a-select-option>
            <a-select-option value="INTERIM">期中结算</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="备注">
          <a-textarea v-model:value="createForm.remark" placeholder="备注（选填）" :rows="3" />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<style scoped>
.stl-page {
  background: #f6f8fc;
  min-height: 100%;
  padding: 4px 0;
}
.stl-breadcrumb {
  margin-bottom: 16px;
  font-size: 14px;
}
.stl-card {
  background: #fff;
  border: 1px solid #e5eaf3;
  border-radius: 10px;
  box-shadow: 0 10px 30px rgba(17, 24, 39, 0.05);
}

/* KPI */
.stl-kpis {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 10px;
  margin-bottom: 14px;
}
.stl-kpi {
  height: 96px;
  padding: 18px;
  background: #fff;
  border-radius: 10px;
  border: 1px solid #edf1f7;
  display: flex;
  gap: 14px;
  align-items: flex-start;
  box-shadow: 0 10px 30px rgba(17, 24, 39, 0.05);
}
.stl-kpi-icon {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  color: #fff;
  display: grid;
  place-items: center;
  font-size: 15px;
  flex-shrink: 0;
}
.stl-kpi-title {
  font-size: 13px;
  color: #6b7280;
  margin-bottom: 6px;
}
.stl-kpi-value {
  font-size: 21px;
  font-weight: 800;
  color: #111827;
  letter-spacing: 0.2px;
}
.stl-kpi-value small {
  font-size: 13px;
  font-weight: 500;
  margin-left: 4px;
}

/* Filter */
.stl-filter {
  padding: 20px 22px;
  margin-bottom: 14px;
}
.stl-filter-row {
  display: flex;
  flex-wrap: wrap;
  gap: 12px 24px;
  align-items: center;
  margin-bottom: 14px;
}
.stl-filter-row--last {
  margin-bottom: 0;
}
.stl-field {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  white-space: nowrap;
}
.stl-field label {
  color: #374151;
  min-width: 56px;
}
.stl-filter-actions {
  display: flex;
  gap: 10px;
  margin-left: auto;
  align-items: center;
}

/* Toolbar */
.stl-toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
}

/* Table */
.stl-table-wrap {
  overflow: hidden;
}
.stl-link {
  color: #1677ff;
  font-weight: 500;
  text-decoration: none;
  cursor: pointer;
}
.stl-money {
  font-variant-numeric: tabular-nums;
}
.stl-ops {
  display: flex;
  gap: 10px;
  justify-content: center;
}
.stl-del {
  color: #ef4444;
}

/* Pagination */
.stl-pagination {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 12px;
  padding: 12px 0 0;
}
.stl-total {
  font-size: 13px;
  color: #4b5563;
}

@media (max-width: 1200px) {
  .stl-kpis {
    grid-template-columns: repeat(3, 1fr);
  }
}
</style>
