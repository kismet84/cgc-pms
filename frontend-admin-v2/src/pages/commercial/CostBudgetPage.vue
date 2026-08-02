<script setup lang="ts">
import { V2Card } from '@/components'
import CostTargetPage from './CostTargetPage.vue'
</script>

<template>
  <div class="cost-budget-page">
    <V2Card title="项目成本预算" :heading-level="1" />
    <CostTargetPage embedded />
  </div>
</template>

<style scoped>
.cost-budget-page {
  display: grid;
  gap: var(--v2-space-4);
}
</style>
