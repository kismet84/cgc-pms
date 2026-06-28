import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { defineComponent, h } from 'vue'

// ── Helpers ──
function flushPromises() {
  return new Promise((resolve) => setTimeout(resolve, 0))
}

// ── Mock EventSource ──
const mockEventSource = {
  addEventListener: vi.fn(),
  close: vi.fn(),
  onerror: null as ((ev: Event) => void) | null,
}

// ── Mock notification API (vi.mock hoisted to top) ──
vi.mock('@/api/modules/notification', () => ({
  getNotifications: vi.fn(),
  getUnreadCount: vi.fn(),
  markAsRead: vi.fn(),
  markAllAsRead: vi.fn(),
  createNotificationStream: vi.fn(),
}))

// ── Mock ant-design-vue (message only; components provided via stubs) ──
vi.mock('ant-design-vue', () => ({
  message: {
    success: vi.fn(),
    error: vi.fn(),
    info: vi.fn(),
    warning: vi.fn(),
  },
}))

// ── Import mocked modules (after vi.mock) ──
import {
  getNotifications,
  getUnreadCount,
  markAsRead,
  markAllAsRead,
  createNotificationStream,
} from '@/api/modules/notification'
import { message } from 'ant-design-vue'
import NotificationBell from '@/components/NotificationBell.vue'

// ── Stub Components ──
const APopoverStub = defineComponent({
  name: 'APopoverStub',
  props: {
    trigger: String,
    placement: String,
    overlayClassName: String,
    arrowPointAtCenter: Boolean,
  },
  emits: ['open-change'],
  setup(_, { slots }) {
    return () => h('div', { class: 'mock-popover' }, [slots.default?.(), slots.content?.()])
  },
})

const ABadgeStub = defineComponent({
  name: 'ABadgeStub',
  props: {
    count: [Number, String],
    overflowCount: { type: Number, default: 99 },
    numberStyle: Object,
    offset: Array,
  },
  setup(props, { slots }) {
    return () => {
      const n = Number(props.count) || 0
      const display = n > (props.overflowCount ?? 99) ? '99+' : String(n)
      return h('span', { class: 'mock-badge' }, [
        slots.default?.(),
        n > 0 ? h('sup', { class: 'badge-count' }, display) : null,
      ])
    }
  },
})

const AButtonStub = defineComponent({
  name: 'AButtonStub',
  props: {
    type: String,
    size: String,
    loading: Boolean,
    disabled: Boolean,
  },
  emits: ['click'],
  setup(props, { emit, slots }) {
    return () =>
      h(
        'button',
        {
          class: 'mock-btn',
          disabled: props.disabled,
          onClick: () => emit('click'),
        },
        slots.default?.(),
      )
  },
})

const stubs = {
  'a-popover': APopoverStub,
  'a-badge': ABadgeStub,
  'a-button': AButtonStub,
  'a-tag': true,
  'a-spin': true,
  BellOutlined: true,
}

// ── Helpers: convenient way to emit popover open-change ──
function emitPopoverOpen(wrapper: ReturnType<typeof mount>, visible: boolean) {
  const popover = wrapper.findComponent<typeof APopoverStub>('.mock-popover')
  popover.vm.$emit('open-change', visible)
}

// ── Tests ──
describe('NotificationBell', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    // Default mocks: SSE returns fake EventSource
    vi.mocked(createNotificationStream).mockReturnValue(mockEventSource as unknown as EventSource)
  })

  it('does not fetch unread count on mount', async () => {
    vi.mocked(getUnreadCount).mockResolvedValue({ count: 5 })

    const wrapper = mount(NotificationBell, { global: { stubs } })
    await flushPromises()

    expect(getUnreadCount).not.toHaveBeenCalled()
    const badge = wrapper.find('.badge-count')
    expect(badge.exists()).toBe(false)
  })

  it('connects SSE when popover opens', async () => {
    vi.mocked(getUnreadCount).mockResolvedValue({ count: 0 })
    vi.mocked(getNotifications).mockResolvedValue({
      records: [],
      total: 0,
      pageNo: 1,
      pageSize: 20,
    })

    mount(NotificationBell, { global: { stubs } })
    await flushPromises()

    expect(createNotificationStream).not.toHaveBeenCalled()

    const wrapper = mount(NotificationBell, { global: { stubs } })
    await flushPromises()

    emitPopoverOpen(wrapper, true)
    await flushPromises()

    expect(createNotificationStream).toHaveBeenCalledTimes(1)
    // Should register both event listeners
    expect(mockEventSource.addEventListener).toHaveBeenCalledWith('connected', expect.any(Function))
    expect(mockEventSource.addEventListener).toHaveBeenCalledWith(
      'notification',
      expect.any(Function),
    )
  })

  it('handlePopoverChange(true) triggers fetch', async () => {
    vi.mocked(getUnreadCount).mockResolvedValue({ count: 0 })
    vi.mocked(getNotifications).mockResolvedValue({
      records: [
        {
          id: '1',
          tenantId: '',
          userId: '',
          title: 'Hello',
          content: 'World',
          bizType: 'INFO',
          bizId: null,
          notifyType: 'INFO',
          isRead: 0,
          readTime: null,
          createdTime: '2026-06-13 10:00:00',
        },
      ],
      total: 1,
      pageNo: 1,
      pageSize: 20,
    })

    const wrapper = mount(NotificationBell, { global: { stubs } })
    await flushPromises()

    emitPopoverOpen(wrapper, true)
    await flushPromises()

    expect(getNotifications).toHaveBeenCalledWith({
      pageNo: 1,
      pageSize: 20,
    })
  })

  it('handleMarkRead decrements unread count', async () => {
    vi.mocked(getUnreadCount).mockResolvedValue({ count: 5 })
    vi.mocked(getNotifications).mockResolvedValue({
      records: [
        {
          id: 'n1',
          tenantId: '',
          userId: '',
          title: 'Unread item',
          content: 'Body',
          bizType: 'INFO',
          bizId: null,
          notifyType: 'INFO',
          isRead: 0,
          readTime: null,
          createdTime: '2026-06-13 10:00:00',
        },
        {
          id: 'n2',
          tenantId: '',
          userId: '',
          title: 'Read item',
          content: 'Body 2',
          bizType: 'WARN',
          bizId: null,
          notifyType: 'WARN',
          isRead: 1,
          readTime: '2026-06-13 09:00:00',
          createdTime: '2026-06-13 09:00:00',
        },
      ],
      total: 2,
      pageNo: 1,
      pageSize: 20,
    })
    vi.mocked(markAsRead).mockResolvedValue({ id: 'n1', read: true })

    const wrapper = mount(NotificationBell, { global: { stubs } })
    await flushPromises()

    // Open popover to render notification list
    emitPopoverOpen(wrapper, true)
    await flushPromises()

    // Click first (unread) item
    const unreadItem = wrapper.find('.nb-item.nb-unread')
    expect(unreadItem.exists()).toBe(true)
    await unreadItem.trigger('click')
    await flushPromises()

    expect(markAsRead).toHaveBeenCalledWith('n1')
    // Badge count should have decreased by 1 (from 5 to 4)
    const badge = wrapper.find('.badge-count')
    expect(badge.exists()).toBe(true)
    expect(badge.text()).toBe('4')
  })

  it('badge not shown when unread count is 0', async () => {
    vi.mocked(getUnreadCount).mockResolvedValue({ count: 0 })

    const wrapper = mount(NotificationBell, { global: { stubs } })
    await flushPromises()

    const badge = wrapper.find('.badge-count')
    expect(badge.exists()).toBe(false)
  })

  it('handleMarkAllRead clears all', async () => {
    vi.mocked(getUnreadCount).mockResolvedValue({ count: 3 })
    vi.mocked(getNotifications).mockResolvedValue({
      records: [
        {
          id: 'n1',
          tenantId: '',
          userId: '',
          title: 'Item',
          content: 'Content',
          bizType: 'INFO',
          bizId: null,
          notifyType: 'INFO',
          isRead: 0,
          readTime: null,
          createdTime: '2026-01-01 10:00:00',
        },
      ],
      total: 1,
      pageNo: 1,
      pageSize: 20,
    })
    vi.mocked(markAllAsRead).mockResolvedValue({
      userId: '1',
      allRead: true,
    })

    const wrapper = mount(NotificationBell, { global: { stubs } })
    await flushPromises()

    // Open popover to reveal the "全部标为已读" button
    emitPopoverOpen(wrapper, true)
    await flushPromises()

    // Click the button
    const btn = wrapper.find('.mock-btn')
    expect(btn.exists()).toBe(true)
    await btn.trigger('click')
    await flushPromises()

    expect(markAllAsRead).toHaveBeenCalled()
    // Badge should no longer show since unreadCount is 0
    const badge = wrapper.find('.badge-count')
    expect(badge.exists()).toBe(false)
  })

  it('logs error and shows feedback when markAsRead fails', async () => {
    const consoleSpy = vi.spyOn(console, 'error').mockImplementation(() => {})

    vi.mocked(getUnreadCount).mockResolvedValue({ count: 5 })
    vi.mocked(getNotifications).mockResolvedValue({
      records: [
        {
          id: 'n1',
          tenantId: '',
          userId: '',
          title: 'Unread item',
          content: 'Body',
          bizType: 'INFO',
          bizId: null,
          notifyType: 'INFO',
          isRead: 0,
          readTime: null,
          createdTime: '2026-06-13 10:00:00',
        },
      ],
      total: 1,
      pageNo: 1,
      pageSize: 20,
    })
    // markAsRead rejects to simulate API failure
    vi.mocked(markAsRead).mockRejectedValue(new Error('Network error'))

    const wrapper = mount(NotificationBell, { global: { stubs } })
    await flushPromises()

    // Open popover to render notification list
    emitPopoverOpen(wrapper, true)
    await flushPromises()

    // Click the unread item to trigger handleMarkRead
    const unreadItem = wrapper.find('.nb-item.nb-unread')
    expect(unreadItem.exists()).toBe(true)
    await unreadItem.trigger('click')
    await flushPromises()

    // Verify error was logged with NotificationBell prefix
    expect(consoleSpy).toHaveBeenCalledWith('NotificationBell: 标记已读失败', expect.any(Error))
    // Verify user-facing feedback
    expect(vi.mocked(message.error)).toHaveBeenCalledWith('标记已读失败')

    consoleSpy.mockRestore()
  })

  it('logs error and shows feedback when markAllAsRead fails', async () => {
    const consoleSpy = vi.spyOn(console, 'error').mockImplementation(() => {})

    vi.mocked(getUnreadCount).mockResolvedValue({ count: 3 })
    vi.mocked(getNotifications).mockResolvedValue({
      records: [
        {
          id: 'n1',
          tenantId: '',
          userId: '',
          title: 'Item',
          content: 'Content',
          bizType: 'INFO',
          bizId: null,
          notifyType: 'INFO',
          isRead: 0,
          readTime: null,
          createdTime: '2026-01-01 10:00:00',
        },
      ],
      total: 1,
      pageNo: 1,
      pageSize: 20,
    })
    // markAllAsRead rejects to simulate API failure
    vi.mocked(markAllAsRead).mockRejectedValue(new Error('Network error'))

    const wrapper = mount(NotificationBell, { global: { stubs } })
    await flushPromises()

    // Open popover to reveal the "全部标为已读" button
    emitPopoverOpen(wrapper, true)
    await flushPromises()

    // Click the button
    const btn = wrapper.find('.mock-btn')
    expect(btn.exists()).toBe(true)
    await btn.trigger('click')
    await flushPromises()

    // Verify error was logged with NotificationBell prefix
    expect(consoleSpy).toHaveBeenCalledWith('NotificationBell: 操作失败', expect.any(Error))
    // Verify user-facing feedback
    expect(vi.mocked(message.error)).toHaveBeenCalledWith('操作失败')

    consoleSpy.mockRestore()
  })
})
