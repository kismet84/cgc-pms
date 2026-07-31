<script setup lang="ts">
import { ReloadOutlined, SearchOutlined } from '@ant-design/icons-vue'

/**
 * LgFilterPanel — 列表页筛选面板（卡片容器 + 关键词输入 + 自定义筛选 slot）
 *
 * Props:
 * - keyword: 关键词 (v-model)
 * - placeholder: 输入框占位文字
 * - loading: 查询加载中
 * - showReset: 是否显示重置按钮
 *
 * Slots:
 * - filters: 额外筛选字段区域，由业务页面自行传入
 *
 * Events:
 * - search: 点击查询或回车
 * - reset: 点击重置
 * - 'update:keyword': 关键词变化
 */
withDefaults(
  defineProps<{
    keyword?: string
    placeholder?: string
    loading?: boolean
    showReset?: boolean
  }>(),
  {
    keyword: '',
    placeholder: '请输入关键词搜索',
    loading: false,
    showReset: true,
  },
)

const emit = defineEmits<{
  'update:keyword': [value: string]
  search: []
  reset: []
}>()
</script>

<template>
  <section class="lg-filter-panel">
    <div v-if="$slots.filters" class="lg-filter-panel__filters">
      <slot name="filters" />
    </div>
    <div class="lg-filter-panel__footer">
      <div class="lg-filter-panel__search">
        <a-input
          class="lg-filter-panel__keyword"
          :value="keyword"
          :placeholder="placeholder"
          allow-clear
          size="large"
          @update:value="(value: string) => emit('update:keyword', value)"
          @press-enter="emit('search')"
        >
          <template #prefix><SearchOutlined style="color: var(--text-secondary)" /></template>
        </a-input>
      </div>
      <div class="lg-filter-panel__actions">
        <a-button type="primary" size="large" :loading="loading" @click="emit('search')">
          查询
        </a-button>
        <a-button v-if="showReset" size="large" @click="emit('reset')">
          <template #icon><ReloadOutlined /></template>
          重置
        </a-button>
      </div>
    </div>
  </section>
</template>

<style scoped>
.lg-filter-panel {
  padding: 18px 20px;
  border: 1px solid var(--border, #dce3ee);
  border-radius: 8px;
  background: var(--surface, #fff);
  box-shadow: 0 10px 30px rgb(15 23 42 / 6%);
}

.lg-filter-panel__filters {
  display: flex;
  flex-wrap: wrap;
  gap: 12px 16px;
  align-items: flex-end;
  min-height: 0;
}

.lg-filter-panel__footer {
  display: flex;
  gap: 16px;
  align-items: flex-end;
  justify-content: space-between;
  margin-top: 16px;
}

.lg-filter-panel__search {
  display: flex;
  flex: 1 1 auto;
}

.lg-filter-panel__keyword {
  flex: 0 1 320px;
  min-width: 220px;
}

.lg-filter-panel__actions {
  display: flex;
  flex: 0 0 auto;
  gap: 12px;
  align-items: center;
}

@media (max-width: 960px) {
  .lg-filter-panel__footer {
    flex-direction: column;
    align-items: stretch;
  }

  .lg-filter-panel__actions {
    justify-content: flex-end;
  }

  .lg-filter-panel__keyword {
    flex-basis: 100%;
  }
}

@media (max-width: 640px) {
  .lg-filter-panel {
    padding: 16px;
    border-radius: 8px;
  }

  .lg-filter-panel__actions {
    width: 100%;
  }

  .lg-filter-panel__actions :deep(.ant-btn) {
    flex: 1 1 0;
  }
}
</style>
