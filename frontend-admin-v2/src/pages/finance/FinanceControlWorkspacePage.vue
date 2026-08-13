<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import AccountingEntryPage from './finance-control-workspace/AccountingEntryPage.vue'
import CashForecastPage from './finance-control-workspace/CashForecastPage.vue'
import CashJournalPage from './finance-control-workspace/CashJournalPage.vue'
import FinanceOperationsPage from './finance-control-workspace/FinanceOperationsPage.vue'
import FinancialClosePage from './finance-control-workspace/FinancialClosePage.vue'
import FundAccountsPage from './finance-control-workspace/FundAccountsPage.vue'

const route = useRoute()
const page = computed(() =>
  route.path === '/fund-accounts'
    ? FundAccountsPage
    : route.path === '/cash-journal'
      ? CashJournalPage
      : route.path === '/cash-forecast'
        ? CashForecastPage
        : route.path === '/accounting-entry'
          ? AccountingEntryPage
          : route.path === '/financial-close'
            ? FinancialClosePage
            : FinanceOperationsPage,
)
</script>

<template>
  <component :is="page" />
</template>
