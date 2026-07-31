<script setup lang="ts">
/**
 * LgEmptyState — 统一空状态组件
 *
 * Props:
 * - description: 描述文字（可选，默认 '暂无数据'）
 * - image: 自定义图片插槽名 (Ant Design Empty 的 image slot 名)
 *
 * Slots:
 * - default: 自定义操作区域（如"去新建"按钮）
 */
withDefaults(
  defineProps<{
    description?: string
  }>(),
  {
    description: '暂无数据',
  },
)
</script>

<template>
  <div class="lg-card-list-empty">
    <a-empty :description="description">
      <slot />
    </a-empty>
  </div>
</template>
