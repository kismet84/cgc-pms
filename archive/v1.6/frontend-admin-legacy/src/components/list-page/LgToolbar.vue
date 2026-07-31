<script setup lang="ts">
/**
 * LgToolbar — 列表工具栏（左主操作区 + 右辅助操作区）
 *
 * Slots:
 * - default: 左侧区域（新建、批量操作等主操作按钮）
 * - right: 右侧区域（列设置、导出等辅助操作按钮）
 *
 * 默认渲染一个空工具栏（左/右均为空），左右各有 slot 插槽。
 */
</script>

<template>
  <div class="lg-toolbar">
    <div class="lg-toolbar-left">
      <slot />
    </div>
    <div class="lg-toolbar-right">
      <slot name="right" />
    </div>
  </div>
</template>
