<script setup lang="ts">
import V2Button from './V2Button.vue'

const props = withDefaults(
  defineProps<{
    text: string
    loading?: boolean
    disabled?: boolean
    onClick?: (event: MouseEvent) => void
    className?: string
  }>(),
  {
    loading: false,
    disabled: false,
    onClick: undefined,
    className: '',
  },
)

function handleClick(event: MouseEvent): void {
  props.onClick?.(event)
}
</script>

<template>
  <V2Button
    class="v2-glass-button"
    :class="className"
    :loading="loading"
    :disabled="disabled"
    @click="handleClick"
  >
    {{ text }}
  </V2Button>
</template>

<style scoped>
.v2-glass-button.v2-button {
  position: relative;
  isolation: isolate;
  width: auto;
  min-width: 0;
  min-height: var(--v2-control-height-md);
  overflow: hidden;
  padding-inline: clamp(var(--v2-space-4), 3vw, var(--v2-space-7));
  color: var(--v2-color-text-strong);
  background: linear-gradient(
    145deg,
    color-mix(in srgb, var(--v2-color-surface) 50%, transparent) 0%,
    color-mix(in srgb, var(--v2-color-surface-subtle) 50%, transparent) 52%,
    color-mix(in srgb, var(--v2-color-border-subtle) 50%, transparent) 100%
  );
  border: var(--v2-border-width) solid color-mix(in srgb, var(--v2-color-surface) 82%, transparent);
  border-radius: var(--v2-radius-lg);
  box-shadow:
    0 10px 24px color-mix(in srgb, var(--v2-color-text-muted) 14%, transparent),
    var(--v2-shadow-control),
    inset 0 1px 0 color-mix(in srgb, var(--v2-color-surface) 92%, transparent),
    inset 0 -2px 5px color-mix(in srgb, var(--v2-color-border) 32%, transparent);
  backdrop-filter: blur(16px) saturate(160%);
  -webkit-backdrop-filter: blur(16px) saturate(160%);
  transition:
    transform var(--v2-motion-fast) var(--v2-ease-standard),
    box-shadow var(--v2-motion-fast) var(--v2-ease-standard),
    border-color var(--v2-motion-fast) var(--v2-ease-standard),
    background var(--v2-motion-fast) var(--v2-ease-standard);
}

.v2-glass-button.v2-button::before,
.v2-glass-button.v2-button::after {
  position: absolute;
  pointer-events: none;
  content: '';
}

.v2-glass-button.v2-button::before {
  inset: 1px 12% auto;
  height: 45%;
  border-top: var(--v2-border-width) solid
    color-mix(in srgb, var(--v2-color-surface) 94%, transparent);
  border-radius: 999px 999px 50% 50%;
  background: linear-gradient(
    180deg,
    color-mix(in srgb, var(--v2-color-surface) 38%, transparent),
    transparent
  );
}

.v2-glass-button.v2-button::after {
  inset: auto 10% 0;
  height: 3px;
  border-radius: 999px;
  background: color-mix(in srgb, var(--v2-color-surface) 36%, transparent);
  filter: blur(1px);
}

.v2-glass-button.v2-button:hover:not(:disabled) {
  color: var(--v2-color-text-strong);
  background: linear-gradient(
    145deg,
    color-mix(in srgb, var(--v2-color-surface) 50%, transparent) 0%,
    color-mix(in srgb, var(--v2-color-surface-subtle) 50%, transparent) 52%,
    color-mix(in srgb, var(--v2-color-border-subtle) 50%, transparent) 100%
  );
  border-color: color-mix(in srgb, var(--v2-color-surface) 96%, transparent);
  box-shadow:
    0 14px 28px color-mix(in srgb, var(--v2-color-text-muted) 18%, transparent),
    0 3px 8px color-mix(in srgb, var(--v2-color-text-muted) 12%, transparent),
    inset 0 1px 0 var(--v2-color-surface),
    inset 0 -2px 6px color-mix(in srgb, var(--v2-color-border) 38%, transparent);
  transform: translateY(-2px);
}

.v2-glass-button.v2-button:active:not(:disabled) {
  transform: translateY(1px) scale(0.985);
}

.v2-glass-button.v2-button:focus-visible {
  outline: 0;
  box-shadow:
    0 0 0 3px var(--v2-color-focus-ring),
    0 10px 24px color-mix(in srgb, var(--v2-color-text-muted) 16%, transparent),
    inset 0 1px 0 color-mix(in srgb, var(--v2-color-surface) 94%, transparent),
    inset 0 -2px 5px color-mix(in srgb, var(--v2-color-border) 32%, transparent);
}

.v2-glass-button.v2-button:disabled {
  color: var(--v2-color-text-disabled);
  background: linear-gradient(
    145deg,
    color-mix(in srgb, var(--v2-color-surface) 50%, transparent),
    color-mix(in srgb, var(--v2-color-border) 50%, transparent)
  );
  box-shadow: inset 0 1px 0 color-mix(in srgb, var(--v2-color-surface) 64%, transparent);
  opacity: 0.72;
}

@media (prefers-reduced-motion: reduce) {
  .v2-glass-button.v2-button {
    transition: none;
  }

  .v2-glass-button.v2-button:hover:not(:disabled),
  .v2-glass-button.v2-button:active:not(:disabled) {
    transform: none;
  }
}

@media (max-width: 48rem) {
  .v2-glass-button.v2-button {
    min-height: var(--v2-control-height-touch);
  }
}
</style>
