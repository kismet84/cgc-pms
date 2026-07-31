<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useNavigationAccess } from '@/composables/useNavigationAccess'
import type { NavigationAccess } from '@/router/navigation'

interface ContextTab extends NavigationAccess {
  key: string
  label: string
}

interface ObjectContext {
  backLabel: string
  backPath: string
  label: string
  tabs: ContextTab[]
}

const route = useRoute()
const router = useRouter()
const { canAccess } = useNavigationAccess()

const context = computed<ObjectContext | undefined>(() => {
  const projectMatch = route.path.match(/^\/project\/([^/]+)\/(overview|members|edit)$/)
  if (projectMatch) {
    const base = `/project/${projectMatch[1]}`
    return {
      label: '项目对象',
      backLabel: '返回项目列表',
      backPath: '/project/list',
      tabs: [
        { key: `${base}/overview`, label: '项目总览', permission: 'project:query' },
        { key: `${base}/members`, label: '项目成员', permission: 'project:member:list' },
        { key: `${base}/edit`, label: '编辑项目', permission: 'project:edit' },
      ],
    }
  }

  const contractMatch = route.path.match(/^\/contract\/([^/]+)(?:\/(edit))?$/)
  if (contractMatch && !['ledger', 'create'].includes(contractMatch[1])) {
    const base = `/contract/${contractMatch[1]}`
    return {
      label: '合同对象',
      backLabel: '返回合同台账',
      backPath: '/contract/ledger',
      tabs: [
        { key: base, label: '合同详情', permission: 'contract:query' },
        { key: `${base}/edit`, label: '编辑合同', permission: 'contract:edit' },
      ],
    }
  }

  const settlementMatch = route.path.match(/^\/settlement\/([^/]+)$/)
  if (settlementMatch && settlementMatch[1] !== 'list') {
    return {
      label: '结算对象',
      backLabel: '返回结算台账',
      backPath: '/settlement/list',
      tabs: [{ key: route.path, label: '结算详情', permission: 'settlement:query' }],
    }
  }

  return undefined
})

const visibleTabs = computed(() => context.value?.tabs.filter((tab) => canAccess(tab)) || [])

function handleChange(key: string | number) {
  const target = visibleTabs.value.find((tab) => tab.key === String(key))
  if (target && target.key !== route.path) {
    router.push({ path: target.key, query: route.query, hash: route.hash })
  }
}
</script>

<template>
  <nav v-if="context" class="object-context" :aria-label="`${context.label}导航`">
    <button type="button" class="object-context__back" @click="router.push(context.backPath)">
      {{ context.backLabel }}
    </button>
    <a-tabs
      v-if="visibleTabs.length > 1"
      :active-key="route.path"
      class="object-context__tabs"
      @change="handleChange"
    >
      <a-tab-pane v-for="tab in visibleTabs" :key="tab.key" :tab="tab.label" />
    </a-tabs>
    <strong v-else class="object-context__title">{{
      visibleTabs[0]?.label || context.label
    }}</strong>
  </nav>
</template>

<style scoped>
.object-context {
  display: flex;
  align-items: center;
  gap: 20px;
  min-height: 50px;
  padding: 0 24px;
  background: var(--surface);
  border-bottom: 1px solid var(--border);
}

.object-context__back {
  flex: 0 0 auto;
  padding: 6px 10px;
  color: var(--primary);
  background: transparent;
  border: 0;
  border-radius: var(--radius-md);
  cursor: pointer;
}

.object-context__back:hover {
  background: var(--primary-hover-bg);
}

.object-context__tabs {
  min-width: 0;
  flex: 1 1 auto;
}

.object-context__tabs :deep(.ant-tabs-nav) {
  margin: 0;
}

.object-context__tabs :deep(.ant-tabs-content-holder) {
  display: none;
}

.object-context__title {
  color: var(--text);
  font-size: 14px;
}

@media (width < 500px) {
  .object-context {
    padding: 0 12px;
    overflow-x: auto;
  }
}
</style>
