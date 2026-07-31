<script setup lang="ts">
import { computed, ref } from 'vue'
import {
  DollarOutlined,
  FilterOutlined,
  LockOutlined,
  ToolOutlined,
  ReloadOutlined,
  SearchOutlined,
  AlertOutlined,
} from '@ant-design/icons-vue'
import type { DictDataVO } from '@/types/dict'

const mobileFiltersOpen = ref(false)

interface KpiStats {
  total: number
  locked: number
  actual: number
  dynamic: number
  deviation: number
}

interface FilterState {
  projectId?: string
  contractId?: string
  partnerId?: string
  costSubjectId?: string
  costType?: string
  sourceType?: string
  costStatus?: string
  dateRange: string[] | null
  keyword: string
}

interface FilterVisibility {
  projectId: boolean
  contractId: boolean
  partnerId: boolean
  costSubjectId: boolean
  costType: boolean
  sourceType: boolean
  costStatus: boolean
  dateRange: boolean
}

interface OptionItem {
  id: string
  projectName?: string
  contractName?: string
  partnerName?: string
  subjectName?: string
}

interface FilterSettingItem {
  key: keyof FilterVisibility
  label: string
}

const props = defineProps<{
  isMobile: boolean
  kpiStats: KpiStats
  filter: FilterState
  filterVisibility: FilterVisibility
  filterSettingItems: readonly FilterSettingItem[]
  projectList: OptionItem[]
  contractOptions: OptionItem[]
  partnerList: OptionItem[]
  costSubjectOptions: OptionItem[]
  costTypeOptions: DictDataVO[]
  sourceTypeOptions: DictDataVO[]
  costStatusOptions: DictDataVO[]
  fmtWan: (val: string | undefined) => string
  barPercent: (amount: string) => string
  handleSearch: () => void
  handleReset: () => void
  handleProjectFilterChange: (val: string | undefined) => void | Promise<void>
  toggleFilterVisibility: (key: keyof FilterVisibility) => void
}>()

const mobileKpiItems = computed(() => [
  {
    icon: DollarOutlined,
    bg: 'var(--kpi-total)',
    label: '成本总额',
    value: props.fmtWan(String(props.kpiStats.total)),
    unit: '万元',
  },
  {
    icon: LockOutlined,
    bg: 'var(--kpi-amount)',
    label: '锁定成本',
    value: props.fmtWan(String(props.kpiStats.locked)),
    unit: '万元',
  },
  {
    icon: ToolOutlined,
    bg: 'var(--kpi-paid)',
    label: '动态成本',
    value: props.fmtWan(String(props.kpiStats.dynamic)),
    unit: '万元',
  },
  {
    icon: AlertOutlined,
    bg: 'var(--kpi-overdue)',
    label: '偏差金额',
    value: props.fmtWan(String(props.kpiStats.deviation)),
    unit: '万元',
  },
])
</script>

<template>
  <section
    v-if="!isMobile"
    class="lg-kpi-strip cost-ledger-kpi-summary project-operation-kpi"
    aria-label="成本关键指标"
  >
    <div class="cost-ledger-kpi-item">
      <span class="cost-ledger-kpi-icon is-total"><DollarOutlined /></span>
      <span class="cost-ledger-kpi-label">成本总额</span>
      <span class="cost-ledger-kpi-value">
        {{ fmtWan(String(kpiStats.total)) }} <small>万元</small>
      </span>
    </div>
    <div class="cost-ledger-kpi-item is-wide">
      <span class="cost-ledger-kpi-icon is-amount"><LockOutlined /></span>
      <span class="cost-ledger-kpi-label">锁定成本</span>
      <span class="cost-ledger-kpi-value">
        {{ fmtWan(String(kpiStats.locked)) }} <small>万元</small>
      </span>
    </div>
    <div class="cost-ledger-kpi-item is-progress">
      <span class="cost-ledger-kpi-icon is-paid"><ToolOutlined /></span>
      <span class="cost-ledger-kpi-label">实际成本</span>
      <span class="cost-ledger-kpi-value">
        {{ fmtWan(String(kpiStats.actual)) }} <small>万元</small>
      </span>
      <span class="cost-ledger-kpi-progress">
        <span :style="{ width: barPercent(String(kpiStats.actual)) }"></span>
      </span>
    </div>
    <div class="cost-ledger-kpi-item is-progress is-dynamic">
      <span class="cost-ledger-kpi-icon is-unpaid"><ToolOutlined /></span>
      <span class="cost-ledger-kpi-label">动态成本</span>
      <span class="cost-ledger-kpi-value">
        {{ fmtWan(String(kpiStats.dynamic)) }} <small>万元</small>
      </span>
      <span class="cost-ledger-kpi-progress">
        <span :style="{ width: barPercent(String(kpiStats.dynamic)) }"></span>
      </span>
    </div>
    <div class="cost-ledger-kpi-item is-overdue">
      <span class="cost-ledger-kpi-icon is-overdue"><AlertOutlined /></span>
      <span class="cost-ledger-kpi-label">偏差金额</span>
      <span class="cost-ledger-kpi-value">
        {{ fmtWan(String(kpiStats.deviation)) }} <small>万元</small>
      </span>
    </div>
  </section>
  <div v-else class="lg-kpi-single">
    <div v-for="item in mobileKpiItems" :key="item.label" class="lg-kpi-single-row">
      <div class="lg-kpi-single-icon" :style="{ background: item.bg }">
        <component :is="item.icon" />
      </div>
      <span class="lg-kpi-single-label">{{ item.label }}</span>
      <span class="lg-kpi-single-value"
        >{{ item.value }} <small>{{ item.unit }}</small></span
      >
    </div>
  </div>

  <div class="lg-search-bar cost-ledger-query-panel project-operation-query-panel">
    <div
      id="cost-ledger-filter-panel"
      class="cost-ledger-query-primary project-operation-filter-panel"
      :class="{ 'is-open': mobileFiltersOpen }"
    >
      <a-select
        v-if="filterVisibility.projectId"
        v-model:value="filter.projectId"
        placeholder="全部项目"
        allow-clear
        class="cost-ledger-query-select"
        size="large"
        @change="handleProjectFilterChange"
      >
        <a-select-option v-for="p in projectList" :key="p.id" :value="p.id">
          {{ p.projectName }}
        </a-select-option>
      </a-select>
      <a-select
        v-if="filterVisibility.contractId"
        v-model:value="filter.contractId"
        placeholder="合同"
        allow-clear
        class="cost-ledger-query-select"
        size="large"
        @change="handleSearch"
      >
        <a-select-option
          v-for="contract in contractOptions"
          :key="contract.id"
          :value="contract.id"
        >
          {{ contract.contractName }}
        </a-select-option>
      </a-select>
      <a-select
        v-if="filterVisibility.partnerId"
        v-model:value="filter.partnerId"
        placeholder="合作方"
        allow-clear
        class="cost-ledger-query-select"
        size="large"
        @change="handleSearch"
      >
        <a-select-option v-for="partner in partnerList ?? []" :key="partner.id" :value="partner.id">
          {{ partner.partnerName }}
        </a-select-option>
      </a-select>
      <a-select
        v-if="filterVisibility.costSubjectId"
        v-model:value="filter.costSubjectId"
        placeholder="成本科目"
        allow-clear
        class="cost-ledger-query-select"
        size="large"
        @change="handleSearch"
      >
        <a-select-option
          v-for="subject in costSubjectOptions"
          :key="subject.id"
          :value="subject.id"
        >
          {{ subject.subjectName }}
        </a-select-option>
      </a-select>
      <a-select
        v-if="filterVisibility.costType"
        v-model:value="filter.costType"
        placeholder="成本类型"
        allow-clear
        class="cost-ledger-query-select"
        size="large"
        @change="handleSearch"
      >
        <a-select-option
          v-for="item in costTypeOptions"
          :key="item.dictValue"
          :value="item.dictValue"
        >
          {{ item.dictLabel }}
        </a-select-option>
      </a-select>
      <a-select
        v-if="filterVisibility.sourceType"
        v-model:value="filter.sourceType"
        placeholder="来源类型"
        allow-clear
        class="cost-ledger-query-select"
        size="large"
        @change="handleSearch"
      >
        <a-select-option
          v-for="item in sourceTypeOptions"
          :key="item.dictValue"
          :value="item.dictValue"
        >
          {{ item.dictLabel }}
        </a-select-option>
      </a-select>
      <a-select
        v-if="filterVisibility.costStatus"
        v-model:value="filter.costStatus"
        placeholder="成本状态"
        allow-clear
        class="cost-ledger-status-select"
        size="large"
        @change="handleSearch"
      >
        <a-select-option
          v-for="item in costStatusOptions"
          :key="item.dictValue"
          :value="item.dictValue"
        >
          {{ item.dictLabel }}
        </a-select-option>
      </a-select>
      <a-range-picker
        v-if="filterVisibility.dateRange"
        v-model:value="filter.dateRange"
        class="cost-ledger-query-range"
        size="large"
        value-format="YYYY-MM-DD"
        @change="handleSearch"
      />
    </div>
    <div class="cost-ledger-query-keyword-row">
      <a-input
        v-model:value="filter.keyword"
        class="cost-ledger-keyword-search"
        placeholder="搜索编号、科目名、类型、项目、合同…"
        allow-clear
        size="large"
        @press-enter="handleSearch"
      >
        <template #prefix><SearchOutlined class="cost-ledger-search-prefix-icon" /></template>
      </a-input>
      <div class="cost-ledger-query-actions">
        <a-button
          class="project-operation-desktop-query-action"
          type="primary"
          size="large"
          @click="handleSearch"
          >搜索</a-button
        >
        <a-button class="project-operation-desktop-query-action" size="large" @click="handleReset">
          <template #icon><ReloadOutlined /></template>
          重置
        </a-button>
        <a-button
          class="project-operation-filter-toggle"
          size="large"
          :aria-expanded="mobileFiltersOpen"
          aria-controls="cost-ledger-filter-panel"
          @click="mobileFiltersOpen = !mobileFiltersOpen"
        >
          <template #icon><FilterOutlined /></template>筛选
        </a-button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.cost-ledger-query-panel {
  align-items: stretch;
  flex-direction: column;
  gap: 12px;
  margin: 0;
}

.cost-ledger-query-primary {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.cost-ledger-query-keyword-row {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.cost-ledger-keyword-search {
  flex: 1 1 auto;
  min-width: 320px;
}

.cost-ledger-search-prefix-icon {
  color: var(--text-secondary);
}

.cost-ledger-query-select {
  width: 160px;
}

.cost-ledger-status-select {
  width: 132px;
}

.cost-ledger-query-range {
  width: 260px;
}

.cost-ledger-query-actions {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  gap: 8px;
}

.cost-ledger-kpi-summary {
  display: grid;
  grid-template-columns: 1fr 1.25fr 1.15fr 1.15fr 1fr;
  gap: 0;
  margin-bottom: 0;
  overflow: hidden;
  background: var(--surface);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-soft);
}

.cost-ledger-kpi-item {
  position: relative;
  display: grid;
  grid-template-columns: 38px minmax(0, 1fr);
  grid-template-rows: 19px 27px 8px;
  column-gap: 10px;
  align-items: center;
  min-width: 0;
  padding: 16px 18px;
  border-right: 1px solid var(--border-subtle);
}

.cost-ledger-kpi-item:last-child {
  border-right: 0;
}

.cost-ledger-kpi-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  color: var(--primary);
  background: var(--primary-soft);
  border-radius: var(--radius-sm);
  grid-row: 1 / span 2;
}

.cost-ledger-kpi-icon.is-amount {
  color: var(--warning);
  background: var(--warning-soft);
}

.cost-ledger-kpi-icon.is-paid {
  color: var(--success);
  background: var(--success-soft);
}

.cost-ledger-kpi-icon.is-unpaid {
  color: var(--primary);
  background: var(--surface-tint);
}

.cost-ledger-kpi-icon.is-overdue {
  color: var(--error);
  background: var(--error-soft);
}

.cost-ledger-kpi-label {
  overflow: hidden;
  color: var(--text-secondary);
  font-size: 13px;
  font-weight: 600;
  line-height: 18px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.cost-ledger-kpi-value {
  overflow: hidden;
  color: var(--text);
  font-size: 24px;
  font-weight: 800;
  line-height: 28px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.cost-ledger-kpi-value small {
  margin-left: 4px;
  color: var(--text-secondary);
  font-size: 13px;
  font-weight: 600;
}

.cost-ledger-kpi-progress {
  display: block;
  overflow: hidden;
  height: 4px;
  background: var(--surface-subtle);
  border-radius: var(--radius-sm);
  grid-column: 2;
}

.cost-ledger-kpi-progress > span {
  display: block;
  height: 100%;
  background: var(--kpi-paid);
  border-radius: var(--radius-sm);
}

.cost-ledger-kpi-item.is-dynamic .cost-ledger-kpi-progress > span {
  background: var(--kpi-unpaid);
}

@media (max-width: 1200px) {
  .cost-ledger-query-panel,
  .cost-ledger-query-keyword-row,
  .cost-ledger-query-primary {
    align-items: stretch;
    flex-direction: column;
  }

  .cost-ledger-query-actions {
    justify-content: flex-start;
  }

  .cost-ledger-keyword-search,
  .cost-ledger-query-select,
  .cost-ledger-status-select,
  .cost-ledger-query-range {
    width: 100%;
    min-width: 0;
  }

  .cost-ledger-kpi-summary {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .cost-ledger-kpi-item {
    border-bottom: 1px solid var(--border-subtle);
  }
}
</style>
