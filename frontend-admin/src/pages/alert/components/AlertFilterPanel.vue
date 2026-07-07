<script setup lang="ts">
import type { Dayjs } from 'dayjs'
import { ReloadOutlined, SearchOutlined } from '@ant-design/icons-vue'

defineProps<{
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
</script>

<template>
  <section class="alert-panel alert-filter-panel">
    <div class="alert-filter-grid">
      <div class="alert-filter-item">
        <label>项目</label>
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
        <label>严重度</label>
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
        <label>已读状态</label>
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
        <label>处理状态</label>
        <a-select
          v-model:value="filter.processStatus"
          allow-clear
          :options="processStatusOptions"
          placeholder="全部"
        />
      </div>
      <div class="alert-filter-item alert-filter-item-range">
        <label>触发时间</label>
        <a-range-picker
          v-model:value="filter.triggeredAtRange"
          value-format=""
          style="width: 100%"
        />
      </div>
    </div>
    <div class="alert-filter-foot">
      <div class="alert-filter-item alert-filter-item-keyword">
        <a-input
          v-model:value="filter.keyword"
          allow-clear
          placeholder="告警ID/消息摘要/业务单据号"
        >
          <template #prefix>
            <SearchOutlined />
          </template>
        </a-input>
      </div>
      <div class="alert-filter-actions">
        <a-button type="primary" @click="handleSearch">搜索</a-button>
        <a-button @click="handleReset">
          <template #icon><ReloadOutlined /></template>
          重置
        </a-button>
      </div>
      <div v-if="hasDefaultScopeDomain" class="alert-filter-scope">
        <a-checkbox v-model:checked="filter.onlyDefaultScope">仅看默认范围</a-checkbox>
      </div>
    </div>
  </section>
</template>

<style scoped>
.alert-panel {
  background: #fff;
  border: 1px solid #e8edf5;
  border-radius: 12px;
  box-shadow: 0 4px 14px rgba(31, 35, 41, 0.04);
}

.alert-filter-panel {
  padding: 12px 18px;
}

.alert-filter-grid {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 10px;
  width: 100%;
}

.alert-filter-item {
  min-width: 0;
}

.alert-filter-item :deep(.ant-select),
.alert-filter-item :deep(.ant-picker),
.alert-filter-item :deep(.ant-input-affix-wrapper) {
  width: 100%;
}

.alert-filter-item label {
  display: block;
  margin-bottom: 4px;
  color: #3b4554;
  font-size: 12px;
  font-weight: 600;
}

.alert-filter-item-keyword {
  min-width: 0;
}

.alert-filter-foot {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 10px;
}

.alert-filter-foot .alert-filter-item-keyword {
  flex: 1 1 auto;
}

.alert-filter-actions {
  display: flex;
  gap: 8px;
  flex: 0 0 auto;
}

.alert-filter-scope {
  margin-left: auto;
  color: #5f6b7a;
  font-size: 13px;
}

@media (max-width: 1100px) {
  .alert-filter-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .alert-filter-foot {
    flex-wrap: wrap;
  }

  .alert-filter-item-keyword {
    flex-basis: 100%;
  }

  .alert-filter-scope {
    width: 100%;
    margin-left: 0;
  }
}

@media (max-width: 768px) {
  .alert-filter-grid {
    grid-template-columns: 1fr;
  }

  .alert-filter-foot,
  .alert-filter-actions {
    flex-direction: column;
    align-items: stretch;
  }

  .alert-filter-scope {
    width: auto;
  }
}
</style>
