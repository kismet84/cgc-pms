<script setup lang="ts">
import { dismissToast, toastItems, V2_TOAST_DURATION_MS } from './toast'
</script>

<template>
  <div class="v2-toast-host" aria-live="polite" aria-relevant="additions removals">
    <TransitionGroup name="v2-toast">
      <article
        v-for="toast in toastItems"
        :key="toast.id"
        :class="['v2-toast', `v2-toast--${toast.type}`]"
        :role="toast.type === 'error' ? 'alert' : 'status'"
        aria-atomic="true"
      >
        <span class="v2-toast__mark" aria-hidden="true"></span>
        <div class="v2-toast__copy">
          <strong>{{ toast.title }}</strong>
          <p>{{ toast.message }}</p>
        </div>
        <button
          class="v2-toast__close"
          type="button"
          :aria-label="`关闭提示：${toast.title}`"
          @click="dismissToast(toast.id)"
        >
          ×
        </button>
        <span
          class="v2-toast__progress"
          aria-hidden="true"
          :style="{ animationDuration: `${V2_TOAST_DURATION_MS}ms` }"
        ></span>
      </article>
    </TransitionGroup>
  </div>
</template>

<style scoped>
.v2-toast-host {
  position: fixed;
  z-index: var(--v2-z-toast);
  top: var(--v2-space-5);
  left: 50%;
  display: grid;
  width: min(26rem, calc(100vw - 2rem));
  gap: var(--v2-space-3);
  pointer-events: none;
  transform: translateX(-50%);
}

.v2-toast {
  --v2-toast-accent: var(--v2-color-primary);
  --v2-toast-surface: var(--v2-toast-surface-info);

  position: relative;
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  gap: var(--v2-space-3);
  align-items: start;
  min-height: 4.5rem;
  padding: var(--v2-space-4);
  overflow: hidden;
  color: var(--v2-color-text-strong);
  background: var(--v2-toast-surface);
  border: var(--v2-border-width) solid color-mix(in srgb, var(--v2-toast-accent) 35%, transparent);
  border-radius: var(--v2-radius-lg);
  box-shadow: var(--v2-shadow-float);
  pointer-events: auto;
  backdrop-filter: blur(40px) saturate(180%);
  -webkit-backdrop-filter: blur(40px) saturate(180%);
}

.v2-toast--success {
  --v2-toast-accent: var(--v2-color-success);
  --v2-toast-surface: var(--v2-toast-surface-success);
}

.v2-toast--info {
  --v2-toast-accent: var(--v2-color-primary);
  --v2-toast-surface: var(--v2-toast-surface-info);
}

.v2-toast--warn {
  --v2-toast-accent: var(--v2-color-warning);
  --v2-toast-surface: var(--v2-toast-surface-warning);
}

.v2-toast--error {
  --v2-toast-accent: var(--v2-color-danger);
  --v2-toast-surface: var(--v2-toast-surface-danger);
}

.v2-toast__mark {
  width: 0.625rem;
  height: 0.625rem;
  margin-top: 0.35rem;
  background: var(--v2-toast-accent);
  border-radius: 50%;
  box-shadow: 0 0 0 0.25rem color-mix(in srgb, var(--v2-toast-accent) 14%, transparent);
}

.v2-toast__copy strong,
.v2-toast__copy p {
  margin: 0;
}

.v2-toast__copy strong {
  font-size: var(--v2-font-size-14);
  line-height: var(--v2-line-height-ui);
}

.v2-toast__copy p {
  margin-top: var(--v2-space-1);
  color: var(--v2-color-text-secondary);
  font-size: var(--v2-font-size-13);
  line-height: var(--v2-line-height-body);
}

.v2-toast__close {
  display: grid;
  width: 1.75rem;
  height: 1.75rem;
  padding: 0;
  color: var(--v2-color-text-secondary);
  font: inherit;
  font-size: var(--v2-font-size-17);
  line-height: 1;
  background: transparent;
  border: 0;
  border-radius: var(--v2-radius-sm);
  cursor: pointer;
  place-items: center;
}

.v2-toast__close:hover {
  color: var(--v2-color-text-strong);
  background: var(--v2-toast-close-hover);
}

.v2-toast__close:focus-visible {
  outline: 3px solid var(--v2-color-focus-ring);
  outline-offset: 1px;
}

.v2-toast__progress {
  position: absolute;
  right: 0;
  bottom: 0;
  left: 0;
  height: 0.1875rem;
  background: var(--v2-toast-accent);
  transform-origin: left;
  animation: v2-toast-countdown linear forwards;
}

.v2-toast-enter-active,
.v2-toast-leave-active,
.v2-toast-move {
  transition:
    opacity var(--v2-motion-slow) var(--v2-ease-standard),
    transform var(--v2-motion-slow) var(--v2-ease-emphasized);
}

.v2-toast-enter-from,
.v2-toast-leave-to {
  opacity: 0;
  transform: translateY(-1rem) scale(0.98);
}

@keyframes v2-toast-countdown {
  to {
    transform: scaleX(0);
  }
}

@media (prefers-reduced-motion: reduce) {
  .v2-toast__progress {
    animation: none;
  }
}
</style>
