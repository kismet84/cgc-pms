<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { message } from 'ant-design-vue'
import { getPartnerList } from '@/api/modules/partner'
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

const TYPE_LABEL: Record<string, string> = {
  SUPPLIER: '供应商',
  SUB: '分包商',
  DESIGN: '设计单位',
  SUPERVISOR: '监理单位',
  OTHER: '其他',
}
const TYPE_COLOR: Record<string, string> = {
  SUPPLIER: 'blue',
  SUB: 'green',
  DESIGN: 'purple',
  SUPERVISOR: 'cyan',
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
  } catch {
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
            <a-select-option value="SUPPLIER">供应商</a-select-option>
            <a-select-option value="SUB">分包商</a-select-option>
            <a-select-option value="DESIGN">设计单位</a-select-option>
            <a-select-option value="SUPERVISOR">监理单位</a-select-option>
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
            <a-select-option value="ENABLED">启用</a-select-option>
            <a-select-option value="DISABLED">禁用</a-select-option>
          </a-select>
        </div>
        <div class="pm-filter-actions">
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
        :scroll="{ x: 1000 }"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'partnerName'">
            <a class="pm-link">{{ record.partnerName }}</a>
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
            <a-tag :color="record.status === 'ENABLED' ? 'success' : 'default'">
              {{ record.status === 'ENABLED' ? '启用' : '禁用' }}
            </a-tag>
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
</style>
