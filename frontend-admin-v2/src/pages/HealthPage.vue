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
      <p class="eyebrow">CGC-PMS / V2 管理端</p>
      <h1 id="health-title">系统健康检查</h1>
      <p class="description">检查管理端静态资源与后端 API 代理状态。</p>
      <dl class="status-list">
        <div>
          <dt>V2 管理端</dt>
          <dd class="status-up">可用</dd>
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
