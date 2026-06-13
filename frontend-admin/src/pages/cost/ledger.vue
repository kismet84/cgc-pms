<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { message } from 'ant-design-vue'
import {
  DollarOutlined,
  LockOutlined,
  ToolOutlined,
  CarOutlined,
  ReloadOutlined,
  SearchOutlined,
} from '@ant-design/icons-vue'
import type { TreeSelectProps } from 'ant-design-vue'
import { getCostLedger, getCostLedgerSummary, getCostLedgerDetail } from '@/api/modules/cost'
import { getProjectList } from '@/api/modules/project'
import { getContractLedger } from '@/api/modules/contract'
import { getPartnerList } from '@/api/modules/partner'
import { getCostSubjectTree, type CostSubjectTreeNode } from '@/api/modules/costSubject'
import type {
  CostLedgerVO,
  CostLedgerQueryParams,
  CostLedgerSummaryVO,
  SourceType,
} from '@/types/cost'
import { SOURCE_TYPE_LABEL, SOURCE_TYPE_COLOR } from '@/types/cost'
import type { PageResult } from '@/types/api'
import type { ProjectVO } from '@/types/project'
import type { ContractVO, ContractQueryParams } from '@/types/contract'
import type { PartnerVO } from '@/types/partner'

// ---- Dropdown data ----
const projectList = ref<ProjectVO[]>([])
const contractList = ref<ContractVO[]>([])
const partnerList = ref<PartnerVO[]>([])
const subjectTree = ref<TreeSelectProps['treeData']>([])

// ---- Filter state ----
const filter = reactive({
  projectId: undefined as string | undefined,
  contractId: undefined as string | undefined,
  partnerId: undefined as string | undefined,
  costSubjectId: undefined as string | undefined,
  costType: undefined as string | undefined,
  sourceType: undefined as string | undefined,
  costStatus: undefined as string | undefined,
  dateRange: [] as string[],
  keyword: '',
})

// ---- Table state ----
const loading = ref(false)
const tableData = ref<CostLedgerVO[]>([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(20)

// ---- KPI state ----
const summary = ref<CostLedgerSummaryVO>({
  totalAmount: '0',
  totalTaxAmount: '0',
  bySourceType: {},
  byProject: {},
  byCostType: {},
})

// ---- Detail drawer ----
const detailVisible = ref(false)
const detailItem = ref<CostLedgerVO | null>(null)

// ---- Summary view mode ----
const summaryMode = ref<'sourceType' | 'project' | 'costType'>('sourceType')

// ---- Fetch dropdown data ----
async function fetchProjects() {
  try {
    const res = await getProjectList({ pageNum: 1, pageSize: 500 })
    projectList.value = res.records
  } catch {
    projectList.value = []
  }
}

async function fetchContracts(projectId?: string) {
  try {
    const params: ContractQueryParams = { pageNo: 1, pageSize: 500 }
    if (projectId) params.projectId = projectId
    const res = await getContractLedger(params)
    contractList.value = res.records
  } catch {
    contractList.value = []
  }
}

async function fetchPartners() {
  try {
    const res = await getPartnerList({ pageNum: 1, pageSize: 500 })
    partnerList.value = res.records
  } catch {
    partnerList.value = []
  }
}

async function fetchSubjectTree() {
  try {
    const data = await getCostSubjectTree()
    subjectTree.value = convertToTreeData(data)
  } catch {
    subjectTree.value = []
  }
}

interface TreeNode {
  id: string
  subjectCode: string
  subjectName: string
  children?: TreeNode[]
}

function convertToTreeData(nodes: TreeNode[]): TreeSelectProps['treeData'] {
  return nodes.map((node) => ({
    value: node.id,
    title: `${node.subjectCode} ${node.subjectName}`,
    children: node.children ? convertToTreeData(node.children) : undefined,
  }))
}

function onProjectChange(val: string | undefined) {
  filter.contractId = undefined
  contractList.value = []
  if (val) fetchContracts(val)
}

// ---- Fetch data ----
async function fetchData() {
  loading.value = true
  const params: CostLedgerQueryParams = {
    pageNum: pageNum.value,
    pageSize: pageSize.value,
    projectId: filter.projectId,
    contractId: filter.contractId,
    partnerId: filter.partnerId,
    costSubjectId: filter.costSubjectId,
    costType: filter.costType,
    sourceType: filter.sourceType,
    costStatus: filter.costStatus,
    startDate: filter.dateRange[0],
    endDate: filter.dateRange[1],
    keyword: filter.keyword || undefined,
  }
  try {
    const res: PageResult<CostLedgerVO> = await getCostLedger(params)
    tableData.value = res.records
    total.value = res.total
  } catch {
    tableData.value = []
    total.value = 0
    message.error('加载成本台账失败，请稍后重试')
  } finally {
    loading.value = false
  }
}

async function fetchSummary() {
  try {
    summary.value = await getCostLedgerSummary({
      projectId: filter.projectId,
      contractId: filter.contractId,
      partnerId: filter.partnerId,
      costSubjectId: filter.costSubjectId,
      costType: filter.costType,
      sourceType: filter.sourceType,
      costStatus: filter.costStatus,
      startDate: filter.dateRange[0],
      endDate: filter.dateRange[1],
      keyword: filter.keyword || undefined,
    })
  } catch {
    summary.value = {
      totalAmount: '0',
      totalTaxAmount: '0',
      bySourceType: {},
      byProject: {},
      byCostType: {},
    }
    message.error('加载成本汇总失败')
  }
}

function handleSearch() {
  pageNum.value = 1
  fetchData()
  fetchSummary()
}

function handleReset() {
  filter.projectId = undefined
  filter.contractId = undefined
  filter.partnerId = undefined
  filter.costSubjectId = undefined
  filter.costType = undefined
  filter.sourceType = undefined
  filter.costStatus = undefined
  filter.dateRange = []
  filter.keyword = ''
  contractList.value = []
  pageNum.value = 1
  fetchData()
  fetchSummary()
}

function handlePageChange(page: number) {
  pageNum.value = page
  fetchData()
}

function handlePageSizeChange(_cur: number, size: number) {
  pageSize.value = size
  pageNum.value = 1
  fetchData()
}

async function handleViewDetail(row: CostLedgerVO) {
  try {
    detailItem.value = await getCostLedgerDetail(row.id)
    detailVisible.value = true
  } catch {
    message.error('加载成本详情失败')
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

const COST_TYPE_LABEL: Record<string, string> = {
  MATERIAL: '材料费',
  LABOR: '人工费',
  MACHINERY: '机械费',
  SUBCONTRACT: '分包费',
  OTHER: '其他费用',
}

const COST_STATUS_LABEL: Record<string, string> = {
  LOCKED: '已锁定',
  CONFIRMED: '已确认',
  PENDING: '待确认',
}

const COST_STATUS_COLOR: Record<string, string> = {
  LOCKED: 'blue',
  CONFIRMED: 'green',
  PENDING: 'orange',
}

// ---- VxeGrid columns ----
const columns = [
  { field: 'costDate', title: '成本日期', width: 110 },
  { field: 'projectName', title: '所属项目', width: 150 },
  { field: 'contractName', title: '关联合同', width: 150 },
  { field: 'partnerName', title: '合作方', width: 140 },
  { field: 'costSubjectName', title: '成本科目', width: 140 },
  { field: 'sourceType', title: '来源类型', width: 120, slots: { default: 'sourceType' } },
  { field: 'costType', title: '费用类型', width: 100, slots: { default: 'costType' } },
  {
    field: 'amount',
    title: '金额(含税)',
    width: 130,
    align: 'right' as const,
    slots: { default: 'amount' },
  },
  {
    field: 'taxAmount',
    title: '税额',
    width: 110,
    align: 'right' as const,
    slots: { default: 'taxAmount' },
  },
  { field: 'costStatus', title: '状态', width: 80, slots: { default: 'costStatus' } },
  { title: '操作', width: 80, fixed: 'right' as const, slots: { default: 'ops' } },
]

// ---- Summary display helpers ----
function summaryEntries(mode: 'sourceType' | 'project' | 'costType'): [string, string][] {
  const map =
    mode === 'sourceType'
      ? summary.value.bySourceType
      : mode === 'project'
        ? summary.value.byProject
        : summary.value.byCostType
  return Object.entries(map || {}).sort(([, a], [, b]) => parseFloat(b) - parseFloat(a))
}

function summaryLabel(mode: 'sourceType' | 'project' | 'costType', key: string): string {
  if (mode === 'sourceType') return SOURCE_TYPE_LABEL[key as SourceType] || key
  return key
}

onMounted(() => {
  fetchProjects()
  fetchContracts()
  fetchPartners()
  fetchSubjectTree()
  fetchData()
  fetchSummary()
})
</script>

<template>
  <div class="cl-page">
    <a-breadcrumb class="cl-breadcrumb">
      <a-breadcrumb-item>成本管理</a-breadcrumb-item>
      <a-breadcrumb-item>成本台账</a-breadcrumb-item>
    </a-breadcrumb>

    <!-- KPI cards -->
    <div class="cl-kpis">
      <div class="cl-kpi">
        <div class="cl-kpi-icon" style="background: #3b82f6"><DollarOutlined /></div>
        <div>
          <div class="cl-kpi-title">成本总额(含税)</div>
          <div class="cl-kpi-value">{{ fmtAmount(summary.totalAmount) }} <small>万元</small></div>
        </div>
      </div>
      <div class="cl-kpi">
        <div class="cl-kpi-icon" style="background: #f59e0b"><LockOutlined /></div>
        <div>
          <div class="cl-kpi-title">合同锁定成本</div>
          <div class="cl-kpi-value">
            {{ fmtAmount(summary.bySourceType?.CT_CONTRACT || '0') }} <small>万元</small>
          </div>
        </div>
      </div>
      <div class="cl-kpi">
        <div class="cl-kpi-icon" style="background: #22c55e"><ToolOutlined /></div>
        <div>
          <div class="cl-kpi-title">材料验收成本</div>
          <div class="cl-kpi-value">
            {{ fmtAmount(summary.bySourceType?.MAT_RECEIPT || '0') }} <small>万元</small>
          </div>
        </div>
      </div>
      <div class="cl-kpi">
        <div class="cl-kpi-icon" style="background: #8b5cf6"><CarOutlined /></div>
        <div>
          <div class="cl-kpi-title">分包计量成本</div>
          <div class="cl-kpi-value">
            {{ fmtAmount(summary.bySourceType?.SUB_MEASURE || '0') }} <small>万元</small>
          </div>
        </div>
      </div>
    </div>

    <!-- Filter card -->
    <div class="cl-card cl-filter">
      <div class="cl-filter-row">
        <div class="cl-field">
          <label>项目名称：</label>
          <a-select
            v-model:value="filter.projectId"
            placeholder="请选择项目"
            allow-clear
            style="width: 160px"
            @change="onProjectChange"
          >
            <a-select-option v-for="p in projectList" :key="p.id" :value="p.id">{{
              p.projectName
            }}</a-select-option>
          </a-select>
        </div>
        <div class="cl-field">
          <label>关联合同：</label>
          <a-select
            v-model:value="filter.contractId"
            placeholder="请选择合同"
            allow-clear
            style="width: 180px"
          >
            <a-select-option v-for="c in contractList" :key="c.id" :value="c.id">{{
              c.contractName
            }}</a-select-option>
          </a-select>
        </div>
        <div class="cl-field">
          <label>合作方：</label>
          <a-select
            v-model:value="filter.partnerId"
            placeholder="请选择合作方"
            allow-clear
            style="width: 160px"
          >
            <a-select-option v-for="p in partnerList" :key="p.id" :value="p.id">{{
              p.partnerName
            }}</a-select-option>
          </a-select>
        </div>
        <div class="cl-field">
          <label>成本科目：</label>
          <a-tree-select
            v-model:value="filter.costSubjectId"
            :tree-data="subjectTree"
            placeholder="请选择科目"
            allow-clear
            style="width: 180px"
          />
        </div>
      </div>
      <div class="cl-filter-row cl-filter-row--last">
        <div class="cl-field">
          <label>费用类型：</label>
          <a-select
            v-model:value="filter.costType"
            placeholder="全部"
            allow-clear
            style="width: 130px"
          >
            <a-select-option value="MATERIAL">材料费</a-select-option>
            <a-select-option value="LABOR">人工费</a-select-option>
            <a-select-option value="MACHINERY">机械费</a-select-option>
            <a-select-option value="SUBCONTRACT">分包费</a-select-option>
            <a-select-option value="OTHER">其他费用</a-select-option>
          </a-select>
        </div>
        <div class="cl-field">
          <label>来源类型：</label>
          <a-select
            v-model:value="filter.sourceType"
            placeholder="全部"
            allow-clear
            style="width: 150px"
          >
            <a-select-option value="CT_CONTRACT">合同锁定</a-select-option>
            <a-select-option value="MAT_RECEIPT">材料验收</a-select-option>
            <a-select-option value="SUB_MEASURE">分包计量</a-select-option>
            <a-select-option value="VAR_ORDER">签证变更</a-select-option>
          </a-select>
        </div>
        <div class="cl-field">
          <label>成本状态：</label>
          <a-select
            v-model:value="filter.costStatus"
            placeholder="全部"
            allow-clear
            style="width: 130px"
          >
            <a-select-option value="LOCKED">已锁定</a-select-option>
            <a-select-option value="CONFIRMED">已确认</a-select-option>
            <a-select-option value="PENDING">待确认</a-select-option>
          </a-select>
        </div>
        <div class="cl-field">
          <label>成本日期：</label>
          <a-range-picker v-model:value="filter.dateRange" style="width: 220px" />
        </div>
        <div class="cl-filter-actions">
          <a-button type="primary" @click="handleSearch"
            ><template #icon><SearchOutlined /></template>查询</a-button
          >
          <a-button @click="handleReset">重置</a-button>
          <a-button @click="fetchData"
            ><template #icon><ReloadOutlined /></template
          ></a-button>
        </div>
      </div>
    </div>

    <!-- Table -->
    <div class="cl-card cl-table-wrap">
      <vxe-grid
        :data="tableData"
        :columns="columns"
        :loading="loading"
        :column-config="{ resizable: true }"
        stripe
        border="inner"
        size="small"
        max-height="480"
      >
        <template #sourceType="{ row }">
          <a-tag :color="SOURCE_TYPE_COLOR[row.sourceType as SourceType] || 'default'">
            {{ SOURCE_TYPE_LABEL[row.sourceType as SourceType] || row.sourceType }}
          </a-tag>
        </template>
        <template #costType="{ row }">
          <span>{{ COST_TYPE_LABEL[row.costType] || row.costType }}</span>
        </template>
        <template #amount="{ row }">
          <span class="cl-money">{{ fmtAmountYuan(row.amount) }}</span>
        </template>
        <template #taxAmount="{ row }">
          <span class="cl-money">{{ fmtAmountYuan(row.taxAmount) }}</span>
        </template>
        <template #costStatus="{ row }">
          <a-tag :color="COST_STATUS_COLOR[row.costStatus] || 'default'">
            {{ COST_STATUS_LABEL[row.costStatus] || row.costStatus }}
          </a-tag>
        </template>
        <template #ops="{ row }">
          <a class="cl-link" @click="handleViewDetail(row)">查看</a>
        </template>
      </vxe-grid>
    </div>

    <!-- Pagination -->
    <div class="cl-pagination">
      <span class="cl-total">共 {{ total }} 条</span>
      <a-pagination
        v-model:current="pageNum"
        v-model:page-size="pageSize"
        :total="total"
        :page-size-options="['10', '20', '50', '100']"
        show-size-changer
        show-quick-jumper
        @change="handlePageChange"
        @show-size-change="handlePageSizeChange"
      />
    </div>

    <!-- Summary section -->
    <div class="cl-card cl-summary" style="margin-top: 14px">
      <div class="cl-summary-header">
        <span class="cl-summary-title">成本汇总</span>
        <a-radio-group v-model:value="summaryMode" size="small">
          <a-radio-button value="sourceType">按来源类型</a-radio-button>
          <a-radio-button value="project">按项目</a-radio-button>
          <a-radio-button value="costType">按费用类型</a-radio-button>
        </a-radio-group>
      </div>
      <div class="cl-summary-list">
        <div v-for="[key, val] in summaryEntries(summaryMode)" :key="key" class="cl-summary-item">
          <span class="cl-summary-label">{{ summaryLabel(summaryMode, key) }}</span>
          <div class="cl-summary-bar">
            <span
              class="cl-summary-bar-fill"
              :style="{
                width:
                  Math.max(
                    2,
                    (parseFloat(val) / Math.max(1, parseFloat(summary.totalAmount || '1'))) * 100,
                  ) + '%',
                background: ['#3b82f6', '#22c55e', '#f59e0b', '#8b5cf6', '#14b8c7'][
                  summaryEntries(summaryMode).findIndex(([k]) => k === key) % 5
                ],
              }"
            ></span>
          </div>
          <span class="cl-summary-amount">{{ fmtAmount(val) }} 万元</span>
        </div>
      </div>
    </div>

    <!-- Detail drawer -->
    <a-drawer v-model:open="detailVisible" title="成本详情" :width="520" placement="right">
      <template v-if="detailItem">
        <a-descriptions :column="2" bordered size="small">
          <a-descriptions-item label="成本日期">{{ detailItem.costDate }}</a-descriptions-item>
          <a-descriptions-item label="成本状态">
            <a-tag :color="COST_STATUS_COLOR[detailItem.costStatus] || 'default'">
              {{ COST_STATUS_LABEL[detailItem.costStatus] || detailItem.costStatus }}
            </a-tag>
          </a-descriptions-item>
          <a-descriptions-item label="所属项目">{{
            detailItem.projectName || '-'
          }}</a-descriptions-item>
          <a-descriptions-item label="关联合同">{{
            detailItem.contractName || '-'
          }}</a-descriptions-item>
          <a-descriptions-item label="合作方">{{
            detailItem.partnerName || '-'
          }}</a-descriptions-item>
          <a-descriptions-item label="成本科目">{{
            detailItem.costSubjectName || '-'
          }}</a-descriptions-item>
          <a-descriptions-item label="费用类型">{{
            COST_TYPE_LABEL[detailItem.costType] || detailItem.costType || '-'
          }}</a-descriptions-item>
          <a-descriptions-item label="来源类型">
            <a-tag :color="SOURCE_TYPE_COLOR[detailItem.sourceType as SourceType] || 'default'">
              {{ SOURCE_TYPE_LABEL[detailItem.sourceType as SourceType] || detailItem.sourceType }}
            </a-tag>
          </a-descriptions-item>
          <a-descriptions-item label="金额(含税)">{{
            fmtAmountYuan(detailItem.amount)
          }}</a-descriptions-item>
          <a-descriptions-item label="税额">{{
            fmtAmountYuan(detailItem.taxAmount)
          }}</a-descriptions-item>
          <a-descriptions-item label="不含税金额">{{
            fmtAmountYuan(detailItem.amountWithoutTax)
          }}</a-descriptions-item>
          <a-descriptions-item label="生成标识">
            {{ detailItem.generatedFlag === '1' ? '自动生成' : '手动录入' }}
          </a-descriptions-item>
          <a-descriptions-item label="来源单据ID">{{
            detailItem.sourceId || '-'
          }}</a-descriptions-item>
          <a-descriptions-item label="来源明细ID">{{
            detailItem.sourceItemId || '-'
          }}</a-descriptions-item>
          <a-descriptions-item label="创建人">{{
            detailItem.createdBy || '-'
          }}</a-descriptions-item>
          <a-descriptions-item label="创建时间">{{
            detailItem.createdAt || '-'
          }}</a-descriptions-item>
          <a-descriptions-item label="备注" :span="2">{{
            detailItem.remark || '-'
          }}</a-descriptions-item>
        </a-descriptions>
      </template>
    </a-drawer>
  </div>
</template>

<style scoped>
.cl-page {
  background: #f6f8fc;
  min-height: 100%;
  padding: 4px 0;
}
.cl-breadcrumb {
  margin-bottom: 16px;
  font-size: 14px;
}
.cl-card {
  background: #fff;
  border: 1px solid #e5eaf3;
  border-radius: 10px;
  box-shadow: 0 10px 30px rgba(17, 24, 39, 0.05);
}

/* KPI */
.cl-kpis {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 10px;
  margin-bottom: 14px;
}
.cl-kpi {
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
.cl-kpi-icon {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  color: #fff;
  display: grid;
  place-items: center;
  font-size: 15px;
  flex-shrink: 0;
}
.cl-kpi-title {
  font-size: 13px;
  color: #6b7280;
  margin-bottom: 6px;
}
.cl-kpi-value {
  font-size: 21px;
  font-weight: 800;
  color: #111827;
  letter-spacing: 0.2px;
}
.cl-kpi-value small {
  font-size: 13px;
  font-weight: 500;
  margin-left: 4px;
}

/* Filter */
.cl-filter {
  padding: 20px 22px;
  margin-bottom: 14px;
}
.cl-filter-row {
  display: flex;
  flex-wrap: wrap;
  gap: 12px 24px;
  align-items: center;
  margin-bottom: 14px;
}
.cl-filter-row--last {
  margin-bottom: 0;
}
.cl-field {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  white-space: nowrap;
}
.cl-field label {
  color: #374151;
  min-width: 56px;
}
.cl-filter-actions {
  display: flex;
  gap: 10px;
  margin-left: auto;
  align-items: center;
}

/* Table */
.cl-table-wrap {
  overflow: hidden;
}
.cl-link {
  color: #1677ff;
  font-weight: 500;
  text-decoration: none;
  cursor: pointer;
}
.cl-money {
  font-variant-numeric: tabular-nums;
}

/* Pagination */
.cl-pagination {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 12px;
  padding: 12px 0 0;
}
.cl-total {
  font-size: 13px;
  color: #4b5563;
}

/* Summary */
.cl-summary {
  padding: 16px 20px;
}
.cl-summary-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}
.cl-summary-title {
  font-size: 15px;
  font-weight: 700;
  color: #111827;
}
.cl-summary-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.cl-summary-item {
  display: grid;
  grid-template-columns: 120px 1fr 100px;
  gap: 12px;
  align-items: center;
  font-size: 13px;
}
.cl-summary-label {
  color: #374151;
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.cl-summary-bar {
  height: 8px;
  border-radius: 99px;
  background: #eef2f7;
  overflow: hidden;
}
.cl-summary-bar-fill {
  height: 100%;
  display: block;
  border-radius: 99px;
  transition: width 0.3s ease;
}
.cl-summary-amount {
  text-align: right;
  color: #111827;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}
</style>
