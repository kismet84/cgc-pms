<script setup lang="ts">
import { nextTick, onBeforeUnmount, ref, useId, watch } from 'vue'

const props = withDefaults(
  defineProps<{
    open: boolean
    title: string
    description?: string
    closeLabel?: string
    closeOnBackdrop?: boolean
    closeDisabled?: boolean
    panelClass?: string
  }>(),
  {
    description: undefined,
    closeLabel: '关闭对话框',
    closeOnBackdrop: true,
    closeDisabled: false,
    panelClass: undefined,
  },
)

const emit = defineEmits<{
  close: []
  'backdrop-click': []
  'update:open': [value: boolean]
}>()

const panel = ref<HTMLElement | null>(null)
const titleId = `v2-dialog-title-${useId()}`
const descriptionId = `${titleId}-description`
let previousFocus: HTMLElement | null = null

function close() {
  if (props.closeDisabled) return
  emit('update:open', false)
  emit('close')
}

function onBackdrop() {
  if (props.closeOnBackdrop) {
    close()
    return
  }
  emit('backdrop-click')
}

function focusPanel() {
  panel.value?.focus()
}

function focusableElements() {
  if (!panel.value) return []
  return Array.from(
    panel.value.querySelectorAll<HTMLElement>(
      'button:not(:disabled), [href], input:not(:disabled), select:not(:disabled), textarea:not(:disabled), [tabindex]:not([tabindex="-1"])',
    ),
  )
}

function onKeydown(event: KeyboardEvent) {
  if (event.key !== 'Tab') return

  const focusables = focusableElements()
  if (focusables.length === 0) {
    event.preventDefault()
    panel.value?.focus()
    return
  }

  const first = focusables[0]
  const last = focusables[focusables.length - 1]
  if (event.shiftKey && document.activeElement === first) {
    event.preventDefault()
    last?.focus()
  } else if (!event.shiftKey && document.activeElement === last) {
    event.preventDefault()
    first?.focus()
  }
}

function onDocumentKeydown(event: KeyboardEvent) {
  if (!props.open || event.defaultPrevented || event.key !== 'Escape') return
  if (!panel.value?.contains(document.activeElement)) return
  event.preventDefault()
  close()
}

watch(
  () => props.open,
  async (open) => {
    if (open) {
      previousFocus = document.activeElement instanceof HTMLElement ? document.activeElement : null
      document.addEventListener('keydown', onDocumentKeydown)
      await nextTick()
      focusPanel()
      return
    }
    document.removeEventListener('keydown', onDocumentKeydown)
    previousFocus?.focus()
    previousFocus = null
  },
  { immediate: true },
)

onBeforeUnmount(() => {
  document.removeEventListener('keydown', onDocumentKeydown)
  previousFocus?.focus()
})
</script>

<template>
  <Teleport to="body">
    <Transition name="v2-dialog" @after-enter="focusPanel">
      <div v-if="open" class="v2-dialog__backdrop" @click.self="onBackdrop">
        <section
          ref="panel"
          :class="['v2-dialog__panel', panelClass]"
          role="dialog"
          aria-modal="true"
          :aria-labelledby="titleId"
          :aria-describedby="description ? descriptionId : undefined"
          tabindex="-1"
          @keydown="onKeydown"
        >
          <header class="v2-dialog__header">
            <div>
              <h2 :id="titleId" class="v2-dialog__title">{{ title }}</h2>
              <p v-if="description" :id="descriptionId" class="v2-dialog__description">
                {{ description }}
              </p>
            </div>
            <button
              type="button"
              class="v2-dialog__close"
              :aria-label="closeLabel"
              :disabled="closeDisabled"
              @click="close"
            >
              <span aria-hidden="true">×</span>
            </button>
          </header>
          <div class="v2-dialog__body"><slot /></div>
          <footer v-if="$slots.footer" class="v2-dialog__footer"><slot name="footer" /></footer>
        </section>
      </div>
    </Transition>
  </Teleport>
</template>
