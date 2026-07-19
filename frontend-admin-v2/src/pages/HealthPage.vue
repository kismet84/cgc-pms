<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { probeBackendHealth, type BackendHealth } from '@/services/health'

const backendHealth = ref<BackendHealth>('checking')
const statusText = computed(() => {
  if (backendHealth.value === 'checking') return '检测中'
  if (backendHealth.value === 'up') return '后端 API 可达'
  return '后端 API 暂不可达'
})

onMounted(async () => {
  backendHealth.value = await probeBackendHealth()
})
</script>

<template>
  <main class="health-page">
    <section class="health-card" aria-labelledby="health-title">
      <p class="eyebrow">CGC-PMS / CLEAN-ROOM V2</p>
      <h1 id="health-title">隔离底座已启动</h1>
      <p class="description">当前仅提供静态健康检查。业务页面、旧组件和旧样式尚未接入。</p>
      <dl class="status-list">
        <div>
          <dt>V2 静态应用</dt>
          <dd class="status-up">可用</dd>
        </div>
        <div>
          <dt>Legacy UI 依赖</dt>
          <dd class="status-up">0</dd>
        </div>
        <div>
          <dt>API 代理</dt>
          <dd :class="backendHealth === 'up' ? 'status-up' : 'status-muted'" aria-live="polite">
            {{ statusText }}
          </dd>
        </div>
      </dl>
    </section>
  </main>
</template>
