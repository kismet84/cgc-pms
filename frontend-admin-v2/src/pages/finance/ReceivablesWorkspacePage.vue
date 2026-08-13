<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import ExpenseApplicationPage from './receivables-workspace/ExpenseApplicationPage.vue'
import InvoiceManagementPage from './receivables-workspace/InvoiceManagementPage.vue'
import PaymentApplicationPage from './receivables-workspace/PaymentApplicationPage.vue'
import RevenueOperationsPage from './receivables-workspace/RevenueOperationsPage.vue'

const route = useRoute()
const page = computed(() =>
  route.path === '/payment/application'
    ? PaymentApplicationPage
    : route.path === '/payment/expense'
      ? ExpenseApplicationPage
      : route.path === '/invoice'
        ? InvoiceManagementPage
        : RevenueOperationsPage,
)
</script>

<template>
  <component :is="page" />
</template>
