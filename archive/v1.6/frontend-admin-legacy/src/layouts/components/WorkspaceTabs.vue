<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useNavigationAccess } from '@/composables/useNavigationAccess'
import { findWorkspaceByPath } from '@/router/navigation'

const route = useRoute()
const router = useRouter()
const { getVisibleTabs } = useNavigationAccess()

const workspaceMatch = computed(() => findWorkspaceByPath(route.path))
const visibleTabs = computed(() => {
  const workspace = workspaceMatch.value?.workspace
  return workspace ? getVisibleTabs(workspace) : []
})
const activeKey = computed(() => visibleTabs.value.find((tab) => tab.key === route.path)?.key)
const usesPageOwnedTabs = computed(
  () => workspaceMatch.value?.workspace.key === '/workbench/my-work',
)
const isVisible = computed(
  () => visibleTabs.value.length > 1 && Boolean(activeKey.value) && !usesPageOwnedTabs.value,
)

function handleChange(key: string | number) {
  const target = visibleTabs.value.find((tab) => tab.key === String(key))
  if (!target || target.key === route.path) return
  router.push({ path: target.key, query: route.query, hash: route.hash })
}
</script>

<template>
  <section
    v-if="isVisible"
    class="workspace-tabs"
    :aria-label="`${workspaceMatch?.workspace.label}导航`"
  >
    <div class="workspace-tabs__context">
      <span class="workspace-tabs__domain">{{ workspaceMatch?.domain.label }}</span>
      <span class="workspace-tabs__separator" aria-hidden="true">/</span>
      <strong class="workspace-tabs__title">{{ workspaceMatch?.workspace.label }}</strong>
    </div>
    <a-tabs :active-key="activeKey" class="workspace-tabs__control" @change="handleChange">
      <a-tab-pane v-for="tab in visibleTabs" :key="tab.key" :tab="tab.label" />
    </a-tabs>
  </section>
</template>

<style scoped>
.workspace-tabs {
  position: sticky;
  top: 0;
  z-index: 12;
  display: flex;
  align-items: center;
  gap: 24px;
  min-height: 54px;
  padding: 0 24px;
  background: color-mix(in srgb, var(--surface) 94%, transparent);
  border-bottom: 1px solid var(--border);
  backdrop-filter: blur(12px);
}

.workspace-tabs__context {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: 0 0 auto;
  color: var(--text-secondary);
  font-size: 13px;
}

.workspace-tabs__title {
  color: var(--text);
}

.workspace-tabs__separator {
  color: var(--muted);
}

.workspace-tabs__control {
  min-width: 0;
  flex: 1 1 auto;
}

.workspace-tabs__control :deep(.ant-tabs-nav) {
  margin: 0;
}

.workspace-tabs__control :deep(.ant-tabs-content-holder) {
  display: none;
}

@media (width < 500px) {
  .workspace-tabs {
    position: static;
    display: block;
    min-height: auto;
    padding: 8px 12px 0;
    overflow-x: auto;
  }

  .workspace-tabs__context {
    margin-bottom: 4px;
  }

  .workspace-tabs__control {
    min-width: max-content;
  }
}
</style>
