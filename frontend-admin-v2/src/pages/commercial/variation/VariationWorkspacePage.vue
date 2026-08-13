<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import VariationDetailPage from './VariationDetailPage.vue'
import VariationEditorPage from './VariationEditorPage.vue'
import VariationLedgerPage from './VariationLedgerPage.vue'

type WorkspaceMode = 'list' | 'create' | 'detail' | 'edit'

const route = useRoute()
const mode = computed<WorkspaceMode>(() => {
  const requested = typeof route.query.mode === 'string' ? route.query.mode : ''
  return requested === 'create' || requested === 'detail' || requested === 'edit'
    ? requested
    : 'list'
})
</script>

<template>
  <div class="variation-page">
    <VariationLedgerPage />
    <VariationEditorPage v-if="mode === 'create' || mode === 'edit'" :mode="mode" />
    <VariationDetailPage v-if="mode === 'detail'" />
  </div>
</template>

<style src="./variation-page.css"></style>
