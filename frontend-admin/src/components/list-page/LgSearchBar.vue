<script setup lang="ts">
import { SearchOutlined, ReloadOutlined } from '@ant-design/icons-vue'

/**
 * LgSearchBar — 通用搜索栏（关键词输入 + 查询/重置按钮 + 扩展筛选 slot）
 *
 * Props:
 * - keyword: 搜索关键词 (v-model)
 * - placeholder: 输入框占位文字
 * - loading: 查询加载中
 * - showReset: 是否显示重置按钮
 *
 * Slots:
 * - expand: 扩展筛选区域（下拉框、日期选择器等）
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
  <div class="lg-search-bar">
    <a-input
      :value="keyword"
      :placeholder="placeholder"
      allow-clear
      size="large"
      @update:value="(v: string) => emit('update:keyword', v)"
      @press-enter="emit('search')"
    >
      <template #prefix><SearchOutlined style="color: #697380" /></template>
    </a-input>
    <a-button type="primary" size="large" :loading="loading" @click="emit('search')">查询</a-button>
    <a-button v-if="showReset" size="large" @click="emit('reset')">
      <template #icon><ReloadOutlined /></template>
      重置
    </a-button>
    <div v-if="$slots.expand" class="lg-search-bar-expand">
      <slot name="expand" />
    </div>
  </div>
</template>
