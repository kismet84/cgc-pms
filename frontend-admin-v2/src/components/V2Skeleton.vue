<script setup lang="ts">
withDefaults(
  defineProps<{
    variant?: 'text' | 'rect' | 'circle'
    label?: string
  }>(),
  {
    variant: 'text',
    label: '内容加载中',
  },
)
</script>

<template>
  <div
    class="v2-skeleton"
    :class="`v2-skeleton--${variant}`"
    role="status"
    aria-busy="true"
    :aria-label="label"
  ></div>
</template>
