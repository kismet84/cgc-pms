<script setup lang="ts">
import { RouterLink } from 'vue-router'
import V2Button from '@/components/V2Button.vue'
import V2PageState from '@/components/V2PageState.vue'
</script>

<template>
  <V2PageState
    code="404"
    kind="empty"
    title="页面不存在"
    description="地址可能已变更，或该页面尚未纳入新版应用壳。"
  >
    <template #actions>
      <RouterLink to="/session" custom v-slot="{ navigate }">
        <V2Button @click="navigate">返回工作台</V2Button>
      </RouterLink>
    </template>
  </V2PageState>
</template>
