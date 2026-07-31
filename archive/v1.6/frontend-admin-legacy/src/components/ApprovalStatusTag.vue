<script setup lang="ts">
import { onMounted } from 'vue'
import { fetchDictData, getDictLabelSync, getDictTagColorSync } from '@/utils/dict'

defineProps<{ status: string }>()

const DICT_CODE = 'approval_status'
const label: Record<string, string> = {
  DRAFT: '草稿',
  APPROVING: '审批中',
  APPROVED: '已通过',
  REJECTED: '已驳回',
  WITHDRAWN: '已撤回',
}

const color: Record<string, string> = {
  DRAFT: 'default',
  APPROVING: 'processing',
  APPROVED: 'success',
  REJECTED: 'error',
  WITHDRAWN: 'warning',
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
