<script setup lang="ts">
import { ref, reactive, onMounted, computed } from 'vue'
import { message, Modal } from 'ant-design-vue'
import { MoreOutlined, PlusOutlined, SearchOutlined, ReloadOutlined } from '@ant-design/icons-vue'
import { getDictDataByCode } from '@/api/modules/dict'
import { getPartnerList, createPartner, updatePartner, deletePartner } from '@/api/modules/partner'
import type { PartnerVO } from '@/types/partner'

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
function normalizeArray<T>(value: unknown): T[] {
  if (Array.isArray(value)) return value as T[]
  if (value && typeof value === 'object') {
    const records = (value as { records?: unknown }).records
    if (Array.isArray(records)) return records as T[]
  }
  return []
}
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
  { field: 'partnerCode', title: '合作方编号', minWidth: 140, ellipsis: true },
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

const partnerStats = computed(() => ({
  total: total.value,
  partyA: tableData.value.filter((item) => item.partnerType === 'PARTY_A').length,
  partyB: tableData.value.filter((item) => item.partnerType === 'PARTY_B').length,
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
    total.value = res.total
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
  <div class="lg-list-page lg-page app-page">
    <div class="lg-page-head">
      <div>
        <a-breadcrumb style="margin-bottom: 5px; font-size: 13px">
          <a-breadcrumb-item>合作方管理</a-breadcrumb-item>
        </a-breadcrumb>
      </div>
    </div>

    <!-- 搜索栏 -->
    <div class="lg-search-bar">
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
        <div class="lg-kpi-strip">
          <div class="lg-kpi-card">
            <span class="lg-kpi-card-label">合作方总数</span>
            <span class="lg-kpi-card-value">{{ partnerStats.total }} <small>个</small></span>
          </div>
          <div class="lg-kpi-card">
            <span class="lg-kpi-card-label">甲方单位</span>
            <span class="lg-kpi-card-value">{{ partnerStats.partyA }} <small>个</small></span>
          </div>
          <div class="lg-kpi-card">
            <span class="lg-kpi-card-label">乙方单位</span>
            <span class="lg-kpi-card-value">{{ partnerStats.partyB }} <small>个</small></span>
          </div>
          <div class="lg-kpi-card is-warn">
            <span class="lg-kpi-card-label">风险合作方</span>
            <span class="lg-kpi-card-value">{{ partnerStats.risk }} <small>个</small></span>
          </div>
        </div>

        <main class="lg-list-table-panel">
          <div class="lg-toolbar">
            <div class="lg-toolbar-left">
              <a-button type="primary" @click="handleAdd">
                <template #icon><PlusOutlined /></template>
                新建合作方
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

      <aside class="lg-analysis-rail">
        <section class="lg-panel">
          <div class="lg-panel-title">合作方类型分布</div>
          <div class="lg-type-list">
            <div v-for="item in partnerTypeSummary" :key="item.key" class="lg-type-row">
              <span class="lg-type-dot" :style="{ background: item.color }"></span>
              <span class="lg-type-label">{{ item.label }}</span>
              <span style="margin-left: auto">{{ item.count }} 个</span>
            </div>
          </div>
        </section>
        <section class="lg-panel">
          <div class="lg-panel-title">近期合作方</div>
          <div class="lg-type-list">
            <div v-for="item in recentPartners" :key="item.id" class="lg-type-row">
              <span class="lg-type-dot" style="background: #1890ff"></span>
              <span class="lg-type-label">{{ item.partnerName }}</span>
            </div>
            <div v-if="!recentPartners.length" class="lg-warning-empty">暂无合作方</div>
          </div>
        </section>
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
      width="640px"
      @ok="handleModalOk"
      @cancel="handleModalCancel"
    >
      <a-form>
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
  </div>
</template>

<style scoped>
/* 页面专属样式 — 其余已由 lg-* 全局类覆盖 */
.lg-none {
  color: var(--muted);
}
</style>
