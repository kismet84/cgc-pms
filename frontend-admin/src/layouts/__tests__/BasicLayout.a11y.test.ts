import { describe, it, expect, vi, beforeAll } from 'vitest'
import { mount } from '@vue/test-utils'
import { ref } from 'vue'
import BasicLayout from '@/layouts/BasicLayoutAsync.vue'

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

// ── Mock vue-router ──
vi.mock('vue-router', () => ({
  useRouter: () => ({ push: vi.fn() }),
}))

// ── Mock pinia stores ──
vi.mock('@/stores/user', () => ({
  useUserStore: () => ({
    logout: vi.fn(),
    userInfo: ref({ userId: '1', realName: '张三', roleName: '项目经理' }),
  }),
}))

vi.mock('pinia', () => ({
  storeToRefs: (store: { userInfo: ReturnType<typeof ref> }) => ({
    userInfo: store.userInfo,
  }),
}))

// ── Mock icons (inheritAttrs: true so aria-label and class are applied to root) ──
vi.mock('@ant-design/icons-vue', () => ({
  MenuFoldOutlined: { template: '<span class="icon-stub"><slot /></span>' },
  ProjectOutlined: { template: '<span class="icon-stub"><slot /></span>' },
}))

// ── Mock sub-components ──
vi.mock('@/components/NotificationBell.vue', () => ({
  default: { name: 'NotificationBell', template: '<span class="nb-stub">Bell</span>' },
}))
vi.mock('@/layouts/components/SidebarMenu.vue', () => ({
  default: { name: 'SidebarMenu', template: '<div class="sidebar-stub">Menu</div>' },
}))

// ── Mock Ant Design Vue components we don't need to fully render ──
const antdStubs = [
  'a-layout',
  'a-layout-sider',
  'a-layout-header',
  'a-layout-content',
  'a-avatar',
  'a-dropdown',
  'a-menu',
  'a-menu-item',
  'a-menu-divider',
  'a-badge',
  'router-view',
]

// Helper: create a stub object from names
function makeStubs(names: string[]) {
  return Object.fromEntries(
    names.map((n) => [n, { template: `<div class="stub-${n}"><slot /></div>` }]),
  )
}

describe('BasicLayout accessibility', () => {
  it('renders sidebar toggle with aria-label attribute', () => {
    const wrapper = mount(BasicLayout, {
      global: {
        stubs: makeStubs(antdStubs),
      },
    })

    const toggle = wrapper.find('.sidebar-toggle')
    expect(toggle.exists()).toBe(true)
    // Default collapsed=false → aria-label should be "折叠菜单"
    expect(toggle.attributes('aria-label')).toBe('折叠菜单')
  })

  it('updates sidebar toggle aria-label when collapsed changes', async () => {
    const wrapper = mount(BasicLayout, {
      global: {
        stubs: makeStubs(antdStubs),
      },
    })

    const toggle = wrapper.find('.sidebar-toggle')

    // Verify initial state
    expect(toggle.attributes('aria-label')).toBe('折叠菜单')

    // Click to toggle — the component should react and update the aria-label
    await toggle.trigger('click')
    await wrapper.vm.$nextTick()

    // After click, collapsed should toggle to true
    expect(toggle.attributes('aria-label')).toBe('展开菜单')

    // Click again
    await toggle.trigger('click')
    await wrapper.vm.$nextTick()
    expect(toggle.attributes('aria-label')).toBe('折叠菜单')
  })

  it('renders notification bell wrapper with aria-label after lazy load delay', async () => {
    vi.useFakeTimers()
    const wrapper = mount(BasicLayout, {
      global: {
        stubs: makeStubs(antdStubs),
      },
    })

    // bellReady starts false; NotificationBell is conditionally rendered via v-if
    expect(wrapper.find('span[aria-label="通知"]').exists()).toBe(false)

    // Advance past the 500ms setTimeout in onMounted
    vi.advanceTimersByTime(500)
    await wrapper.vm.$nextTick()

    const bellWrapper = wrapper.find('span[aria-label="通知"]')
    expect(bellWrapper.exists()).toBe(true)
    expect(wrapper.find('.sidebar-bell-label').text()).toBe('通知中心')

    vi.useRealTimers()
  })

})
