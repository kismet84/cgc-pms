<script setup lang="ts">
import type { ContractStatus } from '@/types/contract'

defineProps<{ status: ContractStatus }>()

const label: Record<ContractStatus, string> = {
  DRAFT: '草稿',
  PERFORMING: '履约中',
  SETTLED: '已结算',
  TERMINATED: '已终止',
}

const color: Record<ContractStatus, string> = {
  DRAFT: 'processing',
  PERFORMING: 'success',
  SETTLED: 'default',
  TERMINATED: 'warning',
}
</script>

<template>
  <a-tag :color="color[status]">{{ label[status] || status }}</a-tag>
</template>
