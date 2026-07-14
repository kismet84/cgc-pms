<script setup lang="ts">
import { ReloadOutlined, SearchOutlined, SettingOutlined } from '@ant-design/icons-vue'

defineProps<{
  filter: {
    keyword: string
    projectType?: string
    status?: string
  }
  filterVisibility: {
    projectType: boolean
    status: boolean
  }
  filterSettingItems: ReadonlyArray<{
    key: 'projectType' | 'status'
    label: string
  }>
  projectTypeOptions: string[]
  projectTypeLabel: (value?: string) => string
  projectStatusOptions: string[]
  statusLabel: Record<string, string>
}>()

const emit = defineEmits<{
  search: []
  reset: []
  toggleFilterVisibility: [key: 'projectType' | 'status']
}>()
</script>

<template>
  <section class="lg-search-bar project-query-panel" aria-label="项目查询条件">
    <div class="project-filter-grid">
      <div v-if="filterVisibility.projectType" class="project-filter-item">
        <label>项目类型</label>
        <a-select
          v-model:value="filter.projectType"
          placeholder="全部类型"
          allow-clear
          class="project-search-select"
          @change="emit('search')"
        >
          <a-select-option v-for="item in projectTypeOptions" :key="item" :value="item">
            {{ projectTypeLabel(item) }}
          </a-select-option>
        </a-select>
      </div>
      <div v-if="filterVisibility.status" class="project-filter-item">
        <label>项目状态</label>
        <a-select
          v-model:value="filter.status"
          placeholder="全部状态"
          allow-clear
          class="project-search-select"
          @change="emit('search')"
        >
          <a-select-option v-for="item in projectStatusOptions" :key="item" :value="item">
            {{ statusLabel[item] ?? item }}
          </a-select-option>
        </a-select>
      </div>
    </div>
    <div class="project-filter-foot">
      <div class="project-filter-item project-filter-item-keyword">
        <a-input
          v-model:value="filter.keyword"
          placeholder="搜索项目编号、名称、类型、建设单位"
          allow-clear
          class="project-search-input"
          @press-enter="emit('search')"
        >
          <template #prefix><SearchOutlined class="project-search-icon" /></template>
        </a-input>
      </div>
      <div class="project-search-actions">
        <a-button type="primary" @click="emit('search')">搜索</a-button>
        <a-button @click="emit('reset')">
          <template #icon><ReloadOutlined /></template>
          重置
        </a-button>
        <a-dropdown trigger="click">
          <a-button>
            <template #icon><SettingOutlined /></template>
            筛选栏设置
          </a-button>
          <template #overlay>
            <a-menu>
              <a-menu-item
                v-for="item in filterSettingItems"
                :key="item.key"
                @click="emit('toggleFilterVisibility', item.key)"
              >
                <a-checkbox :checked="filterVisibility[item.key]">
                  {{ item.label }}
                </a-checkbox>
              </a-menu-item>
            </a-menu>
          </template>
        </a-dropdown>
      </div>
    </div>
  </section>
</template>

<style scoped>
.project-query-panel {
  display: grid;
  grid-template-rows: 25px 40px;
  row-gap: 10px;
  width: 100%;
  height: 95px;
  min-height: 95px;
  box-sizing: border-box;
  padding: 10px 14px;
  margin: 0;
  border: 0;
  box-shadow:
    inset 0 0 0 1px var(--border),
    var(--shadow-soft);
}

.project-filter-grid {
  display: flex;
  height: 25px;
  gap: 12px;
  width: 100%;
  min-width: 0;
}

.project-filter-item {
  flex: 1 1 180px;
  height: 25px;
  min-width: 0;
}

.project-filter-item label {
  display: none;
}

.project-filter-item :deep(.ant-select),
.project-filter-item :deep(.ant-input-affix-wrapper) {
  width: 100%;
  height: 25px;
}

.project-filter-item :deep(.ant-select-selector) {
  height: 25px !important;
  min-height: 25px !important;
  padding-inline: 10px !important;
}

.project-filter-item :deep(.ant-select-selection-item),
.project-filter-item :deep(.ant-select-selection-placeholder) {
  font-size: 13px;
  line-height: 23px !important;
}

.project-filter-item :deep(.ant-input-affix-wrapper) {
  min-height: 25px;
  padding-block: 0;
  padding-inline: 10px;
}

.project-filter-item :deep(.ant-input) {
  font-size: 13px;
  line-height: 23px;
}

.project-search-select {
  width: 100%;
}

.project-search-icon {
  color: var(--text-secondary);
}

.project-filter-foot {
  display: flex;
  align-items: center;
  height: 40px;
  gap: 10px;
  margin-top: 0;
  width: 100%;
  min-width: 0;
}

.project-filter-item-keyword {
  flex: 1 1 320px;
  height: 40px;
}

.project-search-input {
  min-width: 0;
}

.project-filter-item-keyword :deep(.ant-input-affix-wrapper) {
  height: 40px;
  min-height: 40px;
}

.project-filter-item-keyword :deep(.ant-input) {
  line-height: 38px;
}

.project-search-actions {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  gap: 8px;
  margin-left: auto;
}

.project-search-actions :deep(.ant-btn) {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  height: 40px;
  min-width: 52px;
  padding-inline: 10px;
  border-radius: 6px;
  font-size: 13px;
  line-height: 1;
}

.project-search-actions :deep(.ant-btn .anticon) {
  line-height: 1;
}

@media (max-width: 1200px) {
  .project-filter-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 768px) {
  .project-query-panel {
    display: flex;
    height: auto;
    min-height: 95px;
  }

  .project-filter-grid {
    flex-direction: column;
    height: auto;
  }

  .project-filter-foot {
    flex-direction: column;
    align-items: stretch;
    height: auto;
  }

  .project-search-actions {
    width: 100%;
  }

  .project-search-actions :deep(.ant-btn) {
    flex: 1;
  }
}
</style>
