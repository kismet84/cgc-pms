<script setup lang="ts">
import { canRequestAlertNotifications, hasPermission } from '@cgc-pms/frontend-contracts'
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { RouterView, useRoute, useRouter } from 'vue-router'
import { V2Alert } from '@/components'
import { findWorkspace, visibleNavigation } from '@/navigation/catalog'
import { loadPreferences, type UserPreferences } from '@/services/account'
import { useSessionStore } from '@/stores/session'
import { useWorkspaceStore } from '@/stores/workspace'
import ShellNotificationCenter from './ShellNotificationCenter.vue'
import ShellHeaderWorkspace from './ShellHeaderWorkspace.vue'
import ShellSidebar from './ShellSidebar.vue'
import { useShellCommunication } from './useShellCommunication'
import { useShellNotifications } from './useShellNotifications'

const route = useRoute()
const router = useRouter()
const session = useSessionStore()
const workspaceStore = useWorkspaceStore()
const mobileNavigationOpen = ref(false)
const sidebarCollapsed = ref(false)
const switchingDemoUser = ref<string | null>(null)
const isMobile = ref(false)
const shellSidebar = ref<InstanceType<typeof ShellSidebar> | null>(null)
const shellHeaderWorkspace = ref<InstanceType<typeof ShellHeaderWorkspace> | null>(null)
let mobileMedia: MediaQueryList | null = null
let removeAfterEach: (() => void) | null = null
let restoreMenuFocus = false

const navigation = computed(() => visibleNavigation(session.roles, session.permissions))
const visiblePages = computed(() =>
  navigation.value.flatMap((domain) =>
    domain.workspaces.flatMap((workspace) =>
      workspace.tabs.map((tab) => ({
        path: tab.path,
        label: tab.label,
        domainId: domain.id,
        domainLabel: domain.label,
        workspaceLabel: workspace.label,
      })),
    ),
  ),
)
const recentPages = computed(() =>
  workspaceStore.recentPaths.flatMap((path) => {
    const page = visiblePages.value.find((item) => item.path === path)
    return page ? [page] : []
  }),
)
const canRequestNotifications = computed(() => canRequestAlertNotifications(session.permissions))
const canEditNotifications = computed(() => hasPermission(session.permissions, 'notification:edit'))
const canViewCommunication = computed(() => session.hasAdminOrPermission('communication:view'))
const {
  notificationOpen,
  notificationItems,
  notificationUnreadCount,
  notificationLoading,
  notificationError,
  openNotifications,
  readNotification,
  readAllNotifications,
} = useShellNotifications(canRequestNotifications, canEditNotifications)
const { communicationUnreadCount } = useShellCommunication(canViewCommunication)

function normalizeUnreadCount(value: unknown): number | null {
  if (value === null || (typeof value === 'string' && !value.trim())) return null
  const count = Number(value)
  return Number.isInteger(count) && count >= 0 ? count : null
}

const activeMatch = computed(() => findWorkspace(route.path))
const visibleActiveWorkspace = computed(() => {
  const match = activeMatch.value
  if (!match) return undefined
  return navigation.value
    .find((domain) => domain.id === match.domain.id)
    ?.workspaces.find((workspace) => workspace.id === match.workspace.id)
})

const projectOptions = computed(() => [
  { value: '', label: '全部项目' },
  ...workspaceStore.projects,
])
const reportPeriodOptions = computed(() => [
  { value: '', label: '全部报告期' },
  ...workspaceStore.reportPeriods,
])
const accountName = computed(
  () => session.userInfo?.realName || session.userInfo?.username || '当前用户',
)
const showDemoRoleSwitcher = import.meta.env.DEV
const demoRoleAccounts = [
  { persona: 'COMPANY_OWNER', role: 'mgmt', username: 'ui26.gm01', label: '公司老板' },
  { persona: 'COMPANY_FINANCE', role: 'finance', username: 'ui26.fin01', label: '公司财务' },
  { persona: 'PROJECT_MANAGER', role: 'pm', username: 'ui26.pm01', label: '项目经理' },
  {
    persona: 'PROJECT_ACCOUNTANT',
    role: 'cost',
    username: 'ui26.cost01',
    label: '项目会计',
  },
  {
    persona: 'TECHNICAL_LEAD',
    role: 'chiefEngineer',
    username: 'ui26.chief01',
    label: '技术负责人',
  },
  { persona: 'SAFETY_LEAD', role: 'pm', username: 'ui26.bm01', label: '安全负责人' },
  {
    persona: 'CONSTRUCTION_LEAD',
    role: 'production',
    username: 'ui26.prod01',
    label: '施工负责人',
  },
  {
    persona: 'PROCUREMENT_LEAD',
    role: 'purchase',
    username: 'ui26.pur01',
    label: '采购负责人',
  },
  { persona: 'EMPLOYEE', role: 'pm', username: 'ui26.staff01', label: '员工' },
] as const

watch(
  [() => route.fullPath, navigation],
  () => {
    workspaceStore.syncRoute(route.path, route.query, route.params)
    workspaceStore.loadRecentPaths()
    if (visiblePages.value.some((page) => page.path === route.path)) {
      workspaceStore.recordRecentPath(route.path)
    }
    mobileNavigationOpen.value = false
    restoreMenuFocus = false
  },
  { immediate: true },
)

watch(mobileNavigationOpen, async (open) => {
  document.body.classList.toggle('v2-mobile-nav-open', isMobile.value && open)
  if (open) {
    await nextTick()
    shellSidebar.value?.focusClose()
  } else if (restoreMenuFocus) {
    await nextTick()
    shellHeaderWorkspace.value?.focusMenuToggle()
    restoreMenuFocus = false
  }
})

onMounted(() => {
  void initializeWorkspaceContext()
  window.addEventListener('v2-preferences-updated', onPreferencesUpdated)
  void loadShellPreferences()
  mobileMedia = window.matchMedia('(max-width: 48rem)')
  syncMobileMode(mobileMedia)
  mobileMedia.addEventListener('change', syncMobileMode)
  removeAfterEach = router.afterEach(async (to, from, failure) => {
    if (failure || to.fullPath === from.fullPath) return
    await nextTick()
    window.requestAnimationFrame(() => {
      document.querySelector<HTMLElement>('#shell-main-content')?.focus()
    })
  })
})

function applyPreferences(value: Pick<UserPreferences, 'sidebarCollapsed'>): void {
  sidebarCollapsed.value = Boolean(value.sidebarCollapsed)
}

function onPreferencesUpdated(event: Event): void {
  const detail = (event as CustomEvent<Partial<UserPreferences>>).detail
  if (detail && typeof detail.sidebarCollapsed === 'boolean') {
    applyPreferences(detail as Pick<UserPreferences, 'sidebarCollapsed'>)
  }
}

async function loadShellPreferences(): Promise<void> {
  try {
    applyPreferences(await loadPreferences())
  } catch {
    // 偏好读取失败时保持默认展开，不能阻塞主工作区。
  }
}

async function initializeWorkspaceContext(): Promise<void> {
  try {
    await workspaceStore.initialize()
    const query = { ...route.query }
    let changed = false
    if (Object.hasOwn(query, 'projectId') && !workspaceStore.selectedProjectId) {
      delete query.projectId
      changed = true
    }
    if (Object.hasOwn(query, 'period') && !workspaceStore.selectedReportPeriod) {
      delete query.period
      changed = true
    }
    if (changed) await router.replace({ path: route.path, query, hash: route.hash })
  } catch {
    // 页面仍可按无上下文模式运行；请求错误由统一请求提示处理。
  }
}

onBeforeUnmount(() => {
  window.removeEventListener('v2-preferences-updated', onPreferencesUpdated)
  mobileMedia?.removeEventListener('change', syncMobileMode)
  removeAfterEach?.()
  document.body.classList.remove('v2-mobile-nav-open')
})

function syncMobileMode(event: MediaQueryList | MediaQueryListEvent): void {
  isMobile.value = event.matches
  if (!event.matches) mobileNavigationOpen.value = false
}

function openNavigation(): void {
  restoreMenuFocus = false
  mobileNavigationOpen.value = true
}

function closeNavigation(): void {
  restoreMenuFocus = true
  mobileNavigationOpen.value = false
}

function onNavigationKeydown(event: KeyboardEvent): void {
  if (event.key === 'Escape') {
    event.preventDefault()
    closeNavigation()
    return
  }
  if (event.key !== 'Tab' || !isMobile.value || !mobileNavigationOpen.value) return
  const focusable = shellSidebar.value?.focusableElements()
  if (!focusable?.length) return
  const first = focusable[0]
  const last = focusable[focusable.length - 1]
  if (event.shiftKey && document.activeElement === first) {
    event.preventDefault()
    last?.focus()
  } else if (!event.shiftKey && document.activeElement === last) {
    event.preventDefault()
    first?.focus()
  }
}

function selectProject(value: string): void {
  workspaceStore.selectProject(value)
  updateContextQuery('projectId', workspaceStore.selectedProjectId)
}

function selectReportPeriod(value: string): void {
  workspaceStore.selectReportPeriod(value)
  updateContextQuery('period', workspaceStore.selectedReportPeriod)
}

function updateContextQuery(key: 'projectId' | 'period', value: string | null): void {
  const query = { ...route.query }
  if (value) query[key] = value
  else delete query[key]
  void router.replace({ path: route.path, query, hash: route.hash })
}

function contextRoute(path: string) {
  return {
    path,
    query: {
      ...(workspaceStore.selectedProjectId ? { projectId: workspaceStore.selectedProjectId } : {}),
      ...(workspaceStore.selectedReportPeriod
        ? { period: workspaceStore.selectedReportPeriod }
        : {}),
      ...(route.query.desktop === '1' || document.documentElement.dataset.desktopShell === 'true'
        ? { desktop: '1' }
        : {}),
    },
  }
}

async function signOut(): Promise<void> {
  try {
    await session.logout()
  } finally {
    await router.replace('/login')
  }
}

async function switchDemoAccount(account: {
  persona: string
  role: string
  username: string
}): Promise<void> {
  if (!import.meta.env.DEV || switchingDemoUser.value) return
  switchingDemoUser.value = account.username
  session.setRequestNotice(null)
  try {
    const response = await fetch(
      `/api/auth/dev-login?username=${encodeURIComponent(account.username)}`,
      { credentials: 'same-origin' },
    )
    const payload = (await response.json()) as { code?: string; message?: string }
    if (!response.ok || payload.code !== '0') {
      throw new Error(payload.message || payload.code || '演示角色切换失败')
    }
    const query = { ...route.query }
    if (route.path === '/dashboard') {
      query.persona = account.persona
      query.role = account.role
    } else {
      delete query.persona
      delete query.role
    }
    const target = router.resolve({ path: route.path, query }).href
    window.location.assign(target)
  } catch (error) {
    switchingDemoUser.value = null
    session.setRequestNotice({
      code: 'DEV_ROLE_SWITCH_FAILED',
      message: error instanceof Error ? error.message : '演示角色切换失败',
    })
  }
}
</script>

<template>
  <div
    class="app-shell"
    :class="{
      'app-shell--nav-open': mobileNavigationOpen,
      'app-shell--collapsed': sidebarCollapsed && !isMobile,
    }"
  >
    <a class="app-shell__skip-link" href="#shell-main-content">跳到主要内容</a>
    <button
      v-if="mobileNavigationOpen"
      type="button"
      class="app-shell__scrim"
      aria-label="关闭导航"
      @click="closeNavigation"
    ></button>

    <ShellSidebar
      ref="shellSidebar"
      :navigation="navigation"
      :active-domain-id="activeMatch?.domain.id"
      :active-workspace-id="activeMatch?.workspace.id"
      :mobile-navigation-open="mobileNavigationOpen"
      :is-mobile="isMobile"
      :sidebar-collapsed="sidebarCollapsed"
      :account-name="accountName"
      :current-username="session.userInfo?.username"
      :show-demo-role-switcher="showDemoRoleSwitcher"
      :demo-role-accounts="demoRoleAccounts"
      :switching-demo-user="switchingDemoUser"
      :context-route="contextRoute"
      @close="closeNavigation"
      @navigation-keydown="onNavigationKeydown"
      @toggle-collapsed="sidebarCollapsed = !sidebarCollapsed"
      @switch-demo-account="switchDemoAccount"
      @sign-out="signOut"
    />

    <div class="app-shell__main">
      <ShellHeaderWorkspace
        ref="shellHeaderWorkspace"
        :project-options="projectOptions"
        :report-period-options="reportPeriodOptions"
        :selected-project-id="workspaceStore.selectedProjectId || ''"
        :selected-report-period="workspaceStore.selectedReportPeriod || ''"
        :recent-pages="recentPages"
        :can-view-communication="canViewCommunication"
        :communication-unread-count="normalizeUnreadCount(communicationUnreadCount)"
        :notification-unread-count="normalizeUnreadCount(notificationUnreadCount)"
        :notification-open="notificationOpen"
        :mobile-navigation-open="mobileNavigationOpen"
        :account-name="accountName"
        :active-match="activeMatch"
        :visible-active-workspace="visibleActiveWorkspace"
        :route-query="route.query"
        :context-route="contextRoute"
        @open-navigation="openNavigation"
        @select-project="selectProject"
        @select-report-period="selectReportPeriod"
        @open-notifications="openNotifications"
        @sign-out="signOut"
      />

      <div v-if="session.requestNotice" class="app-shell__notice-region">
        <V2Alert
          title="请求未完成"
          tone="danger"
          dismissible
          @dismiss="session.setRequestNotice(null)"
        >
          {{ session.requestNotice.message }}
        </V2Alert>
      </div>

      <main
        id="shell-main-content"
        class="app-shell__content"
        :class="{ 'app-shell__content--full': route.meta.workflowTab }"
        tabindex="-1"
      >
        <RouterView v-slot="{ Component }">
          <component :is="Component" />
        </RouterView>
      </main>
    </div>

    <ShellNotificationCenter
      v-model:open="notificationOpen"
      :items="notificationItems"
      :unread-count="normalizeUnreadCount(notificationUnreadCount)"
      :loading="notificationLoading"
      :error="notificationError"
      :can-request="canRequestNotifications"
      :can-edit="canEditNotifications"
      @read="readNotification"
      @read-all="readAllNotifications"
    />
  </div>
</template>

<style src="./app-shell.css"></style>
