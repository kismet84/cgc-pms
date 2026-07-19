<script setup lang="ts">
import { onErrorCaptured, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import GlobalErrorPage from '@/pages/errors/GlobalErrorPage.vue'

interface CapturedError {
  fingerprint: string
  occurrences: number
}

const route = useRoute()
const captured = ref<CapturedError | null>(null)
const renderKey = ref(0)

onErrorCaptured((error) => {
  const fingerprint = errorFingerprint(error)
  captured.value = {
    fingerprint,
    occurrences: captured.value?.fingerprint === fingerprint ? captured.value.occurrences + 1 : 1,
  }
  return false
})

watch(
  () => route.fullPath,
  () => reset(),
)

function reset(): void {
  captured.value = null
  renderKey.value += 1
}

function errorFingerprint(error: unknown): string {
  if (error instanceof Error) return `${error.name}:${error.message.slice(0, 80)}`
  return typeof error === 'string' ? `Error:${error.slice(0, 80)}` : 'Error:unknown'
}
</script>

<template>
  <GlobalErrorPage v-if="captured" :occurrences="captured.occurrences" @retry="reset" />
  <div v-else :key="renderKey" class="v2-error-boundary__content">
    <slot />
  </div>
</template>
