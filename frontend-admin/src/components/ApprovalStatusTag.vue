<script setup lang="ts">
defineProps<{ status: string }>()

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
</script>

<template>
  <a-tag :color="color[status]">{{ label[status] || status }}</a-tag>
</template>
