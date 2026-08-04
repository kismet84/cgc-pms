<script setup lang="ts">
import CostTargetPage from './CostTargetPage.vue'
</script>

<template>
  <div class="cost-budget-page">
    <CostTargetPage embedded />
  </div>
</template>

<style scoped>
.cost-budget-page {
  display: grid;
  gap: var(--v2-space-4);
}
</style>
