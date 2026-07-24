<script setup lang="ts">
import { RouterView } from 'vue-router'
import { V2ErrorBoundary, V2ToastHost } from '@/components'
import ShellLoadingPage from '@/pages/shell/ShellLoadingPage.vue'
</script>

<template>
  <V2ErrorBoundary>
    <RouterView v-slot="{ Component }">
      <Suspense>
        <div class="v2-route-frame"><component :is="Component" /></div>
        <template #fallback><ShellLoadingPage /></template>
      </Suspense>
    </RouterView>
  </V2ErrorBoundary>
  <V2ToastHost />
</template>

<style>
.v2-route-frame {
  min-height: 100vh;
}
</style>
