<script setup lang="ts">
import { watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

export interface V2TabOption {
  value: string
  label: string
  count?: number
}

const props = withDefaults(
  defineProps<{
    modelValue: string
    tabs: V2TabOption[]
    idPrefix: string
    ariaLabel?: string
    queryKey?: string
  }>(),
  {
    ariaLabel: '页面分区',
    queryKey: 'tab',
  },
)

const emit = defineEmits<{
  'update:modelValue': [value: string]
}>()

const route = useRoute()
const router = useRouter()

function tabId(value: string) {
  return `${props.idPrefix}-tab-${value}`
}

function panelId(value: string) {
  return `${props.idPrefix}-panel-${value}`
}

function validValue(value: unknown) {
  const candidate = Array.isArray(value) ? value[0] : value
  return typeof candidate === 'string' && props.tabs.some((tab) => tab.value === candidate)
    ? candidate
    : props.tabs[0]?.value
}

async function activate(value: string, focus = false) {
  if (!props.tabs.some((tab) => tab.value === value)) return
  emit('update:modelValue', value)
  await router.push({
    query: { ...route.query, [props.queryKey]: value },
    hash: route.hash,
  })
  if (focus) document.getElementById(tabId(value))?.focus()
}

function onKeydown(event: KeyboardEvent, index: number) {
  if (!props.tabs.length) return
  let target = index
  if (event.key === 'ArrowRight') target = (index + 1) % props.tabs.length
  else if (event.key === 'ArrowLeft') target = (index - 1 + props.tabs.length) % props.tabs.length
  else if (event.key === 'Home') target = 0
  else if (event.key === 'End') target = props.tabs.length - 1
  else return
  event.preventDefault()
  void activate(props.tabs[target]!.value, true)
}

watch(
  [() => route.query[props.queryKey], () => props.tabs.map((tab) => tab.value).join('|')],
  async ([queryValue]) => {
    const value = validValue(queryValue)
    if (!value) return
    if (props.modelValue !== value) emit('update:modelValue', value)
    if ((Array.isArray(queryValue) ? queryValue[0] : queryValue) !== value) {
      await router.replace({
        query: { ...route.query, [props.queryKey]: value },
        hash: route.hash,
      })
    }
  },
  { immediate: true },
)
</script>

<template>
  <div class="v2-tabs" role="tablist" :aria-label="ariaLabel">
    <button
      v-for="(tab, index) in tabs"
      :id="tabId(tab.value)"
      :key="tab.value"
      type="button"
      role="tab"
      class="v2-tabs__tab"
      :class="{ 'is-active': modelValue === tab.value }"
      :aria-selected="modelValue === tab.value"
      :aria-controls="panelId(tab.value)"
      :tabindex="modelValue === tab.value ? 0 : -1"
      @click="activate(tab.value)"
      @keydown="onKeydown($event, index)"
      @keydown.enter.prevent="activate(tab.value)"
      @keydown.space.prevent="activate(tab.value)"
    >
      <span>{{ tab.label }}</span>
      <span v-if="tab.count !== undefined" class="v2-tabs__count">{{ tab.count }}</span>
    </button>
  </div>
</template>

<style scoped>
.v2-tabs {
  display: flex;
  gap: 4px;
  max-width: 100%;
  overflow-x: auto;
  overflow-y: hidden;
  padding: 0 2px;
  border-bottom: var(--v2-border-width) solid var(--v2-color-border);
  scrollbar-width: thin;
}

.v2-tabs__tab {
  position: relative;
  display: inline-flex;
  flex: 0 0 auto;
  align-items: center;
  gap: var(--v2-space-2);
  min-height: 42px;
  padding: 0 var(--v2-space-4);
  border: 0;
  border-radius: var(--v2-radius-sm) var(--v2-radius-sm) 0 0;
  background: transparent;
  color: var(--v2-color-text-secondary);
  font: var(--v2-font-weight-semibold) var(--v2-font-size-13) / var(--v2-line-height-ui)
    var(--v2-font-sans);
  white-space: nowrap;
  cursor: pointer;
}

.v2-tabs__tab:hover {
  color: var(--v2-color-primary);
  background: var(--v2-color-primary-soft);
}

.v2-tabs__tab:focus-visible {
  outline: 0;
  box-shadow: 0 0 0 3px var(--v2-color-focus-ring);
}

.v2-tabs__tab.is-active {
  color: var(--v2-color-primary);
}

.v2-tabs__tab.is-active::after {
  position: absolute;
  right: 10px;
  bottom: -1px;
  left: 10px;
  height: 3px;
  border-radius: 3px 3px 0 0;
  background: var(--v2-color-primary);
  content: '';
}

.v2-tabs__count {
  min-width: 20px;
  padding: 1px 6px;
  border-radius: 999px;
  background: var(--v2-color-surface-subtle);
  color: currentColor;
  font-size: var(--v2-font-size-12);
  line-height: var(--v2-line-height-ui);
  text-align: center;
}

.v2-tabs__tab.is-active .v2-tabs__count {
  background: var(--v2-color-primary-soft);
}
</style>
