<script setup lang="ts">
import { ref } from 'vue'
import { FilterOutlined, ReloadOutlined, SearchOutlined } from '@ant-design/icons-vue'
import type { SelectOption } from '@/types/ui'

defineProps<{
  filter: {
    projectId?: string
    contractId?: string
    partnerId?: string
    orderStatus?: string
    orderType?: string
    keyword: string
  }
  projectList: Array<{ id: string; projectName?: string }>
  contractList: Array<{ id: string; contractName?: string }>
  supplierList: Array<{ id: string; partnerName?: string }>
  orderTypeLabel: Record<string, string>
  orderStatusLabelMap: Record<string, string>
  orderStatusLabel: (status: string | undefined) => string
  onProjectChange: (value: string | undefined) => void
  onSearch: () => void
  onReset: () => void
}>()

const filterPanelOpen = ref(false)
</script>

<template>
  <div class="lg-search-bar procurement-subcontract-query-panel">
    <div
      class="purchase-order-search-fields procurement-subcontract-filter-panel"
      :class="{ 'is-open': filterPanelOpen }"
    >
      <a-select
        v-model:value="filter.projectId"
        class="purchase-order-search-select"
        placeholder="全部项目"
        allow-clear
        size="large"
        @change="onProjectChange"
      >
        <a-select-option v-for="p in projectList" :key="p.id" :value="p.id">
          {{ p.projectName }}
        </a-select-option>
      </a-select>
      <a-select
        v-model:value="filter.contractId"
        class="purchase-order-search-select"
        placeholder="全部合同"
        allow-clear
        show-search
        size="large"
        :filter-option="
          (input: string, option: SelectOption) =>
            option.label?.toLowerCase().includes(input.toLowerCase())
        "
        @change="onSearch"
      >
        <a-select-option v-for="c in contractList" :key="c.id" :value="c.id">
          {{ c.contractName }}
        </a-select-option>
      </a-select>
      <a-select
        v-model:value="filter.partnerId"
        class="purchase-order-search-select"
        placeholder="全部供应商"
        allow-clear
        show-search
        size="large"
        :filter-option="
          (input: string, option: SelectOption) =>
            option.label?.toLowerCase().includes(input.toLowerCase())
        "
        @change="onSearch"
      >
        <a-select-option v-for="p in supplierList" :key="p.id" :value="p.id">
          {{ p.partnerName }}
        </a-select-option>
      </a-select>
      <a-select
        v-model:value="filter.orderType"
        class="purchase-order-search-select is-compact"
        placeholder="类型"
        allow-clear
        size="large"
        @change="onSearch"
      >
        <a-select-option v-for="(label, key) in orderTypeLabel" :key="key" :value="key">
          {{ label }}
        </a-select-option>
      </a-select>
      <a-select
        v-model:value="filter.orderStatus"
        class="purchase-order-search-select is-compact"
        placeholder="状态"
        allow-clear
        size="large"
        @change="onSearch"
      >
        <a-select-option v-for="(_, key) in orderStatusLabelMap" :key="key" :value="key">
          {{ orderStatusLabel(String(key)) }}
        </a-select-option>
      </a-select>
    </div>
    <div class="purchase-order-search-keyword-row procurement-subcontract-query-row">
      <a-input
        v-model:value="filter.keyword"
        class="purchase-order-search-input"
        placeholder="搜索订单编号、名称"
        allow-clear
        size="large"
        @press-enter="onSearch"
      >
        <template #prefix><SearchOutlined class="purchase-order-search-prefix-icon" /></template>
      </a-input>
      <div class="purchase-order-search-actions procurement-subcontract-query-actions">
        <a-button
          class="procurement-subcontract-desktop-action"
          type="primary"
          size="large"
          @click="onSearch"
          >搜索</a-button
        >
        <a-button class="procurement-subcontract-desktop-action" size="large" @click="onReset">
          <template #icon><ReloadOutlined /></template>
          重置
        </a-button>
        <a-button
          class="procurement-subcontract-filter-toggle"
          size="large"
          :aria-expanded="filterPanelOpen"
          @click="filterPanelOpen = !filterPanelOpen"
        >
          <template #icon><FilterOutlined /></template>
          筛选
        </a-button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.purchase-order-search-bar {
  display: flex;
  flex: 0 0 auto;
  flex-direction: column;
  align-items: stretch;
  justify-content: flex-start;
  gap: 12px;
  margin: 0;
}

.purchase-order-search-fields {
  display: flex;
  flex: 0 0 auto;
  flex-wrap: wrap;
  gap: 12px;
  align-items: center;
  min-width: 0;
  width: 100%;
}

.purchase-order-search-keyword-row {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  align-items: center;
  min-width: 0;
  width: 100%;
}

.purchase-order-search-input {
  flex: 1 1 320px;
  min-width: 320px;
}

.purchase-order-search-prefix-icon {
  color: var(--text-secondary);
}

.purchase-order-search-select {
  width: 100%;
  min-width: 180px;
  flex: 1 1 180px;
}

.purchase-order-search-select.is-compact {
  min-width: 150px;
}

.purchase-order-search-actions {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  margin-left: auto;
  min-width: 0;
}

@media (max-width: 768px) {
  .purchase-order-search-fields,
  .purchase-order-search-keyword-row {
    align-items: stretch;
    flex-direction: column;
  }

  .purchase-order-search-actions {
    width: 100%;
    margin-left: 0;
  }

  .purchase-order-search-input,
  .purchase-order-search-select,
  .purchase-order-search-select.is-compact {
    width: 100%;
    min-width: 0;
    flex-basis: auto;
  }

  .purchase-order-search-actions :deep(.ant-btn) {
    flex: 1;
  }
}
</style>
