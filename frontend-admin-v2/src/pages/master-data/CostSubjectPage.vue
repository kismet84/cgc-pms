<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import CostSubjectRulesPage from './cost-subject/CostSubjectRulesPage.vue'
import CostSubjectScopePage from './cost-subject/CostSubjectScopePage.vue'
import CostSubjectTaxonomyPage from './cost-subject/CostSubjectTaxonomyPage.vue'
import CostSubjectTracePage from './cost-subject/CostSubjectTracePage.vue'

const route = useRoute()
const page = computed(() => {
  if (route.path.endsWith('/rules')) return CostSubjectRulesPage
  if (route.path.endsWith('/scope')) return CostSubjectScopePage
  if (route.path.endsWith('/trace')) return CostSubjectTracePage
  return CostSubjectTaxonomyPage
})
</script>

<template>
  <component :is="page" />
</template>
