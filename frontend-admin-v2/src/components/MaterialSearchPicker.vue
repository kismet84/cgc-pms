<script setup lang="ts">
import type { MaterialRecord } from '@cgc-pms/frontend-contracts'
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { loadMaterials } from '@/services/supply-chain'
import V2Input from './V2Input.vue'
import V2Select from './V2Select.vue'

withDefaults(
  defineProps<{
    disabled?: boolean
  }>(),
  {
    disabled: false,
  },
)

const emit = defineEmits<{
  select: [material: MaterialRecord]
}>()

const keyword = ref('')
const selectedId = ref('')
const materials = ref<MaterialRecord[]>([])
const loading = ref(false)
const errorMessage = ref('')
const options = computed(() =>
  materials.value.map((material) => ({
    value: material.id,
    label: [material.materialName, material.specification || '—', material.unit || '—'].join(' · '),
  })),
)

let timer: ReturnType<typeof setTimeout> | undefined
let controller: AbortController | undefined
let generation = 0

watch(keyword, (value) => {
  if (timer) clearTimeout(timer)
  controller?.abort()
  errorMessage.value = ''
  const materialName = value.trim()
  if (!materialName) {
    materials.value = []
    loading.value = false
    return
  }
  timer = setTimeout(async () => {
    const currentGeneration = ++generation
    controller = new AbortController()
    loading.value = true
    try {
      const page = await loadMaterials(
        { pageNo: 1, pageSize: 50, status: 'ENABLE', materialName },
        controller.signal,
      )
      if (currentGeneration === generation) materials.value = page.records
    } catch (error) {
      if (!controller.signal.aborted && currentGeneration === generation) {
        materials.value = []
        errorMessage.value = error instanceof Error ? error.message : '材料搜索失败'
      }
    } finally {
      if (currentGeneration === generation) loading.value = false
    }
  }, 250)
})

function selectMaterial(value: string): void {
  selectedId.value = value
  const material = materials.value.find((item) => item.id === value)
  if (!material) return
  emit('select', material)
  selectedId.value = ''
  keyword.value = ''
}

onBeforeUnmount(() => {
  if (timer) clearTimeout(timer)
  controller?.abort()
})
</script>

<template>
  <div class="material-search-picker">
    <V2Input
      v-model="keyword"
      type="search"
      label="搜索材料名称"
      :hide-label="true"
      placeholder="搜索材料名称"
      autocomplete="off"
      :disabled="disabled"
      :loading="loading"
    />
    <V2Select
      :model-value="selectedId"
      label="选择材料"
      :hide-label="true"
      :options="options"
      placeholder="选择材料（名称 / 规格 / 单位）"
      :error="errorMessage"
      :disabled="disabled || loading || !options.length"
      @update:model-value="selectMaterial"
    />
  </div>
</template>

<style scoped>
.material-search-picker {
  display: grid;
  flex: 1 1 30rem;
  grid-template-columns: minmax(10rem, 1fr) minmax(16rem, 1.5fr);
  gap: var(--v2-space-2);
  max-width: 38rem;
}

@media (max-width: 48rem) {
  .material-search-picker {
    grid-template-columns: 1fr;
    max-width: none;
  }
}
</style>
