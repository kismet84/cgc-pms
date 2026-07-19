<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { RouterLink, RouterView, useRoute, useRouter } from 'vue-router'
import { V2Alert, V2Button, V2Dialog, V2PageState, V2Select } from '@/components'
import { findWorkspace, visibleNavigation } from '@/navigation/catalog'
import ShellLoadingPage from '@/pages/shell/ShellLoadingPage.vue'
import { useSessionStore } from '@/stores/session'
import { useWorkspaceStore } from '@/stores/workspace'

const route = useRoute()
const router = useRouter()
const session = useSessionStore()
const workspaceStore = useWorkspaceStore()
const mobileNavigationOpen = ref(false)
const notificationOpen = ref(false)
const isMobile = ref(false)
const menuToggle = ref<HTMLButtonElement | null>(null)
const navigationPanel = ref<HTMLElement | null>(null)
const navigationClose = ref<HTMLButtonElement | null>(null)
let mobileMedia: MediaQueryList | null = null
let removeAfterEach: (() => void) | null = null
let restoreMenuFocus = false

const navigation = computed(() => visibleNavigation(session.permissions))
const activeMatch = computed(() => findWorkspace(route.path))
const visibleActiveWorkspace = computed(() => {
  const match = activeMatch.value
  if (!match) return undefined
  return navigation.value
    .find((domain) => domain.id === match.domain.id)
    ?.workspaces.find((workspace) => workspace.id === match.workspace.id)
})

const projectOptions = computed(() => workspaceStore.projects)
const reportPeriodOptions = computed(() => workspaceStore.reportPeriods)
const accountName = computed(
  () => session.userInfo?.realName || session.userInfo?.username || '当前用户',
)

watch(
  () => route.fullPath,
  () => {
    workspaceStore.syncRoute(route.path, route.query, route.params)
    mobileNavigationOpen.value = false
    restoreMenuFocus = false
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

async function signOut(): Promise<void> {
  try {
    await session.logout()
  } finally {
    await router.replace('/login')
  }
}
</script>

<template>
  <div class="app-shell" :class="{ 'app-shell--nav-open': mobileNavigationOpen }">
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
            :to="domain.workspaces[0]!.tabs[0]!.path"
            :aria-label="domain.label"
          >
            <span class="app-shell__domain-badge" aria-hidden="true">{{ domain.badge }}</span>
            <span>{{ domain.label }}</span>
          </RouterLink>
          <div class="app-shell__workspaces">
            <RouterLink
              v-for="workspace in domain.workspaces"
              :key="workspace.id"
              class="app-shell__workspace-link"
              :class="{ 'router-link-active': activeMatch?.workspace.id === workspace.id }"
              :to="workspace.tabs[0]!.path"
            >
              {{ workspace.label }}
            </RouterLink>
          </div>
        </section>
      </nav>

      <div v-if="!navigation.length" class="app-shell__empty-navigation">
        当前账号无可访问业务域
      </div>

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
            label="当前项目"
            :placeholder="projectOptions.length ? '选择项目' : '暂无可用项目'"
            :disabled="!projectOptions.length"
            @update:model-value="selectProject"
          />
          <V2Select
            id="global-report-period"
            :model-value="workspaceStore.selectedReportPeriod || ''"
            :options="reportPeriodOptions"
            label="报告期"
            :placeholder="reportPeriodOptions.length ? '选择报告期' : '暂无可用报告期'"
            :disabled="!reportPeriodOptions.length"
            @update:model-value="selectReportPeriod"
          />
        </div>

        <button
          type="button"
          class="app-shell__notification"
          aria-label="打开通知中心占位"
          :aria-expanded="notificationOpen"
          aria-haspopup="dialog"
          @click="notificationOpen = true"
        >
          <svg aria-hidden="true" viewBox="0 0 24 24">
            <path d="M18 8a6 6 0 00-12 0c0 7-3 7-3 9h18c0-2-3-2-3-9M10 21h4" />
          </svg>
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
        <div>
          <span>{{ activeMatch?.domain.label || '应用壳' }}</span>
          <strong>{{ activeMatch?.workspace.label || '权限导航' }}</strong>
        </div>
        <span v-if="workspaceStore.objectContext" class="app-shell__object-context">
          对象 {{ workspaceStore.objectContext.kind }} / {{ workspaceStore.objectContext.id }}
        </span>
      </div>

      <nav v-if="visibleActiveWorkspace" class="app-shell__tabs" aria-label="工作区标签页">
        <RouterLink
          v-for="tab in visibleActiveWorkspace.tabs"
          :key="tab.path"
          :to="{ path: tab.path, query: route.query }"
          class="app-shell__tab"
        >
          {{ tab.label }}
        </RouterLink>
      </nav>

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

      <main id="shell-main-content" class="app-shell__content" tabindex="-1">
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
      description="M1 仅提供壳级入口，不连接业务通知、SSE 或写操作。"
    >
      <V2PageState
        kind="empty"
        :heading-level="3"
        title="暂无壳级通知"
        description="业务通知能力尚未迁移；此处不显示模拟数量或业务消息。"
      />
    </V2Dialog>
  </div>
</template>

<style scoped>
.app-shell {
  min-height: 100vh;
  display: grid;
  grid-template-columns: 17rem minmax(0, 1fr);
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
  overflow-y: auto;
  background: var(--v2-color-surface);
  border-inline-end: var(--v2-border-width) solid var(--v2-color-border);
}

.app-shell__brand {
  min-height: 4.75rem;
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
  display: grid;
  gap: var(--v2-space-1);
  padding: var(--v2-space-3);
}

.app-shell__domain {
  border-radius: var(--v2-radius-sm);
}

.app-shell__domain-link {
  min-height: var(--v2-control-height-touch);
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
  border: var(--v2-border-width) solid currentColor;
  border-radius: var(--v2-radius-sm);
  font-size: var(--v2-font-size-11);
}

.app-shell__workspaces {
  display: none;
  padding: var(--v2-space-1) var(--v2-space-2) var(--v2-space-2) 3.5rem;
}

.app-shell__domain--active .app-shell__workspaces {
  display: grid;
}

.app-shell__workspace-link {
  padding: var(--v2-space-2);
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

.app-shell__main {
  min-width: 0;
}

.app-shell__header {
  min-height: 4.75rem;
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

.app-shell__context-controls {
  display: grid;
  grid-template-columns: repeat(2, minmax(12rem, 18rem));
  gap: var(--v2-space-4);
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
  min-height: 3.5rem;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--v2-space-4);
  padding: var(--v2-space-3) var(--v2-page-gutter);
  background: var(--v2-color-surface);
  border-block-end: var(--v2-border-width) solid var(--v2-color-border-subtle);
}

.app-shell__workspace-bar > div {
  display: grid;
  gap: var(--v2-space-1);
}

.app-shell__workspace-bar span {
  color: var(--v2-color-text-muted);
  font-size: var(--v2-font-size-11);
}

.app-shell__workspace-bar strong {
  color: var(--v2-color-text-strong);
  font-size: var(--v2-font-size-17);
}

.app-shell__object-context {
  padding: var(--v2-space-2) var(--v2-space-3);
  background: var(--v2-color-primary-soft);
  border-radius: var(--v2-radius-round);
}

.app-shell__tabs {
  min-height: var(--v2-control-height-touch);
  display: flex;
  gap: var(--v2-space-1);
  overflow-x: auto;
  padding: 0 var(--v2-page-gutter);
  background: var(--v2-color-surface);
  border-block-end: var(--v2-border-width) solid var(--v2-color-border);
}

.app-shell__tab {
  min-height: var(--v2-control-height-touch);
  display: inline-flex;
  align-items: center;
  padding: 0 var(--v2-space-3);
  color: var(--v2-color-text-secondary);
  border-block-end: 2px solid transparent;
  font-size: var(--v2-font-size-12);
  font-weight: var(--v2-font-weight-semibold);
  text-decoration: none;
  white-space: nowrap;
}

.app-shell__tab:hover,
.app-shell__tab.router-link-active {
  color: var(--v2-color-primary);
  border-block-end-color: var(--v2-color-primary);
}

.app-shell__content {
  width: min(100%, var(--v2-page-max-width));
  margin-inline: auto;
  padding: var(--v2-page-gutter);
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

.app-shell__scrim {
  display: none;
}

.app-shell__mobile-account {
  display: none;
}

@media (max-width: 70rem) and (min-width: 48.01rem) {
  .app-shell {
    grid-template-columns: 5rem minmax(0, 1fr);
  }

  .app-shell__brand {
    justify-content: center;
    padding-inline: var(--v2-space-2);
  }

  .app-shell__brand-copy,
  .app-shell__domain-link > span:last-child,
  .app-shell__workspaces,
  .app-shell__domain--active .app-shell__workspaces {
    display: none;
    height: 0;
    overflow: hidden;
    padding: 0;
    visibility: hidden;
  }

  .app-shell__domain-link {
    justify-content: center;
    padding-inline: var(--v2-space-2);
  }

  .app-shell__header {
    grid-template-columns: minmax(20rem, 1fr) auto auto;
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
    grid-column: 1 / -1;
    grid-row: 2;
    min-width: 0;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: var(--v2-space-2);
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
    align-items: flex-start;
    padding-inline: var(--v2-space-4);
  }

  .app-shell__object-context {
    max-width: 50%;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .app-shell__tabs {
    padding-inline: var(--v2-space-3);
  }

  .app-shell__content {
    padding: var(--v2-space-4);
  }

  .app-shell__notice-region {
    padding-inline: var(--v2-space-4);
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
