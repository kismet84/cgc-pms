<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import MaterialReceiptWorkspace from './purchase-execution/MaterialReceiptWorkspace.vue'
import PurchaseOrderWorkspace from './purchase-execution/PurchaseOrderWorkspace.vue'
import PurchaseRequestWorkspace from './purchase-execution/PurchaseRequestWorkspace.vue'

const route = useRoute()
const workspace = computed(() => {
  if (route.path === '/purchase/order') return PurchaseOrderWorkspace
  if (route.path === '/purchase/receipt') return MaterialReceiptWorkspace
  return PurchaseRequestWorkspace
})
</script>

<template>
  <component :is="workspace" />
</template>
