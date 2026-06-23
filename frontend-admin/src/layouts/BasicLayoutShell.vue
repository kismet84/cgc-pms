<template>
  <div class="layout-shell">
    <div v-if="showLoading" class="layout-shell__loading">
      <div class="layout-shell__spinner"></div>
      <span class="layout-shell__text">加载中...</span>
    </div>
    <RouterView />
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'

const showLoading = ref(true)

onMounted(() => {
  // Brief loading overlay while async layout chunk downloads.
  // The shell provides instant first paint; once the async layout
  // resolves via router dynamic import, RouterView renders it.
  setTimeout(() => {
    showLoading.value = false
  }, 1500)
})
</script>

<style scoped>
.layout-shell {
  min-height: 100vh;
  background: var(--bg);
}

.layout-shell__loading {
  position: fixed;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  background: var(--bg);
  z-index: 100;
  transition: opacity 0.3s ease;
}

.layout-shell__spinner {
  width: 32px;
  height: 32px;
  border: 3px solid var(--border-subtle);
  border-top-color: var(--primary);
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
  margin-bottom: 12px;
}

.layout-shell__text {
  font-size: 14px;
  color: var(--muted);
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}
</style>
