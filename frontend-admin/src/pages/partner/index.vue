<script setup lang="ts">
import { ref, reactive, onMounted, computed } from 'vue'
import { message, Modal } from 'ant-design-vue'
import {
  BankOutlined,
  CheckCircleOutlined,
  ClockCircleOutlined,
  MoreOutlined,
  PlusOutlined,
  ReloadOutlined,
  SafetyCertificateOutlined,
  SearchOutlined,
  TeamOutlined,
  WarningOutlined,
} from '@ant-design/icons-vue'
import { getDictDataByCode } from '@/api/modules/dict'
import {
  getPartnerList,
  getPartnerDetail,
  createPartner,
  updatePartner,
  deletePartner,
} from '@/api/modules/partner'
import type { PartnerVO } from '@/types/partner'
import { useColumnSettings } from '@/composables/useColumnSettings'
import { ColumnSettingsButton } from '@/components/list-page'
import { normalizeArray } from '@/utils/normalizeArray'

const filter = reactive({
  partnerCode: '',
  partnerName: '',
  partnerType: undefined as string | undefined,
  status: undefined as string | undefined,
})

const loading = ref(false)
const tableData = ref<PartnerVO[]>([])
const total = ref(0)
const pageNo = ref(1)
const pageSize = ref(20)

const modalVisible = ref(false)
const modalTitle = ref('新建合作方')
const editingId = ref<string | null>(null)
const formLoading = ref(false)
const detailVisible = ref(false)
const detailLoading = ref(false)
const detailPartner = ref<PartnerVO | null>(null)
const formData = reactive<Partial<PartnerVO>>({
  partnerCode: '',
  partnerName: '',
  partnerType: undefined,
  creditCode: '',
  legalPerson: '',
  contactName: '',
  contactPhone: '',
  bankName: '',
  bankAccount: '',
  qualificationLevel: '',
  blacklistFlag: false,
  riskLevel: undefined as string | undefined,
  status: 'ENABLE',
})

const partnerTypeOptions = ref<{ dictLabel: string; dictValue: string }[]>([
  { dictLabel: '甲方', dictValue: 'PARTY_A' },
  { dictLabel: '乙方', dictValue: 'PARTY_B' },
  { dictLabel: '其他', dictValue: 'OTHER' },
])
const fallbackPartnerTypeOptions = [
  { dictLabel: '甲方', dictValue: 'PARTY_A' },
  { dictLabel: '乙方', dictValue: 'PARTY_B' },
  { dictLabel: '其他', dictValue: 'OTHER' },
]
const partnerTypeLabel = (val: string) => {
  const options = Array.isArray(partnerTypeOptions.value) ? partnerTypeOptions.value : []
  const fromDict = options.find((o) => o.dictValue === val)
  if (fromDict) return fromDict.dictLabel
  const fallback: Record<string, string> = { PARTY_A: '甲方', PARTY_B: '乙方', OTHER: '其他' }
  return fallback[val] ?? val
}
const partnerTypeColor = (val: string): string => {
  const map: Record<string, string> = { PARTY_A: 'blue', PARTY_B: 'green', OTHER: 'default' }
  return map[val] ?? 'default'
}

async function fetchPartnerTypes() {
  try {
    const options = normalizeArray<{ dictLabel: string; dictValue: string }>(
      await getDictDataByCode('partner_type'),
    )
    partnerTypeOptions.value = options.length ? options : fallbackPartnerTypeOptions
  } catch (e: unknown) {
    console.error(e)
    partnerTypeOptions.value = fallbackPartnerTypeOptions
  }
}

const RISK_COLOR: Record<string, string> = {
  LOW: 'success',
  MEDIUM: 'warning',
  HIGH: 'error',
}
const RISK_LABEL: Record<string, string> = {
  LOW: '低',
  MEDIUM: '中',
  HIGH: '高',
}

const gridColumns = computed(() => [
  {
    field: 'partnerCode',
    title: '合作方编号',
    minWidth: 150,
    ellipsis: true,
    slots: { default: 'partnerCode' },
  },
  { field: 'partnerName', title: '合作方名称', minWidth: 180, ellipsis: true },
  { field: 'partnerType', title: '类型', width: 80, slots: { default: 'partnerType' } },
  { field: 'contactName', title: '联系人', minWidth: 90 },
  { field: 'contactPhone', title: '联系电话', minWidth: 120 },
  { field: 'qualificationLevel', title: '资质等级', minWidth: 100, ellipsis: true },
  { field: 'blacklistFlag', title: '黑名单', width: 92, slots: { default: 'blacklistFlag' } },
  { field: 'riskLevel', title: '风险等级', width: 104, slots: { default: 'riskLevel' } },
  { field: 'status', title: '状态', width: 88, slots: { default: 'status' } },
  { title: '操作', width: 76, slots: { default: 'ops' } },
])

const {
  visibleColumns: visibleGridColumns,
  columnSettings,
  colVisible,
  toggleCol,
} = useColumnSettings('partner_list_cols_v2', gridColumns, {
  partnerType: false,
  qualificationLevel: false,
  blacklistFlag: false,
  riskLevel: false,
})

const partnerStats = computed(() => ({
  total: total.value,
  partyA: tableData.value.filter((item) => item.partnerType === 'PARTY_A').length,
  partyB: tableData.value.filter((item) => item.partnerType === 'PARTY_B').length,
  enabled: tableData.value.filter((item) => item.status === 'ENABLE').length,
  risk: tableData.value.filter((item) => item.riskLevel === 'HIGH' || item.blacklistFlag).length,
}))

const partnerTypeSummary = computed(() =>
  ['PARTY_A', 'PARTY_B', 'OTHER'].map((type) => ({
    key: type,
    label: partnerTypeLabel(type),
    count: tableData.value.filter((item) => item.partnerType === type).length,
    color: type === 'PARTY_A' ? '#1890ff' : type === 'PARTY_B' ? '#52c41a' : '#8c8c8c',
  })),
)

const recentPartners = computed(() => tableData.value.slice(0, 4))

const partnerStatusSummary = computed(() => [
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
    color: '#94a3b8',
  },
])

function summaryPct(value: number): number {
  const base = tableData.value.length || 1
  return Math.round((value / base) * 100)
}

async function fetchData() {
  loading.value = true
  try {
    const res = await getPartnerList({
      pageNum: pageNo.value,
      pageSize: pageSize.value,
      partnerCode: filter.partnerCode || undefined,
      partnerName: filter.partnerName || undefined,
      partnerType: filter.partnerType,
      status: filter.status,
    })
    tableData.value = normalizeArray<PartnerVO>(res.records)
    tableData.value.sort((a, b) =>
      a.partnerType === 'PARTY_A' ? -1 : b.partnerType === 'PARTY_A' ? 1 : 0,
    )
    total.value = Number(res.total ?? 0)
  } catch (e: unknown) {
    console.error(e)
    tableData.value = []
    total.value = 0
    message.error('加载合作方列表失败，请稍后重试')
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  pageNo.value = 1
  fetchData()
}
function handleReset() {
  filter.partnerCode = ''
  filter.partnerName = ''
  filter.partnerType = undefined
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
  modalTitle.value = '新建合作方'
  editingId.value = null
  Object.assign(formData, {
    partnerCode: '',
    partnerName: '',
    partnerType: undefined,
    creditCode: '',
    legalPerson: '',
    contactName: '',
    contactPhone: '',
    bankName: '',
    bankAccount: '',
    qualificationLevel: '',
    blacklistFlag: false,
    riskLevel: undefined,
    status: 'ENABLE',
  })
  modalVisible.value = true
}

async function handleView(record: PartnerVO) {
  detailPartner.value = record
  detailVisible.value = true
  detailLoading.value = true
  try {
    detailPartner.value = await getPartnerDetail(record.id)
  } catch (e: unknown) {
    console.error(e)
    message.error('加载合作方详情失败')
  } finally {
    detailLoading.value = false
  }
}

function handleEdit(record: PartnerVO) {
  modalTitle.value = '编辑合作方'
  editingId.value = record.id
  Object.assign(formData, {
    partnerCode: record.partnerCode || '',
    partnerName: record.partnerName || '',
    partnerType: record.partnerType || undefined,
    creditCode: record.creditCode || '',
    legalPerson: record.legalPerson || '',
    contactName: record.contactName || '',
    contactPhone: record.contactPhone || '',
    bankName: record.bankName || '',
    bankAccount: record.bankAccount || '',
    qualificationLevel: record.qualificationLevel || '',
    blacklistFlag: record.blacklistFlag,
    riskLevel: record.riskLevel || undefined,
    status: record.status || 'ENABLE',
  })
  modalVisible.value = true
}

function handleDelete(record: PartnerVO) {
  Modal.confirm({
    title: '确认删除',
    content: `确定要删除合作方"${record.partnerName}"吗？`,
    okText: '确定',
    cancelText: '取消',
    onOk: async () => {
      try {
        await deletePartner(record.id)
        message.success('删除成功')
        fetchData()
      } catch (e: unknown) {
        console.error(e)
        Modal.error({ title: '删除失败', content: '删除失败，请稍后重试' })
      }
    },
  })
}

async function handleModalOk() {
  if (!formData.partnerName || !formData.partnerName.trim()) {
    message.warning('请输入合作方名称')
    return
  }
  if (!formData.partnerType) {
    message.warning('请选择合作方类型')
    return
  }
  formLoading.value = true
  try {
    const payload = {
      ...formData,
      blacklistFlag: formData.blacklistFlag ? 1 : 0,
    }
    if (editingId.value) {
      await updatePartner(editingId.value, payload)
      message.success('更新成功')
    } else {
      await createPartner(payload)
      message.success('创建成功')
    }
    modalVisible.value = false
    fetchData()
  } catch (e: unknown) {
    console.error(e)
    message.error('操作失败，请稍后重试')
  } finally {
    formLoading.value = false
  }
}

function handleModalCancel() {
  modalVisible.value = false
}

onMounted(() => {
  fetchData()
  fetchPartnerTypes()
})
</script>

<template>
  <div class="lg-list-page lg-page app-page partner-page">
    <div class="lg-page-head partner-page-head">
      <div class="partner-page-meta-row">
        <a-breadcrumb class="partner-breadcrumb">
          <a-breadcrumb-item>合作方管理</a-breadcrumb-item>
        </a-breadcrumb>
        <span class="partner-page-subtitle"
          >维护甲方、乙方与供应商基础信息，跟踪资质、状态与风险</span
        >
      </div>
    </div>

    <!-- 搜索栏 -->
    <div class="lg-search-bar partner-search-bar">
      <a-input
        v-model:value="filter.partnerName"
        placeholder="搜索合作方名称…"
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
        <div class="partner-kpi-summary" aria-label="合作方关键指标">
          <div class="partner-kpi-item">
            <span class="partner-kpi-icon is-blue"><TeamOutlined /></span>
            <span class="partner-kpi-label">合作方总数</span>
            <strong>{{ partnerStats.total }} <small>个</small></strong>
          </div>
          <div class="partner-kpi-item">
            <span class="partner-kpi-icon is-cyan"><BankOutlined /></span>
            <span class="partner-kpi-label">甲方单位</span>
            <strong>{{ partnerStats.partyA }} <small>个</small></strong>
          </div>
          <div class="partner-kpi-item">
            <span class="partner-kpi-icon is-green"><SafetyCertificateOutlined /></span>
            <span class="partner-kpi-label">乙方单位</span>
            <strong>{{ partnerStats.partyB }} <small>个</small></strong>
          </div>
          <div class="partner-kpi-item">
            <span class="partner-kpi-icon is-purple"><CheckCircleOutlined /></span>
            <span class="partner-kpi-label">启用合作方</span>
            <strong>{{ partnerStats.enabled }} <small>个</small></strong>
          </div>
          <div class="partner-kpi-item">
            <span class="partner-kpi-icon is-red"><WarningOutlined /></span>
            <span class="partner-kpi-label">风险合作方</span>
            <strong>{{ partnerStats.risk }} <small>个</small></strong>
          </div>
        </div>

        <main class="lg-list-table-panel">
          <div class="lg-toolbar">
            <div class="lg-toolbar-left">
              <span class="partner-table-title">合作方列表</span>
              <span class="partner-table-count">共 {{ total }} 条</span>
              <ColumnSettingsButton
                :columns="columnSettings"
                :visible="colVisible"
                @toggle="toggleCol"
              />
              <a-button type="primary" @click="handleAdd">
                <template #icon><PlusOutlined /></template>
                新建合作方
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
              <template #partnerCode="{ row }">
                <a-button class="partner-code-link" type="link" @click="handleView(row)">
                  {{ row.partnerCode || '-' }}
                </a-button>
              </template>
              <template #partnerType="{ row }">
                <a-tag :color="partnerTypeColor(row.partnerType)">
                  {{ partnerTypeLabel(row.partnerType) }}
                </a-tag>
              </template>
              <template #blacklistFlag="{ row }">
                <a-tag v-if="row.blacklistFlag" color="error">黑名单</a-tag>
                <span v-else class="lg-none">-</span>
              </template>
              <template #riskLevel="{ row }">
                <a-tag :color="RISK_COLOR[row.riskLevel]">
                  {{ RISK_LABEL[row.riskLevel] ?? row.riskLevel }}
                </a-tag>
              </template>
              <template #status="{ row }">
                <a-tag :color="row.status === 'ENABLE' ? 'success' : 'default'">
                  {{ row.status === 'ENABLE' ? '启用' : '禁用' }}
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

      <aside class="lg-analysis-rail partner-analysis-rail" aria-label="合作方辅助分析">
        <div class="partner-analysis-panel">
          <header class="partner-analysis-head">
            <div>
              <div class="partner-analysis-title">合作方分析</div>
              <div class="partner-analysis-subtitle">类型、状态与近期维护</div>
            </div>
          </header>
          <section class="partner-analysis-section">
            <div class="partner-section-title">合作方类型分布</div>
            <div>
              <div v-for="item in partnerTypeSummary" :key="item.key" class="lg-type-row">
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
            </div>
          </section>
          <section class="partner-analysis-section">
            <div class="partner-section-title">合作方状态</div>
            <div v-for="item in partnerStatusSummary" :key="item.key" class="lg-type-row">
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
          <section class="partner-analysis-section">
            <div class="partner-section-title">近期合作方</div>
            <div>
              <div v-for="item in recentPartners" :key="item.id" class="lg-type-row">
                <span class="lg-type-dot" style="background: #2563eb"></span>
                <span class="lg-type-label">{{ item.partnerName }}</span>
                <span class="lg-type-bar-wrap"></span>
                <span class="lg-type-num"><ClockCircleOutlined /></span>
                <span class="lg-type-pct"></span>
              </div>
              <div v-if="!recentPartners.length" class="lg-warning-empty">暂无合作方</div>
            </div>
          </section>
        </div>
      </aside>
    </div>

    <!-- Add/Edit Modal -->
    <a-modal
      v-model:open="modalVisible"
      :title="modalTitle"
      :confirm-loading="formLoading"
      :mask-closable="false"
      class="lg-modal-form"
      ok-text="保存"
      cancel-text="取消"
      width="800px"
      wrap-class-name="compact-partner-modal"
      @ok="handleModalOk"
      @cancel="handleModalCancel"
    >
      <a-form size="small" :label-col="{ span: 6 }" :wrapper-col="{ span: 17 }">
        <a-form-item label="合作方名称" required>
          <a-input v-model:value="formData.partnerName" placeholder="请输入合作方名称" />
        </a-form-item>
        <a-form-item label="合作方类型" required>
          <a-select v-model:value="formData.partnerType" placeholder="请选择合作方类型">
            <a-select-option
              v-for="opt in partnerTypeOptions"
              :key="opt.dictValue"
              :value="opt.dictValue"
            >
              {{ opt.dictLabel }}
            </a-select-option></a-select
          >
        </a-form-item>
        <a-form-item label="统一信用代码">
          <a-input v-model:value="formData.creditCode" placeholder="请输入统一社会信用代码" />
        </a-form-item>
        <a-form-item label="法人代表">
          <a-input v-model:value="formData.legalPerson" placeholder="请输入法人代表" />
        </a-form-item>
        <a-form-item label="联系人">
          <a-input v-model:value="formData.contactName" placeholder="请输入联系人" />
        </a-form-item>
        <a-form-item label="联系电话">
          <a-input v-model:value="formData.contactPhone" placeholder="请输入联系电话" />
        </a-form-item>
        <a-form-item label="开户银行">
          <a-input v-model:value="formData.bankName" placeholder="请输入开户银行" />
        </a-form-item>
        <a-form-item label="银行账号">
          <a-input v-model:value="formData.bankAccount" placeholder="请输入银行账号" />
        </a-form-item>
        <a-form-item label="资质等级">
          <a-input v-model:value="formData.qualificationLevel" placeholder="请输入资质等级" />
        </a-form-item>
        <a-form-item label="黑名单">
          <a-switch v-model:checked="formData.blacklistFlag" />
        </a-form-item>
        <a-form-item label="风险等级">
          <a-select v-model:value="formData.riskLevel" placeholder="请选择风险等级">
            <a-select-option value="LOW">低</a-select-option>
            <a-select-option value="MEDIUM">中</a-select-option>
            <a-select-option value="HIGH">高</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="状态">
          <a-select v-model:value="formData.status">
            <a-select-option value="ENABLE">启用</a-select-option>
            <a-select-option value="DISABLE">禁用</a-select-option>
          </a-select>
        </a-form-item>
      </a-form>
    </a-modal>

    <a-modal
      v-model:open="detailVisible"
      title="合作方详情"
      :footer="null"
      :width="800"
      wrap-class-name="compact-partner-detail-modal"
    >
      <a-spin :spinning="detailLoading">
        <a-descriptions
          v-if="detailPartner"
          bordered
          size="small"
          :column="2"
          class="partner-detail-descriptions"
        >
          <a-descriptions-item label="合作方编号">
            {{ detailPartner.partnerCode || '-' }}
          </a-descriptions-item>
          <a-descriptions-item label="合作方名称">
            {{ detailPartner.partnerName || '-' }}
          </a-descriptions-item>
          <a-descriptions-item label="合作方类型">
            <a-tag :color="partnerTypeColor(detailPartner.partnerType)">
              {{ partnerTypeLabel(detailPartner.partnerType) }}
            </a-tag>
          </a-descriptions-item>
          <a-descriptions-item label="状态">
            <a-tag :color="detailPartner.status === 'ENABLE' ? 'success' : 'default'">
              {{ detailPartner.status === 'ENABLE' ? '启用' : '禁用' }}
            </a-tag>
          </a-descriptions-item>
          <a-descriptions-item label="统一信用代码">
            {{ detailPartner.creditCode || '-' }}
          </a-descriptions-item>
          <a-descriptions-item label="法人代表">
            {{ detailPartner.legalPerson || '-' }}
          </a-descriptions-item>
          <a-descriptions-item label="联系人">
            {{ detailPartner.contactName || '-' }}
          </a-descriptions-item>
          <a-descriptions-item label="联系电话">
            {{ detailPartner.contactPhone || '-' }}
          </a-descriptions-item>
          <a-descriptions-item label="开户银行">
            {{ detailPartner.bankName || '-' }}
          </a-descriptions-item>
          <a-descriptions-item label="银行账号">
            {{ detailPartner.bankAccount || '-' }}
          </a-descriptions-item>
          <a-descriptions-item label="资质等级">
            {{ detailPartner.qualificationLevel || '-' }}
          </a-descriptions-item>
          <a-descriptions-item label="风险等级">
            <a-tag v-if="detailPartner.riskLevel" :color="RISK_COLOR[detailPartner.riskLevel]">
              {{ RISK_LABEL[detailPartner.riskLevel] ?? detailPartner.riskLevel }}
            </a-tag>
            <span v-else>-</span>
          </a-descriptions-item>
          <a-descriptions-item label="黑名单">
            <a-tag v-if="detailPartner.blacklistFlag" color="error">黑名单</a-tag>
            <span v-else>-</span>
          </a-descriptions-item>
          <a-descriptions-item label="创建时间">
            {{ detailPartner.createdAt || '-' }}
          </a-descriptions-item>
        </a-descriptions>
      </a-spin>
    </a-modal>
  </div>
</template>

<style scoped>
.lg-none {
  color: var(--muted);
}

.partner-page-head {
  align-items: center;
  justify-content: space-between;
  min-height: 0;
  margin-bottom: 7px;
  padding: 0;
}

.partner-page-meta-row {
  display: flex;
  align-items: center;
  gap: 5em;
  min-width: 0;
}

.partner-breadcrumb {
  font-size: 13px;
  line-height: 20px;
}

.partner-page-subtitle {
  color: var(--text-secondary);
  font-size: 13px;
  line-height: 20px;
  white-space: nowrap;
}

.partner-search-bar {
  margin-top: 21px;
  min-height: 74px;
}

.partner-page .lg-grid {
  margin-top: 14px;
}

.partner-kpi-summary {
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

.partner-kpi-item {
  display: grid;
  grid-template-columns: 38px minmax(0, 1fr);
  grid-template-rows: 20px 30px;
  column-gap: 10px;
  align-content: center;
  min-width: 0;
  padding: 16px 18px;
  border-right: 1px solid var(--border-subtle);
}

.partner-kpi-item:last-child {
  border-right: 0;
}

.partner-kpi-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  border-radius: var(--radius-sm);
  grid-row: 1 / span 2;
}

.partner-kpi-icon.is-blue {
  color: var(--primary);
  background: var(--primary-soft);
}
.partner-kpi-icon.is-cyan {
  color: #0891b2;
  background: #ecfeff;
}
.partner-kpi-icon.is-green {
  color: var(--success);
  background: var(--success-soft);
}
.partner-kpi-icon.is-purple {
  color: #7c3aed;
  background: #f3e8ff;
}
.partner-kpi-icon.is-red {
  color: var(--error);
  background: var(--error-soft);
}

.partner-kpi-label,
.partner-table-count,
.partner-analysis-subtitle {
  color: var(--text-secondary);
  font-size: 13px;
}

.partner-kpi-item strong {
  overflow: hidden;
  color: var(--text);
  font-size: 24px;
  font-weight: 800;
  line-height: 28px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.partner-kpi-item small {
  margin-left: 4px;
  color: var(--text-secondary);
  font-size: 13px;
  font-weight: 600;
}

.partner-table-title {
  color: var(--text);
  font-size: 15px;
  font-weight: 800;
}

.partner-code-link {
  height: auto;
  padding: 0;
  font-weight: 700;
}

.partner-analysis-rail {
  width: 336px;
}

.partner-analysis-panel {
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

.partner-analysis-title {
  color: var(--text);
  font-size: 16px;
  font-weight: 800;
  line-height: 22px;
}

.partner-analysis-section {
  display: flex;
  flex-direction: column;
  gap: 10px;
  min-width: 0;
  padding-top: 16px;
  border-top: 1px solid var(--border-subtle);
}

.partner-section-title {
  color: var(--text);
  font-size: 14px;
  font-weight: 700;
  line-height: 20px;
}

.partner-analysis-section :deep(.lg-type-row),
.partner-analysis-section .lg-type-row {
  grid-template-columns: 9px minmax(54px, 72px) minmax(72px, 1fr) 20px 38px;
}

:global(.compact-partner-modal .ant-modal-body) {
  max-height: calc(100vh - 220px);
  overflow-y: auto;
  padding-top: 14px;
  padding-bottom: 8px;
}

:global(.compact-partner-modal .ant-form-item) {
  margin-bottom: 10px;
}

:global(.compact-partner-detail-modal .ant-modal-body) {
  max-height: calc(100vh - 220px);
  overflow-y: auto;
}

.partner-detail-descriptions :deep(.ant-descriptions-item-label) {
  width: 116px;
  color: var(--text-secondary);
  font-weight: 600;
}
</style>
