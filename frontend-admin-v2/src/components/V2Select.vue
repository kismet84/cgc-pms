<script setup lang="ts">
import { computed, ref, useId } from 'vue'
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
const open = ref(false)
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
  if (next && dropdown.value?.contains(next)) return
  if (dropdown.value) dropdown.value.open = false
  open.value = false
}

function select(option: V2SelectOption): void {
  if (option.disabled) return
  emit('update:modelValue', option.value)
  close()
}

function onToggle(event: Event): void {
  open.value = (event.currentTarget as HTMLDetailsElement).open
}
</script>

<template>
  <div class="v2-field">
    <span v-if="label" class="v2-field__label">
      {{ label }}<span v-if="required" class="v2-field__required" aria-hidden="true">*</span>
    </span>
    <details
      ref="dropdown"
      class="v2-select"
      :class="{ 'is-disabled': disabled }"
      @toggle="onToggle"
      @focusout="close"
      @keydown.esc.prevent="close()"
    >
      <summary
        :id="controlId"
        role="button"
        class="v2-field__control v2-select__trigger"
        :aria-label="label ? `${label}：${selectedLabel}` : selectedLabel"
        :aria-expanded="open"
        :aria-disabled="disabled"
        :aria-required="required"
        :aria-invalid="Boolean(error)"
        :aria-describedby="describedBy"
        @click="disabled && $event.preventDefault()"
      >
        {{ selectedLabel }}
      </summary>
      <div class="v2-select__menu" role="listbox" :aria-label="label || placeholder">
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
    </details>
    <span v-if="error" :id="errorId" class="v2-field__error">{{ error }}</span>
    <span v-else-if="hint" :id="hintId" class="v2-field__hint">{{ hint }}</span>
  </div>
</template>
