import { describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { defineComponent, h, type PropType } from 'vue'
import SidebarMenu from '@/layouts/components/SidebarMenu.vue'

type MenuItem = {
  key: string
  label: string
  children?: MenuItem[]
}

const mockPush = vi.fn()
const mockRoles = vi.hoisted(() => ({ value: ['ADMIN'] as string[] }))

vi.mock('vue-router', async () => {
  const actual = await vi.importActual<typeof import('vue-router')>('vue-router')
  return {
    ...actual,
    useRoute: () => ({
      path: '/dashboard',
      matched: [{ path: '/' }, { path: '/dashboard' }],
    }),
    useRouter: () => ({
      push: mockPush,
    }),
  }
})

vi.mock('@/stores/user', () => ({
  useUserStore: () => ({
    roles: mockRoles.value,
  }),
}))

const AMenuStub = defineComponent({
  name: 'AMenuStub',
  props: {
    items: {
      type: Array as PropType<MenuItem[]>,
      default: () => [],
    },
  },
  emits: ['click'],
  setup(props, { emit }) {
    const renderItems = (items: MenuItem[]) =>
      items.flatMap((item) => [
        h(
          'button',
          {
            type: 'button',
            'data-menu-key': item.key,
            onClick: () => emit('click', { key: item.key }),
          },
          item.label,
        ),
        ...(item.children ? renderItems(item.children) : []),
      ])

    return () => h('nav', { class: 'menu-stub' }, renderItems(props.items))
  },
})

describe('SidebarMenu', () => {
  beforeEach(() => {
    mockRoles.value = ['ADMIN']
  })

  it('uses full paths for nested menu item keys', () => {
    const wrapper = mount(SidebarMenu, {
      global: {
        stubs: {
          'a-menu': AMenuStub,
        },
      },
    })

    const keys = wrapper.findAll('[data-menu-key]').map((node) => node.attributes('data-menu-key'))

    expect(keys).toContain('/variation/order')
    expect(keys).toContain('/inventory/warehouse')
    expect(keys).not.toContain('order')
    expect(keys).not.toContain('warehouse')
  })

  it('orders root menu items by approved business flow and renames target cost', () => {
    const wrapper = mount(SidebarMenu, {
      global: {
        stubs: {
          'a-menu': AMenuStub,
        },
      },
    })

    const rootKeys = wrapper
      .findAll('[data-menu-key]')
      .map((node) => node.attributes('data-menu-key'))
      .filter((key) =>
        [
          '/dashboard',
          '/project',
          '/cost-target',
          '/cost',
          '/contract',
          '/variation',
          '/settlement',
          '/payment',
        ].includes(key),
      )

    expect(rootKeys).toEqual([
      '/dashboard',
      '/project',
      '/cost-target',
      '/cost',
      '/contract',
      '/variation',
      '/settlement',
      '/payment',
    ])

    expect(wrapper.text()).toContain('目标管理')
    expect(wrapper.text()).not.toContain('目标成本管理')
  })

  it('includes partner menu entry between project and cost-target', () => {
    const wrapper = mount(SidebarMenu, {
      global: {
        stubs: {
          'a-menu': AMenuStub,
        },
      },
    })

    const rootKeys = wrapper
      .findAll('[data-menu-key]')
      .map((node) => node.attributes('data-menu-key'))
      .filter((key) =>
        [
          '/dashboard',
          '/project',
          '/partner',
          '/cost-target',
          '/cost',
          '/contract',
          '/variation',
          '/settlement',
          '/payment',
        ].includes(key),
      )

    expect(rootKeys).toEqual([
      '/dashboard',
      '/project',
      '/partner',
      '/cost-target',
      '/cost',
      '/contract',
      '/variation',
      '/settlement',
      '/payment',
    ])

    expect(wrapper.text()).toContain('合作方管理')
  })

  it('shows approval process management only to administrators', () => {
    mockRoles.value = ['ADMIN']
    const adminWrapper = mount(SidebarMenu, {
      global: {
        stubs: {
          'a-menu': AMenuStub,
        },
      },
    })

    expect(adminWrapper.text()).toContain('审批流程管理')
    expect(
      adminWrapper.findAll('[data-menu-key]').map((node) => node.attributes('data-menu-key')),
    ).toContain('/approval/process')

    mockRoles.value = ['PROJECT_MANAGER']
    const userWrapper = mount(SidebarMenu, {
      global: {
        stubs: {
          'a-menu': AMenuStub,
        },
      },
    })

    expect(userWrapper.text()).not.toContain('审批流程管理')
    expect(
      userWrapper.findAll('[data-menu-key]').map((node) => node.attributes('data-menu-key')),
    ).not.toContain('/approval/process')
  })
})
