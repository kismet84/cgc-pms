<script setup lang="ts">
withDefaults(
  defineProps<{
    tone?: 'neutral' | 'info' | 'success' | 'warning' | 'danger'
    dot?: boolean
  }>(),
  {
    tone: 'neutral',
    dot: false,
  },
)
</script>

<template>
  <span class="v2-badge" :class="`v2-badge--${tone}`">
    <span v-if="dot" class="v2-badge__dot" aria-hidden="true"></span>
    <slot />
  </span>
</template>
