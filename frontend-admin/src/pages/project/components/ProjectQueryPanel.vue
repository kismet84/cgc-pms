<script setup lang="ts">
import { computed, ref } from 'vue'
import { FilterOutlined, ReloadOutlined, SearchOutlined } from '@ant-design/icons-vue'

const props = defineProps<{
  filter: {
    keyword: string
    projectType?: string
    status?: string
  }
  projectTypeOptions: string[]
  projectTypeLabel: (value?: string) => string
  projectStatusOptions: string[]
  statusLabel: Record<string, string>
}>()

const emit = defineEmits<{
  search: []
  reset: []
}>()

const mobileFiltersOpen = ref(false)
const hasActiveMobileFilters = computed(() =>
  Boolean(props.filter.projectType || props.filter.status),
)

function applyMobileFilters() {
  mobileFiltersOpen.value = false
  emit('search')
}

function resetMobileFilters() {
  mobileFiltersOpen.value = false
  emit('reset')
}
</script>

<template>
  <section class="lg-search-bar project-query-panel" aria-label="项目查询条件">
    <div class="project-mobile-search-row">
      <a-input
        v-model:value="filter.keyword"
        placeholder="搜索项目名称或编号"
        allow-clear
        class="project-mobile-search-input"
        @press-enter="emit('search')"
      >
        <template #prefix><SearchOutlined class="project-search-icon" /></template>
      </a-input>
      <a-button type="primary" class="project-search-submit-button" @click="emit('search')">
        搜索
      </a-button>
      <a-button class="project-search-reset-button" @click="emit('reset')">
        <template #icon><ReloadOutlined /></template>
        重置
      </a-button>
      <a-button
        class="project-mobile-filter-button"
        :class="{ 'project-mobile-filter-button--active': hasActiveMobileFilters }"
        :aria-expanded="mobileFiltersOpen"
        aria-controls="project-mobile-filter-panel"
        @click="mobileFiltersOpen = !mobileFiltersOpen"
      >
        <template #icon><FilterOutlined /></template>
        筛选
      </a-button>
    </div>

    <div v-if="hasActiveMobileFilters && !mobileFiltersOpen" class="project-mobile-filter-summary">
      <a-tag v-if="filter.projectType" color="blue">
        类型：{{ projectTypeLabel(filter.projectType) }}
      </a-tag>
      <a-tag v-if="filter.status" color="blue">
        状态：{{ statusLabel[filter.status] ?? filter.status }}
      </a-tag>
    </div>

    <div
      v-if="mobileFiltersOpen"
      id="project-mobile-filter-panel"
      class="project-mobile-filter-panel"
    >
      <a-select
        v-model:value="filter.projectType"
        placeholder="全部类型"
        allow-clear
        aria-label="项目类型"
      >
        <a-select-option v-for="item in projectTypeOptions" :key="item" :value="item">
          {{ projectTypeLabel(item) }}
        </a-select-option>
      </a-select>
      <a-select
        v-model:value="filter.status"
        placeholder="全部状态"
        allow-clear
        aria-label="项目状态"
      >
        <a-select-option v-for="item in projectStatusOptions" :key="item" :value="item">
          {{ statusLabel[item] ?? item }}
        </a-select-option>
      </a-select>
      <div class="project-mobile-filter-actions">
        <a-button @click="resetMobileFilters">重置</a-button>
        <a-button type="primary" @click="applyMobileFilters">应用筛选</a-button>
      </div>
    </div>
  </section>
</template>

<style scoped>
.project-query-panel {
  display: flex;
  flex-direction: column;
  gap: 6px;
  width: 100%;
  height: auto;
  min-height: 60px;
  box-sizing: border-box;
  padding: 10px 14px;
  margin: 0;
  border: 0;
  box-shadow:
    inset 0 0 0 1px var(--border),
    var(--shadow-soft);
}

.project-search-icon {
  color: var(--text-secondary);
}

.project-mobile-search-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto auto auto;
  align-items: center;
  gap: 8px;
}

.project-mobile-search-input,
.project-search-submit-button,
.project-search-reset-button,
.project-mobile-filter-button {
  height: 40px;
  min-height: 40px;
  border-radius: var(--radius-md);
}

.project-search-submit-button,
.project-search-reset-button,
.project-mobile-filter-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 72px;
  padding-inline: 10px;
}

.project-mobile-filter-button {
  min-width: 76px;
}

.project-mobile-filter-button--active {
  color: var(--primary);
  border-color: var(--primary);
}

.project-mobile-filter-summary {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}

.project-mobile-filter-panel {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
  padding: 10px;
  background: var(--surface);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-soft);
}

.project-mobile-filter-panel :deep(.ant-select) {
  width: 100%;
}

.project-mobile-filter-actions {
  display: flex;
  grid-column: 1 / -1;
  justify-content: flex-end;
  gap: 8px;
}

.project-mobile-filter-actions :deep(.ant-btn) {
  min-height: 40px;
}

@media (width < 500px) {
  .project-query-panel {
    min-height: 40px;
    padding: 0;
    background: transparent;
    box-shadow: none;
  }

  .project-mobile-search-row {
    grid-template-columns: minmax(0, 1fr) auto;
    gap: 6px;
  }

  .project-search-submit-button,
  .project-search-reset-button {
    display: none;
  }
}
</style>
