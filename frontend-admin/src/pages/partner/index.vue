<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { message, Modal } from 'ant-design-vue'
import { PlusOutlined } from '@ant-design/icons-vue'
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

const TYPE_LABEL: Record<string, string> = {
  PARTY_A: '甲方',
  PARTY_B: '乙方',
  OTHER: '其他',
}

const TYPE_COLOR: Record<string, string> = {
  PARTY_A: 'blue',
  PARTY_B: 'green',
  OTHER: 'default',
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

const columns = [
  { title: '合作方编号', dataIndex: 'partnerCode', width: 150 },
  { title: '合作方名称', dataIndex: 'partnerName', minWidth: 160, key: 'partnerName' },
  { title: '类型', dataIndex: 'partnerType', width: 110, key: 'partnerType' },
  { title: '联系人', dataIndex: 'contactName', width: 100 },
  { title: '联系电话', dataIndex: 'contactPhone', width: 130 },
  { title: '资质等级', dataIndex: 'qualificationLevel', width: 100 },
  { title: '黑名单', dataIndex: 'blacklistFlag', width: 90, key: 'blacklistFlag' },
  { title: '风险等级', dataIndex: 'riskLevel', width: 100, key: 'riskLevel' },
  { title: '状态', dataIndex: 'status', width: 90, key: 'status' },
  { title: '操作', dataIndex: 'ops', width: 140, fixed: 'right' as const },
]

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
    partnerCode: '', partnerName: '', partnerType: undefined, creditCode: '',
    legalPerson: '', contactName: '', contactPhone: '', bankName: '',
    bankAccount: '', qualificationLevel: '', blacklistFlag: false,
    riskLevel: undefined, status: 'ENABLE',
  })
  modalVisible.value = true
}

function handleEdit(record: PartnerVO) {
  modalTitle.value = '编辑合作方'
  editingId.value = record.id
  Object.assign(formData, {
    partnerCode: record.partnerCode || '', partnerName: record.partnerName || '',
    partnerType: record.partnerType || undefined, creditCode: record.creditCode || '',
    legalPerson: record.legalPerson || '', contactName: record.contactName || '',
    contactPhone: record.contactPhone || '', bankName: record.bankName || '',
    bankAccount: record.bankAccount || '', qualificationLevel: record.qualificationLevel || '',
    blacklistFlag: record.blacklistFlag, riskLevel: record.riskLevel || undefined,
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

onMounted(fetchData)
</script>

<template>
  <div class="pm-page">
    <a-page-header title="合作方管理" class="pm-header" />

    <!-- Filter -->
    <div class="pm-card pm-filter">
      <div class="pm-filter-row">
        <div class="pm-field">
          <label>合作方编号：</label>
          <a-input
            v-model:value="filter.partnerCode"
            placeholder="请输入编号"
            style="width: 160px"
            allow-clear
          />
        </div>
        <div class="pm-field">
          <label>合作方名称：</label>
          <a-input
            v-model:value="filter.partnerName"
            placeholder="请输入名称"
            style="width: 160px"
            allow-clear
          />
        </div>
        <div class="pm-field">
          <label>类型：</label>
          <a-select
            v-model:value="filter.partnerType"
            placeholder="全部"
            allow-clear
            style="width: 130px"
          >
            <a-select-option value="PARTY_A">甲方</a-select-option>
            <a-select-option value="PARTY_B">乙方</a-select-option>
            <a-select-option value="OTHER">其他</a-select-option>

          </a-select>
        </div>
        <div class="pm-field">
          <label>状态：</label>
          <a-select
            v-model:value="filter.status"
            placeholder="全部"
            allow-clear
            style="width: 110px"
          >
            <a-select-option value="ENABLE">启用</a-select-option>
            <a-select-option value="DISABLE">禁用</a-select-option>
          </a-select>
        </div>
        <div class="pm-filter-actions">
          <a-button type="primary" @click="handleAdd"><PlusOutlined />新建合作方</a-button>
          <a-button type="primary" @click="handleSearch">查询</a-button>
          <a-button @click="handleReset">重置</a-button>
        </div>
      </div>
    </div>

    <!-- Table -->
    <div class="pm-card pm-table-wrap">
      <a-table
        :columns="columns"
        :data-source="tableData"
        :loading="loading"
        :pagination="false"
        row-key="id"
        size="small"
        :scroll="{ x: 1200 }"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'partnerName'">
            <span>{{ record.partnerName }}</span>
          </template>
          <template v-else-if="column.key === 'partnerType'">
            <a-tag :color="TYPE_COLOR[record.partnerType]">
              {{ TYPE_LABEL[record.partnerType] ?? record.partnerType }}
            </a-tag>
          </template>
          <template v-else-if="column.key === 'blacklistFlag'">
            <a-tag v-if="record.blacklistFlag" color="error">黑名单</a-tag>
            <span v-else class="pm-none">-</span>
          </template>
          <template v-else-if="column.key === 'riskLevel'">
            <a-tag :color="RISK_COLOR[record.riskLevel]">
              {{ RISK_LABEL[record.riskLevel] ?? record.riskLevel }}
            </a-tag>
          </template>
          <template v-else-if="column.key === 'status'">
            <a-tag :color="record.status === 'ENABLE' ? 'success' : 'default'">
              {{ record.status === 'ENABLE' ? '启用' : '禁用' }}
            </a-tag>
          </template>
          <template v-else-if="column.dataIndex === 'ops'">
            <div class="pm-ops">
              <a class="pm-link" @click="handleEdit(record)">编辑</a>
              <a class="pm-link" style="color: #ff4d4f" @click="handleDelete(record)">删除</a>
            </div>
          </template>
        </template>
      </a-table>
    </div>

    <!-- Pagination -->
    <div class="pm-pagination">
      <span class="pm-total">共 {{ total }} 条</span>
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
            <a-select-option value="PARTY_A">甲方</a-select-option>
            <a-select-option value="PARTY_B">乙方</a-select-option>
            <a-select-option value="OTHER">其他</a-select-option>

          </a-select>
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
.pm-page {
  background: #f6f8fc;
  min-height: 100%;
  padding: 4px 0;
}
.pm-header {
  background: transparent;
  padding-bottom: 12px;
}
.pm-card {
  background: #fff;
  border: 1px solid #e5eaf3;
  border-radius: 10px;
  box-shadow: 0 10px 30px rgba(17, 24, 39, 0.05);
}
.pm-filter {
  padding: 20px 22px;
  margin-bottom: 14px;
}
.pm-filter-row {
  display: flex;
  flex-wrap: wrap;
  gap: 12px 24px;
  align-items: center;
}
.pm-field {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  white-space: nowrap;
}
.pm-field label {
  color: #374151;
}
.pm-filter-actions {
  display: flex;
  gap: 10px;
  margin-left: auto;
}
.pm-table-wrap {
  overflow: hidden;
  margin-bottom: 0;
}
.pm-link {
  color: #1677ff;
  font-weight: 500;
  cursor: pointer;
  text-decoration: none;
}
.pm-none {
  color: #9ca3af;
}
.pm-pagination {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 12px;
  padding: 12px 0 0;
}
.pm-total {
  font-size: 13px;
  color: #4b5563;
}
.pm-ops {
  display: flex;
  gap: 10px;
}
</style>
