<script setup lang="ts">
withDefaults(
  defineProps<{
    enabled: boolean
    disabled?: boolean
    ariaLabel?: string
  }>(),
  {
    disabled: false,
    ariaLabel: undefined,
  },
)

defineEmits<{
  toggle: []
}>()
</script>

<template>
  <button
    type="button"
    class="v2-status-toggle"
    :class="enabled ? 'is-enabled' : 'is-disabled'"
    role="switch"
    :aria-checked="enabled"
    :aria-label="ariaLabel"
    :disabled="disabled"
    @click="$emit('toggle')"
  >
    {{ enabled ? '启用' : '停用' }}
  </button>
</template>

<style scoped>
.v2-status-toggle {
  display: inline-flex;
  min-height: 24px;
  align-items: center;
  justify-content: center;
  padding: 2px var(--v2-space-2);
  border: var(--v2-border-width) solid var(--v2-color-border);
  border-radius: 999px;
  background: var(--v2-color-surface-subtle);
  color: var(--v2-color-text-muted);
  cursor: pointer;
  font: inherit;
  font-size: var(--v2-font-size-12);
  font-weight: var(--v2-font-weight-medium);
  line-height: 1;
}

.v2-status-toggle.is-enabled {
  border-color: var(--v2-color-success);
  background: var(--v2-color-success-soft);
  color: var(--v2-color-success-text);
}

.v2-status-toggle:disabled {
  cursor: not-allowed;
  opacity: 0.65;
}

.v2-status-toggle:focus-visible {
  outline: 2px solid var(--v2-color-primary);
  outline-offset: 2px;
}
</style>
