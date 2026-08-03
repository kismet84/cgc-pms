<script setup lang="ts">
import { computed, useId } from 'vue'

const props = withDefaults(
  defineProps<{
    modelValue?: string
    id?: string
    label?: string
    hideLabel?: boolean
    type?: 'text' | 'email' | 'password' | 'search' | 'tel' | 'url'
    placeholder?: string
    hint?: string
    error?: string
    disabled?: boolean
    loading?: boolean
    required?: boolean
    autocomplete?: string
    decimalScale?: 2
  }>(),
  {
    modelValue: '',
    id: undefined,
    label: undefined,
    hideLabel: false,
    type: 'text',
    placeholder: undefined,
    hint: undefined,
    error: undefined,
    disabled: false,
    loading: false,
    required: false,
    autocomplete: undefined,
    decimalScale: undefined,
  },
)

const emit = defineEmits<{
  'update:modelValue': [value: string]
}>()

const generatedId = useId()
const controlId = computed(() => props.id ?? `v2-input-${generatedId}`)
const hintId = computed(() => `${controlId.value}-hint`)
const errorId = computed(() => `${controlId.value}-error`)
const describedBy = computed(() => {
  if (props.error) return errorId.value
  if (props.hint) return hintId.value
  return undefined
})

function onInput(event: Event) {
  emit('update:modelValue', (event.target as HTMLInputElement).value)
}

function onBlur(event: FocusEvent) {
  if (props.decimalScale !== 2) return
  const value = (event.target as HTMLInputElement).value
  const match = /^(-?\d+)(?:\.(\d{1,2}))?$/.exec(value)
  if (!match) return
  const formatted = `${match[1]}.${(match[2] ?? '').padEnd(2, '0')}`
  if (formatted !== value) emit('update:modelValue', formatted)
}
</script>

<template>
  <label class="v2-field" :for="controlId">
    <span v-if="label" class="v2-field__label" :class="{ 'v2-visually-hidden': hideLabel }">
      {{ label }}<span v-if="required" class="v2-field__required" aria-hidden="true">*</span>
    </span>
    <span class="v2-field__control-wrap">
      <input
        :id="controlId"
        class="v2-field__control"
        :value="modelValue"
        :type="type"
        :placeholder="placeholder"
        :disabled="disabled"
        :required="required"
        :autocomplete="autocomplete"
        :inputmode="decimalScale === 2 ? 'decimal' : undefined"
        :aria-label="label || placeholder"
        :aria-invalid="Boolean(error)"
        :aria-describedby="describedBy"
        :aria-busy="loading"
        @input="onInput"
        @blur="onBlur"
      />
      <span v-if="loading" class="v2-field__loading v2-spinner" aria-hidden="true"></span>
    </span>
    <span v-if="error" :id="errorId" class="v2-field__error">{{ error }}</span>
    <span v-else-if="hint" :id="hintId" class="v2-field__hint">{{ hint }}</span>
  </label>
</template>
