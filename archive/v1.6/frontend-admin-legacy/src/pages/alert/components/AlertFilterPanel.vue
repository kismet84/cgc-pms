<script setup lang="ts">
import { computed, ref } from 'vue'
import type { Dayjs } from 'dayjs'
import { FilterOutlined, ReloadOutlined, SearchOutlined } from '@ant-design/icons-vue'

const props = defineProps<{
  filter: {
    keyword: string
    projectId?: string
    alertDomain?: string
    ruleType?: string
    severity?: string
    isRead?: number
    processStatus?: string
    triggeredAtRange: [Dayjs, Dayjs] | null
    onlyDefaultScope: boolean
  }
  projectsLoading: boolean
  projectOptions: Array<{ id: string | number; projectCode: string; projectName: string }>
  processStatusOptions: Array<{ value: string; label: string }>
  hasDefaultScopeDomain: boolean
  handleSearch: () => void
  handleReset: () => void
}>()

const advancedFiltersOpen = ref(false)
const hasActiveFilters = computed(() =>
  Boolean(
    props.filter.keyword.trim() ||
    props.filter.projectId ||
    props.filter.severity ||
    props.filter.isRead !== undefined ||
    props.filter.processStatus ||
    props.filter.triggeredAtRange?.length ||
    props.filter.onlyDefaultScope,
  ),
)

function applyFilters() {
  advancedFiltersOpen.value = false
  props.handleSearch()
}

function resetFilters() {
  advancedFiltersOpen.value = false
  props.handleReset()
}
</script>

<template>
  <section class="lg-search-bar alert-filter-panel" aria-label="预警查询条件">
    <div class="alert-search-row">
      <a-input
        v-model:value="filter.keyword"
        allow-clear
        placeholder="搜索告警ID、消息摘要或业务单据号"
        class="alert-search-input"
        @press-enter="applyFilters"
      >
        <template #prefix><SearchOutlined class="alert-search-icon" /></template>
      </a-input>
      <a-button type="primary" class="alert-search-button" @click="applyFilters">搜索</a-button>
      <a-button class="alert-reset-button" @click="resetFilters">
        <template #icon><ReloadOutlined /></template>
        重置
      </a-button>
      <a-button
        class="alert-filter-button"
        :class="{ 'alert-filter-button--active': hasActiveFilters }"
        :aria-expanded="advancedFiltersOpen"
        aria-controls="alert-advanced-filters"
        @click="advancedFiltersOpen = !advancedFiltersOpen"
      >
        <template #icon><FilterOutlined /></template>
        筛选
      </a-button>
    </div>

    <div v-if="advancedFiltersOpen" id="alert-advanced-filters" class="alert-filter-grid">
      <div class="alert-filter-item">
        <a-select
          v-model:value="filter.projectId"
          allow-clear
          show-search
          :loading="projectsLoading"
          :options="
            projectOptions.map((item) => ({
              value: String(item.id),
              label: `${item.projectCode} ${item.projectName}`,
            }))
          "
          placeholder="请选择项目"
        />
      </div>
      <div class="alert-filter-item">
        <a-select
          v-model:value="filter.severity"
          allow-clear
          :options="[
            { value: 'HIGH', label: '高危' },
            { value: 'MEDIUM', label: '中危' },
            { value: 'LOW', label: '低危' },
          ]"
          placeholder="请选择严重度"
        />
      </div>
      <div class="alert-filter-item">
        <a-select
          v-model:value="filter.isRead"
          allow-clear
          :options="[
            { value: 0, label: '未读' },
            { value: 1, label: '已读' },
          ]"
          placeholder="全部"
        />
      </div>
      <div class="alert-filter-item">
        <a-select
          v-model:value="filter.processStatus"
          allow-clear
          :options="processStatusOptions"
          placeholder="全部"
        />
      </div>
      <div class="alert-filter-item alert-filter-item-range">
        <a-range-picker
          v-model:value="filter.triggeredAtRange"
          value-format=""
          style="width: 100%"
        />
      </div>
      <div v-if="hasDefaultScopeDomain" class="alert-filter-scope">
        <a-checkbox v-model:checked="filter.onlyDefaultScope">仅看默认范围</a-checkbox>
      </div>
      <div class="alert-filter-actions">
        <a-button @click="resetFilters">重置</a-button>
        <a-button type="primary" @click="applyFilters">应用筛选</a-button>
      </div>
    </div>
  </section>
</template>

<style scoped>
.alert-filter-panel {
  display: flex;
  flex-direction: column;
  gap: 8px;
  width: 100%;
  min-height: 60px;
  box-sizing: border-box;
  padding: 10px 14px;
  margin: 0;
  border: 0;
  box-shadow:
    inset 0 0 0 1px var(--border),
    var(--shadow-soft);
}

.alert-search-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto auto auto;
  align-items: center;
  gap: 8px;
}

.alert-search-input,
.alert-search-button,
.alert-reset-button,
.alert-filter-button {
  height: 40px;
  min-height: 40px;
  border-radius: var(--radius-md);
}

.alert-search-button,
.alert-reset-button,
.alert-filter-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 72px;
  padding-inline: 10px;
}

.alert-filter-button {
  min-width: 76px;
}

.alert-filter-button--active,
.alert-search-icon {
  color: var(--primary);
}

.alert-filter-grid {
  display: grid;
  grid-template-columns: repeat(5, minmax(120px, 1fr));
  align-items: center;
  gap: 8px;
  width: 100%;
  padding: 10px;
  background: var(--surface);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-md);
}

.alert-filter-item {
  min-width: 0;
}

.alert-filter-item :deep(.ant-select),
.alert-filter-item :deep(.ant-picker),
.alert-filter-item :deep(.ant-input-affix-wrapper) {
  width: 100%;
}

.alert-filter-actions {
  display: flex;
  grid-column: 1 / -1;
  justify-content: flex-end;
  gap: 8px;
}

.alert-filter-scope {
  grid-column: 1 / -1;
  color: var(--text-secondary);
  font-size: 13px;
}

@media (width < 900px) {
  .alert-filter-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (width < 500px) {
  .alert-filter-panel {
    min-height: 40px;
    padding: 0;
    background: transparent;
    box-shadow: none;
  }

  .alert-search-row {
    grid-template-columns: minmax(0, 1fr) auto;
    gap: 6px;
  }

  .alert-search-button,
  .alert-reset-button {
    display: none;
  }

  .alert-filter-grid {
    grid-template-columns: 1fr;
  }
}
</style>
