import { describe, it, expect, vi, beforeEach, beforeAll } from 'vitest'
import { mount } from '@vue/test-utils'
import { defineComponent, h } from 'vue'

// ── Mock window.matchMedia (not available in jsdom) ──
beforeAll(() => {
  Object.defineProperty(window, 'matchMedia', {
    writable: true,
    value: vi.fn().mockImplementation((query: string) => ({
      matches: false,
      media: query,
      onchange: null,
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
      addListener: vi.fn(), // deprecated
      removeListener: vi.fn(), // deprecated
      dispatchEvent: vi.fn(),
    })),
  })
})

// ── Captured mockPush for assertions ──
const mockPush = vi.fn()

// ── Mock vue-router ──
vi.mock('vue-router', () => ({
  useRouter: () => ({ push: mockPush }),
}))

// ── Mock pinia stores ──
vi.mock('@/stores/user', () => ({
  useUserStore: () => ({
    logout: vi.fn(),
    userInfo: { realName: '张三', roleName: '项目经理' },
  }),
}))

// ── Mock icons ──
vi.mock('@ant-design/icons-vue', () => ({
  MenuFoldOutlined: { template: '<span class="icon-stub hamburger-icon"><slot /></span>' },
  ProjectOutlined: { template: '<span class="icon-stub project-icon"><slot /></span>' },
}))

// ── Mock sub-components ──
vi.mock('@/components/NotificationBell.vue', () => ({
  default: { name: 'NotificationBell', template: '<span class="nb-stub">Bell</span>' },
}))
vi.mock('@/layouts/components/SidebarMenu.vue', () => ({
  default: { name: 'SidebarMenu', template: '<div class="sidebar-stub">Menu</div>' },
}))

// ── Import after mocks ──
import BasicLayout from '@/layouts/BasicLayoutAsync.vue'

// ── Custom stubs for Ant Design components that need click handling ──
const ADropdownStub = defineComponent({
  name: 'ADropdownStub',
  setup(_, { slots }) {
    return () =>
      h('div', { class: 'stub-dropdown' }, [
        h('div', { class: 'stub-dropdown-default' }, slots.default?.()),
        h('div', { class: 'stub-dropdown-overlay' }, slots.overlay?.()),
      ])
  },
})

const AMenuStub = defineComponent({
  name: 'AMenuStub',
  setup(_, { slots }) {
    return () => h('div', { class: 'stub-menu' }, slots.default?.())
  },
})

const AMenuItemStub = defineComponent({
  name: 'AMenuItemStub',
  emits: ['click'],
  setup(_, { emit, slots }) {
    return () =>
      h(
        'button',
        {
          type: 'button',
          class: 'stub-menu-item',
          onClick: () => emit('click'),
        },
        slots.default?.(),
      )
  },
})

const AMenuDividerStub = defineComponent({
  name: 'AMenuDividerStub',
  setup() {
    return () => h('hr', { class: 'stub-menu-divider' })
  },
})

// ── Simple template stubs for remaining antd components ──
const simpleStubs = {
  'a-layout': { template: '<div class="stub-a-layout"><slot /></div>' },
  'a-layout-sider': { template: '<div class="stub-a-layout-sider"><slot /></div>' },
  'a-layout-header': { template: '<div class="stub-a-layout-header"><slot /></div>' },
  'a-layout-content': { template: '<div class="stub-a-layout-content"><slot /></div>' },
  'a-avatar': { template: '<div class="stub-a-avatar"><slot /></div>' },
  'a-badge': { template: '<div class="stub-a-badge"><slot /></div>' },
  'router-view': { template: '<div class="stub-router-view" />' },
}

// ── Helpers ──
function findMenuItemByText(wrapper: ReturnType<typeof mount>, text: string) {
  const items = wrapper.findAll('.stub-menu-item')
  return items.find((item) => item.text() === text)
}

describe('BasicLayout click handlers', () => {
  const createWrapper = () =>
    mount(BasicLayout, {
      global: {
        stubs: {
          ...simpleStubs,
          'a-dropdown': ADropdownStub,
          'a-menu': AMenuStub,
          'a-menu-item': AMenuItemStub,
          'a-menu-divider': AMenuDividerStub,
        },
      },
    })

  beforeEach(() => {
    mockPush.mockClear()
  })

  it('navigates to /profile when "个人中心" menu item is clicked', async () => {
    const wrapper = createWrapper()

    const profileItem = findMenuItemByText(wrapper, '个人中心')
    expect(profileItem).toBeDefined()

    profileItem!.trigger('click')

    expect(mockPush).toHaveBeenCalledTimes(1)
    expect(mockPush).toHaveBeenCalledWith('/profile')
  })

  it('navigates to /settings when "设置" menu item is clicked', async () => {
    const wrapper = createWrapper()

    const settingsItem = findMenuItemByText(wrapper, '设置')
    expect(settingsItem).toBeDefined()

    settingsItem!.trigger('click')

    expect(mockPush).toHaveBeenCalledTimes(1)
    expect(mockPush).toHaveBeenCalledWith('/settings')
  })

  it('navigates to /login when "退出登录" is clicked (existing handler still works)', async () => {
    const wrapper = createWrapper()

    const logoutItem = findMenuItemByText(wrapper, '退出登录')
    expect(logoutItem).toBeDefined()

    logoutItem!.trigger('click')

    expect(mockPush).toHaveBeenCalledWith('/login')
  })
})
