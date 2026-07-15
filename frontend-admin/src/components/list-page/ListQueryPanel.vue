<script setup lang="ts">
import { ref } from 'vue'
import { FilterOutlined, ReloadOutlined } from '@ant-design/icons-vue'

withDefaults(
  defineProps<{
    ariaLabel?: string
  }>(),
  {
    ariaLabel: '列表查询条件',
  },
)

const emit = defineEmits<{
  search: []
  reset: []
}>()

const filtersOpen = ref(false)

function applyFilters() {
  filtersOpen.value = false
  emit('search')
}

function resetFilters() {
  emit('reset')
}
</script>

<template>
  <section class="lg-search-bar list-query-panel" :aria-label="ariaLabel">
    <div class="list-query-row">
      <div class="list-query-primary">
        <slot name="primary" />
      </div>
      <a-button type="primary" class="list-query-search-button" @click="emit('search')">
        搜索
      </a-button>
      <a-button class="list-query-reset-button" @click="resetFilters">
        <template #icon><ReloadOutlined /></template>
        重置
      </a-button>
      <a-button
        class="list-query-filter-button"
        :class="{ 'list-query-filter-button--active': filtersOpen }"
        :aria-expanded="filtersOpen"
        @click="filtersOpen = !filtersOpen"
      >
        <template #icon><FilterOutlined /></template>
        筛选
      </a-button>
    </div>

    <div v-if="filtersOpen" class="list-query-filter-panel">
      <div class="list-query-filter-fields">
        <slot name="filters" />
      </div>
      <div class="list-query-filter-actions">
        <a-button @click="resetFilters">重置</a-button>
        <a-button type="primary" @click="applyFilters">应用筛选</a-button>
      </div>
    </div>
  </section>
</template>

<style scoped>
.list-query-panel {
  display: flex;
  flex-direction: column;
  align-items: stretch;
  gap: 6px;
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

.list-query-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto auto auto;
  align-items: center;
  gap: 8px;
}

.list-query-primary {
  min-width: 0;
}

.list-query-primary :deep(.ant-input-affix-wrapper),
.list-query-primary :deep(.ant-select),
.list-query-primary :deep(input),
.list-query-primary :deep(select) {
  width: 100%;
  height: 40px;
}

.list-query-search-button,
.list-query-reset-button,
.list-query-filter-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  height: 40px;
  min-width: 72px;
  padding-inline: 10px;
  border-radius: var(--radius-md);
}

.list-query-filter-button {
  min-width: 76px;
}

.list-query-filter-button--active {
  color: var(--primary);
  border-color: var(--primary);
}

.list-query-filter-panel {
  padding: 10px;
  background: var(--surface);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-soft);
}

.list-query-filter-fields {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 8px;
}

.list-query-filter-fields :deep(.ant-select),
.list-query-filter-fields :deep(.ant-input-affix-wrapper),
.list-query-filter-fields :deep(input),
.list-query-filter-fields :deep(select) {
  width: 100%;
  min-width: 0;
  height: 40px;
}

.list-query-filter-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 8px;
}

.list-query-filter-actions :deep(.ant-btn) {
  min-height: 40px;
}

@media (max-width: 900px) {
  .list-query-filter-fields {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (width < 500px) {
  .list-query-panel {
    min-height: 40px;
    padding: 0;
    background: transparent;
    box-shadow: none;
  }

  .list-query-row {
    grid-template-columns: minmax(0, 1fr) auto;
    gap: 6px;
  }

  .list-query-search-button,
  .list-query-reset-button {
    display: none;
  }

  .list-query-filter-fields {
    grid-template-columns: 1fr;
  }
}
</style>
