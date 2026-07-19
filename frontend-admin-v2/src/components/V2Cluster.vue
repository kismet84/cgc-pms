<script setup lang="ts">
import { computed } from 'vue'

const props = withDefaults(
  defineProps<{
    gap?: 0 | 1 | 2 | 3 | 4 | 5 | 6 | 8
    align?: 'start' | 'center' | 'end'
    justify?: 'start' | 'center' | 'end' | 'between'
  }>(),
  {
    gap: 2,
    align: 'center',
    justify: 'start',
  },
)

const alignMap = { start: 'flex-start', center: 'center', end: 'flex-end' }
const justifyMap = {
  start: 'flex-start',
  center: 'center',
  end: 'flex-end',
  between: 'space-between',
}
const style = computed(() => ({
  '--v2-cluster-gap': `var(--v2-space-${props.gap})`,
  '--v2-cluster-align': alignMap[props.align],
  '--v2-cluster-justify': justifyMap[props.justify],
}))
</script>

<template>
  <div class="v2-cluster" :style="style"><slot /></div>
</template>
