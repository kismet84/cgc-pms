import { createPinia, setActivePinia } from 'pinia'
import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import AppShell from '@/layouts/AppShell.vue'
import { loadNotificationSummary, openNotificationStream } from '@/services/alerts'
import { loadCommunicationUnreadCount, openCommunicationStream } from '@/services/communication'
import { useSessionStore } from '@/stores/session'

vi.mock('@/services/alerts', () => ({
  loadNotificationSummary: vi.fn(),
  markAllNotificationsRead: vi.fn(),
  markNotificationRead: vi.fn(),
  openNotificationStream: vi.fn(),
}))
vi.mock('@/services/account', () => ({
  loadPreferences: vi.fn().mockResolvedValue({ sidebarCollapsed: false }),
}))
vi.mock('@/services/projects', () => ({ loadVisibleProjects: vi.fn().mockResolvedValue([]) }))
vi.mock('@/services/communication', () => ({
  loadCommunicationUnreadCount: vi.fn(),
  openCommunicationStream: vi.fn(),
}))

beforeEach(() => {
  setActivePinia(createPinia())
  vi.clearAllMocks()
  localStorage.clear()
  vi.stubGlobal('matchMedia', () => ({
    matches: false,
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
  }))
  useSessionStore().replaceUserInfo({
    tenantId: '1001',
    userId: '1',
    username: 'tester',
    roles: ['USER'],
    permissions: ['communication:view'],
  })
  vi.mocked(openCommunicationStream).mockReturnValue({ close: vi.fn() } as unknown as EventSource)
  vi.mocked(openNotificationStream).mockReturnValue({ close: vi.fn() })
  vi.mocked(loadCommunicationUnreadCount).mockResolvedValue({ count: 0 })
})

describe('AppShell communication unread', () => {
  it('shows only accessible catalog pages in recent-open order', async () => {
    useSessionStore().replaceUserInfo({
      tenantId: '1001',
      userId: '1',
      username: 'tester',
      roles: ['USER'],
      permissions: ['dashboard:view', 'project:query'],
    })
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [
        { path: '/dashboard', component: { template: '<div>dashboard</div>' } },
        { path: '/project/list', component: { template: '<div>projects</div>' } },
        { path: '/project/:projectId/overview', component: { template: '<div>project</div>' } },
      ],
    })
    await router.push('/dashboard')
    await router.isReady()
    const wrapper = mount(AppShell, { global: { plugins: [router] } })
    await flushPromises()

    await router.push('/project/list')
    await flushPromises()
    await router.push('/project/P-1/overview')
    await flushPromises()

    expect(wrapper.get('summary[aria-label="最近打开"]')).toBeTruthy()
    expect(wrapper.findAll('.app-shell__recent-item').map((item) => item.text())).toEqual([
      '项目列表项目履约 · 项目管理',
      '驾驶舱工作台 · 经营驾驶舱',
    ])
    wrapper.unmount()
  })

  it('keeps newer unread result when an aborted request rejects later', async () => {
    let rejectFirst!: (reason: Error) => void
    vi.mocked(loadCommunicationUnreadCount)
      .mockReturnValueOnce(
        new Promise((_, reject) => {
          rejectFirst = reject
        }),
      )
      .mockResolvedValueOnce({ count: 7 })
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [{ path: '/', component: { template: '<div />' } }],
    })
    await router.push('/')
    await router.isReady()
    const wrapper = mount(AppShell, { global: { plugins: [router] } })
    await flushPromises()

    window.dispatchEvent(new Event('communication-unread-changed'))
    await flushPromises()
    expect(wrapper.get('a.app-shell__communication').attributes('aria-label')).toContain('7 条未读')

    rejectFirst(new Error('aborted request failed late'))
    await flushPromises()
    expect(wrapper.get('a.app-shell__communication').attributes('aria-label')).toContain('7 条未读')
    wrapper.unmount()
  })

  it('loads notifications once on first stream open and refreshes after reconnect', async () => {
    useSessionStore().replaceUserInfo({
      tenantId: '1001',
      userId: '1',
      username: 'tester',
      roles: ['USER'],
      permissions: ['alert:view', 'notification:view'],
    })
    vi.mocked(loadNotificationSummary).mockResolvedValue([
      { pageNo: 1, pageSize: 8, total: 0, records: [] },
      { count: 3 },
    ])
    let reopen!: () => void
    vi.mocked(openNotificationStream).mockImplementation((onOpen) => {
      reopen = onOpen
      onOpen()
      return { close: vi.fn() }
    })
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [{ path: '/', component: { template: '<div />' } }],
    })
    await router.push('/')
    await router.isReady()
    const wrapper = mount(AppShell, { global: { plugins: [router] } })
    await flushPromises()

    expect(openNotificationStream).toHaveBeenCalledTimes(1)
    expect(loadNotificationSummary).toHaveBeenCalledTimes(1)
    expect(wrapper.get('button.app-shell__notification').attributes('aria-label')).toContain(
      '3 条未读',
    )

    reopen()
    await flushPromises()
    expect(loadNotificationSummary).toHaveBeenCalledTimes(2)
    wrapper.unmount()
  })

  it('ignores first communication handshake and refreshes on messages or reconnect', async () => {
    let onEvent!: Parameters<typeof openCommunicationStream>[0]
    let onOpen!: NonNullable<Parameters<typeof openCommunicationStream>[2]>
    vi.mocked(openCommunicationStream).mockImplementation((event, _error, open) => {
      onEvent = event
      onOpen = open!
      open?.()
      event({ action: 'REFRESH' })
      return { close: vi.fn() }
    })
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [{ path: '/', component: { template: '<div />' } }],
    })
    await router.push('/')
    await router.isReady()
    const wrapper = mount(AppShell, { global: { plugins: [router] } })
    await flushPromises()

    expect(loadCommunicationUnreadCount).toHaveBeenCalledTimes(1)
    onEvent({ action: 'PING' })
    await flushPromises()
    expect(loadCommunicationUnreadCount).toHaveBeenCalledTimes(1)

    onEvent({ action: 'MESSAGE', conversationId: 'conversation-1' })
    await flushPromises()
    expect(loadCommunicationUnreadCount).toHaveBeenCalledTimes(2)

    onOpen()
    onEvent({ action: 'REFRESH' })
    await flushPromises()
    expect(loadCommunicationUnreadCount).toHaveBeenCalledTimes(3)
    wrapper.unmount()
  })

  it('keeps newer notification state when an aborted request rejects later', async () => {
    useSessionStore().replaceUserInfo({
      tenantId: '1001',
      userId: '1',
      username: 'tester',
      roles: ['USER'],
      permissions: ['alert:view', 'notification:view'],
    })
    let rejectFirst!: (reason: Error) => void
    let refreshFromEvent!: () => void
    vi.mocked(loadNotificationSummary)
      .mockReturnValueOnce(
        new Promise((_, reject) => {
          rejectFirst = reject
        }),
      )
      .mockResolvedValueOnce([{ pageNo: 1, pageSize: 8, total: 0, records: [] }, { count: 7 }])
    vi.mocked(openNotificationStream).mockImplementation((_onOpen, onEvent) => {
      refreshFromEvent = onEvent
      return { close: vi.fn() }
    })
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [{ path: '/', component: { template: '<div />' } }],
    })
    await router.push('/')
    await router.isReady()
    const wrapper = mount(AppShell, { global: { plugins: [router] } })
    await flushPromises()

    refreshFromEvent()
    await flushPromises()
    expect(wrapper.get('button.app-shell__notification').attributes('aria-label')).toContain(
      '7 条未读',
    )

    rejectFirst(new Error('aborted request failed late'))
    await flushPromises()
    expect(wrapper.get('button.app-shell__notification').attributes('aria-label')).toContain(
      '7 条未读',
    )
    wrapper.unmount()
  })
})
