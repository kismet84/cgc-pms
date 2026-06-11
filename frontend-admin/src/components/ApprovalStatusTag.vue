<script setup lang="ts">
import type { ApprovalStatus } from '@/types/contract'

defineProps<{ status: ApprovalStatus }>()

const label: Record<ApprovalStatus, string> = {
  DRAFT: '草稿',
  APPROVING: '审批中',
  APPROVED: '已通过',
  REJECTED: '已驳回',
  WITHDRAWN: '已撤回',
}

const color: Record<ApprovalStatus, string> = {
  DRAFT: 'default',
  APPROVING: 'processing',
  APPROVED: 'success',
  REJECTED: 'error',
  WITHDRAWN: 'warning',
}
</script>

<template>
  <a-tag :color="color[status]">{{ label[status] || status }}</a-tag>
</template>
