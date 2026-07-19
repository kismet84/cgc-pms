<script setup lang="ts">
import { RouterLink } from 'vue-router'
import V2Button from '@/components/V2Button.vue'
import V2PageState from '@/components/V2PageState.vue'

withDefaults(defineProps<{ occurrences?: number }>(), { occurrences: 1 })

defineEmits<{ retry: [] }>()
</script>

<template>
  <main class="global-error-page">
    <V2PageState
      code="500"
      kind="error"
      live="assertive"
      title="页面暂时无法显示"
      description="界面运行时发生异常。可重试当前页面，或返回安全入口。"
    >
      <template #actions>
        <V2Button @click="$emit('retry')">重试当前页面</V2Button>
        <RouterLink to="/session" custom v-slot="{ navigate }">
          <V2Button variant="secondary" @click="navigate">返回安全入口</V2Button>
        </RouterLink>
      </template>
      <p v-if="occurrences > 1" class="v2-page-state__technical-detail">
        同一异常已合并 {{ occurrences }} 次，未重复展示错误详情。
      </p>
    </V2PageState>
  </main>
</template>
