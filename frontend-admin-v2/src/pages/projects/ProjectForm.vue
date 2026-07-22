<script setup lang="ts">
import type { ProjectUpsertCommand } from '@cgc-pms/frontend-contracts'
import { V2Input, V2Select, type V2SelectOption } from '@/components'

const props = defineProps<{ modelValue: ProjectUpsertCommand; typeOptions: V2SelectOption[] }>()
const emit = defineEmits<{ 'update:modelValue': [value: ProjectUpsertCommand] }>()
function set(key: keyof ProjectUpsertCommand, value: string) {
  emit('update:modelValue', { ...props.modelValue, [key]: value })
}
</script>

<template>
  <form class="project-form" @submit.prevent>
    <V2Input
      :model-value="modelValue.projectName"
      label="项目名称"
      required
      @update:model-value="set('projectName', $event)"
    />
    <V2Select
      :model-value="modelValue.projectType"
      label="项目类型"
      :options="typeOptions"
      required
      @update:model-value="set('projectType', $event)"
    />
    <V2Input
      :model-value="modelValue.projectAddress"
      label="项目地址"
      @update:model-value="set('projectAddress', $event)"
    />
    <V2Input
      :model-value="modelValue.ownerUnit"
      label="建设单位"
      @update:model-value="set('ownerUnit', $event)"
    />
    <V2Input
      :model-value="modelValue.supervisorUnit"
      label="监理单位"
      @update:model-value="set('supervisorUnit', $event)"
    />
    <V2Input
      :model-value="modelValue.designUnit"
      label="设计单位"
      @update:model-value="set('designUnit', $event)"
    />
    <V2Input
      :model-value="modelValue.contractAmount"
      label="合同金额（元）"
      @update:model-value="set('contractAmount', $event)"
    />
    <V2Input
      :model-value="modelValue.targetCost"
      label="目标成本（元）"
      @update:model-value="set('targetCost', $event)"
    />
    <label
      >计划开工<input
        :value="modelValue.plannedStartDate"
        type="date"
        @input="set('plannedStartDate', ($event.target as HTMLInputElement).value)"
    /></label>
    <label
      >计划完工<input
        :value="modelValue.plannedEndDate"
        type="date"
        @input="set('plannedEndDate', ($event.target as HTMLInputElement).value)"
    /></label>
  </form>
</template>

<style scoped>
.project-form {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: var(--v2-space-3);
  align-items: end;
}
.project-form--dialog {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}
.project-form label {
  display: grid;
  gap: var(--v2-space-1);
  color: var(--v2-color-text-secondary);
}
.project-form input {
  min-height: 2.5rem;
  padding: 0 var(--v2-space-3);
  color: var(--v2-color-text);
  background: var(--v2-color-surface);
  border: 1px solid var(--v2-color-border);
  border-radius: var(--v2-radius-md);
}
.project-form--dialog input {
  background: transparent;
  border-color: color-mix(in srgb, var(--v2-color-primary) 22%, var(--v2-color-surface));
  border-radius: var(--v2-radius-sm);
}
.project-form--dialog :deep(.v2-field__control) {
  background: transparent;
  border-color: color-mix(in srgb, var(--v2-color-primary) 22%, var(--v2-color-surface));
}
@media (max-width: 64rem) {
  .project-form {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
@media (max-width: 48rem) {
  .project-form {
    grid-template-columns: 1fr;
  }
}
</style>
