<script setup lang="ts">
import {
  canRequestAlertNotifications,
  hasPermission,
  resolveDashboardRoles,
  type DashboardRole,
  type NotificationRecord,
} from '@cgc-pms/frontend-contracts'
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { RouterLink, RouterView, useRoute, useRouter } from 'vue-router'
import { V2Alert, V2Button, V2Dialog, V2PageState, V2Select } from '@/components'
import DomainNavigationIcon from '@/components/DomainNavigationIcon.vue'
import { findWorkspace, visibleNavigation } from '@/navigation/catalog'
import ShellLoadingPage from '@/pages/shell/ShellLoadingPage.vue'
import {
  loadNotificationSummary,
  markAllNotificationsRead,
  markNotificationRead,
} from '@/services/alerts'
import { useSessionStore } from '@/stores/session'
import { useWorkspaceStore } from '@/stores/workspace'

const route = useRoute()
const router = useRouter()
const session = useSessionStore()
const workspaceStore = useWorkspaceStore()
const mobileNavigationOpen = ref(false)
const sidebarCollapsed = ref(false)
const notificationOpen = ref(false)
const notificationItems = ref<NotificationRecord[]>([])
const notificationUnreadCount = ref<number | null>(null)
const notificationLoading = ref(false)
const notificationError = ref('')
const roleTesterOpen = ref(false)
const switchingTestUser = ref<string | null>(null)
const isMobile = ref(false)
const menuToggle = ref<HTMLButtonElement | null>(null)
const navigationPanel = ref<HTMLElement | null>(null)
const navigationClose = ref<HTMLButtonElement | null>(null)
let mobileMedia: MediaQueryList | null = null
let removeAfterEach: (() => void) | null = null
let restoreMenuFocus = false
let notificationController: AbortController | null = null

const navigation = computed(() => visibleNavigation(session.permissions))
const canRequestNotifications = computed(() => canRequestAlertNotifications(session.permissions))
const canEditNotifications = computed(() => hasPermission(session.permissions, 'notification:edit'))
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
const dashboardRole = computed<DashboardRole | null>(() => {
  if (route.path !== '/dashboard') return null
  const allowed = resolveDashboardRoles(session.roles, session.permissions)
  const requested = typeof route.query.role === 'string' ? route.query.role : ''
  return allowed.includes(requested as DashboardRole)
    ? (requested as DashboardRole)
    : (allowed[0] ?? null)
})
const projectFilterUnsupported = computed(
  () => route.meta.workspaceContext?.project !== true || dashboardRole.value === 'mgmt',
)
const periodFilterUnsupported = computed(
  () =>
    route.meta.workspaceContext?.period !== true ||
    (dashboardRole.value ? ['bm', 'finance', 'mgmt'].includes(dashboardRole.value) : false),
)
const projectFilterLabel = computed(() => {
  if (!projectFilterUnsupported.value) return '当前项目'
  return dashboardRole.value === 'mgmt' ? '当前项目（管理层为租户汇总）' : '当前项目'
})
const periodFilterLabel = '报告期'
const accountName = computed(
  () => session.userInfo?.realName || session.userInfo?.username || '当前用户',
)
const showRoleTester = import.meta.env.DEV
const roleTestAccounts = [
  { role: 'pm', username: 'demo.manager', label: '项目经理' },
  { role: 'bm', username: 'demo.business', label: '商务经理' },
  { role: 'cost', username: 'demo.cost', label: '成本经理' },
  { role: 'purchase', username: 'demo.purchase', label: '采购经理' },
  { role: 'production', username: 'demo.production', label: '生产经理' },
  { role: 'chiefEngineer', username: 'demo.chief', label: '总工程师' },
  { role: 'finance', username: 'demo.finance', label: '财务经理' },
  { role: 'mgmt', username: 'admin', label: '管理层' },
] as const

watch(
  () => route.fullPath,
  () => {
    workspaceStore.syncRoute(route.path, route.query, route.params)
    mobileNavigationOpen.value = false
    restoreMenuFocus = false
  },
  { immediate: true },
)

watch(
  canRequestNotifications,
  (allowed) => {
    if (allowed) void refreshNotifications()
    else clearNotifications()
  },
  { immediate: true },
)

watch(mobileNavigationOpen, async (open) => {
  document.body.classList.toggle('v2-mobile-nav-open', isMobile.value && open)
  if (open) {
    await nextTick()
    navigationClose.value?.focus()
  } else if (restoreMenuFocus) {
    await nextTick()
    menuToggle.value?.focus()
    restoreMenuFocus = false
  }
})

onMounted(() => {
  void workspaceStore.initialize(session.roles, session.permissions).catch(() => undefined)
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

onBeforeUnmount(() => {
  notificationController?.abort()
  mobileMedia?.removeEventListener('change', syncMobileMode)
  removeAfterEach?.()
  document.body.classList.remove('v2-mobile-nav-open')
})

function clearNotifications(): void {
  notificationController?.abort()
  notificationItems.value = []
  notificationUnreadCount.value = null
  notificationError.value = ''
}

async function refreshNotifications(): Promise<void> {
  if (!canRequestNotifications.value) return
  notificationController?.abort()
  notificationController = new AbortController()
  notificationLoading.value = true
  notificationError.value = ''
  try {
    const [page, unread] = await loadNotificationSummary(notificationController.signal)
    notificationItems.value = page.records
    notificationUnreadCount.value = unread.count
  } catch {
    if (!notificationController.signal.aborted) {
      notificationItems.value = []
      notificationUnreadCount.value = null
      notificationError.value = '通知摘要加载失败'
    }
  } finally {
    if (!notificationController.signal.aborted) notificationLoading.value = false
  }
}

function openNotifications(): void {
  notificationOpen.value = true
  if (canRequestNotifications.value) void refreshNotifications()
}

async function readNotification(id: string): Promise<void> {
  if (!canEditNotifications.value) return
  try {
    await markNotificationRead(id)
    await refreshNotifications()
  } catch {
    notificationError.value = '通知已读操作失败'
  }
}

async function readAllNotifications(): Promise<void> {
  if (!canEditNotifications.value) return
  try {
    await markAllNotificationsRead()
    await refreshNotifications()
  } catch {
    notificationError.value = '全部已读操作失败'
  }
}

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
  const focusable = navigationPanel.value?.querySelectorAll<HTMLElement>(
    'button:not(:disabled), a[href], select:not(:disabled), [tabindex]:not([tabindex="-1"])',
  )
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

async function switchTestAccount(account: (typeof roleTestAccounts)[number]): Promise<void> {
  if (!import.meta.env.DEV || switchingTestUser.value) return
  switchingTestUser.value = account.username
  session.setRequestNotice(null)
  try {
    const response = await fetch(
      `/api/auth/dev-login?username=${encodeURIComponent(account.username)}`,
      { credentials: 'same-origin' },
    )
    const payload = (await response.json()) as { code?: string }
    if (!response.ok || payload.code !== '0') throw new Error('DEV_ROLE_SWITCH_FAILED')
    const query = { ...route.query }
    if (route.path === '/dashboard') query.role = account.role
    else delete query.role
    const target = router.resolve({ path: route.path, query }).href
    window.location.assign(target)
  } catch {
    switchingTestUser.value = null
    session.setRequestNotice({ code: 'DEV_ROLE_SWITCH_FAILED', message: '角色账号切换失败' })
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

    <aside
      id="shell-navigation"
      ref="navigationPanel"
      class="app-shell__sidebar"
      aria-label="应用导航"
      :aria-hidden="isMobile && !mobileNavigationOpen ? 'true' : undefined"
      :inert="isMobile && !mobileNavigationOpen ? true : undefined"
      @keydown="onNavigationKeydown"
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
          @click="closeNavigation"
        >
          <svg aria-hidden="true" viewBox="0 0 24 24">
            <path d="M6 6l12 12M18 6 6 18" />
          </svg>
        </button>
      </div>

      <nav class="app-shell__navigation" aria-label="八域主导航">
        <section
          v-for="domain in navigation"
          :key="domain.id"
          class="app-shell__domain"
          :class="{ 'app-shell__domain--active': activeMatch?.domain.id === domain.id }"
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
              :class="{ 'router-link-active': activeMatch?.workspace.id === workspace.id }"
              :to="contextRoute(workspace.tabs[0]!.path)"
            >
              {{ workspace.label }}
            </RouterLink>
          </div>
        </section>
      </nav>

      <div v-if="!navigation.length" class="app-shell__empty-navigation">
        当前账号无可访问业务域
      </div>

      <div
        v-if="showRoleTester"
        class="app-shell__role-tester"
        @keydown.esc="roleTesterOpen = false"
      >
        <section
          v-if="roleTesterOpen"
          id="role-tester-panel"
          class="app-shell__role-tester-panel"
          aria-label="角色测试账号"
        >
          <header>
            <strong>角色测试</strong>
            <small>仅本地开发环境</small>
          </header>
          <button
            v-for="account in roleTestAccounts"
            :key="account.username"
            type="button"
            :class="{ 'is-active': session.userInfo?.username === account.username }"
            :disabled="Boolean(switchingTestUser)"
            @click="switchTestAccount(account)"
          >
            <span>{{ account.label }}</span>
            <small>{{ account.username }}</small>
          </button>
        </section>
        <button
          type="button"
          class="app-shell__role-tester-trigger"
          aria-label="切换角色测试账号"
          aria-controls="role-tester-panel"
          :aria-expanded="roleTesterOpen"
          @click="roleTesterOpen = !roleTesterOpen"
        >
          角
        </button>
      </div>

      <button
        type="button"
        class="app-shell__collapse-toggle"
        :aria-label="sidebarCollapsed ? '展开侧栏' : '收起侧栏'"
        :aria-expanded="!sidebarCollapsed"
        @click="sidebarCollapsed = !sidebarCollapsed"
      >
        {{ sidebarCollapsed ? '展开' : '收起侧栏' }}
      </button>

      <div class="app-shell__mobile-account">
        <span class="app-shell__avatar" aria-hidden="true">{{ accountName.slice(0, 1) }}</span>
        <span class="app-shell__account-copy">
          <strong>{{ accountName }}</strong>
          <small>当前账号</small>
        </span>
        <V2Button variant="ghost" size="small" @click="signOut">退出登录</V2Button>
      </div>
    </aside>

    <div class="app-shell__main">
      <header class="app-shell__header">
        <button
          ref="menuToggle"
          type="button"
          class="app-shell__menu-toggle"
          aria-label="打开导航"
          :aria-expanded="mobileNavigationOpen"
          aria-controls="shell-navigation"
          @click="openNavigation"
        >
          <svg aria-hidden="true" viewBox="0 0 24 24">
            <path d="M4 7h16M4 12h16M4 17h16" />
          </svg>
        </button>

        <div class="app-shell__context-controls">
          <V2Select
            id="global-project"
            :model-value="workspaceStore.selectedProjectId || ''"
            :options="projectOptions"
            :label="projectFilterLabel"
            placeholder="暂无可用项目"
            :disabled="!workspaceStore.projects.length || projectFilterUnsupported"
            allow-empty
            @update:model-value="selectProject"
          />
          <V2Select
            id="global-report-period"
            :model-value="workspaceStore.selectedReportPeriod || ''"
            :options="reportPeriodOptions"
            :label="periodFilterLabel"
            placeholder="暂无可用报告期"
            :disabled="!workspaceStore.reportPeriods.length || periodFilterUnsupported"
            allow-empty
            @update:model-value="selectReportPeriod"
          />
        </div>

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
          @click="openNotifications"
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
          <V2Button variant="ghost" size="small" @click="signOut">退出</V2Button>
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
            :to="{ path: tab.path, query: route.query }"
            class="app-shell__tab"
          >
            <span>{{ tab.label }}</span>
          </RouterLink>
        </nav>
      </div>

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
          <Suspense>
            <component :is="Component" />
            <template #fallback><ShellLoadingPage /></template>
          </Suspense>
        </RouterView>
      </main>
    </div>

    <V2Dialog
      v-model:open="notificationOpen"
      title="通知中心"
      description="按当前账号读取站内通知摘要；不建立实时连接。"
    >
      <V2Alert v-if="notificationError" tone="danger" title="请求未完成">{{
        notificationError
      }}</V2Alert>
      <V2PageState
        v-if="!canRequestNotifications"
        kind="forbidden"
        :heading-level="3"
        title="当前账号无通知摘要权限"
        description="未发起通知列表或未读数请求。"
      />
      <V2PageState
        v-else-if="notificationLoading"
        kind="loading"
        :heading-level="3"
        title="正在加载通知"
        description="读取当前账号最近通知。"
      />
      <V2PageState
        v-else-if="!notificationItems.length"
        kind="empty"
        :heading-level="3"
        title="暂无站内通知"
        description="当前账号没有可见通知。"
      />
      <div v-else class="app-shell__notification-summary">
        <div class="app-shell__notification-toolbar">
          <span>未读 {{ notificationUnreadCount ?? 0 }} 条</span>
          <V2Button
            v-if="canEditNotifications && notificationUnreadCount"
            variant="ghost"
            size="small"
            @click="readAllNotifications"
            >全部已读</V2Button
          >
        </div>
        <article
          v-for="item in notificationItems"
          :key="item.id"
          :class="['app-shell__notification-item', { 'is-unread': item.isRead !== 1 }]"
        >
          <div>
            <strong>{{ item.title }}</strong>
            <p>{{ item.content }}</p>
            <small>{{ item.createdTime }}</small>
          </div>
          <V2Button
            v-if="canEditNotifications && item.isRead !== 1"
            variant="ghost"
            size="small"
            @click="readNotification(item.id)"
            >已读</V2Button
          >
        </article>
      </div>
    </V2Dialog>
  </div>
</template>

<style scoped>
.app-shell {
  min-height: 100vh;
  display: grid;
  grid-template-columns: 12.5rem minmax(0, 1fr);
  background: var(--v2-color-canvas);
}

.app-shell__skip-link {
  position: fixed;
  z-index: var(--v2-z-toast);
  inset-block-start: var(--v2-space-2);
  inset-inline-start: var(--v2-space-2);
  padding: var(--v2-space-3) var(--v2-space-4);
  color: var(--v2-color-surface);
  background: var(--v2-color-primary-active);
  border-radius: var(--v2-radius-sm);
  transform: translateY(-150%);
  transition: transform var(--v2-motion-fast) var(--v2-ease-standard);
}

.app-shell__skip-link:focus {
  transform: translateY(0);
}

.app-shell__sidebar {
  position: sticky;
  z-index: var(--v2-z-sticky);
  inset-block-start: 0;
  height: 100vh;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background: var(--v2-color-surface);
  border-inline-end: var(--v2-border-width) solid var(--v2-color-border);
}

.app-shell__brand {
  box-sizing: border-box;
  height: 4.0625rem;
  min-height: 4.0625rem;
  display: flex;
  align-items: center;
  gap: var(--v2-space-3);
  padding: var(--v2-space-4) var(--v2-space-5);
  border-block-end: var(--v2-border-width) solid var(--v2-color-border);
}

.app-shell__nav-close {
  display: none;
}

.app-shell__brand-mark,
.app-shell__avatar {
  flex: 0 0 auto;
  display: grid;
  place-items: center;
  color: var(--v2-color-surface);
  background: var(--v2-color-primary);
  border-radius: var(--v2-radius-md);
  font-size: var(--v2-font-size-12);
  font-weight: var(--v2-font-weight-heavy);
  box-shadow: 0 6px 18px var(--v2-color-focus-ring);
}

.app-shell__brand-mark {
  width: 2.5rem;
  height: 2.5rem;
}

.app-shell__brand-copy,
.app-shell__account-copy {
  min-width: 0;
  display: grid;
}

.app-shell__brand-copy strong {
  color: var(--v2-color-primary);
  font-size: var(--v2-font-size-17);
}

.app-shell__brand-copy small,
.app-shell__account-copy small {
  color: var(--v2-color-text-muted);
  font-size: var(--v2-font-size-11);
}

.app-shell__navigation {
  overflow-y: auto;
  display: grid;
  gap: var(--v2-space-1);
  padding: var(--v2-space-2);
}

.app-shell__domain {
  border-radius: var(--v2-radius-sm);
}

.app-shell__domain-link {
  min-height: 2.5rem;
  display: flex;
  align-items: center;
  gap: var(--v2-space-3);
  padding: 0 var(--v2-space-3);
  color: var(--v2-color-text-secondary);
  border-radius: var(--v2-radius-sm);
  font-size: var(--v2-font-size-13);
  font-weight: var(--v2-font-weight-bold);
  text-decoration: none;
}

.app-shell__domain-link:hover,
.app-shell__domain--active > .app-shell__domain-link {
  color: var(--v2-color-primary);
  background: var(--v2-color-primary-soft);
}

.app-shell__domain-badge {
  width: 1.75rem;
  height: 1.75rem;
  display: grid;
  place-items: center;
  color: currentColor;
}

.app-shell__domain-badge svg {
  width: 1.5rem;
  height: 1.5rem;
}

.app-shell__workspaces {
  display: none;
  padding: 0 var(--v2-space-2) var(--v2-space-1) 3.25rem;
}

.app-shell__domain--active .app-shell__workspaces {
  display: grid;
}

.app-shell__workspace-link {
  padding: var(--v2-space-1) var(--v2-space-2);
  color: var(--v2-color-text-muted);
  border-inline-start: 2px solid var(--v2-color-border);
  font-size: var(--v2-font-size-12);
  text-decoration: none;
}

.app-shell__workspace-link:hover,
.app-shell__workspace-link.router-link-active {
  color: var(--v2-color-primary);
  border-inline-start-color: var(--v2-color-primary);
}

.app-shell__empty-navigation {
  margin: var(--v2-space-4);
  padding: var(--v2-space-4);
  color: var(--v2-color-text-muted);
  background: var(--v2-color-surface-subtle);
  border-radius: var(--v2-radius-sm);
  font-size: var(--v2-font-size-12);
}

.app-shell__collapse-toggle {
  min-height: 3rem;
  flex: 0 0 auto;
  margin-block-start: auto;
  color: var(--v2-color-text-secondary);
  background: var(--v2-color-surface);
  border: 0;
  border-block-start: var(--v2-border-width) solid var(--v2-color-border);
  font: var(--v2-font-weight-semibold) var(--v2-font-size-12) / var(--v2-line-height-ui)
    var(--v2-font-sans);
  cursor: pointer;
}

.app-shell__collapse-toggle:hover {
  color: var(--v2-color-primary);
  background: var(--v2-color-primary-soft);
}

.app-shell__main {
  min-width: 0;
  min-height: 100vh;
  display: flex;
  flex-direction: column;
}

.app-shell__header {
  box-sizing: border-box;
  height: 4.0625rem;
  min-height: 4.0625rem;
  display: grid;
  grid-template-columns: minmax(24rem, 1fr) auto auto;
  align-items: center;
  gap: var(--v2-space-4);
  padding: var(--v2-space-2) var(--v2-page-gutter);
  background: var(--v2-color-surface);
  border-block-end: var(--v2-border-width) solid var(--v2-color-border);
}

.app-shell__menu-toggle {
  display: none;
}

.app-shell__notification,
.app-shell__nav-close {
  width: var(--v2-control-height-touch);
  height: var(--v2-control-height-touch);
  place-items: center;
  color: var(--v2-color-text-secondary);
  background: transparent;
  border: var(--v2-border-width) solid transparent;
  border-radius: var(--v2-radius-sm);
  cursor: pointer;
}

.app-shell__notification {
  display: grid;
  position: relative;
}

.app-shell__notification-count {
  position: absolute;
  inset-block-start: 0;
  inset-inline-end: 0;
  min-width: 1rem;
  height: 1rem;
  padding-inline: 0.2rem;
  color: var(--v2-color-surface);
  background: var(--v2-color-danger);
  border-radius: 999px;
  font-size: var(--v2-font-size-11);
  line-height: 1rem;
  text-align: center;
}

.app-shell__notification:hover,
.app-shell__nav-close:hover {
  color: var(--v2-color-primary);
  background: var(--v2-color-primary-soft);
}

.app-shell__notification svg,
.app-shell__nav-close svg,
.app-shell__menu-toggle svg {
  width: 1.25rem;
  height: 1.25rem;
  fill: none;
  stroke: currentColor;
  stroke-linecap: round;
  stroke-linejoin: round;
  stroke-width: 1.8;
}

.app-shell__notification-summary {
  display: grid;
  gap: var(--v2-space-2);
}

.app-shell__notification-toolbar,
.app-shell__notification-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--v2-space-3);
}

.app-shell__notification-toolbar {
  color: var(--v2-color-text-secondary);
  font-size: var(--v2-font-size-12);
}

.app-shell__notification-toolbar > span {
  color: var(--v2-color-danger-text);
  font-size: var(--v2-font-size-12);
  font-weight: var(--v2-font-weight-bold);
}

.app-shell__notification-item {
  padding: var(--v2-space-3);
  border: 1px solid var(--v2-color-border);
  border-radius: var(--v2-radius-md);
}

.app-shell__notification-item strong {
  font-size: var(--v2-font-size-12);
}

.app-shell__notification-item.is-unread {
  border-inline-start: 0.2rem solid var(--v2-color-primary);
  background: var(--v2-color-primary-soft);
}

.app-shell__notification-item p,
.app-shell__notification-item small {
  margin: var(--v2-space-1) 0 0;
  color: var(--v2-color-text-secondary);
  font-size: var(--v2-font-size-11);
}

.app-shell__context-controls {
  display: grid;
  grid-template-columns: repeat(2, minmax(15rem, 20rem));
  gap: var(--v2-space-3);
}

.app-shell__context-controls :deep(.v2-field) {
  grid-template-columns: auto minmax(10rem, 1fr);
  align-items: center;
  gap: var(--v2-space-2);
}

.app-shell__context-controls :deep(.v2-field__label) {
  white-space: nowrap;
}

.app-shell__account {
  display: flex;
  align-items: center;
  gap: var(--v2-space-3);
}

.app-shell__avatar {
  width: 2rem;
  height: 2rem;
  border-radius: var(--v2-radius-round);
}

.app-shell__account-copy strong {
  max-width: 12rem;
  overflow: hidden;
  color: var(--v2-color-text-strong);
  font-size: var(--v2-font-size-12);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.app-shell__workspace-bar {
  box-sizing: border-box;
  height: 3.125rem;
  min-height: 3.125rem;
  display: flex;
  align-items: center;
  gap: var(--v2-space-5);
  padding: 0 var(--v2-page-gutter);
  background: var(--v2-color-surface);
  border-block-end: var(--v2-border-width) solid var(--v2-color-border-subtle);
}

.app-shell__breadcrumb {
  display: flex;
  align-items: center;
  gap: var(--v2-space-2);
  white-space: nowrap;
}

.app-shell__workspace-bar span {
  color: var(--v2-color-text-muted);
  font-size: var(--v2-font-size-11);
}

.app-shell__breadcrumb strong {
  padding-inline-start: var(--v2-space-2);
  color: var(--v2-color-text-strong);
  border-inline-start: var(--v2-border-width) solid var(--v2-color-border);
  font-size: var(--v2-font-size-12);
}

.app-shell__tabs {
  min-width: 0;
  min-height: 2.5rem;
  display: flex;
  flex: 1;
  align-items: flex-end;
  gap: 0;
  overflow-x: auto;
  overflow-y: hidden;
  padding: 0;
  scrollbar-width: none;
}

.app-shell__tabs::-webkit-scrollbar {
  display: none;
}

.app-shell__tabs::after {
  min-width: 4rem;
  flex: 1 0 4rem;
  align-self: flex-end;
  border-block-end: 2px solid var(--v2-color-workspace-tab-accent);
  content: '';
}

.app-shell__tab {
  position: relative;
  box-sizing: border-box;
  width: 11.25rem;
  min-width: 11.25rem;
  height: 2.5rem;
  min-height: 2.5rem;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  margin-block-end: 0;
  margin-inline-end: -0.875rem;
  padding: 0 var(--v2-space-6);
  color: var(--v2-color-text-secondary);
  background: var(--v2-color-canvas);
  border: var(--v2-border-width) solid var(--v2-color-border);
  border-block-end: 2px solid var(--v2-color-workspace-tab-accent);
  border-radius: var(--v2-radius-md) var(--v2-radius-md) 0 0;
  clip-path: path('M 0 0 H 154 C 159 0 161 2 163 7 L 180 40 H 0 Z');
  filter: drop-shadow(-1px 0 0 var(--v2-color-border)) drop-shadow(1px 0 0 var(--v2-color-border));
  font-size: var(--v2-font-size-12);
  font-weight: var(--v2-font-weight-semibold);
  text-decoration: none;
  white-space: nowrap;
}

.app-shell__tab > span {
  color: inherit;
  font-size: inherit;
}

.app-shell__tab:hover {
  color: var(--v2-color-workspace-tab-accent);
  background: var(--v2-color-surface-hover);
  border-block-start: 3px solid var(--v2-color-workspace-tab-accent);
}

.app-shell__tab.router-link-active {
  z-index: 1;
  color: var(--v2-color-workspace-tab-accent);
  background: var(--v2-color-workspace-tab-accent-soft);
  border-block-end-color: var(--v2-color-workspace-tab-accent-soft);
  border-block-start: 3px solid var(--v2-color-workspace-tab-accent);
}

.app-shell__tab:focus-visible {
  outline: 0;
  box-shadow: inset 0 0 0 3px var(--v2-color-focus-ring);
}

.app-shell__content {
  width: min(100%, var(--v2-page-max-width));
  margin-inline: auto;
  padding: 10px;
}

.app-shell__content.app-shell__content--full {
  width: 100%;
  flex: 1;
  display: flex;
  margin: 0;
  padding: 0;
}

.app-shell__content:focus {
  outline: 3px solid var(--v2-color-primary);
  outline-offset: -3px;
}

.app-shell__notice-region {
  width: min(100%, var(--v2-page-max-width));
  margin: var(--v2-space-4) auto 0;
  padding-inline: var(--v2-page-gutter);
}

.app-shell__role-tester {
  flex: 0 0 auto;
  display: grid;
  place-items: center;
  margin-block-start: auto;
  padding: var(--v2-space-2);
}

.app-shell__role-tester + .app-shell__collapse-toggle {
  margin-block-start: 0;
}

.app-shell__role-tester-trigger {
  width: 3.25rem;
  height: 3.25rem;
  color: var(--v2-color-surface);
  background: var(--v2-color-primary);
  border: 0;
  border-radius: 50%;
  box-shadow: 0 0.75rem 2rem var(--v2-color-focus-ring);
  font: inherit;
  font-weight: var(--v2-font-weight-bold);
  cursor: pointer;
}

.app-shell__role-tester-trigger:focus-visible,
.app-shell__role-tester-panel button:focus-visible {
  outline: 3px solid var(--v2-color-focus-ring);
  outline-offset: 2px;
}

.app-shell__role-tester-panel {
  position: fixed;
  z-index: 50;
  inset-inline-start: calc(12.5rem + var(--v2-space-2));
  inset-block-end: 3.5rem;
  width: 15rem;
  max-height: min(32rem, calc(100vh - 7rem));
  overflow-y: auto;
  padding: var(--v2-space-3);
  background: var(--v2-color-surface);
  border: 1px solid var(--v2-color-border);
  border-radius: var(--v2-radius-lg);
  box-shadow: var(--v2-shadow-panel);
}

.app-shell__role-tester-panel header {
  display: grid;
  gap: var(--v2-space-1);
  padding: var(--v2-space-2);
}

.app-shell__role-tester-panel header small,
.app-shell__role-tester-panel button small {
  color: var(--v2-color-text-muted);
  font-size: var(--v2-font-size-11);
}

.app-shell__role-tester-panel button {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  min-height: 2.75rem;
  padding: var(--v2-space-2) var(--v2-space-3);
  color: var(--v2-color-text-secondary);
  background: transparent;
  border: 0;
  border-radius: var(--v2-radius-sm);
  font: inherit;
  cursor: pointer;
}

.app-shell__role-tester-panel button:hover,
.app-shell__role-tester-panel button.is-active {
  color: var(--v2-color-primary);
  background: var(--v2-color-primary-soft);
}

.app-shell__role-tester-panel button:disabled {
  cursor: wait;
  opacity: 0.65;
}

.app-shell__scrim {
  display: none;
}

.app-shell__mobile-account {
  display: none;
}

.app-shell--collapsed {
  grid-template-columns: 5rem minmax(0, 1fr);
}

.app-shell--collapsed .app-shell__role-tester-panel {
  inset-inline-start: calc(5rem + var(--v2-space-2));
}

.app-shell--collapsed .app-shell__brand {
  justify-content: center;
  padding-inline: var(--v2-space-2);
}

.app-shell--collapsed .app-shell__brand-copy,
.app-shell--collapsed .app-shell__domain-link > span:last-child,
.app-shell--collapsed .app-shell__workspaces,
.app-shell--collapsed .app-shell__domain--active .app-shell__workspaces {
  display: none;
}

.app-shell--collapsed .app-shell__domain-link {
  justify-content: center;
  padding-inline: var(--v2-space-2);
}

@media (max-width: 70rem) and (min-width: 48.01rem) {
  .app-shell__header {
    grid-template-columns: minmax(20rem, 1fr) auto auto;
  }

  .app-shell__context-controls {
    grid-template-columns: repeat(2, minmax(10rem, 1fr));
  }

  .app-shell__context-controls :deep(.v2-field) {
    grid-template-columns: 1fr;
  }

  .app-shell__context-controls :deep(.v2-field__label) {
    display: none;
  }
}

@media (max-width: 48rem) {
  .app-shell {
    display: block;
  }

  .app-shell__sidebar {
    position: fixed;
    z-index: var(--v2-z-dropdown);
    inset: 0 auto 0 0;
    width: min(19rem, calc(100vw - 3rem));
    transform: translateX(-100%);
    transition: transform var(--v2-motion-base) var(--v2-ease-standard);
  }

  .app-shell__collapse-toggle {
    display: none;
  }

  .app-shell--nav-open .app-shell__sidebar {
    transform: translateX(0);
  }

  .app-shell__scrim {
    position: fixed;
    z-index: calc(var(--v2-z-dropdown) - 1);
    inset: 0;
    display: block;
    background: var(--v2-color-overlay);
    border: 0;
  }

  .app-shell__header {
    height: auto;
    min-height: auto;
    grid-template-columns: auto minmax(0, 1fr) auto auto;
    gap: var(--v2-space-2);
    padding: var(--v2-space-3);
  }

  .app-shell__menu-toggle {
    width: var(--v2-control-height-touch);
    height: var(--v2-control-height-touch);
    display: grid;
    place-items: center;
    color: var(--v2-color-primary);
    background: var(--v2-color-primary-soft);
    border: 0;
    border-radius: var(--v2-radius-sm);
    grid-column: 1;
    grid-row: 1;
  }

  .app-shell__context-controls {
    grid-column: 2;
    grid-row: 1;
    min-width: 0;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: var(--v2-space-1);
  }

  .app-shell__context-controls :deep(.v2-field) {
    grid-template-columns: minmax(0, 1fr);
  }

  .app-shell__context-controls :deep(.v2-field__control) {
    min-width: 0;
    min-height: var(--v2-control-height-md);
    padding-inline: var(--v2-space-2) var(--v2-space-6);
    font-size: var(--v2-font-size-11);
  }

  .app-shell__context-controls :deep(.v2-field__label),
  .app-shell__account-copy,
  .app-shell__account .v2-button {
    display: none;
  }

  .app-shell__notification {
    grid-column: 3;
    grid-row: 1;
  }

  .app-shell__account {
    grid-column: 4;
    grid-row: 1;
  }

  .app-shell__brand {
    padding-inline: var(--v2-space-4);
  }

  .app-shell__nav-close {
    display: grid;
    margin-inline-start: auto;
  }

  .app-shell__mobile-account {
    position: sticky;
    inset-block-end: 0;
    display: flex;
    align-items: center;
    gap: var(--v2-space-2);
    padding: var(--v2-space-3) var(--v2-space-4);
    background: var(--v2-color-surface);
    border-block-start: var(--v2-border-width) solid var(--v2-color-border);
  }

  .app-shell__mobile-account .app-shell__account-copy {
    min-width: 0;
    display: grid;
    flex: 1;
  }

  .app-shell__workspace-bar {
    height: auto;
    min-height: auto;
    flex-wrap: wrap;
    align-items: center;
    gap: 0 var(--v2-space-3);
    padding: var(--v2-space-2) var(--v2-space-4) 0;
  }

  .app-shell__tabs {
    flex-basis: 100%;
    order: 3;
  }

  .app-shell__tab {
    width: max-content;
    min-width: max-content;
    padding-inline: var(--v2-space-8);
    clip-path: polygon(
      0 0,
      calc(100% - 1.625rem) 0,
      calc(100% - 1.375rem) 0.125rem,
      calc(100% - 1.1875rem) 0.4375rem,
      100% 100%,
      0 100%
    );
  }

  .app-shell__breadcrumb {
    min-height: 2rem;
  }

  .app-shell__content {
    padding: 10px;
  }

  .app-shell__notice-region {
    padding-inline: var(--v2-space-4);
  }

  .app-shell__role-tester {
    padding: var(--v2-space-2) var(--v2-space-4);
  }

  .app-shell__role-tester-panel {
    inset-inline-start: var(--v2-space-4);
    inset-block-end: 4rem;
    width: min(15rem, calc(100vw - 2rem));
  }

  :global(body.v2-mobile-nav-open) {
    overflow: hidden;
  }
}

@media (prefers-reduced-motion: reduce) {
  .app-shell__sidebar {
    transition-duration: 1ms;
  }
}
</style>
