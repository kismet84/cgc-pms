<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { V2Alert, V2Badge, V2Card, V2Stack } from '@/components'
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
    <V2Card :title="title" subtitle="Clean-room V2 应用壳承载层">
      <V2Stack :gap="5">
        <div class="shell-placeholder__heading">
          <div>
            <p class="shell-placeholder__eyebrow">{{ domain }}</p>
            <h1 id="shell-page-title">{{ title }}</h1>
          </div>
          <V2Badge tone="info" dot>壳层已就绪</V2Badge>
        </div>
        <V2Alert title="业务页面尚未迁移" tone="warning">
          当前只验证导航、权限、深链与上下文。真实业务数据和操作将在对应垂直切片接入。
        </V2Alert>
        <dl class="shell-placeholder__context">
          <div>
            <dt>项目上下文</dt>
            <dd>{{ workspaceStore.selectedProjectId || '暂无可用项目' }}</dd>
          </div>
          <div>
            <dt>报告期</dt>
            <dd>{{ workspaceStore.selectedReportPeriod || '暂无可用报告期' }}</dd>
          </div>
          <div>
            <dt>对象上下文</dt>
            <dd>
              {{
                workspaceStore.objectContext
                  ? `${workspaceStore.objectContext.kind} / ${workspaceStore.objectContext.id}`
                  : '当前路由无对象'
              }}
            </dd>
          </div>
        </dl>
      </V2Stack>
    </V2Card>
  </section>
</template>

<style scoped>
.shell-placeholder {
  width: 100%;
}

.shell-placeholder__heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--v2-space-4);
}

.shell-placeholder__eyebrow {
  margin: 0 0 var(--v2-space-2);
  color: var(--v2-color-primary);
  font-size: var(--v2-font-size-11);
  font-weight: var(--v2-font-weight-bold);
  letter-spacing: 0.12em;
  text-transform: uppercase;
}

.shell-placeholder h1 {
  font-size: clamp(var(--v2-font-size-21), 3vw, var(--v2-font-size-28));
}

.shell-placeholder__context {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: var(--v2-space-3);
  margin: 0;
}

.shell-placeholder__context div {
  padding: var(--v2-space-4);
  background: var(--v2-color-surface-subtle);
  border: var(--v2-border-width) solid var(--v2-color-border-subtle);
  border-radius: var(--v2-radius-sm);
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
  margin-block-start: var(--v2-space-2);
  color: var(--v2-color-text-strong);
  font-size: var(--v2-font-size-13);
  font-weight: var(--v2-font-weight-semibold);
  overflow-wrap: anywhere;
}

@media (max-width: 48rem) {
  .shell-placeholder__context {
    grid-template-columns: 1fr;
  }
}
</style>
