<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import ContractCreatePage from './contract/ContractCreatePage.vue'
import ContractDetailPage from './contract/ContractDetailPage.vue'
import ContractEditPage from './contract/ContractEditPage.vue'
import ContractLedgerPage from './contract/ContractLedgerPage.vue'

const route = useRoute()
const routePage = computed(() => {
  if (route.path === '/contract/ledger') return ContractLedgerPage
  if (route.path === '/contract/create') return ContractCreatePage
  if (route.path.endsWith('/edit')) return ContractEditPage
  return ContractDetailPage
})
</script>

<template>
  <component :is="routePage" />
</template>
