<script setup lang="ts">
import { RouterLink, useRoute } from 'vue-router'
import V2Button from '@/components/V2Button.vue'
import V2PageState from '@/components/V2PageState.vue'

const route = useRoute()
</script>

<template>
  <V2PageState
    code="403"
    kind="error"
    title="无权访问此页面"
    description="当前账号缺少该页面所需权限。系统没有加载业务页面或业务数据。"
  >
    <template #actions>
      <RouterLink to="/session" custom v-slot="{ navigate }">
        <V2Button @click="navigate">返回可访问工作区</V2Button>
      </RouterLink>
    </template>
    <p v-if="route.query.from" class="v2-page-state__technical-detail">
      已阻断路径：{{ route.query.from }}
    </p>
  </V2PageState>
</template>
