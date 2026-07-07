<script setup lang="ts">
import { reactive } from 'vue'
import { ReloadOutlined, SearchOutlined, SettingOutlined } from '@ant-design/icons-vue'

defineProps<{
  projectId?: string
  approvalStatus?: string
  status?: string
  keyword: string
  projectList: { id: string; projectName: string }[]
}>()

const emit = defineEmits<{
  'update:projectId': [value: string | undefined]
  'update:approvalStatus': [value: string | undefined]
  'update:status': [value: string | undefined]
  'update:keyword': [value: string]
  search: []
  reset: []
}>()

const filterVisibility = reactive({
  projectId: true,
  approvalStatus: true,
  status: true,
})
const filterSettingItems = [
  { key: 'projectId', label: '项目' },
  { key: 'approvalStatus', label: '审批状态' },
  { key: 'status', label: '业务状态' },
] as const

function toggleFilterVisibility(key: (typeof filterSettingItems)[number]['key']) {
  filterVisibility[key] = !filterVisibility[key]
}
</script>

<template>
  <div class="lg-search-bar purchase-request-search-bar">
    <div class="purchase-request-search-fields">
      <a-select
        v-if="filterVisibility.projectId"
        :value="projectId"
        class="purchase-request-search-select"
        placeholder="全部项目"
        allow-clear
        size="large"
        @update:value="(value: string | undefined) => emit('update:projectId', value)"
        @change="emit('search')"
      >
        <a-select-option v-for="p in projectList" :key="p.id" :value="p.id">
          {{ p.projectName }}
        </a-select-option>
      </a-select>
      <a-select
        v-if="filterVisibility.approvalStatus"
        :value="approvalStatus"
        class="purchase-request-search-select is-compact"
        placeholder="审批状态"
        allow-clear
        size="large"
        @update:value="(value: string | undefined) => emit('update:approvalStatus', value)"
        @change="emit('search')"
      >
        <a-select-option value="DRAFT">草稿</a-select-option>
        <a-select-option value="APPROVING">审批中</a-select-option>
        <a-select-option value="APPROVED">已通过</a-select-option>
        <a-select-option value="REJECTED">已驳回</a-select-option>
      </a-select>
      <a-select
        v-if="filterVisibility.status"
        :value="status"
        class="purchase-request-search-select is-compact"
        placeholder="业务状态"
        allow-clear
        size="large"
        @update:value="(value: string | undefined) => emit('update:status', value)"
        @change="emit('search')"
      >
        <a-select-option value="DRAFT">草稿</a-select-option>
        <a-select-option value="APPROVED">已通过</a-select-option>
        <a-select-option value="CONVERTED">已转PO</a-select-option>
      </a-select>
    </div>
    <div class="purchase-request-search-keyword-row">
      <a-input
        :value="keyword"
        class="purchase-request-search-input"
        placeholder="搜索申请编号"
        allow-clear
        size="large"
        @update:value="(value: string) => emit('update:keyword', value)"
        @press-enter="emit('search')"
      >
        <template #prefix>
          <SearchOutlined class="purchase-request-search-prefix-icon" />
        </template>
      </a-input>
      <div class="purchase-request-search-actions">
        <a-button type="primary" size="large" @click="emit('search')">查询</a-button>
        <a-button size="large" @click="emit('reset')">
          <template #icon><ReloadOutlined /></template>
          重置
        </a-button>
        <a-dropdown trigger="click">
          <a-button size="large">
            <template #icon><SettingOutlined /></template>
            筛选栏设置
          </a-button>
          <template #overlay>
            <a-menu>
              <a-menu-item
                v-for="item in filterSettingItems"
                :key="item.key"
                @click="toggleFilterVisibility(item.key)"
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
  </div>
</template>

<style scoped>
.purchase-request-search-bar {
  display: flex;
  flex: 0 0 auto;
  flex-direction: column;
  align-items: stretch;
  gap: 12px;
  margin: 0;
}

.purchase-request-search-fields {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  align-items: center;
  min-width: 0;
  width: 100%;
}

.purchase-request-search-keyword-row {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  align-items: center;
  min-width: 0;
  width: 100%;
}

.purchase-request-search-keyword-row > :deep(.ant-input-affix-wrapper) {
  min-width: 320px;
  flex: 1 1 320px;
}

.purchase-request-search-prefix-icon {
  color: var(--text-secondary);
}

.purchase-request-search-fields > :deep(.ant-select) {
  min-width: 150px;
  flex: 1 1 180px;
}

.purchase-request-search-select {
  width: 100%;
}

.purchase-request-search-select.is-compact {
  min-width: 150px;
}

.purchase-request-search-actions {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  margin-left: auto;
  min-width: 0;
}

@media (max-width: 768px) {
  .purchase-request-search-bar,
  .purchase-request-search-fields,
  .purchase-request-search-keyword-row {
    align-items: stretch;
    flex-direction: column;
  }

  .purchase-request-search-input,
  .purchase-request-search-select,
  .purchase-request-search-select.is-compact {
    width: 100%;
    min-width: 0;
    flex-basis: auto;
  }

  .purchase-request-search-actions {
    width: 100%;
    margin-left: 0;
  }

  .purchase-request-search-actions :deep(.ant-btn) {
    flex: 1;
  }
}
</style>
