<script setup lang="ts">
import { computed } from 'vue'

const props = withDefaults(
  defineProps<{
    minItemWidth?: string
    gap?: 0 | 1 | 2 | 3 | 4 | 5 | 6 | 8
    align?: 'stretch' | 'start' | 'center' | 'end'
  }>(),
  {
    minItemWidth: '14rem',
    gap: 3,
    align: 'stretch',
  },
)

const alignMap = { stretch: 'stretch', start: 'start', center: 'center', end: 'end' }
const style = computed(() => ({
  '--v2-grid-min': props.minItemWidth,
  '--v2-grid-gap': `var(--v2-space-${props.gap})`,
  '--v2-grid-align': alignMap[props.align],
}))
</script>

<template>
  <div class="v2-grid" :style="style"><slot /></div>
</template>
