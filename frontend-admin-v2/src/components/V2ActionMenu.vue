<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'

withDefaults(
  defineProps<{
    label: string
    triggerText?: string
    placement?: 'bottom-end' | 'top-end'
  }>(),
  {
    triggerText: '更多',
    placement: 'bottom-end',
  },
)

const root = ref<HTMLDetailsElement | null>(null)

function close(restoreFocus = false): void {
  if (!root.value?.open) return
  root.value.open = false
  if (restoreFocus) root.value.querySelector<HTMLElement>('summary')?.focus()
}

function closeFromOutside(event: PointerEvent): void {
  if (event.target instanceof Node && !root.value?.contains(event.target)) close()
}

onMounted(() => document.addEventListener('pointerdown', closeFromOutside))
onBeforeUnmount(() => document.removeEventListener('pointerdown', closeFromOutside))
</script>

<template>
  <details
    ref="root"
    class="v2-action-menu"
    :class="`v2-action-menu--${placement}`"
    @keydown.esc.stop.prevent="close(true)"
  >
    <summary class="v2-action-menu__trigger" :aria-label="label">{{ triggerText }}</summary>
    <div class="v2-action-menu__content" role="group" :aria-label="label" @click="close()">
      <slot />
    </div>
  </details>
</template>
