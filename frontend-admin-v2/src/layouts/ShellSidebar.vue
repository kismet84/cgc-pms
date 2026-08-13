<script setup lang="ts">
import { ref } from 'vue'
import { RouterLink, type RouteLocationRaw } from 'vue-router'
import { V2Button } from '@/components'
import DomainNavigationIcon from '@/components/DomainNavigationIcon.vue'
import type { VisibleDomain } from '@/navigation/catalog'

interface DemoRoleAccount {
  persona: string
  role: string
  username: string
  label: string
}

defineProps<{
  navigation: VisibleDomain[]
  activeDomainId?: string
  activeWorkspaceId?: string
  mobileNavigationOpen: boolean
  isMobile: boolean
  sidebarCollapsed: boolean
  accountName: string
  currentUsername?: string
  showDemoRoleSwitcher: boolean
  demoRoleAccounts: readonly DemoRoleAccount[]
  switchingDemoUser: string | null
  contextRoute: (path: string) => RouteLocationRaw
}>()

const emit = defineEmits<{
  close: []
  'navigation-keydown': [event: KeyboardEvent]
  'toggle-collapsed': []
  'switch-demo-account': [account: DemoRoleAccount]
  'sign-out': []
}>()

const demoRoleSwitcherOpen = ref(false)
const navigationPanel = ref<HTMLElement | null>(null)
const navigationClose = ref<HTMLButtonElement | null>(null)

function focusClose(): void {
  navigationClose.value?.focus()
}

function focusableElements(): HTMLElement[] {
  return [
    ...(navigationPanel.value?.querySelectorAll<HTMLElement>(
      'button:not(:disabled), a[href], select:not(:disabled), [tabindex]:not([tabindex="-1"])',
    ) ?? []),
  ]
}

defineExpose({ focusClose, focusableElements })
</script>

<template>
  <aside
    id="shell-navigation"
    ref="navigationPanel"
    class="app-shell__sidebar"
    aria-label="应用导航"
    :aria-hidden="isMobile && !mobileNavigationOpen ? 'true' : undefined"
    :inert="isMobile && !mobileNavigationOpen ? true : undefined"
    @keydown="emit('navigation-keydown', $event)"
  >
    <div class="app-shell__brand">
      <span class="app-shell__brand-mark" aria-hidden="true">CG</span>
      <span class="app-shell__brand-copy">
        <strong>CGC-PMS</strong>
        <small>建造 · 陪伴 · 成就</small>
      </span>
      <button
        ref="navigationClose"
        type="button"
        class="app-shell__nav-close"
        aria-label="关闭导航"
        @click="emit('close')"
      >
        <svg aria-hidden="true" viewBox="0 0 24 24">
          <path d="M6 6l12 12M18 6 6 18" />
        </svg>
      </button>
    </div>

    <nav class="app-shell__navigation" aria-label="主导航">
      <section
        v-for="domain in navigation"
        :key="domain.id"
        class="app-shell__domain"
        :class="{ 'app-shell__domain--active': activeDomainId === domain.id }"
        :data-domain="domain.id"
      >
        <RouterLink
          class="app-shell__domain-link"
          :to="contextRoute(domain.workspaces[0]!.tabs[0]!.path)"
          :aria-label="domain.label"
        >
          <span class="app-shell__domain-badge" aria-hidden="true">
            <DomainNavigationIcon :domain-id="domain.id" />
          </span>
          <span>{{ domain.label }}</span>
        </RouterLink>
        <div class="app-shell__workspaces">
          <RouterLink
            v-for="workspace in domain.workspaces"
            :key="workspace.id"
            class="app-shell__workspace-link"
            :class="{ 'router-link-active': activeWorkspaceId === workspace.id }"
            :to="contextRoute(workspace.tabs[0]!.path)"
          >
            {{ workspace.label }}
          </RouterLink>
        </div>
      </section>
    </nav>

    <div v-if="!navigation.length" class="app-shell__empty-navigation">当前账号无可访问业务域</div>

    <div
      v-if="showDemoRoleSwitcher"
      class="app-shell__role-tester"
      @keydown.esc="demoRoleSwitcherOpen = false"
    >
      <section
        v-if="demoRoleSwitcherOpen"
        id="role-tester-panel"
        class="app-shell__role-tester-panel"
        aria-label="演示角色"
      >
        <header>
          <strong>演示角色</strong>
          <small>免密码切换 · 仅本地</small>
        </header>
        <button
          v-for="account in demoRoleAccounts"
          :key="account.username"
          type="button"
          :class="{ 'is-active': currentUsername === account.username }"
          :disabled="Boolean(switchingDemoUser)"
          @click="emit('switch-demo-account', account)"
        >
          <span>{{ account.label }}</span>
          <small>{{ account.username }}</small>
        </button>
      </section>
      <button
        type="button"
        class="app-shell__role-tester-trigger"
        aria-label="切换演示角色"
        aria-controls="role-tester-panel"
        :aria-expanded="demoRoleSwitcherOpen"
        @click="demoRoleSwitcherOpen = !demoRoleSwitcherOpen"
      >
        角
      </button>
    </div>

    <button
      type="button"
      class="app-shell__collapse-toggle"
      :aria-label="sidebarCollapsed ? '展开侧栏' : '收起侧栏'"
      :aria-expanded="!sidebarCollapsed"
      @click="emit('toggle-collapsed')"
    >
      {{ sidebarCollapsed ? '展开' : '收起侧栏' }}
    </button>

    <div class="app-shell__mobile-account">
      <span class="app-shell__avatar" aria-hidden="true">{{ accountName.slice(0, 1) }}</span>
      <span class="app-shell__account-copy">
        <strong>{{ accountName }}</strong>
        <small>当前账号</small>
      </span>
      <RouterLink class="app-shell__account-link" to="/profile">账号中心</RouterLink>
      <V2Button variant="ghost" size="small" @click="emit('sign-out')">退出登录</V2Button>
    </div>
  </aside>
</template>
