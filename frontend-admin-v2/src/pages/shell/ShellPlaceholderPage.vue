<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { V2Alert, V2Badge } from '@/components'
import { findWorkspace } from '@/navigation/catalog'
import { useWorkspaceStore } from '@/stores/workspace'

const route = useRoute()
const workspaceStore = useWorkspaceStore()
const match = computed(() => findWorkspace(route.path))
const title = computed(() => match.value?.workspace.label ?? '应用工作区')
const domain = computed(() => match.value?.domain.label ?? '全局')
</script>

<template>
  <section class="shell-placeholder" aria-labelledby="shell-page-title">
    <header class="shell-placeholder__heading">
      <div>
        <p class="shell-placeholder__eyebrow">{{ domain }}</p>
        <h1 id="shell-page-title">{{ title }}</h1>
        <p class="shell-placeholder__description">工作区结构与权限导航已就绪。</p>
      </div>
      <V2Badge tone="info" dot>壳层就绪</V2Badge>
    </header>

    <div class="shell-placeholder__body">
      <V2Alert title="业务页面建设中" tone="info">
        当前验证导航、权限、深链与上下文；真实业务数据和操作将在对应垂直切片接入。
      </V2Alert>
      <dl class="shell-placeholder__context">
        <div>
          <dt>项目</dt>
          <dd>{{ workspaceStore.selectedProjectId || '暂无可用项目' }}</dd>
        </div>
        <div>
          <dt>报告期</dt>
          <dd>{{ workspaceStore.selectedReportPeriod || '暂无可用报告期' }}</dd>
        </div>
        <div>
          <dt>对象</dt>
          <dd>
            {{
              workspaceStore.objectContext
                ? `${workspaceStore.objectContext.kind} / ${workspaceStore.objectContext.id}`
                : '当前路由无对象'
            }}
          </dd>
        </div>
      </dl>
    </div>
  </section>
</template>

<style scoped>
.shell-placeholder {
  width: 100%;
  overflow: hidden;
  background: var(--v2-color-surface);
  border: var(--v2-border-width) solid var(--v2-color-border);
  border-radius: var(--v2-radius-md);
  box-shadow: var(--v2-shadow-panel);
}

.shell-placeholder__heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--v2-space-5);
  padding: var(--v2-space-5) var(--v2-space-6);
  border-block-end: var(--v2-border-width) solid var(--v2-color-border-subtle);
}

.shell-placeholder__eyebrow {
  margin: 0 0 var(--v2-space-1);
  color: var(--v2-color-primary);
  font-size: var(--v2-font-size-11);
  font-weight: var(--v2-font-weight-bold);
  letter-spacing: 0.12em;
  text-transform: uppercase;
}

.shell-placeholder h1 {
  margin: 0;
  font-size: clamp(var(--v2-font-size-21), 2vw, var(--v2-font-size-28));
}

.shell-placeholder__description {
  margin: var(--v2-space-2) 0 0;
  color: var(--v2-color-text-muted);
  font-size: var(--v2-font-size-12);
}

.shell-placeholder__body {
  display: grid;
  gap: var(--v2-space-4);
  padding: var(--v2-space-5) var(--v2-space-6) var(--v2-space-6);
}

.shell-placeholder__context {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: var(--v2-space-3);
  margin: 0;
}

.shell-placeholder__context div {
  min-width: 0;
  padding-inline-start: var(--v2-space-3);
  border-inline-start: 2px solid var(--v2-color-border);
}

.shell-placeholder__context dt,
.shell-placeholder__context dd {
  margin: 0;
}

.shell-placeholder__context dt {
  color: var(--v2-color-text-muted);
  font-size: var(--v2-font-size-11);
}

.shell-placeholder__context dd {
  margin-block-start: var(--v2-space-1);
  color: var(--v2-color-text-strong);
  font-size: var(--v2-font-size-13);
  font-weight: var(--v2-font-weight-semibold);
  overflow-wrap: anywhere;
}

@media (max-width: 48rem) {
  .shell-placeholder__heading,
  .shell-placeholder__body {
    padding: var(--v2-space-4);
  }

  .shell-placeholder__context {
    grid-template-columns: 1fr;
  }
}
</style>
