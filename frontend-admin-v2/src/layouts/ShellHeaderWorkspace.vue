<script setup lang="ts">
import { ref } from 'vue'
import { RouterLink, type LocationQueryRaw, type RouteLocationRaw } from 'vue-router'
import { V2ActionMenu, V2Button, V2Select, type V2SelectOption } from '@/components'
import DomainNavigationIcon from '@/components/DomainNavigationIcon.vue'
import type { VisibleWorkspace, WorkspaceMatch } from '@/navigation/catalog'

interface RecentPage {
  path: string
  label: string
  domainId: string
  domainLabel: string
  workspaceLabel: string
}

defineProps<{
  projectOptions: V2SelectOption[]
  reportPeriodOptions: V2SelectOption[]
  selectedProjectId: string
  selectedReportPeriod: string
  recentPages: RecentPage[]
  canViewCommunication: boolean
  communicationUnreadCount: number | null
  notificationUnreadCount: number | null
  notificationOpen: boolean
  mobileNavigationOpen: boolean
  accountName: string
  activeMatch?: WorkspaceMatch
  visibleActiveWorkspace?: VisibleWorkspace
  routeQuery: LocationQueryRaw
  contextRoute: (path: string) => RouteLocationRaw
}>()

const emit = defineEmits<{
  'open-navigation': []
  'select-project': [value: string]
  'select-report-period': [value: string]
  'open-notifications': []
  'sign-out': []
}>()

const menuToggle = ref<HTMLButtonElement | null>(null)

function focusMenuToggle(): void {
  menuToggle.value?.focus()
}

defineExpose({ focusMenuToggle })
</script>

<template>
  <header class="app-shell__header">
    <button
      ref="menuToggle"
      type="button"
      class="app-shell__menu-toggle"
      aria-label="打开导航"
      :aria-expanded="mobileNavigationOpen"
      aria-controls="shell-navigation"
      @click="emit('open-navigation')"
    >
      <svg aria-hidden="true" viewBox="0 0 24 24">
        <path d="M4 7h16M4 12h16M4 17h16" />
      </svg>
    </button>

    <div class="app-shell__context-controls">
      <V2Select
        id="global-project"
        :model-value="selectedProjectId"
        :options="projectOptions"
        label="当前项目"
        hide-label
        placeholder="暂无可用项目"
        :disabled="projectOptions.length <= 1"
        allow-empty
        @update:model-value="emit('select-project', $event)"
      />
      <V2Select
        id="global-report-period"
        :model-value="selectedReportPeriod"
        :options="reportPeriodOptions"
        label="报告期"
        hide-label
        placeholder="暂无可用报告期"
        :disabled="reportPeriodOptions.length <= 1"
        allow-empty
        @update:model-value="emit('select-report-period', $event)"
      />
    </div>

    <V2ActionMenu class="app-shell__recent" label="最近打开">
      <template #trigger>
        <svg class="app-shell__recent-trigger-icon" aria-hidden="true" viewBox="0 0 24 24">
          <path d="M4.5 5.5V10H9" />
          <path d="M5 9.5A8 8 0 1 1 6.4 17" />
          <path d="M12 7.5V12L15 14" />
        </svg>
      </template>
      <p v-if="!recentPages.length" class="app-shell__recent-empty">暂无最近打开</p>
      <RouterLink
        v-for="page in recentPages"
        :key="page.path"
        :to="contextRoute(page.path)"
        class="v2-action-menu__item app-shell__recent-item"
      >
        <DomainNavigationIcon class="app-shell__recent-domain-icon" :domain-id="page.domainId" />
        <span>
          <strong>{{ page.label }}</strong>
          <small>{{ page.domainLabel }} · {{ page.workspaceLabel }}</small>
        </span>
      </RouterLink>
    </V2ActionMenu>

    <RouterLink
      v-if="canViewCommunication"
      to="/communication"
      class="app-shell__notification app-shell__communication"
      :aria-label="
        communicationUnreadCount === null
          ? '打开站内通讯'
          : `打开站内通讯，${communicationUnreadCount} 条未读`
      "
    >
      <svg aria-hidden="true" viewBox="0 0 24 24">
        <path d="M4 5h16v11H8l-4 4V5zM8 9h8M8 12h5" />
      </svg>
      <span v-if="communicationUnreadCount" class="app-shell__notification-count">{{
        communicationUnreadCount > 99 ? '99+' : communicationUnreadCount
      }}</span>
    </RouterLink>

    <button
      type="button"
      class="app-shell__notification"
      :aria-label="
        notificationUnreadCount === null
          ? '打开通知中心'
          : `打开通知中心，${notificationUnreadCount} 条未读`
      "
      :aria-expanded="notificationOpen"
      aria-haspopup="dialog"
      @click="emit('open-notifications')"
    >
      <svg aria-hidden="true" viewBox="0 0 24 24">
        <path d="M18 8a6 6 0 00-12 0c0 7-3 7-3 9h18c0-2-3-2-3-9M10 21h4" />
      </svg>
      <span v-if="notificationUnreadCount" class="app-shell__notification-count">{{
        notificationUnreadCount > 99 ? '99+' : notificationUnreadCount
      }}</span>
    </button>

    <div class="app-shell__account">
      <span class="app-shell__avatar" aria-hidden="true">{{ accountName.slice(0, 1) }}</span>
      <span class="app-shell__account-copy">
        <strong>{{ accountName }}</strong>
        <small>权限驱动工作区</small>
      </span>
      <RouterLink class="app-shell__account-link" to="/profile">账号中心</RouterLink>
      <V2Button variant="ghost" size="small" @click="emit('sign-out')">退出</V2Button>
    </div>
  </header>

  <div class="app-shell__workspace-bar">
    <div class="app-shell__breadcrumb" aria-label="当前位置">
      <span>{{ activeMatch?.domain.label || '应用壳' }}</span>
      <strong>{{ activeMatch?.workspace.label || '权限导航' }}</strong>
    </div>
    <nav v-if="visibleActiveWorkspace" class="app-shell__tabs" aria-label="工作区标签页">
      <RouterLink
        v-for="tab in visibleActiveWorkspace.tabs"
        :key="tab.path"
        :to="{ path: tab.path, query: routeQuery }"
        class="app-shell__tab"
      >
        <span>{{ tab.label }}</span>
      </RouterLink>
    </nav>
  </div>
</template>
