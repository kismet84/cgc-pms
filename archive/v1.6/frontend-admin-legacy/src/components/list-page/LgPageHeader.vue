<script setup lang="ts">
/**
 * LgPageHeader — 页面头部（面包屑 + 标题 + 右侧操作区）
 *
 * Props:
 * - title: 页面标题
 * - breadcrumb: 面包屑路径数组
 *
 * Slots:
 * - default: 左侧区域（标题下方）
 * - actions: 右侧操作区
 */
interface BreadcrumbItem {
  label: string
  path?: string
}

withDefaults(
  defineProps<{
    title: string
    breadcrumb?: BreadcrumbItem[]
  }>(),
  {
    breadcrumb: () => [],
  },
)
</script>

<template>
  <div class="lg-page-head">
    <div>
      <a-breadcrumb v-if="breadcrumb.length" class="lg-page-head-breadcrumb">
        <a-breadcrumb-item v-for="(item, idx) in breadcrumb" :key="idx">
          <router-link v-if="item.path" :to="item.path">{{ item.label }}</router-link>
          <span v-else>{{ item.label }}</span>
        </a-breadcrumb-item>
      </a-breadcrumb>
      <h1 class="lg-page-head-title">{{ title }}</h1>
      <slot />
    </div>
    <div v-if="$slots.actions" class="lg-page-head-actions">
      <slot name="actions" />
    </div>
  </div>
</template>
