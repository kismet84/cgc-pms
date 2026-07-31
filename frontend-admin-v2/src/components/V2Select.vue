<script setup lang="ts">
import { computed, nextTick, ref, useId } from 'vue'
import type { CSSProperties } from 'vue'
import type { V2SelectOption } from './types'

const props = withDefaults(
  defineProps<{
    modelValue?: string
    options: V2SelectOption[]
    id?: string
    label?: string
    placeholder?: string
    hint?: string
    error?: string
    disabled?: boolean
    required?: boolean
    allowEmpty?: boolean
    hideLabel?: boolean
  }>(),
  {
    modelValue: '',
    id: undefined,
    label: undefined,
    placeholder: '请选择',
    hint: undefined,
    error: undefined,
    disabled: false,
    required: false,
    allowEmpty: false,
    hideLabel: false,
  },
)

const emit = defineEmits<{ 'update:modelValue': [value: string] }>()
const generatedId = useId()
const controlId = computed(() => props.id ?? `v2-select-${generatedId}`)
const hintId = computed(() => `${controlId.value}-hint`)
const errorId = computed(() => `${controlId.value}-error`)
const describedBy = computed(() =>
  props.error ? errorId.value : props.hint ? hintId.value : undefined,
)
const dropdown = ref<HTMLDetailsElement | null>(null)
const trigger = ref<HTMLElement | null>(null)
const menu = ref<HTMLElement | null>(null)
const open = ref(false)
const dropUp = ref(false)
const menuStyle = ref<CSSProperties>({})
const renderedOptions = computed(() => {
  if (!props.allowEmpty)
    return [{ value: '', label: props.placeholder, disabled: true }, ...props.options]
  return props.options.some((option) => option.value === '')
    ? props.options
    : [{ value: '', label: props.placeholder }, ...props.options]
})
const selectedLabel = computed(
  () =>
    renderedOptions.value.find((option) => option.value === props.modelValue)?.label ??
    props.placeholder,
)

function close(event?: FocusEvent): void {
  const next = event?.relatedTarget as Node | null
  if (next && (dropdown.value?.contains(next) || menu.value?.contains(next))) return
  if (dropdown.value) dropdown.value.open = false
  open.value = false
  dropUp.value = false
  menuStyle.value = {}
}

function closeAndFocusTrigger(): void {
  close()
  void nextTick(() => trigger.value?.focus())
}

function select(option: V2SelectOption): void {
  if (option.disabled) return
  emit('update:modelValue', option.value)
  closeAndFocusTrigger()
}

function onToggle(event: Event): void {
  open.value = (event.currentTarget as HTMLDetailsElement).open
  if (!open.value) {
    dropUp.value = false
    menuStyle.value = {}
    return
  }
  void nextTick(() => {
    const triggerRect = trigger.value?.getBoundingClientRect()
    const menuElement = menu.value
    if (!triggerRect || !menuElement) return
    const menuHeight = Math.min(
      Math.max(menuElement.scrollHeight, renderedOptions.value.length * 36 + 8),
      320,
    )
    const margin = 8
    const gap = 4
    const width = Math.min(triggerRect.width, window.innerWidth - margin * 2)
    const left = Math.min(Math.max(margin, triggerRect.left), window.innerWidth - width - margin)
    const spaceBelow = Math.max(0, window.innerHeight - triggerRect.bottom - margin)
    const spaceAbove = Math.max(0, triggerRect.top - margin)
    dropUp.value = spaceBelow < menuHeight && spaceAbove > spaceBelow
    const availableHeight = dropUp.value ? spaceAbove : spaceBelow
    menuStyle.value = {
      position: 'fixed',
      insetInlineStart: `${left}px`,
      width: `${width}px`,
      maxHeight: `${Math.max(72, Math.min(menuHeight, availableHeight - gap))}px`,
      insetBlockStart: dropUp.value ? 'auto' : `${triggerRect.bottom + gap}px`,
      insetBlockEnd: dropUp.value ? `${window.innerHeight - triggerRect.top + gap}px` : 'auto',
    }
  })
}

function enabledOptionButtons(): HTMLButtonElement[] {
  return Array.from(
    menu.value?.querySelectorAll<HTMLButtonElement>('[role="option"]:not(:disabled)') ?? [],
  )
}

function focusOptionAt(index: number): void {
  const options = enabledOptionButtons()
  if (!options.length) return
  options[(index + options.length) % options.length]?.focus()
}

function onTriggerKeydown(event: KeyboardEvent): void {
  if (props.disabled || !['ArrowDown', 'ArrowUp', 'Home', 'End'].includes(event.key)) return
  event.preventDefault()
  if (dropdown.value) dropdown.value.open = true
  open.value = true
  void nextTick(() => {
    focusOptionAt(event.key === 'ArrowUp' || event.key === 'End' ? -1 : 0)
  })
}

function onOptionKeydown(event: KeyboardEvent): void {
  if (!['ArrowDown', 'ArrowUp', 'Home', 'End'].includes(event.key)) return
  const options = enabledOptionButtons()
  const current = options.indexOf(event.target as HTMLButtonElement)
  if (current < 0) return
  event.preventDefault()
  if (event.key === 'Home') {
    focusOptionAt(0)
    return
  }
  if (event.key === 'End') {
    focusOptionAt(-1)
    return
  }
  focusOptionAt(current + (event.key === 'ArrowDown' ? 1 : -1))
}
</script>

<template>
  <div class="v2-field">
    <span v-if="label" class="v2-field__label" :class="{ 'v2-visually-hidden': hideLabel }">
      {{ label }}<span v-if="required" class="v2-field__required" aria-hidden="true">*</span>
    </span>
    <details
      ref="dropdown"
      class="v2-select"
      :class="{ 'is-disabled': disabled, 'is-drop-up': dropUp }"
      @toggle="onToggle"
      @focusout="close"
      @keydown.esc.prevent="closeAndFocusTrigger"
    >
      <summary
        :id="controlId"
        ref="trigger"
        role="button"
        class="v2-field__control v2-select__trigger"
        :aria-label="label ? `${label}：${selectedLabel}` : selectedLabel"
        :aria-expanded="open"
        :aria-disabled="disabled"
        :aria-required="required"
        :aria-invalid="Boolean(error)"
        :aria-describedby="describedBy"
        @click="disabled && $event.preventDefault()"
        @keydown="onTriggerKeydown"
      >
        {{ selectedLabel }}
      </summary>
      <Teleport to="body" :disabled="!open">
        <div
          v-show="open"
          ref="menu"
          class="v2-select__menu"
          role="listbox"
          :style="menuStyle"
          :aria-label="label || placeholder"
          @keydown="onOptionKeydown"
          @keydown.esc.prevent="closeAndFocusTrigger"
        >
          <button
            v-for="option in renderedOptions"
            :key="option.value"
            type="button"
            role="option"
            :data-value="option.value"
            :disabled="option.disabled"
            :aria-selected="modelValue === option.value"
            @click="select(option)"
          >
            {{ option.label }}
          </button>
        </div>
      </Teleport>
    </details>
    <span v-if="error" :id="errorId" class="v2-field__error">{{ error }}</span>
    <span v-else-if="hint" :id="hintId" class="v2-field__hint">{{ hint }}</span>
  </div>
</template>
