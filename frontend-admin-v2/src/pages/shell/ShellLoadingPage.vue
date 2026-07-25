<script setup lang="ts">
import V2PageState from '@/components/V2PageState.vue'
</script>

<template>
  <V2PageState
    kind="loading"
    :heading-level="1"
    title="正在加载工作区"
    description="正在准备页面结构，不会在此阶段发起业务写操作。"
  />
</template>
