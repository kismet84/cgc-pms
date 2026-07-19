<script setup lang="ts">
import { computed, useId } from 'vue'
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
  },
)

const emit = defineEmits<{
  'update:modelValue': [value: string]
}>()

const generatedId = useId()
const controlId = computed(() => props.id ?? `v2-select-${generatedId}`)
const hintId = computed(() => `${controlId.value}-hint`)
const errorId = computed(() => `${controlId.value}-error`)
const describedBy = computed(() => {
  if (props.error) return errorId.value
  if (props.hint) return hintId.value
  return undefined
})

function onChange(event: Event) {
  emit('update:modelValue', (event.target as HTMLSelectElement).value)
}
</script>

<template>
  <label class="v2-field" :for="controlId">
    <span v-if="label" class="v2-field__label">
      {{ label }}<span v-if="required" class="v2-field__required" aria-hidden="true">*</span>
    </span>
    <select
      :id="controlId"
      class="v2-field__control v2-select"
      :value="modelValue"
      :disabled="disabled"
      :required="required"
      :aria-label="label || placeholder"
      :aria-invalid="Boolean(error)"
      :aria-describedby="describedBy"
      @change="onChange"
    >
      <option value="" disabled>{{ placeholder }}</option>
      <option
        v-for="option in options"
        :key="option.value"
        :value="option.value"
        :disabled="option.disabled"
      >
        {{ option.label }}
      </option>
    </select>
    <span v-if="error" :id="errorId" class="v2-field__error">{{ error }}</span>
    <span v-else-if="hint" :id="hintId" class="v2-field__hint">{{ hint }}</span>
  </label>
</template>
