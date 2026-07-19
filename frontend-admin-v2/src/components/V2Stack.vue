<script setup lang="ts">
import { computed } from 'vue'

const props = withDefaults(
  defineProps<{
    gap?: 0 | 1 | 2 | 3 | 4 | 5 | 6 | 8
    align?: 'stretch' | 'start' | 'center' | 'end'
    justify?: 'start' | 'center' | 'end' | 'between'
  }>(),
  {
    gap: 3,
    align: 'stretch',
    justify: 'start',
  },
)

const alignMap = { stretch: 'stretch', start: 'flex-start', center: 'center', end: 'flex-end' }
const justifyMap = {
  start: 'flex-start',
  center: 'center',
  end: 'flex-end',
  between: 'space-between',
}
const style = computed(() => ({
  '--v2-stack-gap': `var(--v2-space-${props.gap})`,
  '--v2-stack-align': alignMap[props.align],
  '--v2-stack-justify': justifyMap[props.justify],
}))
</script>

<template>
  <div class="v2-stack" :style="style"><slot /></div>
</template>
