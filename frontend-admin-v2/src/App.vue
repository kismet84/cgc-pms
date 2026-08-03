<script setup lang="ts">
import { RouterView } from 'vue-router'
import { V2ErrorBoundary, V2ToastHost } from '@/components'
</script>

<template>
  <V2ErrorBoundary>
    <RouterView v-slot="{ Component }">
      <div class="v2-route-frame"><component :is="Component" /></div>
    </RouterView>
  </V2ErrorBoundary>
  <V2ToastHost />
</template>

<style>
.v2-route-frame {
  min-height: 100vh;
}
</style>
