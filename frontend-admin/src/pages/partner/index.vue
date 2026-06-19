<script setup lang="ts">
import { ref, reactive, onMounted, computed } from 'vue'
import { message, Modal } from 'ant-design-vue'
import { PlusOutlined, SearchOutlined, ReloadOutlined } from '@ant-design/icons-vue'
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
const partnerTypeLabel = (val: string) => {
  const fromDict = partnerTypeOptions.value.find((o) => o.dictValue === val)
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
    partnerTypeOptions.value = await getDictDataByCode('partner_type')
  } catch (e: unknown) {
    console.error(e)
    partnerTypeOptions.value = [
      { dictLabel: '甲方', dictValue: 'PARTY_A' },
      { dictLabel: '乙方', dictValue: 'PARTY_B' },
      { dictLabel: '其他', dictValue: 'OTHER' },
    ]
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
  { field: 'partnerCode', title: '合作方编号', width: 130, ellipsis: true },
  { field: 'partnerName', title: '合作方名称', minWidth: 140, ellipsis: true },
  { field: 'partnerType', title: '类型', width: 80, slots: { default: 'partnerType' } },
  { field: 'contactName', title: '联系人', width: 90 },
  { field: 'contactPhone', title: '联系电话', width: 120 },
  { field: 'qualificationLevel', title: '资质等级', width: 90 },
  { field: 'blacklistFlag', title: '黑名单', width: 80, slots: { default: 'blacklistFlag' } },
  { field: 'riskLevel', title: '风险等级', width: 90, slots: { default: 'riskLevel' } },
  { field: 'status', title: '状态', width: 80, slots: { default: 'status' } },
  { title: '操作', width: 110, slots: { default: 'ops' } },
])

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
    tableData.value = res.records
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
  <div class="lg-page app-page">
    <div class="lg-page-head">
      <div>
        <a-breadcrumb style="margin-bottom:5px;font-size:13px">
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
        <template #prefix><SearchOutlined style="color: #697380" /></template>
      </a-input>
      <a-button type="primary" size="large" @click="handleSearch">查询</a-button>
      <a-button size="large" @click="handleReset">
        <template #icon><ReloadOutlined /></template>
        重置
      </a-button>
    </div>

    <!-- 工具栏 -->
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
      <div class="lg-toolbar-right">
        <a-select
          v-model:value="filter.partnerType"
          placeholder="全部类型"
          allow-clear
          style="width: 130px"
          size="small"
          @change="handleSearch"
        >
          <a-select-option
            v-for="opt in partnerTypeOptions"
            :key="opt.dictValue"
            :value="opt.dictValue"
          >
            {{ opt.dictLabel }}
          </a-select-option>
        </a-select>
        <a-select
          v-model:value="filter.status"
          placeholder="全部状态"
          allow-clear
          style="width: 110px"
          size="small"
          @change="handleSearch"
        >
          <a-select-option value="ENABLE">启用</a-select-option>
          <a-select-option value="DISABLE">禁用</a-select-option>
        </a-select>
      </div>
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
          <div class="lg-ops">
            <a class="lg-link" @click="handleEdit(row)">编辑</a>
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
      :confirm-loading="formLoading"
      :mask-closable="false"
      ok-text="保存"
      cancel-text="取消"
      width="600px"
      @ok="handleModalOk"
      @cancel="handleModalCancel"
    >
      <a-form :label-col="{ span: 6 }" :wrapper-col="{ span: 16 }">
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
