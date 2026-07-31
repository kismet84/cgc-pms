<script setup lang="ts">
import { computed } from 'vue'
import type { CostManagerLedgerRow } from '@/types/dashboard'

defineOptions({ name: 'DashboardCostLedgerPanel' })

type LedgerTab = 'cost' | 'contract' | 'fund'

const props = defineProps<{
  activeLedgerTab: LedgerTab
  subjectFilter: string
  statusFilter: string
  ledgerKeyword: string
  subjectOptions: string[]
  statusOptions: string[]
  pagedLedgerRows: string[][]
  pagedLedgerRecords: CostManagerLedgerRow[]
  ledgerTotal: number
  pageSize: number
  currentPage: number
  ledgerStatusLabel: (status: string | undefined) => string
  statusTagColor: (label: string) => string
}>()

const emit = defineEmits<{
  (e: 'update:activeLedgerTab', value: LedgerTab): void
  (e: 'update:subjectFilter', value: string): void
  (e: 'update:statusFilter', value: string): void
  (e: 'update:ledgerKeyword', value: string): void
  (e: 'update:pageSize', value: number): void
  (e: 'update:currentPage', value: number): void
  (e: 'reset'): void
  (e: 'export'): void
  (e: 'view', row: CostManagerLedgerRow | undefined): void
  (e: 'drill', row: CostManagerLedgerRow | undefined): void
}>()

const activeLedgerTabModel = computed({
  get: () => props.activeLedgerTab,
  set: (value: LedgerTab) => emit('update:activeLedgerTab', value),
})
const subjectFilterModel = computed({
  get: () => props.subjectFilter,
  set: (value: string) => emit('update:subjectFilter', value),
})
const statusFilterModel = computed({
  get: () => props.statusFilter,
  set: (value: string) => emit('update:statusFilter', value),
})
const ledgerKeywordModel = computed({
  get: () => props.ledgerKeyword,
  set: (value: string) => emit('update:ledgerKeyword', value),
})
const pageSizeModel = computed({
  get: () => props.pageSize,
  set: (value: number) => emit('update:pageSize', value),
})
const currentPageModel = computed({
  get: () => props.currentPage,
  set: (value: number) => emit('update:currentPage', value),
})
</script>

<template>
  <section class="cost-reference-panel cost-ledger-reference">
    <div class="cost-ledger-tabs">
      <a :class="{ active: activeLedgerTabModel === 'cost' }" @click="activeLedgerTabModel = 'cost'"
        >成本列表</a
      >
      <a
        :class="{ active: activeLedgerTabModel === 'contract' }"
        @click="activeLedgerTabModel = 'contract'"
        >合同执行</a
      >
      <a :class="{ active: activeLedgerTabModel === 'fund' }" @click="activeLedgerTabModel = 'fund'"
        >资金流水</a
      >
    </div>
    <div class="cost-ledger-tools">
      <a-select v-model:value="subjectFilterModel" size="small" style="width: 96px">
        <a-select-option value="all">全部科目</a-select-option>
        <a-select-option v-for="subject in subjectOptions" :key="subject" :value="subject">
          {{ subject }}
        </a-select-option>
      </a-select>
      <a-select v-model:value="statusFilterModel" size="small" style="width: 96px">
        <a-select-option value="all">全部状态</a-select-option>
        <a-select-option v-for="status in statusOptions" :key="status" :value="status">
          {{ ledgerStatusLabel(status) }}
        </a-select-option>
      </a-select>
      <a-range-picker size="small" value-format="YYYY-MM-DD" disabled />
      <a-input
        v-model:value="ledgerKeywordModel"
        size="small"
        placeholder="请输入合同名称/编号"
        style="width: 210px"
      />
      <a-button size="small" @click="emit('reset')">重置</a-button>
      <a-button size="small" type="primary" ghost @click="emit('export')">导出</a-button>
    </div>
    <table class="cost-ledger-table">
      <thead>
        <tr>
          <th>序号</th>
          <th>成本科目</th>
          <th>合同编号</th>
          <th>合同名称</th>
          <th>预算金额（万元）</th>
          <th>累计发生（万元）</th>
          <th>完成量</th>
          <th>偏差（万元）</th>
          <th>偏差率</th>
          <th>状态</th>
          <th>责任人</th>
          <th>操作</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="(row, rowIndex) in pagedLedgerRows" :key="`${row[0]}-${row[2]}-${row[3]}`">
          <td v-for="(cell, cellIndex) in row" :key="cellIndex">
            <a-tag v-if="cellIndex === 9" :color="statusTagColor(cell)">{{ cell }}</a-tag>
            <template v-else>{{ cell }}</template>
          </td>
          <td class="cost-ledger-actions">
            <a @click="emit('view', pagedLedgerRecords[rowIndex])">查看</a>
            <a @click="emit('drill', pagedLedgerRecords[rowIndex])">下钻</a>
          </td>
        </tr>
      </tbody>
    </table>
    <div class="cost-ledger-pagination">
      <span>共 {{ ledgerTotal }} 条</span>
      <a-select v-model:value="pageSizeModel" size="small" style="width: 88px">
        <a-select-option :value="10">10条/页</a-select-option>
        <a-select-option :value="20">20条/页</a-select-option>
        <a-select-option :value="50">50条/页</a-select-option>
      </a-select>
      <a-pagination
        v-model:current="currentPageModel"
        :total="ledgerTotal"
        :page-size="pageSizeModel"
        size="small"
      />
      <span>前往</span>
      <a-input-number v-model:value="currentPageModel" :min="1" size="small" style="width: 56px" />
      <span>页</span>
    </div>
  </section>
</template>

<style scoped>
.cost-reference-panel {
  min-width: 0;
  background: #fff;
  border: 1px solid #e4eaf3;
  border-radius: 6px;
  box-shadow: 0 2px 8px rgba(15, 23, 42, 0.03);
  overflow: hidden;
}

.cost-ledger-reference {
  position: relative;
  padding-top: 43px;
}

.cost-ledger-tabs {
  position: absolute;
  top: 0;
  left: 16px;
  height: 43px;
  display: flex;
  align-items: flex-end;
  gap: 28px;
}

.cost-ledger-tabs a {
  height: 35px;
  color: #334155;
  display: grid;
  align-items: center;
  font-size: 14px;
  font-weight: 700;
}

.cost-ledger-tabs a.active {
  color: #1677ff;
  border-bottom: 2px solid #1677ff;
}

.cost-ledger-tools {
  position: absolute;
  top: 8px;
  right: 16px;
  display: flex;
  align-items: center;
  gap: 8px;
}

.cost-ledger-tools :deep(.ant-select-selector),
.cost-ledger-tools :deep(.ant-picker),
.cost-ledger-tools :deep(.ant-input),
.cost-ledger-tools :deep(.ant-btn) {
  min-height: 26px;
  height: 26px;
  line-height: 24px;
}

.cost-ledger-tools :deep(.ant-select-selection-item),
.cost-ledger-tools :deep(.ant-select-selection-placeholder),
.cost-ledger-tools :deep(.ant-picker-input > input),
.cost-ledger-tools :deep(.ant-input),
.cost-ledger-tools :deep(.ant-btn) {
  font-size: 12px;
}

.cost-ledger-tools :deep(.ant-select-single.ant-select-sm .ant-select-selector) {
  padding-top: 0;
  padding-bottom: 0;
}

.cost-ledger-table {
  width: 100%;
  border-collapse: collapse;
  table-layout: fixed;
  color: #243044;
  font-size: 12px;
}

.cost-ledger-table th,
.cost-ledger-table td {
  height: 30px;
  padding: 0 8px;
  border-bottom: 1px solid #edf1f7;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  text-align: left;
}

.cost-ledger-table th {
  color: #536176;
  background: #fbfcff;
  font-weight: 700;
}

.cost-ledger-table td:first-child,
.cost-ledger-table th:first-child,
.cost-ledger-table th:last-child,
.cost-ledger-table .cost-ledger-actions {
  text-align: center;
}

.cost-ledger-table th:nth-child(1) {
  width: 48px;
}

.cost-ledger-table th:nth-child(2) {
  width: 150px;
}

.cost-ledger-table th:nth-child(3) {
  width: 120px;
}

.cost-ledger-table th:nth-child(4) {
  width: 230px;
}

.cost-ledger-table th:nth-child(5),
.cost-ledger-table th:nth-child(6),
.cost-ledger-table th:nth-child(8) {
  width: 116px;
}

.cost-ledger-table th:nth-child(7),
.cost-ledger-table th:nth-child(9),
.cost-ledger-table th:nth-child(10),
.cost-ledger-table th:nth-child(11),
.cost-ledger-table th:nth-child(12) {
  width: 78px;
}

.cost-ledger-actions {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
}

.cost-ledger-table td.cost-ledger-actions {
  text-align: center;
}

.cost-ledger-actions a {
  color: #1677ff;
}

.cost-ledger-pagination {
  min-height: 54px;
  padding: 10px 18px;
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 12px;
  color: #536176;
  font-size: 12px;
}

.cost-ledger-pagination :deep(.ant-select-single.ant-select-sm),
.cost-ledger-pagination :deep(.ant-pagination-item),
.cost-ledger-pagination :deep(.ant-pagination-prev),
.cost-ledger-pagination :deep(.ant-pagination-next),
.cost-ledger-pagination :deep(.ant-input-number-sm) {
  height: 22px;
  line-height: 20px;
}

.cost-ledger-pagination :deep(.ant-select-single.ant-select-sm .ant-select-selector),
.cost-ledger-pagination :deep(.ant-input-number-sm input) {
  height: 22px;
  line-height: 20px;
}

.cost-ledger-pagination :deep(.ant-select-single.ant-select-sm .ant-select-selection-item) {
  line-height: 20px;
}

@media (max-width: 1280px) {
  .cost-ledger-tools {
    position: static;
    padding: 0 16px 10px;
    flex-wrap: wrap;
  }

  .cost-ledger-reference {
    padding-top: 43px;
  }
}
</style>
