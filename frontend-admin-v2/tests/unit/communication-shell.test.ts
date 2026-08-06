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
})

describe('AppShell communication unread', () => {
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

  it('refreshes notification unread state when each tab stream opens', async () => {
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
    vi.mocked(openNotificationStream).mockImplementation((onOpen) => {
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
    expect(loadNotificationSummary).toHaveBeenCalledTimes(2)
    expect(wrapper.get('button.app-shell__notification').attributes('aria-label')).toContain(
      '3 条未读',
    )
    wrapper.unmount()
  })
})
