<script setup lang="ts">
import { onMounted } from 'vue'
import type { ContractStatus } from '@/types/contract'
import { fetchDictData, getDictLabelSync, getDictTagColorSync } from '@/utils/dict'

defineProps<{ status: ContractStatus }>()

const DICT_CODE = 'contract_status'
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

onMounted(() => {
  fetchDictData(DICT_CODE)
})
</script>

<template>
  <a-tag :color="getDictTagColorSync(DICT_CODE, status, color)">
    {{ getDictLabelSync(DICT_CODE, status, label) }}
  </a-tag>
</template>
